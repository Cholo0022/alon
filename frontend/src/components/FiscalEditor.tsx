import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { currency, todayLocal } from '../lib/format'
import { type FiscalTarget, useAccountingOptions } from '../hooks/useAccountingOptions'
export type FiscalRecord = Record<string, unknown> & {
  alicuotas?: { alicuota: number; neto: number; iva: number }[]
}
const rates = [0, 2.5, 5, 10.5, 21, 27]
export function FiscalEditor({
  target,
  initial,
  month,
  onClose,
  onSaved,
  canReview = true,
}: {
  target?: FiscalTarget
  initial?: FiscalRecord
  month?: string
  onClose: () => void
  onSaved: () => void
  canReview?: boolean
}) {
  const text = (key: string, fallback = '') => String(initial?.[key] ?? fallback)
  const [clase, setClase] = useState(text('clase', target?.clase ?? 'COMPRA'))
  const [tipo, setTipo] = useState(text('tipo', 'FACTURA'))
  const [letra, setLetra] = useState(text('letra', 'A'))
  const [documentKind, setDocumentKind] = useState(
    text('documento_tipo', target?.clase === 'VENTA' ? 'CONSUMIDOR_FINAL' : 'CUIT'),
  )
  const [authType, setAuthType] = useState(text('autorizacion_tipo'))
  const [lines, setLines] = useState(
    rates.map((rate) => {
      const line = initial?.alicuotas?.find((line) => Number(line.alicuota) === rate)
      return { alicuota: rate, neto: String(line?.neto ?? 0), iva: String(line?.iva ?? 0) }
    }),
  )
  const [credit, setCredit] = useState(text('credito_computable', '0'))
  const [total, setTotal] = useState(text('total', String(target?.total ?? 0)))
  const [calculated, setCalculated] = useState(
    () =>
      (initial?.alicuotas ?? []).reduce(
        (sum, line) => sum + Number(line.neto) + Number(line.iva),
        0,
      ) +
      ['exento', 'no_gravado', 'otros_tributos', 'percepcion_iva', 'percepcion_iibb'].reduce(
        (sum, key) => sum + Number(initial?.[key] ?? 0),
        0,
      ),
  )
  const [busy, setBusy] = useState(false),
    [message, setMessage] = useState('')
  const dialog = useRef<HTMLDialogElement>(null),
    cancel = useRef<HTMLButtonElement>(null)
  const title = useId()
  useEffect(() => {
    const modal = dialog.current
    const old = document.activeElement
    modal?.showModal()
    cancel.current?.focus()
    return () => {
      modal?.close()
      if (old instanceof HTMLElement) old.focus()
    }
  }, [])
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy) return
    const data = new FormData(event.currentTarget)
    const numeric = (name: string) => Number(data.get(name) ?? 0)
    const payload = {
      clase,
      tipo,
      letra,
      puntoVenta: numeric('puntoVenta'),
      numero: numeric('numero'),
      tercero: String(data.get('tercero') ?? ''),
      documentoTipo: data.get('documentoTipo'),
      documento: String(data.get('documento') ?? '').replace(/[-\s]/g, ''),
      fecha: data.get('fecha'),
      periodo: data.get('periodo'),
      operacionId: target?.id ?? (initial?.operacion_id ? Number(initial.operacion_id) : null),
      comprobanteAsociado: data.get('comprobanteAsociado'),
      exento: numeric('exento'),
      noGravado: numeric('noGravado'),
      otrosTributos: numeric('otrosTributos'),
      percepcionIva: numeric('percepcionIva'),
      percepcionIibb: numeric('percepcionIibb'),
      creditoComputable: Number(credit),
      total: Number(total),
      controlado: data.get('controlado') === 'on',
      autorizacionTipo: data.get('autorizacionTipo'),
      autorizacionCodigo: data.get('autorizacionCodigo'),
      observaciones: data.get('observaciones'),
      alicuotas: lines
        .filter((line) => Number(line.neto) !== 0 || Number(line.iva) !== 0)
        .map((line) => ({
          alicuota: line.alicuota,
          neto: Number(line.neto),
          iva: Number(line.iva),
        })),
    }
    let endpoint = '/api/v1/operations/accounting/documents'
    if (initial?.id) endpoint += `/${Number(initial.id)}`
    else if (target) {
      const route = clase === 'COMPRA' ? 'purchases' : clase === 'VENTA' ? 'sales' : 'cash/expenses'
      endpoint = `/api/v1/operations/${route}/${target.id}/fiscal`
    }
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch(endpoint, {
        method: initial?.id ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      const result = (await response.json()) as { error?: string }
      if (!response.ok) throw new Error(result.error ?? 'No se pudo guardar el desglose.')
      window.dispatchEvent(new Event('alon:refresh'))
      onSaved()
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <dialog
      ref={dialog}
      className="fiscal-dialog"
      aria-labelledby={title}
      onCancel={(event) => {
        event.preventDefault()
        if (!busy) onClose()
      }}
    >
      <header className="fiscal-dialog-heading">
        <h2 id={title}>
          {initial ? 'Modificar datos fiscales' : 'Cargar comprobante con impuestos'}
        </h2>
        <button
          ref={cancel}
          className="secondary-button"
          type="button"
          disabled={busy}
          onClick={onClose}
        >
          {target ? 'Completar después' : 'Cancelar'}
        </button>
      </header>
      {target && (
        <p className="notice">
          La operación #{target.id} ya quedó guardada por {currency.format(target.total)}. Este
          desglose agrega su información fiscal; no vuelve a registrar stock ni pagos.
        </p>
      )}
      <p>
        Ingresá importes en pesos argentinos (ARS) y copiá los datos del comprobante emitido o
        recibido fuera de Alon. El registro no emite una factura ni verifica su autorización en
        ARCA.
      </p>
      <form
        className="entry-form fiscal-form"
        onInput={(event) => {
          const fields =
            event.currentTarget.querySelectorAll<HTMLInputElement>('input[data-tax-amount]')
          setCalculated(
            Math.round(
              Array.from(fields).reduce((sum, input) => sum + Number(input.value || 0), 0) * 100,
            ) / 100,
          )
        }}
        onSubmit={(event) => void submit(event)}
      >
        <label>
          Clase
          <select
            value={clase}
            disabled={!!target || !!initial || busy}
            onChange={(event) => {
              setClase(event.target.value)
              if (event.target.value === 'VENTA') setCredit('0')
            }}
          >
            <option value="COMPRA">Compra</option>
            <option value="VENTA">Venta</option>
            <option value="GASTO">Gasto</option>
          </select>
        </label>
        <label>
          Tipo
          <select
            value={tipo}
            disabled={busy}
            onChange={(event) => {
              setTipo(event.target.value)
              if (event.target.value === 'INTERNO_NO_FISCAL') {
                setLetra('X')
                setCredit('0')
                setLines(rates.map((alicuota) => ({ alicuota, neto: '0', iva: '0' })))
              }
            }}
          >
            <option value="FACTURA">Factura</option>
            <option value="NOTA_CREDITO">Nota de crédito</option>
            <option value="NOTA_DEBITO">Nota de débito</option>
            {clase === 'GASTO' && (
              <option value="INTERNO_NO_FISCAL">Gasto sin comprobante fiscal</option>
            )}
          </select>
        </label>
        <label>
          Letra
          <select
            value={letra}
            disabled={busy || tipo === 'INTERNO_NO_FISCAL'}
            onChange={(event) => {
              setLetra(event.target.value)
              if (event.target.value !== 'A') setCredit('0')
            }}
          >
            <option>A</option>
            <option>B</option>
            <option>C</option>
            {tipo === 'INTERNO_NO_FISCAL' && <option>X</option>}
          </select>
        </label>
        <label>
          Punto de venta
          <input
            name="puntoVenta"
            type="number"
            min={tipo === 'INTERNO_NO_FISCAL' ? 0 : 1}
            max="99999"
            defaultValue={text('punto_venta', tipo === 'INTERNO_NO_FISCAL' ? '0' : '1')}
            required
            disabled={busy}
          />
        </label>
        <label>
          Número
          <input
            name="numero"
            type="number"
            min="1"
            max="99999999"
            defaultValue={text('numero', '')}
            required
            disabled={busy}
          />
        </label>
        <label>
          Fecha de emisión
          <input
            name="fecha"
            type="date"
            defaultValue={text('fecha', target?.fecha ?? todayLocal()).slice(0, 10)}
            required
            disabled={busy}
          />
        </label>
        <label>
          Mes de registración
          <input
            name="periodo"
            type="month"
            defaultValue={text(
              'periodo',
              month ?? target?.fecha.slice(0, 7) ?? todayLocal().slice(0, 7),
            ).slice(0, 7)}
            required
            disabled={busy}
          />
        </label>
        <label>
          Nombre o razón social del tercero
          <input
            name="tercero"
            defaultValue={text('tercero', target?.tercero ?? '')}
            maxLength={150}
            required
            disabled={busy}
          />
        </label>
        <label>
          Tipo de documento
          <select
            name="documentoTipo"
            value={documentKind}
            onChange={(event) => setDocumentKind(event.target.value)}
            disabled={busy}
          >
            <option value="CUIT">CUIT</option>
            <option value="DNI">DNI</option>
            <option value="CONSUMIDOR_FINAL">Consumidor final</option>
            <option value="SIN_IDENTIFICAR">Sin identificar (gasto no fiscal)</option>
          </select>
        </label>
        <label>
          CUIT / DNI del tercero
          <input
            name="documento"
            required={documentKind === 'CUIT' || documentKind === 'DNI'}
            defaultValue={text('documento', target?.documento ?? '')}
            maxLength={20}
            disabled={busy}
          />
        </label>
        <label>
          Comprobante asociado (notas de crédito o débito)
          <input
            name="comprobanteAsociado"
            required={tipo === 'NOTA_CREDITO' || tipo === 'NOTA_DEBITO'}
            defaultValue={text('comprobante_asociado')}
            maxLength={100}
            disabled={busy}
          />
        </label>
        <div className="fiscal-rates">
          <h3>Desglose del comprobante</h3>
          <p>
            No se calcula IVA desde el total. Ingresá las bases y el IVA discriminado según el
            documento.
          </p>
          <table>
            <thead>
              <tr>
                <th>Alícuota</th>
                <th>Neto gravado</th>
                <th>IVA discriminado</th>
              </tr>
            </thead>
            <tbody>
              {lines.map((line, index) => (
                <tr key={line.alicuota}>
                  <td>{line.alicuota}%</td>
                  <td>
                    <input
                      data-tax-amount
                      aria-label={`Neto ${line.alicuota}%`}
                      type="number"
                      min="0"
                      step="0.01"
                      value={line.neto}
                      required
                      disabled={busy || tipo === 'INTERNO_NO_FISCAL'}
                      onChange={(event) =>
                        setLines((current) =>
                          current.map((row, i) =>
                            i === index ? { ...row, neto: event.target.value } : row,
                          ),
                        )
                      }
                    />
                  </td>
                  <td>
                    <input
                      data-tax-amount
                      aria-label={`IVA ${line.alicuota}%`}
                      type="number"
                      min="0"
                      step="0.01"
                      value={line.iva}
                      required
                      disabled={busy || tipo === 'INTERNO_NO_FISCAL'}
                      onChange={(event) =>
                        setLines((current) =>
                          current.map((row, i) =>
                            i === index ? { ...row, iva: event.target.value } : row,
                          ),
                        )
                      }
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {(
          [
            ['exento', 'Importe exento', 'exento'],
            ['noGravado', 'Importe no gravado', 'no_gravado'],
            ['otrosTributos', 'Otros tributos', 'otros_tributos'],
            ['percepcionIva', 'Percepción de IVA', 'percepcion_iva'],
            ['percepcionIibb', 'Percepción de Ingresos Brutos', 'percepcion_iibb'],
          ] as const
        ).map(([name, label, key]) => (
          <label key={name}>
            {label}
            <input
              name={name}
              data-tax-amount
              type="number"
              min="0"
              step="0.01"
              defaultValue={text(key, '0')}
              required
              disabled={busy}
            />
          </label>
        ))}
        <p className="fiscal-wide" role="status">
          Suma del desglose: {currency.format(calculated)} · Diferencia con el total:{' '}
          {currency.format(Number(total) - calculated)}. Las notas de crédito se restan en el
          informe.
        </p>
        <label>
          Crédito fiscal computable de compras / gastos
          <input
            name="creditoComputable"
            type="number"
            min="0"
            step="0.01"
            value={credit}
            onChange={(event) => setCredit(event.target.value)}
            required
            disabled={busy || clase === 'VENTA' || letra !== 'A'}
          />
          <small>No se asigna automáticamente; requiere revisión de su procedencia.</small>
        </label>
        <label>
          Total del comprobante
          <input
            name="total"
            type="number"
            min="0.01"
            step="0.01"
            value={total}
            onChange={(event) => setTotal(event.target.value)}
            readOnly={!!target && (tipo === 'FACTURA' || tipo === 'INTERNO_NO_FISCAL')}
            required
            disabled={busy}
          />
        </label>
        <label>
          Tipo de autorización
          <select
            name="autorizacionTipo"
            value={authType}
            onChange={(event) => setAuthType(event.target.value)}
            disabled={busy}
          >
            <option value="">Sin registrar / no corresponde</option>
            <option>CAE</option>
            <option>CAI</option>
            <option>CAEA</option>
          </select>
        </label>
        <label>
          Código de autorización
          <input
            name="autorizacionCodigo"
            required={authType !== ''}
            pattern="[0-9]{14}"
            inputMode="numeric"
            maxLength={14}
            defaultValue={text('autorizacion_codigo')}
            disabled={busy}
          />
        </label>
        <label className="fiscal-wide">
          Observaciones / diferencias de redondeo
          <textarea
            name="observaciones"
            maxLength={500}
            defaultValue={text('observaciones')}
            disabled={busy}
          />
        </label>
        <label className="fiscal-wide">
          <span>
            <input
              name="controlado"
              type="checkbox"
              defaultChecked={Boolean(initial?.controlado)}
              disabled={busy || !canReview}
            />{' '}
            Datos controlados para el informe del contador
          </span>
        </label>
        {message && (
          <p className="fiscal-wide" role="alert">
            {message}
          </p>
        )}
        <div className="row-actions fiscal-wide">
          <button className="secondary-button" type="button" disabled={busy} onClick={onClose}>
            Volver sin guardar
          </button>
          <button className="primary-button" type="submit" disabled={busy}>
            {busy ? 'Guardando…' : 'Guardar desglose fiscal'}
          </button>
        </div>
      </form>
    </dialog>
  )
}
export function FiscalCapture({ canReview = false }: { canReview?: boolean }) {
  const options = useAccountingOptions()
  const [target, setTarget] = useState<FiscalTarget | null>(null),
    [open, setOpen] = useState(false)
  useEffect(() => {
    const receive = (event: Event) => {
      if (!options.cargaImpuestos) return
      setTarget((event as CustomEvent<FiscalTarget>).detail)
      setOpen(true)
    }
    window.addEventListener('alon:fiscal-target', receive)
    return () => window.removeEventListener('alon:fiscal-target', receive)
  }, [options.cargaImpuestos])
  if (!target || !options.cargaImpuestos) return null
  return (
    <>
      <div className="notice no-print">
        <span>La operación #{target.id} tiene pendiente completar sus impuestos.</span>
        <button className="secondary-button" type="button" onClick={() => setOpen(true)}>
          Completar impuestos
        </button>
      </div>
      {open && (
        <FiscalEditor
          target={target}
          canReview={canReview}
          onClose={() => setOpen(false)}
          onSaved={() => {
            setTarget(null)
            setOpen(false)
          }}
        />
      )}
    </>
  )
}
