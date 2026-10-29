import { expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { LookupField } from './LookupField'
import { SalesPage } from '../pages/SalesPage'
import { ArticleList } from '../pages/CatalogPage'

it('searches remote records in one field and keeps the selected ID when results change', async () => {
  const row = { id: 250, codigo: 'A250', nombre: 'Artículo remoto' }
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => Response.json(url.includes('remoto') ? [row] : [])),
  )
  const selected = vi.fn()
  const { container } = render(
    <LookupField
      name="articleId"
      label="Artículo"
      endpoint="/api/v1/demo/articles"
      onSelect={selected}
    />,
  )
  const input = screen.getByRole('combobox', { name: 'Artículo' })
  fireEvent.change(input, { target: { value: 'remoto' } })
  await waitFor(() => expect(screen.getByRole('option').textContent).toBe('A250 · Artículo remoto'))
  fireEvent.keyDown(input, { key: 'ArrowDown' })
  expect(screen.getByRole('option').getAttribute('aria-selected')).toBe('true')
  fireEvent.keyDown(input, { key: 'Enter' })
  expect(selected).toHaveBeenLastCalledWith(row)
  await waitFor(() => expect(screen.queryByRole('option')).toBeNull())
  expect(container.querySelector<HTMLInputElement>('input[name="articleId"]')?.value).toBe('250')
  fireEvent.change(input, { target: { value: 'otro' } })
  expect(container.querySelector<HTMLInputElement>('input[name="articleId"]')?.value).toBe('')
})

it('renders one customer field and one article field with no pagination in the sale form', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) =>
      Response.json(url.includes('/cash/status') ? { estado: 'ABIERTA' } : []),
    ),
  )
  const { container } = render(<SalesPage onOpenCash={() => undefined} />)
  fireEvent.click(screen.getByRole('button', { name: /Nueva venta/ }))
  expect(screen.getAllByRole('combobox', { name: 'Cliente' })).toHaveLength(1)
  expect(screen.getAllByRole('combobox', { name: 'Artículo' })).toHaveLength(1)
  expect(container.querySelector('form nav')).toBeNull()
  await waitFor(() => expect(screen.queryByText(/La caja de hoy/)).toBeNull())
})

it('removes the external supplier and category controls from the article directory', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => Response.json([])),
  )
  render(<ArticleList canEditCosts />)
  expect(screen.queryByText('Buscar proveedor del artículo')).toBeNull()
  expect(
    screen.queryByRole('navigation', { name: 'Páginas de proveedores disponibles' }),
  ).toBeNull()
  expect(screen.queryByRole('navigation', { name: 'Páginas de rubros disponibles' })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: /Cargar artículo/ }))
  expect(screen.getByRole('combobox', { name: 'Proveedor principal' })).toBeTruthy()
  expect(screen.getByRole('combobox', { name: 'Rubro' })).toBeTruthy()
  expect(document.querySelector('form nav')).toBeNull()
  await waitFor(() => expect(screen.queryByText('Buscando…')).toBeNull())
})

it('selects Consumidor final by default and preserves another explicit choice', async () => {
  const consumer = { id: 21, codigo: 'FINAL', nombre: 'Consumidor final' }
  const customer = { id: 99, codigo: 'C99', nombre: 'Otro cliente' }
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => Response.json(url.includes('Otro') ? [customer] : [consumer])),
  )
  const { container } = render(
    <LookupField
      name="customerId"
      label="Cliente"
      endpoint="/api/v1/demo/lookups/customers"
      defaultName="Consumidor final"
    />,
  )
  const input = screen.getByRole('combobox', { name: 'Cliente' }) as HTMLInputElement
  await waitFor(() => expect(input.value).toBe('FINAL · Consumidor final'))
  expect(container.querySelector<HTMLInputElement>('input[name="customerId"]')?.value).toBe('21')
  fireEvent.change(input, { target: { value: 'Otro' } })
  await waitFor(() => expect(screen.getByRole('option').textContent).toBe('C99 · Otro cliente'))
  const option = screen.getByRole('option')
  fireEvent.mouseEnter(option)
  expect(option.getAttribute('aria-selected')).toBe('true')
  fireEvent.click(option)
  expect(input.value).toBe('C99 · Otro cliente')
  expect(container.querySelector<HTMLInputElement>('input[name="customerId"]')?.value).toBe('99')
  expect(screen.queryByRole('listbox')).toBeNull()
})

it('offers other database customers when opening the default selection without typing', async () => {
  const consumer = { id: 21, codigo: 'FINAL', nombre: 'Consumidor final' }
  const customer = { id: 99, codigo: 'C99', nombre: 'Cliente registrado' }
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) =>
      Response.json(url.includes('Consumidor') ? [consumer] : [consumer, customer]),
    ),
  )
  const { container } = render(
    <LookupField
      name="customerId"
      label="Cliente"
      endpoint="/api/v1/demo/lookups/customers"
      defaultName="Consumidor final"
    />,
  )
  const input = screen.getByRole('combobox', { name: 'Cliente' }) as HTMLInputElement
  await waitFor(() => expect(input.value).toBe('FINAL · Consumidor final'))
  fireEvent.focus(input)
  await waitFor(() =>
    expect(screen.getByRole('option', { name: 'C99 · Cliente registrado' })).toBeTruthy(),
  )
  fireEvent.click(screen.getByRole('option', { name: 'C99 · Cliente registrado' }))
  expect(input.value).toBe('C99 · Cliente registrado')
  expect(container.querySelector<HTMLInputElement>('input[name="customerId"]')?.value).toBe('99')
  fireEvent.click(input)
  await waitFor(() =>
    expect(screen.getByRole('option', { name: 'FINAL · Consumidor final' })).toBeTruthy(),
  )
  expect(input.value).toBe('C99 · Cliente registrado')
})
