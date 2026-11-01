import { sentenceCase } from '../lib/sentenceCase'
import { announceFiscalTarget } from '../hooks/useAccountingOptions'
// Construye formularios reutilizables y convierte sus campos en los datos que espera el servidor.
import { QuantityInput } from './QuantityInput'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Combobox } from './Combobox'
import { LookupField } from './LookupField'
import { apiFetch } from '../lib/http'
import { ArticlePhotosField } from './ArticlePhotosField'
import { photoPayload, savedPhotos, type SavedArticlePhoto } from '../lib/articlePhotos'
import {
  currency,
  textValue,
  numberValue,
  formatDateDisplay,
  todayLocal,
  dateDisplayToIso,
} from '../lib/format'

export type EntryField = {
  name: string
  label: string
  type?: string
  valueType?: 'number'
  options?: { value: string; label: string }[]
  defaultValue?: string
  lookupEndpoint?: string
  defaultLabel?: string
  searchable?: boolean
  exclusiveGroup?: string
  suggestions?: string[]
  checkboxName?: string
  checkboxLabel?: string
  checkedByDefault?: boolean
}

/** Sugiere nombres de artículos existentes mientras permite escribir un nombre nuevo para el alta. */
export function ArticleNameSearch({ field }: { field: EntryField }) {
  const [value, setValue] = useState(sentenceCase(field.defaultValue ?? ''))
  const options = (field.suggestions ?? [])
    .filter((name) => name.toLocaleLowerCase('es-AR').includes(value.toLocaleLowerCase('es-AR')))
    .map((name) => ({ value: name, label: name }))
  return (
    <Combobox
      name={field.name}
      label={field.label}
      value={value}
      options={options}
      onChange={(value) => setValue(sentenceCase(value))}
      onChoose={(option) => setValue(sentenceCase(option.label))}
      required
      placeholder="Escribí uno nuevo o buscá uno existente…"
    />
  )
}

/** Muestra las opciones de un campo como buscador y conserva el identificador correspondiente a la opción seleccionada. */
export function SearchableEntryField({
  field,
  autoFocus = false,
}: {
  field: EntryField
  autoFocus?: boolean
}) {
  const [query, setQuery] = useState<string | null>(null)
  const initial = field.options?.find((option) => option.value === field.defaultValue)
  const display = query ?? initial?.label ?? ''
  const selected = field.options?.find((option) => option.label === display)
  const options = (field.options ?? []).filter((option) =>
    option.label.toLocaleLowerCase('es-AR').includes((query ?? '').toLocaleLowerCase('es-AR')),
  )
  return (
    <>
      <input type="hidden" name={field.name} value={selected?.value ?? ''} />
      <Combobox
        label={field.label}
        value={display}
        options={options}
        onChange={setQuery}
        onChoose={(option) => setQuery(option.label)}
        required
        valid={!!selected}
        autoFocus={autoFocus}
      />
    </>
  )
}

/** Presenta un campo de fecha con formato argentino y la fecha local como valor inicial cuando no se indica otra. */
export function DateTextInput({ name, defaultValue }: { name: string; defaultValue?: string }) {
  return (
    <input
      name={name}
      type="text"
      inputMode="numeric"
      placeholder="dd/mm/aaaa"
      pattern="[0-9]{2}/[0-9]{2}/[0-9]{4}"
      maxLength={10}
      title="Usá el formato dd/mm/aaaa"
      required
      defaultValue={formatDateDisplay(defaultValue ?? todayLocal())}
    />
  )
}

/** Construye los formularios de alta o edición a partir de su definición, valida sus campos y envía los datos y fotos a la API. */
export function LocalEntryPanel({
  title,
  action,
  endpoint,
  fields,
  transform,
  pricePreview = false,
  duplicateNames = [],
  method = 'POST',
  onSaved,
  initialOpen = false,
  panelId,
  articlePhotos = false,
  initialPhotos = [],
}: {
  title: string
  action: string
  endpoint: string
  fields: EntryField[]
  transform?: (payload: Record<string, string | number | boolean>) => unknown
  pricePreview?: boolean
  duplicateNames?: string[]
  method?: 'POST' | 'PUT'
  onSaved?: () => void
  initialOpen?: boolean
  panelId?: string
  articlePhotos?: boolean
  initialPhotos?: SavedArticlePhoto[]
}) {
  const panel = useRef<HTMLElement>(null)
  const [open, setOpen] = useState(initialOpen)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [calculatedPrice, setCalculatedPrice] = useState<number | null>(() => {
    const price = fields.find((field) => field.name === 'salePrice')?.defaultValue
    return price === undefined ? null : Number(price)
  })
  const [cashReport, setCashReport] = useState<Record<string, unknown> | null>(null)
  const [cashSummary, setCashSummary] = useState<Record<string, unknown> | null>(null)
  const [photos, setPhotos] = useState(() => savedPhotos(initialPhotos))
  const [readingPhotos, setReadingPhotos] = useState(false)
  useEffect(() => {
    if (initialOpen)
      window.requestAnimationFrame(() =>
        document
          .getElementById(panelId ?? '')
          ?.scrollIntoView?.({ behavior: 'smooth', block: 'start' }),
      )
  }, [initialOpen, panelId])
  // Al abrir cualquier formulario de gestión, muestra su encabezado y primeros campos arriba.
  useEffect(() => {
    if (!open) return
    const frame = window.requestAnimationFrame(() => {
      panel.current?.scrollIntoView?.({ behavior: 'instant', block: 'start' })
      panel.current
        ?.querySelector<HTMLElement>('form input:not([type="hidden"]), form select, form textarea')
        ?.focus({ preventScroll: true })
    })
    return () => window.cancelAnimationFrame(frame)
  }, [open])
  /** Consulta los importes esperados de la caja de la fecha indicada para preparar su cierre. */
  const refreshCashSummary = (date: string) => {
    if (!endpoint.endsWith('/cash/close') || !date) return
    apiFetch(
      `/api/v1/operations/cash/summary?date=${encodeURIComponent(dateDisplayToIso(date) ?? date)}`,
    )
      .then(async (response) => {
        if (!response.ok) {
          setCashSummary(null)
          return
        }
        setCashSummary((await response.json()) as Record<string, unknown>)
      })
      .catch(() => setCashSummary(null))
  }
  /** Actualiza la vista previa de precios y, en el cierre de caja, vuelve a consultar el resumen si la fecha es válida. */
  const handleFormChange = (event: FormEvent<HTMLFormElement>) => {
    updatePricePreview(event)
    if (endpoint.endsWith('/cash/close')) {
      const parsedDate = dateDisplayToIso(new FormData(event.currentTarget).get('date'))
      if (parsedDate) refreshCashSummary(parsedDate)
      else setCashSummary(null)
    }
  }
  /** Calcula el precio sugerido desde costo, flete, margen y redondeo, respetando los cambios manuales del precio final. */
  const updatePricePreview = (event: FormEvent<HTMLFormElement>) => {
    if (!pricePreview) return
    const values = new FormData(event.currentTarget)
    const baseCost = Number(values.get('cost') ?? 0)
    const amount = Number(values.get('freightPercent') ?? values.get('freightAmount') ?? 0)
    const freight = (baseCost * amount) / 100
    const margin = Number(values.get('marginPercent') ?? 0)
    let price = Math.round((baseCost + freight) * (1 + margin / 100) * 100) / 100
    if (values.get('roundPrice') === 'on' && price > 0) price = Math.ceil(price / 10) * 10
    const changed = event.target as HTMLInputElement
    if (
      ['cost', 'freightPercent', 'freightAmount', 'marginPercent', 'roundPrice'].includes(
        changed.name,
      )
    ) {
      const salePrice = event.currentTarget.elements.namedItem('salePrice')
      if (salePrice instanceof HTMLInputElement) salePrice.value = price.toFixed(2)
    }
    setCalculatedPrice(price)
  }
  /** Valida duplicados, fechas y campos del formulario; transforma valores y fotos, guarda el registro y notifica el resultado. */
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy || readingPhotos) return
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const submittedName = String(form.get('name') ?? '')
      .trim()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLocaleLowerCase('es-AR')
    if (
      submittedName &&
      duplicateNames.some(
        (name) =>
          name
            .trim()
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .toLocaleLowerCase('es-AR') === submittedName,
      )
    ) {
      setMessage(
        'Ya existe un artículo con ese nombre. Elegí otro o buscá el existente en el listado.',
      )
      return
    }
    const dateField = fields.find((field) => field.type === 'date')
    const parsedDate = dateField ? dateDisplayToIso(form.get(dateField.name)) : null
    if (dateField && !parsedDate) {
      setMessage('Ingresá una fecha válida con el formato dd/mm/aaaa.')
      return
    }
    const payloadEntries = fields.flatMap((field) => {
      const checkboxValue = field.checkboxName
        ? [
            [field.checkboxName, form.get(field.checkboxName) === 'on'] as [
              string,
              string | number | boolean,
            ],
          ]
        : []
      if (field.type === 'checkbox')
        return [[field.name, form.get(field.name) === 'on'] as [string, string | number | boolean]]
      const rawValue = String(form.get(field.name) ?? '')
      const value =
        articlePhotos &&
        field.name !== 'code' &&
        !field.lookupEndpoint &&
        !field.options &&
        (!field.type || field.type === 'text' || field.type === 'textarea')
          ? sentenceCase(rawValue)
          : rawValue
      if (field.type === 'date')
        return [
          ...checkboxValue,
          [field.name, parsedDate ?? ''] as [string, string | number | boolean],
        ]
      return [
        ...checkboxValue,
        [
          field.name,
          field.type === 'number' || field.valueType === 'number' ? Number(value) : value,
        ] as [string, string | number | boolean],
      ]
    })
    const payload = Object.fromEntries(payloadEntries)
    if (articlePhotos || 'freightPercent' in payload || 'freightAmount' in payload) {
      payload.freightPercent = payload.freightPercent ?? payload.freightAmount ?? 0
      payload.freightByValue = false
      payload.freightByPercent = true
      payload.freightPerUnit = 0
      delete payload.freightAmount
    }
    if (fields.some((field) => field.searchable && !payload[field.name])) {
      setMessage('Elegí un artículo del listado.')
      return
    }
    const apparel =
      'apparelKind' in payload
        ? {
            kind: payload.apparelKind,
            model: payload.apparelModel,
            brand: payload.apparelBrand,
            size: payload.apparelSize,
            sizeSystem: payload.apparelSizeSystem,
            color: payload.apparelColor,
            material: payload.apparelMaterial,
            audience: payload.apparelAudience,
            season: payload.apparelSeason,
            description: payload.apparelDescription,
          }
        : undefined
    if (
      apparel &&
      [payload.initialStock, payload.minimumStock].some(
        (value) => value !== undefined && !Number.isInteger(Number(value)),
      )
    ) {
      const fieldName = ['initialStock', 'minimumStock'].find(
        (name) => payload[name] !== undefined && !Number.isInteger(Number(payload[name])),
      )
      const field = fieldName ? formElement.elements.namedItem(fieldName) : null
      if (field instanceof HTMLInputElement) {
        field.setCustomValidity('Cargá cantidades enteras para cada talle y color.')
        field.reportValidity()
        field.focus()
      }
      setMessage('Cargá cantidades enteras para cada talle y color.')
      return
    }
    for (const key of Object.keys(payload)) if (key.startsWith('apparel')) delete payload[key]
    const articleName = apparel ? String(payload.name).trim() : ''
    const suffix = apparel ? ' · ' + apparel.size + ' · ' + apparel.color : ''
    if (apparel && !articleName.endsWith(suffix)) payload.name = articleName + suffix
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch(endpoint, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(
          transform
            ? transform(payload)
            : {
                ...payload,
                ...(articlePhotos ? { supplierId: 0 } : {}),
                ...(apparel
                  ? { apparel, categoryId: 0, initialStock: payload.initialStock ?? 0 }
                  : {}),
                ...(articlePhotos ? { photos: photoPayload(photos) } : {}),
              },
        ),
      })
      const result = (await response.json().catch(() => ({}))) as {
        error?: string
        fields?: Record<string, string>
      }
      if (!response.ok) {
        for (const [key, message] of Object.entries(result.fields ?? {})) {
          const names: Record<string, string> = {
            'apparel.size': 'apparelSize',
            'apparel.color': 'apparelColor',
            'apparel.brand': 'apparelBrand',
            'apparel.model': 'apparelModel',
            'apparel.description': 'apparelDescription',
            'apparel.material': 'apparelMaterial',
            'apparel.sizeSystem': 'apparelSizeSystem',
            'apparel.audience': 'apparelAudience',
            'apparel.season': 'apparelSeason',
            'apparel.kind': 'apparelKind',
          }
          let field = formElement.elements.namedItem(names[key] ?? key)
          if (field instanceof HTMLInputElement && field.type === 'hidden')
            field =
              field
                .closest('.searchable-field')
                ?.querySelector<HTMLInputElement>('input[role="combobox"]') ?? field
          if (
            field instanceof HTMLInputElement ||
            field instanceof HTMLTextAreaElement ||
            field instanceof HTMLSelectElement
          ) {
            field.setCustomValidity(message)
            field.reportValidity()
            field.focus()
            break
          }
        }
        throw new Error(result.error ?? 'No se pudo guardar el registro.')
      }
      if (endpoint === '/api/v1/operations/cash/expenses') {
        const saved = result as { id?: number; importe?: number }
        if (saved.id)
          announceFiscalTarget({
            clase: 'GASTO',
            id: saved.id,
            fecha: String(payload.date),
            total: Number(saved.importe ?? payload.amount),
            tercero: '',
          })
      }
      if (endpoint.endsWith('/cash/close')) setCashReport(result)
      window.dispatchEvent(new Event('alon:refresh'))
      onSaved?.()
      setMessage('Registro guardado correctamente.')
      formElement.reset()
      setPhotos([])
      setCalculatedPrice(null)
      setOpen(false)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section ref={panel} className="entry-panel" id={panelId}>
      <div className="entry-heading">
        <div>
          <span className="alon-tag">GESTIÓN DEL COMERCIO</span>
          <strong>{title}</strong>
        </div>
        <button
          className="primary-button"
          type="button"
          onClick={() => {
            setOpen((value) => {
              const next = !value
              if (next && endpoint.endsWith('/cash/close')) {
                setCashReport(null)
                refreshCashSummary(todayLocal())
              }
              return next
            })
            setMessage('')
          }}
        >
          {open ? 'Cerrar' : `＋ ${action}`}
        </button>
      </div>
      {open && (
        <form
          className="entry-form"
          onSubmit={submit}
          onBlurCapture={(event) => {
            const field = event.target
            if (
              !articlePhotos ||
              !(field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement) ||
              (field.getAttribute('role') === 'combobox' && !field.name) ||
              field.name === 'code' ||
              !field.name ||
              (field instanceof HTMLInputElement && field.type !== 'text')
            )
              return
            const value = sentenceCase(field.value)
            if (value !== field.value) {
              const prototype =
                field instanceof HTMLTextAreaElement
                  ? HTMLTextAreaElement.prototype
                  : HTMLInputElement.prototype
              Object.getOwnPropertyDescriptor(prototype, 'value')?.set?.call(field, value)
              field.dispatchEvent(new Event('input', { bubbles: true }))
            }
          }}
          onInput={(e) => {
            const target = e.target
            if (
              target instanceof HTMLInputElement ||
              target instanceof HTMLTextAreaElement ||
              target instanceof HTMLSelectElement
            )
              target.setCustomValidity('')
          }}
          onChange={handleFormChange}
        >
          {fields.some((field) => field.name === 'apparelKind') && (
            <p style={{ gridColumn: '1 / -1' }}>
              Cargá un talle y color por código. En calzado, una unidad equivale a un par. En la
              tienda, estas variantes se agrupan por el nombre del producto, sin talle ni color.{' '}
              {!fields.some((field) => field.name === 'initialStock') &&
                'Esta alta no suma stock: incorporá después la variante al comprobante de compra.'}
            </p>
          )}
          {fields.map((field) =>
            field.checkboxName ? (
              <div className="entry-freight-field" key={field.name}>
                <label htmlFor={(panelId ?? title) + '-' + field.name}>{field.label}</label>
                <input
                  id={(panelId ?? title) + '-' + field.name}
                  name={field.name}
                  type="number"
                  min="0"
                  step="0.01"
                  defaultValue={
                    articlePhotos && field.name !== 'code' && field.defaultValue
                      ? sentenceCase(field.defaultValue)
                      : field.defaultValue
                  }
                />
                <label className="entry-inline-checkbox">
                  <input
                    name={field.checkboxName}
                    type="checkbox"
                    defaultChecked={field.checkedByDefault}
                  />
                  <span>{field.checkboxLabel}</span>
                </label>
              </div>
            ) : (
              <label
                key={field.name}
                className={field.type === 'checkbox' ? 'entry-checkbox-label' : undefined}
              >
                {field.type === 'checkbox' ? (
                  <>
                    <input
                      name={field.name}
                      type="checkbox"
                      defaultChecked={field.defaultValue === 'true'}
                      data-exclusive-group={field.exclusiveGroup}
                      onChange={(event) => {
                        if (event.currentTarget.checked && field.exclusiveGroup)
                          event.currentTarget.form
                            ?.querySelectorAll<HTMLInputElement>(
                              '[data-exclusive-group="' + field.exclusiveGroup + '"]',
                            )
                            .forEach((other) => {
                              if (other !== event.currentTarget) other.checked = false
                            })
                      }}
                    />
                    <span>{field.label}</span>
                  </>
                ) : (
                  <>
                    {field.label}
                    {field.lookupEndpoint ? (
                      <LookupField
                        name={field.name}
                        label={field.label}
                        endpoint={field.lookupEndpoint}
                        initialRecord={
                          field.defaultValue
                            ? {
                                id: Number(field.defaultValue),
                                nombre: field.defaultLabel ?? field.defaultValue,
                              }
                            : undefined
                        }
                      />
                    ) : field.searchable ? (
                      <SearchableEntryField field={field} />
                    ) : field.suggestions ? (
                      <ArticleNameSearch field={field} />
                    ) : field.options ? (
                      <select name={field.name} required defaultValue={field.defaultValue ?? ''}>
                        <option value="" disabled>
                          Seleccionar…
                        </option>
                        {field.options.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    ) : ['initialStock', 'minimumStock'].includes(field.name) ? (
                      <QuantityInput
                        name={field.name}
                        label={field.label}
                        min="0"
                        required
                        defaultValue={
                          articlePhotos && field.name !== 'code' && field.defaultValue
                            ? sentenceCase(field.defaultValue)
                            : field.defaultValue
                        }
                      />
                    ) : field.type === 'textarea' ? (
                      <textarea
                        name={field.name}
                        required
                        maxLength={4000}
                        defaultValue={
                          articlePhotos && field.name !== 'code' && field.defaultValue
                            ? sentenceCase(field.defaultValue)
                            : field.defaultValue
                        }
                        autoCapitalize="sentences"
                        rows={4}
                      />
                    ) : field.type === 'date' ? (
                      <DateTextInput
                        name={field.name}
                        defaultValue={
                          articlePhotos && field.name !== 'code' && field.defaultValue
                            ? sentenceCase(field.defaultValue)
                            : field.defaultValue
                        }
                      />
                    ) : (
                      <input
                        name={field.name}
                        type={field.type ?? 'text'}
                        autoCapitalize={
                          articlePhotos && field.name !== 'code' ? 'sentences' : 'off'
                        }
                        required={
                          !(
                            pricePreview &&
                            ['freightPerUnit', 'freightPercent'].includes(field.name)
                          )
                        }
                        min={field.type === 'number' ? '0' : undefined}
                        step={field.type === 'number' ? '0.01' : undefined}
                        defaultValue={
                          articlePhotos && field.name !== 'code' && field.defaultValue
                            ? sentenceCase(field.defaultValue)
                            : field.defaultValue
                        }
                      />
                    )}
                  </>
                )}
              </label>
            ),
          )}
          {articlePhotos && (
            <ArticlePhotosField
              value={photos}
              onChange={setPhotos}
              disabled={busy}
              onReading={setReadingPhotos}
            />
          )}
          {pricePreview && (
            <div className="entry-price-preview">
              Precio de venta calculado <strong>{currency.format(calculatedPrice ?? 0)}</strong>
              <small>Se calcula con costo + flete seleccionado + margen indicado.</small>
            </div>
          )}
          <div className="entry-actions">
            <span>
              {title.toLocaleLowerCase('es-AR').includes('factura')
                ? 'Se guarda como registro interno; no emite comprobantes fiscales ante ARCA.'
                : 'La operación se guardará en la base de datos del comercio.'}
            </span>
            <button className="primary-button" type="submit" disabled={busy || readingPhotos}>
              {busy ? 'Guardando…' : 'Guardar'}
            </button>
          </div>
        </form>
      )}
      {message && (
        <p className="entry-message" role="status">
          {message}
        </p>
      )}
      {cashSummary && !cashReport && endpoint.endsWith('/cash/close') && (
        <div className="cash-close-report" role="status">
          <strong>
            Previsión de cierre · {formatDateDisplay(cashSummary.fecha)} · Sesión{' '}
            {textValue(cashSummary.numero_sesion)}
          </strong>
          <div>
            <span>
              Efectivo inicial <b>{currency.format(numberValue(cashSummary.saldo_inicial))}</b>
            </span>
            <span>
              Ventas en efectivo <b>{currency.format(numberValue(cashSummary.ventas_efectivo))}</b>
            </span>
            <span>
              Ventas por Mercado Pago{' '}
              <b>{currency.format(numberValue(cashSummary.ventas_mercado_pago))}</b>
            </span>
            <span>
              Pagos/gastos en efectivo{' '}
              <b>{currency.format(numberValue(cashSummary.egresos_efectivo))}</b>
            </span>
            <span>
              Pagos/gastos por Mercado Pago{' '}
              <b>{currency.format(numberValue(cashSummary.egresos_mercado_pago))}</b>
            </span>
            <span>
              Efectivo que debe haber{' '}
              <b>{currency.format(numberValue(cashSummary.efectivo_esperado))}</b>
            </span>
            <span>
              Mercado Pago neto esperado{' '}
              <b>{currency.format(numberValue(cashSummary.mercado_pago_esperado))}</b>
            </span>
          </div>
        </div>
      )}
      {cashReport && endpoint.endsWith('/cash/close') && (
        <div className="cash-close-report" role="status">
          <strong>
            Informe del cierre · {formatDateDisplay(cashReport.fecha)} · Sesión{' '}
            {textValue(cashReport.numero_sesion)}
          </strong>
          <div>
            <span>
              Efectivo inicial <b>{currency.format(numberValue(cashReport.saldo_inicial))}</b>
            </span>
            <span>
              Ventas en efectivo <b>{currency.format(numberValue(cashReport.ventas_efectivo))}</b>
            </span>
            <span>
              Ventas por Mercado Pago{' '}
              <b>{currency.format(numberValue(cashReport.ventas_mercado_pago))}</b>
            </span>
            <span>
              Pagos/gastos en efectivo{' '}
              <b>{currency.format(numberValue(cashReport.egresos_efectivo))}</b>
            </span>
            <span>
              Pagos/gastos por Mercado Pago{' '}
              <b>{currency.format(numberValue(cashReport.egresos_mercado_pago))}</b>
            </span>
            <span>
              Efectivo que debe haber{' '}
              <b>{currency.format(numberValue(cashReport.efectivo_esperado))}</b>
            </span>
            <span>
              Mercado Pago neto esperado{' '}
              <b>{currency.format(numberValue(cashReport.mercado_pago_esperado))}</b>
            </span>
            <span>
              Diferencia de efectivo <b>{currency.format(numberValue(cashReport.diferencia))}</b>
            </span>
          </div>
        </div>
      )}
    </section>
  )
}

/** Representa visualmente el estado de un registro mediante una etiqueta y su color asociado. */
export function StatusPill({ active }: { active: boolean }) {
  return (
    <span className={`status-pill ${active ? 'status-active' : 'status-inactive'}`}>
      {active ? 'Activo' : 'Inactivo'}
    </span>
  )
}
