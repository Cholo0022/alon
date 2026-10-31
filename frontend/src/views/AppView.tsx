// Muestra el ingreso a Alon y, después de iniciar sesión, las secciones permitidas para cada usuario.
import { useCallback, useEffect, useLayoutEffect, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { currency, textValue } from '../lib/format'
import { navigation, descriptions, type Section } from '../lib/navigation'
import { type ApiRow } from '../hooks/useApiRows'
import { ApiFeedback } from '../components/ApiFeedback'
import { PendingOrdersNotice } from '../components/PendingOrdersNotice'
import { ArticleList, CategoriesPage } from '../pages/CatalogPage'
import { CustomerList } from '../pages/CustomerPage'
import { SupplierList } from '../pages/SupplierPage'
import { SalesPage } from '../pages/SalesPage'
import { PurchasesPage } from '../pages/PurchasesPage'
import { CashPage } from '../pages/CashPage'
import { ReportsPage } from '../pages/ReportsPage'
import { UsersPage } from '../pages/UsersPage'
import { StorePage } from '../pages/StorePage'
import { isSessionUser, type SessionUser, type AuthStatus } from '../lib/session'
import { BusinessPage } from '../pages/BusinessPage'
import { useBusinessBrand, brandStyle } from '../lib/businessBrand'
import '../App.css'

/** Obtiene los accesos efectivos del usuario; el dueño recibe todas las secciones y los permisos adicionales de costos y descuentos. */
function userPermissions(user: SessionUser) {
  return user.rol === 'DUENO'
    ? [...navigation.map((item) => item.id), 'descuentos', 'costos']
    : textValue(user.permisos).split(',').filter(Boolean)
}

/** Construye la navegación de gestión según permisos, muestra el resumen y presenta la sección activa desde su inicio. */
export function MainApp({ user, onLogout }: { user: SessionUser; onLogout: () => void }) {
  const brand = useBusinessBrand()
  const permissions = userPermissions(user)
  const canViewSummary = permissions.includes('inicio')
  const [renderTime] = useState(() => new Date())
  const visibleNavigation = navigation.filter(
    (item) => permissions.includes(item.id) && (item.id !== 'negocio' || user.rol === 'DUENO'),
  )
  const initialSection = (
    permissions.includes('inicio') ? 'inicio' : (visibleNavigation[0]?.id ?? 'ventas')
  ) as Section
  const [activeSection, setActiveSection] = useState<Section>(initialSection)
  // Comienza cada sección arriba; las actualizaciones dentro de ella conservan la posición.
  useLayoutEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
  }, [activeSection])
  const [summary, setSummary] = useState<ApiRow | null>(null)
  const activeItem =
    visibleNavigation.find((item) => item.id === activeSection) ?? visibleNavigation[0]

  useEffect(() => {
    if (!canViewSummary) return
    /** Consulta los indicadores del panel inicial y limpia el resumen si la API no está disponible. */
    const load = () =>
      apiFetch('/api/v1/alon/summary')
        .then((response) => {
          if (!response.ok) throw new Error('API unavailable')
          return response.json() as Promise<ApiRow>
        })
        .then(setSummary)
        .catch(() => setSummary(null))
    /** Vuelve a consultar los datos vinculados a la sesión o al resumen cuando cambia el sistema o se recupera el foco. */
    const refresh = () => {
      void load()
    }
    void load()
    window.addEventListener('alon:refresh', refresh)
    return () => window.removeEventListener('alon:refresh', refresh)
  }, [canViewSummary])

  if (!activeItem)
    return (
      <main className="auth-shell">
        <p>Tu cuenta no tiene secciones habilitadas. Consultá al dueño del comercio.</p>
        <button type="button" onClick={onLogout}>
          Cerrar sesión
        </button>
      </main>
    )
  return (
    <div className="app-shell business-themed" style={brandStyle(brand)}>
      <aside className="sidebar">
        <a
          className="brand"
          href="#inicio"
          onClick={() => setActiveSection(initialSection)}
          aria-label="Alon, inicio"
        >
          <span className="brand-mark">a</span>
          <span className="brand-name">
            alon<span>.</span>
          </span>
        </a>
        <div className="store-switcher">
          <span className="store-avatar">{brand.nombre.slice(0, 1).toUpperCase()}</span>
          <span className="store-copy">
            <strong>{brand.nombre}</strong>
            <small>{brand.slogan || 'Tu comercio en Alon'}</small>
          </span>
          <span className="chevron">⌄</span>
        </div>
        <p className="nav-caption">GESTIÓN</p>
        <nav aria-label="Navegación principal">
          {visibleNavigation.map((item) => (
            <button
              className={`nav-item ${activeSection === item.id ? 'active' : ''}`}
              key={item.id}
              onClick={() => setActiveSection(item.id)}
              type="button"
            >
              <span className="nav-icon" aria-hidden="true">
                {item.icon}
              </span>
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
      </aside>
      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumb">
            {brand.nombre} <span>/</span> <strong>{activeItem.label}</strong>
          </div>
          <div className="topbar-actions">
            <span className="today-label">
              {new Intl.DateTimeFormat('es-AR', {
                dateStyle: 'full',
                timeZone: 'America/Argentina/Buenos_Aires',
              }).format(renderTime)}
            </span>
            <span className="session-summary">
              <strong>Sesión iniciada</strong>
              <span>
                {textValue(user.usuario)} · {textValue(user.rol)}
              </span>
            </span>
            <button className="select-button" type="button" onClick={onLogout}>
              Cerrar sesión
            </button>
          </div>
        </header>
        <div className="page-wrap">
          {permissions.includes('tienda') && (
            <PendingOrdersNotice onOpen={() => setActiveSection('tienda')} />
          )}
          <section className="welcome-row">
            <div>
              <p className="eyebrow">TU NEGOCIO, MÁS SIMPLE</p>
              <h1>{activeSection === 'inicio' ? 'Buen día 👋' : activeItem.label}</h1>
              <p className="page-subtitle">{descriptions[activeSection]}</p>
            </div>
            {activeSection === 'inicio' && permissions.includes('ventas') && (
              <button
                className="primary-button"
                type="button"
                onClick={() => setActiveSection('ventas')}
              >
                <span>＋</span> Nueva venta
              </button>
            )}
          </section>
          {activeSection === 'inicio' ? (
            <>
              <div className="notice">
                <span className="notice-icon">✦</span>
                <span>
                  <strong>
                    {summary ? 'Datos del comercio conectados' : 'Cargando datos del comercio'}
                  </strong>{' '}
                  — Información registrada en tu comercio.
                </span>
                <span className="notice-badge">ALON</span>
              </div>
              <section className="metrics-grid" aria-label="Resumen del comercio">
                <article className="metric-card">
                  <div className="metric-heading">
                    <span>Artículos</span>
                    <span className="metric-icon mint">▦</span>
                  </div>
                  <strong className="metric-value">{String(summary?.articulos ?? '—')}</strong>
                  <span className="metric-foot">Catálogo del comercio</span>
                </article>
                <article className="metric-card">
                  <div className="metric-heading">
                    <span>Ventas confirmadas</span>
                    <span className="metric-icon lilac">↗</span>
                  </div>
                  <strong className="metric-value">{String(summary?.ventas ?? '—')}</strong>
                  <span className="metric-foot">En {String(summary?.dias ?? '—')} días</span>
                </article>
                <article className="metric-card">
                  <div className="metric-heading">
                    <span>Ventas acumuladas</span>
                    <span className="metric-icon peach">▤</span>
                  </div>
                  <strong className="metric-value metric-money">
                    {summary ? currency.format(Number(summary.facturacion ?? 0)) : '—'}
                  </strong>
                  <span className="metric-foot">Importes en ARS</span>
                </article>
                <article className="metric-card">
                  <div className="metric-heading">
                    <span>Ganancia bruta</span>
                    <span className="metric-icon yellow">⌁</span>
                  </div>
                  <strong className="metric-value metric-money">
                    {summary?.margen_bruto === undefined
                      ? '—'
                      : currency.format(Number(summary.margen_bruto))}
                  </strong>
                  <span className="metric-foot">Antes de gastos operativos</span>
                </article>
              </section>
              <section className="content-grid">
                <article className="panel sales-panel">
                  <div className="panel-heading">
                    <div>
                      <h2>Movimiento de ventas</h2>
                      <p>{String(summary?.ventas ?? '—')} operaciones confirmadas.</p>
                    </div>
                    <button
                      className="select-button"
                      type="button"
                      disabled={!permissions.includes('informes')}
                      onClick={() => setActiveSection('informes')}
                    >
                      Ver reportes <span>↗</span>
                    </button>
                  </div>
                  <div className="chart-empty">
                    <div className="chart-lines">
                      <span />
                      <span />
                      <span />
                      <span />
                    </div>
                    <div className="chart-message">
                      <span className="chart-symbol">↗</span>
                      <strong>
                        {summary
                          ? currency.format(Number(summary.facturacion ?? 0))
                          : 'Cargando ventas…'}
                      </strong>
                      <small>Ventas registradas · comprobantes no fiscales</small>
                    </div>
                    <div className="chart-days">
                      <span>{String(summary?.dias ?? '—')} días con ventas</span>
                      <span>{String(summary?.ventas ?? '—')} operaciones</span>
                    </div>
                  </div>
                </article>
                <article className="panel quick-panel">
                  <div className="panel-heading">
                    <div>
                      <h2>Accesos del comercio</h2>
                      <p>Listados y reportes de la base MySQL</p>
                    </div>
                  </div>
                  <div className="quick-actions">
                    <button
                      type="button"
                      disabled={!permissions.includes('ventas')}
                      onClick={() => setActiveSection('ventas')}
                    >
                      <span className="quick-icon violet">↗</span>
                      <span>
                        <strong>Ver ventas</strong>
                        <small>Consultar operaciones registradas</small>
                      </span>
                      <b>›</b>
                    </button>
                    <button
                      type="button"
                      disabled={!permissions.includes('caja')}
                      onClick={() => setActiveSection('caja')}
                    >
                      <span className="quick-icon green">▣</span>
                      <span>
                        <strong>Ver cierres de caja</strong>
                        <small>Consultar arqueos y diferencias</small>
                      </span>
                      <b>›</b>
                    </button>
                    <button
                      type="button"
                      disabled={!permissions.includes('compras')}
                      onClick={() => setActiveSection('compras')}
                    >
                      <span className="quick-icon blue">↙</span>
                      <span>
                        <strong>Ver compras</strong>
                        <small>Consultar compras y recepciones</small>
                      </span>
                      <b>›</b>
                    </button>
                  </div>
                </article>
              </section>
            </>
          ) : activeSection === 'articulos' ? (
            <ArticleList
              canEditCosts={permissions.includes('costos') || permissions.includes('compras')}
            />
          ) : activeSection === 'rubros' ? (
            <CategoriesPage />
          ) : activeSection === 'clientes' ? (
            <CustomerList />
          ) : activeSection === 'proveedores' ? (
            <SupplierList />
          ) : activeSection === 'ventas' ? (
            <SalesPage
              canDiscount={permissions.includes('descuentos')}
              onOpenCash={() => setActiveSection('caja')}
            />
          ) : activeSection === 'compras' ? (
            <PurchasesPage />
          ) : activeSection === 'caja' ? (
            <CashPage />
          ) : activeSection === 'informes' ? (
            <ReportsPage />
          ) : activeSection === 'tienda' ? (
            <StorePage />
          ) : activeSection === 'negocio' ? (
            <BusinessPage brand={brand} />
          ) : activeSection === 'usuarios' ? (
            <UsersPage />
          ) : (
            <section className="module-placeholder">
              <div className="placeholder-icon">{activeItem.icon}</div>
              <p className="eyebrow">MÓDULO DE ALON</p>
              <h2>{activeItem.label}</h2>
              <p>{descriptions[activeSection]}</p>
              <span className="coming-soon">Preparando este espacio para tu comercio</span>
            </section>
          )}
          <footer className="page-footer">
            <span>© {renderTime.getFullYear()} Andrés Alexis Bozzani</span>
            <span>Alon · Gestión comercial simple</span>
            <span>Comprobantes internos no fiscales.</span>
          </footer>
        </div>
      </main>
    </div>
  )
}

/** Muestra el inicio de sesión o la creación de la primera cuenta dueña según el estado del comercio. */
export function AuthScreen({
  setup,
  onAuthenticated,
}: {
  setup: boolean
  onAuthenticated: (user: SessionUser) => void
}) {
  const [renderTime] = useState(() => new Date())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  /** Envía las credenciales de acceso o configuración inicial y acepta únicamente una respuesta con estructura de usuario válida. */
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    const form = new FormData(event.currentTarget)
    try {
      const username = form.get('username')
      const response = await apiFetch(setup ? '/api/v1/auth/setup' : '/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username,
          name: form.get('name') || username,
          password: form.get('password'),
          storeCode: form.get('storeCode') || null,
        }),
      })
      const body: unknown = await response.json().catch(() => ({}))
      if (!response.ok || !isSessionUser(body)) {
        const error = body as { error?: string }
        throw new Error(error.error ?? 'No se pudo iniciar sesión.')
      }
      onAuthenticated(body)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo conectar con el servidor.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <main className="auth-shell">
      <section className="auth-card">
        <span className="brand-name">
          alon<span>.</span>
        </span>
        <p className="eyebrow">GESTIÓN COMERCIAL</p>
        <h1>{setup ? 'Configurá tu comercio' : 'Iniciar sesión'}</h1>
        <p>
          {setup
            ? 'Creá la cuenta dueña para administrar el sistema y sus usuarios.'
            : 'Ingresá con tu usuario y contraseña.'}
        </p>
        <form className="entry-form" onSubmit={submit}>
          {setup && (
            <label>
              Nombre del dueño
              <input name="name" autoComplete="name" />
            </label>
          )}
          {!setup && (
            <label>
              Código del comercio (opcional)
              <input name="storeCode" maxLength={40} autoComplete="organization" />
            </label>
          )}
          <label>
            Usuario
            <input name="username" autoComplete="username" minLength={3} required />
          </label>
          <label>
            Contraseña
            <input
              name="password"
              type="password"
              autoComplete={setup ? 'new-password' : 'current-password'}
              minLength={setup ? 12 : undefined}
              maxLength={128}
              required
            />
          </label>
          <button className="primary-button" type="submit" disabled={busy}>
            {busy ? 'Ingresando…' : setup ? 'Crear cuenta dueña' : 'Ingresar'}
          </button>
        </form>
        {error && (
          <p className="entry-message" role="alert">
            {error}
          </p>
        )}
        <footer className="auth-copyright">
          © {renderTime.getFullYear()} Andrés Alexis Bozzani
        </footer>
      </section>
    </main>
  )
}

/** Consulta la sesión y alterna entre acceso, aplicación y error de conexión; también coordina expiración y cierre de sesión. */
export function App() {
  const [status, setStatus] = useState<AuthStatus | null>(null)
  const [connectionError, setConnectionError] = useState(false)
  /** Consulta y valida el estado de autenticación y configuración inicial; actualiza la sesión o el error de conexión. */
  const loadStatus = useCallback(
    () =>
      apiFetch('/api/v1/auth/status')
        .then(async (response) => {
          if (!response.ok) throw new Error('No se pudo consultar la sesión.')
          const body = (await response.json()) as AuthStatus
          if (
            typeof body.setupRequired !== 'boolean' ||
            (body.user !== null && !isSessionUser(body.user))
          )
            throw new Error('La respuesta de sesión no es válida.')
          setStatus(body)
          setConnectionError(false)
        })
        .catch(() => setConnectionError(true)),
    [],
  )
  useEffect(() => {
    void loadStatus()
    /** Vuelve a consultar los datos vinculados a la sesión o al resumen cuando cambia el sistema o se recupera el foco. */
    const refresh = () => {
      void loadStatus()
    }
    /** Borra el usuario autenticado cuando la API avisa que la sesión venció para volver a mostrar el acceso. */
    const expired = () => setStatus({ setupRequired: false, user: null, csrfToken: '' })
    window.addEventListener('alon:refresh', refresh)
    window.addEventListener('focus', refresh)
    window.addEventListener('alon:session-expired', expired)
    return () => {
      window.removeEventListener('alon:refresh', refresh)
      window.removeEventListener('focus', refresh)
      window.removeEventListener('alon:session-expired', expired)
    }
  }, [loadStatus])
  /** Solicita cerrar la sesión y retira el usuario de la pantalla solo después de recibir confirmación. */
  const logout = async () => {
    try {
      const response = await apiFetch('/api/v1/auth/logout', { method: 'POST' })
      if (response.ok) setStatus((current) => (current ? { ...current, user: null } : current))
    } catch {
      setConnectionError(true)
    }
  }
  return (
    <>
      <ApiFeedback />
      {connectionError ? (
        <main className="auth-shell">
          <p>No se pudo conectar con Alon.</p>
          <button onClick={() => void loadStatus()}>Reintentar</button>
        </main>
      ) : !status ? (
        <main className="auth-shell">
          <p>Conectando con Alon…</p>
        </main>
      ) : !status.user || Object.keys(status.user).length === 0 ? (
        <AuthScreen
          setup={status.setupRequired}
          onAuthenticated={(user) => setStatus({ setupRequired: false, user, csrfToken: '' })}
        />
      ) : (
        <MainApp
          key={
            textValue(status.user.id) + textValue(status.user.rol) + textValue(status.user.permisos)
          }
          user={status.user}
          onLogout={() => void logout()}
        />
      )}
    </>
  )
}

export default App
