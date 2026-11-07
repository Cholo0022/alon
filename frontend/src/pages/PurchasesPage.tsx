import { apparelDraftKey } from '../lib/apparelDraft'
import { PurchaseSupplierDialog } from '../components/PurchaseSupplierDialog'
import { PurchaseVariantsDialog } from '../components/PurchaseVariantsDialog'
import {
  emptyPurchaseHeader,
  purchaseDraftKey,
  readPurchaseDraft,
  validPurchaseLine,
  type PurchaseLine,
  type PurchaseDraft,
} from '../lib/purchaseDraft'
import { BarcodeEntry, NewPurchaseArticleDialog } from '../components/BarcodeEntry'
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
  dateDisplayToIso,
} from '../lib/format'
import { type ApiRow } from '../hooks/useApiRows'
import { LookupField } from '../components/LookupField'
import { ApiTable } from '../components/ApiTable'
import { ArticlePicker } from '../components/ArticlePicker'
import { ArticleEntryDialog, type ArticleEntryValues } from '../components/ArticleEntryDialog'

/** Gestiona el ingreso de compras, el proveedor predeterminado, los artículos y las confirmaciones de pago y costo, además del historial. */
export function PurchasesPage({ draftScope }: { draftScope?: string }) {
  const [recovered] = useState(() => readPurchaseDraft(draftScope))
  const [header, setHeader] = useState(recovered?.header ?? emptyPurchaseHeader)
  const [hasDraft, setHasDraft] = useState(!!recovered)
  const [draftWarning, setDraftWarning] = useState('')
  const [supplierCreateOpen, setSupplierCreateOpen] = useState(false)
  const [variantsArticle, setVariantsArticle] = useState<ApiRow | null>(null)
  const [newArticleOpen, setNewArticleOpen] = useState(false)
  const [productKind, setProductKind] = useState<'' | 'ARTICULO' | 'VESTIMENTA'>('')
  const [supplierPickerOpen, setSupplierPickerOpen] = useState(false)
  const [supplier, setSupplier] = useState<ApiRow | undefined>(recovered?.supplier)
  const [supplierVersion, setSupplierVersion] = useState(0)
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
  const [lines, setLines] = useState<PurchaseLine[]>(recovered?.lines ?? [])
  const [searchVersion, setSearchVersion] = useState(0)
  const [quantity, setQuantity] = useState('1')
  const [unitCost, setUnitCost] = useState('0')
  const [extraCosts, setExtraCosts] = useState(recovered?.extraCosts ?? '0')
  const purchaseFormRef = useRef<HTMLFormElement>(null)
  const draftCompleted = useRef(false)
  useEffect(() => {
    if (!draftScope || !open) return
    const save = () => {
      if (draftCompleted.current) return
      try {
        const draft: PurchaseDraft = { version: 1, header, supplier, lines, extraCosts }
        localStorage.setItem(purchaseDraftKey(draftScope), JSON.stringify(draft))
        setHasDraft(true)
        setDraftWarning('')
      } catch {
        setDraftWarning(
          'No se pudo guardar el borrador en este navegador. Mantené esta pantalla abierta hasta guardar el comprobante.',
        )
      }
    }
    // Sincroniza el almacenamiento después del cambio; al salir, conserva la última edición.
    const timer = window.setTimeout(save, 0)
    return () => {
      window.clearTimeout(timer)
      save()
    }
  }, [draftScope, open, header, supplier, lines, extraCosts])
  const chooseSupplier = (row: ApiRow) => {
    setSupplier(row)
    setSupplierVersion((value) => value + 1)
  }
  const chooseArticle = (article: ApiRow) => {
    setMessage('')
    if (article.tipo_producto === 'INDUMENTARIA' || article.tipo_producto === 'CALZADO') {
      setArticlePickerOpen(false)
      setVariantsArticle(article)
    } else setPendingArticle(article)
  }
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
        if (
          !busy &&
          !pendingArticle &&
          !articlePickerOpen &&
          !paymentOpen &&
          !supplierPickerOpen &&
          !variantsArticle &&
          !supplierCreateOpen &&
          !newArticleOpen
        )
          purchaseFormRef.current?.requestSubmit()
      }
    }
    window.addEventListener('keydown', onShortcut)
    return () => window.removeEventListener('keydown', onShortcut)
  }, [
    open,
    busy,
    pendingArticle,
    articlePickerOpen,
    paymentOpen,
    supplierPickerOpen,
    variantsArticle,
    supplierCreateOpen,
    newArticleOpen,
  ])
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
    if (lines.length >= 500) {
      setMessage('El comprobante admite hasta 500 renglones.')
      return false
    }
    setLines((current) => [
      ...current,
      {
        articleId,
        label: textValue(article.codigo) + ' · ' + textValue(article.nombre),
        quantity: quantityValue,
        unitCost: unitCostValue,
        wholeUnits: article.tipo_producto === 'INDUMENTARIA' || article.tipo_producto === 'CALZADO',
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
    if (
      busy ||
      pendingArticle ||
      articlePickerOpen ||
      paymentOpen ||
      supplierPickerOpen ||
      variantsArticle ||
      supplierCreateOpen ||
      newArticleOpen
    )
      return
    if (!lines.length) {
      setMessage('Ingresá al menos un artículo antes de guardar.')
      return
    }
    if (
      lines.some((line) => !validPurchaseLine(line)) ||
      !Number.isFinite(Number(extraCosts)) ||
      Number(extraCosts) < 0
    ) {
      setMessage('Revisá las cantidades, costos y gastos adicionales.')
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
  const confirmPurchase = async (
    selection: PaymentSelection,
    updateCost: boolean,
    updateStock: boolean,
  ) => {
    const formElement = purchaseFormRef.current
    if (busy || !formElement) return
    const values = new FormData(formElement)
    const parsedDate = dateDisplayToIso(values.get('date'))
    if (!parsedDate) {
      setMessage('Ingresá una fecha válida.')
      return
    }
    if (lines.some((line) => !validPurchaseLine(line))) {
      setMessage('Revisá las cantidades y costos.')
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
          updateStock,
          paymentType: selection.condition,
          paymentMethod: selection.paymentMethod,
        }),
      })
      const result = (await response.json().catch(() => ({}))) as { error?: string }
      if (!response.ok) throw new Error(result.error ?? 'No se pudo guardar la factura.')
      setPaymentOpen(false)
      window.dispatchEvent(new Event('alon:refresh'))
      setMessage('Factura guardada correctamente con ' + lines.length + ' artículos.')
      setLines([])
      setExtraCosts('0')
      setHeader(emptyPurchaseHeader())
      setSupplier(undefined)
      setHasDraft(false)
      draftCompleted.current = true
      if (draftScope) {
        try {
          localStorage.removeItem(purchaseDraftKey(draftScope))
        } catch {
          setDraftWarning(
            'La compra ya se guardó, pero no se pudo eliminar el borrador del navegador. No recuperes ese borrador para evitar repetir la carga.',
          )
        }
      }
      setSelectedArticle(null)
      setQuantity('1')
      setUnitCost('0')
      setCostEdited(false)
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
    lines.reduce(
      (sum, line) =>
        sum +
        (Number.isFinite(line.quantity * line.unitCost)
          ? Math.round(line.quantity * line.unitCost * 100) / 100
          : 0),
      0,
    ) + Number(extraCosts)
  if (selectedPurchase !== null)
    return (
      <PurchaseReceipt purchaseId={selectedPurchase} onBack={() => setSelectedPurchase(null)} />
    )
  return (
    <>
      {supplierCreateOpen && (
        <PurchaseSupplierDialog
          onClose={() => setSupplierCreateOpen(false)}
          onSaved={(row) => {
            chooseSupplier(row)
            setSupplierCreateOpen(false)
          }}
        />
      )}
      {variantsArticle && (
        <PurchaseVariantsDialog
          article={variantsArticle}
          onClose={() => setVariantsArticle(null)}
          onAdd={(added) => {
            if (lines.length + added.length > 500) {
              setMessage('El comprobante admite hasta 500 renglones.')
              setVariantsArticle(null)
              return
            }
            setLines((current) => [...current, ...added])
            setVariantsArticle(null)
            setSelectedArticle(null)
            setSearchVersion((value) => value + 1)
          }}
        />
      )}
      {newArticleOpen && (
        <NewPurchaseArticleDialog
          multipleApparel
          apparelDraftKey={draftScope ? apparelDraftKey(draftScope,'purchase-product') : undefined}
          maxVariants={Math.max(1, Math.min(100, 500 - lines.length))}
          onClose={() => setNewArticleOpen(false)}
          onSaved={(saved) => {
            setNewArticleOpen(false)
            if (Array.isArray(saved.items)) {
              const added: PurchaseLine[] = (saved.items as ApiRow[]).map((item) => ({
                articleId: Number(item.id),
                label: textValue(item.codigo) + ' · ' + textValue(item.nombre),
                quantity: Number(item.quantity),
                unitCost: Number(item.unitCost),
                wholeUnits: true,
              }))
              if (lines.length + added.length > 500) {
                setMessage('Las variantes se crearon. El comprobante admite hasta 500 renglones.')
                return
              }
              setLines((current) => [...current, ...added])
              setSearchVersion((value) => value + 1)
              setMessage('Prenda creada. Se agregaron las cantidades por talle al comprobante.')
              return
            }
            void apiFetch(
              '/api/v1/alon/articles?code=' + encodeURIComponent(textValue(saved.codigo)),
            )
              .then(async (response) => {
                if (!response.ok) throw new Error()
                const articles = (await response.json()) as ApiRow[]
                const article = articles.find(
                  (row) => numberValue(row.id) === numberValue(saved.id),
                )
                if (article) {
                  setCostEdited(false)
                  chooseArticle(article)
                } else
                  setMessage('Artículo creado. Buscalo en el listado para ingresarlo a la factura.')
              })
              .catch(() =>
                setMessage('Artículo creado. Buscalo en el listado para ingresarlo a la factura.'),
              )
          }}
        />
      )}
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
            chooseSupplier(row)
            setSupplierPickerOpen(false)
            purchaseFormRef.current
              ?.querySelector<HTMLInputElement>('[aria-label="Artículo"]')
              ?.focus()
          }}
        />
      )}
      {paymentOpen && (
        <PaymentConfirmation
          mode="purchase"
          purchaseOptions
          documentSummary={`${header.documentType} ${header.documentNumber} · ${header.date} · ${lines.length} productos`}
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
          onConfirm={(selection, choices) => {
            if (choices) void confirmPurchase(selection, choices.updateCost, choices.updateStock)
          }}
        />
      )}
      {articlePickerOpen && (
        <ArticlePicker
          mode="purchase"
          onCreate={() => {
            setArticlePickerOpen(false)
            setNewArticleOpen(true)
          }}
          kind={productKind}
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
            chooseArticle(article)
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
      {draftWarning && (
        <p role="alert" className="entry-message">
          {draftWarning}
        </p>
      )}
      {hasDraft && (
        <div className="notice">
          <span>
            {open
              ? 'Borrador guardado en este navegador.'
              : 'Tenés un comprobante sin terminar en este navegador.'}
          </span>
          {!open && (
            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                draftCompleted.current = false
                setOpen(true)
              }}
            >
              Continuar borrador
            </button>
          )}
          <button
            type="button"
            className="secondary-button"
            disabled={busy}
            onClick={() => {
              if (!window.confirm('¿Descartar el comprobante sin guardar?')) return
              try {
                if (draftScope) localStorage.removeItem(purchaseDraftKey(draftScope))
              } catch {
                setDraftWarning('No se pudo descartar el borrador.')
                return
              }
              draftCompleted.current = true
              setOpen(false)
              setLines([])
              setSupplier(undefined)
              setHeader(emptyPurchaseHeader())
              setExtraCosts('0')
              setHasDraft(false)
              setSelectedArticle(null)
              setQuantity('1')
              setUnitCost('0')
              setCostEdited(false)
              setMessage('')
              setDraftWarning('')
            }}
          >
            Descartar borrador
          </button>
        </div>
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
              draftCompleted.current = false
              if (hasDraft || supplier) {
                setOpen(true)
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
                  onSelect={(row) => setSupplier(row ?? undefined)}
                  name="supplierId"
                  label="Proveedor"
                  endpoint="/api/v1/alon/lookups/suppliers"
                />
              </label>
              <button
                type="button"
                className="secondary-button"
                onClick={() => setSupplierCreateOpen(true)}
              >
                Crear proveedor
              </button>
              <label>
                Tipo de comprobante
                <select
                  name="documentType"
                  required
                  value={header.documentType}
                  onChange={(event) =>
                    setHeader((current) => ({ ...current, documentType: event.target.value }))
                  }
                >
                  <option value="" disabled>
                    Seleccionar…
                  </option>
                  <option value="FACTURA">Factura</option>
                  <option value="REMITO">Remito</option>
                </select>
              </label>
              <label>
                Número de comprobante
                <input
                  name="documentNumber"
                  required
                  maxLength={50}
                  value={header.documentNumber}
                  onChange={(event) =>
                    setHeader((current) => ({ ...current, documentNumber: event.target.value }))
                  }
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
                  title="Usá el formato dd/mm/aaaa"
                  required
                  value={header.date}
                  onChange={(event) =>
                    setHeader((current) => ({ ...current, date: event.target.value }))
                  }
                />
              </label>
            </div>
            <div className="purchase-product-tools">
              <label>
                Mostrar productos
                <select
                  value={productKind}
                  onChange={(event) => {
                    setProductKind(event.target.value as typeof productKind)
                    setSelectedArticle(null)
                    setSearchVersion((value) => value + 1)
                  }}
                >
                  <option value="">Todos</option>
                  <option value="ARTICULO">Artículos</option>
                  <option value="VESTIMENTA">Indumentaria y calzado</option>
                </select>
              </label>
              <button
                type="button"
                className="secondary-button"
                onClick={() => setArticlePickerOpen(true)}
              >
                Buscar productos
              </button>
              <button
                type="button"
                className="secondary-button"
                onClick={() => setNewArticleOpen(true)}
              >
                Crear producto sin salir del comprobante
              </button>
            </div>
            <BarcodeEntry
              purchase
              onSelect={(article) => {
                setMessage('')
                setCostEdited(false)
                setPendingArticle(article)
              }}
            />
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
                  endpoint={'/api/v1/alon/articles?kind=' + productKind}
                  required={false}
                  autoFocus={searchVersion > 0}
                  onSelect={(article) => {
                    setSelectedArticle(article)
                    if (article && !costEdited) setUnitCost(String(numberValue(article.costo)))
                    if (
                      article &&
                      (article.tipo_producto === 'INDUMENTARIA' ||
                        article.tipo_producto === 'CALZADO')
                    )
                      chooseArticle(article)
                  }}
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
                          <td data-label="Cantidad">
                            <input
                              aria-label={'Cantidad de ' + line.label}
                              type="number"
                              min={line.wholeUnits ? '1' : '0.001'}
                              step={line.wholeUnits ? '1' : '0.001'}
                              required
                              value={line.quantityText ?? String(line.quantity)}
                              onChange={(event) =>
                                setLines((current) =>
                                  current.map((item, i) =>
                                    i === index
                                      ? {
                                          ...item,
                                          quantity: Number(event.target.value),
                                          quantityText: event.target.value,
                                        }
                                      : item,
                                  ),
                                )
                              }
                            />
                          </td>
                          <td data-label="Costo unitario">
                            <input
                              aria-label={'Costo de ' + line.label}
                              type="number"
                              min="0"
                              step="0.01"
                              required
                              value={line.unitCostText ?? String(line.unitCost)}
                              onChange={(event) =>
                                setLines((current) =>
                                  current.map((item, i) =>
                                    i === index
                                      ? {
                                          ...item,
                                          unitCost: Number(event.target.value),
                                          unitCostText: event.target.value,
                                        }
                                      : item,
                                  ),
                                )
                              }
                            />
                          </td>
                          <td data-label="Subtotal">
                            {currency.format(
                              Number.isFinite(line.quantity * line.unitCost)
                                ? line.quantity * line.unitCost
                                : 0,
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
      <p>
        Stock:{' '}
        {purchase.modifica_stock === false || purchase.modifica_stock === 0
          ? 'Este comprobante no modifica las existencias.'
          : 'Este comprobante incorpora las cantidades al stock.'}
      </p>
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
