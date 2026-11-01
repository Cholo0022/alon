import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { useApiRows, type ApiRow } from '../hooks/useApiRows'
import { textValue } from '../lib/format'
import './article-picker.css'

/** Lista remota paginada para elegir registros sin enviar el formulario que está detrás. */
export function LookupPicker({
  endpoint,
  label,
  initialQuery,
  onChoose,
  onClose,
}: {
  endpoint: string
  label: string
  initialQuery: string
  onChoose: (row: ApiRow) => void
  onClose: () => void
}) {
  const [query, setQuery] = useState(initialQuery),
    [active, setActive] = useState(0)
  const rows = useApiRows(
    endpoint + (endpoint.includes('?') ? '&q=' : '?q=') + encodeURIComponent(query.trim()),
  )
  const index = Math.min(active, Math.max(0, rows.length - 1)),
    id = useId()
  const panel = useRef<HTMLElement>(null),
    search = useRef<HTMLInputElement>(null)
  useEffect(() => {
    const origin = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    search.current?.focus()
    return () => {
      document.body.style.overflow = overflow
      origin?.focus()
    }
  }, [])
  const keyboard = (e: KeyboardEvent) => {
    if (e.nativeEvent.isComposing) return
    if (e.key === 'Escape') {
      e.preventDefault()
      e.stopPropagation()
      onClose()
    }
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      e.stopPropagation()
      setActive((v) => Math.max(0, Math.min(rows.length - 1, v + (e.key === 'ArrowDown' ? 1 : -1))))
    }
    if (e.key === 'Enter' && e.target === search.current) {
      e.preventDefault()
      e.stopPropagation()
      if (!rows.loading && rows[index]) onChoose(rows[index])
    }
    if (e.key === 'Tab') {
      const fields = panel.current?.querySelectorAll<HTMLElement>('input,button:not(:disabled)')
      if (fields?.length) {
        const first = fields[0],
          last = fields[fields.length - 1]
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault()
          last.focus()
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault()
          first.focus()
        }
      }
    }
  }
  useEffect(() => {
    document.getElementById(id + '-' + index)?.scrollIntoView?.({ block: 'nearest' })
  }, [id, index, rows.page])
  return createPortal(
    <div
      className="article-picker-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <section
        ref={panel}
        className="article-picker supplier-picker"
        role="dialog"
        aria-modal="true"
        aria-labelledby={id + '-title'}
        onKeyDown={keyboard}
      >
        <header>
          <div>
            <h2 id={id + '-title'}>Elegir {label.toLocaleLowerCase('es-AR')}</h2>
            <p>↑ ↓ para recorrer · Enter para elegir · Esc para volver</p>
          </div>
          <button type="button" className="secondary-button" onClick={onClose}>
            Cerrar listado
          </button>
        </header>
        <label className="article-picker-search">
          Buscar por nombre o código
          <input
            ref={search}
            aria-label={'Buscar ' + label.toLocaleLowerCase('es-AR')}
            role="combobox"
            aria-expanded="true"
            aria-controls={id + '-list'}
            aria-activedescendant={rows.length ? id + '-' + index : undefined}
            value={query}
            maxLength={160}
            onChange={(e) => {
              setQuery(e.target.value)
              setActive(0)
            }}
          />
        </label>
        <div
          id={id + '-list'}
          className="article-picker-list"
          role="listbox"
          aria-label={label}
          aria-busy={rows.loading}
        >
          {rows.map((row, i) => (
            <div
              key={textValue(row.id)}
              id={id + '-' + i}
              role="option"
              aria-selected={index === i}
              className={'article-picker-row' + (index === i ? ' selected' : '')}
              onMouseMove={() => setActive(i)}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => onChoose(row)}
            >
              <span>
                <strong>{textValue(row.nombre)}</strong>
                <small>{textValue(row.codigo)}</small>
              </span>
            </div>
          ))}
          {rows.loading && <p role="status">Cargando…</p>}
          {rows.error && (
            <p role="alert">No se pudo cargar el listado. Cerralo y volvé a intentar.</p>
          )}
          {!rows.loading && !rows.error && !rows.length && <p role="status">Sin coincidencias.</p>}
        </div>
        <footer>
          <span>Página {rows.page + 1}</span>
          <div>
            <button
              type="button"
              className="secondary-button"
              disabled={rows.loading || rows.page === 0}
              onClick={() => {
                rows.previous()
                setActive(0)
              }}
            >
              Anterior
            </button>
            <button
              type="button"
              className="secondary-button"
              disabled={rows.loading || !rows.hasMore}
              onClick={() => {
                rows.next()
                setActive(0)
              }}
            >
              Siguiente
            </button>
          </div>
        </footer>
      </section>
    </div>,
    document.body,
  )
}
