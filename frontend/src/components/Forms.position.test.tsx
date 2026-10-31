import { fireEvent, render, screen } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import { LocalEntryPanel } from './Forms'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn(async () => Response.json({})) }))
/** Al abrir Arqueo y cierre se muestra el inicio del formulario sin que el foco desplace la pantalla. */
test('opening a management form scrolls its panel to the top', () => {
  const previous = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView')
  const scroll = vi.fn()
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
    configurable: true,
    value: scroll,
  })
  const frame = vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
    callback(0)
    return 1
  })
  try {
    render(
      <LocalEntryPanel
        title="Cerrar caja"
        action="Arqueo y cierre"
        endpoint="/api/v1/operations/cash/close"
        fields={[{ name: 'date', label: 'Fecha', type: 'date' }]}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: /Arqueo y cierre/ }))
    expect(scroll).toHaveBeenCalledWith({ behavior: 'instant', block: 'start' })
    expect(document.activeElement).toBe(screen.getByRole('textbox'))
  } finally {
    frame.mockRestore()
    if (previous) Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', previous)
    else Reflect.deleteProperty(HTMLElement.prototype, 'scrollIntoView')
  }
})
