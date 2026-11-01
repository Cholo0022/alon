import { expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LocalEntryPanel, type EntryField } from './Forms'
import { apiFetch } from '../lib/http'

vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
const fields: EntryField[] = [
  { name: 'name', label: 'Nombre', defaultValue: 'Artículo de prueba' },
  { name: 'cost', label: 'Costo', type: 'number', defaultValue: '200' },
  {
    name: 'freightPercent',
    label: 'Flete (% del costo)',
    type: 'number',
    defaultValue: '10',
  },
  { name: 'marginPercent', label: 'Margen', type: 'number', defaultValue: '25' },
]

it('calculates and saves freight only as a percentage', async () => {
  vi.mocked(apiFetch)
    .mockReset()
    .mockResolvedValue(Response.json({ id: 1 }))
  const user = userEvent.setup()
  render(
    <LocalEntryPanel
      title="Artículo"
      action="Cargar"
      endpoint="/api/v1/operations/articles"
      fields={fields}
      pricePreview
      initialOpen
    />,
  )
  expect(screen.queryByRole('checkbox', { name: 'Aplicar flete porcentual' })).toBeNull()
  await user.clear(screen.getByRole('spinbutton', { name: 'Flete (% del costo)' }))
  await user.type(screen.getByRole('spinbutton', { name: 'Flete (% del costo)' }), '10')
  expect(screen.getByText(/275,00/)).toBeTruthy()
  await user.click(screen.getByRole('button', { name: 'Guardar' }))
  await waitFor(() => expect(apiFetch).toHaveBeenCalled())
  const payload = JSON.parse(String(vi.mocked(apiFetch).mock.calls[0][1]?.body))
  expect(payload.freightPercent).toBe(10)
  expect(payload.freightPerUnit).toBe(0)
  expect(payload.freightByPercent).toBe(true)
  expect(payload.freightByValue).toBe(false)
})
