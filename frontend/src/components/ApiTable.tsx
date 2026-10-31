// Muestra datos del servidor en una tabla con páginas y formatos de importes y fechas.
import { type ReactNode } from 'react'
import {
  currency,
  formatDateDisplay,
  formatDateTimeDisplay,
  formatMonthDisplay,
} from '../lib/format'
import { useApiRows, type ApiRow } from '../hooks/useApiRows'
import { Pagination } from './Pagination'

export type ApiColumn = {
  key: string
  className?: string
  label: string
  money?: boolean
  date?: boolean
  dateTime?: boolean
  month?: boolean
  render?: (row: ApiRow) => ReactNode
}

export function ApiTable({
  title,
  endpoint,
  columns,
  note,
  onRowClick,
  className = '',
  rowClassName,
}: {
  className?: string
  rowClassName?: (row: ApiRow) => string | undefined
  title: string
  endpoint: string
  columns: ApiColumn[]
  note?: string
  onRowClick?: (row: ApiRow) => void
}) {
  const rows = useApiRows(endpoint)
  const loading = rows.loading
  const error = rows.error

  return (
    <section className={'directory-panel report-table ' + className}>
      <div className="directory-toolbar">
        <div>
          <span className="alon-tag">DATOS DEL COMERCIO</span>
          <strong>{title}</strong>
        </div>
        <span className="row-count">{loading ? 'Cargando…' : `${rows.length} registros`}</span>
      </div>
      {error ? (
        <div className="no-results">
          No se pudo conectar con la API local. Revisá que el backend esté iniciado.
        </div>
      ) : (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                {columns.map((column) => (
                  <th
                    key={column.key}
                    className={[column.money ? 'money-column' : '', column.className ?? '']
                      .filter(Boolean)
                      .join(' ')}
                  >
                    {column.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => (
                <tr
                  key={`${String(row.codigo ?? row.numero ?? row.id ?? row.fecha ?? index)}-${index}`}
                  className={
                    [onRowClick ? 'clickable-row' : '', rowClassName?.(row) ?? '']
                      .filter(Boolean)
                      .join(' ') || undefined
                  }
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                  onKeyDown={
                    onRowClick
                      ? (event) => {
                          if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault()
                            onRowClick(row)
                          }
                        }
                      : undefined
                  }
                  tabIndex={onRowClick ? 0 : undefined}
                >
                  {columns.map((column) => (
                    <td
                      data-label={column.label}
                      key={column.key}
                      className={[column.money ? 'money-cell' : '', column.className ?? '']
                        .filter(Boolean)
                        .join(' ')}
                    >
                      {column.render
                        ? column.render(row)
                        : column.money
                          ? currency.format(Number(row[column.key] ?? 0))
                          : column.dateTime
                            ? formatDateTimeDisplay(row[column.key])
                            : column.date
                              ? formatDateDisplay(row[column.key])
                              : column.month
                                ? formatMonthDisplay(row[column.key])
                                : String(row[column.key] ?? '—')}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
          {!loading && rows.length === 0 && (
            <div className="no-results">Este reporte no tiene registros.</div>
          )}
        </div>
      )}
      <Pagination rows={rows} label={title.toLocaleLowerCase('es-AR')} />
      {note && <div className="table-footer">{note}</div>}
    </section>
  )
}
