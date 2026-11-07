import { useEffect, useId, useRef, useState } from 'react'
import { currency } from '../lib/format'
import './guest-checkout.css'

export type GuestBuyer = {
  firstName: string
  lastName: string
  phone: string
  dni: string
  province: string
  city: string
}
export const defaultGuestBuyer: GuestBuyer = {
  firstName: '',
  lastName: '',
  phone: '',
  dni: '',
  province: 'Entre Ríos',
  city: 'Larroque',
}

export function GuestCheckoutDialog({
  initialBuyer,
  busy,
  error,
  total,
  onClose,
  onSend,
}: {
  initialBuyer?: Partial<GuestBuyer>
  busy: boolean
  error: string
  total: number
  onClose: () => void
  onSend: (buyer: GuestBuyer) => void
}) {
  const [buyer, setBuyer] = useState<GuestBuyer>({ ...defaultGuestBuyer, ...initialBuyer })
  const [validation, setValidation] = useState('')
  const heading = useId(),
    first = useRef<HTMLInputElement>(null)
  useEffect(() => {
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    first.current?.focus()
    return () => {
      document.body.style.overflow = overflow
    }
  }, [])
  const update = (key: keyof GuestBuyer, value: string) => {
    setBuyer((current) => ({ ...current, [key]: value }))
    setValidation('')
  }
  return (
    <div
      className="shop-checkout-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) onClose()
      }}
    >
      <section
        className="shop-checkout-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={heading}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.preventDefault()
            if (!busy) onClose()
          }
          if (event.key === 'Tab') {
            const controls = Array.from(
              event.currentTarget.querySelectorAll<HTMLElement>(
                'input:not(:disabled),button:not(:disabled)',
              ),
            )
            if (!controls.length) {
              event.preventDefault()
              return
            }
            if (event.shiftKey && document.activeElement === controls[0]) {
              event.preventDefault()
              controls.at(-1)?.focus()
            } else if (!event.shiftKey && document.activeElement === controls.at(-1)) {
              event.preventDefault()
              controls[0].focus()
            }
          }
        }}
      >
        <h2 id={heading}>Datos para enviar el pedido</h2>
        <p>
          No necesitás una cuenta. El vendedor se comunicará con vos para coordinar el pago y la
          entrega.
        </p>
        <form
          className="shop-account-form"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            if (busy) return
            const normalized = Object.fromEntries(
              Object.entries(buyer).map(([key, value]) => [key, value.trim()]),
            ) as GuestBuyer
            if (
              !event.currentTarget.reportValidity() ||
              Object.values(normalized).some((value) => !value)
            ) {
              setValidation(
                'Completá todos los datos. El celular debe tener exactamente diez dígitos y el DNI solo números.',
              )
              return
            }
            onSend(normalized)
          }}
        >
          <fieldset disabled={busy} className="shop-contact-fields">
            <label>
              Nombre
              <input
                ref={first}
                name="firstName"
                autoComplete="given-name"
                required
                maxLength={60}
                value={buyer.firstName}
                onChange={(event) => update('firstName', event.target.value)}
              />
            </label>
            <label>
              Apellido
              <input
                name="lastName"
                autoComplete="family-name"
                required
                maxLength={60}
                value={buyer.lastName}
                onChange={(event) => update('lastName', event.target.value)}
              />
            </label>
            <label>
              Celular (10 dígitos)
              <input
                name="phone"
                aria-label="Celular (10 dígitos)"
                type="tel"
                inputMode="numeric"
                autoComplete="tel-national"
                required
                pattern="[0-9]{10}"
                minLength={10}
                maxLength={10}
                placeholder="Ej.: 3446236311"
                value={buyer.phone}
                onChange={(event) => update('phone', event.target.value)}
              />
              <small>Código de área y número, sin 0, 15 ni +54.</small>
            </label>
            <label>
              DNI
              <input
                name="dni"
                inputMode="numeric"
                required
                pattern="[0-9]{1,12}"
                maxLength={12}
                placeholder="Sin puntos"
                value={buyer.dni}
                onChange={(event) => update('dni', event.target.value)}
              />
            </label>
            <label>
              Provincia
              <input
                name="province"
                autoComplete="address-level1"
                required
                maxLength={100}
                value={buyer.province}
                onChange={(event) => update('province', event.target.value)}
              />
            </label>
            <label>
              Ciudad
              <input
                name="city"
                autoComplete="address-level2"
                required
                maxLength={100}
                value={buyer.city}
                onChange={(event) => update('city', event.target.value)}
              />
            </label>
          </fieldset>
          <p className="shop-wide">
            Total estimado: <strong>{currency.format(total)}</strong>
          </p>
          {(validation || error) && (
            <p className="shop-wide shop-danger" role="alert">
              {validation || error}
            </p>
          )}
          <button className="shop-solid shop-wide" type="submit" disabled={busy}>
            {busy ? 'Enviando…' : 'Confirmar envío del pedido'}
          </button>
          <button type="button" className="shop-link shop-wide" disabled={busy} onClick={onClose}>
            Volver al carrito
          </button>
        </form>
      </section>
    </div>
  )
}
