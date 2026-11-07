import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ArticleList } from '../pages/CatalogPage'
import { apiFetch } from '../lib/http'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
const base = {
  id: 10,
  codigo: 'REM-S',
  nombre: 'Remera · S · Negro',
  tipo_producto: 'INDUMENTARIA',
  modelo: 'REM-1',
  marca: 'Marca',
  talle: 'S',
  color: 'Negro',
  sistema_talle: 'LETRAS',
  material: 'Algodón',
  publico: 'UNISEX',
  temporada: 'TODO EL AÑO',
  descripcion: 'Remera',
  unidad: 'UNIDAD',
  costo: 100,
  precio_venta: 155,
  stock: 5,
  stock_minimo: 0,
  flete_porcentaje: 0,
  margen_porcentaje: 20,
  estado: 'ACTIVO',
}
beforeEach(() => {
  vi.mocked(apiFetch)
    .mockReset()
    .mockImplementation(async (url, options) => {
      if (url.includes('/10/variants') && options?.method !== 'POST') return Response.json([base])
      if (url === '/api/v1/operations/articles/10/variants')
        return Response.json({ items: [{ id: 11 }] })
      if (url.startsWith('/api/v1/alon/articles')) return Response.json([base])
      return Response.json([])
    })
})
it('opens add-sizes from Modify, ignores scanner Enter, and adds only the new sizes without losing edits', async () => {
  const user = userEvent.setup()
  render(<ArticleList canEditCosts />)
  fireEvent.click(await screen.findByRole('button', { name: 'Modificar' }))
  const name = screen.getByLabelText('Nombre del artículo') as HTMLInputElement
  fireEvent.change(name, { target: { value: 'Remera editando' } })
  fireEvent.click(screen.getByRole('button', { name: 'Agregar talles a esta prenda' }))
  const dialog = screen.getByRole('dialog', { name: 'Agregar talles a esta prenda' })
  await waitFor(() =>
    expect(
      (within(dialog).getByRole('button', { name: 'Guardar talles nuevos' }) as HTMLButtonElement)
        .disabled,
    ).toBe(false),
  )
  fireEvent.change(within(dialog).getByLabelText('Talle nuevo 1'), { target: { value: 'M' } })
  fireEvent.change(within(dialog).getByLabelText('Stock nuevo 1'), { target: { value: '3' } })
  await user.type(within(dialog).getByLabelText('Código nuevo 1'), '123456{Enter}')
  expect(vi.mocked(apiFetch).mock.calls.some(([, options]) => options?.method === 'POST')).toBe(
    false,
  )
  expect(document.activeElement).toBe(
    within(dialog).getByRole('button', { name: 'Agregar otro talle' }),
  )
  fireEvent.click(within(dialog).getByRole('button', { name: 'Agregar otro talle' }))
  fireEvent.change(within(dialog).getByLabelText('Talle nuevo 2'), { target: { value: 'L' } })
  fireEvent.click(within(dialog).getByRole('button', { name: 'Guardar talles nuevos' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  const call = vi
    .mocked(apiFetch)
    .mock.calls.find(
      ([url, options]) =>
        url === '/api/v1/operations/articles/10/variants' && options?.method === 'POST',
    )!
  expect(JSON.parse(String(call[1]?.body)).variants).toMatchObject([
    { size: 'M', color: 'Negro', quantity: 3, code: '123456' },
    { size: 'L', color: 'Negro', quantity: 0 },
  ])
  expect(name.value).toBe('Remera editando')
  expect(
    screen.getByText('Talles agregados. Se conservaron los stocks que ya estaban cargados.'),
  ).toBeTruthy()
})
it('marks a size that already exists instead of duplicating its stock', async () => {
  render(<ArticleList canEditCosts />)
  fireEvent.click(await screen.findByRole('button', { name: 'Modificar' }))
  fireEvent.click(screen.getByRole('button', { name: 'Agregar talles a esta prenda' }))
  await waitFor(() =>
    expect(
      (screen.getByRole('button', { name: 'Guardar talles nuevos' }) as HTMLButtonElement).disabled,
    ).toBe(false),
  )
  fireEvent.change(screen.getByLabelText('Talle nuevo 1'), { target: { value: 'S' } })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar talles nuevos' }))
  expect(screen.getByLabelText('Talle nuevo 1').getAttribute('aria-invalid')).toBe('true')
  expect(vi.mocked(apiFetch).mock.calls.some(([, options]) => options?.method === 'POST')).toBe(
    false,
  )
})
