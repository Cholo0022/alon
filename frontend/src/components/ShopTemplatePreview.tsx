import { brandStyle, type BusinessBrand } from '../lib/businessBrand'
import '../shop/shop.css'
import '../shop/shop-templates.css'

/** Dibuja una muestra estática con las mismas clases de diseño de la tienda, sin cargar productos ni ejecutar compras. */
export function ShopTemplatePreview({
  brand,
  compact = false,
}: {
  brand: BusinessBrand
  compact?: boolean
}) {
  return (
    <div
      className={`shop-app business-themed shop-template-${brand.plantilla_tienda} storefront-preview ${compact ? 'preview-compact' : ''}`}
      style={brandStyle(brand)}
      role="img"
      aria-label={compact ? undefined : 'Vista previa de la plantilla de tienda'}
      aria-hidden={compact || undefined}
    >
      <div className="shop-header">
        <span className="shop-brand">{brand.nombre || 'Tu comercio'}</span>
        <span className="preview-search">Buscar productos</span>
      </div>
      <div className="shop-main">
        <div className="shop-hero">
          <div>
            <p className="shop-eyebrow">TU COMERCIO, MÁS CERCA</p>
            <h1>{brand.titulo_tienda}</h1>
            <p>{brand.descripcion_tienda}</p>
          </div>
        </div>
        <div className="shop-layout">
          <div className="shop-products">
            {[1, 2, 3].map((item) => (
              <div className="shop-product" key={item}>
                <div className={`shop-image preview-product-image preview-product-${item}`}>
                  <span />
                </div>
                <div className="shop-product-body">
                  <h2>Producto {item}</h2>
                  <strong className="shop-price">$ 1.500</strong>
                  <span className="preview-cart-action">Agregar al carrito</span>
                </div>
              </div>
            ))}
          </div>
          <div className="shop-cart">
            <strong>Tu carrito</strong>
            <p>Resumen del pedido</p>
            <span className="preview-cart-action">Enviar pedido</span>
          </div>
        </div>
      </div>
    </div>
  )
}
