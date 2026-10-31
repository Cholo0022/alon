import { useEffect, useId, useRef } from 'react'
import './article-picker.css'

/** Pide la decisión final de actualizar solo el costo del catálogo o conservar los precios antes de guardar la compra. */
export function CostConfirmation({
  busy,
  message,
  onConfirm,
  onCancel,
}: {
  busy: boolean
  message: string
  onConfirm: (updateCost: boolean) => void
  onCancel: () => void
}) {
  const id = useId()
  const panel = useRef<HTMLElement>(null)
  useEffect(() => {
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    panel.current?.querySelector<HTMLButtonElement>('button')?.focus()
    return () => {
      document.body.style.overflow = overflow
    }
  }, [])
  useEffect(() => {
    /** Interpreta S y N para confirmar la decisión y Escape para volver; ignora atajos modificados y acciones mientras se guarda. */
    const keyboard = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.altKey || event.metaKey || event.isComposing) return
      const key = event.key.toLowerCase()
      if (key === 's' || key === 'n' || key === 'escape') {
        event.preventDefault()
        event.stopPropagation()
        if (!busy) {
          if (key === 'escape') onCancel()
          else onConfirm(key === 's')
        }
      }
    }
    window.addEventListener('keydown', keyboard)
    return () => window.removeEventListener('keydown', keyboard)
  }, [busy, onConfirm, onCancel])
  return (
    <div className="article-picker-backdrop">
      <section
        ref={panel}
        className="article-picker article-entry-dialog payment-confirmation cost-confirmation"
        role="dialog"
        aria-modal="true"
        aria-labelledby={id}
        onKeyDown={(event) => {
          if (event.key !== 'Tab') return
          const buttons =
            panel.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)')
          if (!buttons?.length) return
          const first = buttons[0],
            last = buttons[buttons.length - 1]
          if (event.shiftKey && document.activeElement === first) {
            event.preventDefault()
            last.focus()
          } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault()
            first.focus()
          }
        }}
      >
        <header>
          <h2 id={id}>¿Este comprobante modifica el precio de costo?</h2>
        </header>
        <p>
          Si elegís Sí, se actualizará únicamente el costo unitario de los productos con el cargado
          en este comprobante. El precio de venta, el flete y el porcentaje de ganancia se
          conservan.
        </p>
        <p>
          Si elegís No, se conservarán todos los precios. En ambos casos se registra la compra y el
          ingreso de stock.
        </p>
        {message && <p role="alert">{message}</p>}
        <div className="article-entry-actions">
          <button
            type="button"
            className="secondary-button"
            disabled={busy}
            onClick={() => onConfirm(false)}
          >
            No (N)
          </button>
          <button
            type="button"
            className="primary-button"
            disabled={busy}
            onClick={() => onConfirm(true)}
          >
            {busy ? 'Guardando…' : 'Sí (S)'}
          </button>
          <button type="button" className="secondary-button" disabled={busy} onClick={onCancel}>
            Volver
          </button>
        </div>
      </section>
    </div>
  )
}
