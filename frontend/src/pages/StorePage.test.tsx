import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StorePage, orderWhatsAppUrl } from './StorePage'
import { apiFetch } from '../lib/http'

vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
let cashOpen = true
let customerComment = 'Es cliente web nuevo'
const fetchMock = vi.mocked(apiFetch)
it.each([
  ['3446236311', 'https://wa.me/5493446236311'],
  ['+54 9 3446 236311', 'https://wa.me/5493446236311'],
  ['54 3446-236311', 'https://wa.me/5493446236311'],
  ['', null],
  ['12345', null],
])('builds a WhatsApp link for %s', (contact, expected) => {
  expect(orderWhatsAppUrl(contact)).toBe(expected)
})

it('offers the customer WhatsApp chat in a separate tab', async () => {
  const user = userEvent.setup()
  render(<StorePage />)
  await user.click(await screen.findByRole('button', { name: 'Ver pedido' }))
  const link = await screen.findByRole('link', {
    name: 'Chatear por WhatsApp con Cliente de prueba',
  })
  expect(link.getAttribute('href')).toBe('https://wa.me/5491100000000')
  expect(link.getAttribute('target')).toBe('_blank')
})
beforeEach(() => {
  cashOpen = true
  customerComment = 'Es cliente web nuevo'
  fetchMock.mockReset()
  fetchMock.mockImplementation(async (url) =>
    Response.json(
      url.startsWith('/api/v1/alon/store-orders/7')
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
            comentario_cliente: customerComment,
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

it.each(['Es cliente web nuevo', 'Este cliente ya ha comprado en la web'])(
  'shows the seller comment %s and offers rejection',
  async (comment) => {
    customerComment = comment
    const user = userEvent.setup()
    render(<StorePage />)
    await user.click(await screen.findByRole('button', { name: 'Ver pedido' }, { timeout: 10_000 }))
    expect(await screen.findByText(comment, {}, { timeout: 10_000 })).toBeTruthy()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await user.click(screen.getByRole('button', { name: 'Rechazar pedido' }))
    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/cancel'))).toBe(true),
    )
  },
)

it('blocks confirmation while the cash register is closed', async () => {
  cashOpen = false
  const user = userEvent.setup()
  render(<StorePage />)
  await user.click(await screen.findByRole('button', { name: 'Ver pedido' }, { timeout: 10_000 }))
  await screen.findByText(/La caja de hoy está cerrada/)
  await user.selectOptions(
    screen.getByRole('combobox', { name: 'Medio de pago del pedido' }),
    'EFECTIVO',
  )
  expect(screen.getByRole('button', { name: 'Confirmar venta web' }).hasAttribute('disabled')).toBe(
    true,
  )
  expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/approve'))).toBe(false)
  expect(screen.getByText('Abrí la caja de hoy desde Caja.')).toBeTruthy()
  expect(screen.queryByText('Seleccioná un medio de pago.')).toBeNull()
})

it('moves to and focuses the selected order after its detail loads', async () => {
  const scroll = vi.fn()
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
    configurable: true,
    value: scroll,
  })
  const user = userEvent.setup()
  render(<StorePage />)
  await user.click(await screen.findByRole('button', { name: 'Ver pedido' }, { timeout: 10_000 }))
  await screen.findByRole('button', { name: 'Confirmar venta web' })
  await waitFor(() => expect(scroll).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' }))
  expect(document.activeElement).toBe(screen.getByRole('region', { name: 'Pedido #7' }))
})

it('requires a payment choice and submits it with the approval', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  const user = userEvent.setup()
  render(<StorePage />)
  await user.click(await screen.findByRole('button', { name: 'Ver pedido' }, { timeout: 10_000 }))
  const approve = await screen.findByRole('button', { name: 'Confirmar venta web' })
  expect(approve.hasAttribute('disabled')).toBe(true)
  await user.selectOptions(
    screen.getByRole('combobox', { name: 'Medio de pago del pedido' }),
    'TRANSFERENCIA',
  )
  expect(approve.hasAttribute('disabled')).toBe(false)
  expect(screen.queryByText('Para habilitar Confirmar venta web:')).toBeNull()
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
