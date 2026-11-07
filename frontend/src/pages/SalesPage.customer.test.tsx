import { fireEvent, render, screen, within, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { SalesPage } from './SalesPage'
import { apiFetch } from '../lib/http'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
const request = vi.mocked(apiFetch)
beforeEach(() => {
  request.mockReset()
  request.mockImplementation(async (url, options) => {
    if (url.includes('/cash/status')) return Response.json({ estado: 'ABIERTA' })
    if (url.includes('/lookups/customers'))
      return Response.json([{ id: 99, codigo: 'CF', nombre: 'Consumidor final' }])
    if (url === '/api/v1/operations/customers' && options?.method === 'POST')
      return Response.json({ id: 42, codigo: 'CLI-42', nombre: 'Ana Pérez' })
    if (url.includes('/alon/articles'))
      return Response.json([
        { id: 1, codigo: 'A1', nombre: 'Martillo', stock: 10, precio_venta: 100 },
      ])
    return Response.json([])
  })
})
it('creates and selects a customer while preserving sale items and date', async () => {
  const { container } = render(<SalesPage onOpenCash={() => undefined} />)
  fireEvent.click(screen.getByRole('button', { name: /Nueva venta/ }))
  const article = screen.getByRole('combobox', { name: 'Artículo' })
  fireEvent.change(article, { target: { value: 'Martillo' } })
  fireEvent.click(await screen.findByRole('option', { name: /A1.*Martillo/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
  expect(screen.getByText('Artículos de esta venta · 1')).toBeTruthy()
  const date = container.querySelector<HTMLInputElement>('input[name="date"]')!
  fireEvent.change(date, { target: { value: '04/11/2026' } })
  fireEvent.click(screen.getByRole('button', { name: /Crear cliente nuevo sin salir/ }))
  const dialog = screen.getByRole('dialog', { name: 'Nuevo cliente para esta venta' })
  fireEvent.change(within(dialog).getByLabelText('Nombre y apellido'), {
    target: { value: 'Ana Pérez' },
  })
  fireEvent.change(within(dialog).getByLabelText('Teléfono'), { target: { value: '123456789' } })
  fireEvent.change(within(dialog).getByLabelText('DNI (solo números)'), { target: { value: '30111222' } })
  fireEvent.keyDown(within(dialog).getByLabelText('Nombre y apellido'), { key: 'g', ctrlKey: true })
  expect(screen.queryByText('Confirmar venta')).toBeNull()
  fireEvent.click(within(dialog).getByRole('button', { name: 'Guardar y usar cliente' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  expect(container.querySelector<HTMLInputElement>('input[name="customerId"]')?.value).toBe('42')
  expect(screen.getByRole('combobox', { name: 'Cliente' }).getAttribute('value')).toContain(
    'Ana Pérez',
  )
  expect(screen.getByText('Artículos de esta venta · 1')).toBeTruthy()
  expect(date.value).toBe('04/11/2026')
  const call = request.mock.calls.find(
    ([url, options]) => url === '/api/v1/operations/customers' && options?.method === 'POST',
  )!
  expect(JSON.parse(String(call[1]?.body))).toEqual({
    name: 'Ana Pérez',
    dni: '30111222',
    phone: '123456789',
    email: '',
    creditLimit: 0,
  })
})
it('retains the customer form and sale when creation fails, and allows cancellation', async () => {
  request.mockImplementation(async (url) =>
    url === '/api/v1/operations/customers'
      ? Response.json({ error: 'No se pudo guardar.' }, { status: 400 })
      : Response.json([]),
  )
  render(<SalesPage onOpenCash={() => undefined} />)
  fireEvent.click(screen.getByRole('button', { name: /Nueva venta/ }))
  fireEvent.click(screen.getByRole('button', { name: /Crear cliente nuevo sin salir/ }))
  fireEvent.change(screen.getByLabelText('Nombre y apellido'), { target: { value: 'Ana Pérez' } })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar y usar cliente' }))
  expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'No se pudo guardar.')
  expect((screen.getByLabelText('Nombre y apellido') as HTMLInputElement).value).toBe('Ana Pérez')
  fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(screen.getByRole('button', { name: 'Guardar venta' })).toBeTruthy()
})
it('offers customer creation to a sales user without a separate customer permission', () => {
  render(<SalesPage onOpenCash={() => undefined} />)
  fireEvent.click(screen.getByRole('button', { name: /Nueva venta/ }))
  expect(screen.getByRole('button', { name: /Crear cliente nuevo sin salir/ })).toBeTruthy()
})
