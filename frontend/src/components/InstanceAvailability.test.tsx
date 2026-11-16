import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { InstanceAvailability } from './InstanceAvailability'
const request = vi.fn()
beforeEach(() => {
  request.mockReset()
  vi.stubGlobal('fetch', request)
})
afterEach(() => vi.unstubAllGlobals())
const response = (state: string) =>
  Response.json(
    {},
    { headers: { 'X-Alon-Service-Control': 'enabled', 'X-Alon-Service-State': state } },
  )

test('removes the operative screens when the business is disabled', async () => {
  request.mockImplementation(async () => response('SUSPENDIDO'))
  render(
    <InstanceAvailability>
      <p>Zona de ventas</p>
    </InstanceAvailability>,
  )
  expect(await screen.findByRole('heading', { name: 'Negocio inhabilitado' })).toBeTruthy()
  expect(screen.queryByText('Zona de ventas')).toBeNull()
})

test('restores the screens after the administrator reactivates the business', async () => {
  request
    .mockImplementationOnce(async () => response('BAJA'))
    .mockImplementation(async () => response('ACTIVO'))
  render(
    <InstanceAvailability>
      <p>Zona de ventas</p>
    </InstanceAvailability>,
  )
  await screen.findByRole('heading', { name: 'Negocio dado de baja' })
  fireEvent.click(screen.getByRole('button', { name: 'Comprobar estado' }))
  expect(await screen.findByText('Zona de ventas')).toBeTruthy()
})

test('keeps the central administration available', async () => {
  request.mockImplementation(async () => Response.json({ status: 'ok' }))
  render(
    <InstanceAvailability>
      <p>Administración de Alon</p>
    </InstanceAvailability>,
  )
  expect(await screen.findByText('Administración de Alon')).toBeTruthy()
  expect(screen.queryByRole('heading')).toBeNull()
})
