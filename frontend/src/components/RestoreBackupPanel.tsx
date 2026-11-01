import { useEffect, useId, useRef, useState } from 'react'
import { apiFetch } from '../lib/http'

type RestoreResult = { safetyBackup: string; message: string }
const noticeKey = 'alon:restore-success'
function finishRestore(result: RestoreResult) {
  try {
    sessionStorage.setItem(noticeKey, result.message)
  } catch {
    window.alert(result.message)
  }
  window.location.reload()
}
export function RestoreSuccessNotice() {
  const [message, setMessage] = useState(() => {
    try {
      return sessionStorage.getItem(noticeKey) ?? ''
    } catch {
      return ''
    }
  })
  useEffect(() => {
    try {
      sessionStorage.removeItem(noticeKey)
    } catch {
      /* El aviso sigue en memoria. */
    }
  }, [])
  if (!message) return null
  return (
    <div className="notice" role="status" style={{ margin: 24 }}>
      <span>{message}</span>
      <button type="button" className="secondary-button" onClick={() => setMessage('')}>
        Cerrar aviso
      </button>
    </div>
  )
}
export function RestoreBackupPanel({
  disabled = false,
  onBusyChange,
  onRestored = finishRestore,
}: {
  disabled?: boolean
  onBusyChange?: (busy: boolean) => void
  onRestored?: (result: RestoreResult) => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const [open, setOpen] = useState(false)
  const [confirmation, setConfirmation] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const modal = useRef<HTMLDialogElement>(null)
  const cancel = useRef<HTMLButtonElement>(null)
  const opener = useRef<HTMLButtonElement>(null)
  const title = useId()
  useEffect(() => {
    if (!open) return
    const dialog = modal.current,
      trigger = opener.current
    dialog?.showModal()
    cancel.current?.focus()
    return () => {
      dialog?.close()
      trigger?.focus()
    }
  }, [open])
  async function restore() {
    if (!file || confirmation !== 'RESTAURAR' || busy) return
    setBusy(true)
    setError('')
    onBusyChange?.(true)
    const body = new FormData()
    body.append('file', file)
    body.append('confirmation', confirmation)
    try {
      const response = await apiFetch('/api/v1/backups/restore', {
        method: 'POST',
        body,
        retry: false,
      })
      const result = (await response.json()) as RestoreResult & { error?: string }
      if (!response.ok) throw new Error(result.error ?? 'No se pudo restaurar el backup.')
      onRestored(result)
      setOpen(false)
    } catch (e) {
      setError(
        e instanceof Error && !(e instanceof TypeError)
          ? e.message
          : 'Se perdió la conexión. Antes de repetir la restauración, volvé a ingresar y comprobá el estado del sistema.',
      )
    } finally {
      setBusy(false)
      onBusyChange?.(false)
    }
  }
  return (
    <section style={{ marginTop: 32 }} aria-label="Restaurar backup">
      <h3>Restaurar backup</h3>
      <p>
        Recuperá la información de un ZIP completo generado por Alon. Se reemplazarán los datos
        actuales de toda la instalación, las imágenes y el acceso con autenticador por los de esa
        copia.
      </p>
      <p>
        Antes de reemplazarlos, Alon guardará automáticamente un backup del estado actual. Durante
        la recuperación se pausarán las operaciones y al finalizar se cerrarán todas las sesiones.
      </p>
      <label className="entry-form">
        Archivo de backup (ZIP, hasta 512 MB)
        <input
          type="file"
          accept=".zip,application/zip"
          disabled={disabled || busy}
          onChange={(event) => {
            const selected = event.target.files?.[0] ?? null
            setError('')
            if (selected && selected.size > 512 * 1024 * 1024) {
              setError('El ZIP debe pesar hasta 512 MB.')
              setFile(null)
              event.target.value = ''
              return
            }
            setFile(selected)
          }}
        />
      </label>
      <button
        ref={opener}
        className="secondary-button"
        type="button"
        disabled={!file || disabled || busy}
        onClick={() => {
          setConfirmation('')
          setError('')
          setOpen(true)
        }}
      >
        Restaurar backup…
      </button>
      {error && !open && <p role="alert">{error}</p>}
      {open && (
        <dialog
          ref={modal}
          className="delete-confirmation"
          aria-labelledby={title}
          onCancel={(event) => {
            event.preventDefault()
            if (!busy) setOpen(false)
          }}
        >
          <h2 id={title}>¿Restaurar este backup?</h2>
          <p>
            Archivo elegido: <strong>{file?.name}</strong>
          </p>
          <p>
            Los datos cargados después de esta copia se reemplazarán. Alon guardará una copia del
            estado actual antes de continuar.
          </p>
          <p>
            Después deberás volver a ingresar con los usuarios, contraseñas y autenticador del
            backup recuperado.
          </p>
          <label className="entry-form">
            Escribí RESTAURAR para confirmar
            <input
              value={confirmation}
              disabled={busy}
              autoComplete="off"
              onChange={(event) => setConfirmation(event.target.value)}
            />
          </label>
          {error && <p role="alert">{error}</p>}
          {busy && (
            <p role="status">
              Verificando la copia y recuperando los datos. Puede demorar varios minutos; no cierres
              esta ventana.
            </p>
          )}
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
              disabled={busy || confirmation !== 'RESTAURAR'}
              onClick={() => void restore()}
            >
              {busy ? 'Restaurando…' : 'Confirmar restauración'}
            </button>
          </div>
        </dialog>
      )}
    </section>
  )
}
