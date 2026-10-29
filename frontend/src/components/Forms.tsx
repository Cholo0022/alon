import { useEffect, useId, useState, type FormEvent } from 'react'
import { LookupField } from './LookupField'
import { apiFetch } from '../lib/http'
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

export function ArticleNameSearch({ field }: { field: EntryField }) {
  const listId = useId()
  return (
    <>
      <input
        name={field.name}
        type="text"
        aria-label={field.label}
        list={listId}
        placeholder="Escribí uno nuevo o buscá uno existente…"
        required
      />
      <datalist id={listId}>
        {(field.suggestions ?? []).map((name) => (
          <option value={name} key={name} />
        ))}
      </datalist>
    </>
  )
}

export function SearchableEntryField({
  field,
  autoFocus = false,
}: {
  field: EntryField
  autoFocus?: boolean
}) {
  const listId = useId()
  const [query, setQuery] = useState<string | null>(null)
  const initial = field.options?.find((option) => option.value === field.defaultValue)
  const display = query ?? initial?.label ?? ''
  const selected = field.options?.find((option) => option.label === display)
  return (
    <div className="searchable-field">
      <input type="hidden" name={field.name} value={selected?.value ?? ''} />
      <input
        type="text"
        list={listId}
        aria-label={field.label}
        placeholder={'Buscar ' + field.label.toLocaleLowerCase('es-AR') + '…'}
        value={display}
        autoFocus={autoFocus}
        required
        onChange={(event) => {
          setQuery(event.target.value)
          event.target.setCustomValidity('')
        }}
        onBlur={(event) =>
          event.target.setCustomValidity(selected ? '' : 'Elegí una opción del listado.')
        }
      />
      <datalist id={listId}>
        {(field.options ?? []).map((option) => (
          <option value={option.label} key={option.value} />
        ))}
      </datalist>
    </div>
  )
}

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
}) {
  const [open, setOpen] = useState(initialOpen)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [calculatedPrice, setCalculatedPrice] = useState<number | null>(null)
  const [cashReport, setCashReport] = useState<Record<string, unknown> | null>(null)
  const [cashSummary, setCashSummary] = useState<Record<string, unknown> | null>(null)
  useEffect(() => {
    if (initialOpen)
      window.requestAnimationFrame(() =>
        document
          .getElementById(panelId ?? '')
          ?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
      )
  }, [initialOpen, panelId])
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
  const handleFormChange = (event: FormEvent<HTMLFormElement>) => {
    updatePricePreview(event)
    if (endpoint.endsWith('/cash/close')) {
      const parsedDate = dateDisplayToIso(new FormData(event.currentTarget).get('date'))
      if (parsedDate) refreshCashSummary(parsedDate)
      else setCashSummary(null)
    }
  }
  const updatePricePreview = (event: FormEvent<HTMLFormElement>) => {
    if (!pricePreview) return
    const values = new FormData(event.currentTarget)
    const baseCost = Number(values.get('cost') ?? 0)
    const costFreight =
      values.get('freightByValue') === 'on' ? Number(values.get('freightPerUnit') ?? 0) : 0
    const percentFreight =
      values.get('freightByPercent') === 'on'
        ? (baseCost * Number(values.get('freightPercent') ?? 0)) / 100
        : 0
    const freight = costFreight + percentFreight
    const margin = Number(values.get('marginPercent') ?? 0)
    let price = Math.round((baseCost + freight) * (1 + margin / 100) * 100) / 100
    if (values.get('roundPrice') === 'on' && price > 0) price = Math.ceil(price / 10) * 10
    setCalculatedPrice(price)
  }
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
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
      const value = String(form.get(field.name) ?? '')
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
    if (fields.some((field) => field.searchable && !payload[field.name])) {
      setMessage('Elegí un artículo del listado.')
      return
    }
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch(endpoint, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(transform ? transform(payload) : payload),
      })
      const result = (await response.json().catch(() => ({}))) as { error?: string }
      if (!response.ok) throw new Error(result.error ?? 'No se pudo guardar el registro.')
      if (endpoint.endsWith('/cash/close')) setCashReport(result)
      window.dispatchEvent(new Event('alon:refresh'))
      onSaved?.()
      setMessage('Registro guardado correctamente.')
      formElement.reset()
      setCalculatedPrice(null)
      setOpen(false)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="entry-panel" id={panelId}>
      <div className="entry-heading">
        <div>
          <span className="demo-tag">GESTIÓN DEL COMERCIO</span>
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
        <form className="entry-form" onSubmit={submit} onChange={handleFormChange}>
          {fields.map((field) => (
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
                  ) : field.type === 'date' ? (
                    <DateTextInput name={field.name} defaultValue={field.defaultValue} />
                  ) : field.checkboxName ? (
                    <div className="entry-freight-row">
                      <input
                        name={field.name}
                        type="number"
                        required={false}
                        min="0"
                        step="0.01"
                        defaultValue={field.defaultValue}
                      />
                      <div className="entry-inline-checkbox">
                        <input
                          name={field.checkboxName}
                          type="checkbox"
                          aria-label={field.checkboxLabel}
                          defaultChecked={field.checkedByDefault}
                          data-exclusive-group="freight"
                          onChange={(event) => {
                            if (event.currentTarget.checked)
                              event.currentTarget.form
                                ?.querySelectorAll<HTMLInputElement>(
                                  '[data-exclusive-group="freight"]',
                                )
                                .forEach((other) => {
                                  if (other !== event.currentTarget) other.checked = false
                                })
                          }}
                        />
                        <span>{field.checkboxLabel}</span>
                      </div>
                    </div>
                  ) : (
                    <input
                      name={field.name}
                      type={field.type ?? 'text'}
                      required={
                        !(pricePreview && ['freightPerUnit', 'freightPercent'].includes(field.name))
                      }
                      min={field.type === 'number' ? '0' : undefined}
                      step={
                        field.type === 'number'
                          ? ['initialStock', 'minimumStock'].includes(field.name)
                            ? '0.001'
                            : '0.01'
                          : undefined
                      }
                      defaultValue={field.defaultValue}
                    />
                  )}
                </>
              )}
            </label>
          ))}
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
            <button className="primary-button" type="submit" disabled={busy}>
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

export function StatusPill({ active }: { active: boolean }) {
  return (
    <span className={`status-pill ${active ? 'status-active' : 'status-inactive'}`}>
      {active ? 'Activo' : 'Inactivo'}
    </span>
  )
}
