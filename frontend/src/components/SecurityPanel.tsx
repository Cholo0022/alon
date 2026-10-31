import { useEffect, useRef, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { formatDateTimeDisplay } from '../lib/format'

type BrowserAccess = {
  id: string
  referencia?: string
  usuario: string
  ip: string
  navegador: string
  creado_en?: string
  aprobado_en?: string
  vence_en: string
}
type SecurityData = {
  authenticatorRequired: boolean
  username: string
  email: string
  mailConfigured: boolean
  mailPending: number
  mailFailed: number
  recoveryRemaining: number
  requests: BrowserAccess[]
  browsers: BrowserAccess[]
  events: { tipo: string; usuario: string; ip: string; creado_en: string }[]
}

/** Administra el correo del gerente, sus factores y las solicitudes de navegadores del comercio; nunca recibe claves TOTP guardadas. */
export function SecurityPanel() {
  const [data, setData] = useState<SecurityData | null>(null)
  const [email, setEmail] = useState('')
  const emailEdited = useRef(false)
  const [code, setCode] = useState('')
  const [codes, setCodes] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    /** Refresca el panel sin sobrescribir un correo que el gerente está editando. */
    const load = () => {
      void apiFetch('/api/v1/auth/security/dashboard', { signal: controller.signal })
        .then(async (response) => {
          const body = await response.json()
          if (!response.ok) throw new Error(body.error ?? 'No se pudo consultar la seguridad.')
          if (!controller.signal.aborted) {
            setData(body)
            if (!emailEdited.current) setEmail(body.email)
          }
        })
        .catch((error) => {
          if (!controller.signal.aborted)
            setMessage(error instanceof Error ? error.message : 'No se pudo actualizar el panel.')
        })
    }
    load()
    const timer = window.setInterval(() => {
      if (document.visibilityState !== 'hidden') load()
    }, 15000)
    return () => {
      controller.abort()
      clearInterval(timer)
    }
  }, [revision])
  /** Ejecuta una acción de seguridad, conserva mensajes de rechazo y publica los cambios confirmados. */
  const action = async (path: string, payload?: object) => {
    if (busy) return
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch('/api/v1/auth/security/' + path, {
        method: path === 'email' ? 'PUT' : 'POST',
        ...(payload
          ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }
          : {}),
      })
      const result = await response.json()
      if (!response.ok) throw new Error(result.error ?? 'No se pudo completar la acción.')
      if (result.challenge?.stage) {
        window.dispatchEvent(new CustomEvent('alon:auth-pending', { detail: result }))
        return
      }
      if (result.recoveryCodes) setCodes(result.recoveryCodes)
      if (path === 'email') emailEdited.current = false
      setCode('')
      setRevision((value) => value + 1)
      window.dispatchEvent(new Event('alon:refresh'))
      setMessage('Acción de seguridad completada.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar con Alon.')
    } finally {
      setBusy(false)
    }
  }
  /** Solicita reemplazar el celular comprobando nuevamente la contraseña y un factor actual o de recuperación. */
  const reset = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!code.trim()) {
      setMessage('Ingresá un código del autenticador o de recuperación.')
      return
    }
    const values = new FormData(event.currentTarget)
    await action('reset', { password: values.get('password'), code })
  }
  return (
    <section className="entry-panel security-panel">
      <div className="entry-heading">
        <strong>Seguridad de ingreso y cuenta del Gerente / superusuario</strong>
      </div>
      {!data ? (
        <p role="status">Cargando seguridad…</p>
      ) : (
        <div className="security-panel-content">
          <p>
            Cuenta: <strong>{data.username}</strong>.{' '}
            {data.authenticatorRequired ? (
              <>
                Autenticador activado. Códigos de recuperación disponibles:{' '}
                <strong>{data.recoveryRemaining}</strong>.
              </>
            ) : (
              <>
                Ingreso con el usuario y la contraseña creados por alon. Correo:{' '}
                <strong>{data.email}</strong>.
              </>
            )}
          </p>
          <p>
            {data.mailConfigured
              ? 'El envío de avisos por correo está configurado.'
              : 'Los avisos se muestran dentro de Alon. Para enviarlos por correo falta configurar el servidor de correo.'}
          </p>
          {data.mailConfigured && data.mailFailed > 0 && (
            <p role="alert">
              Hay {data.mailFailed} avisos que no se pudieron enviar. Revisá la configuración del
              correo del servidor.
            </p>
          )}
          {data.authenticatorRequired && (
            <>
              <label>
                Código del autenticador o de recuperación
                <input
                  value={code}
                  onChange={(event) => setCode(event.target.value)}
                  maxLength={80}
                  autoComplete="one-time-code"
                  disabled={busy}
                />
              </label>
              <small>
                Para cambiar el correo, los códigos o el celular, usá un código actual. Si ya usaste
                el del autenticador, esperá el siguiente.
              </small>
              <form
                className="entry-form"
                onSubmit={(event) => {
                  event.preventDefault()
                  void action('email', { email, code })
                }}
              >
                <label>
                  Correo de la cuenta
                  <input
                    type="email"
                    value={email}
                    maxLength={254}
                    onChange={(event) => {
                      emailEdited.current = true
                      setEmail(event.target.value)
                    }}
                    required
                    disabled={busy}
                  />
                </label>
                <button className="primary-button" disabled={busy || !code.trim()}>
                  Guardar correo del gerente
                </button>
              </form>
            </>
          )}
          <h2>Solicitudes de ingreso</h2>
          <p>Confirmá la referencia con la persona que solicita acceso antes de aprobar.</p>
          {data.requests.length === 0 ? (
            <p>No hay solicitudes pendientes.</p>
          ) : (
            <div className="security-browser-list">
              {data.requests.map((row) => (
                <article key={row.id}>
                  <strong>
                    {row.usuario} · {row.referencia}
                  </strong>
                  <span>
                    IP: {row.ip} · {formatDateTimeDisplay(row.creado_en)}
                  </span>
                  <small>{row.navegador}</small>
                  <div>
                    <button
                      className="primary-button"
                      disabled={busy}
                      onClick={() => void action(`browsers/${row.id}/approve`)}
                    >
                      Aprobar
                    </button>
                    <button
                      className="select-button"
                      disabled={busy}
                      onClick={() => void action(`browsers/${row.id}/reject`)}
                    >
                      Rechazar
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}
          <h2>Navegadores autorizados</h2>
          {data.browsers.length === 0 ? (
            <p>No hay navegadores de empleados autorizados.</p>
          ) : (
            <div className="security-browser-list">
              {data.browsers.map((row) => (
                <article key={row.id}>
                  <strong>{row.usuario}</strong>
                  <span>
                    IP: {row.ip} · Vence: {formatDateTimeDisplay(row.vence_en)}
                  </span>
                  <small>{row.navegador}</small>
                  <button
                    className="select-button"
                    disabled={busy}
                    onClick={() => void action(`browsers/${row.id}/revoke`)}
                  >
                    Revocar acceso
                  </button>
                </article>
              ))}
            </div>
          )}
          {data.authenticatorRequired && (
            <>
              <h2>Recuperación de acceso</h2>
              <p>Generar códigos nuevos invalida todos los anteriores.</p>
              <button
                className="select-button"
                disabled={busy || !code.trim()}
                onClick={() => void action('recovery', { code })}
              >
                Generar nuevos códigos de recuperación
              </button>
              {codes.length > 0 && (
                <div className="security-recovery-result">
                  <p>Guardá estos códigos. Cada uno sirve una vez y reemplazan a los anteriores.</p>
                  <ul className="security-codes">
                    {codes.map((value) => (
                      <li key={value}>
                        <code>{value}</code>
                      </li>
                    ))}
                  </ul>
                  <a
                    download="alon-codigos-recuperacion.txt"
                    href={'data:text/plain;charset=utf-8,' + encodeURIComponent(codes.join('\n'))}
                  >
                    Descargar códigos
                  </a>
                  <button className="select-button" onClick={() => setCodes([])}>
                    Ya los guardé, ocultar códigos
                  </button>
                </div>
              )}
              <form className="entry-form" onSubmit={(event) => void reset(event)}>
                <label>
                  Contraseña actual para cambiar de celular
                  <input
                    name="password"
                    type="password"
                    autoComplete="current-password"
                    maxLength={128}
                    required
                    disabled={busy}
                  />
                </label>
                <button className="select-button" disabled={busy || !code.trim()}>
                  Cambiar autenticador
                </button>
              </form>
            </>
          )}
          <h2>Últimos eventos de seguridad</h2>
          <ul>
            {data.events.map((item, index) => (
              <li key={index}>
                {formatDateTimeDisplay(item.creado_en)} · {item.usuario} ·{' '}
                {item.tipo.replaceAll('_', ' ')} · {item.ip}
              </li>
            ))}
          </ul>
        </div>
      )}
      {message && (
        <p role="status" className="entry-message">
          {message}
        </p>
      )}
    </section>
  )
}
