import { render, screen } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { PlatformPage, LicenseActivation } from './PlatformPage'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../lib/http', () => ({ apiFetch: request }))
beforeEach(() => {
  localStorage.clear()
  request.mockReset().mockResolvedValue(Response.json({ businesses: [], movements: [] }))
})
test('general administration contains clients without separate manager or license forms', async () => {
  render(<PlatformPage />)
  expect(await screen.findByRole('heading', { name: 'Mis clientes Alon' })).toBeTruthy()
  expect(screen.queryByLabelText('Usuario del gerente')).toBeNull()
  expect(screen.queryByText(/Renovar licencia/)).toBeNull()
  expect(
    request.mock.calls.every(([path]) => !path.includes('licenses') && !path.includes('dashboard')),
  ).toBe(true)
})
test('legacy activation no longer offers license keys', () => {
  render(<LicenseActivation />)
  expect(screen.getByRole('heading', { name: 'Estado del servicio' })).toBeTruthy()
  expect(screen.queryByRole('button')).toBeNull()
})
