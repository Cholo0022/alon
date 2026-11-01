import { ApparelEntry } from './ApparelFields'
import { expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { LocalEntryPanel } from './Forms'
import { apparelFields } from '../lib/apparelFields'
import { apiFetch } from '../lib/http'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
const row = {
  tipo_producto: 'CALZADO',
  modelo: 'Urbano',
  marca: 'Marca',
  talle: '38',
  sistema_talle: 'AR',
  color: 'Negro',
  material: 'Cuero',
  publico: 'UNISEX',
  temporada: 'TODO EL AÑO',
  descripcion: 'Calzado de cuero. Precio por par.',
}
function editor(stock = '0') {
  return (
    <LocalEntryPanel
      title="Nueva variante"
      action="Guardar variante"
      initialOpen
      endpoint="/api/v1/operations/articles"
      articlePhotos
      fields={[
        { name: 'name', label: 'Nombre', defaultValue: 'Urbano' },
        { name: 'code', label: 'Código', defaultValue: 'Z38N' },
        { name: 'unit', label: 'Unidad', defaultValue: 'UNIDAD' },
        { name: 'initialStock', label: 'Stock', type: 'number', defaultValue: stock },
        ...apparelFields(row),
      ]}
    />
  )
}
it('saves the shoe variant with its own stock, publication attributes and photos without mixing sizes', async () => {
  vi.mocked(apiFetch)
    .mockReset()
    .mockResolvedValue(Response.json({ id: 2 }))
  render(editor())
  fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'urbano' } })
  const description = screen.getByLabelText(
    'Descripción para la página (medidas, cuidados y detalles)',
  )
  fireEvent.change(description, { target: { value: 'zapato cómodo. cuidar el cuero.' } })
  fireEvent.blur(description)
  expect((description as HTMLTextAreaElement).value).toBe('Zapato cómodo. Cuidar el cuero.')
  fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))
  await waitFor(() => expect(apiFetch).toHaveBeenCalledOnce())
  const body = JSON.parse(String(vi.mocked(apiFetch).mock.calls[0][1]?.body))
  expect(body.name).toBe('Urbano · 38 · Negro')
  expect(body.apparel.description).toBe('Zapato cómodo. Cuidar el cuero.')
  expect(body.apparel).toMatchObject({
    kind: 'CALZADO',
    size: '38',
    color: 'Negro',
    sizeSystem: 'AR',
    model: 'Urbano',
    material: 'Cuero',
  })
  expect(body.initialStock).toBe(0)
  expect(body.categoryId).toBe(0)
  expect(body.supplierId).toBe(0)
  expect(body.freightByValue).toBe(false)
  expect(body.freightByPercent).toBe(true)
  expect(body.freightPerUnit).toBe(0)
  expect(body.photos).toEqual([])
  expect(body.apparelKind).toBeUndefined()
})
it('rejects fractional stock before sending a shoe variant', async () => {
  vi.mocked(apiFetch).mockReset()
  render(editor('0.5'))
  fireEvent.submit(screen.getByRole('button', { name: 'Guardar' }).closest('form')!)
  expect(await screen.findByText('Cargá cantidades enteras para cada talle y color.')).toBeTruthy()
  expect(apiFetch).not.toHaveBeenCalled()
})

it('does not ask for a garment category when creating apparel or footwear', () => {
  vi.mocked(apiFetch).mockReset().mockResolvedValue(Response.json([]))
  render(<ApparelEntry />)
  fireEvent.click(screen.getByRole('button', { name: /Cargar indumentaria/ }))
  expect(screen.getByLabelText('Nombre de publicación (sin talle ni color)')).toBeTruthy()
  expect(screen.queryByLabelText(/Tipo de prenda o calzado/)).toBeNull()
  expect(document.querySelector('[name="categoryId"]')).toBeNull()
  expect(document.querySelector('[name="supplierId"]')).toBeNull()
})
