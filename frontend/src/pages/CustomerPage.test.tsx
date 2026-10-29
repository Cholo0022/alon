import { expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CustomerList } from './CustomerPage'

it('shows an empty directory instead of inventing customers', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => Response.json([])),
  )
  render(<CustomerList />)
  await waitFor(() => expect(screen.getByText('Página 1 · 0 clientes')).toBeTruthy())
  expect(screen.getByText('No hay clientes que coincidan con la búsqueda.')).toBeTruthy()
  expect(screen.queryByText(/Cliente de Prueba/)).toBeNull()
})

it('requests a different page and performs search on the server', async () => {
  const fetchMock = vi.fn(async (url: string) => {
    const page = new URL(url, 'http://localhost').searchParams.get('page')
    return Response.json(
      page === '0'
        ? Array.from({ length: 100 }, (_, id) => ({
            id,
            codigo: `C${id}`,
            nombre: `Cliente ${id}`,
            estado: 'ACTIVO',
          }))
        : [],
    )
  })
  vi.stubGlobal('fetch', fetchMock)
  const user = userEvent.setup()
  render(<CustomerList />)
  await waitFor(() => expect(screen.getByText('Página 1 · 100 clientes')).toBeTruthy())
  await user.click(screen.getByRole('button', { name: 'Siguiente' }))
  await waitFor(() =>
    expect(fetchMock.mock.calls.some(([url]) => url.includes('page=1'))).toBe(true),
  )
  const search = screen.getByRole('searchbox')
  await user.click(search)
  await user.paste('Cliente remoto')
  await waitFor(() =>
    expect(
      fetchMock.mock.calls.some(
        ([url]) => url.includes('q=Cliente+remoto') && url.includes('page=0'),
      ),
    ).toBe(true),
  )
})
