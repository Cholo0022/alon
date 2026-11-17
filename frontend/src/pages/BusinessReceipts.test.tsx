import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { BusinessReceipts } from './BusinessReceipts'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../lib/http', () => ({ apiFetch: request }))
beforeEach(() => {
  sessionStorage.clear()
  request.mockReset()
})
const business = { id: 7, nombre: 'Comercio A', abono: '1000' }

test('starts at one month and zero discount and submits the selected months without changing service state', async () => {
  request.mockImplementation(async (_path, options) => Response.json(options?.method === 'POST' ? { id: 8 } : { receipts: [], audit: [] }))
  render(<BusinessReceipts business={business} onSaved={() => {}} />)
  expect((screen.getByLabelText('Cantidad de meses') as HTMLInputElement).value).toBe('1')
  expect((screen.getByLabelText('Bonificación (%)') as HTMLInputElement).value).toBe('0')
  fireEvent.change(screen.getByLabelText('Cantidad de meses'), { target: { value: '2' } })
  fireEvent.change(screen.getByLabelText('Bonificación (%)'), { target: { value: '10' } })
  fireEvent.click(screen.getByRole('button', { name: 'Emitir recibo y registrar cobro' }))
  await screen.findByText(/Recibo #8 registrado/)
  const command = request.mock.calls.find(([, options]) => options?.method === 'POST')!
  expect(JSON.parse(command[1].body)).toMatchObject({ months: 2, discountPercent: 10 })
  expect(command[0]).toBe('/api/v1/super/businesses/7/receipts')
  expect(request.mock.calls.some(([path]) => path.endsWith('/state'))).toBe(false)
})
test('reuses the original reference after a lost response', async () => {
  let attempts = 0
  request.mockImplementation(async (_path, options) => {
    if (options?.method !== 'POST') return Response.json({ receipts: [], audit: [] })
    if (++attempts === 1) throw new Error('Respuesta perdida')
    return Response.json({ id: 9, replayed: true })
  })
  const first = render(<BusinessReceipts business={business} onSaved={() => {}} />)
  fireEvent.click(screen.getByRole('button', { name: 'Emitir recibo y registrar cobro' }))
  await screen.findByText('Respuesta perdida')
  first.unmount()
  render(<BusinessReceipts business={business} onSaved={() => {}} />)
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar recibo pendiente' }))
  await screen.findByText(/Recibo #9 registrado/)
  const commands = request.mock.calls.filter(([, options]) => options?.method === 'POST')
  expect(commands[0][1].body).toBe(commands[1][1].body)
})
test('shows duplicate-period errors and requires a reason before voiding a receipt', async () => {
  request.mockImplementation(async (path, options) => {
    if (options?.method === 'POST') return path.endsWith('/void') ? Response.json({ ok: true }) : Response.json({ error: 'El período ya está cobrado.' }, { status: 409 })
    return Response.json({ receipts: [{ id: 8, fecha: '2026-11-16', periodo_desde: '2026-11-01', meses: 1, abono_unitario: 1000, subtotal: 1000, bonificacion: 0, total: 1000, estado: 'EMITIDO', motivo_anulacion: '' }], audit: [] })
  })
  render(<BusinessReceipts business={business} onSaved={() => {}} />)
  fireEvent.click(screen.getByRole('button', { name: 'Emitir recibo y registrar cobro' }))
  await screen.findByText('El período ya está cobrado.')
  fireEvent.click(await screen.findByRole('button', { name: 'Anular recibo #8' }))
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar anulación' }))
  expect(request.mock.calls.some(([path]) => path.endsWith('/void'))).toBe(false)
  fireEvent.change(screen.getByLabelText('Motivo de anulación'), { target: { value: 'Error de importe' } })
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar anulación' }))
  await waitFor(() => expect(request.mock.calls.some(([path, options]) => path.endsWith('/8/void') && JSON.parse(options.body).reason === 'Error de importe')).toBe(true))
})
