import { useEffect, useRef, useState, type FormEvent } from 'react'
import { demoArticles, demoCustomers, demoSuppliers } from '../demo-data'
import '../App.css'

type Section = 'inicio' | 'ventas' | 'articulos' | 'rubros' | 'clientes' | 'compras' | 'proveedores' | 'caja' | 'informes' | 'tienda' | 'usuarios'

const navigation: { id: Section; label: string; icon: string }[] = [
  { id: 'inicio', label: 'Resumen', icon: '⌂' },
  { id: 'ventas', label: 'Ventas', icon: '↗' },
  { id: 'articulos', label: 'Artículos y stock', icon: '▦' },
  { id: 'rubros', label: 'Rubros', icon: '▤' },
  { id: 'clientes', label: 'Clientes', icon: '♙' },
  { id: 'compras', label: 'Compras', icon: '↙' },
  { id: 'proveedores', label: 'Proveedores', icon: '♧' },
  { id: 'caja', label: 'Caja', icon: '▣' },
  { id: 'informes', label: 'Informes', icon: '▤' },
  { id: 'tienda', label: 'Tienda online', icon: '⌑' },
  { id: 'usuarios', label: 'Usuarios y accesos', icon: '♙' },
]

const descriptions: Record<Section, string> = {
  inicio: 'La actividad del negocio, en un solo lugar.',
  ventas: 'Registrá ventas de contado o en cuenta corriente.',
  articulos: 'Explorá los artículos ficticios de ferretería y sus niveles de stock.',
  rubros: 'Creá y consultá los rubros disponibles para organizar tus artículos.',
  clientes: 'Consultá los clientes ficticios y sus saldos de prueba.',
  compras: 'Cargá facturas y remitos para actualizar costos y stock.',
  proveedores: 'Consultá proveedores de prueba y saldos pendientes ficticios.',
  caja: 'Controlá aperturas, cobros, gastos y cierres de caja.',
  informes: 'Revisá ventas, costos y gastos por período.',
  tienda: 'Prepará el catálogo online de tu comercio.',
  usuarios: 'Creá usuarios y elegí a qué secciones pueden acceder.',
}

const currency = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 })
const formatDateDisplay = (value: unknown) => { const raw = textValue(value); const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(raw); return match ? match[3] + '/' + match[2] + '/' + match[1] : raw || '—' }
const formatDateTimeDisplay = (value: unknown) => { const raw = textValue(value); const match = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/.exec(raw); return match ? match[3] + '/' + match[2] + '/' + match[1] + ' - ' + match[4] + ':' + match[5] : formatDateDisplay(raw) }
const formatMonthDisplay = (value: unknown) => { const raw = textValue(value); const match = /^(\d{4})-(\d{2})$/.exec(raw); return match ? match[2] + '/' + match[1] : raw || '—' }
type ApiRow = Record<string, unknown>

function useDemoApiRows(endpoint: string) {
  const [rows, setRows] = useState<ApiRow[]>([])
  useEffect(() => {
    let active = true
    const load = () => fetch(endpoint).then((response) => {
      if (!response.ok) throw new Error('API unavailable')
      return response.json() as Promise<ApiRow[]>
    }).then((data) => { if (active) setRows(data) }).catch(() => undefined)
    const refresh = () => { void load() }
    void load()
    window.addEventListener('alon:refresh', refresh)
    return () => { active = false; window.removeEventListener('alon:refresh', refresh) }
  }, [endpoint])
  return rows
}

const textValue = (value: unknown) => String(value ?? '')
const numberValue = (value: unknown) => Number(value ?? 0)
const dateDisplayToIso = (value: unknown) => {
  const raw = textValue(value).trim()
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(raw)
  if (!match) return null
  const day = Number(match[1]), month = Number(match[2]), year = Number(match[3])
  const check = new Date(Date.UTC(year, month - 1, day))
  if (check.getUTCFullYear() !== year || check.getUTCMonth() !== month - 1 || check.getUTCDate() !== day) return null
  return year + '-' + match[2] + '-' + match[1]
}

type EntryField = { name: string; label: string; type?: string; valueType?: 'number'; options?: { value: string; label: string }[]; defaultValue?: string; searchable?: boolean; exclusiveGroup?: string; suggestions?: string[]; checkboxName?: string; checkboxLabel?: string; checkedByDefault?: boolean }

function ArticleNameSearch({ field }: { field: EntryField }) {
  const [value, setValue] = useState('')
  const [expanded, setExpanded] = useState(false)
  const suggestions = field.suggestions ?? []
  const filtered = value.trim() ? suggestions.filter((name) => name.toLocaleLowerCase('es-AR').includes(value.toLocaleLowerCase('es-AR'))) : []
  return <div className="searchable-field">
    <input name={field.name} type="search" role="combobox" aria-label={field.label} aria-expanded={expanded} aria-autocomplete="list" placeholder="Escribí uno nuevo o buscá uno existente…" value={value} onFocus={() => { if (value.trim() && filtered.length) setExpanded(true) }} onChange={(event) => { const next = event.target.value; setValue(next); setExpanded(Boolean(next.trim() && suggestions.some((name) => name.toLocaleLowerCase('es-AR').includes(next.toLocaleLowerCase('es-AR'))))) }} onBlur={() => window.setTimeout(() => setExpanded(false), 120)} required />
    {expanded && filtered.length > 0 && <div className="searchable-options" role="listbox">{filtered.map((name) => <button type="button" role="option" key={name} onMouseDown={(event) => event.preventDefault()} onClick={() => { setValue(name); setExpanded(false) }}>{name}</button>)}</div>}
  </div>
}

function SearchableEntryField({ field, autoFocus = false }: { field: EntryField; autoFocus?: boolean }) {
  const rootRef = useRef<HTMLDivElement>(null)
  const [selected, setSelected] = useState(() => field.options?.find((option) => option.value === field.defaultValue) ?? null)
  const [query, setQuery] = useState('')
  const [expanded, setExpanded] = useState(false)
  const [focused, setFocused] = useState(false)
  useEffect(() => {
    if (!selected && field.defaultValue) setSelected(field.options?.find((option) => option.value === field.defaultValue) ?? null)
  }, [field.defaultValue, field.options, selected])
  useEffect(() => { if (autoFocus) rootRef.current?.querySelector<HTMLInputElement>('input[role="combobox"]')?.focus() }, [autoFocus])
  const filtered = query.trim() ? (field.options ?? []).filter((option) => `${option.label} ${option.value}`.toLocaleLowerCase('es-AR').includes(query.toLocaleLowerCase('es-AR'))) : []
  return <div className="searchable-field" ref={rootRef}>
    <input type="hidden" name={field.name} value={selected?.value ?? ''} />
    <input type="search" role="combobox" aria-label={field.label} aria-expanded={expanded} aria-autocomplete="list" placeholder={`Buscar ${field.label.toLocaleLowerCase('es-AR')}…`} value={focused ? query : selected?.label ?? ''} onFocus={() => { setFocused(true); setQuery(''); setExpanded(false) }} onChange={(event) => { const next = event.target.value; setQuery(next); setSelected(null); setExpanded(Boolean(next.trim() && (field.options ?? []).some((option) => `${option.label} ${option.value}`.toLocaleLowerCase('es-AR').includes(next.toLocaleLowerCase('es-AR'))))) }} onBlur={() => window.setTimeout(() => { setExpanded(false); setFocused(false) }, 120)} />
    {expanded && filtered.length > 0 && <div className="searchable-options" role="listbox">{filtered.map((option) => <button type="button" role="option" aria-selected={selected?.value === option.value} key={option.value} onMouseDown={(event) => event.preventDefault()} onClick={() => { setSelected(option); setQuery(''); setExpanded(false) }}>{option.label}</button>)}</div>}
  </div>
}

const todayLocal = () => {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10)
}

function DateTextInput({ name, defaultValue }: { name: string; defaultValue?: string }) {
  return <input name={name} type="text" inputMode="numeric" placeholder="dd/mm/aaaa" pattern="[0-9]{2}/[0-9]{2}/[0-9]{4}" maxLength={10} title="Usá el formato dd/mm/aaaa" required defaultValue={formatDateDisplay(defaultValue ?? todayLocal())} />
}

function LocalEntryPanel({ title, action, endpoint, fields, transform, pricePreview = false, duplicateNames = [], method = 'POST', onSaved, initialOpen = false, panelId }: { title: string; action: string; endpoint: string; fields: EntryField[]; transform?: (payload: Record<string, string | number | boolean>) => unknown; pricePreview?: boolean; duplicateNames?: string[]; method?: 'POST' | 'PUT'; onSaved?: () => void; initialOpen?: boolean; panelId?: string }) {
  const [open, setOpen] = useState(initialOpen)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [calculatedPrice, setCalculatedPrice] = useState<number | null>(null)
  const [cashReport, setCashReport] = useState<Record<string, unknown> | null>(null)
  const [cashSummary, setCashSummary] = useState<Record<string, unknown> | null>(null)
  useEffect(() => {
    if (initialOpen) window.requestAnimationFrame(() => document.getElementById(panelId ?? '')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  }, [initialOpen, panelId])
  const refreshCashSummary = (date: string) => {
    if (!endpoint.endsWith('/cash/close') || !date) return
    fetch(`/api/v1/operations/cash/summary?date=${encodeURIComponent(dateDisplayToIso(date) ?? date)}`).then(async (response) => {
      if (!response.ok) { setCashSummary(null); return }
      setCashSummary(await response.json() as Record<string, unknown>)
    }).catch(() => setCashSummary(null))
  }
  const handleFormChange = (event: FormEvent<HTMLFormElement>) => {
    updatePricePreview(event)
    if (endpoint.endsWith('/cash/close')) { const parsedDate = dateDisplayToIso(new FormData(event.currentTarget).get('date')); if (parsedDate) refreshCashSummary(parsedDate); else setCashSummary(null) }
  }
  const updatePricePreview = (event: FormEvent<HTMLFormElement>) => {
    if (!pricePreview) return
    const values = new FormData(event.currentTarget)
    const baseCost = Number(values.get('cost') ?? 0)
    const costFreight = values.get('freightByValue') === 'on' ? Number(values.get('freightPerUnit') ?? 0) : 0
    const percentFreight = values.get('freightByPercent') === 'on' ? baseCost * Number(values.get('freightPercent') ?? 0) / 100 : 0
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
    const submittedName = String(form.get('name') ?? '').trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-AR')
    if (submittedName && duplicateNames.some((name) => name.trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-AR') === submittedName)) { setMessage('Ya existe un artículo con ese nombre. Elegí otro o buscá el existente en el listado.'); return }
    const dateField = fields.find((field) => field.type === 'date')
    const parsedDate = dateField ? dateDisplayToIso(form.get(dateField.name)) : null
    if (dateField && !parsedDate) { setMessage('Ingresá una fecha válida con el formato dd/mm/aaaa.'); return }
    const payloadEntries = fields.flatMap((field) => {
      const checkboxValue = field.checkboxName ? [[field.checkboxName, form.get(field.checkboxName) === 'on'] as [string, string | number | boolean]] : []
      if (field.type === 'checkbox') return [[field.name, form.get(field.name) === 'on'] as [string, string | number | boolean]]
      const value = String(form.get(field.name) ?? '')
      if (field.type === 'date') return [...checkboxValue, [field.name, parsedDate ?? ''] as [string, string | number | boolean]]
      return [...checkboxValue, [field.name, field.type === 'number' || field.valueType === 'number' ? Number(value) : value] as [string, string | number | boolean]]
    })
    const payload = Object.fromEntries(payloadEntries)
    if (fields.some((field) => field.searchable && !payload[field.name])) { setMessage('Elegí un artículo del listado.'); return }
    setBusy(true); setMessage('')
    try {
      const response = await fetch(endpoint, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(transform ? transform(payload) : payload) })
      const result = await response.json().catch(() => ({})) as { error?: string }
      if (!response.ok) throw new Error(result.error ?? 'No se pudo guardar el registro.')
      if (endpoint.endsWith('/cash/close')) setCashReport(result)
      window.dispatchEvent(new Event('alon:refresh'))
      onSaved?.()
      setMessage('Registro guardado correctamente.')
      formElement.reset()
      setCalculatedPrice(null)
      setOpen(false)
    } catch (error) { setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.') }
    finally { setBusy(false) }
  }

  return <section className="entry-panel" id={panelId}>
    <div className="entry-heading"><div><span className="demo-tag">MODO DEMOSTRACIÓN</span><strong>{title}</strong></div><button className="primary-button" type="button" onClick={() => { setOpen((value) => { const next = !value; if (next && endpoint.endsWith('/cash/close')) { setCashReport(null); refreshCashSummary(todayLocal()) } return next }); setMessage('') }}>{open ? 'Cerrar' : `＋ ${action}`}</button></div>
    {open && <form className="entry-form" onSubmit={submit} onChange={handleFormChange}>
      {fields.map((field) => (
        <label key={field.name} className={field.type === 'checkbox' ? 'entry-checkbox-label' : undefined}>
          {field.type === 'checkbox' ? <><input name={field.name} type="checkbox" defaultChecked={field.defaultValue === 'true'} data-exclusive-group={field.exclusiveGroup} onChange={(event) => { if (event.currentTarget.checked && field.exclusiveGroup) event.currentTarget.form?.querySelectorAll<HTMLInputElement>('[data-exclusive-group="' + field.exclusiveGroup + '"]').forEach((other) => { if (other !== event.currentTarget) other.checked = false }); }} /><span>{field.label}</span></> : <>
            {field.label}
            {field.searchable ? <SearchableEntryField field={field} /> : field.suggestions ? <ArticleNameSearch field={field} /> : field.options ? <select name={field.name} required defaultValue={field.defaultValue ?? ''}><option value="" disabled>Seleccionar…</option>{field.options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select> : field.type === 'date' ? <DateTextInput name={field.name} defaultValue={field.defaultValue} /> : field.checkboxName ? <div className="entry-freight-row"><input name={field.name} type="number" required={false} min="0" step="0.01" defaultValue={field.defaultValue} /><div className="entry-inline-checkbox"><input name={field.checkboxName} type="checkbox" aria-label={field.checkboxLabel} defaultChecked={field.checkedByDefault} data-exclusive-group="freight" onChange={(event) => { if (event.currentTarget.checked) event.currentTarget.form?.querySelectorAll<HTMLInputElement>('[data-exclusive-group="freight"]').forEach((other) => { if (other !== event.currentTarget) other.checked = false }) }} /><span>{field.checkboxLabel}</span></div></div> : <input name={field.name} type={field.type ?? 'text'} required={!(pricePreview && ['freightPerUnit', 'freightPercent'].includes(field.name))} min={field.type === 'number' ? '0' : undefined} step={field.type === 'number' ? '0.01' : undefined} defaultValue={field.defaultValue} />}
          </>}
        </label>
      ))}
      {pricePreview && <div className="entry-price-preview">Precio de venta calculado <strong>{currency.format(calculatedPrice ?? 0)}</strong><small>Se calcula con costo + flete seleccionado + margen indicado.</small></div>}
      <div className="entry-actions"><span>{title.toLocaleLowerCase('es-AR').includes('factura') ? 'Se guarda como registro interno; no emite comprobantes fiscales ante ARCA.' : 'La operación se guardará en la base de datos del comercio.'}</span><button className="primary-button" type="submit" disabled={busy}>{busy ? 'Guardando…' : 'Guardar'}</button></div>
    </form>}
    {message && <p className="entry-message" role="status">{message}</p>}
    {cashSummary && !cashReport && endpoint.endsWith('/cash/close') && <div className="cash-close-report" role="status"><strong>Previsión de cierre · {formatDateDisplay(cashSummary.fecha)} · Sesión {textValue(cashSummary.numero_sesion)}</strong><div><span>Efectivo inicial <b>{currency.format(numberValue(cashSummary.saldo_inicial))}</b></span><span>Ventas en efectivo <b>{currency.format(numberValue(cashSummary.ventas_efectivo))}</b></span><span>Ventas por Mercado Pago <b>{currency.format(numberValue(cashSummary.ventas_mercado_pago))}</b></span><span>Pagos/gastos en efectivo <b>{currency.format(numberValue(cashSummary.egresos_efectivo))}</b></span><span>Pagos/gastos por Mercado Pago <b>{currency.format(numberValue(cashSummary.egresos_mercado_pago))}</b></span><span>Efectivo que debe haber <b>{currency.format(numberValue(cashSummary.efectivo_esperado))}</b></span><span>Mercado Pago neto esperado <b>{currency.format(numberValue(cashSummary.mercado_pago_esperado))}</b></span></div></div>}
    {cashReport && endpoint.endsWith('/cash/close') && <div className="cash-close-report" role="status"><strong>Informe del cierre · {formatDateDisplay(cashReport.fecha)} · Sesión {textValue(cashReport.numero_sesion)}</strong><div><span>Efectivo inicial <b>{currency.format(numberValue(cashReport.saldo_inicial))}</b></span><span>Ventas en efectivo <b>{currency.format(numberValue(cashReport.ventas_efectivo))}</b></span><span>Ventas por Mercado Pago <b>{currency.format(numberValue(cashReport.ventas_mercado_pago))}</b></span><span>Pagos/gastos en efectivo <b>{currency.format(numberValue(cashReport.egresos_efectivo))}</b></span><span>Pagos/gastos por Mercado Pago <b>{currency.format(numberValue(cashReport.egresos_mercado_pago))}</b></span><span>Efectivo que debe haber <b>{currency.format(numberValue(cashReport.efectivo_esperado))}</b></span><span>Mercado Pago neto esperado <b>{currency.format(numberValue(cashReport.mercado_pago_esperado))}</b></span><span>Diferencia de efectivo <b>{currency.format(numberValue(cashReport.diferencia))}</b></span></div></div>}
  </section>
}

function StatusPill({ active }: { active: boolean }) {
  return <span className={`status-pill ${active ? 'status-active' : 'status-inactive'}`}>{active ? 'Activo' : 'Inactivo'}</span>
}

function ArticleList() {
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('Todos los rubros')
  const [showInactive, setShowInactive] = useState(false)
  const [editing, setEditing] = useState<ApiRow | null>(null)
  const categoryRows = useDemoApiRows('/api/v1/demo/categories')
  const supplierRows = useDemoApiRows('/api/v1/demo/suppliers')
  const apiRows = useDemoApiRows('/api/v1/demo/articles?inactive=' + showInactive)
  const allRows = useDemoApiRows('/api/v1/demo/articles')
  const articles = (apiRows.length ? apiRows.map((row) => ({
    id: numberValue(row.id),
    code: textValue(row.codigo), name: textValue(row.nombre), category: textValue(row.rubro), stock: numberValue(row.stock),
    unit: textValue(row.unidad), cost: numberValue(row.costo), freight: numberValue(row.flete_unitario), freightPercent: numberValue(row.flete_porcentaje), margin: numberValue(row.margen_porcentaje), round: Boolean(row.redondeo_precio), price: numberValue(row.precio_venta), supplier: textValue(row.proveedor),
  })) : demoArticles.map((article) => ({ ...article, id: 0, freight: 0, freightPercent: 0, margin: 0, round: false }))).sort((a, b) => a.name.localeCompare(b.name, 'es-AR'))
  const categories = [...new Set([...categoryRows.map((row) => textValue(row.nombre)), ...articles.map((article) => article.category)])].sort((a, b) => a.localeCompare(b, 'es-AR'))
  const filtered = articles.filter((article) => {
    const matchesQuery = `${article.code} ${article.name} ${article.category}`.toLocaleLowerCase('es-AR').includes(query.toLocaleLowerCase('es-AR'))
    return matchesQuery && (category === 'Todos los rubros' || article.category === category)
  })

  const articleFields = (row?: ApiRow): EntryField[] => {
    const value = (key: string, fallback = '') => row ? textValue(row[key] ?? fallback) : fallback
    const selectedCategory = value('rubro_id')
    const selectedSupplier = value('proveedor_principal_id')
    const categoryOptions = categoryRows.map((item) => ({ value: String(item.id), label: textValue(item.nombre) }))
    const supplierOptions = supplierRows.map((item) => ({ value: String(item.id), label: textValue(item.nombre) }))
    return [
      { name: 'name', label: 'Nombre del artículo', defaultValue: value('nombre'), ...(!row ? { suggestions: allRows.filter((item) => textValue(item.estado) !== 'INACTIVO').map((item) => textValue(item.nombre)) } : {}) },
      { name: 'code', label: 'Código o código de barras', defaultValue: value('codigo') },
      { name: 'categoryId', label: 'Rubro', valueType: 'number', options: categoryOptions, defaultValue: selectedCategory },
      { name: 'supplierId', label: 'Proveedor principal', valueType: 'number', options: supplierOptions, defaultValue: selectedSupplier },
      { name: 'unit', label: 'Unidad', options: ['UNIDAD', 'KILO', 'METRO', 'LITRO', 'CAJA'].map((item) => ({ value: item, label: item })), defaultValue: value('unidad', 'UNIDAD') },
      { name: 'initialStock', label: row ? 'Cantidad / stock actual' : 'Stock inicial', type: 'number', defaultValue: value('stock', '0') },
      { name: 'minimumStock', label: 'Stock mínimo', type: 'number', defaultValue: value('stock_minimo', '0') },
      { name: 'cost', label: 'Costo del artículo', type: 'number', defaultValue: value('costo', '0') },
      { name: 'freightPerUnit', label: 'Importe de flete por unidad (opcional)', type: 'number', defaultValue: value('flete_unitario', '0'), checkboxName: 'freightByValue', checkboxLabel: 'Aplicar flete fijo', checkedByDefault: Boolean(row && numberValue(row.flete_unitario) > 0) },
      { name: 'freightPercent', label: 'Porcentaje de flete sobre el costo (opcional)', type: 'number', defaultValue: value('flete_porcentaje', '0'), checkboxName: 'freightByPercent', checkboxLabel: 'Aplicar flete porcentual', checkedByDefault: Boolean(row && numberValue(row.flete_porcentaje) > 0) },
      { name: 'marginPercent', label: 'Porcentaje de ganancia', type: 'number', defaultValue: value('margen_porcentaje', '0') },
      { name: 'roundPrice', label: 'Redondear hacia arriba al próximo $10', type: 'checkbox', defaultValue: row && Boolean(row.redondeo_precio) ? 'true' : undefined },
      ...(row ? [{ name: 'salePrice', label: 'Precio de venta', type: 'number', defaultValue: value('precio_venta', '0') }] : []),
    ]
  }
  const refresh = () => window.dispatchEvent(new Event('alon:refresh'))
  const setActive = async (id: number, active: boolean) => {
    const response = await fetch(`/api/v1/operations/articles/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' })
    if (!response.ok) { const body = await response.json().catch(() => ({})); window.alert(body.error ?? 'No se pudo cambiar el estado del artículo.'); return }
    refresh()
  }

  return <>
    {editing ? <LocalEntryPanel key={'edit-' + textValue(editing.id)} title={'Modificar artículo · ' + textValue(editing.nombre)} action="Guardar cambios" endpoint={'/api/v1/operations/articles/' + textValue(editing.id)} method="PUT" onSaved={() => setEditing(null)} initialOpen panelId="article-editor" duplicateNames={allRows.filter((item) => numberValue(item.id) !== numberValue(editing.id)).map((item) => textValue(item.nombre))} fields={articleFields(editing)} /> : <LocalEntryPanel title="Nuevo artículo" action="Cargar artículo" endpoint="/api/v1/operations/articles" pricePreview duplicateNames={allRows.map((row) => textValue(row.nombre))} fields={articleFields()} />}
    {editing && <button className="secondary-button article-cancel-edit" type="button" onClick={() => setEditing(null)}>Cancelar modificación</button>}
    <section className="directory-panel">
      <div className="directory-toolbar"><div><span className="demo-tag">{showInactive ? 'ARTÍCULOS DESACTIVADOS' : 'CATÁLOGO'}</span><strong>{filtered.length} artículos</strong></div><div className="directory-filters"><input aria-label="Buscar artículos" placeholder="Buscar por nombre o código" value={query} onChange={(event) => setQuery(event.target.value)} /><select aria-label="Filtrar por rubro" value={category} onChange={(event) => setCategory(event.target.value)}><option>Todos los rubros</option>{categories.map((item) => <option key={item}>{item}</option>)}</select><button className="secondary-button" type="button" onClick={() => { setShowInactive((value) => !value); setQuery(''); setCategory('Todos los rubros') }}>{showInactive ? 'Ver artículos activos' : 'Ver desactivados'}</button></div></div>
      <div className="table-scroll"><table><thead><tr><th>CÓDIGO</th><th>ARTÍCULO</th><th>SECTOR</th><th>STOCK</th><th>COSTO</th><th>FLETE</th><th>GANANCIA</th><th>PRECIO</th><th>PROVEEDOR</th><th>ACCIONES</th></tr></thead><tbody>{filtered.map((article) => {
        const row = apiRows.find((item) => numberValue(item.id) === article.id)
        return <tr key={article.code}><td className="mono-cell">{article.code}</td><td className="strong-cell">{article.name}</td><td><span className="category-chip">{article.category}</span></td><td><span className={article.stock < 20 ? 'stock-low' : 'stock-ok'}>{article.stock}</span> <span className="muted-cell">{article.unit}</span></td><td>{currency.format(article.cost)}</td><td>{article.freightPercent ? `${article.freightPercent}% del costo` : article.freight ? currency.format(article.freight) : '—'}</td><td>{article.margin}%{article.round ? ' · redondeo $10' : ''}</td><td className="strong-cell">{currency.format(article.price)}</td><td className="mono-cell">{article.supplier}</td><td className="article-actions">{article.id > 0 && <>{!showInactive && <button type="button" className="secondary-button" onClick={() => row && setEditing(row)}>Modificar</button>}<button type="button" className="secondary-button" onClick={() => void setActive(article.id, showInactive)}>{showInactive ? 'Activar' : 'Desactivar'}</button></>}</td></tr>
      })}</tbody></table>{filtered.length === 0 && <div className="no-results">{showInactive ? 'No hay artículos desactivados.' : 'No hay artículos que coincidan con la búsqueda.'}</div>}</div>
      <div className="table-footer">Precios y existencias inventados únicamente para explorar la pantalla.</div>
    </section>
    <ApiTable title="Movimientos de stock · últimos 200" endpoint="/api/v1/demo/stock-movements" columns={[{ key: 'fecha_hora', label: 'FECHA', date: true }, { key: 'codigo', label: 'CÓDIGO' }, { key: 'articulo', label: 'ARTÍCULO' }, { key: 'tipo', label: 'MOVIMIENTO' }, { key: 'cantidad', label: 'CANTIDAD' }, { key: 'referencia', label: 'REFERENCIA' }, { key: 'descripcion', label: 'DETALLE' }]} note="Los movimientos se registran junto con las altas, ventas y compras confirmadas." />
  </>
}

function CategoriesPage() {
  const rows = useDemoApiRows('/api/v1/demo/categories')
  return <>
    <LocalEntryPanel title="Nuevo rubro" action="Crear rubro" endpoint="/api/v1/operations/categories" fields={[{ name: 'name', label: 'Nombre del rubro' }]} />
    <ApiTable title="Rubros disponibles para artículos" endpoint="/api/v1/demo/categories" columns={[{ key: 'codigo', label: 'CÓDIGO' }, { key: 'nombre', label: 'RUBRO' }]} note={`${rows.length} rubros cargados. Los rubros activos aparecen al crear o modificar un artículo.`} />
  </>
}

function CustomerList() {
  const [query, setQuery] = useState('')
  const apiRows = useDemoApiRows('/api/v1/demo/customers')
  const customers = apiRows.length ? apiRows.map((row) => ({
    code: textValue(row.codigo), name: textValue(row.nombre), phone: textValue(row.telefono), email: textValue(row.email),
    currentAccount: numberValue(row.saldo_cuenta_corriente), creditLimit: numberValue(row.limite_credito), status: textValue(row.estado) === 'ACTIVO' ? 'Activo' : 'Inactivo',
  })) : demoCustomers
  const filtered = customers.filter((customer) => `${customer.code} ${customer.name} ${customer.email}`.toLocaleLowerCase('es-AR').includes(query.toLocaleLowerCase('es-AR')))
  return <>
    <LocalEntryPanel title="Nuevo cliente" action="Cargar cliente" endpoint="/api/v1/operations/customers" fields={[{ name: 'name', label: 'Nombre y apellido' }, { name: 'phone', label: 'Teléfono' }, { name: 'email', label: 'Email', type: 'email' }, { name: 'creditLimit', label: 'Límite de crédito', type: 'number' }]} />
    <section className="directory-panel">
      <div className="directory-toolbar"><div><span className="demo-tag">DATOS FICTICIOS</span><strong>{filtered.length} clientes</strong></div><div className="directory-filters"><input aria-label="Buscar clientes" placeholder="Buscar por nombre, código o email" value={query} onChange={(event) => setQuery(event.target.value)} /></div></div>
      <div className="table-scroll"><table><thead><tr><th>CÓDIGO</th><th>CLIENTE</th><th>CONTACTO</th><th>CUENTA CORRIENTE</th><th>LÍMITE DE CRÉDITO</th><th>ESTADO</th></tr></thead><tbody>{filtered.map((customer) => <tr key={customer.code}><td className="mono-cell">{customer.code}</td><td className="strong-cell">{customer.name}</td><td><span className="contact-cell">{customer.phone}<small>{customer.email}</small></span></td><td className={customer.currentAccount > 0 ? 'balance-due' : ''}>{currency.format(customer.currentAccount)}</td><td>{currency.format(customer.creditLimit)}</td><td><StatusPill active={customer.status === 'Activo'} /></td></tr>)}</tbody></table>{filtered.length === 0 && <div className="no-results">No hay clientes que coincidan con la búsqueda.</div>}</div>
      <div className="table-footer">Nombres, teléfonos, emails y saldos inventados para pruebas. Los emails usan el dominio reservado example.test.</div>
    </section>
  </>
}

function SupplierList() {
  const [query, setQuery] = useState('')
  const [showDebtList, setShowDebtList] = useState(false)
  const [selectedSupplier, setSelectedSupplier] = useState<{ id: number; name: string; balance: number } | null>(null)
  const [amount, setAmount] = useState('0')
  const [receipt, setReceipt] = useState<ApiRow | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const receiptFlowRef = useRef<HTMLElement>(null)
  const debtListRef = useRef<HTMLElement>(null)
  const apiRows = useDemoApiRows('/api/v1/demo/suppliers')
  const suppliers = apiRows.length ? apiRows.map((row) => ({
    id: numberValue(row.id), code: textValue(row.codigo), name: textValue(row.nombre), specialty: textValue(row.especialidad), phone: textValue(row.telefono),
    email: textValue(row.email), balance: numberValue(row.saldo_pendiente), status: textValue(row.estado) === 'ACTIVO' ? 'Activo' : 'Inactivo',
  })) : demoSuppliers.map((supplier) => ({ ...supplier, id: 0 }))
  const filtered = suppliers.filter((supplier) => `${supplier.code} ${supplier.name} ${supplier.specialty} ${supplier.email}`.toLocaleLowerCase('es-AR').includes(query.toLocaleLowerCase('es-AR')))
  const owedSuppliers = filtered.filter((supplier) => supplier.balance > 0)
  const startReceipt = (supplier: { id: number; name: string; balance: number }) => { setShowDebtList(true); setSelectedSupplier(supplier); setAmount(supplier.balance.toFixed(2)); setMessage(''); setReceipt(null); window.setTimeout(() => receiptFlowRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 60) }
  const finishReceipt = () => { setReceipt(null); setSelectedSupplier(null); setAmount('0'); setMessage(''); setShowDebtList(true); window.setTimeout(() => debtListRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 60) }
  const printReceipt = () => { window.print(); finishReceipt() }
  const saveReceipt = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); if (!selectedSupplier) return
    const values = new FormData(event.currentTarget)
    const date = dateDisplayToIso(values.get('date'))
    const paymentAmount = Number(amount)
    if (!date) { setMessage('Ingresá una fecha válida con el formato dd/mm/aaaa.'); return }
    if (!(paymentAmount > 0) || paymentAmount > selectedSupplier.balance) { setMessage('El importe debe ser mayor a cero y no puede superar el saldo pendiente.'); return }
    setBusy(true); setMessage('')
    try {
      const response = await fetch('/api/v1/operations/suppliers/receipt', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ supplierId: selectedSupplier.id, date, amount: paymentAmount, paymentMethod: values.get('paymentMethod') }) })
      const body = await response.json().catch(() => ({})) as ApiRow
      if (!response.ok) throw new Error(String(body.message ?? body.error ?? 'No se pudo registrar el recibo.'))
      setReceipt(body); setSelectedSupplier(null); window.dispatchEvent(new Event('alon:refresh'))
      setMessage('Recibo registrado. El pago se aplicó al saldo del proveedor.')
    } catch (error) { setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.') }
    finally { setBusy(false) }
  }
  return <>
    <LocalEntryPanel title="Nuevo proveedor" action="Cargar proveedor" endpoint="/api/v1/operations/suppliers" fields={[{ name: 'name', label: 'Razón social o nombre' }, { name: 'taxId', label: 'CUIT' }, { name: 'specialty', label: 'Rubro o especialidad' }, { name: 'phone', label: 'Teléfono' }, { name: 'email', label: 'Email', type: 'email' }]} />
    <section className="supplier-debt-toolbar"><div><span className="demo-tag">CUENTAS A PAGAR</span><strong>{suppliers.filter((supplier) => supplier.balance > 0).length} proveedores con saldo pendiente</strong></div><button className="primary-button" type="button" onClick={() => setShowDebtList((current) => !current)}>{showDebtList ? 'Ocultar saldos pendientes' : 'Ver proveedores a los que debo'}</button></section>
    {showDebtList && <section className="directory-panel supplier-debt-list" ref={debtListRef}><div className="panel-heading"><div><h2>Proveedores con saldo pendiente</h2><p>Elegí un proveedor para revisar y confirmar el recibo de pago.</p></div></div><div className="table-scroll"><table><thead><tr><th>PROVEEDOR</th><th>SALDO PENDIENTE</th><th></th></tr></thead><tbody>{owedSuppliers.map((supplier) => <tr key={supplier.id || supplier.code}><td className="strong-cell">{supplier.name}</td><td className="balance-due">{currency.format(supplier.balance)}</td><td><button type="button" className="primary-button" disabled={!supplier.id} onClick={() => startReceipt(supplier)}>Hacer recibo</button></td></tr>)}</tbody></table>{owedSuppliers.length === 0 && <div className="no-results">No hay proveedores con saldo pendiente.</div>}</div></section>}
    {selectedSupplier && <section className="entry-panel supplier-receipt-form" ref={receiptFlowRef}><div className="entry-heading"><div><span className="demo-tag">CONFIRMAR RECIBO</span><strong>{selectedSupplier.name}</strong><small>Saldo pendiente: {currency.format(selectedSupplier.balance)}</small></div><button className="select-button" type="button" onClick={() => setSelectedSupplier(null)}>Volver al listado</button></div><p>Revisá el importe y el medio de pago antes de confirmar. Al confirmar, el pago se aplicará a las facturas pendientes más antiguas.</p><form className="entry-form" onSubmit={saveReceipt}><label>Fecha<DateTextInput name="date" /></label><label>Importe a pagar<input aria-label="Importe a pagar" type="number" min="0.01" max={selectedSupplier.balance} step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} required /></label><label>Medio de pago<select name="paymentMethod" defaultValue="EFECTIVO"><option value="EFECTIVO">Efectivo</option><option value="TRANSFERENCIA">Transferencia</option><option value="MERCADO_PAGO">Mercado Pago</option></select></label><div className="entry-actions"><span>El saldo se actualizará al confirmar.</span><button className="primary-button" type="submit" disabled={busy}>{busy ? 'Confirmando…' : 'Confirmar recibo y pago'}</button></div></form></section>}
    {receipt && <section className="supplier-receipt-print"><div className="receipt-heading"><span className="brand-name">alon<span>.</span></span><span>RECIBO DE PAGO</span></div><h2>Recibo {textValue(receipt.recibo)}</h2><p>Proveedor: <strong>{textValue(receipt.proveedor)}</strong></p><p>Fecha: <strong>{formatDateDisplay(receipt.fecha)}</strong></p><p>Medio de pago: <strong>{textValue(receipt.medio_pago)}</strong></p><p>Importe recibido: <strong>{currency.format(numberValue(receipt.importe))}</strong></p><p>Saldo anterior: {currency.format(numberValue(receipt.saldo_anterior))}</p><p>Saldo pendiente: <strong>{currency.format(numberValue(receipt.saldo_restante))}</strong></p><p>Facturas alcanzadas: {textValue(receipt.facturas_aplicadas)}</p><div className="receipt-actions no-print"><button className="primary-button" type="button" onClick={printReceipt}>Imprimir recibo</button><button className="select-button" type="button" onClick={finishReceipt}>No imprimir</button></div></section>}
    {message && <p className="entry-message" role="status">{message}</p>}
    <section className="directory-panel">
      <div className="directory-toolbar"><div><span className="demo-tag">DATOS FICTICIOS</span><strong>{filtered.length} proveedores</strong></div><div className="directory-filters"><input aria-label="Buscar proveedores" placeholder="Buscar por nombre, rubro o código" value={query} onChange={(event) => setQuery(event.target.value)} /></div></div>
      <div className="table-scroll"><table><thead><tr><th>CÓDIGO</th><th>PROVEEDOR</th><th>ESPECIALIDAD</th><th>CONTACTO</th><th>SALDO PENDIENTE</th><th>ESTADO</th><th></th></tr></thead><tbody>{filtered.map((supplier) => <tr key={supplier.code}><td className="mono-cell">{supplier.code}</td><td className="strong-cell">{supplier.name}</td><td><span className="category-chip">{supplier.specialty}</span></td><td><span className="contact-cell">{supplier.phone}<small>{supplier.email}</small></span></td><td className={supplier.balance > 0 ? 'balance-due' : ''}>{currency.format(supplier.balance)}</td><td><StatusPill active={supplier.status === 'Activo'} /></td><td><button className="select-button" type="button" disabled={!supplier.id || supplier.balance <= 0} onClick={() => startReceipt(supplier)}>Recibo</button></td></tr>)}</tbody></table>{filtered.length === 0 && <div className="no-results">No hay proveedores que coincidan con la búsqueda.</div>}</div>
      <div className="table-footer">Datos de contacto y saldos inventados únicamente para pruebas.</div>
    </section>
  </>
}

type ApiColumn = { key: string; label: string; money?: boolean; date?: boolean; dateTime?: boolean; month?: boolean }

function ApiTable({ title, endpoint, columns, note }: { title: string; endpoint: string; columns: ApiColumn[]; note?: string }) {
  const [rows, setRows] = useState<ApiRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    let active = true
    const load = () => fetch(endpoint)
      .then((response) => {
        if (!response.ok) throw new Error('No se pudo cargar el reporte')
        return response.json() as Promise<ApiRow[]>
      })
      .then((data) => { if (active) setRows(data) })
      .catch(() => { if (active) setError(true) })
      .finally(() => { if (active) setLoading(false) })
    const refresh = () => { void load() }
    void load()
    window.addEventListener('alon:refresh', refresh)
    return () => { active = false; window.removeEventListener('alon:refresh', refresh) }
  }, [endpoint])

  return (
    <section className="directory-panel report-table">
      <div className="directory-toolbar"><div><span className="demo-tag">MYSQL DEMO</span><strong>{title}</strong></div><span className="row-count">{loading ? 'Cargando…' : `${rows.length} registros`}</span></div>
      {error ? <div className="no-results">No se pudo conectar con la API local. Revisá que el backend esté iniciado.</div> : <div className="table-scroll"><table><thead><tr>{columns.map((column) => <th key={column.key}>{column.label}</th>)}</tr></thead><tbody>{rows.map((row, index) => <tr key={`${String(row.codigo ?? row.numero ?? row.fecha ?? index)}-${index}`}>{columns.map((column) => <td key={column.key} className={column.money ? 'money-cell' : ''}>{column.money ? currency.format(Number(row[column.key] ?? 0)) : column.dateTime ? formatDateTimeDisplay(row[column.key]) : column.date ? formatDateDisplay(row[column.key]) : column.month ? formatMonthDisplay(row[column.key]) : String(row[column.key] ?? '—')}</td>)}</tr>)}</tbody></table>{!loading && rows.length === 0 && <div className="no-results">Este reporte no tiene registros.</div>}</div>}
      {note && <div className="table-footer">{note}</div>}
    </section>
  )
}

function CashStatusNotice({ onOpenCash }: { onOpenCash: () => void }) {
  const [status, setStatus] = useState<ApiRow | null>(null)
  useEffect(() => {
    fetch(`/api/v1/demo/cash/status?date=${todayLocal()}`).then((response) => {
      if (!response.ok) throw new Error('No se pudo consultar la caja')
      return response.json() as Promise<ApiRow>
    }).then(setStatus).catch(() => setStatus(null))
  }, [])
  if (!status || status.estado === 'ABIERTA') return null
  return <div className="cash-notice"><span>La caja de hoy está {status.estado === 'CERRADA' ? 'cerrada' : 'sin abrir'}. Abrila para poder registrar ventas cobradas.</span><button type="button" onClick={onOpenCash}>Ir a Caja</button></div>
}

function SalesPage({ onOpenCash }: { onOpenCash: () => void }) {
  const customers = useDemoApiRows('/api/v1/demo/customers')
  const articleRows = useDemoApiRows('/api/v1/demo/articles')
  const articles = [...articleRows].sort((a, b) => textValue(a.nombre).localeCompare(textValue(b.nombre), 'es-AR'))
  const consumerFinal = customers.find((row) => textValue(row.nombre).toLocaleLowerCase('es-AR') === 'consumidor final')
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [lines, setLines] = useState<{ articleId: number; label: string; quantity: number; unitPrice: number; discountPercent: number }[]>([])
  const [searchVersion, setSearchVersion] = useState(0)
  const [quantity, setQuantity] = useState('1')
  const [discountPercent, setDiscountPercent] = useState('0')
  const saleFormRef = useRef<HTMLFormElement>(null)
  const articleOptions = articles.map((row) => ({ value: String(row.id), label: textValue(row.codigo) + ' · ' + textValue(row.nombre) + ' · stock ' + textValue(row.stock) + ' · ' + currency.format(numberValue(row.precio_venta)) }))
  useEffect(() => {
    if (!open) return
    const onShortcut = (event: KeyboardEvent) => { if (event.ctrlKey && event.key.toLowerCase() === 'g') { event.preventDefault(); if (!busy) saleFormRef.current?.requestSubmit() } }
    window.addEventListener('keydown', onShortcut)
    return () => window.removeEventListener('keydown', onShortcut)
  }, [open, busy])
  const addLine = (form: HTMLFormElement) => {
    const articleId = Number(form.querySelector<HTMLInputElement>('input[name="articleId"]')?.value ?? 0)
    const quantityValue = Number(quantity)
    const discountValue = Number(discountPercent)
    const article = articles.find((row) => numberValue(row.id) === articleId)
    if (!article) { setMessage('Buscá y elegí un artículo del listado.'); return }
    if (!(quantityValue > 0) || discountValue < 0 || discountValue > 100) { setMessage('Ingresá una cantidad mayor a cero y un descuento entre 0 y 100%.'); return }
    const alreadyAdded = lines.filter((line) => line.articleId === articleId).reduce((sum, line) => sum + line.quantity, 0)
    if (alreadyAdded + quantityValue > numberValue(article.stock)) { setMessage('La cantidad supera el stock disponible de ' + textValue(article.nombre) + '.'); return }
    setLines((current) => [...current, { articleId, label: textValue(article.codigo) + ' · ' + textValue(article.nombre), quantity: quantityValue, unitPrice: numberValue(article.precio_venta), discountPercent: discountValue }])
    setSearchVersion((current) => current + 1)
    setQuantity('1')
    setDiscountPercent('0')
    setMessage('Artículo ingresado a la venta. Podés agregar otro.')
  }
  const submitSale = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (lines.length === 0) { setMessage('Ingresá al menos un artículo antes de guardar la venta.'); return }
    const formElement = event.currentTarget
    const values = new FormData(formElement)
    const date = dateDisplayToIso(values.get('date'))
    if (!date) { setMessage('Ingresá una fecha válida con el formato dd/mm/aaaa.'); return }
    setBusy(true); setMessage('')
    try {
      const response = await fetch('/api/v1/operations/sales', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ customerId: Number(values.get('customerId')), date, saleType: values.get('saleType'), paymentMethod: values.get('paymentMethod'), lines: lines.map(({ articleId, quantity, discountPercent }) => ({ articleId, quantity, discountPercent })) }) })
      const result = await response.json().catch(() => ({})) as { error?: string }
      if (!response.ok) throw new Error(result.error ?? 'No se pudo guardar la venta.')
      window.dispatchEvent(new Event('alon:refresh'))
      setMessage('Venta guardada correctamente con ' + lines.length + ' artículos.')
      setLines([])
      setSearchVersion((current) => current + 1)
      formElement.reset()
      setOpen(false)
    } catch (error) { setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.') }
    finally { setBusy(false) }
  }
  const saleTotal = lines.reduce((sum, line) => sum + Math.round(line.quantity * line.unitPrice * (1 - line.discountPercent / 100) * 100) / 100, 0)
  return <><CashStatusNotice onOpenCash={onOpenCash} /><section className="entry-panel sale-entry">
    <div className="entry-heading"><div><span className="demo-tag">MODO DEMOSTRACIÓN</span><strong>Registrar una venta</strong></div><button className="primary-button" type="button" onClick={() => { setOpen((value) => !value); setMessage('') }}>{open ? 'Cerrar' : '＋ Nueva venta'}</button></div>
    {open && <form className="entry-form" ref={saleFormRef} onSubmit={submitSale}>
      <label>Cliente<select name="customerId" required defaultValue={consumerFinal ? String(consumerFinal.id) : ''}><option value="" disabled>Seleccionar…</option>{customers.map((row) => <option key={String(row.id)} value={String(row.id)}>{textValue(row.nombre)}</option>)}</select></label>
      <label>Fecha<DateTextInput name="date" /></label>
      <label>Tipo de venta<select name="saleType" defaultValue="CONTADO"><option value="CONTADO">Contado</option><option value="CUENTA_CORRIENTE">Cuenta corriente</option></select></label>
      <label>Medio de pago<select name="paymentMethod" defaultValue="EFECTIVO"><option value="EFECTIVO">Efectivo</option><option value="TRANSFERENCIA">Transferencia</option><option value="MERCADO_PAGO">Mercado Pago</option><option value="CUENTA_CORRIENTE">Cuenta corriente</option></select></label>
      <label>Artículo<SearchableEntryField key={searchVersion} autoFocus field={{ name: 'articleId', label: 'Artículo', searchable: true, options: articleOptions }} /></label>
      <label>Cantidad<input type="number" min="0.001" step="0.001" required value={quantity} onChange={(event) => setQuantity(event.target.value)} /></label>
      <label>Descuento %<input type="number" min="0" max="100" step="0.01" required value={discountPercent} onChange={(event) => setDiscountPercent(event.target.value)} /></label>
      <button className="primary-button purchase-add-button" type="button" onClick={(event) => { const form = event.currentTarget.form; if (form) addLine(form) }}>Ingresar</button>
      {lines.length > 0 && <div className="purchase-lines"><strong>Artículos de esta venta · {lines.length}</strong><div className="table-scroll"><table><thead><tr><th>ARTÍCULO</th><th>CANTIDAD</th><th>PRECIO UNITARIO</th><th>DESCUENTO</th><th>SUBTOTAL</th><th></th></tr></thead><tbody>{lines.map((line, index) => <tr key={index}><td>{line.label}</td><td>{line.quantity}</td><td>{currency.format(line.unitPrice)}</td><td>{line.discountPercent}%</td><td>{currency.format(line.quantity * line.unitPrice * (1 - line.discountPercent / 100))}</td><td><button type="button" className="purchase-remove-button" aria-label="Quitar artículo" onClick={() => setLines((current) => current.filter((_, lineIndex) => lineIndex !== index))}>Quitar</button></td></tr>)}</tbody></table></div></div>}
      {lines.length > 0 && <div className="purchase-total">Total de la venta <strong>{currency.format(saleTotal)}</strong></div>}
      <div className="entry-actions"><span>Ingresá todos los artículos y guardá la venta completa al finalizar.</span><button className="primary-button" type="submit" disabled={busy || lines.length === 0}>{busy ? 'Guardando…' : 'Guardar venta'}</button></div>
    </form>}
    {message && <p className="entry-message" role="status">{message}</p>}
  </section><ApiTable title="Ventas de hoy" endpoint={'/api/v1/demo/sales?date=' + todayLocal() + '&size=200'} columns={[
    { key: 'numero', label: 'NÚMERO' }, { key: 'fecha_hora', label: 'FECHA Y HORA', dateTime: true }, { key: 'cliente', label: 'CLIENTE' },
    { key: 'tipo_venta', label: 'TIPO' }, { key: 'medio_pago', label: 'MEDIO DE PAGO' },
    { key: 'total', label: 'TOTAL', money: true },
  ]} note="Se muestran únicamente las ventas registradas hoy." /></>
}

function PurchasesPage() {
  const suppliers = useDemoApiRows('/api/v1/demo/suppliers')
  const articles = useDemoApiRows('/api/v1/demo/articles')
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [lines, setLines] = useState<{ articleId: number; label: string; quantity: number; unitCost: number }[]>([])
  const [searchVersion, setSearchVersion] = useState(0)
  const [quantity, setQuantity] = useState('1')
  const [unitCost, setUnitCost] = useState('0')
  const [extraCosts, setExtraCosts] = useState('0')
  const [paymentType, setPaymentType] = useState<'CONTADO' | 'CUENTA_CORRIENTE'>('CONTADO')
  const purchaseFormRef = useRef<HTMLFormElement>(null)
  const articleOptions = articles.map((row) => ({ value: String(row.id), label: textValue(row.codigo) + ' · ' + textValue(row.nombre) }))
  useEffect(() => {
    if (!open) return
    const onShortcut = (event: KeyboardEvent) => { if (event.ctrlKey && event.key.toLowerCase() === 'g') { event.preventDefault(); if (!busy) purchaseFormRef.current?.requestSubmit() } }
    window.addEventListener('keydown', onShortcut)
    return () => window.removeEventListener('keydown', onShortcut)
  }, [open, busy])
  const addLine = (form: HTMLFormElement) => {
    const articleId = Number(form.querySelector<HTMLInputElement>('input[name="articleId"]')?.value ?? 0)
    const quantityValue = Number(quantity)
    const unitCostValue = Number(unitCost)
    const article = articles.find((row) => numberValue(row.id) === articleId)
    if (!article) { setMessage('Buscá y elegí un artículo del listado.'); return }
    if (!(quantityValue > 0) || unitCostValue < 0) { setMessage('Ingresá una cantidad mayor a cero y un costo válido.'); return }
    setLines((current) => [...current, { articleId, label: textValue(article.codigo) + ' · ' + textValue(article.nombre), quantity: quantityValue, unitCost: unitCostValue }])
    setSearchVersion((current) => current + 1)
    setQuantity('1')
    setUnitCost('0')
    setMessage('Artículo ingresado a la factura. Podés agregar otro.')
  }
  const submitPurchase = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (lines.length === 0) { setMessage('Ingresá al menos un artículo antes de guardar.'); return }
    const formElement = event.currentTarget
    const values = new FormData(formElement)
    const parsedDate = dateDisplayToIso(values.get('date'))
    if (!parsedDate) { setMessage('Ingresá una fecha válida con el formato dd/mm/aaaa.'); return }
    const extraCosts = Number(values.get('extraCosts') ?? 0)
    const subtotal = lines.reduce((sum, line) => sum + line.quantity * line.unitCost, 0)
    const paid = paymentType === 'CONTADO' ? subtotal + extraCosts : Number(values.get('paid') ?? 0)
    if (paid > subtotal + extraCosts) { setMessage('El importe pagado no puede superar el total de la factura.'); return }
    setBusy(true); setMessage('')
    try {
      const response = await fetch('/api/v1/operations/purchases', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ supplierId: Number(values.get('supplierId')), documentType: values.get('documentType'), documentNumber: values.get('documentNumber'), date: parsedDate, lines: lines.map(({ articleId, quantity, unitCost }) => ({ articleId, quantity, unitCost })), extraCosts, paid, paymentType, paymentMethod: values.get('paymentMethod') }) })
      const result = await response.json().catch(() => ({})) as { error?: string }
      if (!response.ok) throw new Error(result.error ?? 'No se pudo guardar la factura.')
      window.dispatchEvent(new Event('alon:refresh'))
      setMessage('Factura guardada correctamente con ' + lines.length + ' artículos.')
      setLines([])
      setExtraCosts('0')
      formElement.reset()
      setSearchVersion((current) => current + 1)
      setOpen(false)
    } catch (error) { setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.') }
    finally { setBusy(false) }
  }
  const invoiceTotal = lines.reduce((sum, line) => sum + line.quantity * line.unitCost, 0) + Number(extraCosts)
  return <><section className="entry-panel purchase-entry">
    <div className="entry-heading"><div><span className="demo-tag">MODO DEMOSTRACIÓN</span><strong>Cargar factura o remito de proveedor</strong></div><button className="primary-button" type="button" onClick={() => { setOpen((value) => { if (!value) setPaymentType('CONTADO'); return !value }); setMessage('') }}>{open ? 'Cerrar' : '＋ Cargar factura'}</button></div>
    {open && <form className="entry-form" ref={purchaseFormRef} onSubmit={submitPurchase}>
      <label>Proveedor<select name="supplierId" required defaultValue=""><option value="" disabled>Seleccionar…</option>{suppliers.map((row) => <option key={String(row.id)} value={String(row.id)}>{textValue(row.nombre)}</option>)}</select></label>
      <label>Tipo de comprobante<select name="documentType" required defaultValue=""><option value="" disabled>Seleccionar…</option><option value="FACTURA">Factura</option><option value="REMITO">Remito</option></select></label>
      <label>Número de comprobante<input name="documentNumber" required /></label>
      <label>Fecha<input name="date" type="text" inputMode="numeric" placeholder="dd/mm/aaaa" pattern="[0-9]{2}/[0-9]{2}/[0-9]{4}" maxLength={10} title="Usá el formato dd/mm/aaaa" required defaultValue={formatDateDisplay(todayLocal())} /></label>
      <label>Artículo recibido<SearchableEntryField key={searchVersion} autoFocus field={{ name: 'articleId', label: 'Artículo recibido', searchable: true, options: articleOptions }} /></label>
      <label>Cantidad<input type="number" min="0.001" step="0.001" required value={quantity} onChange={(event) => setQuantity(event.target.value)} /></label>
      <label>Costo unitario<input type="number" min="0" step="0.01" required value={unitCost} onChange={(event) => setUnitCost(event.target.value)} /></label>
      <button className="primary-button purchase-add-button" type="button" onClick={(event) => { const form = event.currentTarget.form; if (form) addLine(form) }}>Ingresar</button>
      {lines.length > 0 && <div className="purchase-lines"><strong>Artículos de esta factura · {lines.length}</strong><div className="table-scroll"><table><thead><tr><th>ARTÍCULO</th><th>CANTIDAD</th><th>COSTO UNITARIO</th><th>SUBTOTAL</th><th></th></tr></thead><tbody>{lines.map((line, index) => <tr key={index}><td>{line.label}</td><td>{line.quantity}</td><td>{currency.format(line.unitCost)}</td><td>{currency.format(line.quantity * line.unitCost)}</td><td><button type="button" className="purchase-remove-button" aria-label="Quitar artículo" onClick={() => setLines((current) => current.filter((_, lineIndex) => lineIndex !== index))}>Quitar</button></td></tr>)}</tbody></table></div></div>}
      <label>Gastos adicionales<input name="extraCosts" type="number" min="0" step="0.01" value={extraCosts} onChange={(event) => setExtraCosts(event.target.value)} /></label>
      <label>Medio de pago<select name="paymentMethod" defaultValue="EFECTIVO"><option value="EFECTIVO">Efectivo</option><option value="TRANSFERENCIA">Transferencia</option><option value="MERCADO_PAGO">Mercado Pago</option></select></label>
      <fieldset className="purchase-payment-type"><legend>Condición de pago</legend><label className="entry-checkbox-label"><input type="checkbox" name="paymentType" value="CONTADO" checked={paymentType === 'CONTADO'} onChange={() => setPaymentType('CONTADO')} /><span>Contado</span></label><label className="entry-checkbox-label"><input type="checkbox" name="paymentType" value="CUENTA_CORRIENTE" checked={paymentType === 'CUENTA_CORRIENTE'} onChange={() => setPaymentType('CUENTA_CORRIENTE')} /><span>Cuenta corriente</span></label></fieldset>
      {paymentType === 'CUENTA_CORRIENTE' && <label>Importe pagado<input name="paid" type="number" min="0" step="0.01" defaultValue="0" /></label>}
      {lines.length > 0 && <div className="purchase-total">Total estimado de la factura <strong>{currency.format(invoiceTotal)}</strong></div>}
      <div className="entry-actions"><span>Ingresá cada artículo y guardá la factura completa al finalizar.</span><button className="primary-button" type="submit" disabled={busy || lines.length === 0}>{busy ? 'Guardando…' : 'Guardar factura'}</button></div>
    </form>}
    {message && <p className="entry-message" role="status">{message}</p>}
  </section><ApiTable title="Compras registradas" endpoint="/api/v1/demo/purchases" columns={[
    { key: 'numero_documento', label: 'DOCUMENTO' }, { key: 'tipo_documento', label: 'TIPO' }, { key: 'fecha', label: 'FECHA', date: true },
    { key: 'proveedor', label: 'PROVEEDOR' }, { key: 'articulos', label: 'ARTÍCULOS' },
    { key: 'subtotal', label: 'SUBTOTAL', money: true }, { key: 'gastos_adicionales', label: 'GASTOS EXTRA', money: true },
    { key: 'total', label: 'TOTAL', money: true }, { key: 'pagado', label: 'PAGADO', money: true }, { key: 'saldo', label: 'SALDO', money: true },
  ]} note="Una factura puede reunir varios artículos; el stock y los costos se actualizan al guardar." /></>
}

function CashPage() {
  return <><LocalEntryPanel title="Abrir caja" action="Abrir caja" endpoint="/api/v1/operations/cash/open" fields={[{ name: 'date', label: 'Fecha', type: 'date' }, { name: 'openingBalance', label: 'Saldo inicial en efectivo', type: 'number' }]} /><LocalEntryPanel title="Registrar gasto" action="Cargar gasto" endpoint="/api/v1/operations/cash/expenses" fields={[{ name: 'date', label: 'Fecha', type: 'date' }, { name: 'category', label: 'Categoría' }, { name: 'description', label: 'Detalle' }, { name: 'paymentMethod', label: 'Medio de pago', options: [{ value: 'EFECTIVO', label: 'Efectivo' }, { value: 'TRANSFERENCIA', label: 'Transferencia' }, { value: 'MERCADO_PAGO', label: 'Mercado Pago' }] }, { name: 'amount', label: 'Importe', type: 'number' }]} /><LocalEntryPanel title="Cerrar caja" action="Arqueo y cierre" endpoint="/api/v1/operations/cash/close" fields={[{ name: 'date', label: 'Fecha', type: 'date' }, { name: 'countedCash', label: 'Efectivo contado', type: 'number' }]} /><ApiTable title="Historial de cierres de caja" endpoint="/api/v1/demo/reports/cash" columns={[
    { key: 'fecha', label: 'FECHA', date: true }, { key: 'sesion', label: 'SESIÓN' }, { key: 'saldo_inicial', label: 'EFECTIVO INICIAL', money: true },
    { key: 'ventas_efectivo', label: 'VENTAS EN EFECTIVO', money: true }, { key: 'ventas_mercado_pago', label: 'VENTAS MERCADO PAGO', money: true },
    { key: 'pagos_proveedores', label: 'PAGOS A PROVEEDORES', money: true }, { key: 'gastos', label: 'GASTOS', money: true },
    { key: 'efectivo_esperado', label: 'EFECTIVO QUE DEBE HABER', money: true }, { key: 'efectivo_contado', label: 'EFECTIVO CONTADO', money: true },
    { key: 'diferencia', label: 'DIFERENCIA', money: true },
  ]} note="Los cierres y diferencias son ficticios. Mercado Pago se informa por separado del efectivo físico." /></>
}

function ReportsPage() {
  return <div className="report-stack">
    <ApiTable title="Ventas y ganancia bruta por día" endpoint="/api/v1/demo/reports/daily-sales" columns={[
      { key: 'fecha', label: 'FECHA', date: true }, { key: 'ventas', label: 'VENTAS' }, { key: 'total_ventas', label: 'FACTURACIÓN DEMO', money: true },
      { key: 'costo', label: 'COSTO', money: true }, { key: 'descuentos', label: 'DESCUENTOS', money: true },
      { key: 'ganancia_bruta', label: 'GANANCIA BRUTA', money: true }, { key: 'ticket_promedio', label: 'TICKET PROMEDIO', money: true },
    ]} />
    <ApiTable title="Resumen mensual" endpoint="/api/v1/demo/reports/monthly-sales" columns={[
      { key: 'mes', label: 'MES', month: true }, { key: 'ventas', label: 'VENTAS' }, { key: 'total_ventas', label: 'FACTURACIÓN DEMO', money: true },
      { key: 'costo', label: 'COSTO', money: true }, { key: 'ganancia_bruta', label: 'GANANCIA BRUTA', money: true },
    ]} />
    <ApiTable title="Medios de pago" endpoint="/api/v1/demo/reports/payment-methods" columns={[
      { key: 'medio_pago', label: 'MEDIO DE PAGO' }, { key: 'operaciones', label: 'OPERACIONES' }, { key: 'importe', label: 'IMPORTE', money: true },
    ]} />
    <ApiTable title="Ranking de clientes" endpoint="/api/v1/demo/reports/top-customers" columns={[
      { key: 'codigo', label: 'CÓDIGO' }, { key: 'nombre', label: 'CLIENTE' }, { key: 'compras', label: 'COMPRAS' },
      { key: 'total_consumido', label: 'TOTAL CONSUMIDO', money: true }, { key: 'saldo_pendiente', label: 'CUENTA CORRIENTE', money: true },
      { key: 'limite_credito', label: 'LÍMITE', money: true },
    ]} />
    <ApiTable title="Artículos más vendidos" endpoint="/api/v1/demo/reports/top-articles" columns={[
      { key: 'codigo', label: 'CÓDIGO' }, { key: 'nombre', label: 'ARTÍCULO' }, { key: 'rubro', label: 'RUBRO' },
      { key: 'unidades_vendidas', label: 'UNIDADES' }, { key: 'importe_vendido', label: 'VENTAS', money: true }, { key: 'margen_bruto', label: 'MARGEN', money: true },
    ]} />
    <ApiTable title="Compras por artículo y proveedor" endpoint="/api/v1/demo/reports/purchases-by-article" columns={[
      { key: 'codigo', label: 'CÓDIGO' }, { key: 'articulo', label: 'ARTÍCULO' }, { key: 'proveedor', label: 'PROVEEDOR' },
      { key: 'compras', label: 'COMPRAS' }, { key: 'unidades_compradas', label: 'UNIDADES' }, { key: 'importe', label: 'IMPORTE', money: true },
    ]} />
    <ApiTable title="Saldos de proveedores" endpoint="/api/v1/demo/reports/supplier-balances" columns={[
      { key: 'codigo', label: 'CÓDIGO' }, { key: 'nombre', label: 'PROVEEDOR' }, { key: 'especialidad', label: 'ESPECIALIDAD' },
      { key: 'saldo_pendiente', label: 'SALDO PENDIENTE', money: true }, { key: 'estado', label: 'ESTADO' },
    ]} />
    <ApiTable title="Gastos por día y categoría" endpoint="/api/v1/demo/reports/expenses" columns={[
      { key: 'fecha', label: 'FECHA', date: true }, { key: 'categoria', label: 'CATEGORÍA' }, { key: 'movimientos', label: 'MOVIMIENTOS' }, { key: 'total', label: 'TOTAL', money: true },
    ]} />
    <ApiTable title="Artículos bajo stock mínimo" endpoint="/api/v1/demo/reports/low-stock" columns={[
      { key: 'codigo', label: 'CÓDIGO' }, { key: 'nombre', label: 'ARTÍCULO' }, { key: 'rubro', label: 'RUBRO' },
      { key: 'stock', label: 'STOCK' }, { key: 'stock_minimo', label: 'MÍNIMO' },
    ]} />
  </div>
}

function UsersPage() {
  const users = useDemoApiRows('/api/v1/auth/users')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [role, setRole] = useState('VENDEDOR')
  const [permissions, setPermissions] = useState<string[]>(['ventas', 'clientes', 'caja'])
  const [editing, setEditing] = useState<ApiRow | null>(null)
  const [editRole, setEditRole] = useState('VENDEDOR')
  const [editPermissions, setEditPermissions] = useState<string[]>(['ventas', 'clientes', 'caja'])
  const accessOptions = navigation.filter((item) => item.id !== 'usuarios').map(({ id, label }) => ({ id, label }))
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); const formElement = event.currentTarget; const values = new FormData(formElement); setBusy(true); setMessage('')
    try {
      const response = await fetch('/api/v1/auth/users', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: values.get('username'), password: values.get('password'), role, permissions }) })
      const body = await response.json().catch(() => ({})) as Record<string, unknown>
      if (!response.ok) throw new Error(String(body.message ?? body.error ?? 'No se pudo crear el usuario.'))
      formElement.reset(); window.dispatchEvent(new Event('alon:refresh')); setMessage('Usuario creado correctamente.')
    } catch (error) { setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.') }
    finally { setBusy(false) }
  }
  const toggleActive = async (id: unknown, active: boolean) => {
    const response = await fetch(`/api/v1/auth/users/${id}/active`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ active: !active }) })
    if (response.ok) window.dispatchEvent(new Event('alon:refresh'))
  }
  const startEditing = (user: ApiRow) => {
    const currentRole = textValue(user.rol)
    setEditing(user); setEditRole(currentRole)
    setEditPermissions(currentRole === 'DUENO' ? accessOptions.map((option) => option.id) : currentRole === 'VENDEDOR' ? ['ventas', 'clientes', 'caja'] : textValue(user.permisos).split(',').filter(Boolean))
  }
  const saveEdit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); if (!editing) return
    const values = new FormData(event.currentTarget); setBusy(true); setMessage('')
    try {
      const response = await fetch(`/api/v1/auth/users/${editing.id}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: values.get('username'), password: values.get('password'), role: editRole, permissions: editPermissions }) })
      const body = await response.json().catch(() => ({})) as Record<string, unknown>
      if (!response.ok) throw new Error(String(body.message ?? body.error ?? 'No se pudieron guardar los cambios.'))
      setEditing(null); window.dispatchEvent(new Event('alon:refresh')); setMessage('Usuario actualizado.')
    } catch (error) { setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.') }
    finally { setBusy(false) }
  }
  return <><section className="entry-panel"><div className="entry-heading"><div><span className="demo-tag">ADMINISTRACIÓN</span><strong>Crear usuario</strong></div></div>
    <form className="entry-form" onSubmit={submit}>
      <label>Usuario<input name="username" minLength={3} required /></label><label>Contraseña<input name="password" type="password" minLength={4} autoComplete="new-password" required /></label>
      <label>Perfil<select value={role} onChange={(event) => { const next = event.target.value; setRole(next); setPermissions(next === 'VENDEDOR' ? ['ventas', 'clientes', 'caja'] : next === 'DUENO' ? accessOptions.map((option) => option.id) : []) }}><option value="VENDEDOR">Vendedor</option><option value="DUENO">Dueño</option><option value="PERSONALIZADO">Accesos personalizados</option></select></label>
      {role !== 'VENDEDOR' && role !== 'DUENO' && <fieldset className="permission-choices"><legend>Secciones permitidas</legend>{accessOptions.map(({ id, label }) => <label key={id}><input type="checkbox" checked={permissions.includes(id)} onChange={(event) => setPermissions((current) => event.target.checked ? [...current, id] : current.filter((permission) => permission !== id))} />{label}</label>)}</fieldset>}
      <div className="entry-actions"><span>La contraseña debe tener al menos 4 caracteres.</span><button className="primary-button" type="submit" disabled={busy || (role === 'PERSONALIZADO' && permissions.length === 0)}>{busy ? 'Guardando…' : 'Crear usuario'}</button></div>
    </form>{message && <p className="entry-message" role="status">{message}</p>}</section>
    <ApiTable title="Usuarios del comercio" endpoint="/api/v1/auth/users" columns={[{ key: 'nombre', label: 'NOMBRE' }, { key: 'usuario', label: 'USUARIO' }, { key: 'rol', label: 'PERFIL' }, { key: 'permisos', label: 'ACCESOS' }, { key: 'activo', label: 'ACTIVO' }]} />
    {editing && <section className="entry-panel"><div className="entry-heading"><div><span className="demo-tag">EDITAR ACCESO</span><strong>{textValue(editing.usuario)}</strong></div><button className="select-button" type="button" onClick={() => setEditing(null)}>Cancelar</button></div><form className="entry-form" onSubmit={saveEdit}>
      <label>Usuario<input name="username" defaultValue={textValue(editing.usuario)} minLength={3} required /></label><label>Nueva contraseña<input name="password" type="password" minLength={4} autoComplete="new-password" placeholder="Dejar vacía para mantenerla" /></label>
      <label>Perfil<select value={editRole} onChange={(event) => { const next = event.target.value; setEditRole(next); setEditPermissions(next === 'VENDEDOR' ? ['ventas', 'clientes', 'caja'] : next === 'DUENO' ? accessOptions.map((option) => option.id) : []) }}><option value="VENDEDOR">Vendedor</option><option value="DUENO">Dueño</option><option value="PERSONALIZADO">Accesos personalizados</option></select></label>
      {editRole !== 'VENDEDOR' && editRole !== 'DUENO' && <fieldset className="permission-choices"><legend>Secciones permitidas</legend>{accessOptions.map(({ id, label }) => <label key={id}><input type="checkbox" checked={editPermissions.includes(id)} onChange={(event) => setEditPermissions((current) => event.target.checked ? [...current, id] : current.filter((permission) => permission !== id))} />{label}</label>)}</fieldset>}
      <div className="entry-actions"><span>Solo se modifica la contraseña si escribís una nueva.</span><button className="primary-button" type="submit" disabled={busy || (editRole === 'PERSONALIZADO' && editPermissions.length === 0)}>{busy ? 'Guardando…' : 'Guardar cambios'}</button></div>
    </form></section>}
    <section className="panel user-actions-list"><div className="panel-heading"><div><h2>Administrar accesos</h2><p>Editá usuario, contraseña o permisos. También podés desactivar el acceso.</p></div></div>{users.map((user) => <div className="user-access-row" key={String(user.id)}><span><strong>{textValue(user.usuario)}</strong><small>{textValue(user.rol)} · {user.activo === true || user.activo === 1 || user.activo === '1' ? 'Activo' : 'Desactivado'}</small></span><div className="user-row-actions"><button className="select-button" type="button" onClick={() => startEditing(user)}>Editar</button><button className="select-button" type="button" onClick={() => void toggleActive(user.id, user.activo === true || user.activo === 1 || user.activo === '1')}>{user.activo === true || user.activo === 1 || user.activo === '1' ? 'Desactivar' : 'Activar'}</button></div></div>)}</section></>
}

function StorePage() {
  return <div className="store-module">
    <LocalEntryPanel title="Registrar pedido web" action="Cargar pedido" endpoint="/api/v1/operations/store-orders" fields={[{ name: 'customerName', label: 'Nombre del cliente' }, { name: 'contact', label: 'Teléfono o WhatsApp' }, { name: 'details', label: 'Artículos solicitados' }, { name: 'estimatedTotal', label: 'Total estimado', type: 'number' }]} />
    <ApiTable title="Pedidos web pendientes de confirmar" endpoint="/api/v1/demo/store-orders" columns={[{ key: 'id', label: 'NÚMERO' }, { key: 'nombre_cliente', label: 'CLIENTE' }, { key: 'contacto', label: 'CONTACTO' }, { key: 'detalle', label: 'PEDIDO' }, { key: 'total_estimado', label: 'TOTAL ESTIMADO', money: true }, { key: 'estado', label: 'ESTADO' }, { key: 'creado_en', label: 'FECHA', date: true }]} note="Los pedidos son solicitudes; no descuentan stock ni se convierten en ventas hasta confirmarlos." />
    <section className="module-placeholder"><div className="placeholder-icon">⌑</div><p className="eyebrow">CATÁLOGO ONLINE</p><h2>Pedidos coordinados con tu comercio</h2><p>Los pedidos web se mantienen como solicitudes y no descuentan stock ni generan una venta hasta que el negocio los confirme.</p></section>
  </div>
}

type SessionUser = Record<string, unknown>
function userPermissions(user: SessionUser) { return user.rol === 'DUENO' ? navigation.map((item) => item.id) : textValue(user.permisos).split(',').filter(Boolean) }

function MainApp({ user, onLogout }: { user: SessionUser; onLogout: () => void }) {
  const permissions = userPermissions(user)
  const visibleNavigation = navigation.filter((item) => permissions.includes(item.id))
  const initialSection = (permissions.includes('inicio') ? 'inicio' : visibleNavigation[0]?.id ?? 'ventas') as Section
  const [activeSection, setActiveSection] = useState<Section>(initialSection)
  const [summary, setSummary] = useState<ApiRow | null>(null)
  const activeItem = visibleNavigation.find((item) => item.id === activeSection) ?? visibleNavigation[0]

  useEffect(() => {
    if (!permissions.includes('inicio')) return
    const load = () => fetch('/api/v1/demo/summary')
      .then((response) => { if (!response.ok) throw new Error('API unavailable'); return response.json() as Promise<ApiRow> })
      .then(setSummary)
      .catch(() => setSummary(null))
    const refresh = () => { void load() }
    void load()
    window.addEventListener('alon:refresh', refresh)
    return () => window.removeEventListener('alon:refresh', refresh)
  }, [])

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#inicio" onClick={() => setActiveSection('inicio')} aria-label="Alon, inicio"><span className="brand-mark">a</span><span className="brand-name">alon<span>.</span></span></a>
        <div className="store-switcher"><span className="store-avatar">M</span><span className="store-copy"><strong>Mi comercio</strong><small>Plan inicial</small></span><span className="chevron">⌄</span></div>
        <p className="nav-caption">GESTIÓN</p>
        <nav aria-label="Navegación principal">{visibleNavigation.map((item) => <button className={`nav-item ${activeSection === item.id ? 'active' : ''}`} key={item.id} onClick={() => setActiveSection(item.id)} type="button"><span className="nav-icon" aria-hidden="true">{item.icon}</span><span>{item.label}</span>{item.id === 'caja' && <span className="nav-dot" title="Caja cerrada" />}</button>)}</nav>
        <div className="sidebar-bottom"><div className="help-card"><span>Sesión iniciada</span><small>{textValue(user.nombre)} · {textValue(user.rol)}</small><button type="button" onClick={onLogout}>Cerrar sesión</button></div><button className="user-profile" type="button"><span className="user-avatar">{textValue(user.nombre).slice(0,1).toLocaleUpperCase()}</span><span className="user-copy"><strong>{textValue(user.nombre)}</strong><small>{textValue(user.usuario)}</small></span></button></div>
      </aside>
      <main className="main-content">
        <header className="topbar"><div className="breadcrumb">Mi comercio <span>/</span> <strong>{activeItem.label}</strong></div><div className="topbar-actions"><span className="today-label">{new Intl.DateTimeFormat('es-AR', { dateStyle: 'full' }).format(new Date())}</span><button className="select-button" type="button" onClick={onLogout}>Cambiar de usuario</button><span className="top-avatar">{textValue(user.nombre).slice(0,1).toLocaleUpperCase()}</span></div></header>
        <div className="page-wrap">
          <section className="welcome-row"><div><p className="eyebrow">TU NEGOCIO, MÁS SIMPLE</p><h1>{activeSection === 'inicio' ? 'Buen día 👋' : activeItem.label}</h1><p className="page-subtitle">{descriptions[activeSection]}</p></div>{activeSection === 'inicio' && <button className="primary-button" type="button" onClick={() => setActiveSection('ventas')}><span>＋</span> Nueva venta</button>}</section>
          {activeSection === 'inicio' ? (
            <>
              <div className="notice"><span className="notice-icon">✦</span><span><strong>{summary ? 'Base de demostración conectada' : 'Conectando con la base de prueba'}</strong> — 1.000 ventas en 10 días, artículos, clientes, proveedores, caja y compras ficticios.</span><span className="notice-badge">DEMO</span></div>
              <section className="metrics-grid" aria-label="Datos ficticios cargados">
                <article className="metric-card"><div className="metric-heading"><span>Artículos de prueba</span><span className="metric-icon mint">▦</span></div><strong className="metric-value">{String(summary?.articulos ?? demoArticles.length)}</strong><span className="metric-foot">Ferretería · varios rubros</span></article>
                <article className="metric-card"><div className="metric-heading"><span>Ventas ficticias</span><span className="metric-icon lilac">↗</span></div><strong className="metric-value">{String(summary?.ventas ?? '1.000')}</strong><span className="metric-foot">En {String(summary?.dias ?? 10)} días</span></article>
                <article className="metric-card"><div className="metric-heading"><span>Facturación demo</span><span className="metric-icon peach">▤</span></div><strong className="metric-value metric-money">{currency.format(Number(summary?.facturacion ?? 0))}</strong><span className="metric-foot">Importe ficticio · ARS</span></article>
                <article className="metric-card"><div className="metric-heading"><span>Ganancia bruta demo</span><span className="metric-icon yellow">⌁</span></div><strong className="metric-value metric-money">{currency.format(Number(summary?.margen_bruto ?? 0))}</strong><span className="metric-foot">Antes de gastos operativos</span></article>
              </section>
              <section className="content-grid">
                <article className="panel sales-panel"><div className="panel-heading"><div><h2>Movimiento de ventas</h2><p>{String(summary?.ventas ?? '1.000')} operaciones de prueba en 10 días.</p></div><button className="select-button" type="button" onClick={() => setActiveSection('informes')}>Ver reportes <span>↗</span></button></div><div className="chart-empty"><div className="chart-lines"><span /><span /><span /><span /></div><div className="chart-message"><span className="chart-symbol">↗</span><strong>{summary ? currency.format(Number(summary.facturacion ?? 0)) : 'Cargando ventas…'}</strong><small>Facturación ficticia · comprobantes no fiscales</small></div><div className="chart-days"><span>10 días</span><span>100 ventas por día</span></div></div></article>
                <article className="panel quick-panel"><div className="panel-heading"><div><h2>Explorar datos de prueba</h2><p>Listados y reportes de la base MySQL</p></div></div><div className="quick-actions"><button type="button" onClick={() => setActiveSection('ventas')}><span className="quick-icon violet">↗</span><span><strong>Ver ventas</strong><small>1.000 operaciones · últimos 10 días</small></span><b>›</b></button><button type="button" onClick={() => setActiveSection('caja')}><span className="quick-icon green">▣</span><span><strong>Ver cierres de caja</strong><small>10 arqueos con diferencias demo</small></span><b>›</b></button><button type="button" onClick={() => setActiveSection('compras')}><span className="quick-icon blue">↙</span><span><strong>Ver compras</strong><small>10 proveedores · 100 artículos</small></span><b>›</b></button></div></article>
              </section>
              <footer className="page-footer"><span>Alon · Gestión comercial simple</span><span>Datos inventados para pruebas; no corresponden a personas ni negocios reales.</span></footer>
            </>
          ) : activeSection === 'articulos' ? <ArticleList /> : activeSection === 'rubros' ? <CategoriesPage /> : activeSection === 'clientes' ? <CustomerList /> : activeSection === 'proveedores' ? <SupplierList /> : activeSection === 'ventas' ? <SalesPage onOpenCash={() => setActiveSection('caja')} /> : activeSection === 'compras' ? <PurchasesPage /> : activeSection === 'caja' ? <CashPage /> : activeSection === 'informes' ? <ReportsPage /> : activeSection === 'tienda' ? <StorePage /> : activeSection === 'usuarios' ? <UsersPage /> : (
            <section className="module-placeholder"><div className="placeholder-icon">{activeItem.icon}</div><p className="eyebrow">MÓDULO DE ALON</p><h2>{activeItem.label}</h2><p>{descriptions[activeSection]}</p><span className="coming-soon">Preparando este espacio para tu comercio</span></section>
          )}
        </div>
      </main>
    </div>
  )
}

function AuthScreen({ setup, onAuthenticated }: { setup: boolean; onAuthenticated: (user: SessionUser) => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setBusy(true); setError('')
    const form = new FormData(event.currentTarget)
    try {
      const username = form.get('username')
      const response = await fetch(setup ? '/api/v1/auth/setup' : '/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, name: form.get('name') || username, password: form.get('password') }) })
      const body = await response.json().catch(() => ({})) as SessionUser
      if (!response.ok) throw new Error(textValue(body.message ?? body.error ?? 'No se pudo iniciar sesión.'))
      onAuthenticated(body)
    } catch (e) { setError(e instanceof Error ? e.message : 'No se pudo conectar con el servidor.') }
    finally { setBusy(false) }
  }
  return <main className="auth-shell"><section className="auth-card"><span className="brand-name">alon<span>.</span></span><p className="eyebrow">GESTIÓN COMERCIAL</p><h1>{setup ? 'Configurá tu comercio' : 'Iniciar sesión'}</h1><p>{setup ? 'Creá la cuenta dueña para administrar el sistema y sus usuarios.' : 'Ingresá con tu usuario y contraseña.'}</p><form className="entry-form" onSubmit={submit}>{setup && <label>Nombre del dueño<input name="name" autoComplete="name" /></label>}<label>Usuario<input name="username" autoComplete="username" minLength={3} required /></label><label>Contraseña<input name="password" type="password" autoComplete={setup ? 'new-password' : 'current-password'} minLength={setup ? 4 : undefined} required /></label><button className="primary-button" type="submit" disabled={busy}>{busy ? 'Ingresando…' : setup ? 'Crear cuenta dueña' : 'Ingresar'}</button></form>{error && <p className="entry-message" role="alert">{error}</p>}</section></main>
}

function App() {
  const [status, setStatus] = useState<{ setupRequired: boolean; user: SessionUser } | null>(null)
  useEffect(() => { fetch('/api/v1/auth/status').then((r) => r.json()).then((body) => setStatus(body as { setupRequired: boolean; user: SessionUser })).catch(() => setStatus({ setupRequired: false, user: {} })) }, [])
  const logout = async () => { await fetch('/api/v1/auth/logout', { method: 'POST' }); setStatus((current) => current ? { ...current, user: {} } : current) }
  if (!status) return <main className="auth-shell"><p>Conectando con Alon…</p></main>
  if (!status.user || Object.keys(status.user).length === 0) return <AuthScreen setup={status.setupRequired} onAuthenticated={(user) => setStatus((current) => current ? { ...current, setupRequired: false, user } : current)} />
  return <MainApp key={textValue(status.user.usuario)} user={status.user} onLogout={() => void logout()} />
}

export default App
