import { expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { BarcodeEntry } from './BarcodeEntry'
import { SalesPage } from '../pages/SalesPage'
import { Combobox } from './Combobox'

it('hides suggestions until Enter or typing', () => {
  render(
    <Combobox
      label="Rubro"
      value=""
      options={[{ value: '1', label: 'Ropa' }]}
      onChange={() => {}}
      onChoose={() => {}}
    />,
  )
  const input = screen.getByRole('combobox')
  fireEvent.focus(input)
  expect(screen.queryByRole('option')).toBeNull()
  fireEvent.keyDown(input, { key: 'Enter' })
  expect(screen.getByRole('option').textContent).toBe('Ropa')
  fireEvent.blur(input)
  fireEvent.focus(input)
  expect(screen.queryByRole('option')).toBeNull()
})
it('opens purchase creation with the exact code, without supplier or initial stock', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => Response.json([])),
  )
  render(<BarcodeEntry purchase onSelect={vi.fn()} />)
  const input = screen.getByRole('textbox', { name: 'Código de barras' })
  fireEvent.change(input, { target: { value: '00999' } })
  fireEvent.keyDown(input, { key: 'Enter' })
  const dialog = await screen.findByRole('dialog')
  expect(
    (within(dialog).getByLabelText('Código o código de barras') as HTMLInputElement).value,
  ).toBe('00999')
  expect(dialog.querySelector('[name="supplierId"]')).toBeNull()
  expect(dialog.querySelector('[name="initialStock"]')).toBeNull()
})
it('focuses quantity after scanning a sale and waits for confirmation', async () => {
  const article = { id: 4, codigo: '00123', nombre: 'Remera', stock: 10, precio_venta: 500 }
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) =>
      Response.json(
        url.includes('/cash/status')
          ? { estado: 'ABIERTA' }
          : url.includes('code=00123')
            ? [article]
            : [],
      ),
    ),
  )
  render(<SalesPage onOpenCash={() => {}} />)
  fireEvent.click(screen.getByRole('button', { name: /Nueva venta/ }))
  const input = screen.getByRole('textbox', { name: 'Código de barras' })
  fireEvent.change(input, { target: { value: '00123' } })
  fireEvent.keyDown(input, { key: 'Enter' })
  const dialog = await screen.findByRole('dialog')
  const quantity = within(dialog).getByLabelText('Cantidad')
  await waitFor(() => expect(document.activeElement).toBe(quantity))
  expect(screen.queryByText('Artículos de esta venta · 1')).toBeNull()
  fireEvent.change(quantity, { target: { value: '2' } })
  fireEvent.keyDown(quantity, { key: 'Enter' })
  await screen.findByText('Artículos de esta venta · 1')
})
