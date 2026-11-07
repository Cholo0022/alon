import { GuestCheckoutDialog, type GuestBuyer } from './GuestCheckoutDialog'
import { ApparelProductCard } from './ApparelProductCard'
import type { Product, CartLine } from './types'
import { sentenceCase } from '../lib/sentenceCase'
import { shopOrderKey } from './orderKey'
import { ArticleEntryDialog } from '../components/ArticleEntryDialog'
// Muestra la tienda web, el carrito, el registro de compradores y sus pedidos.
import { QuantityInput } from '../components/QuantityInput'
import { brandStyle, useBusinessBrand } from '../lib/businessBrand'
import { useEffect, useRef, useState } from 'react'
import { currency } from '../lib/format'
import { shopRequest } from './api'
import './shop.css'
import './shop-templates.css'
import { ProductGallery } from './ProductGallery'

type PendingOrder = { body: string; key: string }
type Catalog = {
  heroImage?: string
  items: Product[]
  total: number
  categories: { nombre: string }[]
  store: { nombre: string }
}
/** Formatea cantidades de la tienda con hasta tres decimales y separadores argentinos. */
const quantity = (n: number) =>
  new Intl.NumberFormat('es-AR', { maximumFractionDigits: 3 }).format(n)
/** Recupera y valida el carrito guardado localmente, descarta entradas inválidas y limita la cantidad de productos. */
function initialCart(): CartLine[] {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem('alon-shop-cart') ?? '[]')
    if (!Array.isArray(saved)) return []
    return saved
      .filter(
        (p): p is CartLine =>
          typeof p === 'object' &&
          p !== null &&
          Number.isSafeInteger(p.id) &&
          p.id > 0 &&
          typeof p.nombre === 'string' &&
          typeof p.unidad === 'string' &&
          Number.isFinite(p.quantity) &&
          p.quantity > 0 &&
          Number.isFinite(p.precio_venta) &&
          p.precio_venta >= 0,
      )
      .slice(0, 100)
  } catch {
    return []
  }
}

/** Recupera un envío pendiente de la sesión solo si conserva un cuerpo y una clave de operación válidos para reintentarlo. */
function pendingOrder(): PendingOrder | null {
  try {
    const value = JSON.parse(sessionStorage.getItem('alon-shop-pending-order') ?? 'null')
    return value &&
      typeof value.body === 'string' &&
      value.body.length <= 30000 &&
      typeof value.key === 'string' &&
      /^[A-Za-z0-9_-]{16,80}$/.test(value.key)
      ? (value as PendingOrder)
      : null
  } catch {
    return null
  }
}

function recoverContact(): Partial<GuestBuyer> {
  try {
    const buyer = JSON.parse(pendingOrder()?.body ?? '{}').buyer
    if (!buyer || typeof buyer !== 'object') return {}
    return Object.fromEntries(
      ['firstName', 'lastName', 'phone', 'dni', 'province', 'city']
        .filter((key) => typeof buyer[key] === 'string')
        .map((key) => [key, buyer[key]]),
    )
  } catch {
    return {}
  }
}

/** Organiza el catálogo público, la cuenta del comprador, el carrito y el envío y seguimiento de pedidos. */
export default function ShopApp() {
  const brand = useBusinessBrand(true)
  const [catalog, setCatalog] = useState<Catalog>({
    items: [],
    total: 0,
    categories: [],
    store: { nombre: 'Alon' },
  })
  const [productToAdd, setProductToAdd] = useState<Product | null>(null)
  const [quantityError, setQuantityError] = useState('')
  const [cartOutsideView, setCartOutsideView] = useState(false)
  const [cart, setCart] = useState<CartLine[]>(initialCart)
  const [checkoutOpen, setCheckoutOpen] = useState(false)
  const catalogPanel = useRef<HTMLElement>(null)
  const goToCatalog = () =>
    requestAnimationFrame(() => {
      catalogPanel.current?.scrollIntoView?.({ block: 'start', behavior: 'instant' })
    })
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('')
  const [page, setPage] = useState(0)
  const [refresh, setRefresh] = useState(0)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const mainPanel = useRef<HTMLElement>(null)
  const cartPanel = useRef<HTMLElement>(null)
  const pending = useRef<PendingOrder | null>(pendingOrder())
  const availableToAdd = productToAdd
    ? Math.max(
        0,
        Math.round(
          (Number(productToAdd.stock) -
            (cart.find((line) => line.id === productToAdd.id)?.quantity ?? 0)) *
            1000,
        ) / 1000,
      )
    : 0
  const total = cart.reduce(
    (sum, p) => sum + Math.round(Number(p.precio_venta) * p.quantity * 100) / 100,
    0,
  )

  useEffect(() => {
    const previousTitle = document.title
    document.title = brand.nombre + ' · Tienda online'
    return () => {
      document.title = previousTitle
    }
  }, [brand.nombre])
  useEffect(() => {
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setLoading(true)
      void shopRequest<Catalog>(
        '/catalog?q=' +
          encodeURIComponent(query) +
          '&category=' +
          encodeURIComponent(category) +
          '&page=' +
          page,
        { signal: controller.signal },
      )
        .then((result) => {
          setCatalog((current) => ({
            ...result,
            items:
              page === 0
                ? result.items
                : [
                    ...current.items,
                    ...result.items.filter((p) => !current.items.some((old) => old.id === p.id)),
                  ],
          }))
          setLoading(false)
        })
        .catch((e) => {
          if (!controller.signal.aborted) {
            setError(e.message)
            setLoading(false)
          }
        })
    }, 200)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [query, category, page, refresh])
  useEffect(() => {
    try {
      localStorage.setItem('alon-shop-cart', JSON.stringify(cart))
    } catch {
      /* Cart still works when browser storage is disabled. */
    }
  }, [cart])
  // Observa la visibilidad del carrito sin ejecutar cálculos en cada movimiento de la página.
  useEffect(() => {
    const panel = cartPanel.current
    if (!panel || !window.IntersectionObserver) return
    let active = true
    const observer = new IntersectionObserver((entries) => {
      const entry = entries.find((item) => item.target === panel)
      if (active && entry) setCartOutsideView(!entry.isIntersecting)
    })
    observer.observe(panel)
    return () => {
      active = false
      observer.disconnect()
    }
  }, [])

  /** Lleva al carrito desde el acceso principal o flotante y enfoca su panel sin alterar sus productos. */
  const goToCart = () => {
    requestAnimationFrame(() => {
      const panel = cartPanel.current
      const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches
      panel?.scrollIntoView?.({ behavior: reducedMotion ? 'instant' : 'smooth', block: 'start' })
      panel?.focus({ preventScroll: true })
    })
  }

  /** Abre la selección de cantidad del producto sin modificar todavía el carrito. */
  const add = (product: Product) => {
    setNotice('')
    setQuantityError('')
    setProductToAdd(product)
  }

  /** Valida la cantidad adicional y el stock restante; acumula el producto y cierra el diálogo solo al confirmarlo. */
  const confirmCartQuantity = (amount: number) => {
    if (!productToAdd) return
    const existing = cart.find((line) => line.id === productToAdd.id)
    const newQuantity = Math.round(((existing?.quantity ?? 0) + amount) * 1000) / 1000
    if (
      !Number.isFinite(amount) ||
      amount <= 0 ||
      Math.abs(amount * 1000 - Math.round(amount * 1000)) > 0.000001
    ) {
      setQuantityError('Ingresá una cantidad mayor a cero, con hasta tres decimales.')
      return
    }
    if (newQuantity > Number(productToAdd.stock)) {
      setQuantityError(
        'La cantidad supera el stock disponible, incluyendo lo que ya tenés en el carrito.',
      )
      return
    }
    if (!existing && cart.length >= 100) {
      setQuantityError('El carrito admite hasta 100 artículos distintos.')
      return
    }
    const product = productToAdd
    setCart((current) =>
      existing
        ? current.map((line) =>
            line.id === product.id ? { ...product, quantity: newQuantity } : line,
          )
        : [...current, { ...product, quantity: amount }],
    )
    setProductToAdd(null)
    setQuantityError('')
  }

  /** Revalida stock y precios, prepara o recupera la clave del pedido y envía el carrito evitando duplicados en reintentos. */
  const checkout = async (submitted: GuestBuyer) => {
    if (!cart.length || busy) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const current = await shopRequest<Product[]>('/cart', {
        method: 'POST',
        body: JSON.stringify({ ids: cart.map((p) => p.id) }),
      })
      const updated = cart.map((p) => ({
        ...(current.find((x) => x.id === p.id) ?? { ...p, stock: 0 }),
        quantity: p.quantity,
      }))
      setCart(updated)
      if (updated.some((p) => Number(p.stock) < p.quantity))
        throw new Error('Un artículo ya no tiene stock suficiente. Quitalo o ajustá su cantidad.')
      if (
        updated.some(
          (p) => Number(p.precio_venta) !== Number(cart.find((x) => x.id === p.id)?.precio_venta),
        )
      )
        throw new Error(
          'Actualizamos los precios del carrito. Revisá el total y volvé a enviar el pedido.',
        )
      const body = JSON.stringify({
        buyer: submitted,
        lines: updated.map((p) => ({
          articleId: p.id,
          quantity: p.quantity,
          expectedPrice: Number(p.precio_venta),
        })),
      })
      if (pending.current?.body !== body) pending.current = { body, key: shopOrderKey() }
      try {
        sessionStorage.setItem('alon-shop-pending-order', JSON.stringify(pending.current))
      } catch {
        /* Retry protection remains available until this page closes. */
      }
      const result = await shopRequest<{ id: number }>('/orders', {
        method: 'POST',
        headers: { 'Idempotency-Key': pending.current.key },
        body,
      })
      pending.current = null
      try {
        sessionStorage.removeItem('alon-shop-pending-order')
      } catch {
        /* Nothing to clear when storage is unavailable. */
      }
      setCart([])
      setCheckoutOpen(false)
      setNotice(
        'Pedido #' +
          result.id +
          ' enviado. El vendedor se comunicará con vos para coordinar el pago y la entrega.',
      )
      setRefresh((n) => n + 1)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo enviar el pedido.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div
      className={`shop-app business-themed shop-template-${brand.plantilla_tienda}`}
      data-template={brand.plantilla_tienda}
      style={brandStyle(brand)}
    >
      {checkoutOpen && (
        <GuestCheckoutDialog
          busy={busy}
          error={error}
          total={total}
          initialBuyer={recoverContact()}
          onClose={() => {
            setCheckoutOpen(false)
            setError('')
            cartPanel.current?.focus()
          }}
          onSend={(buyer) => void checkout(buyer)}
        />
      )}
      {productToAdd && (
        <ArticleEntryDialog
          article={productToAdd}
          mode="cart"
          initialQuantity={String(Math.min(1, availableToAdd))}
          initialPrice={Number(productToAdd.precio_venta)}
          maxQuantity={availableToAdd}
          message={quantityError}
          onConfirm={(values) => confirmCartQuantity(values.quantity)}
          onCancel={() => {
            setProductToAdd(null)
            setQuantityError('')
          }}
        />
      )}
      <header className="shop-header">
        <div className="shop-header-brand">
          <button
            className="shop-brand"
            onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
            aria-label={brand.nombre + ', inicio'}
          >
            {brand.nombre}
            <span>TIENDA ONLINE</span>
          </button>
          {brand.telefono_tienda.trim() && (
            <span className="shop-contact-phone">Celular: {brand.telefono_tienda.trim()}</span>
          )}
        </div>
        <input
          className="shop-search"
          type="search"
          placeholder="¿Qué estás buscando?"
          aria-label="Buscar productos"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            setPage(0)
          }}
        />
        <div className="shop-header-actions">
          <button className="shop-solid" onClick={goToCart}>
            Carrito · {quantity(cart.reduce((n, p) => n + p.quantity, 0))}
          </button>
        </div>
      </header>
      <div className="shop-subnav">
        <strong>{brand.nombre}</strong>
        {brand.slogan && <span>{brand.slogan}</span>}
        <span>Precios en pesos argentinos</span>
        <span>Solo productos disponibles</span>
      </div>
      <main ref={mainPanel} className="shop-main">
        {error && !checkoutOpen && (
          <div className="shop-alert shop-error" role="alert">
            {error}
            <button onClick={() => setError('')} aria-label="Cerrar error">
              ×
            </button>
          </div>
        )}
        {notice && (
          <div className="shop-alert" role="status">
            {notice}
          </div>
        )}
        <>
          <section className="shop-hero">
            <div>
              <p className="shop-eyebrow">TU COMERCIO, MÁS CERCA</p>
              <h1>{brand.titulo_tienda}</h1>
              <p>{brand.descripcion_tienda}</p>
              <span className="shop-badge">● Stock disponible</span>
            </div>
            {catalog.heroImage && (
              <img
                src={catalog.heroImage}
                alt="Imagen ilustrativa del comercio"
                onError={(e) => {
                  e.currentTarget.hidden = true
                }}
              />
            )}
          </section>
          <nav
            className="shop-categories"
            aria-label="Filtrar por rubro de artículos o nombre de indumentaria y calzado"
          >
            <button
              aria-pressed={category === ''}
              onClick={() => {
                goToCatalog()
                setCategory('')
                setPage(0)
              }}
            >
              Todos
            </button>
            {catalog.categories.map((c) => (
              <button
                key={sentenceCase(c.nombre)}
                aria-pressed={category === c.nombre}
                onClick={() => {
                  goToCatalog()
                  setCategory(c.nombre)
                  setPage(0)
                }}
              >
                {c.nombre}
              </button>
            ))}
          </nav>
          <div className="shop-layout">
            <section ref={catalogPanel} aria-label="Catálogo de productos">
              <div className="shop-results">
                <span>{catalog.total} artículos disponibles</span>
                <button
                  className="shop-link"
                  onClick={() => {
                    setPage(0)
                    setRefresh((n) => n + 1)
                  }}
                >
                  Actualizar
                </button>
              </div>
              <div className="shop-products">
                {catalog.items.map((p) =>
                  p.variants ? (
                    <ApparelProductCard
                      key={p.id}
                      product={p}
                      cart={cart}
                      busy={busy}
                      onAdd={add}
                    />
                  ) : (
                    <article className="shop-product" key={p.id}>
                      <ProductGallery
                        name={p.nombre}
                        photos={p.photos}
                        cover={p.imagen_clave}
                        illustrative={Boolean(p.imagen_ilustrativa)}
                      />
                      <div className="shop-product-body">
                        <h2>{sentenceCase(p.nombre)}</h2>
                        {p.tipo_producto && p.tipo_producto !== 'ARTICULO' && (
                          <div className="shop-apparel-details">
                            <p>
                              <strong>
                                Talle {sentenceCase(p.talle ?? '')} ({p.sistema_talle}) ·{' '}
                                {sentenceCase(p.color ?? '')}
                              </strong>
                            </p>
                            <p>
                              {sentenceCase(p.marca ?? '')} · {sentenceCase(p.modelo ?? '')}
                            </p>
                            <p>
                              {sentenceCase(p.material ?? '')} · {p.publico} · {p.temporada}
                            </p>
                            <p style={{ whiteSpace: 'pre-wrap' }}>
                              {sentenceCase(p.descripcion ?? '')}
                            </p>
                            <small>
                              {p.tipo_producto === 'CALZADO'
                                ? 'Precio y stock por par.'
                                : 'Precio y stock por unidad.'}
                            </small>
                          </div>
                        )}
                        <strong className="shop-price">
                          {currency.format(Number(p.precio_venta))}
                        </strong>
                        <p className="shop-stock">
                          ● {quantity(Number(p.stock))} {p.unidad.toLocaleLowerCase('es-AR')}{' '}
                          disponibles
                        </p>
                        <button
                          onClick={() => add(p)}
                          disabled={
                            busy ||
                            (cart.find((c) => c.id === p.id)?.quantity ?? 0) >= Number(p.stock)
                          }
                        >
                          + Agregar al carrito
                        </button>
                      </div>
                    </article>
                  ),
                )}
              </div>
              {loading && <p role="status">Cargando artículos…</p>}
              {!loading && !catalog.items.length && (
                <p>No hay artículos disponibles para esta búsqueda.</p>
              )}
              {!loading && catalog.items.length < catalog.total && (
                <button className="shop-load-more" onClick={() => setPage((n) => n + 1)}>
                  Ver más productos
                </button>
              )}
            </section>
            <aside className="shop-cart" ref={cartPanel} tabIndex={-1} aria-label="Tu carrito">
              <h2>Tu carrito</h2>
              {cart.map((p) => (
                <div className="shop-cart-line" key={p.id}>
                  <div>
                    <strong>{p.nombre}</strong>
                    <span>
                      {currency.format(Math.round(Number(p.precio_venta) * p.quantity * 100) / 100)}
                    </span>
                  </div>
                  <div>
                    <label>
                      Cantidad
                      <QuantityInput
                        min="0.001"
                        max={Math.max(Number(p.stock), p.quantity)}
                        value={p.quantity}
                        onChange={(e) => {
                          const n = Number(e.target.value)
                          if (
                            Number.isFinite(n) &&
                            n > 0 &&
                            n <= 999999999 &&
                            Math.abs(Math.round(n * 1000) - n * 1000) < 0.000001
                          )
                            setCart((v) =>
                              v.map((x) => (x.id === p.id ? { ...x, quantity: n } : x)),
                            )
                        }}
                      />
                    </label>
                    <button
                      className="shop-link"
                      onClick={() => setCart((v) => v.filter((x) => x.id !== p.id))}
                    >
                      Quitar
                    </button>
                  </div>
                  {p.quantity > Number(p.stock) && (
                    <small className="shop-danger">
                      Stock disponible: {quantity(Number(p.stock))}
                    </small>
                  )}
                </div>
              ))}
              {!cart.length && (
                <p className="shop-cart-empty">Elegí productos para empezar tu pedido.</p>
              )}
              <div className="shop-total">
                <span>Total estimado</span>
                <strong>{currency.format(total)}</strong>
              </div>
              <button
                className="shop-solid shop-checkout"
                disabled={busy || !cart.length}
                onClick={() => {
                  setError('')
                  setCheckoutOpen(true)
                }}
              >
                {busy ? 'Enviando…' : 'Enviar pedido →'}
              </button>
              <p className="shop-cart-note">
                El comercio revisará la disponibilidad antes de confirmar. Este pedido no realiza un
                pago.
              </p>
            </aside>
          </div>
        </>
      </main>
      {cart.length > 0 && cartOutsideView && !productToAdd && (
        <button className="shop-cart-shortcut" type="button" onClick={goToCart}>
          <span aria-hidden="true">🛒</span> Ir al carrito
        </button>
      )}
      <footer className="shop-footer">
        <div className="shop-slogan-contact">
          <strong>
            {brand.nombre} · {brand.slogan || 'Tu comercio conectado'}
          </strong>
        </div>
        <span>Pedido enviado → revisión del vendedor → confirmación</span>
      </footer>
    </div>
  )
}
