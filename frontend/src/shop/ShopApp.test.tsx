import { shopTemplates } from '../lib/shopTemplates'
import { defaultBrand, normalizeBrand } from '../lib/businessBrand'
import { beforeEach, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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

function fillContact() {
  for (const [label, value] of [
    ['Nombre', 'Ana'],
    ['Apellido', 'Prueba'],
    ['Celular (10 dígitos)', '3446236311'],
    ['DNI', '30111222'],
  ])
    fireEvent.change(screen.getByLabelText(label), { target: { value } })
}
async function submitGuestOrder(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: /Enviar pedido/ }))
  fillContact()
  await user.click(screen.getByRole('button', { name: 'Confirmar envío del pedido' }))
}

it('opens contact details with local defaults without asking for login or payment', async () => {
  const user = userEvent.setup()
  render(<ShopApp />)
  await user.click(await screen.findByRole('button', { name: '+ Agregar al carrito' }))
  await user.click(screen.getByRole('button', { name: 'Agregar cantidad al carrito' }))
  await user.click(screen.getByRole('button', { name: /Enviar pedido/ }))
  expect(screen.getByRole('dialog', { name: 'Datos para enviar el pedido' })).toBeTruthy()
  expect((screen.getByLabelText('Provincia') as HTMLInputElement).value).toBe('Entre Ríos')
  expect((screen.getByLabelText('Ciudad') as HTMLInputElement).value).toBe('Larroque')
  expect(screen.queryByRole('button', { name: 'Ingresar' })).toBeNull()
  expect(screen.queryByLabelText(/contraseña/i)).toBeNull()
  expect(screen.queryByRole('combobox', { name: 'Medio de pago' })).toBeNull()
  expect(request.mock.calls.some(([path]) => path === '/orders')).toBe(false)
})

it('submits a guest order with contact data and no account or payment choice', async () => {
  const user = userEvent.setup()
  render(<ShopApp />)
  await user.click(await screen.findByRole('button', { name: '+ Agregar al carrito' }))
  await user.click(screen.getByRole('button', { name: 'Agregar cantidad al carrito' }))
  await submitGuestOrder(user)
  await screen.findByText(/Pedido #42 enviado/)
  const call = request.mock.calls.find(([path]) => path === '/orders')!
  expect(JSON.parse(String(call[1]?.body))).toMatchObject({
    buyer: {
      firstName: 'Ana',
      lastName: 'Prueba',
      phone: '3446236311',
      dni: '30111222',
      province: 'Entre Ríos',
      city: 'Larroque',
    },
    lines: [{ articleId: 1, quantity: 1, expectedPrice: 100 }],
  })
  expect(JSON.parse(String(call[1]?.body))).not.toHaveProperty('paymentMethod')
  expect(
    request.mock.calls.some(([path]) => ['/login', '/register', '/password'].includes(path)),
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
  await submitGuestOrder(user)
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
  await submitGuestOrder(user)
  await screen.findByText('Respuesta perdida')
  view.unmount()
  render(<ShopApp />)
  await screen.findByRole('heading', { name: 'Artículo de prueba', level: 2 })
  await submitGuestOrder(user)
  await screen.findByText(/Pedido #42 enviado/)
  const calls = request.mock.calls.filter(
    ([path, options]) => path === '/orders' && options?.method === 'POST',
  )
  expect(calls).toHaveLength(2)
  expect(JSON.parse(String(calls[1][1]?.body)).buyer.phone).toBe('3446236311')
  expect(new Headers(calls[0][1]?.headers).get('Idempotency-Key')).toEqual(
    new Headers(calls[1][1]?.headers).get('Idempotency-Key'),
  )
})

it('allows changing the province and city before sending the order', async () => {
  const user = userEvent.setup()
  render(<ShopApp />)
  await user.click(await screen.findByRole('button', { name: '+ Agregar al carrito' }))
  await user.click(screen.getByRole('button', { name: 'Agregar cantidad al carrito' }))
  await user.click(screen.getByRole('button', { name: /Enviar pedido/ }))
  fillContact()
  fireEvent.change(screen.getByLabelText('Provincia'), { target: { value: 'Buenos Aires' } })
  fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: 'Tandil' } })
  await user.click(screen.getByRole('button', { name: 'Confirmar envío del pedido' }))
  await screen.findByText(/Pedido #42 enviado/)
  const call = request.mock.calls.find(([path]) => path === '/orders')!
  expect(JSON.parse(String(call[1]?.body)).buyer).toMatchObject({
    province: 'Buenos Aires',
    city: 'Tandil',
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
it('does not show account or password controls even if an old shopper session exists', async () => {
  render(<ShopApp />)
  await screen.findByRole('heading', { name: 'Artículo de prueba', level: 2 })
  expect(screen.queryByRole('button', { name: 'Ingresar' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Cambiar contraseña' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Mis pedidos' })).toBeNull()
})

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

/** Recupera una cuenta antes de ingresar enviando solamente teléfono y clave nueva. */
it('returns to the cart without sending an order when contact entry is cancelled', async () => {
  const user = userEvent.setup()
  render(<ShopApp />)
  await user.click(await screen.findByRole('button', { name: '+ Agregar al carrito' }))
  await user.click(screen.getByRole('button', { name: 'Agregar cantidad al carrito' }))
  await user.click(screen.getByRole('button', { name: /Enviar pedido/ }))
  await user.click(screen.getByRole('button', { name: 'Volver al carrito' }))
  expect(screen.queryByRole('dialog', { name: 'Datos para enviar el pedido' })).toBeNull()
  expect(JSON.parse(localStorage.getItem('alon-shop-cart')!)).toHaveLength(1)
  expect(request.mock.calls.some(([path]) => path === '/orders')).toBe(false)
})

it('shows the product title as the first text after its image without repeating the category', async () => {
  render(<ShopApp />)
  const title = await screen.findByRole('heading', { name: 'Artículo de prueba', level: 2 })
  const body = title.closest('.shop-product-body')!
  expect(body.firstElementChild).toBe(title)
  expect(body.querySelector('.shop-product-category')).toBeNull()
})

it('shows the optional shop phone in the header and removes it when cleared', async () => {
  const original = request.getMockImplementation()!
  request.mockImplementation(async (path, options) =>
    path === '/business'
      ? { ...defaultBrand, slogan: 'Siempre cerca', telefono_tienda: '3446 - 236311' }
      : original(path, options),
  )
  render(<ShopApp />)
  const phone = await screen.findByText('Celular: 3446 - 236311')
  expect(phone.closest('header')?.className).toBe('shop-header')
  act(() =>
    window.dispatchEvent(
      new CustomEvent('alon:business-updated', {
        detail: { ...defaultBrand, slogan: 'Siempre cerca', telefono_tienda: '' },
      }),
    ),
  )
  expect(screen.queryByText('Celular: 3446 - 236311')).toBeNull()
})
