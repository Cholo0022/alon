import { useEffect, useState } from 'react'
import { apiFetch } from '../lib/http'

export function PendingOrdersNotice({ onOpen }: { onOpen: () => void }) {
  const [count, setCount] = useState<number | null>(null)
  const [error, setError] = useState(false)
  useEffect(() => {
    let current: AbortController | null = null
    const load = () => {
      if (document.visibilityState === 'hidden') return
      current?.abort()
      const controller = new AbortController()
      current = controller
      void apiFetch('/api/v1/demo/store-orders/pending-count', { signal: controller.signal })
        .then(async (response) => {
          if (!response.ok) throw new Error('No se pudo consultar la cantidad de pedidos.')
          const body = (await response.json()) as { count: unknown }
          if (typeof body.count !== 'number' || !Number.isSafeInteger(body.count) || body.count < 0)
            throw new Error('La cantidad no es válida.')
          if (!controller.signal.aborted) {
            setCount(body.count)
            setError(false)
          }
        })
        .catch(() => {
          if (!controller.signal.aborted) setError(true)
        })
    }
    load()
    const timer = window.setInterval(load, 10000)
    window.addEventListener('alon:refresh', load)
    window.addEventListener('focus', load)
    document.addEventListener('visibilitychange', load)
    return () => {
      current?.abort()
      clearInterval(timer)
      window.removeEventListener('alon:refresh', load)
      window.removeEventListener('focus', load)
      document.removeEventListener('visibilitychange', load)
    }
  }, [])
  if (!error && !count) return null
  return (
    <section className="pending-orders-notice" role="status" aria-live="polite" aria-atomic="true">
      <div>
        <strong>Ventas web por confirmar</strong>
        <p>
          {error
            ? 'No se pudo actualizar la cantidad de pedidos pendientes.'
            : count === 1
              ? 'Tenés 1 venta web pendiente de confirmar.'
              : 'Tenés ' + count!.toLocaleString('es-AR') + ' ventas web pendientes de confirmar.'}
        </p>
      </div>
      <div className="row-actions">
        {error && (
          <button
            className="secondary-button"
            type="button"
            onClick={() => window.dispatchEvent(new Event('alon:refresh'))}
          >
            Reintentar
          </button>
        )}
        <button className="primary-button" type="button" onClick={onOpen}>
          Ver pedidos
        </button>
      </div>
    </section>
  )
}
