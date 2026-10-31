import { useEffect, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { navigation } from '../lib/navigation'
import { formatDateTimeDisplay as date } from '../lib/format'

type Manager = {
  id: number
  usuario: string
  nombre: string
  security_email: string
  permisos: string
  activo: boolean
}
type License = {
  id: string
  meses: number
  creado_en: string
  usada_en: string | null
  revocada: boolean
}
type Platform = {
  managers: Manager[]
  licenses: License[]
  business: { nombre: string; licencia_hasta: string | null }
}
type Issued = { key: string; months: number; activateBefore: string }
const options = [
  ...navigation.filter((item) => item.id !== 'administracion'),
  { id: 'costos', label: 'Ver y modificar costos' },
  { id: 'descuentos', label: 'Autorizar descuentos' },
]

/** Muestra la vigencia y permite activar claves únicamente al superusuario. */
export function LicenseActivation() {
  const [key, setKey] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [expiration, setExpiration] = useState('')
  const [isSuper, setIsSuper] = useState(false)
  useEffect(() => {
    const controller = new AbortController()
    void apiFetch('/api/v1/auth/status', { signal: controller.signal })
      .then((response) => response.json())
      .then((body) => {
        if (!controller.signal.aborted) {
          setExpiration(body.license?.expiresAt ?? '')
          setIsSuper(body.user?.rol === 'SUPER')
        }
      })
      .catch(() => undefined)
    return () => controller.abort()
  }, [])
  /** Consume la clave una sola vez y solicita la vigencia confirmada por el servidor. */
  const activate = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch('/api/v1/super/licenses/activate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ key }),
      })
      const result = await response.json()
      if (!response.ok) throw new Error(result.error ?? 'No se pudo activar la licencia.')
      setKey('')
      setExpiration(result.expiresAt)
      setMessage('Licencia activada para todo el negocio.')
      window.dispatchEvent(new Event('alon:refresh'))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="entry-panel platform-panel">
      <h2>Licencia del negocio</h2>
      <p>
        {expiration
          ? 'Vigencia hasta: ' + date(expiration)
          : 'Consultá a alon para activar o renovar la licencia.'}
      </p>
      {isSuper ? (
        <form className="entry-form" onSubmit={activate}>
          <label>
            Clave de licencia
            <input
              value={key}
              onChange={(event) => setKey(event.target.value)}
              autoComplete="off"
              maxLength={80}
              required
            />
          </label>
          <button className="primary-button" disabled={busy}>
            Activar licencia
          </button>
        </form>
      ) : (
        <p>Solo el superusuario alon puede activar o renovar la licencia.</p>
      )}
      {message && <p role="status">{message}</p>}
    </section>
  )
}

/** Panel exclusivo de alon para crear gerentes, limitar sus accesos y emitir claves de licencia. */
export function PlatformPage() {
  const [data, setData] = useState<Platform | null>(null)
  const [revision, setRevision] = useState(0)
  const [editing, setEditing] = useState<number | null>(null)
  const [username, setUsername] = useState('')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [permissions, setPermissions] = useState<string[]>([
    'inicio',
    'ventas',
    'articulos',
    'compras',
    'caja',
  ])
  const [months, setMonths] = useState(1)
  const [issued, setIssued] = useState<Issued | null>(null)
  const [access, setAccess] = useState<{ username: string; password: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    void apiFetch('/api/v1/super/dashboard', { signal: controller.signal })
      .then(async (response) => {
        const result = await response.json()
        if (!response.ok) throw new Error(result.error ?? 'No se pudo consultar el panel.')
        if (!controller.signal.aborted) setData(result)
      })
      .catch((error) => {
        if (!controller.signal.aborted) setMessage(error.message)
      })
    return () => controller.abort()
  }, [revision])
  /** Envía acciones exclusivas del superusuario y conserva los rechazos del servidor. */
  const action = async (path: string, payload?: object, method = 'POST') => {
    if (busy) return null
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch('/api/v1/super/' + path, {
        method,
        ...(payload
          ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }
          : {}),
      })
      const result = await response.json()
      if (!response.ok) throw new Error(result.error ?? 'No se pudo completar la operación.')
      return result
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar.')
      return null
    } finally {
      setBusy(false)
    }
  }
  /** Obtiene una contraseña aleatoria sin recuperar claves guardadas. */
  const generatePassword = async () => {
    const result = await action('password')
    if (result) setPassword(result.password)
  }
  /** Guarda el gerente con sus permisos exactos y muestra la licencia nueva una sola vez. */
  const save = async (event: FormEvent) => {
    event.preventDefault()
    const result = await action(
      editing ? 'managers/' + editing : 'managers',
      { username, name, email, password, permissions, ...(!editing ? { months } : {}) },
      editing ? 'PUT' : 'POST',
    )
    if (!result) return
    if (result.license) setIssued(result.license)
    if (password) setAccess({ username, password })
    setPassword('')
    setRevision((value) => value + 1)
    setMessage(
      editing
        ? 'Gerente actualizado. Sus sesiones anteriores se cerrarán.'
        : 'Gerente creado. Guardá su contraseña y activá la licencia desde este panel.',
    )
    setEditing(null)
    setUsername('')
    setName('')
    setEmail('')
  }
  /** Carga los datos del gerente sin consultar su contraseña existente. */
  const edit = (manager: Manager) => {
    setEditing(manager.id)
    setUsername(manager.usuario)
    setName(manager.nombre)
    setEmail(manager.security_email)
    setPassword('')
    setPermissions(manager.permisos.split(',').filter(Boolean))
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  /** Emite una renovación que se aplica únicamente al activar la clave. */
  const issue = async () => {
    const result = await action('licenses', { months })
    if (result) {
      setIssued(result)
      setRevision((value) => value + 1)
    }
  }
  /** Activa la clave emitida mediante una ruta exclusiva del superusuario. */
  const activateIssued = async () => {
    if (!issued) return
    const result = await action('licenses/activate', { key: issued.key })
    if (result) {
      setIssued(null)
      setRevision((value) => value + 1)
      setMessage('Licencia activada para todo el negocio hasta ' + date(result.expiresAt))
      window.dispatchEvent(new Event('alon:refresh'))
    }
  }
  return (
    <>
      <section className="entry-panel platform-panel">
        <span className="alon-tag">SUPERUSUARIO · ALON</span>
        <h2>{editing ? 'Modificar gerente' : 'Crear gerente'}</h2>
        <p>Elegí su contraseña, correo y las partes del sistema que podrá ver y usar.</p>
        <form className="entry-form" onSubmit={save}>
          <label>
            Usuario del gerente
            <input
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              minLength={3}
              maxLength={80}
              autoComplete="off"
              required
            />
          </label>
          <label>
            Nombre
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={160}
              required
            />
          </label>
          <label>
            Correo del gerente
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              maxLength={254}
              required
            />
          </label>
          <label>
            {editing ? 'Nueva contraseña (opcional)' : 'Contraseña del gerente'}
            <input
              type="text"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              minLength={12}
              maxLength={128}
              autoComplete="new-password"
              required={!editing}
            />
          </label>
          <button
            type="button"
            className="select-button"
            disabled={busy}
            onClick={() => void generatePassword()}
          >
            Generar contraseña segura
          </button>
          {!editing && (
            <label>
              Meses de licencia
              <input
                type="number"
                min={1}
                max={120}
                value={months}
                onChange={(event) => setMonths(Number(event.target.value))}
                required
              />
            </label>
          )}
          <fieldset className="permission-choices">
            <legend>Secciones y funciones del gerente</legend>
            {options.map((option) => (
              <label key={option.id}>
                <input
                  type="checkbox"
                  checked={permissions.includes(option.id)}
                  onChange={(event) =>
                    setPermissions((current) =>
                      event.target.checked
                        ? [...current, option.id]
                        : current.filter((id) => id !== option.id),
                    )
                  }
                />
                {option.label}
              </label>
            ))}
          </fieldset>
          <button className="primary-button" disabled={busy || !permissions.length}>
            {editing ? 'Guardar gerente' : 'Crear gerente y clave de licencia'}
          </button>
          {editing && (
            <button
              className="select-button"
              type="button"
              onClick={() => {
                setEditing(null)
                setUsername('')
                setName('')
                setEmail('')
                setPassword('')
              }}
            >
              Cancelar edición
            </button>
          )}
        </form>
      </section>
      {message && (
        <p className="entry-message" role="status">
          {message}
        </p>
      )}
      {access && (
        <section className="entry-panel platform-panel">
          <h2>Acceso del gerente</h2>
          <p>
            Usuario: <strong>{access.username}</strong>. Guardá la contraseña ahora; no se puede
            recuperar después.
          </p>
          <code className="security-secret">{access.password}</code>
          <a
            className="security-download"
            download="alon-acceso-gerente.txt"
            href={
              'data:text/plain;charset=utf-8,' +
              encodeURIComponent(
                'Acceso del gerente Alon\nUsuario: ' +
                  access.username +
                  '\nContraseña: ' +
                  access.password,
              )
            }
          >
            Descargar acceso del gerente
          </a>
          <button className="select-button" type="button" onClick={() => setAccess(null)}>
            Ya guardé el acceso
          </button>
        </section>
      )}
      {issued && (
        <section className="entry-panel platform-panel">
          <h2>Clave de licencia · {issued.months} meses</h2>
          <button
            className="primary-button"
            type="button"
            disabled={busy}
            onClick={() => void activateIssued()}
          >
            Activar esta licencia
          </button>
          <p>
            Guardala ahora: se muestra una sola vez. Sirve para todo el negocio y anula claves
            anteriores pendientes.
          </p>
          <code className="security-secret">{issued.key}</code>
          <p>Activar antes de {date(issued.activateBefore)}.</p>
          <a
            className="security-download"
            download="alon-licencia.txt"
            href={
              'data:text/plain;charset=utf-8,' +
              encodeURIComponent(
                'Licencia Alon · ' +
                  issued.months +
                  ' meses\n' +
                  issued.key +
                  '\nActivar antes de ' +
                  issued.activateBefore,
              )
            }
          >
            Descargar clave de licencia
          </a>
          <button type="button" className="select-button" onClick={() => setIssued(null)}>
            Ya guardé la clave
          </button>
        </section>
      )}
      <section className="entry-panel platform-panel">
        <h2>Gerentes</h2>
        {data?.managers.map((manager) => (
          <div className="entry-heading" key={manager.id}>
            <div>
              <strong>
                {manager.nombre} · {manager.usuario}
              </strong>
              <p>
                {manager.security_email} · {manager.activo ? 'Activo' : 'Inactivo'}
              </p>
            </div>
            <button type="button" className="select-button" onClick={() => edit(manager)}>
              Modificar accesos y cuenta
            </button>
          </div>
        ))}
        {data && !data.managers.length && <p>Todavía no creaste un gerente.</p>}
      </section>
      <section className="entry-panel platform-panel">
        <h2>Renovar licencia del negocio</h2>
        <p>
          {data?.business.nombre}.{' '}
          {data?.business.licencia_hasta
            ? 'Vigencia hasta ' + date(data.business.licencia_hasta)
            : 'Sin licencia activada.'}
        </p>
        <p>
          Solo alon puede activar esta clave. La clave suma meses calendario desde el vencimiento
          actual si sigue vigente, o desde su activación si ya venció. No cambia la contraseña del
          gerente.
        </p>
        <label>
          Meses a otorgar
          <input
            type="number"
            min={1}
            max={120}
            value={months}
            onChange={(event) => setMonths(Number(event.target.value))}
          />
        </label>
        <button
          type="button"
          className="primary-button"
          disabled={busy || months < 1 || months > 120}
          onClick={() => void issue()}
        >
          Generar clave de licencia
        </button>
        <h3>Historial de licencias</h3>
        {data?.licenses.map((license) => (
          <p key={license.id}>
            {license.meses} meses · {date(license.creado_en)} ·{' '}
            {license.usada_en
              ? 'Activada'
              : license.revocada
                ? 'Anulada'
                : 'Pendiente de activación'}
          </p>
        ))}
      </section>
    </>
  )
}
