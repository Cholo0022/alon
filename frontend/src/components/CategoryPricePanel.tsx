// Muestra una vista previa antes de aplicar un aumento de precios a un rubro.
import { useRef, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { useApiRows } from '../hooks/useApiRows'
import { currency, textValue } from '../lib/format'

type Preview = {
  count: number
  expectedHash: string
  sample: { id: number; name: string; previousPrice: number; newPrice: number }[]
}
export function CategoryPricePanel() {
  const categories = useApiRows('/api/v1/alon/categories')
  const [open, setOpen] = useState(false)
  const [categoryId, setCategoryId] = useState('')
  const [percent, setPercent] = useState('')
  const [preview, setPreview] = useState<Preview | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const requestVersion = useRef(0)
  const invalidate = () => {
    requestVersion.current++
    setPreview(null)
    setMessage('')
  }
  const request = async (apply: boolean) => {
    if (busy) return
    const current = ++requestVersion.current
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch(
        '/api/v1/operations/articles/prices' + (apply ? '' : '/preview'),
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            categoryId: Number(categoryId),
            percent: Number(percent),
            ...(apply ? { expectedHash: preview?.expectedHash } : {}),
          }),
        },
      )
      const result = await response.json()
      if (current !== requestVersion.current) return
      if (!response.ok) {
        if (response.status === 409) setPreview(null)
        throw new Error(result.error ?? 'No se pudieron modificar los precios.')
      }
      if (apply) {
        setPreview(null)
        setMessage(
          'Se actualizaron ' +
            result.updated +
            ' artículos. Los nuevos precios ya están disponibles en la tienda web.',
        )
        window.dispatchEvent(new Event('alon:refresh'))
      } else setPreview(result as Preview)
    } catch (error) {
      if (current === requestVersion.current)
        setMessage(error instanceof Error ? error.message : 'No se pudo conectar.')
    } finally {
      setBusy(false)
    }
  }
  const submit = (event: FormEvent) => {
    event.preventDefault()
    void request(false)
  }
  return (
    <section className="entry-panel category-price-panel">
      <div className="entry-heading">
        <div>
          <span className="alon-tag">PRECIOS POR RUBRO</span>
          <strong>Modificar precios</strong>
        </div>
        <button
          type="button"
          className="secondary-button"
          disabled={busy}
          onClick={() => {
            invalidate()
            setOpen((v) => !v)
          }}
        >
          {open ? 'Cerrar' : 'Modificar precios'}
        </button>
      </div>
      {open && (
        <form className="entry-form" onSubmit={submit}>
          <label>
            Rubro
            <select
              value={categoryId}
              onChange={(e) => {
                invalidate()
                setCategoryId(e.target.value)
              }}
              required
              disabled={busy}
            >
              <option value="">Elegí un rubro</option>
              {categories.map((row) => (
                <option key={textValue(row.id)} value={textValue(row.id)}>
                  {textValue(row.nombre)}
                </option>
              ))}
            </select>
          </label>
          <label>
            Aumento porcentual
            <input
              type="number"
              min="0.01"
              max="10000"
              step="0.01"
              required
              value={percent}
              onChange={(e) => {
                invalidate()
                setPercent(e.target.value)
              }}
              disabled={busy}
              placeholder="Ej.: 10"
            />
          </label>
          <div className="entry-actions">
            <span>
              Se aumenta el precio de venta actual de todos los artículos activos del rubro,
              respetando su opción de redondeo.
            </span>
            <button className="primary-button" disabled={busy || categories.loading}>
              {busy ? 'Procesando…' : 'Ver aumento'}
            </button>
          </div>
          {preview && (
            <div className="price-preview">
              <strong>
                Se modificarán {preview.count} artículos · Aumento {percent}%
              </strong>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>ARTÍCULO</th>
                      <th>PRECIO ACTUAL</th>
                      <th>NUEVO PRECIO</th>
                    </tr>
                  </thead>
                  <tbody>
                    {preview.sample.map((row) => (
                      <tr key={row.id}>
                        <td>{row.name}</td>
                        <td>{currency.format(Number(row.previousPrice))}</td>
                        <td>{currency.format(Number(row.newPrice))}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {preview.count > preview.sample.length && (
                <p>
                  Se muestran {preview.sample.length} ejemplos de los {preview.count} artículos.
                </p>
              )}
              <button
                className="primary-button"
                type="button"
                disabled={busy}
                onClick={() => void request(true)}
              >
                Aplicar aumento a {preview.count} artículos
              </button>
            </div>
          )}
        </form>
      )}
      {message && (
        <p role="status" className="entry-message">
          {message}
        </p>
      )}
    </section>
  )
}
