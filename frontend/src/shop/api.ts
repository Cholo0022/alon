// Comunica la tienda web con el servidor y envía el token necesario para guardar cambios.
let csrf = ''
let loading: Promise<void> | undefined

export async function shopRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const mutation = options.method && options.method !== 'GET'
  if (mutation && !csrf) {
    loading ??= fetch('/api/v1/shop/session', { credentials: 'same-origin' })
      .then(async (response) => {
        if (!response.ok) throw new Error('No se pudo conectar con la tienda.')
        const body = (await response.json()) as { csrfToken: string }
        csrf = body.csrfToken
      })
      .finally(() => {
        loading = undefined
      })
    await loading
  }
  const headers = new Headers(options.headers)
  if (mutation) {
    headers.set('X-CSRF-Token', csrf)
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch('/api/v1/shop' + path, {
    ...options,
    headers,
    credentials: 'same-origin',
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok) {
    if (response.status === 403) csrf = ''
    throw new Error(body.error ?? 'No se pudo completar la solicitud. Volvé a intentar.')
  }
  if (path === '/session') csrf = body.csrfToken
  return body as T
}
