// Muestra avisos de carga y errores que informa la conexión con el servidor.
import { useEffect, useState } from 'react'

export function ApiFeedback() {
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  useEffect(() => {
    const onError = (event: Event) => setError(String((event as CustomEvent<unknown>).detail))
    const onActivity = (event: Event) =>
      setLoading(Number((event as CustomEvent<unknown>).detail) > 0)
    const clear = () => setError('')
    window.addEventListener('alon:api-error', onError)
    window.addEventListener('alon:activity', onActivity)
    window.addEventListener('alon:refresh', clear)
    return () => {
      window.removeEventListener('alon:api-error', onError)
      window.removeEventListener('alon:activity', onActivity)
      window.removeEventListener('alon:refresh', clear)
    }
  }, [])
  return (
    <div className="api-feedback" aria-live="polite">
      {loading && <p role="status">Cargando datos…</p>}
      {error && (
        <div role="alert">
          <span>{error}</span>{' '}
          <button type="button" onClick={() => window.dispatchEvent(new Event('alon:refresh'))}>
            Reintentar
          </button>{' '}
          <button type="button" aria-label="Cerrar aviso" onClick={() => setError('')}>
            Cerrar
          </button>
        </div>
      )}
    </div>
  )
}
