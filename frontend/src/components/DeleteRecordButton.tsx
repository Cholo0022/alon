import { useEffect, useId, useRef, useState } from 'react'
import { apiFetch } from '../lib/http'
import './article-picker.css'

export function DeleteRecordButton({
  endpoint,
  name,
  onDeleted,
}: {
  endpoint: string
  name: string
  onDeleted?: () => void
}) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const dialog = useRef<HTMLDialogElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const cancel = useRef<HTMLButtonElement>(null)
  const title = useId()
  useEffect(() => {
    if (!open) return
    const modal = dialog.current
    const opener = trigger.current
    modal?.showModal()
    cancel.current?.focus()
    return () => {
      modal?.close()
      opener?.focus()
    }
  }, [open])
  async function remove() {
    if (busy) return
    setBusy(true)
    setError('')
    try {
      const response = await apiFetch(endpoint, { method: 'DELETE' })
      if (!response.ok) {
        const body = (await response.json()) as { error?: string }
        throw new Error(body.error ?? 'No se pudo eliminar el registro.')
      }
      setOpen(false)
      onDeleted?.()
      window.dispatchEvent(new Event('alon:refresh'))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo conectar con el servidor.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      <button
        ref={trigger}
        className="secondary-button"
        type="button"
        onClick={() => {
          setError('')
          setOpen(true)
        }}
      >
        Eliminar
      </button>
      {open && (
        <dialog
          ref={dialog}
          className="delete-confirmation"
          aria-labelledby={title}
          onCancel={(event) => {
            event.preventDefault()
            if (!busy) setOpen(false)
          }}
        >
          <h2 id={title}>¿Estás seguro de eliminar?</h2>
          <p>
            Se eliminará definitivamente <strong>{name}</strong>. Esta acción no se puede deshacer.
          </p>
          <p>Si tiene operaciones vinculadas, se conservará el registro y se mostrará el motivo.</p>
          {error && <p role="alert">{error}</p>}
          <div className="row-actions">
            <button
              ref={cancel}
              className="secondary-button"
              type="button"
              disabled={busy}
              onClick={() => setOpen(false)}
            >
              Cancelar
            </button>
            <button
              className="primary-button"
              type="button"
              disabled={busy}
              onClick={() => void remove()}
            >
              {busy ? 'Eliminando…' : 'Sí, eliminar definitivamente'}
            </button>
          </div>
        </dialog>
      )}
    </>
  )
}
