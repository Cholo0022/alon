import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { SecurityPanel } from './SecurityPanel'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../lib/http', () => ({ apiFetch: request }))
beforeEach(() => {
  request.mockReset()
})
const data = {
  username: 'gerente',
  email: 'gerente@example.test',
  mailConfigured: false,
  mailPending: 0,
  mailFailed: 0,
  recoveryRemaining: 0,
  requests: [],
  browsers: [],
  events: [],
}

/** El gerente administra las solicitudes sin recibir formularios que le exijan un autenticador. */
test('manager sees password access and browser requests without authenticator controls', async () => {
  request.mockImplementation(async () => Response.json({ ...data, authenticatorRequired: false }))
  render(<SecurityPanel />)
  expect(
    await screen.findByText(/Ingreso con el usuario y la contraseña creados por alon/),
  ).toBeTruthy()
  expect(screen.getByRole('heading', { name: 'Solicitudes de ingreso' })).toBeTruthy()
  expect(screen.queryByLabelText('Código del autenticador o de recuperación')).toBeNull()
  expect(screen.queryByRole('button', { name: 'Cambiar autenticador' })).toBeNull()
})

/** Conserva los controles del autenticador de la cuenta alon. */
test('superuser retains authenticator and recovery management', async () => {
  request.mockImplementation(async () =>
    Response.json({
      ...data,
      username: 'alon',
      authenticatorRequired: true,
      recoveryRemaining: 10,
    }),
  )
  render(<SecurityPanel />)
  await waitFor(() =>
    expect(screen.getByLabelText('Código del autenticador o de recuperación')).toBeTruthy(),
  )
  expect(screen.getByRole('button', { name: 'Cambiar autenticador' })).toBeTruthy()
})

test('allows the business manager to permit a new user browser without an authenticator', async () => {
  let permitted = false
  request.mockImplementation(async (path: string) => {
    if (path.endsWith('/browsers/request-one/approve')) {
      permitted = true
      return Response.json({ ok: true })
    }
    return Response.json({
      ...data,
      authenticatorRequired: false,
      requests: permitted
        ? []
        : [
            {
              id: 'request-one',
              usuario: 'vendedor1',
              referencia: 'ABCD1234',
              ip: '127.0.0.1',
              navegador: 'Chrome',
              creado_en: '2026-11-14T10:00:00',
              vence_en: '2026-11-14T10:10:00',
            },
          ],
    })
  })
  render(<SecurityPanel />)
  fireEvent.click(await screen.findByRole('button', { name: 'Permitir' }))
  await screen.findByText('No hay solicitudes pendientes.')
  expect(request.mock.calls.find(([path]) => path.endsWith('/approve'))).toMatchObject([
    '/api/v1/auth/security/browsers/request-one/approve',
    { method: 'POST' },
  ])
  expect(screen.queryByLabelText('Código del autenticador o de recuperación')).toBeNull()
})
