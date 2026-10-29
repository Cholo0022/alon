import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import {
  currency,
  textValue,
  numberValue,
  formatDateDisplay,
  todayLocal,
  dateDisplayToIso,
} from '../lib/format'
import { type ApiRow } from '../hooks/useApiRows'
import { LookupField } from '../components/LookupField'
import { ApiTable } from '../components/ApiTable'

export function PurchasesPage() {
  const [selectedPurchase, setSelectedPurchase] = useState<number | null>(null)
  const [selectedArticle, setSelectedArticle] = useState<ApiRow | null>(null)
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [lines, setLines] = useState<
    { articleId: number; label: string; quantity: number; unitCost: number }[]
  >([])
  const [searchVersion, setSearchVersion] = useState(0)
  const [quantity, setQuantity] = useState('1')
  const [unitCost, setUnitCost] = useState('0')
  const [extraCosts, setExtraCosts] = useState('0')
  const [paymentType, setPaymentType] = useState<'CONTADO' | 'CUENTA_CORRIENTE'>('CONTADO')
  const purchaseFormRef = useRef<HTMLFormElement>(null)
  useEffect(() => {
    if (!open) return
    const onShortcut = (event: KeyboardEvent) => {
      if (event.ctrlKey && event.key.toLowerCase() === 'g') {
        event.preventDefault()
        if (!busy) purchaseFormRef.current?.requestSubmit()
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
    const unitCostValue = Number(unitCost)
    const article = selectedArticle
    if (!article || numberValue(article.id) !== articleId) {
      setMessage('Buscá y elegí un artículo del listado.')
      return
    }
    if (!(quantityValue > 0) || unitCostValue < 0) {
      setMessage('Ingresá una cantidad mayor a cero y un costo válido.')
      return
    }
    setLines((current) => [
      ...current,
      {
        articleId,
        label: textValue(article.codigo) + ' · ' + textValue(article.nombre),
        quantity: quantityValue,
        unitCost: unitCostValue,
      },
    ])
    setSelectedArticle(null)
    setSearchVersion((current) => current + 1)
    setQuantity('1')
    setUnitCost('0')
    setMessage('Artículo ingresado a la factura. Podés agregar otro.')
  }
  const submitPurchase = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (lines.length === 0) {
      setMessage('Ingresá al menos un artículo antes de guardar.')
      return
    }
    const formElement = event.currentTarget
    const values = new FormData(formElement)
    const parsedDate = dateDisplayToIso(values.get('date'))
    if (!parsedDate) {
      setMessage('Ingresá una fecha válida con el formato dd/mm/aaaa.')
      return
    }
    const extraCosts = Number(values.get('extraCosts') ?? 0)
    const subtotal = lines.reduce((sum, line) => sum + line.quantity * line.unitCost, 0)
    const paid = paymentType === 'CONTADO' ? subtotal + extraCosts : Number(values.get('paid') ?? 0)
    if (paid > subtotal + extraCosts) {
      setMessage('El importe pagado no puede superar el total de la factura.')
      return
    }
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch('/api/v1/operations/purchases', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          supplierId: Number(values.get('supplierId')),
          documentType: values.get('documentType'),
          documentNumber: values.get('documentNumber'),
          date: parsedDate,
          lines: lines.map(({ articleId, quantity, unitCost }) => ({
            articleId,
            quantity,
            unitCost,
          })),
          extraCosts,
          paid,
          paymentType,
          paymentMethod: values.get('paymentMethod'),
        }),
      })
      const result = (await response.json().catch(() => ({}))) as { error?: string }
      if (!response.ok) throw new Error(result.error ?? 'No se pudo guardar la factura.')
      window.dispatchEvent(new Event('alon:refresh'))
      setMessage('Factura guardada correctamente con ' + lines.length + ' artículos.')
      setLines([])
      setExtraCosts('0')
      formElement.reset()
      setSearchVersion((current) => current + 1)
      setOpen(false)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.')
    } finally {
      setBusy(false)
    }
  }
  const invoiceTotal =
    lines.reduce((sum, line) => sum + line.quantity * line.unitCost, 0) + Number(extraCosts)
  if (selectedPurchase !== null)
    return (
      <PurchaseReceipt purchaseId={selectedPurchase} onBack={() => setSelectedPurchase(null)} />
    )
  return (
    <>
      <section className="entry-panel purchase-entry">
        <div className="entry-heading">
          <div>
            <span className="demo-tag">GESTIÓN DEL COMERCIO</span>
            <strong>Cargar factura o remito de proveedor</strong>
          </div>
          <button
            className="primary-button"
            type="button"
            onClick={() => {
              setOpen((value) => {
                if (!value) setPaymentType('CONTADO')
                return !value
              })
              setMessage('')
            }}
          >
            {open ? 'Cerrar' : '＋ Cargar factura'}
          </button>
        </div>
        {open && (
          <form className="entry-form" ref={purchaseFormRef} onSubmit={submitPurchase}>
            <label>
              Proveedor
              <LookupField
                name="supplierId"
                label="Proveedor"
                endpoint="/api/v1/demo/lookups/suppliers"
              />
            </label>
            <label>
              Tipo de comprobante
              <select name="documentType" required defaultValue="">
                <option value="" disabled>
                  Seleccionar…
                </option>
                <option value="FACTURA">Factura</option>
                <option value="REMITO">Remito</option>
              </select>
            </label>
            <label>
              Número de comprobante
              <input name="documentNumber" required />
            </label>
            <label>
              Fecha
              <input
                name="date"
                type="text"
                inputMode="numeric"
                placeholder="dd/mm/aaaa"
                pattern="[0-9]{2}/[0-9]{2}/[0-9]{4}"
                maxLength={10}
                title="Usá el formato dd/mm/aaaa"
                required
                defaultValue={formatDateDisplay(todayLocal())}
              />
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
              Costo unitario
              <input
                type="number"
                min="0"
                step="0.01"
                required
                value={unitCost}
                onChange={(event) => setUnitCost(event.target.value)}
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
                <strong>Artículos de esta factura · {lines.length}</strong>
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>ARTÍCULO</th>
                        <th>CANTIDAD</th>
                        <th>COSTO UNITARIO</th>
                        <th>SUBTOTAL</th>
                        <th></th>
                      </tr>
                    </thead>
                    <tbody>
                      {lines.map((line, index) => (
                        <tr key={index}>
                          <td data-label="Artículo">{line.label}</td>
                          <td data-label="Cantidad">{line.quantity}</td>
                          <td data-label="Costo unitario">{currency.format(line.unitCost)}</td>
                          <td data-label="Subtotal">
                            {currency.format(line.quantity * line.unitCost)}
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
            <label>
              Gastos adicionales
              <input
                name="extraCosts"
                type="number"
                min="0"
                step="0.01"
                value={extraCosts}
                onChange={(event) => setExtraCosts(event.target.value)}
              />
            </label>
            <label>
              Medio de pago
              <select name="paymentMethod" defaultValue="EFECTIVO">
                <option value="EFECTIVO">Efectivo</option>
                <option value="TRANSFERENCIA">Transferencia</option>
                <option value="MERCADO_PAGO">Mercado Pago</option>
              </select>
            </label>
            <fieldset className="purchase-payment-type">
              <legend>Condición de pago</legend>
              <label className="entry-checkbox-label">
                <input
                  type="checkbox"
                  name="paymentType"
                  value="CONTADO"
                  checked={paymentType === 'CONTADO'}
                  onChange={() => setPaymentType('CONTADO')}
                />
                <span>Contado</span>
              </label>
              <label className="entry-checkbox-label">
                <input
                  type="checkbox"
                  name="paymentType"
                  value="CUENTA_CORRIENTE"
                  checked={paymentType === 'CUENTA_CORRIENTE'}
                  onChange={() => setPaymentType('CUENTA_CORRIENTE')}
                />
                <span>Cuenta corriente</span>
              </label>
            </fieldset>
            {paymentType === 'CUENTA_CORRIENTE' && (
              <label>
                Importe pagado
                <input name="paid" type="number" min="0" step="0.01" defaultValue="0" />
              </label>
            )}
            {lines.length > 0 && (
              <div className="purchase-total">
                Total estimado de la factura <strong>{currency.format(invoiceTotal)}</strong>
              </div>
            )}
            <div className="entry-actions">
              <span>Ingresá cada artículo y guardá la factura completa al finalizar.</span>
              <button
                className="primary-button"
                type="submit"
                disabled={busy || lines.length === 0}
              >
                {busy ? 'Guardando…' : 'Guardar factura'}
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
        className="purchases-table"
        title="Compras registradas"
        endpoint="/api/v1/demo/purchases"
        columns={[
          {
            key: 'numero_documento',
            label: 'DOCUMENTO',
            className: 'document-column',
            render: (row) => (
              <span
                className="document-number"
                title={textValue(row.numero_documento)}
                tabIndex={0}
              >
                {textValue(row.numero_documento)}
              </span>
            ),
          },
          { key: 'tipo_documento', label: 'TIPO' },
          { key: 'fecha', label: 'FECHA', date: true },
          { key: 'proveedor', label: 'PROVEEDOR' },
          { key: 'articulos', label: 'ARTÍCULOS' },
          { key: 'subtotal', label: 'SUBTOTAL', money: true },
          { key: 'gastos_adicionales', label: 'GASTOS EXTRA', money: true },
          { key: 'total', label: 'TOTAL', money: true },
          { key: 'pagado', label: 'PAGADO', money: true },
          { key: 'saldo', label: 'SALDO', money: true },
        ]}
        onRowClick={(row) => setSelectedPurchase(numberValue(row.id))}
        note="Hacé clic en una compra para abrir su comprobante y modificarlo."
      />
    </>
  )
}

export function PurchaseReceipt({
  purchaseId,
  onBack,
}: {
  purchaseId: number
  onBack: () => void
}) {
  const [purchase, setPurchase] = useState<ApiRow | null>(null)
  const [lines, setLines] = useState<
    { articleId: number; code: string; name: string; quantity: string; unitCost: string }[]
  >([])
  const [editing, setEditing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const loadPurchase = useCallback(
    () =>
      apiFetch(`/api/v1/demo/purchases/${purchaseId}`)
        .then((response) => {
          if (!response.ok) throw new Error('No se pudo cargar el comprobante.')
          return response.json() as Promise<ApiRow>
        })
        .then((data) => {
          setPurchase(data)
          const detail = Array.isArray(data.lineas) ? (data.lineas as ApiRow[]) : []
          setLines(
            detail.map((line) => ({
              articleId: numberValue(line.articulo_id),
              code: textValue(line.codigo),
              name: textValue(line.articulo),
              quantity: String(line.cantidad),
              unitCost: String(line.costo_unitario),
            })),
          )
        })
        .catch((error) =>
          setMessage(error instanceof Error ? error.message : 'Error al cargar el comprobante.'),
        ),
    [purchaseId],
  )
  useEffect(() => {
    void loadPurchase()
  }, [loadPurchase])
  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!purchase) return
    const values = new FormData(event.currentTarget)
    const date = dateDisplayToIso(values.get('date'))
    if (!date || lines.length === 0) {
      setMessage(
        !date
          ? 'Ingresá una fecha válida dd/mm/aaaa.'
          : 'El comprobante debe tener al menos un artículo.',
      )
      return
    }
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch(`/api/v1/operations/purchases/${purchaseId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          supplierId: numberValue(purchase.proveedor_id),
          documentType: values.get('documentType'),
          documentNumber: values.get('documentNumber'),
          date,
          lines: lines.map((line) => ({
            articleId: line.articleId,
            quantity: Number(line.quantity),
            unitCost: Number(line.unitCost),
          })),
          extraCosts: Number(values.get('extraCosts') ?? 0),
          paid: numberValue(purchase.pagado),
          paymentType: 'CUENTA_CORRIENTE',
          paymentMethod: 'EFECTIVO',
        }),
      })
      const result = (await response.json().catch(() => ({}))) as {
        error?: string
        message?: string
      }
      if (!response.ok)
        throw new Error(result.error ?? result.message ?? 'No se pudo guardar el comprobante.')
      await loadPurchase()
      setEditing(false)
      setMessage('Comprobante modificado correctamente.')
      window.dispatchEvent(new Event('alon:refresh'))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.')
    } finally {
      setBusy(false)
    }
  }
  if (!purchase)
    return (
      <section className="entry-panel">
        <button className="secondary-button" type="button" onClick={onBack}>
          ← Volver al listado
        </button>
        <p>{message || 'Cargando comprobante…'}</p>
      </section>
    )
  const displayLines = lines
  return (
    <section className="entry-panel purchase-receipt-view">
      <div className="entry-heading">
        <div>
          <span className="demo-tag">COMPROBANTE DE COMPRA</span>
          <strong>
            {textValue(purchase.tipo_documento)} {textValue(purchase.numero_documento)}
          </strong>
          <small>
            {textValue(purchase.proveedor)} · {formatDateDisplay(purchase.fecha)}
          </small>
        </div>
        <button className="secondary-button" type="button" onClick={onBack}>
          ← Volver al listado
        </button>
      </div>
      {editing ? (
        <form className="entry-form" onSubmit={save}>
          <label>
            Tipo de comprobante
            <select name="documentType" defaultValue={textValue(purchase.tipo_documento)}>
              <option value="FACTURA">Factura</option>
              <option value="REMITO">Remito</option>
            </select>
          </label>
          <label>
            Número de comprobante
            <input
              name="documentNumber"
              required
              defaultValue={textValue(purchase.numero_documento)}
            />
          </label>
          <label>
            Fecha
            <input
              name="date"
              type="text"
              inputMode="numeric"
              placeholder="dd/mm/aaaa"
              pattern="[0-9]{2}/[0-9]{2}/[0-9]{4}"
              maxLength={10}
              required
              defaultValue={formatDateDisplay(purchase.fecha)}
            />
          </label>
          <div className="purchase-lines">
            <strong>Artículos</strong>
            {displayLines.map((line, index) => (
              <div className="purchase-edit-line" key={`${line.articleId}-${index}`}>
                <span>
                  {line.code} · {line.name}
                </span>
                <label>
                  Cantidad
                  <input
                    type="number"
                    min="0.001"
                    step="0.001"
                    value={line.quantity}
                    onChange={(event) =>
                      setLines((current) =>
                        current.map((item, i) =>
                          i === index ? { ...item, quantity: event.target.value } : item,
                        ),
                      )
                    }
                  />
                </label>
                <label>
                  Costo unitario
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={line.unitCost}
                    onChange={(event) =>
                      setLines((current) =>
                        current.map((item, i) =>
                          i === index ? { ...item, unitCost: event.target.value } : item,
                        ),
                      )
                    }
                  />
                </label>
                <button
                  type="button"
                  className="purchase-remove-button"
                  onClick={() => setLines((current) => current.filter((_, i) => i !== index))}
                >
                  Quitar
                </button>
              </div>
            ))}
            <label>
              Agregar artículo
              <LookupField
                name="addArticleId"
                label="Agregar artículo"
                endpoint="/api/v1/demo/articles"
                required={false}
                onSelect={(article) => {
                  if (article)
                    setLines((current) => [
                      ...current,
                      {
                        articleId: numberValue(article.id),
                        code: textValue(article.codigo),
                        name: textValue(article.nombre),
                        quantity: '1',
                        unitCost: '0',
                      },
                    ])
                }}
              />
            </label>
          </div>
          <label>
            Gastos adicionales
            <input
              name="extraCosts"
              type="number"
              min="0"
              step="0.01"
              defaultValue={String(purchase.gastos_adicionales ?? 0)}
            />
          </label>
          <div className="entry-actions">
            <button
              className="secondary-button"
              type="button"
              onClick={() => {
                setEditing(false)
                void loadPurchase()
              }}
            >
              Cancelar
            </button>
            <button className="primary-button" type="submit" disabled={busy}>
              {busy ? 'Guardando…' : 'Guardar cambios'}
            </button>
          </div>
        </form>
      ) : (
        <>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>ARTÍCULO</th>
                  <th>CANTIDAD</th>
                  <th>COSTO UNITARIO</th>
                  <th>SUBTOTAL</th>
                </tr>
              </thead>
              <tbody>
                {displayLines.map((line, index) => (
                  <tr key={`${line.articleId}-${index}`}>
                    <td data-label="Artículo">
                      {line.code} · {line.name}
                    </td>
                    <td data-label="Cantidad">{line.quantity}</td>
                    <td data-label="Costo unitario">{currency.format(Number(line.unitCost))}</td>
                    <td data-label="Subtotal">
                      {currency.format(Number(line.quantity) * Number(line.unitCost))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="purchase-total">
            Subtotal <strong>{currency.format(numberValue(purchase.subtotal))}</strong>
          </div>
          <div className="purchase-total">
            Gastos adicionales{' '}
            <strong>{currency.format(numberValue(purchase.gastos_adicionales))}</strong>
          </div>
          <div className="purchase-total">
            Total <strong>{currency.format(numberValue(purchase.total))}</strong>
          </div>
          <div className="purchase-total">
            Pagado / saldo{' '}
            <strong>
              {currency.format(numberValue(purchase.pagado))} /{' '}
              {currency.format(numberValue(purchase.saldo))}
            </strong>
          </div>
          <div className="entry-actions">
            <span>La factura conserva sus pagos registrados.</span>
            <button
              className="primary-button"
              type="button"
              onClick={() => {
                setEditing(true)
                setMessage('')
              }}
            >
              Modificar comprobante
            </button>
          </div>
        </>
      )}
      {message && (
        <p className="entry-message" role="status">
          {message}
        </p>
      )}
    </section>
  )
}
