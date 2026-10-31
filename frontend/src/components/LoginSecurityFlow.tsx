import { useEffect, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { type AccessChallenge, type AuthStatus } from '../lib/session'

/** Gestiona activación, doble factor, recuperación y espera sin montar las secciones de gestión antes de obtener una sesión completa. */
export function LoginSecurityFlow({
  challenge,
  onState,
}: {
  challenge: AccessChallenge
  onState: (status: AuthStatus) => void
}) {
  const [details, setDetails] = useState<{ secret: string; qr: string } | null>(null)
  const [code, setCode] = useState('')
  const [email, setEmail] = useState(challenge.email ?? '')
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    if (challenge.stage !== 'ENROLL') return
    const controller = new AbortController()
    void apiFetch('/api/v1/auth/security/enroll', { method: 'POST', signal: controller.signal })
      .then(async (response) => {
        const body = await response.json()
        if (!response.ok) throw new Error(body.error ?? 'No se pudo preparar el autenticador.')
        if (!controller.signal.aborted) setDetails(body)
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setError(error instanceof Error ? error.message : 'No se pudo preparar el autenticador.')
      })
    return () => controller.abort()
  }, [challenge.stage, challenge.username])
  useEffect(() => {
    if (challenge.stage !== 'DEVICE' || challenge.deviceStatus !== 'PENDIENTE') return
    const controller = new AbortController()
    const timer = window.setInterval(() => {
      void apiFetch('/api/v1/auth/security/continue', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: '{}',
        signal: controller.signal,
      })
        .then(async (response) => {
          const body = await response.json()
          if (response.ok && !controller.signal.aborted) onState(body)
        })
        .catch(() => undefined)
    }, 3000)
    return () => {
      clearInterval(timer)
      controller.abort()
    }
  }, [challenge.stage, challenge.deviceStatus, onState])
  /** Envía el código o la confirmación de resguardo y actualiza la sesión únicamente desde la respuesta del servidor. */
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    try {
      const response = await apiFetch(
        '/api/v1/auth/security/' + (challenge.stage === 'ENROLL' ? 'confirm' : 'continue'),
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(challenge.stage === 'ENROLL' ? { code, email } : { code, saved }),
        },
      )
      const body = await response.json()
      if (!response.ok) throw new Error(body.error ?? 'No se pudo completar la verificación.')
      setCode('')
      onState(body)
    } catch (error) {
      setError(error instanceof Error ? error.message : 'No se pudo verificar el ingreso.')
    } finally {
      setBusy(false)
    }
  }
  /** Retira la solicitud pendiente y vuelve al ingreso sin conservar códigos ni secretos del formulario. */
  const cancel = async () => {
    if (busy) return
    setBusy(true)
    try {
      const response = await apiFetch('/api/v1/auth/security/cancel', { method: 'POST' })
      if (response.ok) onState(await response.json())
    } catch {
      setError('No se pudo cancelar. Volvé a intentar.')
    } finally {
      setBusy(false)
    }
  }
  const waiting = challenge.stage === 'DEVICE' && challenge.deviceStatus === 'PENDIENTE'
  return (
    <main className="auth-shell">
      <section className="auth-card security-login">
        <span className="brand-name">
          alon<span>.</span>
        </span>
        <p className="eyebrow">SEGURIDAD DE INGRESO · {challenge.username}</p>
        <h1>
          {challenge.stage === 'ENROLL'
            ? 'Vinculá tu autenticador'
            : challenge.stage === 'MFA'
              ? 'Verificación de seguridad'
              : challenge.stage === 'RECOVERY'
                ? 'Guardá tus códigos de recuperación'
                : waiting
                  ? 'Esperando autorización del gerente'
                  : 'Solicitud de ingreso finalizada'}
        </h1>
        {challenge.stage === 'ENROLL' && (
          <>
            <p>
              En tu celular abrí una aplicación autenticadora, agregá una cuenta y escaneá este QR.
              Después ingresá el código de seis dígitos que muestra.
            </p>
            {details ? (
              <>
                <img
                  className="security-qr"
                  src={details.qr}
                  alt="QR para configurar el autenticador de Alon"
                />
                <p>Si no podés escanearlo, ingresá esta clave manualmente:</p>
                <code className="security-secret">{details.secret}</code>
              </>
            ) : (
              <p role="status">Preparando el autenticador…</p>
            )}
            <p>No compartas el QR ni la clave. Solo se usan para configurar tu celular.</p>
          </>
        )}
        {challenge.stage === 'MFA' && (
          <p>
            Ingresá el código actual de tu aplicación autenticadora. Si perdiste el celular, podés
            usar uno de tus códigos de recuperación.
          </p>
        )}
        {challenge.stage === 'RECOVERY' && (
          <>
            <p>
              Cada código sirve una sola vez si no tenés acceso al celular. Guardalos fuera de esta
              computadora y no los compartas.
            </p>
            <ul className="security-codes">
              {challenge.recoveryCodes?.map((value) => (
                <li key={value}>
                  <code>{value}</code>
                </li>
              ))}
            </ul>
            <a
              className="security-download"
              download="alon-codigos-recuperacion.txt"
              href={
                'data:text/plain;charset=utf-8,' +
                encodeURIComponent(
                  'Códigos de recuperación Alon - ' +
                    challenge.username +
                    '\nCada código sirve una sola vez.\n\n' +
                    (challenge.recoveryCodes ?? []).join('\n'),
                )
              }
            >
              Descargar códigos de recuperación
            </a>
          </>
        )}
        {challenge.stage === 'DEVICE' && (
          <>
            <p>
              {waiting
                ? 'Tu usuario y contraseña son correctos. El gerente debe aprobar este navegador antes de que puedas ingresar.'
                : challenge.deviceStatus === 'RECHAZADO'
                  ? 'El gerente rechazó esta solicitud.'
                  : 'La solicitud venció o ya no está disponible. Iniciá sesión nuevamente.'}
            </p>
            {challenge.reference && (
              <p>
                Referencia para identificar la solicitud: <strong>{challenge.reference}</strong>
              </p>
            )}
            <p>
              La aprobación se comprueba automáticamente. Todavía no tenés acceso a datos ni
              operaciones.
            </p>
          </>
        )}
        {challenge.stage !== 'DEVICE' && (
          <form className="entry-form" onSubmit={(event) => void submit(event)}>
            {challenge.stage === 'ENROLL' && (
              <label>
                Correo de la cuenta
                <input
                  type="email"
                  autoComplete="email"
                  value={email}
                  maxLength={254}
                  onChange={(event) => setEmail(event.target.value)}
                  required
                  disabled={busy}
                />
              </label>
            )}
            {challenge.stage !== 'RECOVERY' ? (
              <label>
                {challenge.stage === 'ENROLL'
                  ? 'Código del autenticador'
                  : 'Código del autenticador o de recuperación'}
                <input
                  autoFocus
                  value={code}
                  onChange={(event) => setCode(event.target.value)}
                  autoComplete="one-time-code"
                  inputMode={challenge.stage === 'ENROLL' ? 'numeric' : 'text'}
                  maxLength={80}
                  required
                  disabled={busy}
                />
              </label>
            ) : (
              <label className="security-check">
                <input
                  type="checkbox"
                  checked={saved}
                  onChange={(event) => setSaved(event.target.checked)}
                />
                Guardé los códigos en un lugar seguro
              </label>
            )}
            <button
              className="primary-button"
              disabled={
                busy ||
                (challenge.stage === 'ENROLL' && !details) ||
                (challenge.stage === 'RECOVERY' && !saved)
              }
            >
              {busy
                ? 'Verificando…'
                : challenge.stage === 'ENROLL'
                  ? 'Activar y obtener códigos'
                  : challenge.stage === 'RECOVERY'
                    ? 'Finalizar e ingresar'
                    : 'Verificar e ingresar'}
            </button>
          </form>
        )}
        {error && (
          <p role="alert" className="entry-message">
            {error}
          </p>
        )}
        <button
          className="select-button"
          type="button"
          disabled={busy}
          onClick={() => void cancel()}
        >
          Volver al ingreso
        </button>
      </section>
    </main>
  )
}
