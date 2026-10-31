// Permite al dueño administrar los usuarios y sus permisos.
import { useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { textValue } from '../lib/format'
import { navigation } from '../lib/navigation'
import { useApiRows, type ApiRow } from '../hooks/useApiRows'
import { Pagination } from '../components/Pagination'
import { ApiTable } from '../components/ApiTable'

/** Permite administrar usuarios, roles, permisos y estados de acceso del comercio. */
export function UsersPage() {
  const users = useApiRows('/api/v1/auth/users')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [role, setRole] = useState('VENDEDOR')
  const [permissions, setPermissions] = useState<string[]>(['ventas', 'clientes', 'caja'])
  const [editing, setEditing] = useState<ApiRow | null>(null)
  const [editRole, setEditRole] = useState('VENDEDOR')
  const [editPermissions, setEditPermissions] = useState<string[]>(['ventas', 'clientes', 'caja'])
  const accessOptions = [
    ...navigation.filter((item) => item.id !== 'usuarios' && item.id !== 'negocio'),
    { id: 'descuentos', label: 'Autorizar descuentos' },
    { id: 'costos', label: 'Ver y modificar costos' },
  ].map(({ id, label }) => ({ id, label }))
  /** Envía el alta del usuario con su rol y permisos y limpia el formulario cuando el servidor lo crea. */
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const formElement = event.currentTarget
    const values = new FormData(formElement)
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch('/api/v1/auth/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: values.get('username'),
          password: values.get('password'),
          role,
          permissions,
        }),
      })
      const body = (await response.json().catch(() => ({}))) as Record<string, unknown>
      if (!response.ok)
        throw new Error(String(body.message ?? body.error ?? 'No se pudo crear el usuario.'))
      formElement.reset()
      window.dispatchEvent(new Event('alon:refresh'))
      setMessage('Usuario creado correctamente.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.')
    } finally {
      setBusy(false)
    }
  }
  /** Invierte el estado de acceso del usuario en el servidor y actualiza el listado si se confirma. */
  const toggleActive = async (id: unknown, active: boolean) => {
    const response = await apiFetch(`/api/v1/auth/users/${id}/active`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ active: !active }),
    })
    if (response.ok) window.dispatchEvent(new Event('alon:refresh'))
  }
  /** Carga los datos y permisos correspondientes al rol del usuario elegido para preparar su edición. */
  const startEditing = (user: ApiRow) => {
    const currentRole = textValue(user.rol)
    setEditing(user)
    setEditRole(currentRole)
    setEditPermissions(
      currentRole === 'DUENO'
        ? accessOptions.map((option) => option.id)
        : currentRole === 'VENDEDOR'
          ? ['ventas', 'clientes', 'caja']
          : textValue(user.permisos).split(',').filter(Boolean),
    )
  }
  /** Envía los cambios de usuario, contraseña opcional, rol y permisos e informa si se guardaron. */
  const saveEdit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!editing) return
    const values = new FormData(event.currentTarget)
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch(`/api/v1/auth/users/${editing.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: values.get('username'),
          password: values.get('password'),
          role: editRole,
          permissions: editPermissions,
        }),
      })
      const body = (await response.json().catch(() => ({}))) as Record<string, unknown>
      if (!response.ok)
        throw new Error(String(body.message ?? body.error ?? 'No se pudieron guardar los cambios.'))
      setEditing(null)
      window.dispatchEvent(new Event('alon:refresh'))
      setMessage('Usuario actualizado.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      <section className="entry-panel">
        <div className="entry-heading">
          <div>
            <span className="alon-tag">ADMINISTRACIÓN</span>
            <strong>Crear usuario</strong>
          </div>
        </div>
        <form className="entry-form" onSubmit={submit}>
          <label>
            Usuario
            <input name="username" minLength={3} required />
          </label>
          <label>
            Contraseña
            <input
              name="password"
              type="password"
              minLength={12}
              maxLength={128}
              autoComplete="new-password"
              required
            />
          </label>
          <label>
            Perfil
            <select
              value={role}
              onChange={(event) => {
                const next = event.target.value
                setRole(next)
                setPermissions(
                  next === 'VENDEDOR'
                    ? ['ventas', 'clientes', 'caja']
                    : next === 'DUENO'
                      ? accessOptions.map((option) => option.id)
                      : [],
                )
              }}
            >
              <option value="VENDEDOR">Vendedor</option>
              <option value="DUENO">Dueño</option>
              <option value="PERSONALIZADO">Accesos personalizados</option>
            </select>
          </label>
          {role !== 'VENDEDOR' && role !== 'DUENO' && (
            <fieldset className="permission-choices">
              <legend>Secciones permitidas</legend>
              {accessOptions.map(({ id, label }) => (
                <label key={id}>
                  <input
                    type="checkbox"
                    checked={permissions.includes(id)}
                    onChange={(event) =>
                      setPermissions((current) =>
                        event.target.checked
                          ? [...current, id]
                          : current.filter((permission) => permission !== id),
                      )
                    }
                  />
                  {label}
                </label>
              ))}
            </fieldset>
          )}
          <div className="entry-actions">
            <span>La contraseña debe tener entre 12 y 128 caracteres.</span>
            <button
              className="primary-button"
              type="submit"
              disabled={busy || (role === 'PERSONALIZADO' && permissions.length === 0)}
            >
              {busy ? 'Guardando…' : 'Crear usuario'}
            </button>
          </div>
        </form>
        {message && (
          <p className="entry-message" role="status">
            {message}
          </p>
        )}
      </section>
      <ApiTable
        title="Usuarios del comercio"
        endpoint="/api/v1/auth/users"
        columns={[
          { key: 'nombre', label: 'NOMBRE' },
          { key: 'usuario', label: 'USUARIO' },
          { key: 'rol', label: 'PERFIL' },
          { key: 'permisos', label: 'ACCESOS' },
          { key: 'activo', label: 'ACTIVO' },
        ]}
      />
      {editing && (
        <section className="entry-panel">
          <div className="entry-heading">
            <div>
              <span className="alon-tag">EDITAR ACCESO</span>
              <strong>{textValue(editing.usuario)}</strong>
            </div>
            <button className="select-button" type="button" onClick={() => setEditing(null)}>
              Cancelar
            </button>
          </div>
          <form className="entry-form" onSubmit={saveEdit}>
            <label>
              Usuario
              <input
                name="username"
                defaultValue={textValue(editing.usuario)}
                minLength={3}
                required
              />
            </label>
            <label>
              Nueva contraseña
              <input
                name="password"
                type="password"
                minLength={12}
                maxLength={128}
                autoComplete="new-password"
                placeholder="Dejar vacía para mantenerla"
              />
            </label>
            <label>
              Perfil
              <select
                value={editRole}
                onChange={(event) => {
                  const next = event.target.value
                  setEditRole(next)
                  setEditPermissions(
                    next === 'VENDEDOR'
                      ? ['ventas', 'clientes', 'caja']
                      : next === 'DUENO'
                        ? accessOptions.map((option) => option.id)
                        : [],
                  )
                }}
              >
                <option value="VENDEDOR">Vendedor</option>
                <option value="DUENO">Dueño</option>
                <option value="PERSONALIZADO">Accesos personalizados</option>
              </select>
            </label>
            {editRole !== 'VENDEDOR' && editRole !== 'DUENO' && (
              <fieldset className="permission-choices">
                <legend>Secciones permitidas</legend>
                {accessOptions.map(({ id, label }) => (
                  <label key={id}>
                    <input
                      type="checkbox"
                      checked={editPermissions.includes(id)}
                      onChange={(event) =>
                        setEditPermissions((current) =>
                          event.target.checked
                            ? [...current, id]
                            : current.filter((permission) => permission !== id),
                        )
                      }
                    />
                    {label}
                  </label>
                ))}
              </fieldset>
            )}
            <div className="entry-actions">
              <span>Solo se modifica la contraseña si escribís una nueva.</span>
              <button
                className="primary-button"
                type="submit"
                disabled={busy || (editRole === 'PERSONALIZADO' && editPermissions.length === 0)}
              >
                {busy ? 'Guardando…' : 'Guardar cambios'}
              </button>
            </div>
          </form>
        </section>
      )}
      <section className="panel user-actions-list">
        <div className="panel-heading">
          <div>
            <h2>Administrar accesos</h2>
            <p>Editá usuario, contraseña o permisos. También podés desactivar el acceso.</p>
          </div>
        </div>
        {users.map((user) => (
          <div className="user-access-row" key={String(user.id)}>
            <span>
              <strong>{textValue(user.usuario)}</strong>
              <small>
                {textValue(user.rol)} ·{' '}
                {user.activo === true || user.activo === 1 || user.activo === '1'
                  ? 'Activo'
                  : 'Desactivado'}
              </small>
            </span>
            <div className="user-row-actions">
              <button className="select-button" type="button" onClick={() => startEditing(user)}>
                Editar
              </button>
              <button
                className="select-button"
                type="button"
                onClick={() =>
                  void toggleActive(
                    user.id,
                    user.activo === true || user.activo === 1 || user.activo === '1',
                  ).catch(() => undefined)
                }
              >
                {user.activo === true || user.activo === 1 || user.activo === '1'
                  ? 'Desactivar'
                  : 'Activar'}
              </button>
            </div>
          </div>
        ))}
        <Pagination rows={users} label="usuarios para administrar" />
      </section>
    </>
  )
}
