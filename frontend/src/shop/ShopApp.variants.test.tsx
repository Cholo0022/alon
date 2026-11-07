import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import ShopApp from './ShopApp'
import { shopRequest } from './api'
vi.mock('./api', () => ({ shopRequest: vi.fn() }))
const variants = [
  {
    id: 11,
    nombre: 'Remera · S · Negro',
    codigo: 'R-S',
    tipo_producto: 'INDUMENTARIA',
    talle: 'S',
    color: 'Negro',
    sistema_talle: 'LETRAS',
    unidad: 'UNIDAD',
    stock: 2,
    precio_venta: 100,
    imagen_clave: null,
    imagen_ilustrativa: false,
  },
  {
    id: 12,
    nombre: 'Remera · M · Negro',
    codigo: 'R-M',
    tipo_producto: 'INDUMENTARIA',
    talle: 'M',
    color: 'Negro',
    sistema_talle: 'LETRAS',
    unidad: 'UNIDAD',
    stock: 4,
    precio_venta: 120,
    imagen_clave: null,
    imagen_ilustrativa: false,
  },
  {
    id: 13,
    nombre: 'Remera · L · Azul',
    codigo: 'R-L',
    tipo_producto: 'INDUMENTARIA',
    talle: 'L',
    color: 'Azul',
    sistema_talle: 'LETRAS',
    unidad: 'UNIDAD',
    stock: 0,
    precio_venta: 120,
    imagen_clave: null,
    imagen_ilustrativa: false,
  },
]
beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  vi.mocked(shopRequest)
    .mockReset()
    .mockImplementation(async (path, options) => {
      if (path === '/session') return { user: { id: 1, nombre: 'Ana', apellido: 'Pérez' } }
      if (path.startsWith('/catalog'))
        return {
          items: [{ ...variants[0], nombre: 'Remera', variants }],
          total: 1,
          categories: [],
          store: { nombre: 'Alon' },
        }
      if (path === '/cart') {
        const ids = JSON.parse(String(options?.body)).ids as number[]
        return variants.filter((variant) => ids.includes(variant.id))
      }
      if (path === '/orders') return { id: 42 }
      return {}
    })
})
it('shows one garment, stock and price for each selected size, and orders the selected variant codes', async () => {
  render(<ShopApp />)
  const title = await screen.findByRole('heading', { name: 'Remera', level: 2 })
  const card = title.closest('article')!
  expect(screen.getAllByRole('heading', { name: 'Remera', level: 2 })).toHaveLength(1)
  expect(
    (within(card).getByRole('button', { name: '+ Agregar al carrito' }) as HTMLButtonElement)
      .disabled,
  ).toBe(true)
  fireEvent.change(within(card).getByLabelText('Color'), { target: { value: 'Negro' } })
  fireEvent.change(within(card).getByLabelText('Talle'), { target: { value: '12' } })
  expect(within(card).getByText('Talle M · Negro: 4 unidades disponibles')).toBeTruthy()
  expect(within(card).getByText(/120,00/)).toBeTruthy()
  fireEvent.click(within(card).getByRole('button', { name: '+ Agregar al carrito' }))
  const dialog = screen.getByRole('dialog', { name: 'Cantidad para agregar al carrito' })
  expect((within(dialog).getByLabelText('Cantidad') as HTMLInputElement).max).toBe('4')
  fireEvent.change(within(dialog).getByLabelText('Cantidad'), { target: { value: '3' } })
  fireEvent.click(within(dialog).getByRole('button', { name: 'Agregar cantidad al carrito' }))
  expect(JSON.parse(localStorage.getItem('alon-shop-cart')!)[0]).toMatchObject({
    id: 12,
    quantity: 3,
  })
  fireEvent.change(within(card).getByLabelText('Talle'), { target: { value: '11' } })
  expect(within(card).getByText('Talle S · Negro: 2 unidades disponibles')).toBeTruthy()
  fireEvent.click(within(card).getByRole('button', { name: '+ Agregar al carrito' }))
  fireEvent.click(screen.getByRole('button', { name: 'Agregar cantidad al carrito' }))
  expect(JSON.parse(localStorage.getItem('alon-shop-cart')!)).toHaveLength(2)
  fireEvent.change(within(card).getByLabelText('Color'), { target: { value: 'Azul' } })
  fireEvent.change(within(card).getByLabelText('Talle'), { target: { value: '13' } })
  expect(within(card).getByText('Talle L · Azul: 0 unidades disponibles')).toBeTruthy()
  expect(
    (within(card).getByRole('button', { name: '+ Agregar al carrito' }) as HTMLButtonElement)
      .disabled,
  ).toBe(true)
  fireEvent.click(screen.getByRole('button', { name: 'Enviar pedido →' }))
  for (const [label, value] of [
    ['Nombre', 'Ana'],
    ['Apellido', 'Prueba'],
    ['Celular (10 dígitos)', '3446236311'],
    ['DNI', '30111222'],
  ])
    fireEvent.change(screen.getByLabelText(label), { target: { value } })
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar envío del pedido' }))
  await waitFor(() =>
    expect(vi.mocked(shopRequest).mock.calls.some(([path]) => path === '/orders')).toBe(true),
  )
  const call = vi.mocked(shopRequest).mock.calls.find(([path]) => path === '/orders')!
  expect(JSON.parse(String(call[1]?.body)).lines).toEqual([
    { articleId: 12, quantity: 3, expectedPrice: 120 },
    { articleId: 11, quantity: 1, expectedPrice: 100 },
  ])
})
