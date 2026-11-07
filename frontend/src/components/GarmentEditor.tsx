import { useEffect, useRef, useState } from 'react'
import type { ApiRow } from '../hooks/useApiRows'
import { apiFetch } from '../lib/http'
import { textValue, numberValue } from '../lib/format'
import {
  savedPhotos,
  photoPayload,
  type ArticlePhoto,
  type SavedArticlePhoto,
} from '../lib/articlePhotos'
import { ArticlePhotosField } from './ArticlePhotosField'
import { DeleteRecordButton } from './DeleteRecordButton'
import { clearFieldValidation, showFieldErrors, validateForm } from '../lib/formValidation'
import { preventImplicitArticleSubmit } from '../lib/articleKeyboard'
import './article-picker.css'

type Snapshot = { product: ApiRow; variants: ApiRow[]; token: string }
type StockRow = { id: number; size: string; color: string; stock: string; code: string }
type NewRow = { key: string; size: string; color: string; stock: string; code: string }
export function GarmentEditor({
  articleId,
  canEdit,
  onBack,
  onSaved,
}: {
  articleId: number
  canEdit: boolean
  onBack: () => void
  onSaved: () => void
}) {
  const [data, setData] = useState<Snapshot | null>(null),
    [error, setError] = useState(''),
    [version, setVersion] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    void apiFetch(`/api/v1/alon/articles/${articleId}/group`, { signal: controller.signal })
      .then(async (response) => {
        const body = await response.json()
        if (!response.ok) throw new Error(body.error ?? 'No se pudo abrir la prenda.')
        return body as Snapshot
      })
      .then((body) => {
        if (!controller.signal.aborted) setData(body)
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setError(error instanceof Error ? error.message : 'No se pudo abrir la prenda.')
      })
    return () => controller.abort()
  }, [articleId, version])
  return data ? (
    <GarmentEditorForm
      key={data.token}
      data={data}
      canEdit={canEdit}
      onBack={onBack}
      onSaved={onSaved}
      onReload={() => {
        if (
          !window.confirm(
            '¿Volver a consultar la prenda? Se descartarán las modificaciones sin guardar.',
          )
        )
          return
        setData(null)
        setError('')
        setVersion((value) => value + 1)
      }}
    />
  ) : (
    <section className="entry-panel">
      <p role={error ? 'alert' : 'status'}>{error || 'Abriendo la prenda y sus talles…'}</p>
      <button className="secondary-button" type="button" onClick={onBack}>
        Volver al listado
      </button>
    </section>
  )
}
function GarmentEditorForm({
  data,
  canEdit,
  onBack,
  onSaved,
  onReload,
}: {
  data: Snapshot
  canEdit: boolean
  onBack: () => void
  onSaved: () => void
  onReload: () => void
}) {
  const panel = useRef<HTMLElement>(null)
  useEffect(() => {
    panel.current?.scrollIntoView?.({ block: 'start', behavior: 'instant' })
    panel.current?.querySelector<HTMLInputElement>('input')?.focus({ preventScroll: true })
  }, [])
  const { product, variants } = data
  const [rows, setRows] = useState<StockRow[]>(() =>
    variants.map((row) => ({
      id: Number(row.id),
      size: textValue(row.talle),
      color: textValue(row.color),
      stock: textValue(row.stock),
      code: textValue(row.codigo),
    })),
  )
  const [added, setAdded] = useState<NewRow[]>([])
  const shared = (key: string) =>
    variants.every((row) => numberValue(row[key]) === numberValue(variants[0][key]))
      ? textValue(variants[0][key])
      : ''
  const initial = {
    name: textValue(product.nombre_publico),
    cost: shared('costo'),
    salePrice: shared('precio_venta'),
    model: textValue(product.modelo),
    brand: textValue(product.marca),
    material: textValue(product.material),
    sizeSystem: textValue(product.sistema_talle),
    audience: textValue(product.publico),
    season: textValue(product.temporada),
    description: textValue(product.descripcion),
  }
  const [common, setCommon] = useState(initial),
    [photos, setPhotos] = useState<ArticlePhoto[]>(() =>
      savedPhotos((product.photos ?? []) as SavedArticlePhoto[]),
    ),
    [photosChanged, setPhotosChanged] = useState(false),
    [reading, setReading] = useState(false)
  const [publish, setPublish] = useState(Boolean(product.cargar_web)),
    [publishChanged, setPublishChanged] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [conflict, setConflict] = useState(false)
  const update = (key: keyof typeof common, value: string) =>
    setCommon((current) => ({ ...current, [key]: value }))
  const addSize = (size = '') => {
    const exists = rows.findIndex((row) => row.size === size)
    if (size && exists >= 0) {
      document.getElementsByName(`lines[${exists}].stock`)[0]?.focus()
      return
    }
    const pending = added.findIndex(
      (row) => row.size === size && row.color === (rows[0]?.color ?? 'Unico'),
    )
    if (size && pending >= 0) {
      document.getElementsByName(`variants[${pending}].quantity`)[0]?.focus()
      return
    }
    if (added.length >= 100) return
    setAdded((current) => [
      ...current,
      {
        key: 'VAR-' + crypto.randomUUID().slice(0, 12).toUpperCase(),
        size,
        color: rows[0]?.color ?? 'Único',
        stock: '0',
        code: '',
      },
    ])
  }
  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy || !canEdit || reading) return
    const form = event.currentTarget,
      invalid = validateForm(form)
    if (invalid) {
      setError(invalid)
      return
    }
    const changes: Record<string, unknown> = {}
    for (const key of Object.keys(common) as (keyof typeof common)[])
      if (common[key] !== initial[key]) {
        if ((key === 'cost' || key === 'salePrice') && !common[key].trim()) {
          showFieldErrors(form, { ['common.' + key]: 'Ingresá un importe válido.' })
          setError('Revisá el importe señalado.')
          return
        }
        changes[key] =
          key === 'cost' || key === 'salePrice' ? Number(common[key]) : common[key].trim()
      }
    if (photosChanged) changes.photos = photoPayload(photos)
    if (publishChanged) changes.publishOnWeb = publish
    const body = {
      token: data.token,
      lines: rows.map((row, index) => ({
        id: row.id,
        stock: Number(row.stock),
        size: row.size.trim(),
        color: row.color.trim(),
        code: row.code === textValue(variants[index].codigo) ? null : row.code.trim(),
      })),
      common: changes,
      newVariants: added.map((row) => ({
        code: row.code.trim() || row.key,
        size: row.size.trim(),
        color: row.color.trim() || 'Único',
        quantity: Number(row.stock),
      })),
    }
    const seen = new Set<string>(),
      errors: Record<string, string> = {}
    body.lines.forEach((row, index) => {
      const key = row.size.toUpperCase() + '\n' + row.color.toUpperCase()
      if (seen.has(key)) errors[`lines[${index}].size`] = 'Este talle y color ya están cargados.'
      seen.add(key)
    })
    body.newVariants.forEach((row, index) => {
      const key = row.size.toUpperCase() + '\n' + row.color.toUpperCase()
      if (seen.has(key)) errors[`variants[${index}].size`] = 'Este talle y color ya están cargados.'
      seen.add(key)
    })
    if (Object.keys(errors).length) {
      showFieldErrors(form, errors)
      setError('Revisá los talles repetidos.')
      return
    }
    setBusy(true)
    setError('')
    setConflict(false)
    try {
      const response = await apiFetch(`/api/v1/operations/articles/${Number(product.id)}/group`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      const result = await response.json()
      if (!response.ok) {
        const aliases: Record<string, string> = {}
        for (let index = 0; index < added.length; index++)
          for (const field of ['size', 'color', 'code', 'quantity'])
            aliases[`newVariants[${index}].${field}`] = `variants[${index}].${field}`
        showFieldErrors(form, result.fields ?? {}, aliases)
        setConflict(response.status === 409)
        throw new Error(result.error ?? 'No se pudo guardar la prenda.')
      }
      onSaved()
      window.dispatchEvent(new Event('alon:refresh'))
    } catch (error) {
      setError(error instanceof Error ? error.message : 'No se pudo guardar la prenda.')
      setBusy(false)
    }
  }
  const colorVisible = true
  return (
    <section ref={panel} className="entry-panel garment-editor">
      <div className="entry-heading">
        <strong>Modificar prenda · {textValue(product.nombre_publico)}</strong>
        <button type="button" className="secondary-button" disabled={busy} onClick={onBack}>
          Volver al listado
        </button>
      </div>
      <form
        className="purchase-new-apparel"
        noValidate
        onSubmit={submit}
        onKeyDown={preventImplicitArticleSubmit}
        onInputCapture={(event) => {
          clearFieldValidation(event)
          setError('')
        }}
        onChangeCapture={clearFieldValidation}
      >
        <fieldset disabled={busy || !canEdit}>
          <label>
            Nombre de la prenda
            <input
              name="common.name"
              required
              maxLength={80}
              value={common.name}
              onChange={(event) => update('name', event.target.value)}
            />
          </label>
          {canEdit && (
            <>
              <ArticlePhotosField
                value={photos}
                onChange={(value) => {
                  setPhotos(value)
                  setPhotosChanged(true)
                }}
                disabled={busy}
                onReading={setReading}
              />
              <div className="apparel-price-fields">
                <label>
                  Costo común
                  <input
                    name="common.cost"
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="Costos por talle"
                    value={common.cost}
                    onChange={(event) => update('cost', event.target.value)}
                  />
                </label>
                <label>
                  Precio de venta común
                  <input
                    name="common.salePrice"
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="Precios por talle"
                    value={common.salePrice}
                    onChange={(event) => update('salePrice', event.target.value)}
                  />
                </label>
              </div>
            </>
          )}
          <h3>Stock actual por talle</h3>
          <p>
            Estas cantidades reemplazan el stock actual de cada talle. Para recibir mercadería, usá
            Compras.
          </p>
          <div className="table-scroll">
            <table className="purchase-variant-table">
              <thead>
                <tr>
                  <th>Talle</th>
                  {colorVisible && <th>Color</th>}
                  <th>Stock actual</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row, index) => (
                  <tr key={row.id}>
                    <td>
                      <input
                        name={`lines[${index}].size`}
                        aria-label={`Talle actual ${index + 1}`}
                        required
                        maxLength={30}
                        value={row.size}
                        onChange={(event) =>
                          setRows((current) =>
                            current.map((item) =>
                              item.id === row.id ? { ...item, size: event.target.value } : item,
                            ),
                          )
                        }
                      />
                    </td>
                    {colorVisible && (
                      <td>
                        <input
                          name={`lines[${index}].color`}
                          aria-label={`Color actual ${index + 1}`}
                          required
                          maxLength={60}
                          value={row.color}
                          onChange={(event) =>
                            setRows((current) =>
                              current.map((item) =>
                                item.id === row.id ? { ...item, color: event.target.value } : item,
                              ),
                            )
                          }
                        />
                      </td>
                    )}
                    <td>
                      <input
                        name={`lines[${index}].stock`}
                        aria-label={`Stock actual ${row.size} ${row.color}`}
                        type="number"
                        required
                        min="0"
                        max="999999999999"
                        step="1"
                        value={row.stock}
                        onChange={(event) =>
                          setRows((current) =>
                            current.map((item) =>
                              item.id === row.id ? { ...item, stock: event.target.value } : item,
                            ),
                          )
                        }
                      />
                    </td>
                    <td>Ya cargado</td>
                  </tr>
                ))}
                {added.map((row, index) => (
                  <tr key={row.key}>
                    <td>
                      <input
                        name={`variants[${index}].size`}
                        aria-label={`Talle nuevo ${index + 1}`}
                        required
                        maxLength={30}
                        value={row.size}
                        onChange={(event) =>
                          setAdded((current) =>
                            current.map((item) =>
                              item.key === row.key ? { ...item, size: event.target.value } : item,
                            ),
                          )
                        }
                      />
                    </td>
                    {colorVisible && (
                      <td>
                        <input
                          name={`variants[${index}].color`}
                          aria-label={`Color nuevo ${index + 1}`}
                          required
                          maxLength={60}
                          value={row.color}
                          onChange={(event) =>
                            setAdded((current) =>
                              current.map((item) =>
                                item.key === row.key
                                  ? { ...item, color: event.target.value }
                                  : item,
                              ),
                            )
                          }
                        />
                      </td>
                    )}
                    <td>
                      <input
                        name={`variants[${index}].quantity`}
                        aria-label={`Stock nuevo ${index + 1}`}
                        type="number"
                        required
                        min="0"
                        max="1000000000"
                        step="1"
                        value={row.stock}
                        onChange={(event) =>
                          setAdded((current) =>
                            current.map((item) =>
                              item.key === row.key ? { ...item, stock: event.target.value } : item,
                            ),
                          )
                        }
                      />
                    </td>
                    <td>
                      <button
                        type="button"
                        aria-label={`Quitar talle nuevo ${index + 1}`}
                        onClick={() =>
                          setAdded((current) => current.filter((item) => item.key !== row.key))
                        }
                      >
                        Quitar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {canEdit && (
            <>
              <div className="apparel-size-buttons">
                {(product.tipo_producto === 'CALZADO'
                  ? ['35', '36', '37', '38', '39', '40', '41', '42', '43', '44', '45']
                  : ['XS', 'S', 'M', 'L', 'XL', 'XXL']
                ).map((size) => (
                  <button
                    className="secondary-button"
                    key={size}
                    type="button"
                    onClick={() => addSize(size)}
                  >
                    {size}
                  </button>
                ))}
              </div>
              <button
                className="secondary-button"
                type="button"
                data-add-variant
                disabled={added.length >= 100}
                onClick={() => addSize()}
              >
                Agregar otro talle
              </button>
            </>
          )}
          <details>
            <summary>Más opciones y códigos</summary>
            <div className="purchase-apparel-common">
              {(
                [
                  'model',
                  'brand',
                  'material',
                  'sizeSystem',
                  'audience',
                  'season',
                  'description',
                ] as const
              ).map((key) => (
                <label key={key}>
                  {
                    {
                      model: 'Modelo / referencia',
                      brand: 'Marca',
                      material: 'Material',
                      sizeSystem: 'Sistema de talles',
                      audience: 'Público',
                      season: 'Temporada',
                      description: 'Descripción',
                    }[key]
                  }
                  <input
                    name={'common.' + key}
                    required
                    maxLength={
                      key === 'description'
                        ? 4000
                        : key === 'material'
                          ? 120
                          : key === 'sizeSystem'
                            ? 30
                            : ['audience', 'season'].includes(key)
                              ? 40
                              : 80
                    }
                    value={common[key]}
                    onChange={(event) => update(key, event.target.value)}
                  />
                </label>
              ))}
              {rows.map((row, index) => (
                <label key={row.id}>
                  Código · {row.size} · {row.color}
                  <input
                    name={`lines[${index}].code`}
                    aria-label={`Código actual ${index + 1}`}
                    required
                    maxLength={30}
                    value={row.code}
                    onChange={(event) =>
                      setRows((current) =>
                        current.map((item) =>
                          item.id === row.id ? { ...item, code: event.target.value } : item,
                        ),
                      )
                    }
                  />
                </label>
              ))}
              {added.map((row, index) => (
                <label key={row.key}>
                  Código del talle nuevo {row.size}
                  <input
                    name={`variants[${index}].code`}
                    aria-label={`Código nuevo ${index + 1}`}
                    maxLength={30}
                    value={row.code}
                    onChange={(event) =>
                      setAdded((current) =>
                        current.map((item) =>
                          item.key === row.key ? { ...item, code: event.target.value } : item,
                        ),
                      )
                    }
                  />
                </label>
              ))}
            </div>
          </details>
          {canEdit && (
            <label className="apparel-inline-check">
              <input
                type="checkbox"
                checked={publish}
                onChange={(event) => {
                  setPublishChanged(true)
                  setPublish(event.target.checked)
                }}
              />{' '}
              Publicar la prenda en la web
            </label>
          )}
        </fieldset>
        {error && <p role="alert">{error}</p>}
        {conflict && (
          <button type="button" className="secondary-button" onClick={onReload}>
            Volver a consultar el stock
          </button>
        )}
        {canEdit && (
          <button type="submit" className="primary-button" disabled={busy || reading}>
            {busy ? 'Guardando…' : 'Guardar prenda y talles'}
          </button>
        )}
      </form>
      {canEdit && !busy && (
        <details className="garment-maintenance">
          <summary>Eliminar un talle sin operaciones vinculadas</summary>
          {variants.map((row) => (
            <div className="row-actions" key={textValue(row.id)}>
              <span>
                {textValue(row.talle)} · {textValue(row.color)}
              </span>
              <DeleteRecordButton
                endpoint={`/api/v1/operations/articles/${Number(row.id)}`}
                name={textValue(row.nombre)}
                onDeleted={onSaved}
              />
            </div>
          ))}
        </details>
      )}
    </section>
  )
}
