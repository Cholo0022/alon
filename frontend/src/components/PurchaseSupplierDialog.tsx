import { useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import type { ApiRow } from '../hooks/useApiRows'
import { PurchaseDialog } from './PurchaseDialog'

export function PurchaseSupplierDialog({
  onSaved,
  onClose,
}: {
  onSaved: (row: ApiRow) => void
  onClose: () => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy) return
    const values = new FormData(event.currentTarget)
    const body = Object.fromEntries(
      ['name', 'taxId', 'specialty', 'phone', 'email'].map((key) => [
        key,
        String(values.get(key) ?? '').trim(),
      ]),
    )
    if (!body.name) {
      setError('Ingresá el nombre del proveedor.')
      return
    }
    setBusy(true)
    setError('')
    try {
      const response = await apiFetch('/api/v1/operations/purchases/suppliers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      const result = await response.json()
      if (!response.ok || !Number(result.id))
        throw new Error(result.error ?? 'No se pudo crear el proveedor.')
      onSaved(result as ApiRow)
      window.dispatchEvent(new Event('alon:refresh'))
    } catch (error) {
      setError(error instanceof Error ? error.message : 'No se pudo crear el proveedor.')
      setBusy(false)
    }
  }
  return (
    <PurchaseDialog title="Nuevo proveedor para este comprobante" busy={busy} onClose={onClose}>
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <label>
            Nombre del proveedor
            <input name="name" required maxLength={150} />
          </label>
          <label>
            CUIT
            <input name="taxId" maxLength={13} />
          </label>
          <label>
            Especialidad
            <input name="specialty" maxLength={90} />
          </label>
          <label>
            Teléfono
            <input name="phone" type="tel" maxLength={40} />
          </label>
          <label>
            Email
            <input name="email" type="email" maxLength={160} />
          </label>
        </fieldset>
        {error && <p role="alert">{error}</p>}
        <div className="entry-actions">
          <button type="button" className="secondary-button" disabled={busy} onClick={onClose}>
            Cancelar
          </button>
          <button className="primary-button" disabled={busy}>
            Guardar y usar proveedor
          </button>
        </div>
      </form>
    </PurchaseDialog>
  )
}
