import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ReceiptPdfPreview } from '../components/SalesReceiptPreview'
import { formatDateDisplay, formatDateTimeDisplay } from '../lib/format'
import { apiFetch } from '../lib/http'
import { monthNumber, rateAt, type PlatformRate } from '../lib/platformBilling'

const today = () =>
  new Date().toLocaleDateString('en-CA', { timeZone: 'America/Argentina/Buenos_Aires' })
const money = (n: unknown) =>
  Number(n || 0).toLocaleString('es-AR', { style: 'currency', currency: 'ARS' })
type Draft = {
  requestId: string
  date: string
  period: string
  months: number
  discountPercent: number
}
type Receipt = {
  id: number
  fecha: string
  periodo_desde: string
  meses: number
  abono_unitario: number
  abonos_detalle?: string | null
  cargo_configuracion?: number
  bonificacion: number
  subtotal: number
  total: number
  estado: string
  motivo_anulacion: string
}
type Audit = { accion: string; detalle: string; fecha: string; usuario_id: number }
const fresh = (): Draft => ({
  requestId: crypto.randomUUID(),
  date: today(),
  period: today().slice(0, 7),
  months: 1,
  discountPercent: 0,
})
function pending(key: string): Draft | null {
  try {
    const saved = JSON.parse(sessionStorage.getItem(key) ?? 'null')
    return saved?.requestId && saved?.period ? saved : null
  } catch {
    return null
  }
}

export function BusinessReceipts({
  business,
  onSaved,
  showAudit = true,
  rates = [],
}: {
  business: { id: number; nombre: string; abono: string; cargo_configuracion?: number }
  onSaved: () => void
  showAudit?: boolean
  rates?: PlatformRate[]
}) {
  const key = 'alon.pending-service-receipt.' + business.id
  const [draft, setDraft] = useState<Draft>(() => pending(key) ?? fresh())
  const [locked, setLocked] = useState(() => Boolean(pending(key)))
  const [receipts, setReceipts] = useState<Receipt[]>([])
  const [previewId, setPreviewId] = useState<number | null>(null)
  const [previewContainer, setPreviewContainer] = useState<HTMLDialogElement | null>(null)
  const captureSection = useCallback((element: HTMLElement | null) => {
    setPreviewContainer(element?.closest('dialog') ?? null)
  }, [])
  const [audit, setAudit] = useState<Audit[]>([])
  const [revision, setRevision] = useState(0)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [voidId, setVoidId] = useState<number | null>(null)
  const [reason, setReason] = useState('')
  const path = '/api/v1/super/businesses/' + business.id
  useEffect(() => {
    const controller = new AbortController()
    void apiFetch(path + '/account', { signal: controller.signal })
      .then(async (response) => {
        const body = await response.json()
        if (!response.ok) throw new Error(body.error ?? 'No se pudo consultar la cuenta.')
        if (!controller.signal.aborted) {
          setReceipts(body.receipts ?? [])
          setAudit(body.audit ?? [])
        }
      })
      .catch((error) => {
        if (!controller.signal.aborted) setMessage(error.message)
      })
    return () => controller.abort()
  }, [path, revision])
  const issue = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy || !event.currentTarget.reportValidity()) return
    setBusy(true)
    setMessage('')
    try {
      sessionStorage.setItem(key, JSON.stringify(draft))
      setLocked(true)
      const response = await apiFetch(path + '/receipts', {
        method: 'POST',
        retry: false,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(draft),
      })
      const body = await response.json()
      if (!response.ok) {
        if (response.status >= 400 && response.status < 500) {
          sessionStorage.removeItem(key)
          setLocked(false)
          setDraft({ ...draft, requestId: crypto.randomUUID() })
        }
        throw new Error(body.error ?? 'No se pudo registrar el recibo.')
      }
      sessionStorage.removeItem(key)
      setLocked(false)
      setDraft(fresh())
      setPreviewId(Number(body.id))
      setMessage('Recibo #' + body.id + ' registrado. El cobro no modifica el estado de acceso.')
      setRevision((n) => n + 1)
      onSaved()
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : 'No se pudo confirmar el recibo. Reintentá con la misma referencia.',
      )
    } finally {
      setBusy(false)
    }
  }
  const cancel = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy || !voidId || !reason.trim()) return
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch(path + '/receipts/' + voidId + '/void', {
        method: 'POST',
        retry: false,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason }),
      })
      const body = await response.json()
      if (!response.ok) throw new Error(body.error ?? 'No se pudo anular.')
      setVoidId(null)
      setReason('')
      setRevision((n) => n + 1)
      onSaved()
      setMessage(
        'Recibo anulado. Se conserva el historial y los períodos vuelven a estar disponibles.',
      )
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar.')
    } finally {
      setBusy(false)
    }
  }
  const configuration = receipts.some(
    (receipt) => receipt.estado === 'EMITIDO' && Number(receipt.cargo_configuracion) > 0,
  )
    ? 0
    : Number(business.cargo_configuracion ?? 0)
  const firstMonth = monthNumber(draft.period)
  const monthlySubtotal = Array.from(
    { length: Math.min(120, Math.max(0, draft.months)) },
    (_, offset) => rateAt(rates, firstMonth + offset, Number(business.abono)),
  ).reduce((sum, amount) => sum + amount, 0)
  const subtotal = monthlySubtotal + configuration
  const total = monthlySubtotal * (1 - draft.discountPercent / 100) + configuration
  return (
    <section ref={captureSection} aria-label={'Recibos de ' + business.nombre}>
      {previewId !== null && (
        <ReceiptPdfPreview
          key={previewId}
          title={'Recibo Alon #' + previewId}
          path={path + '/receipts/' + previewId + '/pdf'}
          download={'alon-recibo-' + previewId + '.pdf'}
          container={previewContainer}
          onClose={() => setPreviewId(null)}
        />
      )}
      <h4>Recibos del servicio Alon</h4>
      <p>
        El recibo registra el cargo neto del abono y su cobro. Los cargos manuales anteriores se
        concilian por separado. Los pagos no habilitan ni inhabilitan el servicio.
      </p>
      {locked && (
        <p role="status">
          Hay un envío pendiente. Reintentá el mismo recibo para confirmar su resultado sin
          duplicarlo.
        </p>
      )}
      <form className="entry-form" onSubmit={(event) => void issue(event)}>
        <fieldset disabled={busy || locked}>
          <label>
            Fecha del recibo
            <input
              type="date"
              required
              value={draft.date}
              onChange={(e) => setDraft({ ...draft, date: e.target.value })}
            />
          </label>
          <label>
            Primer mes a cobrar
            <input
              type="month"
              required
              value={draft.period}
              onChange={(e) => setDraft({ ...draft, period: e.target.value })}
            />
          </label>
          <label>
            Cantidad de meses
            <input
              type="number"
              required
              min={1}
              max={120}
              step={1}
              value={draft.months}
              onChange={(e) => setDraft({ ...draft, months: Number(e.target.value) })}
            />
          </label>
          <label>
            {configuration > 0 ? 'Bonificación sobre el abono (%)' : 'Bonificación (%)'}
            <input
              type="number"
              required
              min={0}
              max={100}
              step="0.01"
              value={draft.discountPercent}
              onChange={(e) => setDraft({ ...draft, discountPercent: Number(e.target.value) })}
            />
          </label>
        </fieldset>
        {configuration > 0 && <p>Configuración por única vez: {money(configuration)}</p>}
        <p>
          Abonos del período: {money(monthlySubtotal)} · Subtotal: {money(subtotal)} · Total
          estimado: {money(total)}. El servidor confirma el importe al emitir.
        </p>
        <button className="primary-button" disabled={busy}>
          {locked ? 'Confirmar recibo pendiente' : 'Emitir recibo y registrar cobro'}
        </button>
      </form>
      {message && <p role="status">{message}</p>}
      <h4>Historial de recibos</h4>
      {!receipts.length && <p>Todavía no hay recibos.</p>}
      {receipts.map((receipt) => {
        return (
          <article className="platform-receipt" key={receipt.id}>
            <strong>
              Recibo #{receipt.id} · {receipt.estado}
            </strong>
            <p>
              {formatDateDisplay(receipt.fecha)} · Desde {formatDateDisplay(receipt.periodo_desde)}{' '}
              · {receipt.meses} meses · Bonificación {receipt.bonificacion}% · Total{' '}
              {money(receipt.total)}
            </p>
            {Number(receipt.cargo_configuracion) > 0 && (
              <p>Configuración por única vez: {money(receipt.cargo_configuracion)}</p>
            )}
            {receipt.motivo_anulacion && <p>Motivo de anulación: {receipt.motivo_anulacion}</p>}
            <button
              type="button"
              className="select-button"
              onClick={() => setPreviewId(receipt.id)}
            >
              Ver recibo #{receipt.id}
            </button>
            {receipt.estado === 'EMITIDO' && (
              <button
                type="button"
                className="select-button"
                disabled={busy}
                onClick={() => {
                  setVoidId(receipt.id)
                  setReason('')
                }}
              >
                Anular recibo #{receipt.id}
              </button>
            )}
          </article>
        )
      })}
      {voidId && (
        <form className="entry-form" onSubmit={(event) => void cancel(event)}>
          <p>Se anulará el recibo #{voidId} y su cargo y cobro. El historial quedará conservado.</p>
          <label>
            Motivo de anulación
            <input
              required
              maxLength={300}
              value={reason}
              disabled={busy}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <button className="primary-button" disabled={busy}>
            Confirmar anulación
          </button>
          <button
            type="button"
            className="select-button"
            disabled={busy}
            onClick={() => setVoidId(null)}
          >
            Conservar recibo
          </button>
        </form>
      )}
      {showAudit && <AuditEntries audit={audit} />}
    </section>
  )
}

function AuditEntries({ audit }: { audit: Audit[] }) {
  return (
    <>
      <h4>Historial de cambios</h4>
      {!audit.length && <p>Todavía no hay cambios registrados.</p>}
      {audit.map((item, index) => (
        <p key={index}>
          {formatDateTimeDisplay(item.fecha)} · {item.accion} {item.detalle && '· ' + item.detalle}{' '}
          · Usuario {item.usuario_id}
        </p>
      ))}
    </>
  )
}

export function BusinessAudit({ businessId }: { businessId: number }) {
  const [audit, setAudit] = useState<Audit[] | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    void apiFetch('/api/v1/super/businesses/' + businessId + '/account', {
      signal: controller.signal,
    })
      .then(async (response) => {
        const body = await response.json()
        if (!response.ok) throw new Error(body.error ?? 'No se pudo consultar el historial.')
        if (!controller.signal.aborted) setAudit(body.audit ?? [])
      })
      .catch((failure: Error) => {
        if (!controller.signal.aborted) setError(failure.message)
      })
    return () => controller.abort()
  }, [businessId])
  return audit ? (
    <AuditEntries audit={audit} />
  ) : (
    <p role="status">{error || 'Cargando historial.'}</p>
  )
}
