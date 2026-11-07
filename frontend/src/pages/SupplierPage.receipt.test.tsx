import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { SupplierList } from './SupplierPage'
import { apiFetch } from '../lib/http'

vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
beforeEach(() => {
  vi.mocked(apiFetch)
    .mockReset()
    .mockImplementation(async (url, options) => {
      if (options?.method === 'POST') return Response.json({ recibo: 'REC-1' })
      return Response.json(
        url.includes('/alon/suppliers')
          ? [
              {
                id: 1,
                codigo: 'P1',
                nombre: 'Proveedor prueba',
                saldo_pendiente: 100,
                estado: 'ACTIVO',
              },
            ]
          : [],
      )
    })
  HTMLElement.prototype.scrollIntoView = vi.fn()
})

it.each(['yes', 'no'])(
  'sends the explicit cash choice %s with the supplier receipt',
  async (choice) => {
    render(<SupplierList />)
    fireEvent.click(screen.getByRole('button', { name: 'Ver proveedores a los que debo' }))
    fireEvent.click(await screen.findByRole('button', { name: /Hacer recibo/ }))
    const selection = screen.getByLabelText('¿Vas a sacar dinero de caja para realizar este pago?')
    const form = selection.closest('form')!
    fireEvent.submit(form)
    expect(vi.mocked(apiFetch).mock.calls.some(([, options]) => options?.method === 'POST')).toBe(
      false,
    )
    fireEvent.change(selection, { target: { value: choice } })
    fireEvent.submit(form)
    await waitFor(() =>
      expect(vi.mocked(apiFetch).mock.calls.some(([, options]) => options?.method === 'POST')).toBe(
        true,
      ),
    )
    const call = vi.mocked(apiFetch).mock.calls.find(([, options]) => options?.method === 'POST')!
    expect(JSON.parse(String(call[1]?.body))).toMatchObject({
      supplierId: 1,
      amount: 100,
      useCash: choice === 'yes',
    })
  },
)
