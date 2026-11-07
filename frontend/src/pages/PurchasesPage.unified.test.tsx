import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { PurchasesPage } from './PurchasesPage'
import { apiFetch } from '../lib/http'
import { purchaseDraftKey } from '../lib/purchaseDraft'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
const request = vi.mocked(apiFetch)
const product = {
  id: 1,
  codigo: 'A1',
  nombre: 'Martillo',
  costo: 75,
  stock: 2,
  tipo_producto: 'ARTICULO',
}
const variant = {
  id: 2,
  codigo: 'R-M-N',
  nombre: 'Remera · M · Negro',
  costo: 100,
  tipo_producto: 'INDUMENTARIA',
  modelo: 'Remera',
  marca: 'Marca',
  talle: 'M',
  color: 'Negro',
  sistema_talle: 'LETRAS',
}
const otherVariant = {
  ...variant,
  id: 3,
  codigo: 'R-L-N',
  nombre: 'Remera · L · Negro',
  talle: 'L',
}
const scope = '1:7'
beforeEach(() => {
  localStorage.clear()
  request.mockReset().mockImplementation(async (url, options) => {
    if (url.endsWith('/default-supplier'))
      return Response.json({ id: 9, codigo: 'P9', nombre: 'Proveedor sin nombre' })
    if (url.includes('/variants?')) return Response.json([variant, otherVariant])
    if (url.includes('/alon/articles'))
      return Response.json(url.includes('code=') ? [product] : [product, variant, otherVariant])
    if (url.includes('/lookups/suppliers'))
      return Response.json([{ id: 9, codigo: 'P9', nombre: 'Proveedor sin nombre' }])
    if (url === '/api/v1/operations/purchases/suppliers')
      return Response.json({ id: 12, codigo: 'P12', nombre: 'Proveedor nuevo' })
    if (options?.method === 'POST') return Response.json({ id: 1 })
    return Response.json([])
  })
})
async function openAndAdd() {
  fireEvent.click(screen.getByRole('button', { name: /Cargar factura/ }))
  await screen.findByLabelText('Número de comprobante')
  fireEvent.change(screen.getByLabelText('Número de comprobante'), { target: { value: 'F-123' } })
  fireEvent.change(screen.getByLabelText('Código de barras'), { target: { value: 'A1' } })
  fireEvent.keyDown(screen.getByLabelText('Código de barras'), { key: 'Enter' })
  const dialog = await screen.findByRole('dialog', { name: 'Cantidad y precio de compra' })
  fireEvent.keyDown(within(dialog).getByLabelText('Costo unitario'), { key: 'Enter' })
}
it('recovers an edited draft for this user, keeps it after failure and clears it after successful confirmation', async () => {
  const first = render(<PurchasesPage draftScope={scope} />)
  await openAndAdd()
  fireEvent.change(screen.getByLabelText('Cantidad de A1 · Martillo'), { target: { value: '4' } })
  fireEvent.change(screen.getByLabelText('Costo de A1 · Martillo'), { target: { value: '90' } })
  fireEvent.change(screen.getByLabelText('Gastos adicionales'), { target: { value: '10' } })
  first.unmount()
  const draft = JSON.parse(localStorage.getItem(purchaseDraftKey(scope))!)
  expect(draft.lines[0]).toMatchObject({ quantity: 4, unitCost: 90 })
  expect(draft.header.documentNumber).toBe('F-123')
  const otherUser = render(<PurchasesPage draftScope="1:8" />)
  expect(screen.queryByRole('button', { name: 'Continuar borrador' })).toBeNull()
  otherUser.unmount()
  render(<PurchasesPage draftScope={scope} />)
  fireEvent.click(screen.getByRole('button', { name: 'Continuar borrador' }))
  expect((screen.getByLabelText('Número de comprobante') as HTMLInputElement).value).toBe('F-123')
  expect((screen.getByLabelText('Cantidad de A1 · Martillo') as HTMLInputElement).value).toBe('4')
  fireEvent.click(screen.getByRole('button', { name: 'Guardar factura' }))
  fireEvent.change(screen.getByLabelText('Condición de pago'), {
    target: { value: 'CUENTA_CORRIENTE' },
  })
  fireEvent.click(screen.getByLabelText('Actualizar costo del catálogo'))
  const original = request.getMockImplementation()!
  request.mockImplementationOnce(async () =>
    Response.json({ error: 'No se pudo guardar.' }, { status: 400 }),
  )
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar factura' }))
  await within(screen.getByRole('dialog')).findByText('No se pudo guardar.')
  expect(localStorage.getItem(purchaseDraftKey(scope))).not.toBeNull()
  request.mockImplementation(original)
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar factura' }))
  await screen.findByText(/Factura guardada correctamente/)
  await waitFor(() => expect(localStorage.getItem(purchaseDraftKey(scope))).toBeNull())
  const call = request.mock.calls.filter(([url]) => url === '/api/v1/operations/purchases').at(-1)!
  expect(JSON.parse(String(call[1]?.body))).toMatchObject({
    paid: 0,
    updateCost: true,
    updateStock: true,
    lines: [{ articleId: 1, quantity: 4, unitCost: 90 }],
    extraCosts: 10,
  })
})
it('loads several sizes together, combines them with ordinary articles and creates a supplier inline', async () => {
  render(<PurchasesPage draftScope={scope} />)
  await openAndAdd()
  fireEvent.click(screen.getByRole('button', { name: 'Crear proveedor' }))
  fireEvent.change(screen.getByLabelText('Nombre del proveedor'), {
    target: { value: 'Proveedor nuevo' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar y usar proveedor' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  expect((screen.getByRole('combobox', { name: 'Proveedor' }) as HTMLInputElement).value).toContain(
    'Proveedor nuevo',
  )
  fireEvent.click(screen.getByRole('button', { name: 'Buscar productos' }))
  fireEvent.click(await screen.findByRole('option', { name: /Remera · M · Negro/ }))
  const grid = screen.getByRole('dialog', { name: 'Cantidades recibidas por talle' })
  fireEvent.change(await within(grid).findByLabelText('Negro · M · R-M-N'), {
    target: { value: '2' },
  })
  fireEvent.change(within(grid).getByLabelText('Negro · L · R-L-N'), { target: { value: '3' } })
  fireEvent.change(within(grid).getByLabelText('Costo unitario común'), {
    target: { value: '120' },
  })
  fireEvent.click(
    within(grid).getByRole('button', { name: 'Agregar combinaciones al comprobante' }),
  )
  expect(screen.getByText('Artículos de esta factura · 3')).toBeTruthy()
  expect(
    (screen.getByLabelText('Cantidad de R-M-N · Remera · M · Negro') as HTMLInputElement).value,
  ).toBe('2')
  expect(
    (screen.getByLabelText('Costo de R-L-N · Remera · L · Negro') as HTMLInputElement).value,
  ).toBe('120')
  expect(screen.getByText('A1 · Martillo')).toBeTruthy()
})
it('discards a draft without recreating it on close', async () => {
  const view = render(<PurchasesPage draftScope={scope} />)
  await openAndAdd()
  await waitFor(() => expect(localStorage.getItem(purchaseDraftKey(scope))).not.toBeNull())
  vi.spyOn(window, 'confirm').mockReturnValueOnce(true)
  fireEvent.click(screen.getByRole('button', { name: 'Descartar borrador' }))
  view.unmount()
  expect(localStorage.getItem(purchaseDraftKey(scope))).toBeNull()
})
