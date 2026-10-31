import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { SalesPage } from './SalesPage'
import { ArticlePicker } from '../components/ArticlePicker'
import { apiFetch } from '../lib/http'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
const request = vi.mocked(apiFetch)
const articles = [
  { id: 1, codigo: 'A1', nombre: 'Martillo', stock: 5, precio_venta: 100, unidad: 'UNIDAD' },
  { id: 2, codigo: 'A2', nombre: 'Clavos', stock: 10, precio_venta: 200, unidad: 'CAJA' },
]
beforeEach(() => {
  request.mockReset()
  request.mockImplementation(async (url, options) => {
    if (url.includes('/cash/status')) return Response.json({ estado: 'ABIERTA' })
    if (url.includes('/lookups/customers'))
      return Response.json([{ id: 99, codigo: 'CF', nombre: 'Consumidor final' }])
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
it('opens all articles on empty Enter, adds by keyboard repeatedly and saves only at the final step', async () => {
  render(<SalesPage onOpenCash={() => undefined} />)
  fireEvent.click(screen.getByRole('button', { name: /Nueva venta/ }))
  let articleInput = screen.getByRole('combobox', { name: 'Artículo' })
  expect(screen.queryByRole('dialog')).toBeNull()
  fireEvent.keyDown(articleInput, { key: 'Enter' })
  const dialog = screen.getByRole('dialog', { name: 'Elegir artículo' })
  await within(dialog).findByRole('option', { name: /Martillo/ }, { timeout: 10000 })
  expect(within(dialog).getByText('STOCK')).toBeTruthy()
  expect(within(dialog).getByText('PRECIO')).toBeTruthy()
  const search = within(dialog).getByRole('combobox', { name: 'Buscar en el listado de artículos' })
  expect(document.activeElement).toBe(search)
  fireEvent.keyDown(search, { key: 'ArrowDown' })
  expect(
    within(dialog)
      .getByRole('option', { name: /Clavos/ })
      .getAttribute('aria-selected'),
  ).toBe('true')
  fireEvent.keyDown(search, { key: 'Enter' })
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(screen.getByText('A2 · Clavos')).toBeTruthy()
  articleInput = screen.getByRole('combobox', { name: 'Artículo' })
  expect(document.activeElement).toBe(articleInput)
  fireEvent.keyDown(articleInput, { key: 'Enter' })
  const nextSearch = screen.getByRole('combobox', { name: 'Buscar en el listado de artículos' })
  fireEvent.change(nextSearch, { target: { value: 'Martillo' } })
  await screen.findByRole('option', { name: /Martillo/ }, { timeout: 10000 })
  expect(screen.queryByRole('option', { name: /Clavos/ })).toBeNull()
  fireEvent.keyDown(nextSearch, { key: 'Enter' })
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(screen.getByText('A1 · Martillo')).toBeTruthy()
  expect(
    request.mock.calls.some(
      ([url, options]) => url === '/api/v1/operations/sales' && options?.method === 'POST',
    ),
  ).toBe(false)
  fireEvent.click(screen.getByRole('button', { name: 'Guardar venta' }))
  await waitFor(() =>
    expect(
      request.mock.calls.some(
        ([url, options]) => url === '/api/v1/operations/sales' && options?.method === 'POST',
      ),
    ).toBe(true),
  )
  const call = request.mock.calls.find(
    ([url, options]) => url === '/api/v1/operations/sales' && options?.method === 'POST',
  )!
  expect(JSON.parse(String(call[1]?.body)).lines).toEqual([
    { articleId: 2, quantity: 1, discountPercent: 0 },
    { articleId: 1, quantity: 1, discountPercent: 0 },
  ])
})
it('keeps an unavailable article out of the sale and returns focus on Escape', async () => {
  const implementation = request.getMockImplementation()!
  request.mockImplementation(async (url, options) =>
    url.includes('/alon/articles')
      ? Response.json([{ ...articles[0], stock: 0 }])
      : implementation(url, options),
  )
  render(<SalesPage onOpenCash={() => undefined} />)
  fireEvent.click(screen.getByRole('button', { name: /Nueva venta/ }))
  fireEvent.keyDown(screen.getByRole('combobox', { name: 'Artículo' }), { key: 'Enter' })
  await screen.findByRole('option', { name: /Martillo/ }, { timeout: 10000 })
  const search = screen.getByRole('combobox', { name: 'Buscar en el listado de artículos' })
  fireEvent.keyDown(search, { key: 'Enter' })
  expect(screen.getByRole('dialog')).toBeTruthy()
  expect(within(screen.getByRole('dialog')).getByText(/supera el stock/)).toBeTruthy()
  fireEvent.keyDown(search, { key: 'Escape' })
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(document.activeElement).toBe(screen.getByRole('combobox', { name: 'Artículo' }))
  expect(
    (screen.getByRole('button', { name: 'Guardar venta' }) as HTMLButtonElement).disabled,
  ).toBe(true)
})

it('reaches all article pages with the keyboard', async () => {
  const first = Array.from({ length: 100 }, (_, i) => ({
    id: i + 1,
    nombre: 'Artículo ' + i,
    codigo: 'A' + i,
    stock: 5,
    precio_venta: 100,
  }))
  const last = { id: 101, nombre: 'Último artículo', codigo: 'A100', stock: 5, precio_venta: 100 }
  request.mockImplementation(async (url) =>
    Response.json(
      new URL(url, 'http://localhost').searchParams.get('page') === '1' ? [last] : first,
    ),
  )
  const choose = vi.fn()
  render(<ArticlePicker onChoose={choose} onClose={() => undefined} message="" />)
  await screen.findByRole('option', { name: /Artículo 99/ }, { timeout: 10000 })
  fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  await screen.findByRole('option', { name: /Último artículo/ }, { timeout: 10000 })
  const search = screen.getByRole('combobox', { name: 'Buscar en el listado de artículos' })
  fireEvent.keyDown(search, { key: 'ArrowUp' })
  await waitFor(
    () =>
      expect(
        screen.getByRole('option', { name: /Artículo 99/ }).getAttribute('aria-selected'),
      ).toBe('true'),
    { timeout: 10000 },
  )
  fireEvent.keyDown(search, { key: 'ArrowDown' })
  await screen.findByRole('option', { name: /Último artículo/ }, { timeout: 10000 })
  fireEvent.keyDown(search, { key: 'Enter' })
  expect(choose).toHaveBeenCalledWith(last)
})
