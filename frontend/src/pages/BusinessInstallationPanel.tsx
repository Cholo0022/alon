import { useEffect, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { navigation } from '../lib/navigation'
import { businessStates } from '../lib/businessStates'

type Installation = {
  businessState?: string
  estado: string
  error?: string
  running: boolean
  prepared?: boolean
  username?: string
  managementUrl?: string
  shopUrl?: string
}
type Business = { id: number; nombre: string; responsable: string; email: string }
const permissions = [
  ...navigation
    .filter((n) => n.id !== 'administracion')
    .map((n) => ({ id: n.id as string, label: n.label })),
  { id: 'costos', label: 'Ver y modificar costos' },
  { id: 'descuentos', label: 'Autorizar descuentos' },
]

export function BusinessInstallationPanel({
  business,
  onSaved,
  onClose,
}: {
  business: Business
  onSaved: (result: Installation) => void
  onClose: () => void
}) {
  const [installation, setInstallation] = useState<Installation | null>(null)
  const [username, setUsername] = useState('')
  const [name, setName] = useState(business.responsable)
  const [email, setEmail] = useState(business.email)
  const [password, setPassword] = useState('')
  const [months, setMonths] = useState(1)
  const selected = permissions.map((p) => p.id)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const path = `/api/v1/super/businesses/${business.id}/installation`
  const blocked = Boolean(
    installation?.businessState && !['ACTIVO', 'PRUEBA'].includes(installation.businessState),
  )
  const read = async () => {
    try {
      const response = await apiFetch(path)
      const body = await response.json()
      if (!response.ok) throw new Error(body.error ?? 'No se pudo consultar la instalación.')
      setInstallation(body)
      if (body.username) setUsername(body.username)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar.')
    }
  }
  useEffect(() => {
    const controller = new AbortController()
    void apiFetch(path, { signal: controller.signal })
      .then(async (response) => {
        const body = await response.json()
        if (!response.ok) throw new Error(body.error ?? 'No se pudo consultar la instalación.')
        if (!controller.signal.aborted) {
          setInstallation(body)
          if (body.username) setUsername(body.username)
        }
      })
      .catch((error) => {
        if (!controller.signal.aborted) setMessage(error.message)
      })
    return () => controller.abort()
  }, [path])
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setMessage('')
    try {
      const start = installation?.prepared
      const response = await apiFetch(path + (start ? '/start' : ''), {
        method: 'POST',
        retry: false,
        headers: { 'Content-Type': 'application/json' },
        body: start
          ? '{}'
          : JSON.stringify({ username, name, email, password, months, permissions: selected }),
      })
      const body = await response.json()
      if (!response.ok) throw new Error(body.error ?? 'No se pudo completar el alta.')
      setInstallation(body)
      setPassword('')
      onSaved(body)
      setMessage(
        body.running
          ? 'Negocio listo. El gerente puede ingresar con el usuario y contraseña que elegiste.'
          : 'Consultá el estado de la instalación.',
      )
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : 'No se pudo conectar. Consultá el estado antes de reintentar.',
      )
      await read()
    } finally {
      setBusy(false)
    }
  }
  return (
    <section
      className="entry-panel platform-panel"
      aria-label={`Instalación de ${business.nombre}`}
    >
      <h3>Habilitar sistema y tienda de {business.nombre}</h3>
      <p>
        Se crea una base independiente, un gerente y una período de prueba. Los enlaces son locales a esta
        computadora; publicar en Internet requiere configurar hosting y dominio.
      </p>
      {installation && (
        <p>
          Estado:{' '}
          {installation.running
            ? 'Listo para operar'
            : installation.estado === 'SIN_CREAR'
              ? 'Sin crear'
              : installation.estado === 'LISTO'
                ? 'Habilitado; sin respuesta en este momento'
                : installation.estado}
        </p>
      )}
      {installation?.error && <p role="alert">{installation.error}</p>}
      {message && <p role="status">{message}</p>}
      {busy && (
        <p role="status">
          Preparando base, gerente y sistema. Puede demorar varios minutos. No cierres esta ventana.
        </p>
      )}
      {blocked ? (
        <p>
          Sin acceso: {businessStates[installation!.businessState!]}. Para habilitarlo, cambiá su
          categoría a Activo o Prueba desde el listado.
        </p>
      ) : installation?.running ? (
        <>
          <p>
            Gerente: <strong>{installation.username}</strong>. Su contraseña se guarda protegida y
            no se puede recuperar desde el panel.
          </p>
          <p>
            <a href={installation.managementUrl} target="_blank" rel="noreferrer">
              Abrir gestión del negocio
            </a>
          </p>
          <p>
            <a href={installation.shopUrl} target="_blank" rel="noreferrer">
              Abrir tienda del negocio
            </a>
          </p>
        </>
      ) : (
        installation && (
          <form className="entry-form" onSubmit={(e) => void submit(e)}>
            {!installation.prepared && (
              <>
                <label>
                  Usuario del nuevo gerente
                  <input
                    required
                    minLength={3}
                    maxLength={80}
                    pattern="[a-zA-Z0-9_.\-]+"
                    autoComplete="off"
                    value={username}
                    readOnly={Boolean(installation.username)}
                    onChange={(e) => setUsername(e.target.value)}
                  />
                </label>
                <label>
                  Nombre del nuevo gerente
                  <input
                    required
                    maxLength={160}
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                  />
                </label>
                <label>
                  Correo del nuevo gerente
                  <input
                    type="email"
                    required
                    maxLength={254}
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                  />
                </label>
                <label>
                  Contraseña del nuevo gerente
                  <input
                    type="password"
                    required
                    minLength={4}
                    maxLength={128}
                    autoComplete="new-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                </label>
                <label>
                  Meses de prueba inicial
                  <input
                    type="number"
                    min={1}
                    max={120}
                    required
                    value={months}
                    onChange={(e) => setMonths(Number(e.target.value))}
                  />
                </label>
                <p>
                  El gerente tendrá todos los permisos de administración de su negocio. Las
                  funciones de superusuario quedan reservadas a Alon.
                </p>
                <p>
                  Guardá la contraseña que elegiste. Si reintentás un alta incompleta, se conserva
                  el gerente original y su contraseña. La período de prueba no se extiende por reintentar.
                </p>
              </>
            )}
            <button className="primary-button" disabled={busy || !selected.length}>
              {installation.prepared
                ? 'Iniciar instalación existente'
                : 'Crear base, gerente y habilitar negocio'}
            </button>
          </form>
        )
      )}
      <button type="button" className="select-button" disabled={busy} onClick={() => void read()}>
        Consultar estado
      </button>
      <button type="button" className="select-button" disabled={busy} onClick={onClose}>
        Cerrar instalación
      </button>
    </section>
  )
}
