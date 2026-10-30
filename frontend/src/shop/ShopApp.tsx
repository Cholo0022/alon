import { useEffect, useRef, useState, type FormEvent } from 'react'
import { currency, formatDateTimeDisplay } from '../lib/format'
import { shopRequest } from './api'
import './shop.css'
import { ProductGallery } from './ProductGallery'

type Product = {
  id: number
  nombre: string
  codigo?: string
  rubro?: string
  unidad: string
  precio_venta: number
  stock: number
  imagen_clave: string | null
  imagen_ilustrativa: boolean
  photos?: { key: string; illustrative: boolean }[]
}
type CartLine = Product & { quantity: number }
type PendingOrder = { body: string; key: string }
type Buyer = { id: number; nombre: string; apellido: string; telefono: string; domicilio: string }
type Catalog = {
  heroImage?: string
  items: Product[]
  total: number
  categories: { nombre: string }[]
  store: { nombre: string }
}
type Order = { id: number; estado: string; total_estimado: number; creado_en: string }
const quantity = (n: number) =>
  new Intl.NumberFormat('es-AR', { maximumFractionDigits: 3 }).format(n)
const stateName = (s: string) =>
  s === 'CONFIRMADO' ? 'Confirmado' : s === 'CANCELADO' ? 'Cancelado' : 'Pendiente de confirmación'

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

export default function ShopApp() {
  const [catalog, setCatalog] = useState<Catalog>({
    items: [],
    total: 0,
    categories: [],
    store: { nombre: 'Alon' },
  })
  const [cart, setCart] = useState<CartLine[]>(initialCart)
  const [buyer, setBuyer] = useState<Buyer | null>(null)
  const [screen, setScreen] = useState<'catalog' | 'account' | 'orders'>('catalog')
  const [register, setRegister] = useState(false)
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('')
  const [page, setPage] = useState(0)
  const [refresh, setRefresh] = useState(0)
  const [busy, setBusy] = useState(false)
  const [paymentMethod, setPaymentMethod] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [orders, setOrders] = useState<Order[]>([])
  const cartPanel = useRef<HTMLElement>(null)
  const pending = useRef<PendingOrder | null>(pendingOrder())
  const total = cart.reduce(
    (sum, p) => sum + Math.round(Number(p.precio_venta) * p.quantity * 100) / 100,
    0,
  )

  useEffect(() => {
    const previousTitle = document.title
    document.title = 'Alon · Tienda online'
    return () => {
      document.title = previousTitle
    }
  }, [])
  useEffect(() => {
    void shopRequest<{ user: Buyer | Record<string, never> }>('/session')
      .then((s) => setBuyer(s.user.id ? (s.user as Buyer) : null))
      .catch((e) => setError(String(e.message)))
  }, [])
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
  useEffect(() => {
    if (screen !== 'orders' || !buyer) return
    const controller = new AbortController()
    const load = () => {
      void shopRequest<Order[]>('/orders', { signal: controller.signal })
        .then(setOrders)
        .catch((e) => {
          if (!controller.signal.aborted) setError(e.message)
        })
    }
    load()
    const timer = window.setInterval(load, 15000)
    return () => {
      controller.abort()
      clearInterval(timer)
    }
  }, [screen, buyer, refresh])

  const add = (p: Product) => {
    setNotice('')
    setCart((current) => {
      const existing = current.find((x) => x.id === p.id)
      if (existing)
        return current.map((x) =>
          x.id === p.id
            ? {
                ...p,
                quantity: Math.min(Number(p.stock), Math.round((x.quantity + 1) * 1000) / 1000),
              }
            : x,
        )
      if (current.length >= 100) {
        setError('El carrito admite hasta 100 artículos distintos.')
        return current
      }
      return [...current, { ...p, quantity: Math.min(1, Number(p.stock)) }]
    })
  }

  const auth = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    const form = event.currentTarget
    const data = new FormData(form)
    try {
      const input = register
        ? Object.fromEntries(data.entries())
        : { phone: data.get('phone'), password: data.get('password') }
      const user = await shopRequest<Buyer>(register ? '/register' : '/login', {
        method: 'POST',
        body: JSON.stringify(input),
      })
      setBuyer(user)
      form.reset()
      setScreen('catalog')
      setNotice('Ya podés enviar tu pedido.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo ingresar.')
    } finally {
      setBusy(false)
    }
  }

  const checkout = async () => {
    if (!paymentMethod) {
      setError('Elegí un medio de pago antes de enviar el pedido.')
      return
    }
    if (!buyer) {
      setScreen('account')
      setNotice('Ingresá o creá tu cuenta para enviar el pedido.')
      return
    }
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
        paymentMethod,
        lines: updated.map((p) => ({
          articleId: p.id,
          quantity: p.quantity,
          expectedPrice: Number(p.precio_venta),
        })),
      })
      if (pending.current?.body !== body) pending.current = { body, key: crypto.randomUUID() }
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
      setNotice('Pedido #' + result.id + ' enviado. El comercio lo revisará antes de confirmarlo.')
      setRefresh((n) => n + 1)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo enviar el pedido.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="shop-app">
      <header className="shop-header">
        <button
          className="shop-brand"
          onClick={() => setScreen('catalog')}
          aria-label="Alon, inicio"
        >
          alon<span>TIENDA ONLINE</span>
        </button>
        <input
          className="shop-search"
          type="search"
          placeholder="¿Qué estás buscando?"
          aria-label="Buscar productos"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            setPage(0)
            setScreen('catalog')
          }}
        />
        <div className="shop-header-actions">
          {buyer ? (
            <>
              <button onClick={() => setScreen('orders')}>Mis pedidos</button>
              <button
                onClick={() => {
                  void shopRequest('/logout', { method: 'POST' })
                    .then(() => {
                      setBuyer(null)
                      setOrders([])
                      setScreen('catalog')
                      setNotice('Sesión cerrada.')
                    })
                    .catch((e) => setError(e.message))
                }}
              >
                Salir
              </button>
            </>
          ) : (
            <button onClick={() => setScreen('account')}>Ingresar</button>
          )}
          <button
            className="shop-solid"
            onClick={() => {
              setScreen('catalog')
              requestAnimationFrame(() =>
                cartPanel.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }),
              )
            }}
          >
            Carrito · {quantity(cart.reduce((n, p) => n + p.quantity, 0))}
          </button>
        </div>
      </header>
      <div className="shop-subnav">
        <strong>{catalog.store.nombre}</strong>
        <span>Precios en pesos argentinos</span>
        <span>Solo productos disponibles</span>
      </div>
      <main className="shop-main">
        {error && (
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
        {screen === 'account' ? (
          <section className="shop-account">
            <p className="shop-eyebrow">TU CUENTA EN ALON</p>
            <h1>{register ? 'Creá tu cuenta' : 'Bienvenido de nuevo'}</h1>
            <p>Ingresá para enviar y consultar tus pedidos.</p>
            <form onSubmit={(e) => void auth(e)} className="shop-account-form">
              {register && (
                <>
                  <label>
                    Nombre
                    <input name="firstName" maxLength={60} autoComplete="given-name" required />
                  </label>
                  <label>
                    Apellido
                    <input name="lastName" maxLength={60} autoComplete="family-name" required />
                  </label>
                  <label>
                    DNI
                    <input
                      name="dni"
                      inputMode="numeric"
                      pattern="[0-9]{7,8}"
                      maxLength={8}
                      placeholder="Sin puntos"
                      required
                    />
                  </label>
                  <label className="shop-wide">
                    Domicilio
                    <input
                      name="address"
                      maxLength={180}
                      autoComplete="street-address"
                      placeholder="Calle, número y localidad"
                      required
                    />
                  </label>
                </>
              )}
              <label className="shop-wide">
                Teléfono con código de país
                <input
                  name="phone"
                  type="tel"
                  autoComplete="username"
                  placeholder="Ej.: +54 9 11 2345 6789"
                  maxLength={25}
                  required
                />
              </label>
              <label className="shop-wide">
                Contraseña
                <input
                  name="password"
                  type="password"
                  autoComplete={register ? 'new-password' : 'current-password'}
                  minLength={register ? 12 : undefined}
                  maxLength={128}
                  required
                />
                {register && <small>Entre 12 y 128 caracteres.</small>}
              </label>
              <button className="shop-solid shop-wide" disabled={busy}>
                {busy ? 'Un momento…' : register ? 'Crear cuenta' : 'Ingresar'}
              </button>
              <button
                type="button"
                className="shop-link shop-wide"
                onClick={() => setRegister((v) => !v)}
              >
                {register ? 'Ya tengo una cuenta' : 'Primera vez · Crear cuenta'}
              </button>
            </form>
          </section>
        ) : screen === 'orders' ? (
          <section>
            <p className="shop-eyebrow">TU CUENTA</p>
            <h1>Mis pedidos</h1>
            <p>{buyer?.nombre}, estos son tus últimos pedidos.</p>
            <div className="shop-orders">
              {orders.map((o) => (
                <article key={o.id}>
                  <div>
                    <strong>Pedido #{o.id}</strong>
                    <small>{formatDateTimeDisplay(o.creado_en)}</small>
                  </div>
                  <span className="shop-badge">{stateName(o.estado)}</span>
                  <strong>{currency.format(Number(o.total_estimado))}</strong>
                </article>
              ))}
              {!orders.length && <p>Todavía no enviaste pedidos.</p>}
            </div>
            <button onClick={() => setScreen('catalog')}>Seguir comprando</button>
          </section>
        ) : (
          <>
            <section className="shop-hero">
              <div>
                <p className="shop-eyebrow">TU COMERCIO, MÁS CERCA</p>
                <h1>
                  Todo lo que necesitás,
                  <br />a un carrito de distancia.
                </h1>
                <p>Elegí tus productos. Nosotros preparamos tu pedido.</p>
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
            <nav className="shop-categories" aria-label="Filtrar por rubro">
              <button
                aria-pressed={category === ''}
                onClick={() => {
                  setCategory('')
                  setPage(0)
                }}
              >
                Todos
              </button>
              {catalog.categories.map((c) => (
                <button
                  key={c.nombre}
                  aria-pressed={category === c.nombre}
                  onClick={() => {
                    setCategory(c.nombre)
                    setPage(0)
                  }}
                >
                  {c.nombre}
                </button>
              ))}
            </nav>
            <div className="shop-layout">
              <section aria-label="Catálogo de productos">
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
                  {catalog.items.map((p) => (
                    <article className="shop-product" key={p.id}>
                      <ProductGallery
                        name={p.nombre}
                        photos={p.photos}
                        cover={p.imagen_clave}
                        illustrative={Boolean(p.imagen_ilustrativa)}
                      />
                      <div className="shop-product-body">
                        <span className="shop-product-category">{p.rubro}</span>
                        <h2>{p.nombre}</h2>
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
                  ))}
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
              <aside className="shop-cart" ref={cartPanel}>
                <h2>Tu carrito</h2>
                {cart.map((p) => (
                  <div className="shop-cart-line" key={p.id}>
                    <div>
                      <strong>{p.nombre}</strong>
                      <span>
                        {currency.format(
                          Math.round(Number(p.precio_venta) * p.quantity * 100) / 100,
                        )}
                      </span>
                    </div>
                    <div>
                      <label>
                        Cantidad
                        <input
                          type="number"
                          min="0"
                          step="1"
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
                <label className="shop-payment">
                  Medio de pago
                  <select
                    aria-label="Medio de pago"
                    value={paymentMethod}
                    onChange={(e) => setPaymentMethod(e.target.value)}
                    disabled={busy}
                  >
                    <option value="">Seleccioná un medio de pago</option>
                    <option value="EFECTIVO">Efectivo</option>
                    <option value="TRANSFERENCIA">Transferencia</option>
                    <option value="MERCADO_PAGO">Mercado Pago</option>
                  </select>
                </label>
                <div className="shop-total">
                  <span>Total estimado</span>
                  <strong>{currency.format(total)}</strong>
                </div>
                <button
                  className="shop-solid shop-checkout"
                  disabled={busy || !cart.length}
                  onClick={() => void checkout()}
                >
                  {busy ? 'Enviando…' : 'Enviar pedido →'}
                </button>
                <p className="shop-cart-note">
                  El comercio revisará la disponibilidad antes de confirmar. Este pedido no realiza
                  un pago.
                </p>
                {buyer && (
                  <p className="shop-cart-note">
                    Pedido a nombre de {buyer.nombre} {buyer.apellido}.
                  </p>
                )}
              </aside>
            </div>
          </>
        )}
      </main>
      <footer className="shop-footer">
        <strong>alon · Tu comercio conectado</strong>
        <span>Pedido enviado → revisión del vendedor → confirmación</span>
      </footer>
    </div>
  )
}
