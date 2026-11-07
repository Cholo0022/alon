import { render, screen, fireEvent } from '@testing-library/react'
import { vi, it, expect } from 'vitest'
import { ReportsPage } from './ReportsPage'
vi.mock('../components/ApiTable', () => ({
  ApiTable: ({ endpoint }: { endpoint: string }) => <p data-testid="report-endpoint">{endpoint}</p>,
}))
it('opens with the current Argentine month and applies both selected months', () => {
  Element.prototype.scrollIntoView = vi.fn()
  render(<ReportsPage />)
  const month = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'America/Argentina/Buenos_Aires',
    year: 'numeric',
    month: '2-digit',
  }).format(new Date())
  fireEvent.click(screen.getByRole('button', { name: /Ventas y ganancia bruta por día/ }))
  expect(screen.getByTestId('report-endpoint').textContent).toContain(`from=${month}&to=${month}`)
  fireEvent.change(screen.getByLabelText('Desde mes'), { target: { value: '2025-12' } })
  fireEvent.change(screen.getByLabelText('Hasta mes'), { target: { value: '2026-02' } })
  fireEvent.click(screen.getByRole('button', { name: 'Consultar período' }))
  expect(screen.getByTestId('report-endpoint').textContent).toContain('from=2025-12&to=2026-02')
  fireEvent.click(screen.getByRole('button', { name: 'Medios de pago' }))
  expect(screen.getByTestId('report-endpoint').textContent).toContain(`from=${month}&to=${month}`)
  fireEvent.click(screen.getByRole('button', { name: /Ventas y ganancia bruta por día/ }))
  expect(screen.getByTestId('report-endpoint').textContent).toContain('from=2025-12&to=2026-02')
  expect((screen.getByLabelText('Desde mes') as HTMLInputElement).value).toBe('2025-12')
})
