import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { PurchasesPage } from './PurchasesPage'
import { apiFetch } from '../lib/http'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
beforeEach(() => {
  vi.mocked(apiFetch)
    .mockReset()
    .mockImplementation(async (path, options) => {
      if (path.endsWith('/default-supplier')) return Response.json({ id: 9, nombre: 'Proveedor' })
      if (path === '/api/v1/operations/purchases/apparel') {
        const input = JSON.parse(String(options?.body))
        return Response.json({
          items: input.variants.map(
            (
              variant: { code: string; size: string; color: string; quantity: number },
              i: number,
            ) => ({
              id: i + 10,
              codigo: variant.code,
              nombre: input.product.name + ' · ' + variant.size + ' · ' + variant.color,
              quantity: variant.quantity,
              unitCost: input.product.cost,
            }),
          ),
        })
      }
      return Response.json([])
    })
})
it('creates a garment once and adds its size quantities to the same purchase without entering initial stock', async () => {
  render(<PurchasesPage />)
  fireEvent.click(screen.getByRole('button', { name: /Cargar factura/ }))
  await screen.findByLabelText('Número de comprobante')
  fireEvent.change(screen.getByLabelText('Número de comprobante'), { target: { value: 'F-ROPA' } })
  fireEvent.click(screen.getByRole('button', { name: 'Crear producto sin salir del comprobante' }))
  const dialog = screen.getByRole('dialog', { name: 'Nuevo artículo de la compra' })
  fireEvent.change(within(dialog).getByLabelText('Tipo de producto'), {
    target: { value: 'INDUMENTARIA' },
  })
  fireEvent.change(within(dialog).getByLabelText('Nombre de la prenda'), {
    target: { value: 'Remera' },
  })
  fireEvent.change(within(dialog).getByLabelText('Costo unitario común'), {
    target: { value: '100' },
  })
  fireEvent.change(within(dialog).getByLabelText('Talle 1'), { target: { value: 'S' } })
  fireEvent.change(within(dialog).getByLabelText('Color 1'), { target: { value: 'Negro' } })
  fireEvent.change(within(dialog).getByLabelText('Cantidad 1'), { target: { value: '2' } })
  fireEvent.click(within(dialog).getByRole('button', { name: 'Agregar otro talle' }))
  fireEvent.change(within(dialog).getByLabelText('Talle 2'), { target: { value: 'M' } })
  fireEvent.change(within(dialog).getByLabelText('Cantidad 2'), { target: { value: '3' } })
  fireEvent.click(
    within(dialog).getByRole('button', { name: 'Crear prenda y agregar talles al comprobante' }),
  )
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  expect(screen.getByText('Artículos de esta factura · 2')).toBeTruthy()
  expect((screen.getByLabelText('Número de comprobante') as HTMLInputElement).value).toBe('F-ROPA')
  const call = vi
    .mocked(apiFetch)
    .mock.calls.find(([url]) => url === '/api/v1/operations/purchases/apparel')!
  const body = JSON.parse(String(call[1]?.body))
  expect(body.product).toMatchObject({
    name: 'Remera',
    cost: 100,
    initialStock: 0,
    apparel: { model: 'Remera', kind: 'INDUMENTARIA' },
  })
  expect(body.variants).toMatchObject([
    { size: 'S', color: 'Negro', quantity: 2 },
    { size: 'M', color: 'Negro', quantity: 3 },
  ])
  expect(body.variants[0].code).not.toBe(body.variants[1].code)
  expect(
    vi.mocked(apiFetch).mock.calls.some(([url]) => url === '/api/v1/operations/purchases'),
  ).toBe(false)
})
