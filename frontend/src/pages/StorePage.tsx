import { orderWhatsAppUrl } from '../lib/orderWhatsApp'
// Muestra los pedidos web y permite revisarlos antes de confirmar la venta.
import { useEffect, useRef, useState } from 'react'
import { ApiTable } from '../components/ApiTable'
import { apiFetch } from '../lib/http'
import { currency, formatDateTimeDisplay } from '../lib/format'

type OrderLine = {
  id: number
  nombre_articulo: string
  cantidad: number
  unidad_actual?: string
  precio_unitario: number
  incluido: boolean
  stock: number
  estado_articulo: string
  unidad: string
  tipo_producto?: string
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
const orderStatuses: Record<string, string> = {
  PENDIENTE_CONFIRMACION: 'Pendiente de confirmación',
  PENDIENTE_PAGO: 'Confirmado · pendiente de pago',
  CONFIRMADO: 'Pagado',
  CANCELADO: 'Rechazado',
}
const statusLabel = (state: string) => orderStatuses[state] ?? state

/** Permite revisar los pedidos web, confirmar sus artículos y registrar el pago. */
export function StorePage() {
  const [status, setStatus] = useState('')
  const [selected, setSelected] = useState<number | null>(null)
  const [order, setOrder] = useState<Order | null>(null)
  const [quantityDraft, setQuantityDraft] = useState<{ lineId: number; value: string } | null>(null)
  const [quantityError, setQuantityError] = useState('')
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
    if (selected === null || busy) return
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
  }, [selected, revision, busy])
  useEffect(() => {
    const timer = window.setInterval(() => {
      window.dispatchEvent(new Event('alon:refresh'))
      setRevision((n) => n + 1)
    }, 20000)
    return () => clearInterval(timer)
  }, [])
  /** Actualiza el pedido y recarga el detalle y los contadores. */
  const action = async (
    suffix: string,
    payload?: { quantity: number } | { paymentMethod: string },
  ) => {
    if (!order || busy) return
    setBusy(true)
    setError('')
    try {
      const r = await apiFetch('/api/v1/operations/store-orders/' + order.id + suffix, {
        method: 'POST',
        ...(payload
          ? {
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(payload),
            }
          : {}),
      })
      if (!r.ok) {
        const b = await r.json().catch(() => ({}))
        throw new Error(b.error ?? 'No se pudo actualizar el pedido.')
      }
      if (suffix.endsWith('/quantity')) {
        setOrder((await r.json()) as Order)
        setQuantityDraft(null)
      }
      setRevision((n) => n + 1)
      window.dispatchEvent(new Event('alon:refresh'))
    } catch (e) {
      const message = e instanceof Error ? e.message : 'No se pudo actualizar el pedido.'
      if (suffix.endsWith('/quantity')) setQuantityError(message)
      else setError(message)
    } finally {
      setBusy(false)
    }
  }
  const pending = order?.estado === 'PENDIENTE_CONFIRMACION'
  const awaitingPayment = order?.estado === 'PENDIENTE_PAGO'
  const whatsAppUrl = order ? orderWhatsAppUrl(order.contacto ?? '') : null
  const included = order?.lines.filter((l) => Boolean(l.incluido)) ?? []
  const shortage = included.some(
    (l) => Number(l.cantidad) > Number(l.stock) || l.estado_articulo !== 'ACTIVO',
  )
  const approvalBlockers = [
    ...(!included.length ? ['El pedido no tiene artículos incluidos para vender.'] : []),
    ...(shortage
      ? ['Ajustá la cantidad al stock disponible o quitá los artículos inactivos o sin stock.']
      : []),
  ]
  return (
    <div className="store-module">
      <section className="panel store-introduction">
        <div>
          <h2>Tu tienda conectada</h2>
          <p>
            Confirmá el pedido para descontar el stock. Registrá el pago cuando lo recibas, con la
            caja del día abierta.
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
      <label className="store-payment-field">
        Estado de los pedidos
        <select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="">Todos</option>
          <option value="PENDIENTE_CONFIRMACION">Pendientes de confirmación</option>
          <option value="PENDIENTE_PAGO">Confirmados pendientes de pago</option>
          <option value="CONFIRMADO">Pagados</option>
          <option value="CANCELADO">Rechazados</option>
        </select>
      </label>
      <ApiTable
        title="Pedidos de la tienda online"
        endpoint={'/api/v1/alon/store-orders' + (status ? '?status=' + status : '')}
        rowClassName={(row) =>
          row.estado === 'PENDIENTE_CONFIRMACION' ? 'store-order-pending' : undefined
        }
        columns={[
          { key: 'id', label: 'NÚMERO' },
          { key: 'nombre_cliente', label: 'CLIENTE', partyName: true },
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
                disabled={busy}
                onClick={() => {
                  scrollToOrder.current = true
                  setQuantityDraft(null)
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
              disabled={busy}
              onClick={() => {
                scrollToOrder.current = false
                setQuantityDraft(null)
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
                  Ajustá las cantidades al stock disponible y quitá los artículos inactivos o sin
                  stock.
                </p>
              )}
              {awaitingPayment && !order.cash_open && (
                <p className="entry-message" role="status">
                  La caja de hoy está cerrada. Abrila desde Caja antes de registrar el pago.
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
                            {pending && l.incluido && quantityDraft?.lineId === l.id ? (
                              <form
                                onSubmit={(event) => {
                                  event.preventDefault()
                                  const value = quantityDraft.value.trim().replace(',', '.')
                                  if (
                                    !/^\d{1,12}(?:\.\d{1,3})?$/.test(value) ||
                                    Number(value) <= 0
                                  ) {
                                    setQuantityError(
                                      'Ingresá una cantidad mayor que cero con hasta tres decimales.',
                                    )
                                    return
                                  }
                                  if (
                                    (String(l.unidad_actual ?? l.unidad)
                                      .trim()
                                      .toUpperCase() === 'UNIDAD' ||
                                      l.tipo_producto === 'INDUMENTARIA' ||
                                      l.tipo_producto === 'CALZADO') &&
                                    !Number.isInteger(Number(value))
                                  ) {
                                    setQuantityError('Ingresá una cantidad entera.')
                                    return
                                  }
                                  setQuantityError('')
                                  void action('/lines/' + l.id + '/quantity', {
                                    quantity: Number(value),
                                  })
                                }}
                              >
                                <input
                                  aria-label={'Cantidad de ' + l.nombre_articulo}
                                  type="text"
                                  inputMode="decimal"
                                  aria-invalid={quantityError ? true : undefined}
                                  aria-describedby={
                                    quantityError ? 'store-quantity-error' : undefined
                                  }
                                  disabled={busy}
                                  value={quantityDraft.value}
                                  onChange={(event) => {
                                    setQuantityError('')
                                    setQuantityDraft({ lineId: l.id, value: event.target.value })
                                  }}
                                />
                                {quantityError && (
                                  <p
                                    id="store-quantity-error"
                                    className="entry-message"
                                    role="alert"
                                  >
                                    {quantityError}
                                  </p>
                                )}
                                <div className="row-actions">
                                  <button
                                    type="submit"
                                    className="secondary-button"
                                    disabled={busy}
                                  >
                                    Guardar cantidad
                                  </button>
                                  <button
                                    type="button"
                                    className="secondary-button"
                                    disabled={busy}
                                    onClick={() => setQuantityDraft(null)}
                                  >
                                    Cancelar
                                  </button>
                                </div>
                              </form>
                            ) : (
                              <>
                                {Number(l.cantidad)} {l.unidad}
                              </>
                            )}
                          </td>
                          <td data-label="Stock actual">
                            <span
                              className={
                                pending &&
                                (Number(l.stock) < Number(l.cantidad) ||
                                  l.estado_articulo !== 'ACTIVO')
                                  ? 'stock-low'
                                  : 'stock-ok'
                              }
                            >
                              {Number(l.stock)}
                              {l.estado_articulo !== 'ACTIVO' && ' · Inactivo'}
                            </span>
                          </td>
                          <td data-label="Precio unitario" className="money-cell">
                            {currency.format(Number(l.precio_unitario))}
                          </td>
                          <td data-label="Subtotal" className="money-cell">
                            {currency.format(
                              Math.round(Number(l.cantidad) * Number(l.precio_unitario) * 100) /
                                100,
                            )}
                          </td>
                          <td data-label="Acción">
                            {pending && Boolean(l.incluido) && (
                              <div className="row-actions">
                                <button
                                  type="button"
                                  className="secondary-button"
                                  disabled={busy || quantityDraft !== null}
                                  onClick={() => {
                                    setQuantityError('')
                                    setQuantityDraft({ lineId: l.id, value: String(l.cantidad) })
                                  }}
                                >
                                  Modificar cantidad
                                </button>
                                <button
                                  className="secondary-button"
                                  disabled={busy || quantityDraft !== null}
                                  onClick={() => void action('/lines/' + l.id + '/exclude')}
                                >
                                  Quitar
                                </button>
                              </div>
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
                  <strong>Para habilitar Confirmar pedido:</strong>
                  <ul>
                    {approvalBlockers.map((reason) => (
                      <li key={reason}>{reason}</li>
                    ))}
                  </ul>
                </div>
              )}
              <div className="store-order-actions">
                <strong>Total: {currency.format(Number(order.total_estimado))}</strong>
                {awaitingPayment && (
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
                {(pending || awaitingPayment) && (
                  <div className="row-actions">
                    <button
                      className="secondary-button"
                      disabled={busy}
                      onClick={() => {
                        if (
                          window.confirm(
                            awaitingPayment
                              ? '¿Rechazar este pedido y devolver los artículos al stock?'
                              : '¿Rechazar este pedido?',
                          )
                        )
                          void action('/cancel')
                      }}
                    >
                      Rechazar pedido
                    </button>
                    {pending ? (
                      <button
                        className="primary-button"
                        disabled={busy || quantityDraft !== null || approvalBlockers.length > 0}
                        aria-describedby={
                          approvalBlockers.length ? 'store-approval-blockers' : undefined
                        }
                        onClick={() => {
                          if (
                            window.confirm(
                              '¿Confirmar el pedido y descontar sus artículos del stock?',
                            )
                          )
                            void action('/confirm')
                        }}
                      >
                        {busy ? 'Procesando.' : 'Confirmar pedido'}
                      </button>
                    ) : (
                      <button
                        className="primary-button"
                        disabled={busy || !payment || !order.cash_open}
                        onClick={() => {
                          if (window.confirm('¿Registrar el pago en la caja de hoy?'))
                            void action('/pay', { paymentMethod: payment })
                        }}
                      >
                        {busy ? 'Procesando.' : 'Registrar pago'}
                      </button>
                    )}
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
