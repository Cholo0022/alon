// Prueba de lectura a través del túnel SSH de Alon.
import { setTimeout as pause } from 'node:timers/promises'

const base = 'http://127.0.0.1:18080'
const catalog = '/api/v1/shop/catalog?page=0'
const routes = ['/', catalog, catalog + '&q=camisa', catalog + '&q=zapatilla']

async function request(path) {
  const started = performance.now()
  try {
    const response = await fetch(base + path, {
      redirect: 'error',
      signal: AbortSignal.timeout(5_000),
    })
    await response.arrayBuffer()
    return { status: String(response.status), ms: performance.now() - started }
  } catch (error) {
    return { status: error.cause?.code ?? error.name, ms: performance.now() - started }
  }
}

async function measure(users) {
  const started = performance.now()
  const until = started + 15_000
  const results = []
  let failures = 0
  await Promise.all(
    Array.from({ length: users }, async (_, index) => {
      let step = index
      while (performance.now() < until && failures < 3) {
        const result = await request(routes[step++ % routes.length])
        results.push(result)
        if (result.status !== '200') failures++
        await pause(350)
      }
    }),
  )

  const successful = results.filter(({ status }) => status === '200')
  const statuses = Object.fromEntries(
    [...new Set(results.map(({ status }) => status))].map((status) => [
      status,
      results.filter((result) => result.status === status).length,
    ]),
  )
  const responseTimes = successful
    .map(({ ms }) => ms)
    .sort((left, right) => left - right)
  console.log(JSON.stringify({
    concurrencia: users,
    solicitudes: results.length,
    respuestas: statuses,
    solicitudesPorSegundo: Number((results.length * 1_000 / (performance.now() - started)).toFixed(1)),
    p95Ms: responseTimes.length
      ? Math.round(responseTimes[Math.ceil(responseTimes.length * 0.95) - 1])
      : null,
  }))
  return failures
}

const health = await request('/api/v1/health')
const shop = await request(catalog)
if (health.status !== '200' || shop.status !== '200') {
  console.error('Prueba cancelada. Revisá el túnel y la tienda.', { health, shop })
  process.exitCode = 1
} else {
  for (const users of [1, 5, 10, 20]) {
    if (await measure(users)) {
      console.error('Prueba detenida por errores. No se aumenta la concurrencia.')
      process.exitCode = 1
      break
    }
    await pause(1_000)
  }
}
