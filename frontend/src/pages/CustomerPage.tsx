import { useState } from 'react'
import { apiFetch } from '../lib/http'
import { currency, textValue, numberValue } from '../lib/format'
import { useApiRows, type ApiRow } from '../hooks/useApiRows'
import { Pagination } from '../components/Pagination'
import { LocalEntryPanel, StatusPill } from '../components/Forms'

export function CustomerList() {
  const [query, setQuery] = useState('')
  const [showInactive, setShowInactive] = useState(false)
  const [editing, setEditing] = useState<ApiRow | null>(null)
  const apiRows = useApiRows(
    '/api/v1/demo/customers?inactive=' + showInactive + '&q=' + encodeURIComponent(query),
  )
  const customers = apiRows.map((row) => ({
    id: numberValue(row.id),
    raw: row,
    code: textValue(row.codigo),
    name: textValue(row.nombre),
    phone: textValue(row.telefono),
    email: textValue(row.email),
    currentAccount: numberValue(row.saldo_cuenta_corriente),
    creditLimit: numberValue(row.limite_credito),
    status: textValue(row.estado) === 'ACTIVO' ? 'Activo' : 'Inactivo',
  }))
  const filtered = customers.filter((customer) =>
    `${customer.code} ${customer.name} ${customer.email}`
      .toLocaleLowerCase('es-AR')
      .includes(query.toLocaleLowerCase('es-AR')),
  )
  const refresh = () => window.dispatchEvent(new Event('alon:refresh'))
  const setActive = async (id: number, active: boolean) => {
    const response = await apiFetch(
      `/api/v1/operations/customers/${id}/${active ? 'activate' : 'deactivate'}`,
      { method: 'POST' },
    )
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      window.alert(body.error ?? 'No se pudo cambiar el estado del cliente.')
      return
    }
    refresh()
  }
  return (
    <>
      {editing ? (
        <LocalEntryPanel
          key={'customer-' + textValue(editing.id)}
          title={'Modificar cliente · ' + textValue(editing.nombre)}
          action="Guardar cambios"
          endpoint={'/api/v1/operations/customers/' + textValue(editing.id)}
          method="PUT"
          initialOpen
          panelId="customer-editor"
          onSaved={() => setEditing(null)}
          fields={[
            { name: 'name', label: 'Nombre y apellido', defaultValue: textValue(editing.nombre) },
            { name: 'phone', label: 'Teléfono', defaultValue: textValue(editing.telefono) },
            {
              name: 'email',
              label: 'Email',
              type: 'email',
              defaultValue: textValue(editing.email),
            },
            {
              name: 'creditLimit',
              label: 'Límite de crédito',
              type: 'number',
              defaultValue: textValue(editing.limite_credito),
            },
          ]}
        />
      ) : (
        <LocalEntryPanel
          title="Nuevo cliente"
          action="Cargar cliente"
          endpoint="/api/v1/operations/customers"
          fields={[
            { name: 'name', label: 'Nombre y apellido' },
            { name: 'phone', label: 'Teléfono' },
            { name: 'email', label: 'Email', type: 'email' },
            { name: 'creditLimit', label: 'Límite de crédito', type: 'number' },
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
      <section className="directory-panel">
        <div className="directory-toolbar">
          <div>
            <span className="demo-tag">
              {showInactive ? 'CLIENTES DESACTIVADOS' : 'CLIENTES ACTIVOS'}
            </span>
            <strong>{filtered.length} clientes</strong>
          </div>
          <div className="directory-filters">
            <input
              type="search"
              aria-label="Buscar clientes"
              placeholder="Buscar por nombre, código o email"
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
                <th>CLIENTE</th>
                <th>CONTACTO</th>
                <th>CUENTA CORRIENTE</th>
                <th>LÍMITE DE CRÉDITO</th>
                <th>ESTADO</th>
                <th>ACCIONES</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((customer) => (
                <tr key={customer.code}>
                  <td data-label="Código" className="mono-cell">
                    {customer.code}
                  </td>
                  <td data-label="Cliente" className="strong-cell">
                    {customer.name}
                  </td>
                  <td data-label="Contacto">
                    <span className="contact-cell">
                      {customer.phone}
                      <small>{customer.email}</small>
                    </span>
                  </td>
                  <td
                    data-label="Cuenta corriente"
                    className={customer.currentAccount > 0 ? 'balance-due' : ''}
                  >
                    {currency.format(customer.currentAccount)}
                  </td>
                  <td data-label="Límite de crédito">{currency.format(customer.creditLimit)}</td>
                  <td data-label="Estado">
                    <StatusPill active={customer.status === 'Activo'} />
                  </td>
                  <td data-label="Acciones" className="article-actions">
                    {customer.id > 0 && (
                      <>
                        {!showInactive && (
                          <button
                            className="secondary-button"
                            type="button"
                            onClick={() => setEditing(customer.raw)}
                          >
                            Modificar
                          </button>
                        )}
                        <button
                          className="secondary-button"
                          type="button"
                          onClick={() =>
                            void setActive(customer.id, showInactive).catch(() => undefined)
                          }
                        >
                          {showInactive ? 'Activar' : 'Desactivar'}
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!apiRows.loading && !apiRows.error && filtered.length === 0 && (
            <div className="no-results">
              {showInactive
                ? 'No hay clientes desactivados.'
                : 'No hay clientes que coincidan con la búsqueda.'}
            </div>
          )}
        </div>
        <Pagination rows={apiRows} label="clientes" />
        <div className="table-footer">
          Datos del comercio; los importes se muestran en pesos argentinos.
        </div>
      </section>
    </>
  )
}
