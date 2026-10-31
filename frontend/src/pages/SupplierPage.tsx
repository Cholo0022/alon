// Permite consultar proveedores y registrar los pagos de sus cuentas.
import { useRef, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import {
  currency,
  textValue,
  numberValue,
  formatDateDisplay,
  dateDisplayToIso,
} from '../lib/format'
import { useApiRows, type ApiRow } from '../hooks/useApiRows'
import { Pagination } from '../components/Pagination'
import { DateTextInput, LocalEntryPanel, StatusPill } from '../components/Forms'

export function SupplierList() {
  const [query, setQuery] = useState('')
  const [showInactive, setShowInactive] = useState(false)
  const [editing, setEditing] = useState<ApiRow | null>(null)
  const [showDebtList, setShowDebtList] = useState(false)
  const [selectedSupplier, setSelectedSupplier] = useState<{
    id: number
    name: string
    balance: number
  } | null>(null)
  const [amount, setAmount] = useState('0')
  const [receipt, setReceipt] = useState<ApiRow | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const receiptFlowRef = useRef<HTMLElement>(null)
  const debtListRef = useRef<HTMLElement>(null)
  const apiRows = useApiRows(
    '/api/v1/alon/suppliers?inactive=' + showInactive + '&q=' + encodeURIComponent(query),
  )
  const suppliers = apiRows.map((row) => ({
    id: numberValue(row.id),
    raw: row,
    code: textValue(row.codigo),
    name: textValue(row.nombre),
    specialty: textValue(row.especialidad),
    phone: textValue(row.telefono),
    email: textValue(row.email),
    balance: numberValue(row.saldo_pendiente),
    status: textValue(row.estado) === 'ACTIVO' ? 'Activo' : 'Inactivo',
  }))
  const filtered = suppliers.filter((supplier) =>
    `${supplier.code} ${supplier.name} ${supplier.specialty} ${supplier.email}`
      .toLocaleLowerCase('es-AR')
      .includes(query.toLocaleLowerCase('es-AR')),
  )
  const owedSuppliers = filtered.filter(
    (supplier) => supplier.balance > 0 && supplier.status === 'Activo',
  )
  const setSupplierActive = async (id: number, active: boolean) => {
    const response = await apiFetch(
      `/api/v1/operations/suppliers/${id}/${active ? 'activate' : 'deactivate'}`,
      { method: 'POST' },
    )
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      window.alert(body.error ?? 'No se pudo cambiar el estado del proveedor.')
      return
    }
    window.dispatchEvent(new Event('alon:refresh'))
  }
  const startReceipt = (supplier: { id: number; name: string; balance: number }) => {
    setShowDebtList(true)
    setSelectedSupplier(supplier)
    setAmount(supplier.balance.toFixed(2))
    setMessage('')
    setReceipt(null)
    window.setTimeout(
      () => receiptFlowRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
      60,
    )
  }
  const finishReceipt = () => {
    setReceipt(null)
    setSelectedSupplier(null)
    setAmount('0')
    setMessage('')
    setShowDebtList(true)
    window.setTimeout(
      () => debtListRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
      60,
    )
  }
  const printReceipt = () => {
    window.print()
    finishReceipt()
  }
  const saveReceipt = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!selectedSupplier) return
    const values = new FormData(event.currentTarget)
    const date = dateDisplayToIso(values.get('date'))
    const paymentAmount = Number(amount)
    if (!date) {
      setMessage('Ingresá una fecha válida con el formato dd/mm/aaaa.')
      return
    }
    if (!(paymentAmount > 0) || paymentAmount > selectedSupplier.balance) {
      setMessage('El importe debe ser mayor a cero y no puede superar el saldo pendiente.')
      return
    }
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch('/api/v1/operations/suppliers/receipt', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          supplierId: selectedSupplier.id,
          date,
          amount: paymentAmount,
          paymentMethod: values.get('paymentMethod'),
        }),
      })
      const body = (await response.json().catch(() => ({}))) as ApiRow
      if (!response.ok)
        throw new Error(String(body.message ?? body.error ?? 'No se pudo registrar el recibo.'))
      setReceipt(body)
      setSelectedSupplier(null)
      window.dispatchEvent(new Event('alon:refresh'))
      setMessage('Recibo registrado. El pago se aplicó al saldo del proveedor.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el servidor.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      {editing ? (
        <LocalEntryPanel
          key={'supplier-' + textValue(editing.id)}
          title={'Modificar proveedor · ' + textValue(editing.nombre)}
          action="Guardar cambios"
          endpoint={'/api/v1/operations/suppliers/' + textValue(editing.id)}
          method="PUT"
          initialOpen
          panelId="supplier-editor"
          onSaved={() => setEditing(null)}
          fields={[
            {
              name: 'name',
              label: 'Razón social o nombre',
              defaultValue: textValue(editing.nombre),
            },
            { name: 'taxId', label: 'CUIT', defaultValue: textValue(editing.cuit) },
            {
              name: 'specialty',
              label: 'Rubro o especialidad',
              defaultValue: textValue(editing.especialidad),
            },
            { name: 'phone', label: 'Teléfono', defaultValue: textValue(editing.telefono) },
            {
              name: 'email',
              label: 'Email',
              type: 'email',
              defaultValue: textValue(editing.email),
            },
          ]}
        />
      ) : (
        <LocalEntryPanel
          title="Nuevo proveedor"
          action="Cargar proveedor"
          endpoint="/api/v1/operations/suppliers"
          fields={[
            { name: 'name', label: 'Razón social o nombre' },
            { name: 'taxId', label: 'CUIT' },
            { name: 'specialty', label: 'Rubro o especialidad' },
            { name: 'phone', label: 'Teléfono' },
            { name: 'email', label: 'Email', type: 'email' },
          ]}
        />
      )}
      {editing && (
        <button
          className="secondary-button article-cancel-edit"
          type="button"
          onClick={() => setEditing(null)}
        >
          Volver al listado
        </button>
      )}
      <section className="supplier-debt-toolbar">
        <div>
          <span className="alon-tag">CUENTAS A PAGAR</span>
          <strong>
            {suppliers.filter((supplier) => supplier.balance > 0).length} proveedores con saldo
            pendiente
          </strong>
        </div>
        <button
          className="primary-button"
          type="button"
          onClick={() => setShowDebtList((current) => !current)}
        >
          {showDebtList ? 'Ocultar saldos pendientes' : 'Ver proveedores a los que debo'}
        </button>
      </section>
      {showDebtList && (
        <section className="directory-panel supplier-debt-list" ref={debtListRef}>
          <div className="panel-heading">
            <div>
              <h2>Proveedores con saldo pendiente</h2>
              <p>Elegí un proveedor para revisar y confirmar el recibo de pago.</p>
            </div>
          </div>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>PROVEEDOR</th>
                  <th>SALDO PENDIENTE</th>
                  <th>ACCIÓN</th>
                </tr>
              </thead>
              <tbody>
                {owedSuppliers.map((supplier) => (
                  <tr key={supplier.id || supplier.code}>
                    <td data-label="Proveedor" className="strong-cell">
                      {supplier.name}
                    </td>
                    <td data-label="Saldo pendiente" className="balance-due">
                      {currency.format(supplier.balance)}
                    </td>
                    <td data-label="Acción">
                      <button
                        type="button"
                        className="primary-button"
                        disabled={!supplier.id}
                        onClick={() => startReceipt(supplier)}
                      >
                        Hacer recibo
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {owedSuppliers.length === 0 && (
              <div className="no-results">No hay proveedores con saldo pendiente.</div>
            )}
          </div>
        </section>
      )}
      {selectedSupplier && (
        <section className="entry-panel supplier-receipt-form" ref={receiptFlowRef}>
          <div className="entry-heading">
            <div>
              <span className="alon-tag">CONFIRMAR RECIBO</span>
              <strong>{selectedSupplier.name}</strong>
              <small>Saldo pendiente: {currency.format(selectedSupplier.balance)}</small>
            </div>
            <button
              className="select-button"
              type="button"
              onClick={() => setSelectedSupplier(null)}
            >
              Volver al listado
            </button>
          </div>
          <p>
            Revisá el importe y el medio de pago antes de confirmar. Al confirmar, el pago se
            aplicará a las facturas pendientes más antiguas.
          </p>
          <form className="entry-form" onSubmit={saveReceipt}>
            <label>
              Fecha
              <DateTextInput name="date" />
            </label>
            <label>
              Importe a pagar
              <input
                aria-label="Importe a pagar"
                type="number"
                min="0.01"
                max={selectedSupplier.balance}
                step="0.01"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                required
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
            <div className="entry-actions">
              <span>El saldo se actualizará al confirmar.</span>
              <button className="primary-button" type="submit" disabled={busy}>
                {busy ? 'Confirmando…' : 'Confirmar recibo y pago'}
              </button>
            </div>
          </form>
        </section>
      )}
      {receipt && (
        <section className="supplier-receipt-print">
          <div className="receipt-heading">
            <span className="brand-name">
              alon<span>.</span>
            </span>
            <span>RECIBO DE PAGO</span>
          </div>
          <h2>Recibo {textValue(receipt.recibo)}</h2>
          <p>
            Proveedor: <strong>{textValue(receipt.proveedor)}</strong>
          </p>
          <p>
            Fecha: <strong>{formatDateDisplay(receipt.fecha)}</strong>
          </p>
          <p>
            Medio de pago: <strong>{textValue(receipt.medio_pago)}</strong>
          </p>
          <p>
            Importe recibido: <strong>{currency.format(numberValue(receipt.importe))}</strong>
          </p>
          <p>Saldo anterior: {currency.format(numberValue(receipt.saldo_anterior))}</p>
          <p>
            Saldo pendiente: <strong>{currency.format(numberValue(receipt.saldo_restante))}</strong>
          </p>
          <p>Facturas alcanzadas: {textValue(receipt.facturas_aplicadas)}</p>
          <div className="receipt-actions no-print">
            <button className="primary-button" type="button" onClick={printReceipt}>
              Imprimir recibo
            </button>
            <button className="select-button" type="button" onClick={finishReceipt}>
              No imprimir
            </button>
          </div>
        </section>
      )}
      {message && (
        <p className="entry-message" role="status">
          {message}
        </p>
      )}
      <section className="directory-panel">
        <div className="directory-toolbar">
          <div>
            <span className="alon-tag">
              {showInactive ? 'PROVEEDORES DESACTIVADOS' : 'PROVEEDORES ACTIVOS'}
            </span>
            <strong>{filtered.length} proveedores</strong>
          </div>
          <div className="directory-filters">
            <input
              type="search"
              aria-label="Buscar proveedores"
              placeholder="Buscar por nombre, rubro o código"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
            <button
              className="secondary-button"
              type="button"
              onClick={() => {
                setShowInactive((value) => !value)
                setQuery('')
                setEditing(null)
              }}
            >
              {showInactive ? 'Ver activos' : 'Ver desactivados'}
            </button>
          </div>
        </div>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>CÓDIGO</th>
                <th>PROVEEDOR</th>
                <th>ESPECIALIDAD</th>
                <th>CONTACTO</th>
                <th>SALDO PENDIENTE</th>
                <th>ESTADO</th>
                <th>ACCIONES</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((supplier) => (
                <tr key={supplier.code}>
                  <td data-label="Código" className="mono-cell">
                    {supplier.code}
                  </td>
                  <td data-label="Proveedor" className="strong-cell">
                    {supplier.name}
                  </td>
                  <td data-label="Especialidad">
                    <span className="category-chip">{supplier.specialty}</span>
                  </td>
                  <td data-label="Contacto">
                    <span className="contact-cell">
                      {supplier.phone}
                      <small>{supplier.email}</small>
                    </span>
                  </td>
                  <td
                    data-label="Saldo pendiente"
                    className={supplier.balance > 0 ? 'balance-due' : ''}
                  >
                    {currency.format(supplier.balance)}
                  </td>
                  <td data-label="Estado">
                    <StatusPill active={supplier.status === 'Activo'} />
                  </td>
                  <td data-label="Acciones" className="article-actions">
                    <div className="row-actions">
                      {supplier.id > 0 && (
                        <>
                          {!showInactive && (
                            <>
                              <button
                                className="secondary-button"
                                type="button"
                                onClick={() => setEditing(supplier.raw)}
                              >
                                Modificar
                              </button>
                              <button
                                className="select-button"
                                type="button"
                                disabled={supplier.balance <= 0}
                                onClick={() => startReceipt(supplier)}
                              >
                                Recibo
                              </button>
                            </>
                          )}
                          <button
                            className="secondary-button"
                            type="button"
                            onClick={() =>
                              void setSupplierActive(supplier.id, showInactive).catch(
                                () => undefined,
                              )
                            }
                          >
                            {showInactive ? 'Activar' : 'Desactivar'}
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!apiRows.loading && !apiRows.error && filtered.length === 0 && (
            <div className="no-results">
              {showInactive
                ? 'No hay proveedores desactivados.'
                : 'No hay proveedores que coincidan con la búsqueda.'}
            </div>
          )}
        </div>
        <Pagination rows={apiRows} label="proveedores" />
        <div className="table-footer">Datos de contacto y saldos del comercio.</div>
      </section>
    </>
  )
}
