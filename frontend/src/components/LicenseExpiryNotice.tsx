import type { AuthStatus } from '../lib/session'
import { formatDateDisplay } from '../lib/format'

export function LicenseExpiryNotice({ license }: { license?: AuthStatus['license'] }) {
  if (!license?.managed || !license.active || !license.expiringSoon) return null
  const days = license.daysRemaining ?? 0
  return (
    <aside
      className="notice license-expiry-notice"
      role="status"
      aria-label="Vencimiento de licencia"
    >
      <div>
        <strong>Tu licencia está por vencer</strong>
        <p>
          {days === 0 ? 'Vence hoy' : days === 1 ? 'Falta 1 día' : 'Faltan ' + days + ' días'} ·
          Vencimiento: {formatDateDisplay(license.expirationDate ?? license.expiresAt.slice(0, 10))}
          .
        </p>
        <p>Contactá a alon para renovar la licencia y continuar usando la aplicación.</p>
      </div>
    </aside>
  )
}
