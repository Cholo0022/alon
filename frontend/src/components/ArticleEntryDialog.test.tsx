import { expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { ArticleEntryDialog } from './ArticleEntryDialog'
const article = { id: 1, codigo: 'A1', nombre: 'Martillo' }
it('requires a valid available quantity before adding a sale article', () => {
  const confirm = vi.fn()
  render(
    <ArticleEntryDialog
      article={article}
      mode="sale"
      initialQuantity="1"
      initialPrice={100}
      maxQuantity={2}
      message=""
      onConfirm={confirm}
      onCancel={() => undefined}
    />,
  )
  const quantity = screen.getByRole('spinbutton', { name: 'Cantidad' }) as HTMLInputElement
  fireEvent.change(quantity, { target: { value: '3' } })
  fireEvent.keyDown(quantity, { key: 'Enter' })
  expect(confirm).not.toHaveBeenCalled()
  expect(quantity.checkValidity()).toBe(false)
  fireEvent.change(quantity, { target: { value: '1.5' } })
  fireEvent.keyDown(quantity, { key: 'Enter' })
  expect(confirm).toHaveBeenCalledWith({ quantity: 1.5 })
})
it('moves from quantity to purchase cost and rejects a negative cost', () => {
  const confirm = vi.fn()
  render(
    <ArticleEntryDialog
      article={article}
      mode="purchase"
      initialQuantity="1"
      initialPrice={75}
      message=""
      onConfirm={confirm}
      onCancel={() => undefined}
    />,
  )
  const quantity = screen.getByRole('spinbutton', { name: 'Cantidad' })
  const cost = screen.getByRole('spinbutton', { name: 'Costo unitario' })
  fireEvent.change(quantity, { target: { value: '2.5' } })
  fireEvent.keyDown(quantity, { key: 'Enter' })
  expect(document.activeElement).toBe(cost)
  fireEvent.change(cost, { target: { value: '-1' } })
  fireEvent.keyDown(cost, { key: 'Enter' })
  expect(confirm).not.toHaveBeenCalled()
  fireEvent.change(cost, { target: { value: '12.5' } })
  fireEvent.keyDown(cost, { key: 'Enter' })
  expect(confirm).toHaveBeenCalledWith({ quantity: 2.5, unitCost: 12.5 })
})
