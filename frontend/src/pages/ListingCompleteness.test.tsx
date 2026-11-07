import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { SupplierList } from './SupplierPage'
import { CustomerList } from './CustomerPage'
import { ArticleList } from './CatalogPage'
import { apiFetch } from '../lib/http'

vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
const response = (rows: unknown[], total: number) =>
  Response.json(rows, { headers: { 'X-Total-Count': String(total) } })
beforeEach(() => {
  vi.mocked(apiFetch).mockReset().mockResolvedValue(response([], 0))
  HTMLElement.prototype.scrollIntoView = vi.fn()
})

it('loads debtors independently of the first supplier page and paginates their real total', async () => {
  vi.mocked(apiFetch).mockImplementation(async (url) => {
    const query = new URL(url, 'http://localhost').searchParams
    if (query.get('debtOnly') === 'true')
      return response(
        query.get('page') === '1'
          ? [{ id: 501, codigo: 'P501', nombre: 'Proveedor última página', saldo_pendiente: 50 }]
          : Array.from({ length: 100 }, (_, index) => ({
              id: index + 201,
              codigo: `P${index + 201}`,
              nombre: `Proveedor con deuda ${index}`,
              saldo_pendiente: 50,
            })),
        101,
      )
    return response(
      [
        {
          id: 1,
          codigo: 'P1',
          nombre: 'Proveedor sin deuda',
          saldo_pendiente: 0,
          estado: 'ACTIVO',
        },
      ],
      350,
    )
  })
  render(<SupplierList />)
  await screen.findByText('350 proveedores')
  expect(await screen.findByText('101 proveedores con saldo pendiente')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'Ver proveedores a los que debo' }))
  expect(await screen.findByText('Proveedor con deuda 0')).toBeTruthy()
  const debtPagination = screen.getByRole('navigation', {
    name: 'Páginas de proveedores con deuda',
  })
  fireEvent.click(within(debtPagination).getByRole('button', { name: 'Siguiente' }))
  await screen.findByText('Proveedor última página')
  expect(
    (within(debtPagination).getByRole('button', { name: 'Siguiente' }) as HTMLButtonElement)
      .disabled,
  ).toBe(true)
  expect(screen.getByText('101 proveedores con saldo pendiente')).toBeTruthy()
})

it('keeps server customer matches by phone and resets to page zero on a new search', async () => {
  vi.mocked(apiFetch).mockImplementation(async (url) =>
    response(
      url.includes('q=3446')
        ? [{ id: 250, codigo: 'C250', nombre: 'Ana', telefono: '3446', estado: 'ACTIVO' }]
        : Array.from({ length: 100 }, (_, id) => ({
            id,
            codigo: `C${id}`,
            nombre: `Cliente ${id}`,
            estado: 'ACTIVO',
          })),
      url.includes('q=3446') ? 1 : 250,
    ),
  )
  render(<CustomerList />)
  await screen.findByText('250 clientes')
  fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  await waitFor(() =>
    expect(vi.mocked(apiFetch).mock.calls.some(([url]) => url.includes('page=1'))).toBe(true),
  )
  fireEvent.change(screen.getByLabelText('Buscar clientes'), { target: { value: '3446' } })
  await screen.findByText('Ana')
  expect(screen.getByText('1 clientes')).toBeTruthy()
  expect(
    vi
      .mocked(apiFetch)
      .mock.calls.some(([url]) => url.includes('q=3446') && url.includes('page=0')),
  ).toBe(true)
})

it('finds a category outside the initial page and counts grouped products on the server', async () => {
  vi.mocked(apiFetch).mockImplementation(async (url) =>
    url.includes('/categories')
      ? response(url.includes('q=Rubro+remoto') ? [{ id: 151, nombre: 'Rubro remoto' }] : [], 1)
      : response([], url.includes('/grouped') ? 9500 : 0),
  )
  render(<ArticleList />)
  await screen.findByText('9500 productos')
  fireEvent.change(screen.getByRole('combobox', { name: 'Filtrar por rubro' }), {
    target: { value: 'Rubro remoto' },
  })
  fireEvent.click(await screen.findByRole('option', { name: 'Rubro remoto' }))
  await waitFor(() =>
    expect(
      vi
        .mocked(apiFetch)
        .mock.calls.some(
          ([url]) => url.includes('/grouped') && url.includes('category=Rubro+remoto'),
        ),
    ).toBe(true),
  )
})
