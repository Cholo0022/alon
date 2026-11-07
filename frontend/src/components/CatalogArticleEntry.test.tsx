import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ArticleList } from '../pages/CatalogPage'
import { apiFetch } from '../lib/http'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
beforeEach(() => {
  vi.mocked(apiFetch)
    .mockReset()
    .mockImplementation(async (url, options) => {
      if (url === '/api/v1/operations/articles/apparel' && options?.method === 'POST')
        return Response.json({ items: [{ id: 1 }, { id: 2 }, { id: 3 }] })
      return Response.json([])
    })
})
it('offers stock per size in the same article entry and sends S5 M3 L0 without creating a purchase', async () => {
  render(<ArticleList canEditCosts />)
  fireEvent.click(screen.getByRole('button', { name: /Cargar artículo/ }))
  expect((screen.getByLabelText('Tipo de producto') as HTMLSelectElement).value).toBe('ARTICULO')
  fireEvent.change(screen.getByLabelText('Tipo de producto'), { target: { value: 'INDUMENTARIA' } })
  expect(document.querySelectorAll('form')).toHaveLength(1)
  fireEvent.change(screen.getByLabelText('Nombre del artículo'), { target: { value: 'Remera' } })
  fireEvent.change(screen.getByLabelText('Talle 1'), { target: { value: 'S' } })
  fireEvent.change(screen.getByLabelText('Color único (opcional)'), { target: { value: 'Negro' } })
  fireEvent.change(screen.getByLabelText('Stock talle 1'), { target: { value: '5' } })
  fireEvent.click(screen.getByRole('button', { name: 'Agregar otro talle' }))
  fireEvent.change(screen.getByLabelText('Talle 2'), { target: { value: 'M' } })
  fireEvent.change(screen.getByLabelText('Stock talle 2'), { target: { value: '3' } })
  fireEvent.click(screen.getByRole('button', { name: 'Agregar otro talle' }))
  fireEvent.change(screen.getByLabelText('Talle 3'), { target: { value: 'L' } })
  expect((screen.getByLabelText('Stock talle 3') as HTMLInputElement).value).toBe('0')
  fireEvent.click(screen.getByRole('button', { name: 'Guardar artículo con stock por talle' }))
  await screen.findByText('Prenda guardada con 3 talles.')
  const call = vi
    .mocked(apiFetch)
    .mock.calls.find(([url]) => url === '/api/v1/operations/articles/apparel')!
  const body = JSON.parse(String(call[1]?.body))
  expect(body.product).toMatchObject({
    freightByValue: false,
    freightByPercent: true,
    freightPerUnit: 0,
  })
  expect(body.variants).toMatchObject([
    { size: 'S', color: 'Negro', quantity: 5 },
    { size: 'M', color: 'Negro', quantity: 3 },
    { size: 'L', color: 'Negro', quantity: 0 },
  ])
  expect(
    vi.mocked(apiFetch).mock.calls.some(([url]) => url.includes('/operations/purchases')),
  ).toBe(false)
})
it('keeps the per-size stock form after a failed save and allows footwear selection', async () => {
  vi.mocked(apiFetch).mockImplementation(async (url) =>
    url === '/api/v1/operations/articles/apparel'
      ? Response.json({ error: 'No se pudo guardar.' }, { status: 400 })
      : Response.json([]),
  )
  render(<ArticleList canEditCosts />)
  fireEvent.click(screen.getByRole('button', { name: /Cargar artículo/ }))
  fireEvent.change(screen.getByLabelText('Tipo de producto'), { target: { value: 'CALZADO' } })
  fireEvent.change(screen.getByLabelText('Nombre del artículo'), { target: { value: 'Zapato' } })
  fireEvent.change(screen.getByLabelText('Talle 1'), { target: { value: '38' } })
  fireEvent.change(screen.getByLabelText('Color único (opcional)'), { target: { value: 'Negro' } })
  fireEvent.change(screen.getByLabelText('Stock talle 1'), { target: { value: '2' } })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar artículo con stock por talle' }))
  await waitFor(() => expect(screen.getByRole('alert').textContent).toBe('No se pudo guardar.'))
  expect((screen.getByLabelText('Stock talle 1') as HTMLInputElement).value).toBe('2')
  const call = vi
    .mocked(apiFetch)
    .mock.calls.find(([url]) => url === '/api/v1/operations/articles/apparel')!
  expect(JSON.parse(String(call[1]?.body)).product.apparel.kind).toBe('CALZADO')
})
it('marks missing fields red and goes to the next field without clearing the article', async () => {
  render(<ArticleList canEditCosts />)
  fireEvent.click(screen.getByRole('button', { name: /Cargar artículo/ }))
  fireEvent.change(screen.getByLabelText('Tipo de producto'), { target: { value: 'INDUMENTARIA' } })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar artículo con stock por talle' }))
  const name = screen.getByLabelText('Nombre del artículo')
  const size = screen.getByLabelText('Talle 1')
  expect(name.getAttribute('aria-invalid')).toBe('true')
  expect(size.getAttribute('aria-invalid')).toBe('true')
  fireEvent.change(name, { target: { value: 'Remera' } })
  expect(name.getAttribute('aria-invalid')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Guardar artículo con stock por talle' }))
  await waitFor(() => expect(document.activeElement).toBe(size))
  expect((name as HTMLInputElement).value).toBe('Remera')
  expect(
    vi.mocked(apiFetch).mock.calls.some(([url]) => url === '/api/v1/operations/articles/apparel'),
  ).toBe(false)
})
it('marks every server field and opens folded details when correcting the next error', async () => {
  vi.mocked(apiFetch).mockImplementation(async (url) =>
    url === '/api/v1/operations/articles/apparel'
      ? Response.json(
          {
            error: 'Revisá los campos señalados.',
            fields: {
              'variants[0].quantity': 'Revisá el stock.',
              'product.apparel.model': 'Revisá el modelo.',
            },
          },
          { status: 400 },
        )
      : Response.json([]),
  )
  render(<ArticleList canEditCosts />)
  fireEvent.click(screen.getByRole('button', { name: /Cargar artículo/ }))
  fireEvent.change(screen.getByLabelText('Tipo de producto'), { target: { value: 'INDUMENTARIA' } })
  fireEvent.change(screen.getByLabelText('Nombre del artículo'), { target: { value: 'Remera' } })
  fireEvent.change(screen.getByLabelText('Talle 1'), { target: { value: 'S' } })
  fireEvent.change(screen.getByLabelText('Color único (opcional)'), { target: { value: 'Negro' } })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar artículo con stock por talle' }))
  const stock = screen.getByLabelText('Stock talle 1')
  const model = screen.getByLabelText('Modelo / referencia')
  await waitFor(() => expect(document.activeElement).toBe(stock))
  expect(stock.getAttribute('aria-invalid')).toBe('true')
  expect(model.getAttribute('aria-invalid')).toBe('true')
  fireEvent.change(stock, { target: { value: '2' } })
  expect(stock.getAttribute('aria-invalid')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Guardar artículo con stock por talle' }))
  await waitFor(() => expect(document.activeElement).toBe(model))
  expect(model.closest('details')?.open).toBe(true)
})

it('does not save when a scanner finishes the size barcode with Enter', async () => {
  const { default: userEvent } = await import('@testing-library/user-event')
  const user = userEvent.setup()
  render(<ArticleList canEditCosts />)
  fireEvent.click(screen.getByRole('button', { name: /Cargar artículo/ }))
  fireEvent.change(screen.getByLabelText('Tipo de producto'), { target: { value: 'INDUMENTARIA' } })
  fireEvent.change(screen.getByLabelText('Nombre del artículo'), { target: { value: 'Remera' } })
  fireEvent.change(screen.getByLabelText('Talle 1'), { target: { value: 'S' } })
  fireEvent.change(screen.getByLabelText('Color único (opcional)'), { target: { value: 'Negro' } })
  fireEvent.change(screen.getByLabelText('Stock talle 1'), { target: { value: '5' } })
  screen.getByLabelText('Código 1').closest('details')!.open = true
  await user.type(screen.getByLabelText('Código 1'), '789123{Enter}')
  expect(vi.mocked(apiFetch).mock.calls.some(([, options]) => options?.method === 'POST')).toBe(
    false,
  )
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Agregar otro talle' }))
  fireEvent.click(screen.getByRole('button', { name: 'Agregar otro talle' }))
  fireEvent.change(screen.getByLabelText('Talle 2'), { target: { value: 'M' } })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar artículo con stock por talle' }))
  await screen.findByText(/Prenda guardada con/)
  const call = vi
    .mocked(apiFetch)
    .mock.calls.find(([url]) => url === '/api/v1/operations/articles/apparel')!
  expect(JSON.parse(String(call[1]?.body)).variants).toHaveLength(2)
})
