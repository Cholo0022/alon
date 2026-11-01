import { afterEach, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { installFormValidation } from './formValidation'
let dispose: (() => void) | undefined
afterEach(() => {
  dispose?.()
  vi.restoreAllMocks()
})
it('scrolls and focuses the first missing required field, marks errors and clears corrected fields', async () => {
  dispose = installFormValidation()
  const scroll = vi.fn()
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
    configurable: true,
    value: scroll,
  })
  const saved = vi.fn((event: React.FormEvent) => event.preventDefault())
  render(
    <form onSubmit={saved}>
      <label>
        Nombre
        <input name="name" required aria-describedby="help" />
      </label>
      <span id="help">Tu nombre</span>
      <label>
        Email
        <input name="email" type="email" required />
      </label>
      <button>Guardar</button>
    </form>,
  )
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: 'Guardar' }))
  const name = screen.getByRole('textbox', { name: 'Nombre' })
  const email = screen.getByRole('textbox', { name: 'Email' })
  expect(saved).not.toHaveBeenCalled()
  expect(document.activeElement).toBe(name)
  expect(name.getAttribute('aria-invalid')).toBe('true')
  expect(name.classList.contains('field-invalid')).toBe(true)
  expect(scroll).toHaveBeenCalledWith({ block: 'center', behavior: 'smooth' })
  expect(screen.getAllByText('Completá este campo obligatorio.')).toHaveLength(2)
  await user.type(name, 'Ana')
  expect(name.getAttribute('aria-invalid')).toBeNull()
  expect(name.getAttribute('aria-describedby')).toBe('help')
  await user.click(screen.getByRole('button', { name: 'Guardar' }))
  expect(document.activeElement).toBe(email)
  await user.type(email, 'ana@example.com')
  await user.click(screen.getByRole('button', { name: 'Guardar' }))
  expect(saved).toHaveBeenCalledOnce()
  expect(email.getAttribute('aria-invalid')).toBeNull()
})
