import { useId, useState } from 'react'
import { useApiRows, type ApiRow } from '../hooks/useApiRows'
import { textValue } from '../lib/format'

/** One field searches the entire directory and keeps the chosen record independently of results. */
export function LookupField({
  name,
  label,
  endpoint,
  required = true,
  autoFocus = false,
  onSelect,
  initialRecord,
}: {
  name: string
  label: string
  endpoint: string
  required?: boolean
  autoFocus?: boolean
  initialRecord?: ApiRow
  onSelect?: (row: ApiRow | null) => void
}) {
  const listId = useId()
  const [query, setQuery] = useState(initialRecord ? textValue(initialRecord.nombre) : '')
  const [selected, setSelected] = useState<ApiRow | null>(initialRecord ?? null)
  const rows = useApiRows(endpoint + '?q=' + encodeURIComponent(selected ? '' : query))
  const labelFor = (row: ApiRow) =>
    [row.codigo, row.nombre].filter(Boolean).map(textValue).join(' · ')
  return (
    <div className="searchable-field">
      <input type="hidden" name={name} value={selected ? textValue(selected.id) : ''} />
      <input
        type="text"
        list={listId}
        aria-label={label}
        placeholder="Escribí nombre o código…"
        autoComplete="off"
        autoFocus={autoFocus}
        required={required}
        value={query}
        onChange={(event) => {
          const value = event.target.value
          const match = rows.find((row) => labelFor(row) === value) ?? null
          setQuery(value)
          setSelected(match)
          onSelect?.(match)
          event.target.setCustomValidity(
            required && !match ? 'Elegí un registro de las sugerencias.' : '',
          )
        }}
      />
      <datalist id={listId}>
        {rows.map((row) => (
          <option key={textValue(row.id)} value={labelFor(row)} />
        ))}
      </datalist>
      {rows.loading && query && !selected && <small role="status">Buscando…</small>}
      {!rows.loading && query && !selected && (
        <small role="status">
          {rows.error
            ? 'No se pudo buscar. Volvé a intentar.'
            : rows.length
              ? 'Elegí una sugerencia del listado.'
              : 'Sin coincidencias.'}
        </small>
      )}
    </div>
  )
}
