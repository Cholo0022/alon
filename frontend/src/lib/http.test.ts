import { beforeEach, expect, it, vi } from 'vitest'

beforeEach(() => vi.resetModules())

it('reuses one operation key if the response is lost and adds CSRF protection', async () => {
  const requests: RequestInit[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, options?: RequestInit) => {
      if (url.endsWith('/auth/status')) return Response.json({ csrfToken: 'session-token' })
      requests.push(options ?? {})
      if (requests.length === 1) throw new TypeError('connection lost after commit')
      return Response.json({ id: 42 })
    }),
  )
  const { apiFetch } = await import('./http')
  const response = await apiFetch('/api/v1/operations/sales', { method: 'POST', body: '{}' })
  expect(response.ok).toBe(true)
  expect(requests).toHaveLength(2)
  const first = new Headers(requests[0].headers),
    second = new Headers(requests[1].headers)
  expect(first.get('X-CSRF-Token')).toBe('session-token')
  expect(first.get('Idempotency-Key')).toBeTruthy()
  expect(first.get('Idempotency-Key')).toBe(second.get('Idempotency-Key'))
})

it('notifies the app when an authenticated session has been revoked', async () => {
  const expired = vi.fn()
  window.addEventListener('alon:session-expired', expired)
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => Response.json({ error: 'Sesión vencida' }, { status: 401 })),
  )
  const { apiFetch } = await import('./http')
  await apiFetch('/api/v1/demo/customers')
  expect(expired).toHaveBeenCalledOnce()
  window.removeEventListener('alon:session-expired', expired)
})
