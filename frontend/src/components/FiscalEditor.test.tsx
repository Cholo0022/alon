import { beforeEach, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FiscalCapture, FiscalEditor } from './FiscalEditor'
import { apiFetch } from '../lib/http'
let taxMode = false
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
vi.mock('../hooks/useAccountingOptions', () => ({
  useAccountingOptions: () => ({ cargaImpuestos: taxMode, controlPeriodos: false }),
}))
beforeEach(() => {
  taxMode = false
  vi.mocked(apiFetch).mockReset()
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.setAttribute('open', '')
    },
  })
  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.removeAttribute('open')
    },
  })
})
const target = {
  clase: 'COMPRA' as const,
  id: 7,
  fecha: '2026-10-28',
  total: 121,
  tercero: 'Proveedor de prueba',
  documento: '20123456786',
}
it('keeps the simple entry flow when taxes are disabled', () => {
  render(<FiscalCapture />)
  act(() => {
    window.dispatchEvent(new CustomEvent('alon:fiscal-target', { detail: target }))
  })
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(apiFetch).not.toHaveBeenCalled()
})
it('opens the optional fiscal step only after the operation is saved and never infers VAT from its total', async () => {
  taxMode = true
  render(<FiscalCapture />)
  act(() => {
    window.dispatchEvent(new CustomEvent('alon:fiscal-target', { detail: target }))
  })
  expect(screen.getByRole('dialog')).toBeTruthy()
  expect((screen.getByLabelText('Neto 21%') as HTMLInputElement).value).toBe('0')
  expect((screen.getByLabelText('IVA 21%') as HTMLInputElement).value).toBe('0')
  expect((screen.getByLabelText(/Crédito fiscal computable/) as HTMLInputElement).value).toBe('0')
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Completar después' }))
  await userEvent.keyboard('{Enter}')
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(apiFetch).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: 'Completar impuestos' })).toBeTruthy()
})
it('saves the actual breakdown and selected credit without registering the purchase twice', async () => {
  vi.mocked(apiFetch).mockResolvedValue(Response.json({ id: 8 }))
  const saved = vi.fn()
  render(<FiscalEditor target={target} onSaved={saved} onClose={vi.fn()} />)
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Número'), '345')
  await user.clear(screen.getByLabelText('Neto 21%'))
  await user.type(screen.getByLabelText('Neto 21%'), '100')
  await user.clear(screen.getByLabelText('IVA 21%'))
  await user.type(screen.getByLabelText('IVA 21%'), '21')
  await user.clear(screen.getByLabelText(/Crédito fiscal computable/))
  await user.type(screen.getByLabelText(/Crédito fiscal computable/), '15')
  await user.click(
    screen.getByRole('checkbox', { name: 'Datos controlados para el informe del contador' }),
  )
  await user.click(screen.getByRole('button', { name: 'Guardar desglose fiscal' }))
  expect(saved).toHaveBeenCalledOnce()
  expect(apiFetch).toHaveBeenCalledOnce()
  const [url, options] = vi.mocked(apiFetch).mock.calls[0]
  expect(url).toBe('/api/v1/operations/purchases/7/fiscal')
  const body = JSON.parse(String(options?.body)) as Record<string, unknown>
  expect(body.creditoComputable).toBe(15)
  expect(body.total).toBe(121)
  expect(body.documento).toBe('20123456786')
  expect(body.alicuotas).toEqual([{ alicuota: 21, neto: 100, iva: 21 }])
})
