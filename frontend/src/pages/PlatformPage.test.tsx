import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { PlatformPage, LicenseActivation } from './PlatformPage'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../lib/http', () => ({ apiFetch: request }))
beforeEach(() => {
  request.mockReset()
})

/** Comprueba la selección de permisos y la activación exclusiva desde el panel de alon. */
test('alon creates a manager with selected modules and activates its license', async () => {
  request.mockImplementation(async (path: string) => {
    if (path.endsWith('dashboard'))
      return Response.json({
        managers: [],
        licenses: [],
        business: { nombre: 'Prueba', licencia_hasta: null },
      })
    if (path.endsWith('password')) return Response.json({ password: 'ClaveAleatoria-Segura-12345' })
    if (path.endsWith('managers'))
      return Response.json({
        managerId: 2,
        license: {
          key: 'AABBCCDD-11223344-55667788-EEFF0011',
          months: 2,
          activateBefore: '2026-11-30T12:00',
        },
      })
    return Response.json({ expiresAt: '2026-12-31T12:00' })
  })
  render(<PlatformPage />)
  fireEvent.change(screen.getByLabelText('Usuario del gerente'), { target: { value: 'gerente' } })
  fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Mi gerente' } })
  fireEvent.change(screen.getByLabelText('Correo del gerente'), {
    target: { value: 'gerente@example.test' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Generar contraseña segura' }))
  await waitFor(() =>
    expect((screen.getByLabelText('Contraseña del gerente') as HTMLInputElement).value).toBe(
      'ClaveAleatoria-Segura-12345',
    ),
  )
  fireEvent.change(screen.getByLabelText('Meses de licencia'), { target: { value: '2' } })
  fireEvent.click(screen.getByLabelText('Compras'))
  fireEvent.click(screen.getByRole('button', { name: 'Crear gerente y clave de licencia' }))
  expect(await screen.findByText('AABBCCDD-11223344-55667788-EEFF0011')).toBeTruthy()
  expect(screen.getByText('ClaveAleatoria-Segura-12345')).toBeTruthy()
  expect(screen.getByRole('link', { name: 'Descargar acceso del gerente' })).toBeTruthy()
  const call = request.mock.calls.find(([path]) => path.endsWith('/managers'))!
  const payload = JSON.parse(call[1].body)
  expect(payload.permissions).not.toContain('compras')
  expect(payload.permissions).not.toContain('administracion')
  expect(payload.months).toBe(2)
  fireEvent.click(screen.getByRole('button', { name: 'Activar esta licencia' }))
  await waitFor(() =>
    expect(request.mock.calls.some(([path]) => path === '/api/v1/super/licenses/activate')).toBe(
      true,
    ),
  )
  expect(await screen.findByText(/Licencia activada para todo el negocio hasta/)).toBeTruthy()
})

/** Evita ofrecer formularios de activación a gerentes, incluso con licencia vencida. */
test('manager can see validity but cannot activate the system', async () => {
  request.mockResolvedValue(
    Response.json({
      user: { rol: 'DUENO' },
      license: { active: false, expiresAt: '2026-10-01T12:00' },
    }),
  )
  render(<LicenseActivation />)
  expect(
    await screen.findByText('Solo el superusuario alon puede activar o renovar la licencia.'),
  ).toBeTruthy()
  expect(screen.queryByRole('button', { name: 'Activar licencia' })).toBeNull()
})

/** La edición no debe enviar meses, que pertenecen al alta y a las licencias. */
test('manager editing sends only the fields accepted by the server', async () => {
  request.mockImplementation(async (path: string, options?: RequestInit) => {
    if (path.endsWith('dashboard'))
      return Response.json({
        managers: [
          {
            id: 2,
            usuario: 'gerente',
            nombre: 'Gerente',
            security_email: 'gerente@example.test',
            permisos: 'ventas',
            activo: true,
          },
        ],
        licenses: [],
        business: { nombre: 'Prueba', licencia_hasta: null },
      })
    const payload = JSON.parse(String(options?.body))
    if ('months' in payload) return Response.json({ error: 'Campos inesperados' }, { status: 400 })
    return Response.json({ ok: true })
  })
  render(<PlatformPage />)
  fireEvent.click(await screen.findByRole('button', { name: 'Modificar accesos y cuenta' }))
  fireEvent.click(screen.getByRole('button', { name: 'Guardar gerente' }))
  expect(
    await screen.findByText('Gerente actualizado. Sus sesiones anteriores se cerrarán.'),
  ).toBeTruthy()
  const call = request.mock.calls.find(([path]) => path.endsWith('/managers/2'))!
  expect(Object.keys(JSON.parse(call[1].body)).sort()).toEqual([
    'email',
    'name',
    'password',
    'permissions',
    'username',
  ])
})
