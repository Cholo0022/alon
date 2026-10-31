import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { AuthScreen } from './AppView'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../lib/http', () => ({ apiFetch: request }))
beforeEach(() => {
  request.mockReset()
})

/** Impide mezclar los campos de acceso con contraseña con la solicitud exclusiva del autenticador. */
test('superuser submits only the authenticator code and accepts the complete session', async () => {
  const status = {
    setupRequired: false,
    user: {
      id: 1,
      comercio_id: 1,
      usuario: 'alon',
      nombre: 'Alon',
      rol: 'SUPER',
      permisos: '',
      activo: true,
    },
    csrfToken: 'test',
    challenge: {},
    license: { active: true },
  }
  request.mockImplementation(async (_path: string, options: RequestInit) => {
    const payload = JSON.parse(String(options.body))
    if (Object.keys(payload).some((key) => key !== 'code'))
      return Response.json({ error: 'Campos inesperados' }, { status: 400 })
    return Response.json(status)
  })
  const authenticated = vi.fn()
  render(<AuthScreen setup={false} onAuthenticated={authenticated} onChallenge={vi.fn()} />)
  fireEvent.click(
    screen.getByRole('button', { name: 'Ingresar como alon con Google Authenticator' }),
  )
  fireEvent.change(screen.getByLabelText('Código de Google Authenticator o de recuperación'), {
    target: { value: '123456' },
  })
  fireEvent.click(screen.getByRole('button', { name: /^Ingresar$/ }))
  await waitFor(() => expect(authenticated).toHaveBeenCalledWith(status))
  expect(request.mock.calls[0][0]).toBe('/api/v1/auth/super-login')
  expect(JSON.parse(request.mock.calls[0][1].body)).toEqual({ code: '123456' })
})

/** Mantiene el ingreso del gerente compatible con el contrato estricto de credenciales. */
test('password sign-in never includes an authenticator field', async () => {
  const status = {
    setupRequired: false,
    user: {
      id: 2,
      comercio_id: 1,
      usuario: 'gerente',
      nombre: 'Gerente',
      rol: 'DUENO',
      permisos: 'ventas',
      activo: true,
    },
    csrfToken: 'test',
    challenge: {},
    license: { active: true },
  }
  request.mockResolvedValue(Response.json(status))
  const authenticated = vi.fn()
  render(<AuthScreen setup={false} onAuthenticated={authenticated} onChallenge={vi.fn()} />)
  fireEvent.change(screen.getByLabelText('Usuario', { exact: true }), {
    target: { value: 'gerente' },
  })
  fireEvent.change(screen.getByLabelText('Contraseña', { exact: true }), {
    target: { value: 'Una contraseña de prueba' },
  })
  fireEvent.click(screen.getByRole('button', { name: /^Ingresar$/ }))
  await waitFor(() => expect(authenticated).toHaveBeenCalledWith(status))
  expect(Object.keys(JSON.parse(request.mock.calls[0][1].body)).sort()).toEqual([
    'name',
    'password',
    'storeCode',
    'username',
  ])
})
