// Pide la cantidad y, en compras, el costo antes de incorporar el artículo al comprobante.
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { type ApiRow } from '../hooks/useApiRows'
import { currency, textValue } from '../lib/format'
import { QuantityInput } from './QuantityInput'
import './article-picker.css'

export type ArticleEntryValues = { quantity: number; unitCost?: number }
/** Pide cantidad y costo de compra, cantidad de venta o cantidad para el carrito antes de confirmar el artículo; limita la cantidad al stock disponible. */
export function ArticleEntryDialog({
  article,
  mode,
  initialQuantity,
  initialPrice,
  maxQuantity,
  message,
  onConfirm,
  onCancel,
}: {
  article: ApiRow
  mode: 'sale' | 'purchase' | 'cart'
  initialQuantity: string
  initialPrice: number
  maxQuantity?: number
  message: string
  onConfirm: (values: ArticleEntryValues) => void
  onCancel: () => void
}) {
  const [quantity, setQuantity] = useState(initialQuantity)
  const [cost, setCost] = useState(String(initialPrice))
  const id = useId()
  const form = useRef<HTMLFormElement>(null)
  const backdrop = useRef<HTMLDivElement>(null)
  const priceInput = useRef<HTMLInputElement>(null)
  const origin = useRef(document.activeElement as HTMLElement | null)
  const noStock = mode !== 'purchase' && maxQuantity !== undefined && maxQuantity <= 0
  useEffect(() => {
    const field = form.current?.querySelector<HTMLInputElement>('[aria-label="Cantidad"]')
    field?.focus()
    field?.select()
  }, [])
  // En la tienda bloquea el desplazamiento de fondo y devuelve el foco al botón de origen.
  useEffect(() => {
    if (mode !== 'cart') return
    const previous = origin.current
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = overflow
      if (previous?.isConnected) previous.focus()
    }
  }, [mode])
  // Ajusta el carrito al espacio disponible cuando aparece el teclado del celular.
  useEffect(() => {
    if (mode !== 'cart') return
    const viewport = window.visualViewport
    /** Mantiene el diálogo dentro de la parte visible de la pantalla. */
    const resize = () => {
      if (backdrop.current)
        backdrop.current.dataset.compact = String((viewport?.height ?? window.innerHeight) < 420)
      backdrop.current?.style.setProperty(
        '--cart-viewport-height',
        `${viewport?.height ?? window.innerHeight}px`,
      )
      backdrop.current?.style.setProperty('--cart-viewport-top', `${viewport?.offsetTop ?? 0}px`)
    }
    resize()
    viewport?.addEventListener('resize', resize)
    viewport?.addEventListener('scroll', resize)
    window.addEventListener('resize', resize)
    return () => {
      viewport?.removeEventListener('resize', resize)
      viewport?.removeEventListener('scroll', resize)
      window.removeEventListener('resize', resize)
    }
  }, [mode])
  /** Permite confirmar los campos con Enter, cancelar con Escape y mantener Tab dentro del diálogo. */
  const keyboard = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      onCancel()
      return
    }
    if (event.key === 'Tab') {
      const fields = form.current?.querySelectorAll<HTMLElement>('input,button:not(:disabled)')
      if (fields?.length) {
        const first = fields[0],
          last = fields[fields.length - 1]
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first.focus()
        }
      }
    }
    if (event.key === 'Enter' && event.target instanceof HTMLInputElement) {
      event.preventDefault()
      event.stopPropagation()
      if (event.repeat || event.nativeEvent.isComposing) return
      if (mode === 'purchase' && event.target !== priceInput.current) {
        if (event.target.reportValidity()) {
          priceInput.current?.focus()
          priceInput.current?.select()
        }
      } else if (!noStock) form.current?.requestSubmit()
    }
  }
  return (
    <div
      ref={backdrop}
      className={`article-picker-backdrop${mode === 'cart' ? ' article-cart-backdrop' : ''}`}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onCancel()
      }}
    >
      <section
        className="article-picker article-entry-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={id + '-title'}
        aria-describedby={id + '-article'}
      >
        <header>
          <div>
            <h2 id={id + '-title'}>
              {mode === 'cart'
                ? 'Cantidad para agregar al carrito'
                : mode === 'sale'
                  ? 'Cantidad a vender'
                  : 'Cantidad y precio de compra'}
            </h2>
            <p id={id + '-article'}>
              <strong>{textValue(article.nombre)}</strong>
              <br />
              {textValue(article.codigo)}
            </p>
          </div>
        </header>
        <form
          ref={form}
          onKeyDown={keyboard}
          onSubmit={(event) => {
            event.preventDefault()
            if (!noStock)
              onConfirm({
                quantity: Number(quantity),
                ...(mode === 'purchase' ? { unitCost: Number(cost) } : {}),
              })
          }}
        >
          <label>
            Cantidad
            <QuantityInput
              value={quantity}
              min="0.001"
              max={mode !== 'purchase' ? maxQuantity : undefined}
              required
              onChange={(event) => setQuantity(event.target.value)}
            />
          </label>
          {mode === 'purchase' ? (
            <label>
              Costo unitario
              <input
                ref={priceInput}
                type="number"
                inputMode="decimal"
                min="0"
                step="0.01"
                value={cost}
                required
                onChange={(event) => setCost(event.target.value)}
              />
            </label>
          ) : (
            <p className="article-entry-info">
              Precio unitario: <strong>{currency.format(initialPrice)}</strong>
              <br />
              {mode === 'cart'
                ? 'Disponible para agregar al carrito:'
                : 'Disponible para esta venta:'}{' '}
              {maxQuantity?.toLocaleString('es-AR', { maximumFractionDigits: 3 })}
            </p>
          )}
          {noStock && (
            <p className="article-picker-message" role="alert">
              No hay stock disponible para este artículo.
            </p>
          )}
          {message && (
            <p className="article-picker-message" role="status">
              {message}
            </p>
          )}
          <p className="article-entry-help">
            {mode === 'purchase'
              ? 'Enter: cantidad → costo → ingresar · Esc vuelve al listado'
              : mode === 'cart'
                ? 'Enter para agregar · Esc para cancelar'
                : 'Enter para ingresar · Esc vuelve al listado'}
          </p>
          <div className="article-entry-actions">
            <button type="button" className="secondary-button" onClick={onCancel}>
              {mode === 'cart' ? 'Cancelar' : 'Volver al listado'}
            </button>
            <button type="submit" className="primary-button" disabled={noStock}>
              {mode === 'cart' ? 'Agregar cantidad al carrito' : 'Ingresar artículo'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}
