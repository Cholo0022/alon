import { PaymentConfirmation, type PaymentSelection } from '../components/PaymentConfirmation'
import { useTransactionEntry } from '../hooks/useTransactionEntry'
// Reúne los artículos de la venta y la guarda completa cuando el usuario confirma.
import { QuantityInput } from '../components/QuantityInput'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { currency, textValue, numberValue, todayLocal, dateDisplayToIso } from '../lib/format'
import { type ApiRow } from '../hooks/useApiRows'
import { LookupField } from '../components/LookupField'
import { DateTextInput } from '../components/Forms'
import { ApiTable } from '../components/ApiTable'
import { ArticlePicker } from '../components/ArticlePicker'
import { ArticleEntryDialog, type ArticleEntryValues } from '../components/ArticleEntryDialog'

/** Informa si la fecha de venta tiene caja abierta y ofrece ir a Caja cuando hace falta abrirla. */
export function CashStatusNotice({ onOpenCash }: { onOpenCash: () => void }) {
  const [status, setStatus] = useState<ApiRow | null>(null)
  useEffect(() => {
    apiFetch(`/api/v1/alon/cash/status?date=${todayLocal()}`)
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

/** Gestiona el borrador de venta, sus artículos y descuentos, la confirmación de cobro y el historial con anulaciones. */
export function SalesPage({
  onOpenCash,
  canDiscount = false,
}: {
  onOpenCash: () => void
  canDiscount?: boolean
}) {
  const [paymentOpen, setPaymentOpen] = useState(false)
  const [paymentPartyName, setPaymentPartyName] = useState('')
  const [pendingArticle, setPendingArticle] = useState<ApiRow | null>(null)
  const [articlePickerOpen, setArticlePickerOpen] = useState(false)
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
  useTransactionEntry(saleFormRef, open, searchVersion)
  useEffect(() => {
    if (!open) return
    /** Abre la confirmación de venta con Ctrl+G si no hay una operación o diálogo en curso. */
    const onShortcut = (event: KeyboardEvent) => {
      if (event.ctrlKey && event.key.toLowerCase() === 'g') {
        event.preventDefault()
        if (!busy && !articlePickerOpen && !paymentOpen) saleFormRef.current?.requestSubmit()
      }
    }
    window.addEventListener('keydown', onShortcut)
    return () => window.removeEventListener('keydown', onShortcut)
  }, [open, busy, articlePickerOpen, paymentOpen])
  // Agrega el artículo a la venta en pantalla; todavía no guarda ni descuenta stock.
  /** Valida artículo, cantidad, descuento y stock disponible y agrega el renglón al borrador de venta. */
  const addLine = (
    form: HTMLFormElement,
    pickedArticle?: ApiRow,
    entryValues?: ArticleEntryValues,
  ) => {
    const articleId = pickedArticle
      ? numberValue(pickedArticle.id)
      : Number(form.querySelector<HTMLInputElement>('input[name="articleId"]')?.value ?? 0)
    const quantityValue = entryValues?.quantity ?? Number(quantity)
    const discountValue = Number(discountPercent)
    const article = pickedArticle ?? selectedArticle
    if (!article || numberValue(article.id) !== articleId) {
      setMessage('Buscá y elegí un artículo del listado.')
      return false
    }
    if (!(quantityValue > 0) || discountValue < 0 || discountValue > 100) {
      setMessage('Ingresá una cantidad mayor a cero y un descuento entre 0 y 100%.')
      return false
    }
    const alreadyAdded = lines
      .filter((line) => line.articleId === articleId)
      .reduce((sum, line) => sum + line.quantity, 0)
    if (
      Math.round((alreadyAdded + quantityValue) * 1000) >
      Math.round(numberValue(article.stock) * 1000)
    ) {
      setMessage('La cantidad supera el stock disponible de ' + textValue(article.nombre) + '.')
      return false
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
    return true
  }
  // Envía todos los renglones juntos cuando el usuario elige guardar la venta.
  /** Comprueba artículos y fecha y abre el diálogo para elegir tipo de venta y medio de pago antes de guardar. */
  const submitSale = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy || articlePickerOpen || paymentOpen) return
    if (!lines.length) {
      setMessage('Ingresá al menos un artículo antes de guardar.')
      return
    }
    const date = dateDisplayToIso(new FormData(event.currentTarget).get('date'))
    if (!date) {
      setMessage('Ingresá una fecha válida con el formato dd/mm/aaaa.')
      return
    }
    setPaymentPartyName(
      event.currentTarget.querySelector<HTMLInputElement>('[role="combobox"][aria-label="Cliente"]')
        ?.value ?? '',
    )
    setMessage('')
    setPaymentOpen(true)
  }
  /** Envía la venta con la condición elegida y vacía el borrador únicamente cuando el servidor confirma su registro. */
  const confirmSale = async (selection: PaymentSelection) => {
    const formElement = saleFormRef.current
    if (busy || !formElement) return
    const values = new FormData(formElement)
    const date = dateDisplayToIso(values.get('date'))
    if (!date) {
      setMessage('Ingresá una fecha válida.')
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
          saleType: selection.condition,
          paymentMethod: selection.paymentMethod,
          lines: lines.map(({ articleId, quantity, discountPercent }) => ({
            articleId,
            quantity,
            discountPercent,
          })),
        }),
      })
      const result = (await response.json().catch(() => ({}))) as { error?: string }
      if (!response.ok) throw new Error(result.error ?? 'No se pudo guardar la venta.')
      setPaymentOpen(false)
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
  /** Pide confirmación de anulación, solicita el reverso de venta y actualiza stock, caja y listados al completarse. */
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
      {paymentOpen && (
        <PaymentConfirmation
          mode="sale"
          total={saleTotal}
          partyName={paymentPartyName}
          busy={busy}
          message={message}
          onChange={() => setMessage('')}
          onCancel={() => {
            if (!busy) {
              setPaymentOpen(false)
              setMessage('')
              saleFormRef.current
                ?.querySelector<HTMLButtonElement>('button[type="submit"]')
                ?.focus()
            }
          }}
          onConfirm={(selection) => void confirmSale(selection)}
        />
      )}
      {articlePickerOpen && (
        <ArticlePicker
          message={message}
          suspended={!!pendingArticle}
          onClose={() => {
            setArticlePickerOpen(false)
            saleFormRef.current
              ?.querySelector<HTMLInputElement>('[role="combobox"][aria-label="Artículo"]')
              ?.focus()
          }}
          onChoose={(article) => {
            setMessage('')
            setPendingArticle(article)
          }}
        />
      )}
      {pendingArticle && (
        <ArticleEntryDialog
          article={pendingArticle}
          mode="sale"
          initialQuantity={quantity}
          initialPrice={numberValue(pendingArticle.precio_venta)}
          maxQuantity={Math.max(
            0,
            Math.round(
              (numberValue(pendingArticle.stock) -
                lines
                  .filter((line) => line.articleId === numberValue(pendingArticle.id))
                  .reduce((sum, line) => sum + line.quantity, 0)) *
                1000,
            ) / 1000,
          )}
          message={message}
          onCancel={() => {
            setPendingArticle(null)
            setMessage('')
          }}
          onConfirm={(values) => {
            if (saleFormRef.current && addLine(saleFormRef.current, pendingArticle, values)) {
              setPendingArticle(null)
              setArticlePickerOpen(false)
            }
          }}
        />
      )}
      <CashStatusNotice onOpenCash={onOpenCash} />
      <section className="entry-panel sale-entry">
        <div className="entry-heading">
          <div>
            <span className="alon-tag">GESTIÓN DEL COMERCIO</span>
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
            <div className="transaction-header">
              <label>
                Cliente
                <LookupField
                  name="customerId"
                  defaultName="Consumidor final"
                  label="Cliente"
                  endpoint="/api/v1/alon/lookups/customers"
                />
              </label>
              <label>
                Fecha
                <DateTextInput name="date" />
              </label>
            </div>
            <div className="transaction-article-entry">
              <label>
                Artículo
                <LookupField
                  key={searchVersion}
                  searchOnly
                  onEmptyEnter={() => {
                    if (!busy) {
                      setMessage('')
                      setArticlePickerOpen(true)
                    }
                  }}
                  name="articleId"
                  label="Artículo"
                  endpoint="/api/v1/alon/articles"
                  required={false}
                  autoFocus
                  onSelect={setSelectedArticle}
                />
              </label>
              <label>
                Cantidad
                <QuantityInput
                  min="0.001"
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
            </div>
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
        endpoint={'/api/v1/alon/sales?date=' + todayLocal() + '&size=200'}
        columns={[
          { key: 'numero', label: 'NÚMERO' },
          { key: 'fecha_hora', label: 'FECHA Y HORA', dateTime: true },
          { key: 'cliente', label: 'CLIENTE' },
          { key: 'tipo_venta', label: 'TIPO' },
          { key: 'medio_pago', label: 'MEDIO DE PAGO' },
          {
            key: 'origen',
            label: 'ORIGEN',
            render: (row) => (
              <span className="status-pill">{row.origen === 'WEB' ? 'Web' : 'Local'}</span>
            ),
          },
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
