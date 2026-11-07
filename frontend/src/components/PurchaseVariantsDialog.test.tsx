import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { PurchaseVariantsDialog } from './PurchaseVariantsDialog'
import { apiFetch } from '../lib/http'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
const base = { id: 1, modelo: 'Remera', marca: 'Marca', sistema_talle: 'LETRAS', costo: 10 }
it('fetches every page and only adds selected integer quantities with the common cost', async () => {
  const first = Array.from({ length: 200 }, (_, index) => ({
    ...base,
    id: index + 1,
    codigo: 'R' + index,
    nombre: 'Remera',
    color: 'Negro',
    talle: 'M',
  }))
  vi.mocked(apiFetch)
    .mockReset()
    .mockImplementation(async (url) =>
      Response.json(
        url.includes('page=0')
          ? first
          : [{ ...base, id: 201, codigo: 'LAST', nombre: 'Remera L', color: 'Blanco', talle: 'L' }],
      ),
    )
  const add = vi.fn()
  render(<PurchaseVariantsDialog article={base} onClose={() => undefined} onAdd={add} />)
  const amount = await screen.findByLabelText('Blanco · L · LAST')
  expect(vi.mocked(apiFetch).mock.calls).toHaveLength(2)
  fireEvent.change(amount, { target: { value: '0.5' } })
  fireEvent.submit(
    screen.getByRole('button', { name: 'Agregar combinaciones al comprobante' }).closest('form')!,
  )
  expect(screen.getByRole('alert').textContent).toContain('cantidades enteras')
  expect(add).not.toHaveBeenCalled()
  fireEvent.change(amount, { target: { value: '2' } })
  fireEvent.change(screen.getByLabelText('Costo unitario común'), { target: { value: '15' } })
  fireEvent.click(screen.getByRole('button', { name: 'Agregar combinaciones al comprobante' }))
  expect(add).toHaveBeenCalledWith([
    { articleId: 201, label: 'LAST · Remera L', quantity: 2, unitCost: 15, wholeUnits: true },
  ])
})
it('shows a failed query and never allows adding an incomplete variant list', async () => {
  vi.mocked(apiFetch)
    .mockReset()
    .mockResolvedValue(Response.json({}, { status: 503 }))
  render(<PurchaseVariantsDialog article={base} onClose={() => undefined} onAdd={vi.fn()} />)
  await waitFor(() =>
    expect(screen.getByRole('alert').textContent).toContain('No se pudieron consultar'),
  )
  expect(
    (
      screen.getByRole('button', {
        name: 'Agregar combinaciones al comprobante',
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true)
})
