// Busca registros y conserva el identificador elegido aunque cambien las sugerencias.
import { useEffect, useState } from 'react'
import { useApiRows, type ApiRow } from '../hooks/useApiRows'
import { apiFetch } from '../lib/http'
import { Combobox } from './Combobox'
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
  defaultName,
  searchOnly = false,
  onEmptyEnter,
}: {
  name: string
  label: string
  endpoint: string
  required?: boolean
  autoFocus?: boolean
  defaultName?: string
  onEmptyEnter?: () => void
  searchOnly?: boolean
  initialRecord?: ApiRow
  onSelect?: (row: ApiRow | null) => void
}) {
  const [query, setQuery] = useState(initialRecord ? textValue(initialRecord.nombre) : '')
  const [edited, setEdited] = useState(false)
  const [chosen, setSelected] = useState<ApiRow | null>(initialRecord ?? null)
  const [defaultResult, setDefaultResult] = useState<{ key: string; row: ApiRow | null } | null>(
    null,
  )
  const defaultKey = endpoint + ':' + (defaultName ?? '')
  useEffect(() => {
    if (!defaultName) return
    const controller = new AbortController()
    const key = endpoint + ':' + defaultName
    void apiFetch(endpoint + '?q=' + encodeURIComponent(defaultName) + '&size=20', {
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) throw new Error('No se pudo consultar el cliente predeterminado.')
        const rows = (await response.json()) as ApiRow[]
        if (!controller.signal.aborted)
          setDefaultResult({
            key,
            row:
              rows.find(
                (row) =>
                  textValue(row.nombre).trim().toLocaleLowerCase('es-AR') ===
                  defaultName.toLocaleLowerCase('es-AR'),
              ) ?? null,
          })
      })
      .catch(() => {
        if (!controller.signal.aborted) setDefaultResult({ key, row: null })
      })
    return () => controller.abort()
  }, [endpoint, defaultName])
  const rows = useApiRows(endpoint + '?q=' + encodeURIComponent(chosen || !edited ? '' : query))
  const selected =
    chosen ?? (!edited && defaultResult?.key === defaultKey ? defaultResult.row : null)
  const labelFor = (row: ApiRow) =>
    [row.codigo, row.nombre].filter(Boolean).map(textValue).join(' · ')
  return (
    <div className="searchable-field">
      <input type="hidden" name={name} value={selected ? textValue(selected.id) : ''} />
      <Combobox
        label={label}
        onEmptyEnter={onEmptyEnter}
        value={!edited && selected ? labelFor(selected) : query}
        required={required}
        autoFocus={autoFocus}
        valid={!!selected}
        selectOnFocus={!!selected}
        options={(searchOnly && (!edited || !query.trim() || selected) ? [] : rows).map((row) => ({
          value: textValue(row.id),
          label: labelFor(row),
        }))}
        onChange={(value) => {
          setEdited(true)
          setQuery(value)
          const match = rows.find((row) => labelFor(row) === value) ?? null
          setSelected(match)
          onSelect?.(match)
        }}
        onChoose={(option) => {
          const match = rows.find((row) => textValue(row.id) === option.value) ?? null
          setEdited(true)
          setQuery(option.label)
          setSelected(match)
          onSelect?.(match)
        }}
      />
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
