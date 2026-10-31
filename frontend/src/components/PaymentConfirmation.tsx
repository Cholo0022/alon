// Pide las condiciones y el medio de pago antes de confirmar todo el comprobante.
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { currency } from '../lib/format'
import './article-picker.css'
export type PaymentSelection = { condition: string; paymentMethod: string }
/** Recoge las condiciones de compra o venta: fija Efectivo para Contado de caja, permite elegir medio en Contado y lo omite en Cuenta corriente. */
export function PaymentConfirmation({
  mode,
  total,
  partyName,
  busy,
  message,
  onConfirm,
  onCancel,
  onChange,
}: {
  mode: 'sale' | 'purchase'
  total: number
  partyName: string
  busy: boolean
  message: string
  onConfirm: (selection: PaymentSelection) => void
  onCancel: () => void
  onChange: () => void
}) {
  const [condition, setCondition] = useState('')
  const [method, setMethod] = useState('')
  const form = useRef<HTMLFormElement>(null)
  const id = useId()
  const credit = condition === 'CUENTA_CORRIENTE'
  const cashBox = mode === 'purchase' && condition === 'EFECTIVO_CAJA'
  const methods =
    mode === 'sale' && credit
      ? [{ value: 'CUENTA_CORRIENTE', label: 'Cuenta corriente' }]
      : cashBox
        ? [{ value: 'EFECTIVO', label: 'Efectivo' }]
        : [
            { value: 'EFECTIVO', label: 'Efectivo' },
            { value: 'TRANSFERENCIA', label: 'Transferencia' },
            { value: 'MERCADO_PAGO', label: 'Mercado Pago' },
          ]
  useEffect(() => {
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    form.current?.querySelector('select')?.focus()
    return () => {
      document.body.style.overflow = previous
    }
  }, [])
  /** Permite volver con Escape y mantiene el foco de Tab dentro de los controles habilitados del diálogo. */
  const keyboard = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      if (!busy) onCancel()
    }
    if (event.key === 'Tab') {
      const fields = form.current?.querySelectorAll<HTMLElement>(
        'select:not(:disabled),button:not(:disabled)',
      )
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
  }
  return (
    <div className="article-picker-backdrop">
      <section
        className="article-picker article-entry-dialog payment-confirmation"
        role="dialog"
        aria-modal="true"
        aria-labelledby={id + '-title'}
      >
        <header>
          <div>
            <h2 id={id + '-title'}>
              {mode === 'purchase' ? 'Confirmar factura de compra' : 'Confirmar venta'}
            </h2>
            <p>
              {partyName}
              <br />
              Total: <strong>{currency.format(total)}</strong>
            </p>
          </div>
        </header>
        <form
          ref={form}
          onKeyDown={keyboard}
          onSubmit={(event) => {
            event.preventDefault()
            if (!busy && condition && (credit || cashBox || method))
              onConfirm({
                condition,
                paymentMethod: credit ? 'CUENTA_CORRIENTE' : cashBox ? 'EFECTIVO' : method,
              })
          }}
        >
          <label>
            {mode === 'purchase' ? 'Condición de pago' : 'Tipo de venta'}
            <select
              required
              value={condition}
              disabled={busy}
              onChange={(event) => {
                setCondition(event.target.value)
                setMethod('')
                onChange()
              }}
            >
              <option value="">Seleccioná una opción</option>
              {mode === 'purchase' ? (
                <>
                  <option value="EFECTIVO">CONTADO</option>
                  <option value="EFECTIVO_CAJA">CONTADO DE CAJA</option>
                  <option value="CUENTA_CORRIENTE">CUENTA CORRIENTE</option>
                </>
              ) : (
                <>
                  <option value="CONTADO">Contado</option>
                  <option value="CUENTA_CORRIENTE">Cuenta corriente</option>
                </>
              )}
            </select>
          </label>
          {!credit && (
            <label>
              Medio de pago
              <select
                required
                value={cashBox ? 'EFECTIVO' : method}
                disabled={busy || !condition || cashBox}
                onChange={(event) => {
                  setMethod(event.target.value)
                  onChange()
                }}
              >
                <option value="">Seleccioná el medio de pago</option>
                {methods.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
            </label>
          )}
          {condition && (
            <p className="article-entry-info">
              {mode === 'purchase'
                ? cashBox
                  ? 'Se descontará de la caja de la fecha de la compra. Esa caja debe estar abierta.'
                  : credit
                    ? 'El total queda pendiente en la cuenta del proveedor. No se registra un pago inicial.'
                    : 'El pago se registra fuera de caja y no modifica el arqueo.'
                : credit
                  ? 'El total queda pendiente en la cuenta del cliente.'
                  : 'Se registra el cobro en la caja abierta de la fecha de la venta.'}
            </p>
          )}
          {message && (
            <p className="article-picker-message" role="alert">
              {message}
            </p>
          )}
          <div className="article-entry-actions">
            <button type="button" className="secondary-button" disabled={busy} onClick={onCancel}>
              Volver a la carga
            </button>
            <button className="primary-button" type="submit" disabled={busy}>
              {busy
                ? 'Confirmando…'
                : mode === 'purchase'
                  ? 'Confirmar factura'
                  : 'Confirmar venta'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}
