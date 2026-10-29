import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StorePage } from './StorePage'
import { apiFetch } from '../lib/http'

vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
let cashOpen = true
const fetchMock = vi.mocked(apiFetch)
beforeEach(() => {
  cashOpen = true
  fetchMock.mockReset()
  fetchMock.mockImplementation(async (url) =>
    Response.json(
      url.startsWith('/api/v1/demo/store-orders/7')
        ? {
            id: 7,
            nombre_cliente: 'Cliente de prueba',
            contacto: '1100000000',
            estado: 'PENDIENTE_CONFIRMACION',
            total_estimado: 200,
            creado_en: '2026-10-29 10:00:00',
            medio_pago: null,
            venta_id: null,
            cash_open: cashOpen,
            lines: [
              {
                id: 1,
                nombre_articulo: 'Artículo de prueba',
                cantidad: 1,
                precio_unitario: 200,
                incluido: true,
                stock: 5,
                estado_articulo: 'ACTIVO',
                unidad: 'UNIDAD',
              },
            ],
          }
        : url.startsWith('/api/v1/operations/')
          ? { ok: true }
          : [{ id: 7, nombre_cliente: 'Cliente de prueba', estado: 'PENDIENTE_CONFIRMACION' }],
    ),
  )
})

it('blocks confirmation while the cash register is closed', async () => {
  cashOpen = false
  const user = userEvent.setup()
  render(<StorePage />)
  await user.click(await screen.findByRole('button', { name: 'Ver pedido' }))
  await screen.findByText(/La caja de hoy está cerrada/)
  await user.selectOptions(
    screen.getByRole('combobox', { name: 'Medio de pago del pedido' }),
    'EFECTIVO',
  )
  expect(screen.getByRole('button', { name: 'Confirmar venta web' }).hasAttribute('disabled')).toBe(
    true,
  )
  expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/approve'))).toBe(false)
})

it('moves to and focuses the selected order after its detail loads', async () => {
  const scroll = vi.fn()
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
    configurable: true,
    value: scroll,
  })
  const user = userEvent.setup()
  render(<StorePage />)
  await user.click(await screen.findByRole('button', { name: 'Ver pedido' }))
  await screen.findByRole('button', { name: 'Confirmar venta web' })
  await waitFor(() => expect(scroll).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' }))
  expect(document.activeElement).toBe(screen.getByRole('region', { name: 'Pedido #7' }))
})

it('requires a payment choice and submits it with the approval', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  const user = userEvent.setup()
  render(<StorePage />)
  await user.click(await screen.findByRole('button', { name: 'Ver pedido' }))
  const approve = await screen.findByRole('button', { name: 'Confirmar venta web' })
  expect(approve.hasAttribute('disabled')).toBe(true)
  await user.selectOptions(
    screen.getByRole('combobox', { name: 'Medio de pago del pedido' }),
    'TRANSFERENCIA',
  )
  await user.click(approve)
  await waitFor(() =>
    expect(
      fetchMock.mock.calls.some(
        ([url, options]) =>
          url.endsWith('/approve') &&
          JSON.parse(String(options?.body)).paymentMethod === 'TRANSFERENCIA',
      ),
    ).toBe(true),
  )
})
