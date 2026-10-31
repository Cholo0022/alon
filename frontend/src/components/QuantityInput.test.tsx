import { useState } from 'react'
import { expect, it } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { QuantityInput } from './QuantityInput'

function Controlled() {
  const [value, setValue] = useState('1')
  return (
    <form>
      <QuantityInput
        value={value}
        min="0"
        max="5"
        onChange={(event) => setValue(event.target.value)}
      />
      <output>{value}</output>
    </form>
  )
}
it('changes quantities by one with buttons and keyboard while allowing typed decimals', () => {
  render(<Controlled />)
  const input = screen.getByRole('spinbutton', { name: 'Cantidad' }) as HTMLInputElement
  fireEvent.click(screen.getByRole('button', { name: 'Aumentar cantidad' }))
  expect(input.value).toBe('2')
  expect(screen.getByRole('status').textContent).toBe('2')
  fireEvent.keyDown(input, { key: 'ArrowUp' })
  expect(input.value).toBe('3')
  fireEvent.keyDown(input, { key: 'ArrowDown' })
  expect(input.value).toBe('2')
  fireEvent.change(input, { target: { value: '1.5' } })
  expect(input.value).toBe('1.5')
  expect(input.checkValidity()).toBe(true)
  fireEvent.click(screen.getByRole('button', { name: 'Aumentar cantidad' }))
  expect(input.value).toBe('2.5')
  fireEvent.click(screen.getByRole('button', { name: 'Disminuir cantidad' }))
  expect(input.value).toBe('1.5')
})
it('preserves decimal precision and minimum and maximum limits in form submissions', () => {
  const { container } = render(
    <form>
      <QuantityInput name="quantity" defaultValue="0.5" min="0" max="2.5" required />
    </form>,
  )
  const input = screen.getByRole('spinbutton') as HTMLInputElement
  fireEvent.click(screen.getByRole('button', { name: 'Disminuir cantidad' }))
  expect(input.value).toBe('0')
  fireEvent.change(input, { target: { value: '2' } })
  fireEvent.click(screen.getByRole('button', { name: 'Aumentar cantidad' }))
  expect(input.value).toBe('2.5')
  expect(input.checkValidity()).toBe(true)
  expect(new FormData(container.querySelector('form')!).get('quantity')).toBe('2.5')
  fireEvent.change(input, { target: { value: '1.2345' } })
  expect(input.checkValidity()).toBe(false)
  fireEvent.change(input, { target: { value: '1.234' } })
  expect(input.checkValidity()).toBe(true)
})
it('respects disabled and read-only quantities', () => {
  render(<QuantityInput defaultValue="1" readOnly />)
  const input = screen.getByRole('spinbutton') as HTMLInputElement
  fireEvent.keyDown(input, { key: 'ArrowUp' })
  expect(input.value).toBe('1')
  expect(
    (screen.getByRole('button', { name: 'Aumentar cantidad' }) as HTMLButtonElement).disabled,
  ).toBe(true)
})
