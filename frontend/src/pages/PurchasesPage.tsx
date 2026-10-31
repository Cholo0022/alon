import { CostConfirmation } from '../components/CostConfirmation'
import { PaymentConfirmation, type PaymentSelection } from '../components/PaymentConfirmation'
import { useTransactionEntry } from '../hooks/useTransactionEntry'
// Permite cargar y corregir compras con sus artículos, cantidades, gastos y pagos.
import { QuantityInput } from '../components/QuantityInput'
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
import { ArticlePicker } from '../components/ArticlePicker'
import { ArticleEntryDialog, type ArticleEntryValues } from '../components/ArticleEntryDialog'

/** Gestiona el ingreso de compras, el proveedor predeterminado, los artículos y las confirmaciones de pago y costo, además del historial. */
export function PurchasesPage() {
  const [supplierPickerOpen, setSupplierPickerOpen] = useState(false)
  const [supplier, setSupplier] = useState<ApiRow | undefined>()
  const [supplierVersion, setSupplierVersion] = useState(0)
  const [costSelection, setCostSelection] = useState<PaymentSelection | null>(null)
  const [paymentOpen, setPaymentOpen] = useState(false)
  const [paymentPartyName, setPaymentPartyName] = useState('')
  const [pendingArticle, setPendingArticle] = useState<ApiRow | null>(null)
  const [articlePickerOpen, setArticlePickerOpen] = useState(false)
  const [costEdited, setCostEdited] = useState(false)
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
  const purchaseFormRef = useRef<HTMLFormElement>(null)
  useTransactionEntry(purchaseFormRef, open, searchVersion)
  useEffect(() => {
    if (open)
      purchaseFormRef.current?.querySelector<HTMLInputElement>('[aria-label="Proveedor"]')?.focus()
  }, [open])
  useEffect(() => {
    if (!open) return
    /** Abre la confirmación de guardado con Ctrl+G solo cuando no hay otro diálogo o solicitud en curso. */
    const onShortcut = (event: KeyboardEvent) => {
      if (event.ctrlKey && event.key.toLowerCase() === 'g') {
        event.preventDefault()
        if (!busy && !articlePickerOpen && !paymentOpen && !supplierPickerOpen && !costSelection)
          purchaseFormRef.current?.requestSubmit()
      }
    }
    window.addEventListener('keydown', onShortcut)
    return () => window.removeEventListener('keydown', onShortcut)
  }, [open, busy, articlePickerOpen, paymentOpen, supplierPickerOpen, costSelection])
  /** Valida artículo, cantidad y costo; agrega un renglón al borrador y prepara el buscador para cargar el siguiente. */
  const addLine = (
    form: HTMLFormElement,
    pickedArticle?: ApiRow,
    entryValues?: ArticleEntryValues,
  ) => {
    const articleId = pickedArticle
      ? numberValue(pickedArticle.id)
      : Number(form.querySelector<HTMLInputElement>('input[name="articleId"]')?.value ?? 0)
    const quantityValue = entryValues?.quantity ?? Number(quantity)
    const unitCostValue =
      entryValues?.unitCost ??
      (pickedArticle && !costEdited ? numberValue(pickedArticle.costo) : Number(unitCost))
    const article = pickedArticle ?? selectedArticle
    if (!article || numberValue(article.id) !== articleId) {
      setMessage('Buscá y elegí un artículo del listado.')
      return false
    }
    if (
      !Number.isFinite(quantityValue) ||
      !(quantityValue > 0) ||
      (!entryValues && costEdited && !unitCost.trim()) ||
      !Number.isFinite(unitCostValue) ||
      unitCostValue < 0
    ) {
      setMessage('Ingresá una cantidad mayor a cero y un costo válido.')
      return false
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
    setCostEdited(false)
    setMessage('Artículo ingresado a la factura. Podés agregar otro.')
    return true
  }
  // Guarda la factura o el remito completo junto con sus artículos.
  /** Valida el borrador y su fecha y abre la selección de pago sin guardar todavía la compra. */
  const submitPurchase = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy || articlePickerOpen || paymentOpen || supplierPickerOpen || costSelection) return
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
      event.currentTarget.querySelector<HTMLInputElement>(
        '[role="combobox"][aria-label="Proveedor"]',
      )?.value ?? '',
    )
    setMessage('')
    setPaymentOpen(true)
  }
  /** Envía la compra con su condición, medio de pago y decisión de costo; limpia el formulario solo si el servidor la confirma. */
  const confirmPurchase = async (selection: PaymentSelection, updateCost: boolean) => {
    const formElement = purchaseFormRef.current
    if (busy || !formElement) return
    const values = new FormData(formElement)
    const parsedDate = dateDisplayToIso(values.get('date'))
    if (!parsedDate) {
      setMessage('Ingresá una fecha válida.')
      return
    }
    const extraCosts = Number(values.get('extraCosts') ?? 0)
    const paid = selection.condition === 'CUENTA_CORRIENTE' ? 0 : invoiceTotal
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
          updateCost,
          paymentType: selection.condition,
          paymentMethod: selection.paymentMethod,
        }),
      })
      const result = (await response.json().catch(() => ({}))) as { error?: string }
      if (!response.ok) throw new Error(result.error ?? 'No se pudo guardar la factura.')
      setPaymentOpen(false)
      setCostSelection(null)
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
    lines.reduce((sum, line) => sum + Math.round(line.quantity * line.unitCost * 100) / 100, 0) +
    Number(extraCosts)
  if (selectedPurchase !== null)
    return (
      <PurchaseReceipt purchaseId={selectedPurchase} onBack={() => setSelectedPurchase(null)} />
    )
  return (
    <>
      {supplierPickerOpen && (
        <ArticlePicker
          mode="supplier"
          message=""
          onClose={() => {
            setSupplierPickerOpen(false)
            purchaseFormRef.current
              ?.querySelector<HTMLInputElement>('[aria-label="Proveedor"]')
              ?.focus()
          }}
          onChoose={(row) => {
            setSupplier(row)
            setSupplierVersion((value) => value + 1)
            setSupplierPickerOpen(false)
            purchaseFormRef.current
              ?.querySelector<HTMLInputElement>('[aria-label="Artículo"]')
              ?.focus()
          }}
        />
      )}
      {costSelection && (
        <CostConfirmation
          busy={busy}
          message={message}
          onCancel={() => {
            setCostSelection(null)
            setMessage('')
            setPaymentOpen(true)
          }}
          onConfirm={(updateCost) => void confirmPurchase(costSelection, updateCost)}
        />
      )}
      {paymentOpen && (
        <PaymentConfirmation
          mode="purchase"
          total={invoiceTotal}
          partyName={paymentPartyName}
          busy={busy}
          message={message}
          onChange={() => setMessage('')}
          onCancel={() => {
            if (!busy) {
              setPaymentOpen(false)
              setMessage('')
              purchaseFormRef.current
                ?.querySelector<HTMLButtonElement>('button[type="submit"]')
                ?.focus()
            }
          }}
          onConfirm={(selection) => {
            setPaymentOpen(false)
            setCostSelection(selection)
            setMessage('')
          }}
        />
      )}
      {articlePickerOpen && (
        <ArticlePicker
          mode="purchase"
          suspended={!!pendingArticle}
          message={message}
          unitCostOverride={costEdited ? Number(unitCost) : undefined}
          onClose={() => {
            setArticlePickerOpen(false)
            purchaseFormRef.current
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
          mode="purchase"
          initialQuantity={quantity}
          initialPrice={
            costEdited && unitCost.trim() ? Number(unitCost) : numberValue(pendingArticle.costo)
          }
          message={message}
          onCancel={() => {
            setPendingArticle(null)
            setMessage('')
          }}
          onConfirm={(values) => {
            if (
              purchaseFormRef.current &&
              addLine(purchaseFormRef.current, pendingArticle, values)
            ) {
              setPendingArticle(null)
              setArticlePickerOpen(false)
            }
          }}
        />
      )}
      <section className="entry-panel purchase-entry">
        <div className="entry-heading">
          <div>
            <span className="alon-tag">GESTIÓN DEL COMERCIO</span>
            <strong>Cargar factura o remito de proveedor</strong>
          </div>
          <button
            className="primary-button"
            type="button"
            disabled={busy}
            onClick={async () => {
              setMessage('')
              if (open) {
                setOpen(false)
                return
              }
              setBusy(true)
              try {
                const response = await apiFetch('/api/v1/operations/purchases/default-supplier', {
                  method: 'POST',
                })
                if (!response.ok)
                  throw new Error('No se pudo preparar el proveedor predeterminado.')
                setSupplier((await response.json()) as ApiRow)
                setSupplierVersion(0)
                setOpen(true)
              } catch (error) {
                setMessage(error instanceof Error ? error.message : 'No se pudo abrir la carga.')
              } finally {
                setBusy(false)
              }
            }}
          >
            {open ? 'Cerrar' : '＋ Cargar factura'}
          </button>
        </div>
        {open && (
          <form className="entry-form" ref={purchaseFormRef} onSubmit={submitPurchase}>
            <div className="transaction-header">
              <label>
                Proveedor
                <LookupField
                  searchOnly
                  key={supplierVersion}
                  initialRecord={supplier}
                  autoFocus={supplierVersion === 0}
                  onEnter={() => setSupplierPickerOpen(true)}
                  name="supplierId"
                  label="Proveedor"
                  endpoint="/api/v1/alon/lookups/suppliers"
                />
              </label>
              <label>
                Tipo de comprobante
                <select name="documentType" required defaultValue="REMITO">
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
                  autoFocus={searchVersion > 0}
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
                Costo unitario
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  required
                  value={unitCost}
                  onChange={(event) => {
                    setUnitCost(event.target.value)
                    setCostEdited(true)
                  }}
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
        endpoint="/api/v1/alon/purchases"
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
          {
            key: 'condicion_pago',
            label: 'CONDICIÓN',
            render: (row) => {
              const condition = textValue(row.condicion_pago)
              return condition === 'EFECTIVO'
                ? 'CONTADO'
                : condition === 'EFECTIVO_CAJA' || condition === 'CONTADO'
                  ? 'CONTADO DE CAJA'
                  : condition.replaceAll('_', ' ')
            },
          },
          {
            key: 'medio_pago',
            label: 'MEDIO DE PAGO',
            render: (row) => textValue(row.medio_pago).replaceAll('_', ' '),
          },
          { key: 'pagado', label: 'PAGADO', money: true },
          { key: 'saldo', label: 'SALDO', money: true },
        ]}
        onRowClick={(row) => setSelectedPurchase(numberValue(row.id))}
        note="Hacé clic en una compra para abrir su comprobante y modificarlo."
      />
    </>
  )
}

/** Carga el detalle de una compra guardada y ofrece su corrección con los renglones y totales actuales. */
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
  /** Consulta de nuevo la cabecera y los renglones del comprobante y prepara los valores del formulario de corrección. */
  const loadPurchase = useCallback(
    () =>
      apiFetch(`/api/v1/alon/purchases/${purchaseId}`)
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
  /** Valida y envía la corrección de una compra guardada, vuelve a cargar su comprobante e informa el resultado. */
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
          paymentType: textValue(purchase.condicion_pago) || 'CONTADO',
          paymentMethod: textValue(purchase.medio_pago) || 'EFECTIVO',
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
          <span className="alon-tag">COMPROBANTE DE COMPRA</span>
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
                  <QuantityInput
                    min="0.001"
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
                endpoint="/api/v1/alon/articles"
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
