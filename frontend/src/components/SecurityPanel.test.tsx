import { render, screen, waitFor } from '@testing-library/react'
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
