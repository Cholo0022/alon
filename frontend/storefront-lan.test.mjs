import assert from 'node:assert/strict'
import { test } from 'node:test'
import http from 'node:http'
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { createStorefrontServer, sameSubnet } from './storefront-lan.mjs'

/** Comprueba que solo la tienda y sus imágenes/API sean accesibles, conservando sesión y CSRF del comprador. */
test('Wi-Fi serves the compiled shop, proxies customer requests and blocks administration and source files', async (t) => {
  const root = await mkdtemp(path.join(tmpdir(), 'alon-wifi-'))
  await mkdir(path.join(root, 'assets'))
  await writeFile(path.join(root, 'index.html'), '<html>Tienda de prueba</html>')
  await writeFile(path.join(root, 'assets', 'shop.js'), 'console.log("prueba")')
  const backend = http.createServer((req, res) => {
    res.setHeader('Content-Type', 'application/json')
    res.setHeader('Set-Cookie', 'JSESSIONID=solo-prueba; Path=/api/v1; HttpOnly; SameSite=Strict')
    res.end(
      JSON.stringify({
        route: req.url,
        csrf: req.headers['x-csrf-token'],
        cookie: req.headers.cookie,
        key: req.headers['idempotency-key'],
      }),
    )
  })
  await new Promise((resolve) => backend.listen(0, '127.0.0.1', resolve))
  const server = createStorefrontServer({
    distDir: root,
    backendUrl: 'http://127.0.0.1:' + backend.address().port,
    allowedSubnet: { address: '127.0.0.1', prefixLength: 8 },
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  t.after(async () => {
    await new Promise((resolve) => server.close(resolve))
    await new Promise((resolve) => backend.close(resolve))
    await rm(root, { recursive: true })
  })
  const origin = 'http://127.0.0.1:' + server.address().port
  assert.equal((await fetch(origin + '/tienda')).status, 200)
  assert.equal((await fetch(origin + '/assets/shop.js')).status, 200)
  for (const route of [
    '/index.html',
    '/src/main.tsx',
    '/@vite/client',
    '/.env',
    '/api/v1/auth/status',
    '/api/v1/super/dashboard',
    '/api/v1/operations/sales',
    '/api/v1/shop/../auth/status',
  ]) {
    const result = await fetch(origin + route)
    assert.ok([400, 403, 404].includes(result.status), route)
  }
  const response = await fetch(origin + '/api/v1/shop/orders', {
    method: 'POST',
    headers: {
      'X-CSRF-Token': 'csrf-prueba',
      Cookie: 'JSESSIONID=solo-prueba',
      'Idempotency-Key': 'pedido-prueba',
      'Content-Type': 'application/json',
    },
    body: '{}',
  })
  const data = await response.json()
  assert.equal(data.csrf, 'csrf-prueba')
  assert.equal(data.cookie, 'JSESSIONID=solo-prueba')
  assert.equal(data.key, 'pedido-prueba')
  assert.match(response.headers.get('set-cookie'), /HttpOnly/)
})
/** Bloquea otras redes y acepta las direcciones reales de celulares en la Wi-Fi. */
test('connections are restricted to the actual Wi-Fi subnet', () => {
  assert.equal(sameSubnet('192.168.1.100', '192.168.1.82', 24), true)
  assert.equal(sameSubnet('::ffff:192.168.1.100', '192.168.1.82', 24), true)
  assert.equal(sameSubnet('192.168.2.100', '192.168.1.82', 24), false)
  assert.equal(sameSubnet('8.8.8.8', '192.168.1.82', 24), false)
  assert.equal(sameSubnet('invalid', '192.168.1.82', 24), false)
})
