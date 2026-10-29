import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ShopApp from './ShopApp'
import { shopRequest } from './api'

vi.mock('./api', () => ({ shopRequest: vi.fn() }))
const product = {
  id: 1,
  nombre: 'Artículo de prueba',
  unidad: 'UNIDAD',
  precio_venta: 100,
  stock: 5,
  imagen_clave: null,
  imagen_ilustrativa: false,
  rubro: 'Rubro',
}
const buyer = {
  id: 1,
  nombre: 'Ana',
  apellido: 'Prueba',
  telefono: '+5491100000000',
  domicilio: 'Calle 123',
}
const request = vi.mocked(shopRequest)

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  request.mockReset()
  request.mockImplementation(async (path) => {
    if (path === '/session') return { user: buyer, csrfToken: 'test' }
    if (path.startsWith('/catalog'))
      return {
        items: [product],
        total: 1,
        categories: [{ nombre: 'Rubro' }],
        store: { nombre: 'Comercio' },
      }
    if (path === '/cart') return [product]
    if (path === '/orders') return { id: 42 }
    return {}
  })
})

it('requires choosing a payment method before submitting', async () => {
  const user = userEvent.setup()
  render(<ShopApp />)
  await user.click(await screen.findByRole('button', { name: '+ Agregar al carrito' }))
  await user.click(screen.getByRole('button', { name: 'Enviar pedido →' }))
  expect(screen.getByRole('alert').textContent).toContain('Elegí un medio de pago')
  expect(request.mock.calls.some(([path]) => path === '/orders')).toBe(false)
})

it('requires login before submitting an order', async () => {
  request.mockImplementation(async (path) =>
    path === '/session'
      ? { user: {} }
      : { items: [product], total: 1, categories: [], store: { nombre: 'Comercio' } },
  )
  const user = userEvent.setup()
  render(<ShopApp />)
  await user.click(await screen.findByRole('button', { name: '+ Agregar al carrito' }))
  await user.selectOptions(screen.getByRole('combobox', { name: 'Medio de pago' }), 'EFECTIVO')
  await user.click(screen.getByRole('button', { name: 'Enviar pedido →' }))
  expect(screen.getByRole('heading', { name: 'Bienvenido de nuevo' })).toBeTruthy()
  expect(
    request.mock.calls.some(([path, options]) => path === '/orders' && options?.method === 'POST'),
  ).toBe(false)
})

it('requires reviewing a changed price before placing the order', async () => {
  const implementation = request.getMockImplementation()!
  request.mockImplementation(async (path, options) =>
    path === '/cart' ? [{ ...product, precio_venta: 120 }] : implementation(path, options),
  )
  const user = userEvent.setup()
  render(<ShopApp />)
  await user.click(await screen.findByRole('button', { name: '+ Agregar al carrito' }))
  await user.selectOptions(screen.getByRole('combobox', { name: 'Medio de pago' }), 'EFECTIVO')
  await user.click(screen.getByRole('button', { name: 'Enviar pedido →' }))
  await waitFor(() =>
    expect(screen.getByRole('alert').textContent).toContain('Actualizamos los precios'),
  )
  expect(
    request.mock.calls.some(([path, options]) => path === '/orders' && options?.method === 'POST'),
  ).toBe(false)
})

it('reuses the operation key when retrying after a lost response', async () => {
  const implementation = request.getMockImplementation()!
  let submissions = 0
  request.mockImplementation(async (path, options) => {
    if (path === '/orders' && options?.method === 'POST' && submissions++ === 0)
      throw new Error('Respuesta perdida')
    return implementation(path, options)
  })
  const user = userEvent.setup()
  const view = render(<ShopApp />)
  await user.click(await screen.findByRole('button', { name: '+ Agregar al carrito' }))
  await user.selectOptions(screen.getByRole('combobox', { name: 'Medio de pago' }), 'EFECTIVO')
  await user.click(screen.getByRole('button', { name: 'Enviar pedido →' }))
  await screen.findByText('Respuesta perdida')
  view.unmount()
  render(<ShopApp />)
  await screen.findByRole('button', { name: 'Mis pedidos' })
  await user.selectOptions(screen.getByRole('combobox', { name: 'Medio de pago' }), 'EFECTIVO')
  await user.click(screen.getByRole('button', { name: 'Enviar pedido →' }))
  await screen.findByText(/Pedido #42 enviado/)
  const calls = request.mock.calls.filter(
    ([path, options]) => path === '/orders' && options?.method === 'POST',
  )
  expect(calls).toHaveLength(2)
  expect(JSON.parse(String(calls[1][1]?.body)).paymentMethod).toBe('EFECTIVO')
  expect(new Headers(calls[0][1]?.headers).get('Idempotency-Key')).toEqual(
    new Headers(calls[1][1]?.headers).get('Idempotency-Key'),
  )
})
