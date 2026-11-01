import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DeleteRecordButton } from './DeleteRecordButton'
import { apiFetch } from '../lib/http'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
beforeEach(() => {
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
afterEach(() => vi.restoreAllMocks())
it('defaults to Cancel and Enter never deletes the record', async () => {
  const user = userEvent.setup()
  render(<DeleteRecordButton endpoint="/api/v1/operations/customers/3" name="Cliente de prueba" />)
  await user.click(screen.getByRole('button', { name: 'Eliminar' }))
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Cancelar' }))
  await user.keyboard('{Enter}')
  expect(apiFetch).not.toHaveBeenCalled()
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Eliminar' }))
})
it('only deletes after explicit confirmation', async () => {
  vi.mocked(apiFetch).mockResolvedValue(Response.json({ eliminado: true }))
  const user = userEvent.setup()
  const saved = vi.fn()
  render(
    <DeleteRecordButton
      endpoint="/api/v1/operations/articles/4"
      name="Artículo"
      onDeleted={saved}
    />,
  )
  await user.click(screen.getByRole('button', { name: 'Eliminar' }))
  expect(apiFetch).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Sí, eliminar definitivamente' }))
  expect(apiFetch).toHaveBeenCalledWith('/api/v1/operations/articles/4', { method: 'DELETE' })
  expect(saved).toHaveBeenCalledOnce()
})
it('explains linked records and keeps the dialog when deletion is rejected', async () => {
  vi.mocked(apiFetch).mockResolvedValue(
    Response.json({ error: 'El registro tiene operaciones vinculadas.' }, { status: 409 }),
  )
  const user = userEvent.setup()
  render(<DeleteRecordButton endpoint="/api/v1/operations/suppliers/5" name="Proveedor" />)
  await user.click(screen.getByRole('button', { name: 'Eliminar' }))
  await user.click(screen.getByRole('button', { name: 'Sí, eliminar definitivamente' }))
  expect((await screen.findByRole('alert')).textContent).toBe(
    'El registro tiene operaciones vinculadas.',
  )
  expect(screen.getByRole('dialog')).toBeTruthy()
})
