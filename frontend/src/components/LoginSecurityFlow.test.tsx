import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { LoginSecurityFlow } from './LoginSecurityFlow'
import { apiFetch } from '../lib/http'
import type { AccessChallenge } from '../lib/session'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
const request = vi.mocked(apiFetch)
const base = { username: 'owner', email: 'owner@example.test', expiresAt: '2099-01-01T00:00:00Z' }
beforeEach(() => {
  request.mockReset()
})
afterEach(() => {
  vi.useRealTimers()
})

it('configures MFA only with a code and preserves the owner account email', async () => {
  request.mockImplementation(async (url) =>
    url.endsWith('/enroll')
      ? Response.json({ secret: 'TEST-ONLY-SECRET', qr: 'data:image/svg+xml;base64,PHN2Zy8+' })
      : Response.json({
          user: null,
          challenge: { ...base, stage: 'RECOVERY', recoveryCodes: ['TEST-RECOVERY-CODE'] },
        }),
  )
  const onState = vi.fn()
  render(<LoginSecurityFlow challenge={{ ...base, stage: 'ENROLL' }} onState={onState} />)
  await screen.findByAltText('QR para configurar el autenticador de Alon')
  expect((screen.getByLabelText('Correo de la cuenta') as HTMLInputElement).value).toBe(base.email)
  fireEvent.change(screen.getByLabelText('Código del autenticador'), {
    target: { value: '123456' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Activar y obtener códigos' }))
  await waitFor(() => expect(onState).toHaveBeenCalled())
  const call = request.mock.calls.find(([url]) => url.endsWith('/confirm'))!
  expect(JSON.parse(String(call[1]?.body))).toEqual({ code: '123456', email: base.email })
  expect(onState.mock.calls[0][0].user).toBeNull()
})
it('requires saving recovery codes before completing login', async () => {
  request.mockResolvedValue(Response.json({ user: { id: 1 }, challenge: {} }))
  const onState = vi.fn()
  render(
    <LoginSecurityFlow
      challenge={{ ...base, stage: 'RECOVERY', recoveryCodes: ['CODE-ONE', 'CODE-TWO'] }}
      onState={onState}
    />,
  )
  const finish = screen.getByRole('button', { name: 'Finalizar e ingresar' })
  expect(finish.hasAttribute('disabled')).toBe(true)
  expect(request).not.toHaveBeenCalled()
  const link = screen.getByRole('link', { name: 'Descargar códigos de recuperación' })
  expect(decodeURIComponent(link.getAttribute('href')!)).toContain('CODE-ONE\nCODE-TWO')
  fireEvent.click(screen.getByLabelText('Guardé los códigos en un lugar seguro'))
  fireEvent.click(finish)
  await waitFor(() => expect(onState).toHaveBeenCalled())
  expect(JSON.parse(String(request.mock.calls[0][1]?.body))).toMatchObject({ saved: true })
})
it('keeps employees waiting until the server approves their bound browser', async () => {
  vi.useFakeTimers()
  request.mockResolvedValue(Response.json({ user: { id: 2 }, challenge: {} }))
  const onState = vi.fn()
  render(
    <LoginSecurityFlow
      challenge={{ ...base, stage: 'DEVICE', reference: 'ABC123', deviceStatus: 'PENDIENTE' }}
      onState={onState}
    />,
  )
  expect(screen.getByRole('heading', { name: 'Esperando autorización del gerente' })).toBeTruthy()
  expect(onState).not.toHaveBeenCalled()
  await act(() => vi.advanceTimersByTimeAsync(3000))
  expect(onState).toHaveBeenCalledWith({ user: { id: 2 }, challenge: {} })
})
it('stops polling a rejected browser and allows returning to login', async () => {
  vi.useFakeTimers()
  request.mockResolvedValue(Response.json({ user: null, challenge: {} }))
  const onState = vi.fn()
  render(
    <LoginSecurityFlow
      challenge={{ ...base, stage: 'DEVICE', deviceStatus: 'RECHAZADO' } as AccessChallenge}
      onState={onState}
    />,
  )
  await act(() => vi.advanceTimersByTimeAsync(15000))
  expect(request).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Volver al ingreso' }))
  await act(() => vi.advanceTimersByTimeAsync(0))
  expect(request.mock.calls[0][0]).toBe('/api/v1/auth/security/cancel')
})
