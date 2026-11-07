import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { GarmentEditor } from './GarmentEditor'
import { apiFetch } from '../lib/http'
import { installSentenceCase } from '../lib/sentenceCase'

vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
beforeEach(() => {
  vi.mocked(apiFetch)
    .mockReset()
    .mockResolvedValue(
      Response.json({
        product: {
          id: 1,
          nombre_publico: 'Remera',
          tipo_producto: 'INDUMENTARIA',
          modelo: 'R1',
          marca: 'Marca',
          material: 'Algodón',
          sistema_talle: 'Letras',
          publico: 'Adultos',
          temporada: 'Verano',
          descripcion: 'Remera',
        },
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

it('allows changing existing size and color and sends them to the server', async () => {
  render(<GarmentEditor articleId={1} canEdit onBack={() => {}} onSaved={() => {}} />)
  fireEvent.change(await screen.findByLabelText('Talle actual 1'), { target: { value: 'M' } })
  fireEvent.change(screen.getByLabelText('Color actual 1'), { target: { value: 'Azul' } })
  vi.mocked(apiFetch).mockResolvedValueOnce(Response.json({}))
  fireEvent.click(screen.getByRole('button', { name: 'Guardar prenda y talles' }))
  const call = vi.mocked(apiFetch).mock.calls.find(([, options]) => options?.method === 'PUT')
  expect(call).toBeDefined()
  expect(JSON.parse(String(call?.[1]?.body)).lines[0]).toMatchObject({
    id: 1,
    size: 'M',
    color: 'Azul',
    stock: 5,
  })
})

it('keeps the capitalized single-letter size in React state', async () => {
  const uninstall = installSentenceCase()
  try {
    render(<GarmentEditor articleId={1} canEdit onBack={() => {}} onSaved={() => {}} />)
    const size = (await screen.findByLabelText('Talle actual 1')) as HTMLInputElement
    fireEvent.input(size, { target: { value: 'm' } })
    fireEvent.change(screen.getByLabelText('Color actual 1'), { target: { value: 'azul' } })
    expect(size.value).toBe('M')
    expect((screen.getByLabelText('Color actual 1') as HTMLInputElement).value).toBe('Azul')
  } finally {
    uninstall()
  }
})
