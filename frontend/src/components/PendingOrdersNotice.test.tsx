import { act, fireEvent, render, screen, cleanup } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { PendingOrdersNotice } from './PendingOrdersNotice'
import { apiFetch } from '../lib/http'

vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
beforeEach(() => {
  vi.useFakeTimers()
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
  vi.mocked(apiFetch).mockReset()
})
afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

it('shows the singular, updates automatically and disappears after the last confirmation', async () => {
  let count = 1
  vi.mocked(apiFetch).mockImplementation(async () => Response.json({ count }))
  const open = vi.fn()
  render(<PendingOrdersNotice onOpen={open} />)
  await act(async () => {})
  expect(screen.getByRole('status').textContent).toContain('1 venta web pendiente')
  fireEvent.click(screen.getByRole('button', { name: 'Ver pedidos' }))
  expect(open).toHaveBeenCalledOnce()
  count = 130
  await act(async () => {
    await vi.advanceTimersByTimeAsync(10000)
  })
  expect(screen.getByRole('status').textContent).toContain('130 ventas web pendientes')
  count = 0
  await act(async () => {
    window.dispatchEvent(new Event('alon:refresh'))
  })
  expect(screen.queryByRole('status')).toBeNull()
})

it('refreshes on returning to Alon and does not poll while the page is hidden', async () => {
  const visibility = vi.spyOn(document, 'visibilityState', 'get')
  vi.mocked(apiFetch).mockResolvedValue(Response.json({ count: 0 }))
  render(<PendingOrdersNotice onOpen={() => {}} />)
  await act(async () => {})
  const initial = vi.mocked(apiFetch).mock.calls.length
  visibility.mockReturnValue('hidden')
  await act(async () => {
    await vi.advanceTimersByTimeAsync(20000)
  })
  expect(apiFetch).toHaveBeenCalledTimes(initial)
  visibility.mockReturnValue('visible')
  vi.mocked(apiFetch).mockImplementation(async () => Response.json({ count: 2 }))
  await act(async () => {
    document.dispatchEvent(new Event('visibilitychange'))
  })
  expect(screen.getByRole('status').textContent).toContain('2 ventas web pendientes')
})

it('reports a real connection failure without presenting a false zero', async () => {
  vi.mocked(apiFetch).mockRejectedValue(new Error('Sin conexión'))
  render(<PendingOrdersNotice onOpen={() => {}} />)
  await act(async () => {})
  expect(screen.getByRole('status').textContent).toContain('No se pudo actualizar')
  vi.mocked(apiFetch).mockImplementation(async () => Response.json({ count: 3 }))
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
  })
  expect(screen.getByRole('status').textContent).toContain('3 ventas web pendientes')
})
