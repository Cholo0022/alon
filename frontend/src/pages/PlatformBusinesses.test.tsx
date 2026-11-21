import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { PlatformBusinesses } from './PlatformBusinesses'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../lib/http', () => ({ apiFetch: request }))
function fillManager() {
  for (const [label, value] of [
    ['Usuario del gerente', 'gerente'],
    ['Nombre del gerente', 'Encargado'],
    ['Correo del gerente', 'gerente@example.test'],
    ['Contraseña del gerente', '1234'],
  ])
    fireEvent.change(screen.getByLabelText(label), { target: { value } })
}
beforeEach(() => {
  localStorage.clear()
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
})

test('resumes a lost registration after remount without storing its password or changing its token', async () => {
  request.mockReset()
  let attempts = 0
  request.mockImplementation(async (_path, options) => {
    if (options?.method !== 'POST') return Response.json({ businesses: [], movements: [] })
    attempts++
    if (attempts === 1) throw new TypeError('response lost')
    return Response.json({
      id: 7,
      running: true,
      managementUrl: 'http://localhost/negocio',
      shopUrl: 'http://localhost/tienda/negocio',
    })
  })
  const first = render(<PlatformBusinesses />)
  await screen.findByText('No hay negocios que mostrar. Registrá tu primer cliente.')
  fireEvent.click(screen.getByRole('button', { name: 'Registrar negocio' }))
  fireEvent.change(screen.getByLabelText('Nombre comercial'), { target: { value: 'Comercio A' } })
  fillManager()
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar negocio y crear gerente' }))
  await screen.findByText('response lost')
  const saved = localStorage.getItem('alon.pending-business-registration.v1')!
  expect(saved).not.toContain('1234')
  expect(saved).not.toContain('password')
  first.unmount()
  render(<PlatformBusinesses />)
  expect((screen.getByLabelText('Nombre comercial') as HTMLInputElement).disabled).toBe(true)
  expect((screen.getByLabelText('Contraseña del gerente') as HTMLInputElement).value).toBe('')
  fireEvent.change(screen.getByLabelText('Contraseña del gerente'), { target: { value: '1234' } })
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar negocio y crear gerente' }))
  await screen.findByText(/Negocio listo. Se crearon la base y el gerente/)
  const commands = request.mock.calls.filter(([, options]) => options?.method === 'POST')
  expect(JSON.parse(commands[0][1].body).requestId).toBe(JSON.parse(commands[1][1].body).requestId)
  expect(localStorage.getItem('alon.pending-business-registration.v1')).toBeNull()
})

test('preserves the business form when the running server is outdated', async () => {
  request.mockReset()
  request.mockImplementation(async (_path: string, options?: RequestInit) =>
    options?.method === 'POST'
      ? Response.json({}, { status: 404 })
      : Response.json({ businesses: [], movements: [] }),
  )
  render(<PlatformBusinesses />)
  await screen.findByText('No hay negocios que mostrar. Registrá tu primer cliente.')
  fireEvent.click(screen.getByRole('button', { name: 'Registrar negocio' }))
  fireEvent.change(screen.getByLabelText('Nombre comercial'), { target: { value: 'Mi cliente' } })
  fillManager()
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar negocio y crear gerente' }))
  await screen.findByText(/Los datos del formulario se conservaron/)
  expect((screen.getByLabelText('Nombre comercial') as HTMLInputElement).value).toBe('Mi cliente')
})

test.each(['PRUEBA', 'ACTIVO'])(
  'confirms a %s business and its full manager together',
  async (state) => {
    request.mockReset()
    request.mockImplementation(async (_path, options) =>
      Response.json(
        options?.method === 'POST'
          ? {
              id: 7,
              running: true,
              managementUrl: 'http://127.0.0.1:20007/comercio-a',
              shopUrl: 'http://127.0.0.1:20007/tienda/comercio-a',
            }
          : { businesses: [], movements: [] },
      ),
    )
    render(<PlatformBusinesses />)
    await screen.findByText('No hay negocios que mostrar. Registrá tu primer cliente.')
    fireEvent.click(screen.getByRole('button', { name: 'Registrar negocio' }))
    fireEvent.change(screen.getByLabelText('Nombre comercial'), { target: { value: 'Comercio A' } })
    expect(screen.queryByLabelText(/Configuración por única vez/)).toBeNull()
    expect(screen.queryByLabelText(/Abono mensual/)).toBeNull()
    if (state === 'ACTIVO') {
      fireEvent.change(screen.getByLabelText('Estado del servicio'), { target: { value: state } })
      fireEvent.change(screen.getByLabelText(/Configuración por única vez/), {
        target: { value: '500' },
      })
      fireEvent.change(screen.getByLabelText(/Abono mensual/), { target: { value: '1000' } })
      expect(screen.queryByLabelText('Meses de prueba')).toBeNull()
    }
    fillManager()
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar negocio y crear gerente' }))
    await screen.findByText(/Negocio listo. Se crearon la base y el gerente/)
    const call = request.mock.calls.find(([, options]) => options?.method === 'POST')!
    expect(call[0]).toBe('/api/v1/super/businesses/confirm')
    const payload = JSON.parse(call[1].body)
    expect(payload).toMatchObject({
      business: {
        nombre: 'Comercio A',
        estado: state,
        abono: state === 'ACTIVO' ? 1000 : 0,
        respaldo: null,
        vencimiento: null,
      },
      manager: {
        username: 'gerente',
        name: 'Encargado',
        password: '1234',
        email: 'gerente@example.test',
        months: 1,
      },
    })
    expect(payload.business.cargo_configuracion).toBe(state === 'ACTIVO' ? 500 : undefined)
    expect(JSON.parse(call[1].body).manager.permissions).toBeUndefined()
    expect(JSON.parse(call[1].body).manager.role).toBeUndefined()
    expect(screen.queryByLabelText('Contraseña del gerente')).toBeNull()
  },
)

test('displays automatically generated management and shop links after registration', async () => {
  request.mockReset().mockImplementation(async (_path, options) =>
    Response.json(
      options?.method === 'POST'
        ? {
            id: 7,
            running: true,
            managementUrl: 'http://127.0.0.1:20007/comercio-a',
            shopUrl: 'http://127.0.0.1:20007/tienda/comercio-a',
          }
        : { businesses: [], movements: [] },
    ),
  )
  render(<PlatformBusinesses />)
  await screen.findByText('No hay negocios que mostrar. Registrá tu primer cliente.')
  fireEvent.click(screen.getByRole('button', { name: 'Registrar negocio' }))
  expect(screen.queryByLabelText('URL de gestión')).toBeNull()
  fireEvent.change(screen.getByLabelText('Nombre comercial'), { target: { value: 'Comercio A' } })
  fillManager()
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar negocio y crear gerente' }))
  expect(
    (await screen.findByRole('link', { name: 'http://127.0.0.1:20007/comercio-a' })).getAttribute(
      'href',
    ),
  ).toBe('http://127.0.0.1:20007/comercio-a')
  expect(
    screen.getByRole('link', { name: 'http://127.0.0.1:20007/tienda/comercio-a' }),
  ).toBeTruthy()
  expect(screen.getByText(/El negocio está listo para ingresar con su gerente/)).toBeTruthy()
})

test('does not register without a manager and reuses its reference after a partial failure', async () => {
  request.mockReset()
  let attempts = 0
  request.mockImplementation(async (_path, options) => {
    if (options?.method !== 'POST') return Response.json({ businesses: [], movements: [] })
    attempts++
    return attempts === 1
      ? Response.json(
          { id: 7, registered: true, running: false, error: 'No se pudo preparar la base.' },
          { status: 409 },
        )
      : Response.json({
          id: 7,
          running: true,
          managementUrl: 'http://127.0.0.1:20007/comercio-a',
          shopUrl: 'http://127.0.0.1:20007/tienda/comercio-a',
        })
  })
  render(<PlatformBusinesses />)
  await screen.findByText('No hay negocios que mostrar. Registrá tu primer cliente.')
  fireEvent.click(screen.getByRole('button', { name: 'Registrar negocio' }))
  fireEvent.change(screen.getByLabelText('Nombre comercial'), { target: { value: 'Comercio A' } })
  const button = screen.getByRole('button', { name: 'Confirmar negocio y crear gerente' })
  fireEvent.submit(button.closest('form')!)
  expect(attempts).toBe(0)
  fillManager()
  fireEvent.click(button)
  await screen.findByText('No se pudo preparar la base.')
  expect(screen.queryByText(/Negocio listo/)).toBeNull()
  expect((screen.getByLabelText('Contraseña del gerente') as HTMLInputElement).value).toBe('1234')
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar negocio y crear gerente' }))
  await screen.findByText(/Negocio listo. Se crearon la base y el gerente/)
  const commands = request.mock.calls.filter(([, options]) => options?.method === 'POST')
  expect(commands).toHaveLength(2)
  expect(JSON.parse(commands[0][1].body).requestId).toBe(JSON.parse(commands[1][1].body).requestId)
})

test('counts payments by receipt date and tracks coverage independently', async () => {
  request.mockReset().mockImplementation(async () =>
    Response.json({
      businesses: [
        { id: 7, nombre: 'Comercio A', estado: 'ACTIVO', alta: '2026-11-01', abono: 100 },
        { id: 8, nombre: 'Comercio B', estado: 'ACTIVO', alta: '2026-11-01', abono: 200 },
      ],
      movements: [],
      receipts: [
        {
          negocio_id: 7,
          fecha: '2026-11-12',
          periodo_desde: '2026-11-01',
          meses: 2,
          cargo_configuracion: 50,
          total: 250,
        },
      ],
      rates: [
        { negocio_id: 8, desde: '2026-11-01', importe: 140 },
        { negocio_id: 8, desde: '2026-12-01', importe: 200 },
      ],
    }),
  )
  render(<PlatformBusinesses />)
  await screen.findByRole('button', { name: 'Ver Comercio A' })
  const month = screen.getByLabelText('Mes del informe')
  fireEvent.change(month, { target: { value: '2026-11' } })
  const collected = screen.getByText('Cobrado en el mes')
  const pending = screen.getByText('Abonos pendientes del mes')
  expect(collected.querySelector('strong')?.textContent).toContain('250')
  expect(pending.querySelector('strong')?.textContent).toContain('140')
  fireEvent.change(month, { target: { value: '2026-12' } })
  expect(collected.querySelector('strong')?.textContent).toContain('0,00')
  expect(pending.querySelector('strong')?.textContent).toContain('200')
  expect(screen.getByText('Próximo pendiente 01/2027')).toBeTruthy()
  fireEvent.change(screen.getByLabelText('Filtrar seguimiento'), { target: { value: 'pending' } })
  expect(screen.queryByRole('button', { name: 'Ver Comercio A' })).toBeNull()
  expect(screen.getByRole('button', { name: 'Ver Comercio B' })).toBeTruthy()
})

test('shows ledger balance and posts a payment to the selected business', async () => {
  request.mockReset()
  request.mockImplementation(async () =>
    Response.json({
      businesses: [
        {
          id: 7,
          nombre: 'Comercio A',
          estado: 'ACTIVO',
          plan: 'Inicial',
          abono: 100,
          saldo: 80,
          gestion_url: '',
          tienda_url: '',
        },
      ],
      movements: [],
    }),
  )
  render(<PlatformBusinesses />)
  const view = await screen.findByRole('button', { name: 'Ver Comercio A' })
  const row = view.closest('tr')!
  expect(within(row).getAllByRole('button')).toHaveLength(1)
  expect(view.textContent).toBe('Ver')
  expect(screen.queryByRole('button', { name: 'Editar Comercio A' })).toBeNull()
  expect(screen.queryByLabelText('Estado de Comercio A')).toBeNull()
  fireEvent.click(view)
  expect(screen.getByRole('region', { name: 'Detalle de Comercio A' })).toBeTruthy()
  expect(screen.getByRole('button', { name: 'Editar Comercio A' })).toBeTruthy()
  expect(screen.getByRole('button', { name: 'Habilitar sistema de Comercio A' })).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'Servicio' }))
  expect(screen.getByLabelText('Estado de Comercio A')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'Cobros' }))
  fireEvent.change(screen.getByLabelText('Importe (ARS)'), { target: { value: '80' } })
  fireEvent.change(screen.getByLabelText('Concepto o comprobante'), {
    target: { value: 'Transferencia noviembre' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Registrar movimiento' }))
  await waitFor(() =>
    expect(
      request.mock.calls.some(
        ([path, options]) =>
          path === '/api/v1/super/businesses/7/movements' &&
          JSON.parse(options.body).importe === 80,
      ),
    ).toBe(true),
  )
  fireEvent.click(screen.getByRole('button', { name: 'Servicio' }))
  fireEvent.change(screen.getByLabelText('Estado de Comercio A'), { target: { value: 'BAJA' } })
  const confirmation = screen.getByRole('dialog', { name: 'Confirmar baja de Comercio A' })
  expect(
    within(confirmation).getByRole('textbox', {
      name: 'Para eliminar, escribí el nombre exacto: Comercio A',
    }),
  ).toBeTruthy()
  expect(document.activeElement).toBe(
    within(confirmation).getByRole('button', { name: 'No dar de baja' }),
  )
  fireEvent.keyDown(confirmation, { key: 'Enter' })
  expect(request.mock.calls.some(([path]) => path.endsWith('/state'))).toBe(false)
  fireEvent.click(screen.getByRole('button', { name: 'Volver al listado' }))
  expect(screen.queryByRole('region', { name: 'Detalle de Comercio A' })).toBeNull()
})
