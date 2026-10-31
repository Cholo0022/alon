// Permite avanzar y volver entre páginas de resultados.
import type { PagedRows } from '../hooks/useApiRows'

/** Muestra el número de página y los botones para recorrer los resultados, bloqueando acciones durante la carga. */
export function Pagination({ rows, label = 'registros' }: { rows: PagedRows; label?: string }) {
  return (
    <nav aria-label={'Páginas de ' + label} className="pagination">
      <button type="button" disabled={rows.page === 0 || rows.loading} onClick={rows.previous}>
        Anterior
      </button>
      <span aria-live="polite">
        {rows.error
          ? 'No se pudieron cargar los datos'
          : rows.loading
            ? 'Cargando…'
            : `Página ${rows.page + 1} · ${rows.length} ${label}`}
      </span>
      <button type="button" disabled={!rows.hasMore || rows.loading} onClick={rows.next}>
        Siguiente
      </button>
    </nav>
  )
}
