import { useId, useState } from 'react'
import type { Product, CartLine } from './types'
import { currency } from '../lib/format'
import { sentenceCase } from '../lib/sentenceCase'
import { ProductGallery } from './ProductGallery'

/** Una publicación por prenda; el carrito siempre recibe el código del talle y color elegidos. */
export function ApparelProductCard({
  product,
  cart,
  busy,
  onAdd,
}: {
  product: Product
  cart: CartLine[]
  busy: boolean
  onAdd: (product: Product) => void
}) {
  const variants = product.variants ?? []
  const colors = [...new Set(variants.map((variant) => variant.color ?? ''))]
  const [color, setColor] = useState(colors.length === 1 ? colors[0] : '')
  const [variantId, setVariantId] = useState('')
  const id = useId()
  const standardSizes = [
    'XXS',
    'XS',
    'S',
    'M',
    'L',
    'XL',
    'XXL',
    'XXXL',
    '3XL',
    '4XL',
    'UNICO',
    'ÚNICO',
  ]
  const choices = variants
    .filter((variant) => variant.color === color)
    .sort((a, b) => {
      const first = standardSizes.indexOf((a.talle ?? '').toUpperCase()),
        second = standardSizes.indexOf((b.talle ?? '').toUpperCase())
      return first >= 0 && second >= 0
        ? first - second
        : (a.talle ?? '').localeCompare(b.talle ?? '', 'es', { numeric: true })
    })
  const selected = choices.find((variant) => String(variant.id) === variantId)
  const available = selected
    ? Math.max(
        0,
        Number(selected.stock) - (cart.find((line) => line.id === selected.id)?.quantity ?? 0),
      )
    : 0
  const priced = variants.filter((variant) => Number(variant.stock) > 0)
  const lowest = Math.min(...priced.map((variant) => Number(variant.precio_venta)))
  const gallery = selected ?? product
  return (
    <article className="shop-product" aria-labelledby={id}>
      <ProductGallery
        name={product.nombre}
        photos={gallery.photos?.length ? gallery.photos : product.photos}
        cover={gallery.imagen_clave ?? product.imagen_clave}
        illustrative={Boolean(gallery.imagen_ilustrativa)}
      />
      <div className="shop-product-body">
        <h2 id={id}>{sentenceCase(product.nombre)}</h2>
        <div className="shop-apparel-details">
          <p>
            {sentenceCase(product.marca ?? '')} · {sentenceCase(product.modelo ?? '')}
          </p>
          <p>
            {sentenceCase(product.material ?? '')} · {product.publico} · {product.temporada}
          </p>
          <p style={{ whiteSpace: 'pre-wrap' }}>{sentenceCase(product.descripcion ?? '')}</p>
        </div>
        <div className="shop-variant-options">
          <label>
            Color
            <select
              value={color}
              onChange={(event) => {
                setColor(event.target.value)
                setVariantId('')
              }}
            >
              <option value="">Elegí un color</option>
              {colors.map((value) => (
                <option key={value} value={value}>
                  {sentenceCase(value)}
                </option>
              ))}
            </select>
          </label>
          <label>
            Talle
            <select
              value={variantId}
              disabled={!color}
              onChange={(event) => setVariantId(event.target.value)}
            >
              <option value="">Elegí un talle</option>
              {choices.map((variant) => (
                <option key={variant.id} value={variant.id}>
                  {variant.talle} ({variant.sistema_talle})
                  {choices.filter((other) => other.talle === variant.talle).length > 1
                    ? ' · ' + variant.codigo
                    : ''}
                  {Number(variant.stock) <= 0 ? ' · Sin stock' : ''}
                </option>
              ))}
            </select>
          </label>
        </div>
        <strong className="shop-price">
          {selected
            ? currency.format(Number(selected.precio_venta))
            : Number.isFinite(lowest)
              ? 'Desde ' + currency.format(lowest)
              : 'Sin stock'}
        </strong>
        <p className="shop-stock" aria-live="polite">
          {selected
            ? `Talle ${selected.talle} · ${selected.color}: ${Number(selected.stock).toLocaleString('es-AR')} ${selected.tipo_producto === 'CALZADO' ? 'pares' : 'unidades'} disponibles`
            : 'Elegí un talle y color para ver su stock.'}
        </p>
        {selected && cart.some((line) => line.id === selected.id) && (
          <small>
            Podés agregar {available.toLocaleString('es-AR')} más de este talle y color.
          </small>
        )}
        <button
          disabled={busy || !selected || available <= 0}
          onClick={() => {
            if (selected) onAdd(selected)
          }}
        >
          + Agregar al carrito
        </button>
      </div>
    </article>
  )
}
