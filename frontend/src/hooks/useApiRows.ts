// Carga una página de registros y descarta respuestas anteriores cuando cambia la búsqueda.
import { useEffect, useState } from 'react'
import { apiFetch } from '../lib/http'

export type ApiRow = Record<string, unknown>
export type PagedRows = ApiRow[] & {
  page: number
  loading: boolean
  error: boolean
  hasMore: boolean
  total: number | null
  next: () => void
  previous: () => void
}

export function rowCount(rows: PagedRows, label: string): string {
  if (rows.loading) return 'Cargando…'
  if (rows.error) return 'No se pudo consultar la cantidad'
  return rows.total == null ? `${rows.length} ${label} en esta página` : `${rows.total} ${label}`
}

/** Carga y pagina registros de una consulta; expone carga, error y navegación y descarta respuestas que ya no corresponden a la búsqueda actual. */
export function useApiRows(endpoint: string | null): PagedRows {
  const [position, setPosition] = useState({ endpoint, page: 0 })
  const page = position.endpoint === endpoint ? position.page : 0
  const url = new URL(endpoint ?? '/', window.location.origin)
  url.searchParams.set('page', String(page))
  url.searchParams.set('size', '100')
  url.searchParams.set('includeTotal', 'true')
  const requestUrl = endpoint ? url.pathname + url.search : ''
  const [result, setResult] = useState({
    url: '',
    rows: [] as ApiRow[],
    error: false,
    total: null as number | null,
  })
  useEffect(() => {
    if (!requestUrl) return
    let controller = new AbortController()
    // El número de solicitud permite ignorar una respuesta vieja que llegue tarde.
    let sequence = 0
    /** Cancela la solicitud anterior, consulta la página actual y guarda únicamente la respuesta más reciente que siga vigente. */
    const load = async () => {
      controller.abort()
      controller = new AbortController()
      const current = ++sequence
      try {
        const response = await apiFetch(requestUrl, { signal: controller.signal })
        if (!response.ok) throw new Error('No se pudieron cargar los registros.')
        const rows: unknown = await response.json()
        if (!Array.isArray(rows)) throw new Error('La respuesta del servidor no es válida.')
        const count = response.headers.get('X-Total-Count')
        const total =
          count !== null && /^\d+$/.test(count) && Number.isSafeInteger(Number(count))
            ? Number(count)
            : null
        if (current === sequence)
          setResult({ url: requestUrl, rows: rows as ApiRow[], error: false, total })
      } catch {
        if (current === sequence && !controller.signal.aborted)
          setResult({ url: requestUrl, rows: [], error: true, total: null })
      }
    }
    /** Vuelve a cargar la página actual cuando se dispara una actualización del sistema. */
    const refresh = () => {
      void load()
    }
    const timer = window.setTimeout(refresh, 150)
    window.addEventListener('alon:refresh', refresh)
    return () => {
      sequence++
      controller.abort()
      window.clearTimeout(timer)
      window.removeEventListener('alon:refresh', refresh)
    }
  }, [requestUrl])
  const rows = result.url === requestUrl ? result.rows : []
  const total = result.url === requestUrl ? result.total : null
  return Object.assign([...rows], {
    page,
    loading: !!requestUrl && result.url !== requestUrl,
    error: result.url === requestUrl && result.error,
    total,
    hasMore: total === null ? rows.length === 100 : (page + 1) * 100 < total,
    next: () => setPosition({ endpoint, page: page + 1 }),
    previous: () => setPosition({ endpoint, page: Math.max(0, page - 1) }),
  })
}
