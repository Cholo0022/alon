import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { apiFetch } from '../lib/http'
import { AccountingPage } from './AccountingPage'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
let config: Record<string, unknown>
let state = 'SIN_ABRIR'
beforeEach(() => {
  config = {
    nombre: 'Comercio de prueba',
    cuit_contable: '',
    condicion_iva: 'RESPONSABLE_INSCRIPTO',
    control_periodos: false,
    carga_impuestos: false,
  }
  state = 'SIN_ABRIR'
  vi.mocked(apiFetch)
    .mockReset()
    .mockImplementation(async (url, options) => {
      if (url.endsWith('/settings') && options?.method === 'PUT') {
        const body = JSON.parse(String(options.body)) as Record<string, unknown>
        config = {
          ...config,
          carga_impuestos: body.cargaImpuestos,
          control_periodos: body.controlPeriodos,
          cuit_contable: body.cuit,
        }
        return Response.json(config)
      }
      if (url.endsWith('/settings')) return Response.json(config)
      if (url.includes('/history')) return Response.json([])
      if (url.endsWith('/close')) {
        state = 'CERRADO'
        return Response.json({ estado: state })
      }
      return Response.json({
        mes: '2026-11',
        generado_en: '2026-11-01T12:00:00',
        fuente: 'ACTUAL',
        estado_periodo: state,
        configuracion: config,
        resumen: {
          operaciones_sin_datos_fiscales: 0,
          comprobantes_sin_controlar: 0,
          cajas_abiertas: 0,
        },
        notas: ['Informe auxiliar para el contador.'],
        ventas: [],
        compras: [],
        gastos: [],
        caja: [],
        fiscales: [],
        alicuotas: [],
      })
    })
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.setAttribute('open', '')
    },
  })
  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.removeAttribute('open')
    },
  })
})
it('keeps the two switches independent and off by default', async () => {
  render(<AccountingPage canManage />)
  await screen.findByText(/Informe mensual para el contador/)
  const periods = screen.getByRole('checkbox', {
    name: 'Activar control de períodos mensuales',
  }) as HTMLInputElement
  const taxes = screen.getByRole('checkbox', {
    name: 'Activar carga de comprobantes con impuestos',
  }) as HTMLInputElement
  expect(periods.checked).toBe(false)
  expect(taxes.checked).toBe(false)
  const user = userEvent.setup()
  await user.click(taxes)
  await user.type(screen.getByLabelText('CUIT del negocio'), '30712345671')
  await user.click(screen.getByRole('button', { name: 'Guardar opciones' }))
  const call = vi.mocked(apiFetch).mock.calls.find(([, options]) => options?.method === 'PUT')
  expect(call).toBeTruthy()
  const body = JSON.parse(String(call?.[1]?.body)) as Record<string, unknown>
  expect(body.controlPeriodos).toBe(false)
  expect(body.cargaImpuestos).toBe(true)
})
it('does not give employee report access the ability to change period controls', async () => {
  render(<AccountingPage canManage={false} />)
  await screen.findByText(/Informe mensual para el contador/)
  expect(
    (
      screen.getByRole('checkbox', {
        name: 'Activar control de períodos mensuales',
      }) as HTMLInputElement
    ).disabled,
  ).toBe(true)
  expect(screen.queryByRole('button', { name: 'Guardar opciones' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Cerrar mes' })).toBeNull()
})
it('opens closing confirmation with Cancel selected and Enter performs no write', async () => {
  config.control_periodos = true
  state = 'ABIERTO'
  render(<AccountingPage canManage />)
  await screen.findByText(/Informe mensual para el contador/)
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: 'Cerrar mes' }))
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Cancelar' }))
  await user.keyboard('{Enter}')
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(vi.mocked(apiFetch).mock.calls.some(([, options]) => options?.method === 'POST')).toBe(
    false,
  )
})
