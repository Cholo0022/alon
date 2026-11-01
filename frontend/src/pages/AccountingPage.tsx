import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { currency, todayLocal, formatDateDisplay } from '../lib/format'
import { FiscalEditor, type FiscalRecord } from '../components/FiscalEditor'
import { type FiscalTarget } from '../hooks/useAccountingOptions'
import './accounting.css'
type Row = Record<string, unknown>
type Report = {
  mes: string
  generado_en: string
  fuente: string
  estado_periodo: string
  revision?: number
  cierre_id?: number
  cerrado_en?: string
  configuracion: Row
  resumen: Row
  notas: string[]
  ventas: Row[]
  compras: Row[]
  gastos: Row[]
  caja: Row[]
  fiscales: Row[]
  alicuotas: Row[]
}
const flag = (value: unknown) => value === true || value === 1
const metrics: [string, string][] = [
  ['ventas_registradas', 'Ventas confirmadas de gestión'],
  ['compras_registradas', 'Compras y remitos registrados'],
  ['costo_historico_registrado', 'Costo histórico registrado de lo vendido'],
  ['gastos_registrados', 'Gastos registrados'],
  ['resultado_gestion', 'Resultado de gestión (no impositivo)'],
  ['iva_debito_controlado', 'IVA débito registrado y controlado'],
  ['iva_credito_computable_controlado', 'IVA crédito computable controlado'],
  ['diferencia_iva_parcial', 'Diferencia parcial débito − crédito'],
  ['percepciones_iva_compras', 'Percepciones de IVA en compras/gastos'],
  ['percepciones_iibb_compras', 'Percepciones de IIBB en compras/gastos'],
  ['cobros_caja', 'Ingresos de caja del mes'],
  ['pagos_caja', 'Egresos de caja del mes'],
  ['saldos_clientes_al_generar', 'Saldos de clientes al generar'],
  ['saldos_proveedores_al_generar', 'Saldos de proveedores al generar'],
]
function Table({
  rows,
  columns,
  onSelect,
  label,
}: {
  rows: Row[]
  columns: [string, string][]
  onSelect?: (row: Row) => void
  label: string
}) {
  return (
    <section className="accounting-section">
      <h3>
        {label} · {rows.length}
      </h3>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              {columns.map(([key, title]) => (
                <th key={key}>{title}</th>
              ))}
              {onSelect && <th className="no-print">Acciones</th>}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={String(row.id ?? index)}>
                {columns.map(([key]) => (
                  <td key={key}>
                    {['fecha', 'periodo'].includes(key)
                      ? formatDateDisplay(String(row[key] ?? ''))
                      : [
                            'total',
                            'costo_total',
                            'importe',
                            'neto_gravado',
                            'iva_signado',
                            'total_signado',
                            'credito_computable',
                            'credito_computable_signado',
                            'neto_signado',
                          ].includes(key) && row[key] != null
                        ? currency.format(Number(row[key]))
                        : typeof row[key] === 'boolean'
                          ? row[key]
                            ? 'Sí'
                            : 'No'
                          : String(row[key] ?? '—')}
                  </td>
                ))}
                {onSelect && (
                  <td className="no-print">
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={() => onSelect(row)}
                    >
                      {row.fiscal_id || label.startsWith('Comprobantes')
                        ? 'Revisar impuestos'
                        : 'Completar impuestos'}
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
        {!rows.length && <p>Sin registros en este mes.</p>}
      </div>
    </section>
  )
}
function PeriodDialog({
  month,
  action,
  onClose,
  onDone,
}: {
  month: string
  action: 'open' | 'close'
  onClose: () => void
  onDone: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null),
    cancel = useRef<HTMLButtonElement>(null)
  const [busy, setBusy] = useState(false),
    [error, setError] = useState('')
  useEffect(() => {
    const modal = dialog.current
    const previous = document.activeElement
    modal?.showModal()
    cancel.current?.focus()
    return () => {
      modal?.close()
      if (previous instanceof HTMLElement) previous.focus()
    }
  }, [])
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy) return
    const form = new FormData(event.currentTarget)
    setBusy(true)
    setError('')
    try {
      const response = await apiFetch(`/api/v1/operations/accounting/${action}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mes: month, motivo: form.get('motivo') }),
      })
      const body = (await response.json()) as { error?: string }
      if (!response.ok) throw new Error(body.error ?? 'No se pudo cambiar el período.')
      window.dispatchEvent(new Event('alon:refresh'))
      onDone()
    } catch (error) {
      setError(error instanceof Error ? error.message : 'No se pudo conectar.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <dialog
      ref={dialog}
      className="delete-confirmation"
      aria-labelledby="period-action-title"
      onCancel={(event) => {
        event.preventDefault()
        if (!busy) onClose()
      }}
    >
      <h2 id="period-action-title">
        {action === 'close' ? 'Cerrar' : 'Abrir / reabrir'} el período {month}
      </h2>
      <p>
        {action === 'close'
          ? 'Se guardará una copia del informe. El mes quedará bloqueado para nuevas cargas y modificaciones.'
          : 'Se habilitará la carga de ese mes. Si estaba cerrado, se conservará la versión anterior del informe y quedará registrada la reapertura.'}
      </p>
      <form className="entry-form" onSubmit={(event) => void submit(event)}>
        <label>
          Motivo (mínimo 10 caracteres)
          <textarea name="motivo" minLength={10} maxLength={500} required disabled={busy} />
        </label>
        {action === 'close' && (
          <label>
            <span>
              <input type="checkbox" required disabled={busy} /> Revisé el informe y los pendientes
              del mes
            </span>
          </label>
        )}
        {error && <p role="alert">{error}</p>}
        <div className="row-actions">
          <button
            ref={cancel}
            className="secondary-button"
            type="button"
            disabled={busy}
            onClick={onClose}
          >
            Cancelar
          </button>
          <button className="primary-button" type="submit" disabled={busy}>
            {busy
              ? 'Guardando…'
              : action === 'close'
                ? 'Confirmar cierre'
                : 'Confirmar apertura / reapertura'}
          </button>
        </div>
      </form>
    </dialog>
  )
}
export function AccountingPage({ canManage }: { canManage: boolean }) {
  const [month, setMonth] = useState(todayLocal().slice(0, 7)),
    [report, setReport] = useState<Report | null>(null),
    [history, setHistory] = useState<Row[]>([])
  const [config, setConfig] = useState<Row>({}),
    [draft, setDraft] = useState({
      controlPeriodos: false,
      cargaImpuestos: false,
      cuit: '',
      condicionIva: 'RESPONSABLE_INSCRIPTO',
    })
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [message, setMessage] = useState(''),
    [archive, setArchive] = useState<number | null>(null)
  const [action, setAction] = useState<'open' | 'close' | null>(null),
    [editor, setEditor] = useState<{ target?: FiscalTarget; initial?: FiscalRecord } | null>(null)
  const generation = useRef(0)
  const load = useCallback(async () => {
    const current = ++generation.current
    setBusy(true)
    try {
      const responses = await Promise.all([
        apiFetch(`/api/v1/accounting/report?month=${encodeURIComponent(month)}`),
        apiFetch('/api/v1/accounting/settings'),
        apiFetch(`/api/v1/accounting/history?month=${encodeURIComponent(month)}`),
      ])
      const bodies = (await Promise.all(responses.map((response) => response.json()))) as [
        Report,
        Row,
        Row[],
      ]
      if (responses.some((response) => !response.ok))
        throw new Error('No se pudo consultar el informe de contabilidad.')
      if (current !== generation.current) return
      setReport(bodies[0])
      setConfig(bodies[1])
      setDraft({
        controlPeriodos: flag(bodies[1].control_periodos),
        cargaImpuestos: flag(bodies[1].carga_impuestos),
        cuit: String(bodies[1].cuit_contable ?? ''),
        condicionIva: String(bodies[1].condicion_iva ?? 'RESPONSABLE_INSCRIPTO'),
      })
      setHistory(bodies[2])
      setArchive(null)
      setError('')
    } catch (error) {
      if (current === generation.current)
        setError(error instanceof Error ? error.message : 'No se pudo conectar.')
    } finally {
      if (current === generation.current) setBusy(false)
    }
  }, [month])
  const invalidate = useCallback(() => {
    generation.current++
  }, [])
  useEffect(() => {
    let active = true
    void Promise.resolve().then(() => {
      if (active) return load()
    })
    return () => {
      active = false
      invalidate()
    }
  }, [load, invalidate])
  const saveSettings = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    const form = new FormData(event.currentTarget)
    try {
      const response = await apiFetch('/api/v1/operations/accounting/settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...draft, motivo: form.get('motivo') }),
      })
      const result = (await response.json()) as { error?: string }
      if (!response.ok) throw new Error(result.error ?? 'No se pudieron guardar las opciones.')
      setMessage(
        'Opciones guardadas. Si activaste el control mensual, abrí el mes antes de cargar comprobantes.',
      )
      window.dispatchEvent(new Event('alon:refresh'))
      await load()
    } catch (error) {
      setError(error instanceof Error ? error.message : 'No se pudo conectar.')
    } finally {
      setBusy(false)
    }
  }
  const edit = async (row: Row) => {
    try {
      const id = Number(row.fiscal_id ?? row.id)
      const response = await apiFetch(`/api/v1/accounting/documents/${id}`)
      const record = (await response.json()) as FiscalRecord
      if (!response.ok) throw new Error('No se pudieron consultar los impuestos.')
      setEditor({ initial: record })
    } catch (error) {
      setError(error instanceof Error ? error.message : 'No se pudo conectar.')
    }
  }
  const complete = (clase: FiscalTarget['clase']) => (row: Row) => {
    if (row.fiscal_id) {
      void edit(row)
      return
    }
    setEditor({
      target: {
        clase,
        id: Number(row.id),
        fecha: String(row.fecha).slice(0, 10),
        total: Number(row.total ?? row.importe ?? 0),
        tercero: clase === 'GASTO' ? '' : String(row.tercero ?? ''),
        documento: String(row.cuit ?? ''),
      },
    })
  }
  const showClosure = async (id: number) => {
    setBusy(true)
    try {
      const response = await apiFetch(`/api/v1/accounting/closures/${id}`)
      if (!response.ok) throw new Error('No se pudo consultar el cierre.')
      setReport((await response.json()) as Report)
      setArchive(id)
      setError('')
    } catch (error) {
      setError(error instanceof Error ? error.message : 'No se pudo conectar.')
    } finally {
      setBusy(false)
    }
  }
  const editable = flag(config.carga_impuestos) && report?.estado_periodo !== 'CERRADO' && !archive
  return (
    <div className="accounting-page">
      <section className="panel accounting-controls no-print">
        <h2>Opciones de contabilidad</h2>
        <p>
          Las dos opciones son independientes y comienzan desactivadas. Desactivar el control
          mensual no desbloquea los meses ya cerrados: deben reabrirse con un motivo.
        </p>
        <form className="entry-form" onSubmit={(event) => void saveSettings(event)}>
          <label>
            <span>
              <input
                type="checkbox"
                checked={draft.controlPeriodos}
                disabled={!canManage || busy || !!archive}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, controlPeriodos: event.target.checked }))
                }
              />{' '}
              Activar control de períodos mensuales
            </span>
          </label>
          <label>
            <span>
              <input
                type="checkbox"
                checked={draft.cargaImpuestos}
                disabled={!canManage || busy || !!archive}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, cargaImpuestos: event.target.checked }))
                }
              />{' '}
              Activar carga de comprobantes con impuestos
            </span>
          </label>
          <label>
            CUIT del negocio
            <input
              value={draft.cuit}
              inputMode="numeric"
              maxLength={11}
              pattern="[0-9]{11}"
              required={draft.cargaImpuestos}
              disabled={!canManage || busy || !!archive}
              onChange={(event) =>
                setDraft((current) => ({
                  ...current,
                  cuit: event.target.value.replace(/[-\s]/g, ''),
                }))
              }
            />
          </label>
          <label>
            Condición de IVA
            <select
              value={draft.condicionIva}
              disabled={!canManage || busy || !!archive}
              onChange={(event) =>
                setDraft((current) => ({ ...current, condicionIva: event.target.value }))
              }
            >
              <option value="RESPONSABLE_INSCRIPTO">Responsable inscripto</option>
              <option value="MONOTRIBUTO">Monotributo</option>
              <option value="EXENTO">Exento</option>
              <option value="SIN_CONFIGURAR">Sin configurar</option>
            </select>
          </label>
          {canManage && (
            <>
              <label>
                Motivo del cambio
                <input
                  name="motivo"
                  minLength={10}
                  maxLength={500}
                  defaultValue="Configuración de contabilidad del negocio"
                  required
                  disabled={busy || !!archive}
                />
              </label>
              <button className="primary-button" type="submit" disabled={busy || !!archive}>
                Guardar opciones
              </button>
            </>
          )}
        </form>
        {!canManage && (
          <p>
            El gerente o el superusuario con permiso de Contabilidad administra estas opciones y los
            períodos.
          </p>
        )}
      </section>
      <section className="panel accounting-controls no-print">
        <h2>Período del informe</h2>
        <label>
          Mes
          <input
            aria-label="Mes del informe"
            type="month"
            value={month}
            disabled={busy}
            onChange={(event) => {
              if (!event.target.value) return
              setReport(null)
              setHistory([])
              setArchive(null)
              setMonth(event.target.value)
              setMessage('')
            }}
            required
          />
        </label>
        <div className="row-actions">
          <button
            type="button"
            className="secondary-button"
            disabled={busy}
            onClick={() => void load()}
          >
            Actualizar informe
          </button>
          {canManage && (
            <>
              <button
                type="button"
                className="secondary-button"
                disabled={busy || !!archive}
                onClick={() => setAction('open')}
              >
                {report?.estado_periodo === 'CERRADO' ? 'Reabrir mes' : 'Abrir mes'}
              </button>
              <button
                type="button"
                className="primary-button"
                disabled={
                  busy ||
                  !!archive ||
                  !flag(config.control_periodos) ||
                  report?.estado_periodo !== 'ABIERTO'
                }
                onClick={() => setAction('close')}
              >
                Cerrar mes
              </button>
            </>
          )}
          {editable && (
            <button
              className="secondary-button"
              type="button"
              disabled={busy}
              onClick={() => setEditor({})}
            >
              Nuevo comprobante con impuestos
            </button>
          )}
          {report && (
            <>
              <a
                className="secondary-button"
                href={`/api/v1/accounting/export?month=${encodeURIComponent(month)}${archive ? `&closureId=${archive}` : ''}`}
                download
              >
                Descargar informe para contador (ZIP/CSV)
              </a>
              <button className="secondary-button" type="button" onClick={() => window.print()}>
                Imprimir / guardar PDF
              </button>
            </>
          )}
        </div>
      </section>
      {error && <p role="alert">{error}</p>}
      {message && <p role="status">{message}</p>}
      {busy && <p role="status">Cargando contabilidad…</p>}
      {report && (
        <section className="accounting-report">
          <h2>Informe mensual para el contador · {report.mes}</h2>
          <p>
            <strong>{String(report.configuracion.nombre)}</strong> · CUIT{' '}
            {String(report.configuracion.cuit_contable || 'pendiente')} ·{' '}
            {String(report.configuracion.condicion_iva).replaceAll('_', ' ')} · ARS
          </p>
          <p>
            Estado: <strong>{report.estado_periodo}</strong> ·{' '}
            {report.fuente === 'CIERRE'
              ? `Copia del cierre, revisión ${report.revision}`
              : 'Información actual'}{' '}
            · Generado {new Date(report.generado_en).toLocaleString('es-AR')}
          </p>
          {archive && (
            <div className="notice no-print">
              Estás consultando un cierre conservado.{' '}
              <button className="secondary-button" type="button" onClick={() => void load()}>
                Volver al informe actual
              </button>
            </div>
          )}
          <div className="metrics-grid">
            {metrics.map(([key, label]) => (
              <article className="metric-card" key={key}>
                <span>{label}</span>
                <strong>{currency.format(Number(report.resumen[key] ?? 0))}</strong>
              </article>
            ))}
          </div>
          <div className="notice accounting-pending">
            <strong>Pendientes:</strong> {String(report.resumen.operaciones_sin_datos_fiscales)}{' '}
            operaciones sin datos fiscales · {String(report.resumen.comprobantes_sin_controlar)}{' '}
            comprobantes sin controlar · {String(report.resumen.cajas_abiertas)} cajas abiertas ·{' '}
            {String(report.resumen.ventas_anuladas_sin_nota_credito ?? 0)} ventas anuladas con
            factura y sin nota de crédito.
          </div>
          {!flag(config.carga_impuestos) && (
            <p>
              La carga con impuestos está desactivada. Los datos fiscales ya registrados se
              conservan y pueden consultarse.
            </p>
          )}
          <Table
            label="Ventas de gestión"
            rows={report.ventas}
            columns={[
              ['fecha', 'Fecha'],
              ['numero', 'Comprobante interno'],
              ['tercero', 'Cliente'],
              ['total', 'Total registrado'],
              ['costo_total', 'Costo histórico'],
              ['estado', 'Estado'],
              ['fiscal_id', 'Registro fiscal'],
            ]}
            onSelect={editable ? complete('VENTA') : undefined}
          />
          <Table
            label="Compras y remitos"
            rows={report.compras}
            columns={[
              ['fecha', 'Fecha'],
              ['numero_documento', 'Comprobante'],
              ['tipo_documento', 'Tipo'],
              ['tercero', 'Proveedor'],
              ['total', 'Total'],
              ['fiscal_id', 'Registro fiscal'],
            ]}
            onSelect={editable ? complete('COMPRA') : undefined}
          />
          <Table
            label="Gastos"
            rows={report.gastos}
            columns={[
              ['fecha', 'Fecha'],
              ['categoria', 'Categoría'],
              ['descripcion', 'Detalle'],
              ['importe', 'Importe'],
              ['fiscal_id', 'Registro fiscal'],
            ]}
            onSelect={editable ? complete('GASTO') : undefined}
          />
          <Table
            label="Movimientos de caja"
            rows={report.caja}
            columns={[
              ['fecha_hora', 'Fecha/hora'],
              ['tipo', 'Tipo'],
              ['categoria', 'Concepto'],
              ['medio_pago', 'Medio'],
              ['referencia', 'Referencia'],
              ['importe', 'Importe'],
            ]}
          />
          <Table
            label="Comprobantes con impuestos"
            rows={report.fiscales}
            columns={[
              ['fecha', 'Emisión'],
              ['periodo', 'Registración'],
              ['clase', 'Clase'],
              ['tipo', 'Tipo'],
              ['letra', 'Letra'],
              ['punto_venta', 'P. venta'],
              ['numero', 'Número'],
              ['tercero', 'Tercero'],
              ['documento', 'CUIT/DNI'],
              ['neto_gravado', 'Neto'],
              ['iva_signado', 'IVA con signo'],
              ['total_signado', 'Total con signo'],
              ['credito_computable_signado', 'Crédito con signo'],
              ['controlado', 'Controlado'],
            ]}
            onSelect={editable ? (row) => void edit(row) : undefined}
          />
          <Table
            label="Detalle de alícuotas"
            rows={report.alicuotas}
            columns={[
              ['clase', 'Clase'],
              ['tipo', 'Tipo'],
              ['numero', 'Número'],
              ['tercero', 'Tercero'],
              ['alicuota', 'IVA %'],
              ['neto_signado', 'Neto con signo'],
              ['iva_signado', 'IVA con signo'],
              ['controlado', 'Controlado'],
            ]}
          />
          <section className="accounting-section">
            <h3>Alcance y controles</h3>
            {report.notas.map((note) => (
              <p key={note}>{note}</p>
            ))}
          </section>
        </section>
      )}
      <section className="panel accounting-controls no-print">
        <h3>Historial de cierres conservados</h3>
        {history.length ? (
          history.map((row) => (
            <p key={String(row.id)}>
              Revisión {String(row.revision)} · {String(row.cerrado_en)} · {String(row.responsable)}{' '}
              · {String(row.motivo)}{' '}
              <button
                type="button"
                className="secondary-button"
                disabled={busy}
                onClick={() => void showClosure(Number(row.id))}
              >
                Ver cierre
              </button>
            </p>
          ))
        ) : (
          <p>Este mes todavía no tiene cierres guardados.</p>
        )}
      </section>
      {action && (
        <PeriodDialog
          month={month}
          action={action}
          onClose={() => setAction(null)}
          onDone={() => {
            setAction(null)
            setMessage('Estado del período actualizado.')
            void load()
          }}
        />
      )}
      {editor && (
        <FiscalEditor
          {...editor}
          month={month}
          onClose={() => setEditor(null)}
          onSaved={() => {
            setEditor(null)
            setMessage('Datos fiscales guardados.')
            void load()
          }}
        />
      )}
    </div>
  )
}
