import { useEffect, useRef, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { currency, textValue, numberValue, todayLocal, dateDisplayToIso } from '../lib/format'
import { type ApiRow } from '../hooks/useApiRows'
import { LookupField } from '../components/LookupField'
import { DateTextInput } from '../components/Forms'
import { ApiTable } from '../components/ApiTable'

export function CashStatusNotice({ onOpenCash }: { onOpenCash: () => void }) {
  const [status, setStatus] = useState<ApiRow | null>(null)
  useEffect(() => {
    apiFetch(`/api/v1/demo/cash/status?date=${todayLocal()}`)
      .then((response) => {
        if (!response.ok) throw new Error('No se pudo consultar la caja')
        return response.json() as Promise<ApiRow>
      })
      .then(setStatus)
      .catch(() => setStatus(null))
  }, [])
  if (!status || status.estado === 'ABIERTA') return null
  return (
    <div className="cash-notice">
      <span>
        La caja de hoy está {status.estado === 'CERRADA' ? 'cerrada' : 'sin abrir'}. Abrila para
        poder registrar ventas cobradas.
      </span>
      <button type="button" onClick={onOpenCash}>
        Ir a Caja
      </button>
    </div>
  )
}

export function SalesPage({
  onOpenCash,
  canDiscount = false,
}: {
  onOpenCash: () => void
  canDiscount?: boolean
}) {
  const [selectedArticle, setSelectedArticle] = useState<ApiRow | null>(null)
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [cancelingSaleId, setCancelingSaleId] = useState<number | null>(null)
  const [lines, setLines] = useState<
    {
      articleId: number
      label: string
      quantity: number
      unitPrice: number
      discountPercent: number
    }[]
  >([])
  const [searchVersion, setSearchVersion] = useState(0)
  const [quantity, setQuantity] = useState('1')
  const [discountPercent, setDiscountPercent] = useState('0')
  const saleFormRef = useRef<HTMLFormElement>(null)
  useEffect(() => {
    if (!open) return
    const onShortcut = (event: KeyboardEvent) => {
      if (event.ctrlKey && event.key.toLowerCase() === 'g') {
        event.preventDefault()
        if (!busy) saleFormRef.current?.requestSubmit()
      }
    }
    window.addEventListener('keydown', onShortcut)
    return () => window.removeEventListener('keydown', onShortcut)
  }, [open, busy])
  const addLine = (form: HTMLFormElement) => {
    const articleId = Number(
      form.querySelector<HTMLInputElement>('input[name="articleId"]')?.value ?? 0,
    )
    const quantityValue = Number(quantity)
    const discountValue = Number(discountPercent)
    const article = selectedArticle
    if (!article || numberValue(article.id) !== articleId) {
      setMessage('Buscá y elegí un artículo del listado.')
      return
    }
    if (!(quantityValue > 0) || discountValue < 0 || discountValue > 100) {
      setMessage('Ingresá una cantidad mayor a cero y un descuento entre 0 y 100%.')
      return
    }
    const alreadyAdded = lines
      .filter((line) => line.articleId === articleId)
      .reduce((sum, line) => sum + line.quantity, 0)
    if (alreadyAdded + quantityValue > numberValue(article.stock)) {
      setMessage('La cantidad supera el stock disponible de ' + textValue(article.nombre) + '.')
      return
    }
    setLines((current) => [
      ...current,
      {
        articleId,
        label: textValue(article.codigo) + ' · ' + textValue(article.nombre),
        quantity: quantityValue,
        unitPrice: numberValue(article.precio_venta),
        discountPercent: discountValue,
      },
    ])
    setSelectedArticle(null)
    setSearchVersion((current) => current + 1)
    setQuantity('1')
    setDiscountPercent('0')
    setMessage('Artículo ingresado a la venta. Podés agregar otro.')
  }
  const submitSale = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (lines.length === 0) {
      setMessage('Ingresá al menos un artículo antes de guardar la venta.')
      return
    }
    const formElement = event.currentTarget
    const values = new FormData(formElement)
    const date = dateDisplayToIso(values.get('date'))
    if (!date) {
      setMessage('Ingresá una fecha válida con el formato dd/mm/aaaa.')
      return
    }
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch('/api/v1/operations/sales', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          customerId: Number(values.get('customerId')),
          date,
          saleType: values.get('saleType'),
          paymentMethod: values.get('paymentMethod'),
          lines: lines.map(({ articleId, quantity, discountPercent }) => ({
            articleId,
            quantity,
            discountPercent,
          })),
        }),
      })
      const result = (await response.json().catch(() => ({}))) as { error?: string }
      if (!response.ok) throw new Error(result.error ?? 'No se pudo guardar la venta.')
      window.dispatchEvent(new Event('alon:refresh'))
      setMessage('Venta guardada correctamente con ' + lines.length + ' artículos.')
      setLines([])
      setSearchVersion((current) => current + 1)
      formElement.reset()
      setOpen(false)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.')
    } finally {
      setBusy(false)
    }
  }
  const cancelSale = async (row: ApiRow) => {
    const saleId = numberValue(row.id)
    if (!saleId || textValue(row.estado) !== 'CONFIRMADA') return
    if (
      !window.confirm(
        `¿Cancelar la venta ${textValue(row.numero)}? Se revertirá el cobro y se devolverán sus artículos al stock.`,
      )
    )
      return
    setCancelingSaleId(saleId)
    setMessage('')
    try {
      const response = await apiFetch(`/api/v1/operations/sales/${saleId}/cancel`, {
        method: 'POST',
      })
      const result = (await response.json().catch(() => ({}))) as {
        error?: string
        message?: string
      }
      if (!response.ok)
        throw new Error(result.error ?? result.message ?? 'No se pudo cancelar la venta.')
      window.dispatchEvent(new Event('alon:refresh'))
      setMessage(
        `Venta ${textValue(row.numero)} cancelada. Se revirtió el cobro y se devolvieron los artículos al stock.`,
      )
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.')
    } finally {
      setCancelingSaleId(null)
    }
  }
  const saleTotal = lines.reduce(
    (sum, line) =>
      sum +
      Math.round(line.quantity * line.unitPrice * (1 - line.discountPercent / 100) * 100) / 100,
    0,
  )
  return (
    <>
      <CashStatusNotice onOpenCash={onOpenCash} />
      <section className="entry-panel sale-entry">
        <div className="entry-heading">
          <div>
            <span className="demo-tag">GESTIÓN DEL COMERCIO</span>
            <strong>Registrar una venta</strong>
          </div>
          <button
            className="primary-button"
            type="button"
            onClick={() => {
              setOpen((value) => !value)
              setMessage('')
            }}
          >
            {open ? 'Cerrar' : '＋ Nueva venta'}
          </button>
        </div>
        {open && (
          <form className="entry-form" ref={saleFormRef} onSubmit={submitSale}>
            <label>
              Cliente
              <LookupField
                name="customerId"
                label="Cliente"
                endpoint="/api/v1/demo/lookups/customers"
              />
            </label>
            <label>
              Fecha
              <DateTextInput name="date" />
            </label>
            <label>
              Tipo de venta
              <select name="saleType" defaultValue="CONTADO">
                <option value="CONTADO">Contado</option>
                <option value="CUENTA_CORRIENTE">Cuenta corriente</option>
              </select>
            </label>
            <label>
              Medio de pago
              <select name="paymentMethod" defaultValue="EFECTIVO">
                <option value="EFECTIVO">Efectivo</option>
                <option value="TRANSFERENCIA">Transferencia</option>
                <option value="MERCADO_PAGO">Mercado Pago</option>
                <option value="CUENTA_CORRIENTE">Cuenta corriente</option>
              </select>
            </label>
            <label>
              Artículo
              <LookupField
                key={searchVersion}
                name="articleId"
                label="Artículo"
                endpoint="/api/v1/demo/articles"
                required={false}
                autoFocus
                onSelect={setSelectedArticle}
              />
            </label>
            <label>
              Cantidad
              <input
                type="number"
                min="0.001"
                step="0.001"
                required
                value={quantity}
                onChange={(event) => setQuantity(event.target.value)}
              />
            </label>
            <label>
              Descuento %
              <input
                type="number"
                min="0"
                max="100"
                step="0.01"
                required
                disabled={!canDiscount}
                value={discountPercent}
                onChange={(event) => setDiscountPercent(event.target.value)}
              />
            </label>
            <button
              className="primary-button purchase-add-button"
              type="button"
              onClick={(event) => {
                const form = event.currentTarget.form
                if (form) addLine(form)
              }}
            >
              Ingresar
            </button>
            {lines.length > 0 && (
              <div className="purchase-lines">
                <strong>Artículos de esta venta · {lines.length}</strong>
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>ARTÍCULO</th>
                        <th>CANTIDAD</th>
                        <th>PRECIO UNITARIO</th>
                        <th>DESCUENTO</th>
                        <th>SUBTOTAL</th>
                        <th></th>
                      </tr>
                    </thead>
                    <tbody>
                      {lines.map((line, index) => (
                        <tr key={index}>
                          <td data-label="Artículo">{line.label}</td>
                          <td data-label="Cantidad">{line.quantity}</td>
                          <td data-label="Precio unitario">{currency.format(line.unitPrice)}</td>
                          <td data-label="Descuento">{line.discountPercent}%</td>
                          <td data-label="Subtotal">
                            {currency.format(
                              line.quantity * line.unitPrice * (1 - line.discountPercent / 100),
                            )}
                          </td>
                          <td data-label="Acción">
                            <button
                              type="button"
                              className="purchase-remove-button"
                              aria-label="Quitar artículo"
                              onClick={() =>
                                setLines((current) =>
                                  current.filter((_, lineIndex) => lineIndex !== index),
                                )
                              }
                            >
                              Quitar
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
            {lines.length > 0 && (
              <div className="purchase-total">
                Total de la venta <strong>{currency.format(saleTotal)}</strong>
              </div>
            )}
            <div className="entry-actions">
              <span>Ingresá todos los artículos y guardá la venta completa al finalizar.</span>
              <button
                className="primary-button"
                type="submit"
                disabled={busy || lines.length === 0}
              >
                {busy ? 'Guardando…' : 'Guardar venta'}
              </button>
            </div>
          </form>
        )}
        {message && (
          <p className="entry-message" role="status">
            {message}
          </p>
        )}
      </section>
      <ApiTable
        title="Ventas de hoy"
        endpoint={'/api/v1/demo/sales?date=' + todayLocal() + '&size=200'}
        columns={[
          { key: 'numero', label: 'NÚMERO' },
          { key: 'fecha_hora', label: 'FECHA Y HORA', dateTime: true },
          { key: 'cliente', label: 'CLIENTE' },
          { key: 'tipo_venta', label: 'TIPO' },
          { key: 'medio_pago', label: 'MEDIO DE PAGO' },
          { key: 'total', label: 'TOTAL', money: true },
          {
            key: 'estado',
            label: 'ESTADO',
            render: (row) =>
              textValue(row.estado) === 'CANCELADA' ? (
                <span className="status-pill sale-cancelled">Cancelada</span>
              ) : (
                <span className="status-pill sale-confirmed">Confirmada</span>
              ),
          },
          {
            key: 'acciones',
            label: 'ACCIONES',
            render: (row) =>
              textValue(row.estado) === 'CANCELADA' ? (
                <span className="muted-cell">—</span>
              ) : (
                <button
                  type="button"
                  className="table-action-button cancel-sale-button"
                  disabled={cancelingSaleId === numberValue(row.id)}
                  onClick={() => void cancelSale(row)}
                  aria-label={`Cancelar venta ${textValue(row.numero)}`}
                >
                  {cancelingSaleId === numberValue(row.id) ? 'Cancelando…' : 'Cancelar'}
                </button>
              ),
          },
        ]}
        note="Se muestran únicamente las ventas registradas hoy. Las canceladas quedan identificadas; se revierte el cobro y se devuelve el stock."
      />
    </>
  )
}
