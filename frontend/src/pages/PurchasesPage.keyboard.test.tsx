import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { PurchasesPage } from './PurchasesPage'
import { apiFetch } from '../lib/http'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
const request = vi.mocked(apiFetch)
const articles = [
  {
    id: 1,
    codigo: 'A1',
    nombre: 'Martillo',
    stock: 0,
    costo: 75,
    precio_venta: 100,
    unidad: 'UNIDAD',
  },
  {
    id: 2,
    codigo: 'A2',
    nombre: 'Clavos',
    stock: 10,
    costo: 40,
    precio_venta: 200,
    unidad: 'CAJA',
  },
]
beforeEach(() => {
  request.mockReset()
  request.mockImplementation(async (url, options) => {
    if (url.endsWith('/purchases/default-supplier'))
      return Response.json({ id: 9, codigo: 'P9', nombre: 'Proveedor sin nombre' })
    if (url.includes('/lookups/suppliers'))
      return Response.json([{ id: 7, codigo: 'P7', nombre: 'Proveedor Norte' }])
    if (url.includes('/alon/articles')) {
      const query = new URL(url, 'http://localhost').searchParams.get('q')?.toLowerCase() ?? ''
      return Response.json(
        articles.filter((row) => (row.nombre + ' ' + row.codigo).toLowerCase().includes(query)),
      )
    }
    if (options?.method === 'POST') return Response.json({ id: 1 })
    return Response.json([])
  })
})
it('keeps the supplier and document while repeatedly adding from the purchase picker and saves only at the end', async () => {
  render(<PurchasesPage />)
  fireEvent.click(screen.getByRole('button', { name: /Cargar factura/ }))
  await screen.findByRole('combobox', { name: 'Proveedor' })
  expect(screen.queryByLabelText('Condición de pago')).toBeNull()
  expect(screen.queryByLabelText('Medio de pago')).toBeNull()
  const supplier = screen.getByRole('combobox', { name: 'Proveedor' }) as HTMLInputElement
  fireEvent.change(supplier, { target: { value: 'Norte' } })
  fireEvent.click(
    await screen.findByRole('option', { name: 'P7 · Proveedor Norte' }, { timeout: 10000 }),
  )
  fireEvent.change(screen.getByLabelText('Tipo de comprobante'), { target: { value: 'FACTURA' } })
  fireEvent.change(screen.getByLabelText('Número de comprobante'), { target: { value: 'F-100' } })
  fireEvent.keyDown(screen.getByRole('combobox', { name: 'Artículo' }), { key: 'Enter' })
  const dialog = screen.getByRole('dialog', { name: 'Elegir artículo' })
  await within(dialog).findByRole('option', { name: /Martillo/ }, { timeout: 10000 })
  expect(within(dialog).getByText('COSTO UNITARIO')).toBeTruthy()
  fireEvent.keyDown(
    within(dialog).getByRole('combobox', { name: 'Buscar en el listado de artículos' }),
    { key: 'Enter' },
  )
  const details = screen.getByRole('dialog', { name: 'Cantidad y precio de compra' })
  expect(screen.queryByText('A1 · Martillo')).toBeNull()
  const quantityInput = within(details).getByRole('spinbutton', { name: 'Cantidad' })
  fireEvent.change(quantityInput, { target: { value: '3.5' } })
  fireEvent.keyDown(quantityInput, { key: 'Enter' })
  const costInput = within(details).getByRole('spinbutton', { name: 'Costo unitario' })
  expect(document.activeElement).toBe(costInput)
  fireEvent.change(costInput, { target: { value: '80.25' } })
  fireEvent.keyDown(costInput, { key: 'Enter' })
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(screen.getByText('A1 · Martillo')).toBeTruthy()
  expect(supplier.value).toBe('P7 · Proveedor Norte')
  expect((screen.getByLabelText('Número de comprobante') as HTMLInputElement).value).toBe('F-100')
  expect(document.activeElement).toBe(screen.getByRole('combobox', { name: 'Artículo' }))
  fireEvent.keyDown(screen.getByRole('combobox', { name: 'Artículo' }), { key: 'Enter' })
  const search = screen.getByRole('combobox', { name: 'Buscar en el listado de artículos' })
  fireEvent.change(search, { target: { value: 'Clavos' } })
  await screen.findByRole('option', { name: /Clavos/ }, { timeout: 10000 })
  fireEvent.keyDown(search, { key: 'Enter' })
  const nextDetails = screen.getByRole('dialog', { name: 'Cantidad y precio de compra' })
  fireEvent.keyDown(within(nextDetails).getByRole('spinbutton', { name: 'Cantidad' }), {
    key: 'Enter',
  })
  fireEvent.keyDown(within(nextDetails).getByRole('spinbutton', { name: 'Costo unitario' }), {
    key: 'Enter',
  })
  expect(screen.getByText('A2 · Clavos')).toBeTruthy()
  expect(
    request.mock.calls.some(
      ([url, options]) => url === '/api/v1/operations/purchases' && options?.method === 'POST',
    ),
  ).toBe(false)
  fireEvent.click(screen.getByRole('button', { name: 'Guardar factura' }))
  const paymentDialog = screen.getByRole('dialog', { name: 'Confirmar factura de compra' })
  expect(
    request.mock.calls.some(
      ([url, options]) => url === '/api/v1/operations/purchases' && options?.method === 'POST',
    ),
  ).toBe(false)
  fireEvent.change(within(paymentDialog).getByLabelText('Condición de pago'), {
    target: { value: 'EFECTIVO' },
  })
  fireEvent.change(within(paymentDialog).getByLabelText('Medio de pago'), {
    target: { value: 'EFECTIVO' },
  })
  fireEvent.click(within(paymentDialog).getByRole('button', { name: 'Confirmar factura' }))
  expect(
    request.mock.calls.some(
      ([url, options]) => url === '/api/v1/operations/purchases' && options?.method === 'POST',
    ),
  ).toBe(false)
  const costDialog = screen.getByRole('dialog', {
    name: '¿Este comprobante modifica el precio de costo?',
  })
  fireEvent.keyDown(costDialog, { key: 's' })
  await waitFor(() =>
    expect(
      request.mock.calls.some(
        ([url, options]) => url === '/api/v1/operations/purchases' && options?.method === 'POST',
      ),
    ).toBe(true),
  )
  const call = request.mock.calls.find(
    ([url, options]) => url === '/api/v1/operations/purchases' && options?.method === 'POST',
  )!
  expect(JSON.parse(String(call[1]?.body))).toMatchObject({
    supplierId: 7,
    updateCost: true,
    paymentType: 'EFECTIVO',
    paymentMethod: 'EFECTIVO',
    documentNumber: 'F-100',
    lines: [
      { articleId: 1, quantity: 3.5, unitCost: 80.25 },
      { articleId: 2, quantity: 1, unitCost: 40 },
    ],
  })
})
it('respects a cost entered before opening the picker and restores focus on Escape', async () => {
  render(<PurchasesPage />)
  fireEvent.click(screen.getByRole('button', { name: /Cargar factura/ }))
  await screen.findByRole('combobox', { name: 'Proveedor' })
  fireEvent.change(screen.getByLabelText('Costo unitario'), { target: { value: '55.5' } })
  fireEvent.keyDown(screen.getByRole('combobox', { name: 'Artículo' }), { key: 'Enter' })
  await screen.findByRole('option', { name: /Martillo/ }, { timeout: 10000 })
  const search = screen.getByRole('combobox', { name: 'Buscar en el listado de artículos' })
  fireEvent.keyDown(search, { key: 'Enter' })
  const details = screen.getByRole('dialog', { name: 'Cantidad y precio de compra' })
  expect(
    (within(details).getByRole('spinbutton', { name: 'Costo unitario' }) as HTMLInputElement).value,
  ).toBe('55.5')
  fireEvent.keyDown(within(details).getByRole('spinbutton', { name: 'Cantidad' }), { key: 'Enter' })
  fireEvent.keyDown(within(details).getByRole('spinbutton', { name: 'Costo unitario' }), {
    key: 'Enter',
  })
  const row = screen.getByText('A1 · Martillo').closest('tr')!
  expect(row.querySelector('[data-label="Costo unitario"]')?.textContent).toContain('55,50')
  fireEvent.keyDown(screen.getByRole('combobox', { name: 'Artículo' }), { key: 'Enter' })
  const listSearch = screen.getByRole('combobox', { name: 'Buscar en el listado de artículos' })
  fireEvent.change(listSearch, { target: { value: 'Clavos' } })
  await screen.findByRole('option', { name: /Clavos/ }, { timeout: 10000 })
  fireEvent.keyDown(listSearch, { key: 'Enter' })
  fireEvent.keyDown(
    within(screen.getByRole('dialog', { name: 'Cantidad y precio de compra' })).getByRole(
      'spinbutton',
      { name: 'Cantidad' },
    ),
    { key: 'Escape' },
  )
  expect(
    (
      screen.getByRole('combobox', {
        name: 'Buscar en el listado de artículos',
      }) as HTMLInputElement
    ).value,
  ).toBe('Clavos')
  fireEvent.keyDown(listSearch, { key: 'Escape' })
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(document.activeElement).toBe(screen.getByRole('combobox', { name: 'Artículo' }))
})

it('starts at the unnamed supplier, opens the supplier directory with Enter and saves credit without a medium after N', async () => {
  render(<PurchasesPage />)
  fireEvent.click(screen.getByRole('button', { name: /Cargar factura/ }))
  const supplier = (await screen.findByRole('combobox', { name: 'Proveedor' })) as HTMLInputElement
  expect(supplier.value).toBe('P9 · Proveedor sin nombre')
  expect(document.activeElement).toBe(supplier)
  expect((screen.getByLabelText('Tipo de comprobante') as HTMLSelectElement).value).toBe('REMITO')
  fireEvent.keyDown(supplier, { key: 'Enter' })
  const suppliers = screen.getByRole('dialog', { name: 'Elegir proveedor' })
  await within(suppliers).findByRole('option', { name: /Proveedor Norte/ }, { timeout: 10000 })
  fireEvent.keyDown(within(suppliers).getByRole('combobox'), { key: 'Enter' })
  expect((screen.getByRole('combobox', { name: 'Proveedor' }) as HTMLInputElement).value).toBe(
    'P7 · Proveedor Norte',
  )
  fireEvent.change(screen.getByLabelText('Número de comprobante'), { target: { value: 'R-1' } })
  fireEvent.keyDown(screen.getByRole('combobox', { name: 'Artículo' }), { key: 'Enter' })
  await screen.findByRole('option', { name: /Martillo/ }, { timeout: 10000 })
  fireEvent.keyDown(screen.getByRole('combobox', { name: 'Buscar en el listado de artículos' }), {
    key: 'Enter',
  })
  const details = screen.getByRole('dialog', { name: 'Cantidad y precio de compra' })
  fireEvent.keyDown(within(details).getByRole('spinbutton', { name: 'Costo unitario' }), {
    key: 'Enter',
  })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar factura' }))
  fireEvent.change(screen.getByLabelText('Condición de pago'), {
    target: { value: 'CUENTA_CORRIENTE' },
  })
  expect(screen.queryByLabelText('Medio de pago')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar factura' }))
  fireEvent.keyDown(screen.getByRole('dialog'), { key: 'n' })
  await waitFor(() =>
    expect(
      request.mock.calls.some(
        ([url, options]) => url === '/api/v1/operations/purchases' && options?.method === 'POST',
      ),
    ).toBe(true),
  )
  const call = request.mock.calls.find(
    ([url, options]) => url === '/api/v1/operations/purchases' && options?.method === 'POST',
  )!
  expect(JSON.parse(String(call[1]?.body))).toMatchObject({
    supplierId: 7,
    documentType: 'REMITO',
    updateCost: false,
    paymentType: 'CUENTA_CORRIENTE',
    paid: 0,
  })
})
