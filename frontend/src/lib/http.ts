// Centraliza las solicitudes de Alon y conserva la clave de una operación cuando debe reintentarse.
type PendingCommand = { key: string; created: number }
const pending = new Map<string, PendingCommand>()
let csrfToken = ''
let bootstrap: Promise<void> | null = null
let activeRequests = 0

/** Actualiza la cantidad de solicitudes activas y emite el evento que controla el indicador global de carga. */
function activity(delta: number) {
  activeRequests += delta
  window.dispatchEvent(new CustomEvent('alon:activity', { detail: activeRequests }))
}

/** Obtiene una sola vez el token de protección de la sesión y comparte la consulta entre solicitudes simultáneas. */
async function ensureCsrf() {
  if (csrfToken) return
  bootstrap ??= fetch('/api/v1/auth/status', { credentials: 'same-origin' })
    .then(async (response) => {
      if (!response.ok) throw new Error('No se pudo iniciar la conexión segura.')
      const status = (await response.json()) as { csrfToken?: string }
      if (!status.csrfToken) throw new Error('No se pudo iniciar la conexión segura.')
      csrfToken = status.csrfToken
    })
    .finally(() => {
      bootstrap = null
    })
  await bootstrap
}

/** Centraliza solicitudes con sesión, CSRF, seguimiento de actividad y claves de operación para reintentos sin duplicar escrituras. */
export async function apiFetch(url: string, options: RequestInit = {}): Promise<Response> {
  const method = (options.method ?? 'GET').toUpperCase()
  const mutation = !['GET', 'HEAD', 'OPTIONS'].includes(method)
  activity(1)
  try {
    if (mutation) await ensureCsrf()
    const headers = new Headers(options.headers)
    if (mutation) headers.set('X-CSRF-Token', csrfToken)
    // Reconoce un reintento para no volver a registrar el mismo movimiento.
    const signature = `${method}:${url}:${String(options.body ?? '')}`
    if (mutation && url.startsWith('/api/v1/operations/')) {
      const now = Date.now()
      for (const [key, value] of pending) if (now - value.created > 86_400_000) pending.delete(key)
      const command = pending.get(signature) ?? { key: crypto.randomUUID(), created: now }
      pending.set(signature, command)
      headers.set('Idempotency-Key', command.key)
    }
    /** Envía la solicitud con los encabezados preparados y las credenciales de la sesión actual. */
    const send = () => fetch(url, { ...options, headers, credentials: 'same-origin' })
    const response = await send().catch((error: unknown) => {
      if (!mutation || options.signal?.aborted) throw error
      return send()
    })
    if (response.ok || response.status < 500) pending.delete(signature)
    if (url === '/api/v1/auth/status' && response.ok) {
      const status = (await response.clone().json()) as { csrfToken?: string }
      csrfToken = status.csrfToken ?? ''
    }
    if (url === '/api/v1/auth/logout' && response.ok) {
      csrfToken = ''
      pending.clear()
    }
    if (response.status === 401) {
      csrfToken = ''
      window.dispatchEvent(new Event('alon:session-expired'))
    }
    if (!response.ok) {
      const body = (await response
        .clone()
        .json()
        .catch(() => ({}))) as { error?: string }
      if (body.error?.startsWith('La licencia del negocio venció'))
        window.dispatchEvent(new Event('alon:license-required'))
      window.dispatchEvent(
        new CustomEvent('alon:api-error', {
          detail: body.error ?? 'No se pudieron cargar los datos. Volvé a intentar.',
        }),
      )
    }
    return response
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError')) {
      window.dispatchEvent(
        new CustomEvent('alon:api-error', {
          detail: 'No se pudo conectar. Revisá la conexión y volvé a intentar.',
        }),
      )
    }
    throw error
  } finally {
    activity(-1)
  }
}
