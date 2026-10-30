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
    name: 'freightAmount',
    label: 'Flete',
    type: 'number',
    defaultValue: '10',
    checkboxName: 'freightByPercent',
    checkboxLabel: 'Aplicar flete porcentual',
    checkedByDefault: true,
  },
  { name: 'marginPercent', label: 'Margen', type: 'number', defaultValue: '25' },
]

it.each([true, false])(
  'calculates and saves the single freight value with percentage mode %s',
  async (percentage) => {
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
    const checkbox = screen.getByRole('checkbox', {
      name: 'Aplicar flete porcentual',
    }) as HTMLInputElement
    expect(checkbox.checked).toBe(true)
    if (!percentage) await user.click(checkbox)
    else {
      await user.clear(screen.getByRole('spinbutton', { name: 'Flete' }))
      await user.type(screen.getByRole('spinbutton', { name: 'Flete' }), '10')
    }
    expect(screen.getByText(percentage ? /275,00/ : /262,50/)).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(apiFetch).toHaveBeenCalled())
    const payload = JSON.parse(String(vi.mocked(apiFetch).mock.calls[0][1]?.body))
    expect(payload.freightByPercent).toBe(percentage)
    expect(payload.freightByValue).toBe(!percentage)
    expect(payload.freightPerUnit).toBe(percentage ? 0 : 10)
    expect(payload.freightPercent).toBe(percentage ? 10 : 0)
    expect(payload.freightAmount).toBeUndefined()
  },
)
