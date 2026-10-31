import { shopTemplates } from '../lib/shopTemplates'
import { defaultBrand, normalizeBrand } from '../lib/businessBrand'
import { beforeEach, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor, within } from '@testing-library/react'
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
  await user.click(
    await screen.findByRole('button', { name: '+ Agregar al carrito' }, { timeout: 10000 }),
  )
  await user.click(screen.getByRole('button', { name: 'Agregar cantidad al carrito' }))
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
  await user.click(screen.getByRole('button', { name: 'Agregar cantidad al carrito' }))
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
  await user.click(screen.getByRole('button', { name: 'Agregar cantidad al carrito' }))
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
  await user.click(screen.getByRole('button', { name: 'Agregar cantidad al carrito' }))
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

it('registers location with Argentina selected by default and allows another country', async () => {
  const implementation = request.getMockImplementation()!
  request.mockImplementation(async (path, options) =>
    path === '/session'
      ? { user: {} }
      : path === '/register'
        ? buyer
        : implementation(path, options),
  )
  const user = userEvent.setup()
  render(<ShopApp />)
  await user.click(await screen.findByRole('button', { name: 'Ingresar' }))
  await user.click(screen.getByRole('button', { name: 'Primera vez · Crear cuenta' }))
  const country = screen.getByRole('combobox', { name: 'País' }) as HTMLSelectElement
  expect(country.value).toBe('Argentina')
  await user.type(screen.getByLabelText('Nombre'), 'Ana')
  await user.type(screen.getByLabelText('Apellido'), 'Prueba')
  await user.type(screen.getByLabelText('DNI'), '30111222')
  await user.type(screen.getByLabelText('Domicilio'), 'Calle 123')
  await user.type(screen.getByLabelText('Código postal'), '11000')
  await user.type(screen.getByLabelText('Ciudad'), 'Montevideo')
  await user.type(screen.getByLabelText('Provincia'), 'Montevideo')
  await user.selectOptions(country, 'Uruguay')
  await user.type(screen.getByLabelText('Teléfono con código de país'), '+59899123456')
  expect((screen.getByLabelText(/^Contraseña/) as HTMLInputElement).minLength).toBe(8)
  await user.type(screen.getByLabelText(/^Contraseña/), '12345678')
  await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
  await waitFor(() => expect(request.mock.calls.some(([path]) => path === '/register')).toBe(true))
  const call = request.mock.calls.find(([path]) => path === '/register')!
  expect(JSON.parse(String(call[1]?.body))).toMatchObject({
    postalCode: '11000',
    city: 'Montevideo',
    province: 'Montevideo',
    country: 'Uruguay',
  })
})

it('uses the business identity and refreshes it when a saved brand is announced', async () => {
  const implementation = request.getMockImplementation()!
  const brand = {
    nombre: 'Almacén Norte',
    slogan: 'Siempre cerca',
    color_principal: '#aa3300',
    color_secundario: '#112233',
    color_fondo: '#fefefe',
  }
  request.mockImplementation(async (path, options) =>
    path === '/business' ? brand : implementation(path, options),
  )
  const { container } = render(<ShopApp />)
  await screen.findByRole('button', { name: 'Almacén Norte, inicio' })
  expect(screen.getByText('Siempre cerca')).toBeTruthy()
  expect(
    (container.firstElementChild as HTMLElement).style.getPropertyValue('--business-primary'),
  ).toBe('#aa3300')
  act(() =>
    window.dispatchEvent(
      new CustomEvent('alon:business-updated', {
        detail: { ...brand, nombre: 'Comercio renovado', color_principal: '#114477' },
      }),
    ),
  )
  expect(screen.getByRole('button', { name: 'Comercio renovado, inicio' })).toBeTruthy()
  expect(
    (container.firstElementChild as HTMLElement).style.getPropertyValue('--business-primary'),
  ).toBe('#114477')
})

it('asks for quantity before adding, accumulates decimal amounts and respects remaining stock', async () => {
  const user = userEvent.setup()
  render(<ShopApp />)
  const add = await screen.findByRole(
    'button',
    { name: '+ Agregar al carrito' },
    { timeout: 10000 },
  )
  await user.click(add)
  let dialog = screen.getByRole('dialog', { name: 'Cantidad para agregar al carrito' })
  expect(screen.getByText('Elegí productos para empezar tu pedido.')).toBeTruthy()
  let input = within(dialog).getByRole('spinbutton', { name: 'Cantidad' })
  expect(document.activeElement).toBe(input)
  await user.clear(input)
  await user.type(input, '2.5')
  await user.keyboard('{Enter}')
  expect(screen.queryByRole('dialog')).toBeNull()
  expect((screen.getByRole('spinbutton', { name: 'Cantidad' }) as HTMLInputElement).value).toBe(
    '2.5',
  )
  await user.click(add)
  dialog = screen.getByRole('dialog')
  input = within(dialog).getByRole('spinbutton', { name: 'Cantidad' })
  expect(input.getAttribute('max')).toBe('2.5')
  await user.clear(input)
  await user.type(input, '3')
  await user.click(within(dialog).getByRole('button', { name: 'Agregar cantidad al carrito' }))
  expect(screen.getByRole('dialog')).toBeTruthy()
  expect(JSON.parse(localStorage.getItem('alon-shop-cart')!)[0].quantity).toBe(2.5)
  await user.clear(input)
  await user.type(input, '2.5')
  await user.click(within(dialog).getByRole('button', { name: 'Agregar cantidad al carrito' }))
  expect(screen.queryByRole('dialog')).toBeNull()
  expect((add as HTMLButtonElement).disabled).toBe(true)
  expect(JSON.parse(localStorage.getItem('alon-shop-cart')!)[0].quantity).toBe(5)
})

it('cancels the quantity dialog without changing the cart', async () => {
  const user = userEvent.setup()
  render(<ShopApp />)
  const add = await screen.findByRole(
    'button',
    { name: '+ Agregar al carrito' },
    { timeout: 10000 },
  )
  await user.click(add)
  await user.keyboard('{Escape}')
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(screen.getByText('Elegí productos para empezar tu pedido.')).toBeTruthy()
  expect(document.activeElement).toBe(add)
})

it('rounds fractional stock remaining before proposing the quantity', async () => {
  const fractional = { ...product, stock: 0.3 }
  localStorage.setItem('alon-shop-cart', JSON.stringify([{ ...fractional, quantity: 0.1 }]))
  const implementation = request.getMockImplementation()!
  request.mockImplementation(async (path, options) =>
    path.startsWith('/catalog')
      ? { items: [fractional], total: 1, categories: [], store: { nombre: 'Comercio' } }
      : implementation(path, options),
  )
  const user = userEvent.setup()
  render(<ShopApp />)
  await user.click(
    await screen.findByRole('button', { name: '+ Agregar al carrito' }, { timeout: 10000 }),
  )
  const dialog = screen.getByRole('dialog')
  expect(
    (within(dialog).getByRole('spinbutton', { name: 'Cantidad' }) as HTMLInputElement).value,
  ).toBe('0.2')
  await user.click(within(dialog).getByRole('button', { name: 'Agregar cantidad al carrito' }))
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(JSON.parse(localStorage.getItem('alon-shop-cart')!)[0].quantity).toBe(0.3)
})

it('switches among all ten templates without changing the cart or shopping controls', async () => {
  localStorage.setItem('alon-shop-cart', JSON.stringify([{ ...product, quantity: 2 }]))
  const { container } = render(<ShopApp />)
  await screen.findByRole('button', { name: '+ Agregar al carrito' }, { timeout: 10000 })
  for (const template of shopTemplates) {
    const brand = {
      ...defaultBrand,
      plantilla_tienda: template.id,
      titulo_tienda: 'Bienvenidos ' + template.name,
      descripcion_tienda: 'Comprá en nuestra tienda',
    }
    act(() => window.dispatchEvent(new CustomEvent('alon:business-updated', { detail: brand })))
    expect(container.querySelector('.shop-app')?.getAttribute('data-template')).toBe(template.id)
    expect(screen.getByRole('heading', { name: brand.titulo_tienda })).toBeTruthy()
    expect(screen.getByRole('button', { name: '+ Agregar al carrito' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Enviar pedido →' })).toBeTruthy()
    expect((screen.getByRole('spinbutton', { name: 'Cantidad' }) as HTMLInputElement).value).toBe(
      '2',
    )
  }
  expect(
    normalizeBrand({ plantilla_tienda: 'desconocida' } as unknown as Partial<typeof defaultBrand>)
      .plantilla_tienda,
  ).toBe('clasica')
})

/** Cambia la contraseña de la cuenta compradora sin pedir la anterior ni solicitar datos de otra persona. */
it('changes the signed-in shopper password using only a new eight-character password', async () => {
  const implementation = request.getMockImplementation()!
  request.mockImplementation(async (path, options) =>
    path === '/password' ? buyer : implementation(path, options),
  )
  const user = userEvent.setup()
  render(<ShopApp />)
  await user.click(await screen.findByRole('button', { name: 'Cambiar contraseña' }))
  const field = screen.getByLabelText(/^Nueva contraseña/) as HTMLInputElement
  expect(field.minLength).toBe(8)
  expect(screen.queryByLabelText(/contraseña anterior/i)).toBeNull()
  expect(screen.queryByLabelText(/código de verificación/i)).toBeNull()
  await user.type(field, '12345678')
  await user.click(screen.getByRole('button', { name: 'Guardar nueva contraseña' }))
  expect(
    await screen.findByText(
      'Contraseña actualizada. Usá la nueva contraseña en tus próximos ingresos.',
    ),
  ).toBeTruthy()
  const call = request.mock.calls.find(([path]) => path === '/password')!
  expect(JSON.parse(String(call[1]?.body))).toEqual({ password: '12345678' })
})

/** El acceso flotante no aparece con carrito vacío aunque el panel esté fuera de la pantalla. */
it('hides the cart shortcut until a product has been added', async () => {
  class Observer {
    callback: IntersectionObserverCallback
    constructor(callback: IntersectionObserverCallback) {
      this.callback = callback
    }
    observe(target: Element) {
      this.callback(
        [{ target, isIntersecting: false } as IntersectionObserverEntry],
        this as unknown as IntersectionObserver,
      )
    }
    disconnect() {}
  }
  vi.stubGlobal('IntersectionObserver', Observer)
  try {
    const user = userEvent.setup()
    render(<ShopApp />)
    const add = await screen.findByRole('button', { name: '+ Agregar al carrito' })
    expect(screen.queryByRole('button', { name: /Ir al carrito/ })).toBeNull()
    await user.click(add)
    await user.click(screen.getByRole('button', { name: 'Agregar cantidad al carrito' }))
    expect(await screen.findByRole('button', { name: /Ir al carrito/ })).toBeTruthy()
  } finally {
    vi.unstubAllGlobals()
  }
})
