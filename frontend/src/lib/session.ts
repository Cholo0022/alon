// Comprueba que la respuesta del servidor contiene una sesión válida.
export type SessionUser = {
  id: number
  comercio_id: number
  usuario: string
  nombre: string
  rol: 'SUPER' | 'DUENO' | 'VENDEDOR' | 'PERSONALIZADO'
  permisos: string
  activo: boolean
}
export type AccessChallenge = {
  stage: 'ENROLL' | 'MFA' | 'RECOVERY' | 'DEVICE'
  username: string
  email?: string
  expiresAt: string
  reference?: string
  deviceStatus?: string
  recoveryCodes?: string[]
}
export type AuthStatus = {
  setupRequired: boolean
  user: SessionUser | null
  csrfToken: string
  license?: { active: boolean; managed: boolean; expiresAt: string }
  challenge?: AccessChallenge | Record<string, never>
}

/** Comprueba la estructura del usuario recibido antes de tratar una respuesta desconocida como una sesión autenticada. */
export function isSessionUser(value: unknown): value is SessionUser {
  if (!value || typeof value !== 'object') return false
  const row = value as Record<string, unknown>
  return (
    typeof row.id === 'number' &&
    typeof row.comercio_id === 'number' &&
    typeof row.usuario === 'string' &&
    typeof row.nombre === 'string' &&
    ['SUPER', 'DUENO', 'VENDEDOR', 'PERSONALIZADO'].includes(String(row.rol)) &&
    typeof row.permisos === 'string' &&
    typeof row.activo === 'boolean'
  )
}
