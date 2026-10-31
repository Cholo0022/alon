import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { CategoryPricePanel } from './CategoryPricePanel'
import { apiFetch } from '../lib/http'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
const request = vi.mocked(apiFetch)
beforeEach(() => {
  request.mockReset()
  request.mockImplementation(async (url) =>
    Response.json(
      url.includes('/categories')
        ? [
            { id: 1, nombre: 'Almacén' },
            { id: 2, nombre: 'Bebidas' },
          ]
        : url.endsWith('/preview')
          ? {
              count: 2,
              expectedHash: 'hash-reviewed',
              sample: [{ id: 1, name: 'Yerba', previousPrice: 100, newPrice: 110 }],
            }
          : { updated: 2 },
    ),
  )
})
it('previews a category increase before applying the reviewed prices', async () => {
  render(<CategoryPricePanel />)
  fireEvent.click(screen.getByRole('button', { name: 'Modificar precios' }))
  await screen.findByRole('option', { name: 'Almacén' })
  fireEvent.change(screen.getByLabelText('Rubro'), { target: { value: '1' } })
  fireEvent.change(screen.getByLabelText('Aumento porcentual'), { target: { value: '10' } })
  fireEvent.click(screen.getByRole('button', { name: 'Ver aumento' }))
  const apply = await screen.findByRole('button', { name: 'Aplicar aumento a 2 artículos' })
  expect(request.mock.calls.some(([url]) => url === '/api/v1/operations/articles/prices')).toBe(
    false,
  )
  expect(screen.getByText('Yerba')).toBeTruthy()
  fireEvent.click(apply)
  await screen.findByText(/Se actualizaron 2 artículos/)
  const call = request.mock.calls.find(([url]) => url === '/api/v1/operations/articles/prices')!
  expect(JSON.parse(String(call[1]?.body))).toEqual({
    categoryId: 1,
    percent: 10,
    expectedHash: 'hash-reviewed',
  })
  expect(screen.queryByRole('button', { name: /Aplicar aumento/ })).toBeNull()
})
it('invalidates the preview when the percentage changes and when prices become stale', async () => {
  render(<CategoryPricePanel />)
  fireEvent.click(screen.getByRole('button', { name: 'Modificar precios' }))
  await screen.findByRole('option', { name: 'Almacén' })
  fireEvent.change(screen.getByLabelText('Rubro'), { target: { value: '1' } })
  fireEvent.change(screen.getByLabelText('Aumento porcentual'), { target: { value: '10' } })
  fireEvent.click(screen.getByRole('button', { name: 'Ver aumento' }))
  await screen.findByRole('button', { name: /Aplicar aumento/ })
  fireEvent.change(screen.getByLabelText('Aumento porcentual'), { target: { value: '20' } })
  expect(screen.queryByRole('button', { name: /Aplicar aumento/ })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Ver aumento' }))
  const apply = await screen.findByRole('button', { name: /Aplicar aumento/ })
  request.mockResolvedValueOnce(Response.json({ error: 'Los precios cambiaron.' }, { status: 409 }))
  fireEvent.click(apply)
  await waitFor(() =>
    expect(screen.getByRole('status').textContent).toContain('Los precios cambiaron.'),
  )
  expect(screen.queryByRole('button', { name: /Aplicar aumento/ })).toBeNull()
})
