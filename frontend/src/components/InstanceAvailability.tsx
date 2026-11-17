import { useEffect, useState, type ReactNode } from 'react'
import './instance-availability.css'

/** Retira las pantallas operativas cuando el administrador bloquea una instancia. */
export function InstanceAvailability({ children }: { children: ReactNode }) {
  const [state, setState] = useState('CHECKING')
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    let managed: boolean | null = null
    const check = async () => {
      try {
        const response = await fetch('/api/v1/health', {
          cache: 'no-store',
          credentials: 'same-origin',
          signal: controller.signal,
        })
        if (!response.ok) throw new Error('No disponible')
        if (controller.signal.aborted) return
        managed = response.headers.get('X-Alon-Service-Control') === 'enabled'
        setState(managed ? (response.headers.get('X-Alon-Service-State') ?? 'UNAVAILABLE') : '')
      } catch {
        if (!controller.signal.aborted && managed !== false) setState('UNAVAILABLE')
      } finally {
        if (!controller.signal.aborted && managed !== false)
          timer = setTimeout(() => void check(), 5000)
      }
    }
    void check()
    return () => {
      controller.abort()
      if (timer) clearTimeout(timer)
    }
  }, [revision])
  if (state === 'CHECKING')
    return (
      <main className="instance-unavailable" role="status">
        Comprobando disponibilidad…
      </main>
    )
  if (!state || ['ACTIVO', 'PRUEBA'].includes(state)) return children
  const title =
    state === 'PRUEBA_VENCIDA'
      ? 'Período de prueba vencido'
      : state === 'BAJA'
        ? 'Negocio dado de baja'
        : state === 'SUSPENDIDO'
          ? 'Negocio inhabilitado'
          : state === 'PREPARACION'
            ? 'Negocio en preparación'
            : 'Negocio no disponible'
  return (
    <main className="instance-unavailable" aria-live="polite">
      <section>
        <h1>{title}</h1>
        <p>El sistema y la tienda no están disponibles. Comunicate con el administrador de Alon.</p>
        <button
          type="button"
          onClick={() => {
            setState('CHECKING')
            setRevision((n) => n + 1)
          }}
        >
          Comprobar estado
        </button>
      </section>
    </main>
  )
}
