// Busca y elige artículos con el teclado para agregarlos a una venta o una compra.
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { useApiRows, type ApiRow } from '../hooks/useApiRows'
import { currency, numberValue, textValue } from '../lib/format'
import './article-picker.css'

/** Abre un listado flotante paginado de artículos o proveedores, con búsqueda y selección por teclado o ratón. */
export function ArticlePicker({
  onChoose,
  onClose,
  message,
  mode = 'sale',
  suspended = false,
  unitCostOverride,
}: {
  onChoose: (row: ApiRow) => void
  onClose: () => void
  message: string
  mode?: 'sale' | 'purchase' | 'supplier'
  suspended?: boolean
  unitCostOverride?: number
}) {
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const supplier = mode === 'supplier'
  const rows = useApiRows(
    (supplier ? '/api/v1/alon/lookups/suppliers?q=' : '/api/v1/alon/articles?q=') +
      encodeURIComponent(query.trim()),
  )
  const current = Math.min(active, Math.max(0, rows.length - 1))
  const id = useId()
  const search = useRef<HTMLInputElement>(null)
  const panel = useRef<HTMLElement>(null)
  useEffect(() => {
    if (!suspended) search.current?.focus()
  }, [suspended])
  useEffect(() => {
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    search.current?.focus()
    return () => {
      document.body.style.overflow = overflow
    }
  }, [])
  useEffect(() => {
    document.getElementById(id + '-' + current)?.scrollIntoView?.({ block: 'nearest' })
  }, [id, current, rows.page, rows.loading])
  /** Carga la página anterior del listado y deja activa su última fila para continuar navegando hacia arriba. */
  const previous = () => {
    if (rows.page > 0) {
      rows.previous()
      setActive(99)
    }
  }
  /** Carga la página siguiente si hay más resultados y selecciona su primera fila. */
  const next = () => {
    if (rows.hasMore) {
      rows.next()
      setActive(0)
    }
  }
  /** Controla flechas, Enter, Escape y el recorrido con Tab dentro del listado, evitando enviar el formulario de fondo. */
  const keyboard = (event: KeyboardEvent) => {
    if (event.nativeEvent.isComposing) return
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      onClose()
      return
    }
    if (event.key === 'Tab') {
      const controls = panel.current?.querySelectorAll<HTMLElement>('input,button:not(:disabled)')
      if (controls?.length) {
        const first = controls[0],
          last = controls[controls.length - 1]
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first.focus()
        }
      }
      return
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      event.stopPropagation()
      if (rows.loading || !rows.length) return
      if (event.key === 'ArrowDown') {
        if (current === rows.length - 1) next()
        else setActive(current + 1)
      } else {
        if (current === 0) previous()
        else setActive(current - 1)
      }
    } else if (event.key === 'Enter' && event.target === search.current) {
      event.preventDefault()
      event.stopPropagation()
      if (!rows.loading && rows[current]) onChoose(rows[current])
    }
  }
  return (
    <div
      className="article-picker-backdrop"
      hidden={suspended}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <section
        className={supplier ? 'article-picker supplier-picker' : 'article-picker'}
        role="dialog"
        aria-modal="true"
        aria-labelledby={id + '-title'}
        aria-describedby={id + '-help'}
        ref={panel}
        onKeyDown={keyboard}
      >
        <header>
          <div>
            <h2 id={id + '-title'}>{supplier ? 'Elegir proveedor' : 'Elegir artículo'}</h2>
            <p id={id + '-help'}>↑ ↓ para recorrer · Enter para ingresar · Esc para volver</p>
          </div>
          <button type="button" className="secondary-button" onClick={onClose}>
            Cerrar listado
          </button>
        </header>
        <label className="article-picker-search">
          Buscar por nombre o código
          <input
            ref={search}
            type="text"
            role="combobox"
            aria-label={
              supplier ? 'Buscar en el listado de proveedores' : 'Buscar en el listado de artículos'
            }
            aria-autocomplete="list"
            aria-expanded={true}
            aria-controls={id + '-list'}
            aria-activedescendant={!rows.loading && rows.length ? id + '-' + current : undefined}
            value={query}
            maxLength={160}
            placeholder="Escribí para buscar…"
            onChange={(event) => {
              setQuery(event.target.value)
              setActive(0)
            }}
          />
        </label>
        <div className="article-picker-columns" aria-hidden="true">
          <span>{supplier ? 'PROVEEDOR' : 'ARTÍCULO'}</span>
          {!supplier && <span>STOCK</span>}
          {!supplier && <span>{mode === 'purchase' ? 'COSTO UNITARIO' : 'PRECIO'}</span>}
        </div>
        <div
          className="article-picker-list"
          id={id + '-list'}
          role="listbox"
          aria-label={
            supplier
              ? 'Proveedores para la compra'
              : mode === 'purchase'
                ? 'Artículos para la compra'
                : 'Artículos para la venta'
          }
          aria-busy={rows.loading}
        >
          {rows.map((row, index) => (
            <div
              key={textValue(row.id)}
              id={id + '-' + index}
              role="option"
              aria-selected={index === current}
              className={index === current ? 'article-picker-row selected' : 'article-picker-row'}
              onMouseMove={() => setActive(index)}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => onChoose(row)}
            >
              <span>
                <strong>{textValue(row.nombre)}</strong>
                <small>{textValue(row.codigo)}</small>
              </span>
              {!supplier && (
                <>
                  <span className={numberValue(row.stock) <= 0 ? 'article-picker-no-stock' : ''}>
                    {numberValue(row.stock).toLocaleString('es-AR', { maximumFractionDigits: 3 })}
                    <small>{textValue(row.unidad)}</small>
                  </span>
                  <strong>
                    {currency.format(
                      mode === 'purchase'
                        ? (unitCostOverride ?? numberValue(row.costo))
                        : numberValue(row.precio_venta),
                    )}
                  </strong>
                </>
              )}
            </div>
          ))}
          {rows.loading && (
            <p role="status">{supplier ? 'Cargando proveedores…' : 'Cargando artículos…'}</p>
          )}
          {!rows.loading && rows.error && (
            <p role="alert">No se pudo cargar el listado. Cerralo y volvé a intentar.</p>
          )}
          {!rows.loading && !rows.error && !rows.length && <p role="status">Sin coincidencias.</p>}
        </div>
        {message && (
          <p className="article-picker-message" role="status">
            {message}
          </p>
        )}
        <footer>
          <span>
            Página {rows.page + 1} · {rows.length} {supplier ? 'proveedores' : 'artículos'}
            {rows.hasMore ? ' · hay más artículos' : ''}
          </span>
          <div>
            <button
              type="button"
              className="secondary-button"
              disabled={rows.loading || rows.page === 0}
              onClick={() => {
                previous()
                search.current?.focus()
              }}
            >
              Anterior
            </button>
            <button
              type="button"
              className="secondary-button"
              disabled={rows.loading || !rows.hasMore}
              onClick={() => {
                next()
                search.current?.focus()
              }}
            >
              Siguiente
            </button>
          </div>
        </footer>
      </section>
    </div>
  )
}
