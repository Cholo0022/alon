import { clearFieldValidation, showFieldErrors, validateForm } from '../lib/formValidation'
import { preventImplicitArticleSubmit } from '../lib/articleKeyboard'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { currency } from '../lib/format'
import { ArticlePhotosField } from './ArticlePhotosField'
import { photoPayload, type ArticlePhoto } from '../lib/articlePhotos'
import {
  readApparelDraft,
  saveApparelDraft,
  type ApparelCommon,
  type ApparelDraft,
  type ApparelDraftRow,
} from '../lib/apparelDraft'

const newVariant = (stockMode: boolean, color = 'Único', size = ''): ApparelDraftRow => ({
  key: 'VAR-' + crypto.randomUUID().slice(0, 12).toUpperCase(),
  code: '',
  size,
  color,
  quantity: stockMode ? '0' : '1',
})
const defaults = (kind: string): ApparelCommon => ({
  name: '',
  cost: '0',
  price: '0',
  model: '',
  brand: 'Sin marca',
  sizeSystem: kind === 'CALZADO' ? 'AR' : 'LETRAS',
  material: 'Sin especificar',
  audience: 'UNISEX',
  season: 'TODO EL AÑO',
  description: '',
  minimumStock: '0',
  freightPercent: '0',
  marginPercent: '0',
  roundPrice: true,
  publishOnWeb: false,
  singleColor: '',
})
const suggestedPrice = (common: ApparelCommon) => {
  const price =
    Number(common.cost) *
    (1 + Number(common.freightPercent) / 100) *
    (1 + Number(common.marginPercent) / 100)
  return String(
    common.roundPrice && price > 0 ? Math.ceil(price / 10) * 10 : Math.round(price * 100) / 100,
  )
}

/** Datos comunes una sola vez y cantidades independientes por talle, con borrador recuperable. */
export function NewPurchaseApparelForm({
  kind,
  maxVariants = 100,
  stockMode = false,
  draftKey,
  onDraftChange,
  onSaved,
  onBusyChange,
}: {
  kind: string
  maxVariants?: number
  stockMode?: boolean
  draftKey?: string
  onDraftChange?: (draft: ApparelDraft | null) => void
  onSaved: (result: Record<string, unknown>) => void
  onBusyChange: (busy: boolean) => void
}) {
  const [recovered] = useState(() => {
    const draft = readApparelDraft(draftKey)
    return draft?.kind === kind && draft.stockMode === stockMode ? draft : null
  })
  const [common, setCommon] = useState<ApparelCommon>(() => ({
    ...defaults(kind),
    ...recovered?.common,
  }))
  const [variants, setVariants] = useState<ApparelDraftRow[]>(
    () => recovered?.rows ?? [newVariant(stockMode)],
  )
  const [photos, setPhotos] = useState<ArticlePhoto[]>(recovered?.photos ?? [])
  const [multipleColors, setMultipleColors] = useState(recovered?.multipleColors ?? false)
  const [priceEdited, setPriceEdited] = useState(recovered?.priceEdited ?? false)
  const [readingPhotos, setReadingPhotos] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [draftWarning, setDraftWarning] = useState(
    recovered?.photosOmitted
      ? 'Borrador recuperado. Volvé a seleccionar las fotos de la prenda.'
      : '',
  )
  const formRef = useRef<HTMLFormElement>(null)
  const completed = useRef(false)
  const meaningful =
    !!common.name.trim() ||
    common.cost !== '0' ||
    common.price !== '0' ||
    variants.some((row) => row.size.trim() || row.code.trim()) ||
    photos.length > 0
  useEffect(() => {
    if (!draftKey) return
    const save = () => {
      if (completed.current) return
      if (!meaningful) {
        try {
          localStorage.removeItem(draftKey)
        } catch {
          /* Se conserva el formulario en memoria. */
        }
        onDraftChange?.(null)
        return
      }
      const draft: ApparelDraft = {
        version: 1,
        kind,
        stockMode,
        common,
        rows: variants,
        photos,
        multipleColors,
        priceEdited,
        savedAt: Date.now(),
      }
      const warning = saveApparelDraft(draftKey, draft)
      setDraftWarning(warning)
      onDraftChange?.(draft)
    }
    const timer = window.setTimeout(save, 250)
    return () => {
      window.clearTimeout(timer)
      save()
    }
  }, [
    draftKey,
    meaningful,
    kind,
    stockMode,
    common,
    variants,
    photos,
    multipleColors,
    priceEdited,
    onDraftChange,
  ])
  const updateCommon = (key: keyof ApparelCommon, value: string | boolean) =>
    setCommon((current) => {
      const next = { ...current, [key]: value }
      if (!priceEdited && ['cost', 'freightPercent', 'marginPercent', 'roundPrice'].includes(key))
        next.price = suggestedPrice(next)
      return next
    })
  const addSize = (size: string) => {
    const current = variants.findIndex(
      (row) => row.size.trim().toLocaleUpperCase('es-AR') === size.toLocaleUpperCase('es-AR'),
    )
    if (current >= 0) {
      ;(
        formRef.current?.elements.namedItem(
          `variants[${current}].quantity`,
        ) as HTMLInputElement | null
      )?.focus()
      return
    }
    if (variants.length >= Math.min(maxVariants, 100)) return
    setVariants((rows) => {
      const empty = rows.findIndex((row) => !row.size.trim())
      const row = newVariant(stockMode, multipleColors ? (rows.at(-1)?.color ?? '') : 'Único', size)
      return empty >= 0
        ? rows.map((item, index) => (index === empty ? { ...item, size } : item))
        : [...rows, row]
    })
  }
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy || readingPhotos) return
    const form = event.currentTarget,
      invalid = validateForm(form)
    if (invalid) {
      setError(invalid)
      return
    }
    const name = common.name.trim()
    const chosen = variants.map((row) => ({
      code: row.code.trim() || row.key,
      size: row.size.trim(),
      color: multipleColors ? row.color.trim() : common.singleColor.trim() || 'Único',
      quantity: Number(row.quantity),
    }))
    const errors: Record<string, string> = {},
      seen = new Set<string>()
    chosen.forEach((row, index) => {
      const key = row.size.toLocaleUpperCase('es-AR') + '\n' + row.color.toLocaleUpperCase('es-AR')
      if (seen.has(key))
        errors[`variants[${index}].size`] =
          'Este talle y color ya están cargados. Sumá las cantidades.'
      seen.add(key)
      if (
        !row.size ||
        !row.color ||
        !Number.isSafeInteger(row.quantity) ||
        row.quantity < (stockMode ? 0 : 1)
      )
        errors[`variants[${index}].quantity`] = 'Revisá el talle, color y cantidad entera.'
    })
    if (!name || !chosen.length || chosen.length > maxVariants || Object.keys(errors).length) {
      showFieldErrors(form, errors)
      setError('Revisá los talles y las cantidades señaladas.')
      return
    }
    const product = {
      code: chosen[0].code,
      name,
      categoryId: 0,
      supplierId: 0,
      unit: 'UNIDAD',
      freightByValue: false,
      freightByPercent: true,
      freightPerUnit: 0,
      cost: Number(common.cost),
      salePrice: Number(common.price),
      freightPercent: Number(common.freightPercent),
      marginPercent: Number(common.marginPercent),
      minimumStock: Number(common.minimumStock),
      roundPrice: common.roundPrice,
      publishOnWeb: common.publishOnWeb,
      initialStock: 0,
      photos: photoPayload(photos),
      apparel: {
        kind,
        model: common.model.trim() || name,
        brand: common.brand.trim() || 'Sin marca',
        size: chosen[0].size,
        color: chosen[0].color,
        sizeSystem: common.sizeSystem,
        material: common.material.trim() || 'Sin especificar',
        audience: common.audience,
        season: common.season,
        description: common.description.trim() || name,
      },
    }
    setBusy(true)
    onBusyChange(true)
    setError('')
    try {
      const response = await apiFetch(
        stockMode ? '/api/v1/operations/articles/apparel' : '/api/v1/operations/purchases/apparel',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ product, variants: chosen }),
        },
      )
      const result = await response.json()
      if (!response.ok || !Array.isArray(result.items)) {
        const aliases: Record<string, string> = {
          'product.name': 'name',
          'product.code': 'variants[0].code',
          'product.cost': 'cost',
          'product.salePrice': 'price',
          'product.freightPercent': 'freightPercent',
          'product.marginPercent': 'marginPercent',
          'product.minimumStock': 'minimumStock',
          'product.apparel.size': 'variants[0].size',
          'product.apparel.color': multipleColors ? 'variants[0].color' : 'singleColor',
        }
        for (const field of [
          'model',
          'brand',
          'sizeSystem',
          'material',
          'audience',
          'season',
          'description',
        ])
          aliases['product.apparel.' + field] = field
        showFieldErrors(form, result.fields ?? {}, aliases)
        throw new Error(result.error ?? 'No se pudo guardar la prenda.')
      }
      completed.current = true
      if (draftKey) {
        try {
          localStorage.removeItem(draftKey)
        } catch {
          /* El alta ya se confirmó. */
        }
      }
      onDraftChange?.(null)
      onSaved(result)
    } catch (error) {
      setError(error instanceof Error ? error.message : 'No se pudo guardar la prenda.')
      setBusy(false)
      onBusyChange(false)
    }
  }
  const quickSizes =
    kind === 'CALZADO'
      ? Array.from({ length: 11 }, (_, index) => String(35 + index))
      : ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'Único']
  return (
    <form
      ref={formRef}
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
      <fieldset disabled={busy}>
        <h2>{stockMode ? 'Nueva prenda' : 'Prenda para el comprobante'}</h2>
        <label>
          {stockMode ? 'Nombre del artículo' : 'Nombre de la prenda'}
          <input
            name="name"
            required
            maxLength={80}
            autoFocus
            value={common.name}
            onChange={(event) => updateCommon('name', event.target.value)}
          />
        </label>
        <ArticlePhotosField
          value={photos}
          onChange={setPhotos}
          disabled={busy}
          onReading={setReadingPhotos}
        />
        <div className="apparel-price-fields">
          <label>
            Costo unitario común
            <input
              name="cost"
              type="number"
              min="0"
              step="0.01"
              required
              value={common.cost}
              onChange={(event) => updateCommon('cost', event.target.value)}
            />
          </label>
          <label>
            Precio de venta
            <input
              name="price"
              type="number"
              min="0"
              step="0.01"
              required
              value={common.price}
              onChange={(event) => {
                setPriceEdited(true)
                updateCommon('price', event.target.value)
              }}
            />
          </label>
        </div>
        <h3>Elegí los talles</h3>
        <div className="apparel-size-buttons">
          {quickSizes.map((size) => (
            <button
              type="button"
              key={size}
              className="secondary-button"
              aria-pressed={variants.some((row) => row.size === size)}
              onClick={() => addSize(size)}
            >
              {size}
            </button>
          ))}
        </div>
        <label className="apparel-inline-check">
          <input
            type="checkbox"
            checked={multipleColors}
            onChange={(event) => {
              const next = event.target.checked
              if (!next && new Set(variants.map((row) => row.size.trim())).size < variants.length) {
                setError(
                  'Esta carga repite talles con distintos colores. Conservá los colores o quitá los renglones repetidos.',
                )
                return
              }
              setMultipleColors(next)
              if (next)
                setVariants((rows) =>
                  rows.map((row) => ({
                    ...row,
                    color: row.color === 'Único' ? common.singleColor : row.color,
                  })),
                )
            }}
          />{' '}
          Esta prenda tiene distintos colores
        </label>
        <p>
          {stockMode
            ? 'Stock actual de cada talle. Cargá cero si todavía no tenés unidades.'
            : 'Cantidades recibidas de cada talle. Se sumarán al stock al guardar el comprobante.'}
        </p>
        <div className="table-scroll">
          <table className="purchase-variant-table">
            <thead>
              <tr>
                <th>Talle</th>
                {multipleColors && <th>Color</th>}
                <th>{stockMode ? 'Stock inicial' : 'Cantidad recibida'}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {variants.map((row, index) => (
                <tr key={row.key}>
                  <td>
                    <input
                      name={`variants[${index}].size`}
                      aria-label={`Talle ${index + 1}`}
                      required
                      maxLength={30}
                      value={row.size}
                      onChange={(event) =>
                        setVariants((rows) =>
                          rows.map((item) =>
                            item.key === row.key ? { ...item, size: event.target.value } : item,
                          ),
                        )
                      }
                    />
                  </td>
                  {multipleColors && (
                    <td>
                      <input
                        name={`variants[${index}].color`}
                        aria-label={`Color ${index + 1}`}
                        required
                        maxLength={60}
                        value={row.color}
                        onChange={(event) =>
                          setVariants((rows) =>
                            rows.map((item) =>
                              item.key === row.key ? { ...item, color: event.target.value } : item,
                            ),
                          )
                        }
                      />
                    </td>
                  )}
                  <td>
                    <input
                      name={`variants[${index}].quantity`}
                      aria-label={`${stockMode ? 'Stock talle' : 'Cantidad'} ${index + 1}`}
                      type="number"
                      required
                      min={stockMode ? '0' : '1'}
                      max="1000000000"
                      step="1"
                      value={row.quantity}
                      onChange={(event) =>
                        setVariants((rows) =>
                          rows.map((item) =>
                            item.key === row.key ? { ...item, quantity: event.target.value } : item,
                          ),
                        )
                      }
                    />
                  </td>
                  <td>
                    <button
                      type="button"
                      disabled={variants.length === 1}
                      aria-label={`Quitar talle ${index + 1}`}
                      onClick={() =>
                        setVariants((rows) => rows.filter((item) => item.key !== row.key))
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
        <button
          type="button"
          className="secondary-button"
          data-add-variant
          disabled={variants.length >= Math.min(maxVariants, 100)}
          onClick={() =>
            setVariants((rows) => [
              ...rows,
              newVariant(stockMode, multipleColors ? (rows.at(-1)?.color ?? '') : 'Único'),
            ])
          }
        >
          Agregar otro talle
        </button>
        <details>
          <summary>Códigos de barras (opcional)</summary>
          <p>Escanear un código avanza la carga. Guardá la prenda al finalizar.</p>
          {variants.map((row, index) => (
            <label key={row.key}>
              Código del talle {row.size || index + 1}
              {multipleColors ? ' · ' + row.color : ''}
              <input
                name={`variants[${index}].code`}
                aria-label={`Código ${index + 1}`}
                maxLength={30}
                placeholder="Automático si lo dejás vacío"
                value={row.code}
                onChange={(event) =>
                  setVariants((rows) =>
                    rows.map((item) =>
                      item.key === row.key ? { ...item, code: event.target.value } : item,
                    ),
                  )
                }
              />
            </label>
          ))}
        </details>
        <details>
          <summary>Más opciones</summary>
          <div className="purchase-apparel-common">
            {(['model', 'brand', 'singleColor', 'material', 'description'] as const).map((key) => (
              <label key={key}>
                {
                  {
                    model: 'Modelo / referencia',
                    brand: 'Marca',
                    singleColor: 'Color único (opcional)',
                    material: 'Material',
                    description: 'Descripción',
                  }[key]
                }
                <input
                  name={key}
                  maxLength={
                    key === 'description'
                      ? 4000
                      : key === 'material'
                        ? 120
                        : key === 'singleColor'
                          ? 60
                          : 80
                  }
                  value={common[key]}
                  onChange={(event) => updateCommon(key, event.target.value)}
                />
              </label>
            ))}
            <label>
              Sistema de talles
              <select
                name="sizeSystem"
                value={common.sizeSystem}
                onChange={(event) => updateCommon('sizeSystem', event.target.value)}
              >
                {['AR', 'EU', 'US', 'UK', 'LETRAS', 'UNICO', 'OTRO'].map((value) => (
                  <option key={value}>{value}</option>
                ))}
              </select>
            </label>
            <label>
              Público
              <select
                name="audience"
                value={common.audience}
                onChange={(event) => updateCommon('audience', event.target.value)}
              >
                {['UNISEX', 'MUJER', 'HOMBRE', 'INFANTIL', 'BEBE'].map((value) => (
                  <option key={value}>{value}</option>
                ))}
              </select>
            </label>
            <label>
              Temporada
              <select
                name="season"
                value={common.season}
                onChange={(event) => updateCommon('season', event.target.value)}
              >
                {['TODO EL AÑO', 'PRIMAVERA / VERANO', 'OTOÑO / INVIERNO'].map((value) => (
                  <option key={value}>{value}</option>
                ))}
              </select>
            </label>
            {(['minimumStock', 'freightPercent', 'marginPercent'] as const).map((key) => (
              <label key={key}>
                {
                  {
                    minimumStock: 'Stock mínimo por talle',
                    freightPercent: 'Flete %',
                    marginPercent: 'Ganancia %',
                  }[key]
                }
                <input
                  name={key}
                  type="number"
                  min="0"
                  step={key === 'minimumStock' ? '1' : '0.01'}
                  value={common[key]}
                  onChange={(event) => updateCommon(key, event.target.value)}
                />
              </label>
            ))}
            <label className="apparel-inline-check">
              <input
                name="roundPrice"
                type="checkbox"
                checked={common.roundPrice}
                onChange={(event) => updateCommon('roundPrice', event.target.checked)}
              />{' '}
              Redondear precio al próximo $10
            </label>
            <button
              className="secondary-button"
              type="button"
              onClick={() => {
                setPriceEdited(false)
                setCommon((current) => ({ ...current, price: suggestedPrice(current) }))
              }}
            >
              Calcular precio con costo y ganancia
            </button>
          </div>
        </details>
        <label className="apparel-inline-check">
          <input
            name="publishOnWeb"
            type="checkbox"
            checked={common.publishOnWeb}
            onChange={(event) => updateCommon('publishOnWeb', event.target.checked)}
          />{' '}
          Publicar la prenda en la web
        </label>
      </fieldset>
      {error && <p role="alert">{error}</p>}
      {draftWarning && <p role="status">{draftWarning}</p>}
      <p>
        {variants.reduce((total, row) => total + Number(row.quantity || 0), 0)} unidades ·{' '}
        {currency.format(Number(common.price || 0))} por unidad
      </p>
      <div className="entry-actions">
        <button className="primary-button" type="submit" disabled={busy || readingPhotos}>
          {busy
            ? 'Guardando…'
            : stockMode
              ? 'Guardar artículo con stock por talle'
              : 'Crear prenda y agregar talles al comprobante'}
        </button>
        {draftKey && meaningful && (
          <button
            type="button"
            className="secondary-button"
            disabled={busy}
            onClick={() => {
              if (!window.confirm('¿Descartar esta carga sin guardar?')) return
              setCommon(defaults(kind))
              setVariants([newVariant(stockMode)])
              setPhotos([])
              setMultipleColors(false)
              setPriceEdited(false)
              setError('')
              setDraftWarning('')
            }}
          >
            Descartar borrador
          </button>
        )}
      </div>
    </form>
  )
}
