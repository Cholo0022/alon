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
type Order = {
  id: number
  nombre_cliente: string
  contacto: string
  dni: string | null
  domicilio: string | null
  detalle: string
  estado: string
  total_estimado: number
  creado_en: string
  lines: OrderLine[]
  medio_pago: string | null
  venta_id: number | null
  cash_open: boolean
}
const statusLabel = (state: string) =>
  state === 'CONFIRMADO'
    ? 'Confirmado'
    : state === 'CANCELADO'
      ? 'Cancelado'
      : 'Pendiente de confirmación'

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
    void apiFetch('/api/v1/demo/store-orders/' + selected, { signal: controller.signal })
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
  const included = order?.lines.filter((l) => Boolean(l.incluido)) ?? []
  const shortage = included.some(
    (l) => Number(l.cantidad) > Number(l.stock) || l.estado_articulo !== 'ACTIVO',
  )
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
        endpoint="/api/v1/demo/store-orders"
        columns={[
          { key: 'id', label: 'NÚMERO' },
          { key: 'nombre_cliente', label: 'CLIENTE' },
          { key: 'contacto', label: 'CONTACTO' },
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
                className="secondary-button"
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
                  <p>{order.domicilio ?? 'Domicilio no informado'}</p>
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
                        if (window.confirm('¿Cancelar este pedido?')) void action('/cancel')
                      }}
                    >
                      Cancelar pedido
                    </button>
                    <button
                      className="primary-button"
                      disabled={
                        busy || shortage || !included.length || !order.cash_open || !payment
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
