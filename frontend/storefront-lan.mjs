import http from 'node:http'
import { createReadStream } from 'node:fs'
import { realpath, stat } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const types = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
}
/** Devuelve errores breves sin mostrar rutas del servidor ni detalles de la administración. */
function reject(response, status, message) {
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  })
  response.end(JSON.stringify({ error: message }))
}
/** Sirve únicamente la tienda compilada y su API; no publica Vite, archivos fuente ni rutas de gestión. */
export function createStorefrontServer({
  distDir,
  backendUrl = 'http://127.0.0.1:8080',
  allowedSubnet,
}) {
  const root = path.resolve(distDir)
  const backend = new URL(backendUrl)
  if (
    !['127.0.0.1', 'localhost', '[::1]'].includes(backend.hostname) ||
    backend.protocol !== 'http:'
  )
    throw new Error('El servidor de la tienda debe usar la API local.')
  return http.createServer(async (request, response) => {
    if (
      allowedSubnet &&
      !sameSubnet(request.socket.remoteAddress, allowedSubnet.address, allowedSubnet.prefixLength)
    )
      return reject(response, 403, 'Este acceso es exclusivo de la misma red Wi-Fi.')
    response.setHeader('X-Content-Type-Options', 'nosniff')
    response.setHeader('X-Frame-Options', 'DENY')
    response.setHeader('Referrer-Policy', 'same-origin')
    try {
      const url = new URL(request.url, 'http://tienda.local')
      const decoded = decodeURIComponent(url.pathname)
      if (decoded.includes('\\') || decoded.includes('%') || decoded.includes('\0'))
        return reject(response, 400, 'Dirección inválida.')
      const route = path.posix.normalize(decoded)
      if (route.startsWith('/api/')) {
        if (!route.startsWith('/api/v1/shop/'))
          return reject(response, 403, 'Este acceso es exclusivo de la tienda.')
        const headers = { ...request.headers }
        for (const name of [
          'connection',
          'proxy-connection',
          'keep-alive',
          'te',
          'trailer',
          'transfer-encoding',
          'upgrade',
          'proxy-authorization',
          'proxy-authenticate',
        ])
          delete headers[name]
        const upstream = http.request(
          {
            hostname: backend.hostname,
            port: backend.port || 80,
            path: route + url.search,
            method: request.method,
            headers,
          },
          (result) => {
            response.writeHead(result.statusCode ?? 502, result.headers)
            result.pipe(response)
            result.on('error', () => response.destroy())
          },
        )
        upstream.setTimeout(20000, () => upstream.destroy(new Error('Tiempo agotado')))
        upstream.on('error', () => {
          if (!response.headersSent)
            reject(response, 503, 'La tienda está iniciando. Volvé a intentar.')
          else response.destroy()
        })
        request.on('aborted', () => upstream.destroy())
        request.pipe(upstream)
        return
      }
      if (!['GET', 'HEAD'].includes(request.method))
        return reject(response, 405, 'Método no permitido.')
      if (route === '/') {
        response.writeHead(302, { Location: '/tienda', 'Cache-Control': 'no-store' })
        response.end()
        return
      }
      const isStore = route === '/tienda' || route.startsWith('/tienda/')
      const relative = isStore ? 'index.html' : route.slice(1)
      if (
        !isStore &&
        !route.startsWith('/assets/') &&
        route !== '/alon-mark.svg' &&
        route !== '/vite.svg'
      )
        return reject(response, 404, 'Página no disponible en la tienda.')
      const filename = await realpath(path.resolve(root, relative))
      if (!filename.startsWith(root + path.sep))
        return reject(response, 403, 'Archivo no disponible.')
      const info = await stat(filename)
      if (!info.isFile()) return reject(response, 404, 'Archivo no disponible.')
      response.writeHead(200, {
        'Content-Type': types[path.extname(filename)] ?? 'application/octet-stream',
        'Content-Length': info.size,
        'Cache-Control':
          relative === 'index.html' ? 'no-store' : 'public, max-age=31536000, immutable',
      })
      if (request.method === 'HEAD') response.end()
      else
        createReadStream(filename)
          .on('error', () => response.destroy())
          .pipe(response)
    } catch {
      if (!response.headersSent) reject(response, 404, 'Página no disponible.')
      else response.destroy()
    }
  })
}

/** Inicia el acceso de Wi-Fi en la dirección elegida por el iniciador de Windows. */
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const host = process.env.ALON_SHOP_WIFI_HOST
  if (!host || !/^\d{1,3}(\.\d{1,3}){3}$/.test(host))
    throw new Error('Falta la dirección Wi-Fi de esta PC.')
  const prefixLength = Number(process.env.ALON_SHOP_WIFI_PREFIX ?? 24)
  if (!Number.isInteger(prefixLength) || prefixLength < 1 || prefixLength > 32)
    throw new Error('La red Wi-Fi no tiene un prefijo válido.')
  const server = createStorefrontServer({
    distDir: fileURLToPath(new URL('./dist', import.meta.url)),
    allowedSubnet: { address: host, prefixLength },
  })
  server.on('error', (error) => {
    process.stderr.write('No se pudo iniciar la tienda por Wi-Fi: ' + error.message + '\n')
    process.exitCode = 1
  })
  server.listen(5174, host, () =>
    process.stdout.write('Tienda por Wi-Fi: http://' + host + ':5174/tienda\n'),
  )
}

/** Compara la conexión real con la subred Wi-Fi sin confiar en cabeceras del navegador. */
export function sameSubnet(remote, address, prefixLength) {
  const parse = (value) => {
    const parts = String(value ?? '')
      .replace(/^::ffff:/, '')
      .split('.')
    if (parts.length !== 4 || parts.some((part) => !/^\d{1,3}$/.test(part) || Number(part) > 255))
      return null
    return parts.reduce((result, part) => (result * 256 + Number(part)) >>> 0, 0)
  }
  const client = parse(remote),
    local = parse(address)
  if (
    client === null ||
    local === null ||
    !Number.isInteger(prefixLength) ||
    prefixLength < 1 ||
    prefixLength > 32
  )
    return false
  const mask = (0xffffffff << (32 - prefixLength)) >>> 0
  return (client & mask) >>> 0 === (local & mask) >>> 0
}
