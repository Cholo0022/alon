import { expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { LicenseExpiryNotice } from './LicenseExpiryNotice'
import { MainApp } from '../views/AppView'
import { navigation } from '../lib/navigation'
const license = {
  active: true,
  managed: true,
  expiresAt: '2026-11-14T23:59:00',
  expirationDate: '2026-11-14',
  daysRemaining: 10,
  expiringSoon: true,
}
it('shows expiry date and remaining days and updates after renewal', () => {
  const { rerender } = render(<LicenseExpiryNotice license={license} />)
  expect(screen.getByRole('status').textContent).toContain('Faltan 10 días')
  expect(screen.getByRole('status').textContent).toContain('Vencimiento: 14/11/2026')
  rerender(<LicenseExpiryNotice license={{ ...license, expiringSoon: false, daysRemaining: 40 }} />)
  expect(screen.queryByRole('status')).toBeNull()
})
it.each([0, 1])('shows an accurate message with %s days left', (days) => {
  render(<LicenseExpiryNotice license={{ ...license, daysRemaining: days }} />)
  expect(screen.getByRole('status').textContent).toContain(days === 0 ? 'Vence hoy' : 'Falta 1 día')
})
it('does not warn for unmanaged or inactive licenses', () => {
  const { rerender } = render(<LicenseExpiryNotice license={{ ...license, managed: false }} />)
  expect(screen.queryByRole('status')).toBeNull()
  rerender(<LicenseExpiryNotice license={{ ...license, active: false }} />)
  expect(screen.queryByRole('status')).toBeNull()
})
it('shows the notice in the app and removes accounting navigation and fiscal requests', async () => {
  const fetch = vi.fn(async (_url: string) => Response.json([]))
  vi.stubGlobal('fetch', fetch)
  render(
    <MainApp
      user={{
        id: 2,
        comercio_id: 1,
        usuario: 'gerente',
        nombre: 'Gerente',
        rol: 'DUENO',
        permisos: 'inicio,contabilidad,ventas',
        activo: true,
      }}
      onLogout={() => {}}
      license={license}
    />,
  )
  expect(screen.getByRole('status', { name: 'Vencimiento de licencia' })).toBeTruthy()
  expect(navigation.some((item) => item.id === 'contabilidad')).toBe(false)
  expect(screen.queryByRole('button', { name: 'Contabilidad' })).toBeNull()
  await waitFor(() => expect(fetch).toHaveBeenCalled())
  expect(fetch.mock.calls.some((args) => String(args[0]).includes('/accounting/'))).toBe(false)
})
