import { beforeEach, expect, test, vi } from 'vitest'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { UsersPage } from './UsersPage'

const { request, rows } = vi.hoisted(() => ({
  request: vi.fn(),
  rows: [
    { id: 2, usuario: 'Nuevo', rol: 'VENDEDOR', activo: true, can_delete: true },
    { id: 3, usuario: 'Con ventas', rol: 'VENDEDOR', activo: true, can_delete: false },
  ],
}))
vi.mock('../lib/http', () => ({ apiFetch: request }))
vi.mock('../hooks/useApiRows', () => ({ useApiRows: () => rows }))
vi.mock('../components/ApiTable', () => ({ ApiTable: () => null }))
vi.mock('../components/Pagination', () => ({ Pagination: () => null }))
beforeEach(() => {
  request.mockReset().mockResolvedValue(Response.json({ ok: true }))
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
})

test('only unused users offer deletion, defaults to No and Enter cancels', () => {
  render(<UsersPage />)
  expect(screen.getAllByRole('button', { name: 'Dar de baja' })).toHaveLength(1)
  fireEvent.click(screen.getByRole('button', { name: 'Dar de baja' }))
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'No eliminar' }))
  fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Enter' })
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(request).not.toHaveBeenCalled()
})

test('requires an explicit click to delete and keeps deactivation for users with history', async () => {
  render(<UsersPage />)
  fireEvent.click(screen.getByRole('button', { name: 'Dar de baja' }))
  fireEvent.click(
    within(screen.getByRole('dialog')).getByRole('button', { name: 'Eliminar usuario' }),
  )
  await screen.findByText('Usuario sin movimientos eliminado definitivamente.')
  expect(request).toHaveBeenCalledWith('/api/v1/auth/users/2', { method: 'DELETE', retry: false })
  expect(screen.getAllByRole('button', { name: 'Desactivar' })).toHaveLength(2)
})

test('deactivates a user with movements without requesting deletion', async () => {
  render(<UsersPage />)
  const row = screen.getByText('Con ventas').closest('.user-access-row') as HTMLElement
  expect(within(row).queryByRole('button', { name: 'Dar de baja' })).toBeNull()
  fireEvent.click(within(row).getByRole('button', { name: 'Desactivar' }))
  await screen.findByText('Usuario desactivado. Su historial se conserva.')
  expect(request).toHaveBeenCalledWith(
    '/api/v1/auth/users/3/active',
    expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ active: false }) }),
  )
  expect(request.mock.calls.some(([, options]) => options.method === 'DELETE')).toBe(false)
})
