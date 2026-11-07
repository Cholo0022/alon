import { useEffect, useState } from 'react'
import type { ApiRow } from '../hooks/useApiRows'
import { apiFetch } from '../lib/http'
import { numberValue, textValue, currency } from '../lib/format'
import type { PurchaseLine } from '../lib/purchaseDraft'
import { PurchaseDialog } from './PurchaseDialog'

/** Carga cantidades de las variantes existentes sin mezclar sus códigos o existencias. */
export function PurchaseVariantsDialog({
  article,
  onClose,
  onAdd,
}: {
  article: ApiRow
  onClose: () => void
  onAdd: (lines: PurchaseLine[]) => void
}) {
  const [rows, setRows] = useState<ApiRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [quantities, setQuantities] = useState<Record<string, string>>({})
  const [cost, setCost] = useState(String(numberValue(article.costo)))
  useEffect(() => {
    const controller = new AbortController()
    void (async () => {
      try {
        const all: ApiRow[] = []
        for (let page = 0; ; page++) {
          const response = await apiFetch(
            `/api/v1/operations/purchases/variants?articleId=${Number(article.id)}&size=200&page=${page}`,
            { signal: controller.signal },
          )
          if (!response.ok) throw new Error('No se pudieron consultar los talles y colores.')
          const chunk: unknown = await response.json()
          if (!Array.isArray(chunk)) throw new Error('La respuesta de variantes no es válida.')
          all.push(...(chunk as ApiRow[]))
          if (chunk.length < 200) break
          if (all.length >= 2000)
            throw new Error('Este modelo tiene demasiadas variantes. Cargalas individualmente.')
        }
        if (!controller.signal.aborted) {
          setRows(all)
          setLoading(false)
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setError(error instanceof Error ? error.message : 'No se pudieron cargar las variantes.')
          setLoading(false)
        }
      }
    })()
    return () => controller.abort()
  }, [article.id])
  const sizes = [...new Set(rows.map((row) => textValue(row.talle)))].sort((a, b) =>
    a.localeCompare(b, 'es', { numeric: true }),
  )
  const colors = [...new Set(rows.map((row) => textValue(row.color)))]
  const totalUnits = rows.reduce((sum, row) => sum + Number(quantities[textValue(row.id)] || 0), 0)
  return (
    <PurchaseDialog title="Cantidades recibidas por talle" onClose={onClose} wide>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          const chosen = rows.filter((row) => Number(quantities[textValue(row.id)] || 0) !== 0)
          if (!chosen.length) {
            setError('Ingresá la cantidad de al menos un talle y color.')
            return
          }
          if (
            !cost.trim() ||
            !Number.isFinite(Number(cost)) ||
            Number(cost) < 0 ||
            rows.some((row) => {
              const amount = Number(quantities[textValue(row.id)] || 0)
              return !Number.isSafeInteger(amount) || amount < 0
            })
          ) {
            setError('Usá cantidades enteras y un costo válido.')
            return
          }
          onAdd(
            chosen.map((row) => ({
              articleId: Number(row.id),
              label: textValue(row.codigo) + ' · ' + textValue(row.nombre),
              quantity: Number(quantities[textValue(row.id)]),
              unitCost: Number(cost),
              wholeUnits: true,
            })),
          )
        }}
      >
        <p>
          <strong>
            {textValue(article.marca)} · {textValue(article.modelo)}
          </strong>{' '}
          · Sistema de talles: {textValue(article.sistema_talle)}
        </p>
        <p>
          Indicá las cantidades recibidas, no el stock total que ya tenés. Cargá solo las combinaciones que recibiste. Cada código mantiene su stock
          propio. Las variantes nuevas se crean desde «Crear producto».
        </p>
        <label>
          Costo unitario común
          <input
            type="number"
            min="0"
            step="0.01"
            required
            value={cost}
            onChange={(event) => setCost(event.target.value)}
          />
        </label>
        {loading ? (
          <p role="status">Cargando variantes…</p>
        ) : rows.length ? (
          <div className="table-scroll">
            <table className="purchase-variant-table">
              <thead>
                <tr>
                  <th>Cantidades recibidas · color / talle</th>
                  {sizes.map((size) => (
                    <th key={size}>{size}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {colors.map((color) => (
                  <tr key={color}>
                    <th scope="row">{color}</th>
                    {sizes.map((size) => (
                      <td key={size}>
                        {rows
                          .filter(
                            (row) =>
                              textValue(row.color) === color && textValue(row.talle) === size,
                          )
                          .map((row) => (
                            <label key={textValue(row.id)}>
                              <small>{textValue(row.codigo)}</small>
                              <input
                                aria-label={`${color} · ${size} · ${textValue(row.codigo)}`}
                                type="number"
                                inputMode="numeric"
                                min="0"
                                step="1"
                                value={quantities[textValue(row.id)] ?? ''}
                                onChange={(event) =>
                                  setQuantities((current) => ({
                                    ...current,
                                    [textValue(row.id)]: event.target.value,
                                  }))
                                }
                              />
                            </label>
                          ))}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          !error && <p>No hay variantes activas para este modelo.</p>
        )}
        {error && <p role="alert">{error}</p>}
        <p>
          {totalUnits} unidades · {currency.format(totalUnits * Number(cost || 0))}
        </p>
        <div className="entry-actions">
          <button type="button" className="secondary-button" onClick={onClose}>
            Volver
          </button>
          <button className="primary-button" disabled={loading || !rows.length}>
            Agregar combinaciones al comprobante
          </button>
        </div>
      </form>
    </PurchaseDialog>
  )
}
