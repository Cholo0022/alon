import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { GarmentEditor } from './GarmentEditor'
import { apiFetch } from '../lib/http'

vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
beforeEach(() => {
  vi.mocked(apiFetch)
    .mockReset()
    .mockResolvedValue(
      Response.json({
        product: { id: 1, nombre_publico: 'Remera', tipo_producto: 'INDUMENTARIA' },
        variants: [{ id: 1, talle: 'S', color: 'Unico', stock: 5, codigo: 'REM-S' }],
        token: 'a'.repeat(64),
      }),
    )
})

it('shows sizes without deletion controls when the garment is read only', async () => {
  render(<GarmentEditor articleId={1} canEdit={false} onBack={() => {}} onSaved={() => {}} />)
  expect((await screen.findByLabelText('Stock actual S Unico')).matches(':disabled')).toBe(true)
  expect(screen.queryByText('Eliminar un talle sin operaciones vinculadas')).toBeNull()
  expect(screen.queryByRole('button', { name: 'Guardar prenda y talles' })).toBeNull()
})

it('focuses the pending size instead of adding it twice', async () => {
  render(<GarmentEditor articleId={1} canEdit onBack={() => {}} onSaved={() => {}} />)
  const medium = await screen.findByRole('button', { name: 'M' })
  fireEvent.click(medium)
  fireEvent.click(medium)
  expect(screen.getAllByLabelText(/Talle nuevo/)).toHaveLength(1)
  expect(document.activeElement).toBe(screen.getByLabelText('Stock nuevo 1'))
})
