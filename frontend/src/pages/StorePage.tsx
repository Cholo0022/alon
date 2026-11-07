// Muestra los pedidos web y permite revisarlos antes de confirmar la venta.
import { useEffect, useRef, useState } from 'react'
import { ApiTable } from '../components/ApiTable'
import { apiFetch } from '../lib/http'
import { currency, formatDateTimeDisplay } from '../lib/format'

type OrderLine = {
  id: number
  nombre_articulo: string
  cantidad: number
  precio_unitario: number
  incluido: boolean
  stock: number
  estado_articulo: string
  unidad: string
}

/** El formulario web recibe celulares argentinos con área, sin 0 ni 15. */
export function orderWhatsAppUrl(contact: string): string | null {
  const digits = contact.replace(/[^0-9]/g, '')
  const number = /^\d{10}$/.test(digits)
    ? '549' + digits
    : /^54\d{10}$/.test(digits)
      ? '549' + digits.slice(2)
      : /^549\d{10}$/.test(digits)
        ? digits
        : null
  return number ? 'https://wa.me/' + number : null
}
type Order = {
  id: number
  nombre_cliente: string
  contacto: string
  dni: string | null
  domicilio: string | null
  provincia?: string | null
  ciudad?: string | null
  comentario_cliente?: string | null
  detalle: string
  estado: string
  total_estimado: number
  creado_en: string
  lines: OrderLine[]
  medio_pago: string | null
  venta_id: number | null
  cash_open: boolean
}
/** Traduce el estado interno de un pedido a una etiqueta legible. */
const statusLabel = (state: string) =>
  state === 'CONFIRMADO'
    ? 'Confirmado'
    : state === 'CANCELADO'
      ? 'Rechazado'
      : 'Pendiente de confirmación'

/** Presenta pedidos web y su detalle para quitar artículos, elegir cobro, confirmar la venta o cancelar el pedido. */
export function StorePage() {
  const [selected, setSelected] = useState<number | null>(null)
  const [order, setOrder] = useState<Order | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [paymentDraft, setPaymentDraft] = useState({ orderId: 0, value: '' })
  const detailPanel = useRef<HTMLElement>(null)
  const scrollToOrder = useRef(false)
  const payment =
    order && paymentDraft.orderId === order.id ? paymentDraft.value : (order?.medio_pago ?? '')
  useEffect(() => {
    if (!order || !scrollToOrder.current) return
    scrollToOrder.current = false
    detailPanel.current?.scrollIntoView?.({
      behavior: window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
      block: 'start',
    })
    detailPanel.current?.focus({ preventScroll: true })
  }, [order])
  useEffect(() => {
    if (selected === null) return
    let active = true
    const controller = new AbortController()
    void apiFetch('/api/v1/alon/store-orders/' + selected, { signal: controller.signal })
      .then(async (r) => {
        const body = await r.json()
        if (!r.ok) throw new Error(body.error ?? 'No se pudo cargar el pedido.')
        if (active) setOrder(body as Order)
      })
      .catch((e) => {
        if (active) setError(e.message)
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [selected, revision])
  useEffect(() => {
    const timer = window.setInterval(() => {
      window.dispatchEvent(new Event('alon:refresh'))
      setRevision((n) => n + 1)
    }, 20000)
    return () => clearInterval(timer)
  }, [])
  /** Envía la acción elegida sobre el pedido, incluye el medio al aprobarlo y recarga el detalle y los contadores al completarse. */
  const action = async (suffix: string) => {
    if (!order || busy) return
    setBusy(true)
    setError('')
    try {
      const r = await apiFetch('/api/v1/operations/store-orders/' + order.id + suffix, {
        method: 'POST',
        ...(suffix === '/approve'
          ? {
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ paymentMethod: payment }),
            }
          : {}),
      })
      if (!r.ok) {
        const b = await r.json()
        throw new Error(b.error ?? 'No se pudo actualizar el pedido.')
      }
      setRevision((n) => n + 1)
      window.dispatchEvent(new Event('alon:refresh'))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo actualizar el pedido.')
    } finally {
      setBusy(false)
    }
  }
  const pending = order?.estado === 'PENDIENTE_CONFIRMACION'
  const whatsAppUrl = order ? orderWhatsAppUrl(order.contacto ?? '') : null
  const included = order?.lines.filter((l) => Boolean(l.incluido)) ?? []
  const shortage = included.some(
    (l) => Number(l.cantidad) > Number(l.stock) || l.estado_articulo !== 'ACTIVO',
  )
  const approvalBlockers = [
    ...(!order?.cash_open ? ['Abrí la caja de hoy desde Caja.'] : []),
    ...(!included.length ? ['El pedido no tiene artículos incluidos para vender.'] : []),
    ...(shortage ? ['Quitá los artículos inactivos o sin stock suficiente.'] : []),
    ...(!payment ? ['Seleccioná un medio de pago.'] : []),
  ]
  return (
    <div className="store-module">
      <section className="panel store-introduction">
        <div>
          <h2>Tu tienda conectada</h2>
          <p>
            Los pedidos llegan pendientes. Revisá los artículos y el medio de pago. Para confirmar
            necesitás la caja del día abierta.
          </p>
        </div>
        <a className="primary-button" href="/tienda" target="_blank" rel="noreferrer">
          Abrir tienda ↗
        </a>
      </section>
      {error && (
        <p className="entry-message" role="alert">
          {error}
        </p>
      )}
      <ApiTable
        title="Pedidos de la tienda online"
        endpoint="/api/v1/alon/store-orders"
        rowClassName={(row) =>
          row.estado === 'PENDIENTE_CONFIRMACION' ? 'store-order-pending' : undefined
        }
        columns={[
          { key: 'id', label: 'NÚMERO' },
          { key: 'nombre_cliente', label: 'CLIENTE' },
          { key: 'contacto', label: 'CONTACTO' },
          { key: 'comentario_cliente', label: 'CLIENTE WEB' },
          { key: 'total_estimado', label: 'TOTAL', money: true },
          { key: 'medio_pago', label: 'MEDIO DE PAGO' },
          { key: 'estado', label: 'ESTADO', render: (row) => statusLabel(String(row.estado)) },
          { key: 'creado_en', label: 'FECHA', dateTime: true },
          {
            key: 'review',
            label: 'ACCIONES',
            render: (row) => (
              <button
                type="button"
                className="secondary-button store-view-order"
                onClick={() => {
                  scrollToOrder.current = true
                  setOrder(null)
                  setError('')
                  setSelected(Number(row.id))
                  setRevision((n) => n + 1)
                }}
              >
                Ver pedido
              </button>
            ),
          },
        ]}
        note="Confirmar registra una venta de origen web y su cobro en caja, y descuenta el stock una sola vez. El comprobante es interno no fiscal."
      />
      {selected !== null && (
        <section
          className="panel store-order-detail"
          ref={detailPanel}
          tabIndex={-1}
          aria-labelledby="store-order-heading"
        >
          <div className="panel-heading">
            <h2 id="store-order-heading">Pedido #{selected}</h2>
            <button
              className="secondary-button"
              onClick={() => {
                scrollToOrder.current = false
                setSelected(null)
                setOrder(null)
              }}
            >
              Cerrar detalle
            </button>
          </div>
          {!order ? (
            <p>Cargando pedido…</p>
          ) : (
            <>
              <div className="store-buyer">
                <div>
                  <strong>{order.nombre_cliente}</strong>
                  <p>
                    DNI: {order.dni ?? 'No informado'} · Tel.: {order.contacto}
                  </p>
                  {whatsAppUrl && (
                    <a
                      className="store-whatsapp-link"
                      href={whatsAppUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      aria-label={'Chatear por WhatsApp con ' + order.nombre_cliente}
                    >
                      <svg
                        width="22"
                        height="22"
                        viewBox="0 0 24 24"
                        fill="currentColor"
                        aria-hidden="true"
                      >
                        <path d="M20.5 3.5A11.9 11.9 0 0 0 12 0C5.4 0 0 5.4 0 12c0 2.1.5 4.2 1.6 6L0 24l6.2-1.6A12 12 0 0 0 12 24c6.6 0 12-5.4 12-12 0-3.2-1.2-6.2-3.5-8.5ZM12 22a10 10 0 0 1-5.1-1.4l-.4-.2-3.7 1 1-3.6-.3-.4A10 10 0 1 1 12 22Zm5.5-7.5c-.3-.1-1.8-.9-2.1-1-.3-.1-.5-.1-.7.2l-1 1.2c-.2.2-.4.2-.7.1a8.2 8.2 0 0 1-2.4-1.5 9 9 0 0 1-1.7-2.1c-.2-.3 0-.5.1-.6l.5-.6.3-.5c.1-.2 0-.4 0-.6L8.9 7c-.2-.5-.5-.5-.7-.5h-.6c-.2 0-.6.1-.9.4-.3.3-1.1 1.1-1.1 2.6s1.1 2.9 1.3 3.1c.1.2 2.2 3.4 5.3 4.8.7.3 1.3.5 1.8.6.8.3 1.5.2 2.1.1.6-.1 1.8-.7 2.1-1.5.2-.7.2-1.4.2-1.5-.1-.2-.3-.3-.6-.4Z" />
                      </svg>
                      WhatsApp
                    </a>
                  )}
                  <p>{order.domicilio ?? 'Domicilio no informado'}</p>
                  {order.comentario_cliente && (
                    <p className="entry-message" role="status">
                      {order.comentario_cliente}
                    </p>
                  )}
                </div>
                <div>
                  <strong>{statusLabel(order.estado)}</strong>
                  <p>{formatDateTimeDisplay(order.creado_en)}</p>
                </div>
              </div>
              {pending && shortage && (
                <p className="entry-message">
                  Hay artículos inactivos o sin stock suficiente. Quitalos del pedido para poder
                  confirmar.
                </p>
              )}
              {pending && !order.cash_open && (
                <p className="entry-message" role="status">
                  La caja de hoy está cerrada. Abrila desde Caja antes de confirmar este pedido.
                </p>
              )}
              {order.venta_id && (
                <p>
                  Venta web vinculada: #{order.venta_id} · Medio de pago:{' '}
                  {order.medio_pago?.replaceAll('_', ' ')}
                </p>
              )}
              {!order.lines.length ? (
                <p>
                  Pedido anterior sin artículos vinculados: {order.detalle}. No se puede descontar
                  stock automáticamente.
                </p>
              ) : (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>ARTÍCULO</th>
                        <th>CANTIDAD</th>
                        <th>STOCK ACTUAL</th>
                        <th>PRECIO UNITARIO</th>
                        <th>SUBTOTAL</th>
                        <th>ACCIÓN</th>
                      </tr>
                    </thead>
                    <tbody>
                      {order.lines.map((l) => (
                        <tr key={l.id} className={!l.incluido ? 'store-excluded' : ''}>
                          <td data-label="Artículo">
                            {l.nombre_articulo}
                            {!l.incluido && <small> · Quitado del pedido</small>}
                          </td>
                          <td data-label="Cantidad">
                            {Number(l.cantidad)} {l.unidad}
                          </td>
                          <td data-label="Stock actual">
                            <span
                              className={
                                Number(l.stock) < Number(l.cantidad) ||
                                l.estado_articulo !== 'ACTIVO'
                                  ? 'stock-low'
                                  : 'stock-ok'
                              }
                            >
                              {Number(l.stock)}
                              {l.estado_articulo !== 'ACTIVO' && ' · Inactivo'}
                            </span>
                          </td>
                          <td data-label="Precio unitario">
                            {currency.format(Number(l.precio_unitario))}
                          </td>
                          <td data-label="Subtotal">
                            {currency.format(
                              Math.round(Number(l.cantidad) * Number(l.precio_unitario) * 100) /
                                100,
                            )}
                          </td>
                          <td data-label="Acción">
                            {pending && Boolean(l.incluido) && (
                              <button
                                className="secondary-button"
                                disabled={busy}
                                onClick={() => void action('/lines/' + l.id + '/exclude')}
                              >
                                Quitar
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {pending && approvalBlockers.length > 0 && (
                <div id="store-approval-blockers" className="entry-message" role="status">
                  <strong>Para habilitar Confirmar venta web:</strong>
                  <ul>
                    {approvalBlockers.map((reason) => (
                      <li key={reason}>{reason}</li>
                    ))}
                  </ul>
                </div>
              )}
              <div className="store-order-actions">
                <strong>Total: {currency.format(Number(order.total_estimado))}</strong>
                {pending && (
                  <label className="store-payment-field">
                    Medio de pago
                    <select
                      aria-label="Medio de pago del pedido"
                      value={payment}
                      onChange={(e) =>
                        setPaymentDraft({ orderId: order.id, value: e.target.value })
                      }
                      disabled={busy}
                    >
                      <option value="">Seleccioná un medio de pago</option>
                      <option value="EFECTIVO">Efectivo</option>
                      <option value="TRANSFERENCIA">Transferencia</option>
                      <option value="MERCADO_PAGO">Mercado Pago</option>
                    </select>
                  </label>
                )}
                {pending && (
                  <div className="row-actions">
                    <button
                      className="secondary-button"
                      disabled={busy}
                      onClick={() => {
                        if (window.confirm('¿Rechazar este pedido? No se generará una venta.'))
                          void action('/cancel')
                      }}
                    >
                      Rechazar pedido
                    </button>
                    <button
                      className="primary-button"
                      disabled={busy || approvalBlockers.length > 0}
                      aria-describedby={
                        approvalBlockers.length ? 'store-approval-blockers' : undefined
                      }
                      onClick={() => {
                        if (
                          window.confirm(
                            '¿Confirmar el pedido #' +
                              order.id +
                              ' con pago por ' +
                              payment.replaceAll('_', ' ').toLowerCase() +
                              '? Se registrará una venta web y su cobro en caja, y se descontará el stock.',
                          )
                        )
                          void action('/approve')
                      }}
                    >
                      {busy ? 'Procesando…' : 'Confirmar venta web'}
                    </button>
                  </div>
                )}
              </div>
            </>
          )}
        </section>
      )}
    </div>
  )
}
