import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { GuestCheckoutDialog } from './GuestCheckoutDialog'

const send = vi.fn()
beforeEach(() => send.mockReset())
it.each(['344623631', '34462363111', '3446abc311'])(
  'does not send an invalid phone %s',
  (phone) => {
    render(
      <GuestCheckoutDialog
        busy={false}
        error=""
        total={100}
        onClose={() => {}}
        onSend={send}
        initialBuyer={{ firstName: 'Ana', lastName: 'Prueba', phone, dni: '30111222' }}
      />,
    )
    fireEvent.submit(
      screen.getByRole('button', { name: 'Confirmar envío del pedido' }).closest('form')!,
    )
    expect(send).not.toHaveBeenCalled()
    expect(screen.getByRole('alert').textContent).toContain('diez dígitos')
  },
)
it('does not send a DNI containing letters or punctuation', () => {
  render(
    <GuestCheckoutDialog
      busy={false}
      error=""
      total={100}
      onClose={() => {}}
      onSend={send}
      initialBuyer={{
        firstName: 'Ana',
        lastName: 'Prueba',
        phone: '3446236311',
        dni: '30.111.222',
      }}
    />,
  )
  fireEvent.submit(
    screen.getByRole('button', { name: 'Confirmar envío del pedido' }).closest('form')!,
  )
  expect(send).not.toHaveBeenCalled()
})
it('trims contact fields and sends the location defaults', () => {
  render(
    <GuestCheckoutDialog
      busy={false}
      error=""
      total={100}
      onClose={() => {}}
      onSend={send}
      initialBuyer={{
        firstName: ' Ana ',
        lastName: ' Prueba ',
        phone: '3446236311',
        dni: '30111222',
      }}
    />,
  )
  fireEvent.submit(
    screen.getByRole('button', { name: 'Confirmar envío del pedido' }).closest('form')!,
  )
  expect(send).toHaveBeenCalledWith({
    firstName: 'Ana',
    lastName: 'Prueba',
    phone: '3446236311',
    dni: '30111222',
    province: 'Entre Ríos',
    city: 'Larroque',
  })
})
