import { expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { PaymentConfirmation } from './PaymentConfirmation'
it('requires a medium for contado and fixes efectivo automatically for contado de caja', () => {
  const confirm = vi.fn(),
    cancel = vi.fn()
  render(
    <PaymentConfirmation
      mode="purchase"
      total={200}
      partyName="Proveedor"
      busy={false}
      message=""
      onConfirm={confirm}
      onCancel={cancel}
      onChange={() => undefined}
    />,
  )
  const condition = screen.getByLabelText('Condición de pago'),
    method = screen.getByLabelText('Medio de pago') as HTMLSelectElement
  expect(method.disabled).toBe(true)
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar factura' }))
  expect(confirm).not.toHaveBeenCalled()
  expect(screen.getByRole('option', { name: 'CONTADO' })).toBeTruthy()
  expect(screen.getByRole('option', { name: 'CONTADO DE CAJA' })).toBeTruthy()
  fireEvent.change(condition, { target: { value: 'EFECTIVO' } })
  expect(method.disabled).toBe(false)
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar factura' }))
  expect(confirm).not.toHaveBeenCalled()
  fireEvent.change(method, { target: { value: 'TRANSFERENCIA' } })
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar factura' }))
  expect(confirm).toHaveBeenLastCalledWith({
    condition: 'EFECTIVO',
    paymentMethod: 'TRANSFERENCIA',
  })
  fireEvent.change(condition, { target: { value: 'EFECTIVO_CAJA' } })
  expect(method.value).toBe('EFECTIVO')
  expect(method.disabled).toBe(true)
  expect(screen.queryByRole('option', { name: 'Transferencia' })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar factura' }))
  expect(confirm).toHaveBeenLastCalledWith({
    condition: 'EFECTIVO_CAJA',
    paymentMethod: 'EFECTIVO',
  })
  fireEvent.change(condition, { target: { value: 'EFECTIVO' } })
  expect(method.disabled).toBe(false)
  expect(method.value).toBe('')
})
it('resets the medium when changing the sale type and allows cancelling', () => {
  const confirm = vi.fn(),
    cancel = vi.fn()
  render(
    <PaymentConfirmation
      mode="sale"
      total={200}
      partyName="Cliente"
      busy={false}
      message=""
      onConfirm={confirm}
      onCancel={cancel}
      onChange={() => undefined}
    />,
  )
  const condition = screen.getByLabelText('Tipo de venta'),
    method = screen.getByLabelText('Medio de pago') as HTMLSelectElement
  fireEvent.change(condition, { target: { value: 'CONTADO' } })
  fireEvent.change(method, { target: { value: 'EFECTIVO' } })
  fireEvent.change(condition, { target: { value: 'CUENTA_CORRIENTE' } })
  expect(screen.queryByLabelText('Medio de pago')).toBeNull()
  expect(screen.queryByRole('option', { name: 'Efectivo' })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar venta' }))
  expect(confirm).toHaveBeenCalledWith({
    condition: 'CUENTA_CORRIENTE',
    paymentMethod: 'CUENTA_CORRIENTE',
  })
  fireEvent.click(screen.getByRole('button', { name: 'Volver a la carga' }))
  expect(cancel).toHaveBeenCalledOnce()
})
