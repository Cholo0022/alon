import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { BusinessInstallationPanel } from './BusinessInstallationPanel'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../lib/http', () => ({ apiFetch: request }))
const business = {
  id: 5,
  nombre: 'Las Tres L',
  responsable: 'Andrés',
  email: 'gerente@example.test',
}
beforeEach(() => request.mockReset())

test('provisions the selected business with its manager and displays verified links', async () => {
  request.mockImplementation(async (_path: string, options?: RequestInit) =>
    Response.json(
      options?.method === 'POST'
        ? {
            estado: 'LISTO',
            running: true,
            username: 'gerente',
            managementUrl: 'http://127.0.0.1:20005',
            shopUrl: 'http://127.0.0.1:20005/tienda',
          }
        : { estado: 'SIN_CREAR', running: false },
    ),
  )
  const saved = vi.fn()
  render(<BusinessInstallationPanel business={business} onSaved={saved} onClose={vi.fn()} />)
  fireEvent.change(await screen.findByLabelText('Usuario del nuevo gerente'), {
    target: { value: 'gerente' },
  })
  fireEvent.change(screen.getByLabelText('Contraseña del nuevo gerente'), {
    target: { value: 'ClaveSegura123!' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Crear base, gerente y habilitar negocio' }))
  expect(await screen.findByRole('link', { name: 'Abrir tienda del negocio' })).toHaveProperty(
    'href',
    'http://127.0.0.1:20005/tienda',
  )
  const call = request.mock.calls.find(([, options]) => options?.method === 'POST')!
  expect(call[0]).toBe('/api/v1/super/businesses/5/installation')
  expect(call[1].retry).toBe(false)
  expect(JSON.parse(call[1].body)).toMatchObject({ username: 'gerente', name: 'Andrés', months: 1 })
  expect(JSON.parse(call[1].body).permissions).not.toContain('administracion')
  expect(saved).toHaveBeenCalledOnce()
})

test('restarts an existing installation without asking for or resetting its manager password', async () => {
  request.mockImplementation(async (_path: string, options?: RequestInit) =>
    Response.json({
      estado: 'LISTO',
      prepared: true,
      running: options?.method === 'POST',
      username: 'gerente',
    }),
  )
  render(<BusinessInstallationPanel business={business} onSaved={vi.fn()} onClose={vi.fn()} />)
  fireEvent.click(await screen.findByRole('button', { name: 'Iniciar instalación existente' }))
  await waitFor(() =>
    expect(request.mock.calls.some(([path]) => path.endsWith('/installation/start'))).toBe(true),
  )
  expect(screen.queryByLabelText('Contraseña del nuevo gerente')).toBeNull()
})
