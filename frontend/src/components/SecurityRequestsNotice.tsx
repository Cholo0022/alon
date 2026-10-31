import { useEffect, useState } from 'react'
import { apiFetch } from '../lib/http'

/** Avisa al gerente que hay navegadores esperando autorización sin exponer la administración a empleados. */
export function SecurityRequestsNotice({ onOpen }: { onOpen: () => void }) {
  const [count, setCount] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    /** Consulta el total de solicitudes vigentes, incluso cuando el listado tiene más de una página. */
    const load = () => {
      if (document.visibilityState === 'hidden') return
      void apiFetch('/api/v1/auth/security/pending-count', { signal: controller.signal })
        .then(async (response) => {
          if (response.ok) {
            const result = await response.json()
            if (!controller.signal.aborted) setCount(Number(result.count) || 0)
          }
        })
        .catch(() => undefined)
    }
    load()
    const timer = window.setInterval(load, 15000)
    window.addEventListener('alon:refresh', load)
    return () => {
      controller.abort()
      clearInterval(timer)
      window.removeEventListener('alon:refresh', load)
    }
  }, [])
  if (!count) return null
  return (
    <div className="security-access-notice">
      <button className="select-button" type="button" onClick={onOpen}>
        {count
          ? `${count} solicitud${count === 1 ? '' : 'es'} de ingreso pendiente${count === 1 ? '' : 's'}`
          : 'Seguridad de ingreso y cuenta del gerente'}
      </button>
    </div>
  )
}
