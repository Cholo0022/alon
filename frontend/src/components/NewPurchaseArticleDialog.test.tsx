import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NewPurchaseArticleDialog } from './BarcodeEntry'
import { apiFetch } from '../lib/http'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
beforeEach(() =>
  vi
    .mocked(apiFetch)
    .mockReset()
    .mockImplementation(async (url) =>
      Response.json(
        url === '/api/v1/operations/purchases/articles' ? { id: 20, codigo: 'Z-38' } : [],
      ),
    ),
)
it('creates footwear inside purchases with the exact kind and no initial stock', async () => {
  const saved = vi.fn()
  render(<NewPurchaseArticleDialog onClose={() => undefined} onSaved={saved} />)
  fireEvent.change(screen.getByLabelText('Tipo de producto'), { target: { value: 'CALZADO' } })
  for (const [label, value] of [
    ['Código o código de barras', 'Z-38'],
    ['Nombre del artículo', 'Zapatilla'],
    ['Modelo / referencia', 'Z1'],
    ['Marca (o Sin marca)', 'Marca'],
    ['Talle de esta variante (S, M, L, 38, 40…)', '38'],
    ['Color de esta variante', 'Negro'],
    ['Material / composición', 'Tela'],
    ['Descripción para la página (medidas, cuidados y detalles)', 'Zapatilla de tela'],
  ])
    fireEvent.change(screen.getByLabelText(label), { target: { value } })
  fireEvent.submit(screen.getByRole('button', { name: 'Guardar' }).closest('form')!)
  await waitFor(() => expect(saved).toHaveBeenCalled())
  const call = vi
    .mocked(apiFetch)
    .mock.calls.find(([url]) => url === '/api/v1/operations/purchases/articles')!
  expect(JSON.parse(String(call[1]?.body))).toMatchObject({
    code: 'Z-38',
    initialStock: 0,
    categoryId: 0,
    unit: 'UNIDAD',
    apparel: { kind: 'CALZADO', size: '38', color: 'Negro' },
  })
})
