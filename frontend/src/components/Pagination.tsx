// Permite avanzar y volver entre páginas de resultados.
import { useEffect, useRef } from 'react'
import type { PagedRows } from '../hooks/useApiRows'

/** Muestra el número de página y los botones para recorrer los resultados, bloqueando acciones durante la carga. */
export function Pagination({ rows, label = 'registros' }: { rows: PagedRows; label?: string }) {
  const navigation = useRef<HTMLElement>(null)
  const navigating = useRef(false)
  useEffect(() => {
    if (rows.loading || !navigating.current) return
    navigating.current = false
    const panel = navigation.current?.closest('section')
    panel?.scrollIntoView?.({ block: 'start', behavior: 'instant' })
  }, [rows.loading, rows.page])
  return (
    <nav ref={navigation} aria-label={'Páginas de ' + label} className="pagination">
      <button
        type="button"
        disabled={rows.page === 0 || rows.loading}
        onClick={() => {
          navigating.current = true
          rows.previous()
        }}
      >
        Anterior
      </button>
      <span aria-live="polite">
        {rows.error
          ? 'No se pudieron cargar los datos'
          : rows.loading
            ? 'Cargando…'
            : `Página ${rows.page + 1} · ${rows.length} ${label} en esta página${rows.total == null ? '' : ` · ${rows.total} en total`}`}
      </span>
      <button
        type="button"
        disabled={!rows.hasMore || rows.loading}
        onClick={() => {
          navigating.current = true
          rows.next()
        }}
      >
        Siguiente
      </button>
    </nav>
  )
}
