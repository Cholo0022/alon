export type SessionUser = {
  id: number
  comercio_id: number
  usuario: string
  nombre: string
  rol: 'DUENO' | 'VENDEDOR' | 'PERSONALIZADO'
  permisos: string
  activo: boolean
}
export type AuthStatus = { setupRequired: boolean; user: SessionUser | null; csrfToken: string }

export function isSessionUser(value: unknown): value is SessionUser {
  if (!value || typeof value !== 'object') return false
  const row = value as Record<string, unknown>
  return (
    typeof row.id === 'number' &&
    typeof row.comercio_id === 'number' &&
    typeof row.usuario === 'string' &&
    typeof row.nombre === 'string' &&
    ['DUENO', 'VENDEDOR', 'PERSONALIZADO'].includes(String(row.rol)) &&
    typeof row.permisos === 'string' &&
    typeof row.activo === 'boolean'
  )
}
