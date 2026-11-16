import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { BusinessStateActions } from './BusinessStateActions'
import { installSentenceCase } from '../lib/sentenceCase'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../lib/http', () => ({ apiFetch: request }))
const business = { id: 7, nombre: 'Las Tres L', estado: 'ACTIVO' }
beforeEach(() => {
  request.mockReset()
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
})

test('offers every category and records an administrative disable with its reason', async () => {
  request.mockResolvedValue(Response.json({ ok: true, state: 'SUSPENDIDO' }))
  const saved = vi.fn()
  render(<BusinessStateActions business={business} onSaved={saved} />)
  for (const name of ['En preparación', 'Prueba', 'Activo', 'Inhabilitado', 'Baja'])
    expect(screen.getByRole('option', { name })).toBeTruthy()
  fireEvent.change(screen.getByLabelText('Estado de Las Tres L'), {
    target: { value: 'SUSPENDIDO' },
  })
  fireEvent.change(screen.getByLabelText('Motivo del cambio de Las Tres L'), {
    target: { value: 'Abono pendiente' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Aplicar estado de Las Tres L' }))
  await screen.findByText('Estado actualizado: Inhabilitado.')
  expect(request.mock.calls[0][0]).toBe('/api/v1/super/businesses/7/state')
  expect(request.mock.calls[0][1]).toMatchObject({ method: 'PUT', retry: false })
  expect(JSON.parse(request.mock.calls[0][1].body)).toEqual({
    state: 'SUSPENDIDO',
    reason: 'Abono pendiente',
  })
  expect(saved).toHaveBeenCalledOnce()
})

test('retains the requested category after an error and never reports success', async () => {
  request.mockResolvedValue(
    Response.json({ error: 'No se pudo aplicar el estado.' }, { status: 409 }),
  )
  const saved = vi.fn()
  render(<BusinessStateActions business={business} onSaved={saved} />)
  fireEvent.change(screen.getByLabelText('Estado de Las Tres L'), { target: { value: 'BAJA' } })
  fireEvent.change(screen.getByLabelText('Para eliminar, escribí el nombre exacto: Las Tres L'), {
    target: { value: business.nombre },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Eliminar definitivamente' }))
  await screen.findByText('No se pudo aplicar el estado.')
  expect(saved).not.toHaveBeenCalled()
  expect((screen.getByLabelText('Estado de Las Tres L') as HTMLSelectElement).value).toBe('BAJA')
})

test('defaults to No and Enter cancels even after typing the business name', () => {
  render(<BusinessStateActions business={business} onSaved={vi.fn()} />)
  fireEvent.change(screen.getByLabelText('Estado de Las Tres L'), { target: { value: 'BAJA' } })
  const review = screen.getByRole('button', { name: 'Revisar baja definitiva de Las Tres L' })
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'No dar de baja' }))
  expect(
    screen.getByRole('button', { name: 'Eliminar definitivamente' }).hasAttribute('disabled'),
  ).toBe(true)
  const input = screen.getByLabelText('Para eliminar, escribí el nombre exacto: Las Tres L')
  fireEvent.change(input, { target: { value: business.nombre } })
  fireEvent.keyDown(input, { key: 'Enter' })
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(request).not.toHaveBeenCalled()
  fireEvent.click(review)
  fireEvent.click(screen.getByRole('button', { name: 'No dar de baja' }))
  expect(request).not.toHaveBeenCalled()
})

test('only deletes on an explicit confirmed click and includes the exact name', async () => {
  request.mockResolvedValue(Response.json({ ok: true, deleted: true }))
  const saved = vi.fn()
  render(<BusinessStateActions business={{ ...business, estado: 'BAJA' }} onSaved={saved} />)
  fireEvent.click(screen.getByRole('button', { name: 'Revisar baja definitiva de Las Tres L' }))
  expect(request).not.toHaveBeenCalled()
  fireEvent.change(screen.getByLabelText('Para eliminar, escribí el nombre exacto: Las Tres L'), {
    target: { value: business.nombre },
  })
  fireEvent.keyDown(screen.getByRole('button', { name: 'Eliminar definitivamente' }), {
    key: 'Enter',
  })
  expect(request).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Revisar baja definitiva de Las Tres L' }))
  fireEvent.change(screen.getByLabelText('Para eliminar, escribí el nombre exacto: Las Tres L'), {
    target: { value: business.nombre },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Eliminar definitivamente' }))
  await screen.findByText(/Negocio eliminado definitivamente/)
  expect(JSON.parse(request.mock.calls[0][1].body)).toEqual({
    state: 'BAJA',
    reason: '',
    confirmation: business.nombre,
  })
  expect(saved).toHaveBeenCalledOnce()
})

test('does not offer reactivation or another deletion after a completed purge', () => {
  render(
    <BusinessStateActions
      business={{ ...business, estado: 'BAJA', eliminado_en: '2026-11-14' }}
      onSaved={vi.fn()}
    />,
  )
  expect(screen.queryByRole('combobox')).toBeNull()
  expect(screen.queryByRole('button')).toBeNull()
})

test('does not claim deletion when an older server only changes the administrative state', async () => {
  request.mockResolvedValue(Response.json({ ok: true, state: 'BAJA' }))
  const saved = vi.fn()
  render(<BusinessStateActions business={business} onSaved={saved} />)
  fireEvent.change(screen.getByLabelText('Estado de Las Tres L'), { target: { value: 'BAJA' } })
  fireEvent.change(screen.getByLabelText('Para eliminar, escribí el nombre exacto: Las Tres L'), {
    target: { value: business.nombre },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Eliminar definitivamente' }))
  await screen.findByText(/El servidor no confirmó la eliminación definitiva/)
  expect(saved).not.toHaveBeenCalled()
})

test('opens the name field immediately on Baja and preserves the exact case with global normalization', () => {
  const uninstall = installSentenceCase()
  try {
    render(
      <BusinessStateActions business={{ ...business, nombre: 'mi Negocio' }} onSaved={vi.fn()} />,
    )
    fireEvent.change(screen.getByLabelText('Estado de mi Negocio'), { target: { value: 'BAJA' } })
    const input = screen.getByRole('textbox', {
      name: 'Para eliminar, escribí el nombre exacto: mi Negocio',
    }) as HTMLInputElement
    fireEvent.input(input, { target: { value: 'mi Negocio' } })
    expect(input.value).toBe('mi Negocio')
    expect(
      screen.getByRole('button', { name: 'Eliminar definitivamente' }).hasAttribute('disabled'),
    ).toBe(false)
    expect(request).not.toHaveBeenCalled()
  } finally {
    uninstall()
  }
})
