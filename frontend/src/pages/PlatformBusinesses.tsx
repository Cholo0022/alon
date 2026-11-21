import { useEffect, useRef, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import './platform-businesses.css'
import { BusinessInstallationPanel } from './BusinessInstallationPanel'
import { BusinessReceipts, BusinessAudit } from './BusinessReceipts'
import { BusinessStateActions } from './BusinessStateActions'
import { businessStates as states } from '../lib/businessStates'
import { monthNumber, rateAt, type PlatformRate } from '../lib/platformBilling'

const today = () =>
  new Date().toLocaleDateString('en-CA', { timeZone: 'America/Argentina/Buenos_Aires' })
const money = (n: unknown) =>
  Number(n || 0).toLocaleString('es-AR', { style: 'currency', currency: 'ARS' })
const blank = () => ({
  nombre: '',
  razon_social: '',
  cuit: '',
  rubro: '',
  localidad: '',
  responsable: '',
  telefono: '',
  email: '',
  plan: 'Inicial',
  abono: '0',
  cargo_configuracion: '0',
  estado: 'PRUEBA',
  alta: today(),
  vencimiento: '',
  gestion_url: '',
  tienda_url: '',
  base_referencia: '',
  respaldo: '',
  notas: '',
})
type Draft = ReturnType<typeof blank>
type Business = Omit<Draft, 'vencimiento' | 'respaldo' | 'cargo_configuracion'> & {
  cargo_configuracion?: number
  instalacion_estado?: string | null
  gerente_usuario?: string | null
  alta_token?: string
  estado_objetivo?: string | null
  estado_aplicacion?: string
  estado_error?: string
  baja_paso?: string
  estado_motivo?: string
  eliminado_en?: string | null
  baja_iniciada_en?: string | null
  id: number
  saldo: number
  vencimiento: string | null
  respaldo: string | null
}
type Movement = {
  id: number
  negocio_id: number
  nombre: string
  tipo: string
  fecha: string
  importe: number
  concepto: string
  anulado?: boolean
}
type ServiceReceipt = {
  negocio_id: number
  fecha: string
  periodo_desde: string
  meses: number
  cargo_configuracion: number
  total: number
}
type Report = {
  businesses: Business[]
  movements: Movement[]
  receipts?: ServiceReceipt[]
  rates?: PlatformRate[]
}

function monthLabel(value: number) {
  return Number.isFinite(value)
    ? String((value % 12) + 1).padStart(2, '0') + '/' + Math.floor(value / 12)
    : 'Sin períodos cubiertos'
}
const detailSections = ['Resumen', 'Cobros', 'Servicio', 'Historial'] as const

const fields: {
  key: keyof Draft
  label: string
  type?: string
  max?: number
  required?: boolean
}[] = [
  { key: 'nombre', label: 'Nombre comercial', required: true, max: 160 },
  { key: 'razon_social', label: 'Razón social', max: 160 },
  { key: 'cuit', label: 'CUIT (11 números)', max: 11 },
  { key: 'rubro', label: 'Rubro', max: 100 },
  { key: 'localidad', label: 'Ciudad y provincia', max: 160 },
  { key: 'responsable', label: 'Responsable', max: 160 },
  { key: 'telefono', label: 'Teléfono del responsable', max: 40 },
  { key: 'email', label: 'Correo del responsable', type: 'email', max: 254 },
  { key: 'plan', label: 'Plan contratado', required: true, max: 80 },
  {
    key: 'cargo_configuracion',
    label: 'Configuración por única vez (ARS)',
    type: 'number',
    required: true,
  },
  { key: 'abono', label: 'Abono mensual (ARS)', type: 'number', required: true },
  { key: 'alta', label: 'Fecha de alta', type: 'date', required: true },
  { key: 'base_referencia', label: 'Referencia de base de datos (sin contraseñas)', max: 160 },
  { key: 'respaldo', label: 'Último respaldo confirmado (manual)', type: 'date' },
]

const pendingRegistrationKey = 'alon.pending-business-registration.v1'
type PendingRegistration = {
  token: string
  draft: Draft
  manager: { username: string; name: string; email: string; months: number }
}
function readPendingRegistration(): PendingRegistration | null {
  try {
    const value = JSON.parse(localStorage.getItem(pendingRegistrationKey) ?? 'null')
    return value &&
      /^[0-9a-f-]{36}$/i.test(value.token) &&
      value.draft?.nombre &&
      value.manager?.username
      ? value
      : null
  } catch {
    return null
  }
}
export function PlatformBusinesses() {
  const [pendingRegistration, setPendingRegistration] = useState(readPendingRegistration)
  const [registrationLocked, setRegistrationLocked] = useState(Boolean(pendingRegistration))
  const [upcomingLimit] = useState(() =>
    new Date(Date.now() + 30 * 86400000).toLocaleDateString('en-CA', {
      timeZone: 'America/Argentina/Buenos_Aires',
    }),
  )
  const [report, setReport] = useState<Report | null>(null)
  const [revision, setRevision] = useState(0)
  const [draft, setDraft] = useState<Draft | null>(pendingRegistration?.draft ?? null)
  const [editing, setEditing] = useState<number | null>(null)
  const [selected, setSelected] = useState<number | null>(null)
  const [installationBusiness, setInstallationBusiness] = useState<Business | null>(null)
  const editorOpen = draft !== null
  const clientDialog = useRef<HTMLDialogElement>(null)
  const detail = useRef<HTMLElement>(null)
  const editor = useRef<HTMLElement>(null)
  const installation = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (selected !== null) {
      detail.current?.scrollIntoView?.({ block: 'start', behavior: 'smooth' })
      detail.current?.focus({ preventScroll: true })
    }
  }, [selected])
  useEffect(() => {
    if (editorOpen) editor.current?.scrollIntoView?.({ block: 'start', behavior: 'smooth' })
  }, [editorOpen, editing])
  useEffect(() => {
    if (installationBusiness)
      installation.current?.scrollIntoView?.({ block: 'start', behavior: 'smooth' })
  }, [installationBusiness])
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [focus, setFocus] = useState('')
  const [detailSection, setDetailSection] = useState<(typeof detailSections)[number]>('Resumen')
  const [month, setMonth] = useState(today().slice(0, 7))
  const [effectiveMonth, setEffectiveMonth] = useState(today().slice(0, 7))
  const [message, setMessage] = useState('')
  const [createdLinks, setCreatedLinks] = useState<{
    managementUrl: string
    shopUrl: string
  } | null>(null)
  const [busy, setBusy] = useState(false)
  const [registrationToken, setRegistrationToken] = useState(
    () => pendingRegistration?.token ?? crypto.randomUUID(),
  )
  const [manager, setManager] = useState({
    username: pendingRegistration?.manager.username ?? '',
    name: pendingRegistration?.manager.name ?? '',
    email: pendingRegistration?.manager.email ?? '',
    password: '',
    months: pendingRegistration?.manager.months ?? 1,
  })
  const [movement, setMovement] = useState({
    tipo: 'COBRO',
    fecha: today(),
    importe: '',
    concepto: '',
  })
  useEffect(() => {
    const controller = new AbortController()
    void apiFetch('/api/v1/super/businesses', { signal: controller.signal })
      .then(async (response) => {
        if (response.status === 404)
          throw new Error(
            'El servidor de Alon necesita actualizarse. Reiniciá Alon con la versión preparada para habilitar el registro de negocios.',
          )
        const body = await response.json()
        if (!response.ok) throw new Error(body.error ?? 'No se pudo cargar el informe.')
        if (!controller.signal.aborted) setReport(body)
      })
      .catch((error) => {
        if (!controller.signal.aborted) setMessage(error.message)
      })
    return () => controller.abort()
  }, [revision])
  const save = async (event: FormEvent, path: string, body: object, method = 'POST') => {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch('/api/v1/super/businesses' + path, {
        method,
        retry: false,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      if (response.status === 404)
        throw new Error(
          'El servidor no tiene disponible el registro de negocios. Reiniciá Alon con la versión actualizada. Los datos del formulario se conservaron.',
        )
      const result = await response.json()
      if (!response.ok) throw new Error(result.error ?? 'No se pudo guardar.')
      if (path === '' && result.managementUrl && result.shopUrl) {
        setCreatedLinks({ managementUrl: result.managementUrl, shopUrl: result.shopUrl })
        setSelected(Number(result.id))
      }
      setRevision((n) => n + 1)
      setDraft(null)
      setMovement({ tipo: 'COBRO', fecha: today(), importe: '', concepto: '' })
      setMessage('Datos guardados.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar.')
    } finally {
      setBusy(false)
    }
  }
  const confirmBusiness = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy || !draft || !event.currentTarget.reportValidity()) return
    const pending = pendingRegistration ?? {
      token: registrationToken,
      draft: draft.estado === 'PRUEBA' ? { ...draft, abono: '0' } : draft,
      manager: {
        username: manager.username,
        name: manager.name,
        email: manager.email,
        months: manager.months,
      },
    }
    try {
      localStorage.setItem(pendingRegistrationKey, JSON.stringify(pending))
    } catch {
      setMessage(
        'No se pudo conservar la referencia del alta. Habilitá el almacenamiento del navegador antes de confirmar.',
      )
      return
    }
    setPendingRegistration(pending)
    setRegistrationLocked(true)
    setBusy(true)
    setMessage(
      'Preparando la base, el gerente y la aplicación. Esperá la confirmación antes de cerrar esta pantalla.',
    )
    try {
      const response = await apiFetch('/api/v1/super/businesses/confirm', {
        method: 'POST',
        retry: false,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          requestId: registrationToken,
          business: {
            ...pending.draft,
            abono: Number(pending.draft.abono),
            cargo_configuracion:
              pending.draft.estado === 'ACTIVO' && pending.draft.cargo_configuracion !== undefined
                ? Number(pending.draft.cargo_configuracion)
                : undefined,
            vencimiento: draft.vencimiento || null,
            respaldo: draft.respaldo || null,
          },
          manager,
        }),
      })
      const result = await response.json()
      if (result.id) {
        setRevision((current) => current + 1)
      }
      if (!response.ok && !result.id && [400, 403, 404, 422].includes(response.status)) {
        localStorage.removeItem(pendingRegistrationKey)
        setPendingRegistration(null)
        setRegistrationLocked(false)
      }
      if (response.status === 404)
        throw new Error(
          'El servidor necesita actualizarse para confirmar el negocio con su gerente. Reiniciá Alon. Los datos del formulario se conservaron.',
        )
      if (!response.ok) throw new Error(result.error ?? 'No se pudo completar el alta del negocio.')
      if (result.running !== true || !result.managementUrl || !result.shopUrl)
        throw new Error(
          'El servidor no confirmó que la aplicación esté lista. Reintentá la confirmación con los mismos datos.',
        )
      setCreatedLinks({ managementUrl: result.managementUrl, shopUrl: result.shopUrl })
      setSelected(Number(result.id))
      localStorage.removeItem(pendingRegistrationKey)
      setPendingRegistration(null)
      setRegistrationLocked(false)
      setRegistrationToken(crypto.randomUUID())
      setManager({ username: '', name: '', email: '', password: '', months: 1 })
      setDraft(null)
      setMessage(
        'Negocio listo. Se crearon la base y el gerente con todos los permisos de administración del negocio.',
      )
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : 'No se pudo conectar. Reintentá con los mismos datos; el intento conserva su referencia para evitar duplicados.',
      )
    } finally {
      setBusy(false)
    }
  }
  const businesses = report?.businesses ?? []
  const movements = report?.movements ?? []
  const current = businesses.find((b) => b.id === selected)
  const dialogOpen = Boolean(current || draft || installationBusiness)
  useEffect(() => {
    const element = clientDialog.current
    if (!dialogOpen || !element) return
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element.showModal()
    return () => {
      element.close()
      document.body.style.overflow = overflow
      if (previous?.isConnected) previous.focus()
    }
  }, [dialogOpen])
  const closeDetail = () => {
    if (busy) return
    setSelected(null)
    setDraft(null)
    setInstallationBusiness(null)
  }
  const receipts = report?.receipts ?? []
  const monthReceipts = receipts.filter((receipt) => receipt.fecha.slice(0, 7) === month)
  const collected = monthReceipts.reduce((sum, receipt) => sum + Number(receipt.total), 0)
  const collectedSetup = monthReceipts.reduce(
    (sum, receipt) => sum + Number(receipt.cargo_configuracion),
    0,
  )
  const byBusiness = new Map<number, ServiceReceipt[]>()
  for (const receipt of receipts) {
    const list = byBusiness.get(receipt.negocio_id) ?? []
    list.push(receipt)
    byBusiness.set(receipt.negocio_id, list)
  }
  const selectedMonth = monthNumber(month)
  const ratesByBusiness = new Map<number, PlatformRate[]>()
  for (const rate of report?.rates ?? []) {
    const list = ratesByBusiness.get(rate.negocio_id) ?? []
    list.push(rate)
    ratesByBusiness.set(rate.negocio_id, list)
  }
  const summaries = businesses.map((b) => {
    const rates = ratesByBusiness.get(b.id) ?? []
    const monthlyRate = rateAt(rates, selectedMonth, Number(b.abono))
    const periods = new Set<number>()
    let setupPaid = false
    let latest = -Infinity
    for (const receipt of byBusiness.get(b.id) ?? []) {
      const start = monthNumber(receipt.periodo_desde)
      for (let offset = 0; offset < receipt.meses; offset++) periods.add(start + offset)
      latest = Math.max(latest, start + receipt.meses - 1)
      setupPaid ||= Number(receipt.cargo_configuracion) > 0
    }
    const trialAlert =
      b.estado === 'PRUEBA' && Boolean(b.vencimiento && b.vencimiento <= upcomingLimit)
    const installationAlert = b.instalacion_estado === 'ERROR' || b.estado_aplicacion === 'ERROR'
    const monthlyDue =
      b.estado === 'ACTIVO' &&
      b.alta &&
      month.slice(0, 7) >= b.alta.slice(0, 7) &&
      monthlyRate > 0 &&
      !periods.has(selectedMonth)
    const setupDue = setupPaid ? 0 : Number(b.cargo_configuracion ?? 0)
    let nextPeriod = Math.max(selectedMonth, monthNumber(b.alta ?? month))
    while (periods.has(nextPeriod)) nextPeriod++
    const nextPending =
      b.estado === 'ACTIVO' && rateAt(rates, nextPeriod, Number(b.abono)) > 0 ? nextPeriod : null
    return {
      b,
      periods,
      latest,
      monthlyDue,
      monthlyRate,
      rates,
      nextPending,
      setupDue,
      trialAlert,
      installationAlert,
      attention: trialAlert || installationAlert || b.estado === 'SUSPENDIDO',
    }
  })
  const currentSummary = summaries.find(({ b }) => b.id === selected)
  const filtered = summaries.filter(
    ({ b, monthlyDue, setupDue, trialAlert, installationAlert }) =>
      (!status || b.estado === status) &&
      (focus !== 'pending' || monthlyDue || setupDue > 0) &&
      (focus !== 'trial' || trialAlert) &&
      (focus !== 'installation' || installationAlert) &&
      `${b.nombre} ${b.responsable} ${b.cuit}`.toLowerCase().includes(search.toLowerCase()),
  )
  const edit = (b?: Business) => {
    setEffectiveMonth(today().slice(0, 7))
    if (!b && pendingRegistration) {
      setEditing(null)
      setDraft(pendingRegistration.draft)
      setRegistrationToken(pendingRegistration.token)
      setManager({ ...pendingRegistration.manager, password: '' })
      setRegistrationLocked(true)
      return
    }
    if (!b) {
      setRegistrationLocked(false)
      setCreatedLinks(null)
      setRegistrationToken(crypto.randomUUID())
      setManager({ username: '', name: '', email: '', password: '', months: 1 })
    }
    setEditing(b?.id ?? null)
    setDraft(
      b
        ? (Object.fromEntries(
            Object.keys(blank()).map((key) => [key, b[key as keyof Business] ?? '']),
          ) as Draft)
        : blank(),
    )
  }
  return (
    <section className="entry-panel platform-businesses">
      <div className="entry-heading">
        <div>
          <span className="alon-tag">ADMINISTRACIÓN GENERAL</span>
          <h2>Mis clientes Alon</h2>
        </div>
        <button type="button" className="primary-button" disabled={busy} onClick={() => edit()}>
          {pendingRegistration ? 'Continuar alta pendiente' : 'Registrar negocio'}
        </button>
      </div>
      <p>
        Completá los datos del negocio y de su gerente. Al confirmar se crean la base, la aplicación
        y la tienda, con un administrador propio del comercio.
      </p>
      {message && <p role="status">{message}</p>}
      {createdLinks && (
        <section aria-label="Enlaces del negocio registrado">
          <h3>Enlaces generados automáticamente</h3>
          <p>
            Gestión:{' '}
            <a href={createdLinks.managementUrl} target="_blank" rel="noreferrer">
              {createdLinks.managementUrl}
            </a>
          </p>
          <p>
            Tienda:{' '}
            <a href={createdLinks.shopUrl} target="_blank" rel="noreferrer">
              {createdLinks.shopUrl}
            </a>
          </p>
          <p>
            El negocio está listo para ingresar con su gerente. Estas direcciones funcionan en esta
            computadora.
          </p>
        </section>
      )}
      {!report ? (
        <p>Cargando informe…</p>
      ) : (
        <>
          <div className="platform-metrics">
            <div>
              Cobrado en el mes<strong>{money(collected)}</strong>
              <span>
                Abonos {money(collected - collectedSetup)} · Configuración {money(collectedSetup)}
              </span>
            </div>
            <div>
              Abonos pendientes del mes
              <strong>
                {money(
                  summaries.reduce((sum, row) => sum + (row.monthlyDue ? row.monthlyRate : 0), 0),
                )}
              </strong>
              <span>{summaries.filter((row) => row.monthlyDue).length} negocios</span>
            </div>
            <div>
              Configuración pendiente
              <strong>{money(summaries.reduce((sum, row) => sum + row.setupDue, 0))}</strong>
            </div>
            <div>
              Negocios que requieren atención
              <strong>{summaries.filter((row) => row.attention).length}</strong>
            </div>
          </div>
          <div className="entry-form">
            <label>
              Buscar negocio
              <input value={search} onChange={(e) => setSearch(e.target.value)} />
            </label>
            <label>
              Filtrar estado
              <select value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="">Todos</option>
                {Object.entries(states).map(([k, v]) => (
                  <option key={k} value={k}>
                    {v}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Filtrar seguimiento
              <select value={focus} onChange={(e) => setFocus(e.target.value)}>
                <option value="">Todos</option>
                <option value="pending">Pendientes de cobro</option>
                <option value="trial">Pruebas por vencer o vencidas</option>
                <option value="installation">Problemas de instalación</option>
              </select>
            </label>
            <label>
              Mes del informe
              <input
                type="month"
                required
                value={month}
                onChange={(e) => {
                  if (e.target.value) setMonth(e.target.value)
                }}
              />
            </label>
          </div>
          <div className="platform-table">
            <h3>Listado de negocios ({filtered.length})</h3>
            <p>
              Inhabilitar conserva los datos. La baja definitiva elimina la base y la instalación;
              se conserva la ficha administrativa y el historial de abonos.
            </p>
            <table>
              <thead>
                <tr>
                  {[
                    'Negocio',
                    'Plan / estado',
                    'Abono',
                    'Último período cubierto',
                    'Cobro y próximo pendiente',
                    'Atención',
                    'Acciones',
                  ].map((h) => (
                    <th key={h}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map(
                  ({
                    b,
                    nextPending,
                    latest,
                    monthlyDue,
                    monthlyRate,
                    setupDue,
                    periods,
                    trialAlert,
                    installationAlert,
                  }) => (
                    <tr key={b.id}>
                      <td>
                        {b.nombre}
                        <br />
                        {b.responsable}
                      </td>
                      <td>
                        {b.plan}
                        <br />
                        {states[b.estado]}
                        {b.estado_motivo && <p>{b.estado_motivo}</p>}
                        {b.estado_aplicacion && b.estado_aplicacion !== 'CONFIRMADO' && (
                          <p role="status">
                            {b.estado_aplicacion === 'APLICANDO'
                              ? 'Aplicando cambio'
                              : 'Cambio incompleto; abrir detalle'}
                          </p>
                        )}
                      </td>
                      <td>{money(monthlyRate)}</td>
                      <td>{monthLabel(latest)}</td>
                      <td>
                        {periods.has(selectedMonth)
                          ? 'Cubierto'
                          : monthlyDue
                            ? 'Abono pendiente'
                            : '—'}
                        {setupDue > 0 && <p>Configuración pendiente</p>}
                        {nextPending !== null && <p>Próximo pendiente {monthLabel(nextPending)}</p>}
                      </td>
                      <td>
                        {trialAlert && <p>Prueba {b.vencimiento}</p>}
                        {installationAlert && <p>Revisar instalación</p>}
                        {b.estado === 'SUSPENDIDO' && <p>Inhabilitado</p>}
                      </td>
                      <td>
                        <button
                          type="button"
                          className="select-button"
                          aria-label={'Ver ' + b.nombre}
                          onClick={() => {
                            setDetailSection('Resumen')
                            setSelected(b.id)
                            setDraft(null)
                            setInstallationBusiness(null)
                            setMovement({
                              tipo: 'COBRO',
                              fecha: today(),
                              importe: '',
                              concepto: '',
                            })
                          }}
                        >
                          Ver
                        </button>
                      </td>
                    </tr>
                  ),
                )}
              </tbody>
            </table>
          </div>
          {!filtered.length && <p>No hay negocios que mostrar. Registrá tu primer cliente.</p>}
        </>
      )}
      {dialogOpen && (
        <dialog
          ref={clientDialog}
          className="client-detail-dialog"
          aria-label={current ? 'Ficha de ' + current.nombre : 'Alta de cliente'}
          onCancel={(event) => {
            event.preventDefault()
            closeDetail()
          }}
        >
          <button type="button" className="select-button" disabled={busy} onClick={closeDetail}>
            Cerrar ficha
          </button>
          {installationBusiness && (
            <div ref={installation}>
              <BusinessInstallationPanel
                key={installationBusiness.id}
                business={installationBusiness}
                onSaved={(result) => {
                  if (
                    result.running &&
                    pendingRegistration &&
                    installationBusiness.alta_token === pendingRegistration.token
                  ) {
                    localStorage.removeItem(pendingRegistrationKey)
                    setPendingRegistration(null)
                    setRegistrationLocked(false)
                    setRegistrationToken(crypto.randomUUID())
                    setDraft(null)
                    setManager({ username: '', name: '', email: '', password: '', months: 1 })
                  }
                  setRevision((n) => n + 1)
                }}
                onClose={() => setInstallationBusiness(null)}
              />
            </div>
          )}
          {draft && (
            <section className="entry-panel" ref={editor}>
              {!editing && registrationLocked && (
                <p role="status">
                  Alta pendiente: se conservaron los datos y la referencia. Reingresá la contraseña
                  original para continuar. También podés consultar la instalación desde el listado.
                </p>
              )}
              <h3>{editing ? 'Editar negocio' : 'Registrar nuevo negocio'}</h3>
              <form
                className="entry-form"
                onSubmit={(e) =>
                  editing
                    ? void save(
                        e,
                        '/' + editing + '?abonoDesde=' + encodeURIComponent(effectiveMonth),
                        {
                          ...draft,
                          abono: Number(draft.abono),
                          cargo_configuracion: undefined,
                          vencimiento: draft.vencimiento || null,
                          respaldo: draft.respaldo || null,
                        },
                        editing ? 'PUT' : 'POST',
                      )
                    : void confirmBusiness(e)
                }
              >
                {fields
                  .filter(({ key }) => {
                    if (key === 'cargo_configuracion') return !editing && draft.estado === 'ACTIVO'
                    return key !== 'abono' || editing || draft.estado === 'ACTIVO'
                  })
                  .map((f) => (
                    <label key={f.key}>
                      {f.label}
                      <input
                        type={f.type ?? 'text'}
                        disabled={busy || (!editing && registrationLocked)}
                        value={draft[f.key] ?? '0'}
                        required={f.required}
                        maxLength={f.max}
                        min={f.type === 'number' ? 0 : undefined}
                        step={f.type === 'number' ? '0.01' : undefined}
                        pattern={f.key === 'cuit' ? '[0-9]{11}' : undefined}
                        onChange={(e) => setDraft({ ...draft, [f.key]: e.target.value })}
                      />
                    </label>
                  ))}
                {editing &&
                  Number(draft.abono) !==
                    Number(businesses.find((b) => b.id === editing)?.abono) && (
                    <label>
                      Nuevo abono desde
                      <input
                        type="month"
                        required
                        min={today().slice(0, 7)}
                        disabled={busy}
                        value={effectiveMonth}
                        onChange={(event) => setEffectiveMonth(event.target.value)}
                      />
                    </label>
                  )}
                <label>
                  Estado del servicio
                  <select
                    disabled={busy || Boolean(editing) || registrationLocked}
                    value={draft.estado}
                    onChange={(e) => setDraft({ ...draft, estado: e.target.value })}
                  >
                    {Object.entries(states)
                      .filter(([key]) => editing || ['ACTIVO', 'PRUEBA'].includes(key))
                      .map(([k, v]) => (
                        <option key={k} value={k}>
                          {v}
                        </option>
                      ))}
                  </select>
                </label>
                <label>
                  Observaciones y soporte
                  <textarea
                    disabled={busy || (!editing && registrationLocked)}
                    maxLength={2000}
                    value={draft.notas}
                    onChange={(e) => setDraft({ ...draft, notas: e.target.value })}
                  />
                </label>
                {!editing && (
                  <fieldset className="permission-choices" disabled={busy}>
                    <legend>Gerente o encargado administrador del negocio</legend>
                    <label>
                      Usuario del gerente
                      <input
                        name="managerUsername"
                        required
                        minLength={3}
                        maxLength={80}
                        pattern="[a-zA-Z0-9_.\-]+"
                        autoComplete="off"
                        disabled={registrationLocked}
                        value={manager.username}
                        onChange={(event) =>
                          setManager({ ...manager, username: event.target.value })
                        }
                      />
                    </label>
                    <label>
                      Nombre del gerente
                      <input
                        required
                        maxLength={160}
                        disabled={registrationLocked}
                        value={manager.name}
                        onChange={(event) => setManager({ ...manager, name: event.target.value })}
                      />
                    </label>
                    <label>
                      Correo del gerente
                      <input
                        type="email"
                        required
                        maxLength={254}
                        disabled={registrationLocked}
                        value={manager.email}
                        onChange={(event) => setManager({ ...manager, email: event.target.value })}
                      />
                    </label>
                    <label>
                      Contraseña del gerente
                      <input
                        type="password"
                        required
                        minLength={4}
                        maxLength={128}
                        autoComplete="new-password"
                        value={manager.password}
                        onChange={(event) =>
                          setManager({ ...manager, password: event.target.value })
                        }
                      />
                    </label>
                    {draft.estado === 'PRUEBA' && (
                      <label>
                        Meses de prueba
                        <input
                          type="number"
                          required
                          min={1}
                          max={120}
                          disabled={registrationLocked}
                          value={manager.months}
                          step={1}
                          onChange={(event) =>
                            setManager({ ...manager, months: Number(event.target.value) })
                          }
                        />
                      </label>
                    )}
                    <p>
                      El gerente tendrá todos los permisos de administración de su negocio. No
                      tendrá acceso al panel de superusuario, a otros negocios ni a la
                      administración general de Alon.
                    </p>
                  </fieldset>
                )}
                <button className="primary-button" disabled={busy}>
                  {busy
                    ? 'Preparando negocio…'
                    : editing
                      ? 'Guardar negocio'
                      : 'Confirmar negocio y crear gerente'}
                </button>
                <button
                  className="select-button"
                  type="button"
                  disabled={busy}
                  onClick={() => setDraft(null)}
                >
                  Cancelar
                </button>
              </form>
            </section>
          )}
          {current && (
            <section
              className="entry-panel business-detail"
              ref={detail}
              tabIndex={-1}
              aria-label={'Detalle de ' + current.nombre}
            >
              <div className="entry-heading">
                <h3>{current.nombre}</h3>
                <button
                  type="button"
                  className="select-button"
                  onClick={() => {
                    setSelected(null)
                    setDraft(null)
                    setInstallationBusiness(null)
                  }}
                >
                  Volver al listado
                </button>
              </div>
              <p>
                {states[current.estado]} · Último período cubierto{' '}
                {monthLabel(currentSummary?.latest ?? -Infinity)}
              </p>
              <div className="entry-actions" role="group" aria-label="Secciones del negocio">
                {detailSections.map((section) => (
                  <button
                    key={section}
                    type="button"
                    className={detailSection === section ? 'primary-button' : 'select-button'}
                    aria-pressed={detailSection === section}
                    onClick={() => setDetailSection(section)}
                  >
                    {section}
                  </button>
                ))}
              </div>
              <div hidden={detailSection !== 'Servicio'}>
                <h4>Estado y acceso de la aplicación</h4>
                <BusinessStateActions
                  key={
                    current.id +
                    ':' +
                    current.estado +
                    ':' +
                    current.eliminado_en +
                    ':' +
                    current.baja_iniciada_en +
                    ':' +
                    current.estado_aplicacion +
                    ':' +
                    current.baja_paso
                  }
                  business={current}
                  onRefresh={() => setRevision((n) => n + 1)}
                  onSaved={() => {
                    setRevision((n) => n + 1)
                    setInstallationBusiness(null)
                  }}
                />
                <p>Último respaldo confirmado: {current.respaldo || 'Sin confirmar'}</p>
                {!current.baja_iniciada_en && <BusinessAvailability businessId={current.id} />}
              </div>
              <div hidden={detailSection !== 'Resumen'}>
                <div className="entry-actions">
                  <button
                    type="button"
                    className="select-button"
                    disabled={Boolean(current.baja_iniciada_en)}
                    onClick={() => edit(current)}
                  >
                    Editar {current.nombre}
                  </button>
                  <button
                    type="button"
                    className="select-button"
                    disabled={Boolean(current.baja_iniciada_en)}
                    onClick={() => setInstallationBusiness(current)}
                  >
                    Habilitar sistema de {current.nombre}
                  </button>
                </div>
                <h4>Datos del negocio</h4>
                <p>
                  Alta: {current.alta} · Gerente: {current.gerente_usuario || 'Sin informar'}
                </p>
                <p>Responsable: {current.responsable || 'Sin informar'}</p>
                <p>
                  Abono mensual vigente:{' '}
                  {money(
                    rateAt(
                      currentSummary?.rates ?? [],
                      monthNumber(today()),
                      Number(current.abono),
                    ),
                  )}{' '}
                  · Configuración: {money(current.cargo_configuracion)}
                </p>
                {[
                  ['Gestión', current.gestion_url],
                  ['Tienda', current.tienda_url],
                ].map(
                  ([label, url]) =>
                    url &&
                    /^https?:\/\//i.test(url) && (
                      <p key={label}>
                        <a href={url} target="_blank" rel="noreferrer">
                          Abrir {label}
                        </a>
                      </p>
                    ),
                )}
                <p>
                  {current.razon_social} · {current.cuit} · {current.localidad}
                </p>
                <p>
                  {current.telefono} · {current.email}
                </p>
                <p>Base: {current.base_referencia || 'Sin informar'}</p>
                <p>{current.notas}</p>
              </div>
              <div hidden={detailSection !== 'Cobros'}>
                <h4>Períodos de {month.slice(0, 4)}</h4>
                <ul className="platform-periods">
                  {Array.from({ length: 12 }, (_, index) => {
                    const period = Number(month.slice(0, 4)) * 12 + index
                    const key = month.slice(0, 4) + '-' + String(index + 1).padStart(2, '0')
                    const covered = currentSummary?.periods.has(period)
                    const amount = rateAt(
                      currentSummary?.rates ?? [],
                      period,
                      Number(current.abono),
                    )
                    const expected =
                      current.estado === 'ACTIVO' &&
                      amount > 0 &&
                      Boolean(current.alta && key >= current.alta.slice(0, 7))
                    return (
                      <li key={period}>
                        {monthLabel(period)} ·{' '}
                        {covered
                          ? 'Cubierto'
                          : expected && key <= today().slice(0, 7)
                            ? 'Pendiente ' + money(amount)
                            : '—'}
                      </li>
                    )
                  })}
                </ul>
                <BusinessReceipts
                  key={current.id}
                  business={current}
                  rates={currentSummary?.rates}
                  showAudit={false}
                  onSaved={() => setRevision((n) => n + 1)}
                />
                <h4>Ajustes manuales de cuenta de {current.nombre}</h4>
                <p>Saldo: {money(current.saldo)}. Los movimientos son del abono de Alon.</p>
                <form
                  className="entry-form"
                  onSubmit={(e) =>
                    void save(e, '/' + current.id + '/movements', {
                      ...movement,
                      importe: Number(movement.importe),
                    })
                  }
                >
                  <label>
                    Tipo de movimiento
                    <select
                      value={movement.tipo}
                      onChange={(e) => setMovement({ ...movement, tipo: e.target.value })}
                    >
                      <option value="COBRO">Cobro recibido</option>
                      <option value="CARGO">Cargo al negocio</option>
                    </select>
                  </label>
                  <label>
                    Fecha del movimiento
                    <input
                      type="date"
                      required
                      value={movement.fecha}
                      onChange={(e) => setMovement({ ...movement, fecha: e.target.value })}
                    />
                  </label>
                  <label>
                    Importe (ARS)
                    <input
                      type="number"
                      required
                      min="0.01"
                      step="0.01"
                      value={movement.importe}
                      onChange={(e) => setMovement({ ...movement, importe: e.target.value })}
                    />
                  </label>
                  <label>
                    Concepto o comprobante
                    <input
                      required
                      maxLength={300}
                      value={movement.concepto}
                      onChange={(e) => setMovement({ ...movement, concepto: e.target.value })}
                    />
                  </label>
                  <button className="primary-button" disabled={busy}>
                    Registrar movimiento
                  </button>
                </form>
                <h4>Historial de cargos y cobros</h4>
                {movements
                  .filter((m) => m.negocio_id === current.id)
                  .map((m) => (
                    <p key={m.id}>
                      {m.anulado ? 'ANULADO · ' : ''}
                      {m.fecha} · {m.tipo === 'COBRO' ? 'Cobro' : 'Cargo'} · {money(m.importe)} ·{' '}
                      {m.concepto}
                    </p>
                  ))}
              </div>
              {detailSection === 'Historial' && <BusinessAudit businessId={current.id} />}
            </section>
          )}
        </dialog>
      )}
    </section>
  )
}

function BusinessAvailability({ businessId }: { businessId: number }) {
  const [result, setResult] = useState<{
    running: boolean
    managementUrl?: string
    shopUrl?: string
  } | null>(null)
  useEffect(() => {
    const controller = new AbortController()
    const read = () =>
      void apiFetch('/api/v1/super/businesses/' + businessId + '/installation', {
        signal: controller.signal,
      })
        .then(async (response) => {
          if (!response.ok) return
          const body = await response.json()
          if (!controller.signal.aborted) setResult(body)
        })
        .catch(() => {})
    read()
    const timer = setInterval(read, 10000)
    return () => {
      controller.abort()
      clearInterval(timer)
    }
  }, [businessId])
  if (!result?.running)
    return (
      <p role="status">
        {result
          ? 'El negocio todavía no confirmó su disponibilidad. Consultá Habilitar sistema.'
          : 'Comprobando disponibilidad del negocio.'}
      </p>
    )
  return (
    <>
      {[
        ['Gestión', result.managementUrl],
        ['Tienda', result.shopUrl],
      ].map(
        ([label, url]) =>
          url &&
          /^https?:\/\//i.test(url) && (
            <p key={label}>
              <a href={url} target="_blank" rel="noreferrer">
                Abrir {label}
              </a>
            </p>
          ),
      )}
    </>
  )
}
