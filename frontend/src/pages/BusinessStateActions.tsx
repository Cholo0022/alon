import { useEffect, useRef, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { businessStates } from '../lib/businessStates'

export function BusinessStateActions({
  business,
  onSaved,
  onRefresh,
}: {
  business: {
    id: number
    nombre: string
    estado: string
    eliminado_en?: string | null
    baja_iniciada_en?: string | null
    baja_paso?: string
    estado_objetivo?: string | null
    estado_aplicacion?: string
    estado_error?: string
  }
  onSaved: () => void
  onRefresh?: () => void
}) {
  const [state, setState] = useState(business.estado)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [confirmation, setConfirmation] = useState('')
  const dialog = useRef<HTMLDialogElement>(null)
  const cancelButton = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    if (confirming) {
      dialog.current?.showModal()
      cancelButton.current?.focus()
    } else if (dialog.current?.open) dialog.current.close()
  }, [confirming])
  const cancelDeletion = () => {
    if (busy) return
    setConfirming(false)
    setConfirmation('')
  }
  const apply = async () => {
    if (
      busy ||
      business.eliminado_en ||
      (state === 'BAJA' && (!confirming || confirmation !== business.nombre))
    )
      return
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch(`/api/v1/super/businesses/${business.id}/state`, {
        method: 'PUT',
        retry: false,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ state, reason, ...(state === 'BAJA' ? { confirmation } : {}) }),
      })
      const body = await response.json()
      if (!response.ok) throw new Error(body.error ?? 'No se pudo cambiar el estado.')
      if (state === 'BAJA' && body.deleted !== true)
        throw new Error(
          'El servidor no confirmó la eliminación definitiva. Reiniciá Alon actualizado y consultá el estado antes de reintentar.',
        )
      setReason('')
      setConfirming(false)
      setConfirmation('')
      setMessage(
        state === 'BAJA'
          ? 'Negocio eliminado definitivamente. Se conserva la ficha administrativa y los abonos.'
          : 'Estado actualizado: ' + businessStates[state] + '.',
      )
      onSaved()
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : 'No se pudo conectar. Actualizá el listado antes de reintentar.',
      )
      setConfirming(false)
      setConfirmation('')
      onRefresh?.()
    } finally {
      setBusy(false)
    }
  }
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (busy || business.eliminado_en || (state === business.estado && state !== 'BAJA')) return
    if (state === 'BAJA') {
      setConfirmation('')
      setConfirming(true)
    } else await apply()
  }
  if (business.eliminado_en)
    return <p>Eliminado definitivamente. Su base e instalación ya no existen.</p>
  return (
    <form onSubmit={(event) => void submit(event)} className="business-state-actions">
      <p>
        Estado confirmado: <strong>{businessStates[business.estado]}</strong>
      </p>
      {business.estado_aplicacion && business.estado_aplicacion !== 'CONFIRMADO' && (
        <p role="alert">
          Cambio {business.estado_aplicacion === 'APLICANDO' ? 'en curso' : 'incompleto'}
          {business.estado_objetivo ? ': ' + businessStates[business.estado_objetivo] : ''}.{' '}
          {business.estado_error}
        </p>
      )}
      {business.baja_iniciada_en && !business.eliminado_en && (
        <p role="alert">
          Baja incompleta. Paso pendiente:{' '}
          {(
            {
              DETENER: 'detener la aplicación',
              BASE: 'eliminar la base de datos',
              USUARIO: 'eliminar el usuario MySQL (base eliminada)',
              ARCHIVOS: 'eliminar archivos (base y usuario eliminados)',
              FINALIZAR: 'confirmar el cierre administrativo (recursos eliminados)',
            } as Record<string, string>
          )[business.baja_paso ?? ''] ?? 'revisar recursos pendientes'}
          . Reintentá la baja para completarla.
        </p>
      )}
      <label>
        Estado de {business.nombre}
        <select
          value={state}
          disabled={busy || confirming || Boolean(business.baja_iniciada_en)}
          onChange={(event) => {
            setState(event.target.value)
            setMessage('')
            setConfirmation('')
            if (event.target.value === 'BAJA') setConfirming(true)
          }}
        >
          {Object.entries(businessStates).map(([key, label]) => (
            <option key={key} value={key}>
              {label}
            </option>
          ))}
        </select>
      </label>
      {(state !== business.estado || state === 'BAJA') && (
        <>
          <p>
            <strong>Cambio pendiente: presioná el botón para revisarlo y confirmarlo.</strong>
          </p>
          <label>
            Motivo del cambio de {business.nombre}
            <input
              value={reason}
              maxLength={300}
              disabled={busy}
              onChange={(event) => setReason(event.target.value)}
            />
          </label>
          <p>
            {state === 'BAJA'
              ? 'Baja definitiva: se borrarán la base, el usuario MySQL y todos los archivos de la instalación, incluidas imágenes y copias locales. No se puede deshacer.'
              : ['PREPARACION', 'SUSPENDIDO'].includes(state)
                ? 'Se bloqueará el acceso al sistema y los pedidos de la tienda. Se conservan los datos y el historial.'
                : 'Se permitirá operar si la instalación está preparada y su licencia está vigente.'}
          </p>
          <button className="primary-button" disabled={busy || confirming}>
            {busy
              ? 'Aplicando…'
              : state === 'BAJA'
                ? 'Revisar baja definitiva de ' + business.nombre
                : 'Aplicar estado de ' + business.nombre}
          </button>
        </>
      )}
      <dialog
        ref={dialog}
        className="business-delete-dialog"
        aria-label={'Confirmar baja de ' + business.nombre}
        onCancel={(event) => {
          event.preventDefault()
          cancelDeletion()
        }}
        onKeyDownCapture={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault()
            event.stopPropagation()
            cancelDeletion()
          }
        }}
      >
        <h3>¿Eliminar definitivamente {business.nombre}?</h3>
        <p>
          Se eliminarán la base de datos, usuarios del negocio, imágenes, copias locales y archivos
          de su app. Esta acción no se puede deshacer. Solo se conserva la ficha administrativa y el
          historial de abonos de Alon.
        </p>
        <p>
          Inhabilitar conserva los datos y permite volver a operar. Si querés conservar una copia,
          descargala antes de dar la baja.
        </p>
        <button
          ref={cancelButton}
          autoFocus
          type="button"
          className="select-button"
          disabled={busy}
          onClick={cancelDeletion}
        >
          No dar de baja
        </button>
        <label>
          Para eliminar, escribí el nombre exacto: {business.nombre}
          <input
            name="confirmation"
            data-preserve-case="true"
            type="text"
            placeholder={business.nombre}
            autoCapitalize="off"
            spellCheck={false}
            value={confirmation}
            autoComplete="off"
            disabled={busy}
            onChange={(event) => setConfirmation(event.target.value)}
          />
        </label>
        <p>Enter cancela la baja. Para eliminar, usá el botón explícitamente.</p>
        <button
          type="button"
          className="primary-button"
          disabled={busy || confirmation !== business.nombre}
          onClick={() => void apply()}
        >
          Eliminar definitivamente
        </button>
      </dialog>
      {message && <p role="status">{message}</p>}
    </form>
  )
}
