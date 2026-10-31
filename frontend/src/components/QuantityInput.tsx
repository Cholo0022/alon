// Permite escribir cantidades decimales y usar las flechas para cambiar una unidad por vez.
import { useRef, type InputHTMLAttributes } from 'react'
import './quantity-input.css'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'step'> & { label?: string }
export function QuantityInput({
  label = 'Cantidad',
  onChange,
  onKeyDown,
  disabled,
  readOnly,
  ...props
}: Props) {
  const input = useRef<HTMLInputElement>(null)
  const move = (direction: number) => {
    const field = input.current
    if (!field || disabled || readOnly) return
    const value = field.value === '' ? 0 : Number(field.value)
    if (!Number.isFinite(value)) return
    const minimum = props.min === undefined ? -Infinity : Number(props.min)
    const maximum = props.max === undefined ? Infinity : Number(props.max)
    const next = Math.max(minimum, Math.min(maximum, Math.round((value + direction) * 1000) / 1000))
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.call(
      field,
      String(next),
    )
    field.dispatchEvent(new Event('input', { bubbles: true }))
  }
  return (
    <span className="quantity-input">
      <input
        {...props}
        ref={input}
        aria-label={props['aria-label'] ?? label}
        type="number"
        step="any"
        inputMode="decimal"
        disabled={disabled}
        readOnly={readOnly}
        onChange={(event) => {
          const value = event.target.valueAsNumber
          event.target.setCustomValidity(
            Number.isFinite(value) && Math.abs(value * 1000 - Math.round(value * 1000)) > 0.000001
              ? 'La cantidad admite hasta tres decimales.'
              : '',
          )
          onChange?.(event)
        }}
        onKeyDown={(event) => {
          onKeyDown?.(event)
          if (!event.defaultPrevented && (event.key === 'ArrowUp' || event.key === 'ArrowDown')) {
            event.preventDefault()
            move(event.key === 'ArrowUp' ? 1 : -1)
          }
        }}
      />
      <span className="quantity-controls">
        <button
          type="button"
          aria-label={'Aumentar ' + label.toLocaleLowerCase('es-AR')}
          disabled={disabled || readOnly}
          onClick={() => move(1)}
        >
          <span className="quantity-up" aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label={'Disminuir ' + label.toLocaleLowerCase('es-AR')}
          disabled={disabled || readOnly}
          onClick={() => move(-1)}
        >
          <span className="quantity-down" aria-hidden="true" />
        </button>
      </span>
    </span>
  )
}
