// Muestra avisos de carga y errores que informa la conexión con el servidor.
import { useEffect, useState } from 'react'

/** Muestra la actividad de las solicitudes y los errores globales de conexión sin interrumpir los formularios. */
export function ApiFeedback() {
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  useEffect(() => {
    /** Recibe el mensaje del evento de error de la API y lo presenta en el aviso global. */
    const onError = (event: Event) => setError(String((event as CustomEvent<unknown>).detail))
    /** Actualiza el indicador de carga según la cantidad de solicitudes que siguen en curso. */
    const onActivity = (event: Event) =>
      setLoading(Number((event as CustomEvent<unknown>).detail) > 0)
    /** Oculta el error global cuando se notifica una actualización o se solicita reintentar la consulta. */
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
