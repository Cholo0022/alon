import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { type ApiRow } from '../hooks/useApiRows'
import './article-picker.css'

/** Crea y selecciona un cliente sin desmontar el borrador de venta. */
export function CustomerEntryDialog({
  onSaved,
  onCancel,
}: {
  onSaved: (row: ApiRow) => void
  onCancel: () => void
}) {
  const id = useId()
  const form = useRef<HTMLFormElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    form.current?.querySelector('input')?.focus()
    return () => {
      document.body.style.overflow = overflow
      if (previous?.isConnected) previous.focus()
    }
  }, [])
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy) return
    const data = new FormData(event.currentTarget)
    const name = String(data.get('name') ?? '').trim()
    if (!name) {
      setError('El nombre del cliente es obligatorio.')
      return
    }
    setBusy(true)
    setError('')
    try {
      const response = await apiFetch('/api/v1/operations/customers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name,
          dni: String(data.get('dni') ?? '').trim(),
          phone: String(data.get('phone') ?? '').trim(),
          email: String(data.get('email') ?? '').trim(),
          creditLimit: Number(data.get('creditLimit') ?? 0),
        }),
      })
      const result = await response.json()
      if (!response.ok) throw new Error(result.error ?? 'No se pudo crear el cliente.')
      if (!Number(result.id)) throw new Error('La respuesta del cliente no es válida.')
      onSaved(result as ApiRow)
      window.dispatchEvent(new Event('alon:refresh'))
    } catch (error) {
      setError(error instanceof Error ? error.message : 'No se pudo crear el cliente.')
      setBusy(false)
    }
  }
  return (
    <div className="article-picker-backdrop">
      <section
        className="article-picker article-entry-dialog customer-entry-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={id}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.preventDefault()
            event.stopPropagation()
            if (!busy) onCancel()
          }
          if (event.key === 'Tab') {
            const controls = event.currentTarget.querySelectorAll<HTMLElement>(
              'input:not(:disabled),button:not(:disabled)',
            )
            const first = controls[0],
              last = controls[controls.length - 1]
            if (event.shiftKey && document.activeElement === first) {
              event.preventDefault()
              last?.focus()
            } else if (!event.shiftKey && document.activeElement === last) {
              event.preventDefault()
              first?.focus()
            }
          }
        }}
      >
        <header>
          <h2 id={id}>Nuevo cliente para esta venta</h2>
        </header>
        <form ref={form} onSubmit={submit}>
          <fieldset disabled={busy}>
            <label>
              Nombre y apellido
              <input name="name" required maxLength={150} />
            </label>
            <label>
              DNI (solo números)
              <input name="dni" inputMode="numeric" pattern="[0-9]{1,12}" maxLength={12} />
            </label>
            <label>
              Teléfono
              <input name="phone" type="tel" maxLength={40} />
            </label>
            <label>
              Email
              <input name="email" type="email" maxLength={160} />
            </label>
            <label>
              Límite de crédito
              <input
                name="creditLimit"
                type="number"
                min="0"
                step="0.01"
                defaultValue="0"
                required
              />
            </label>
          </fieldset>
          {error && <p role="alert">{error}</p>}
          <div className="entry-actions">
            <button type="button" className="secondary-button" disabled={busy} onClick={onCancel}>
              Cancelar
            </button>
            <button type="submit" className="primary-button" disabled={busy}>
              {busy ? 'Guardando…' : 'Guardar y usar cliente'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}
