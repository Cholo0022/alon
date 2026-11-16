import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { NewPurchaseApparelForm } from './NewPurchaseApparelForm'
import { apiFetch } from '../lib/http'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
beforeEach(() => {
  vi.mocked(apiFetch)
    .mockReset()
    .mockImplementation(async (url) =>
      Response.json(
        url.startsWith('/api/v1/alon/categories')
          ? [{ id: 12, nombre: 'Remeras' }]
          : { items: [{ id: 1 }] },
      ),
    )
})

test.each([true, false])(
  'requires a category first and uses it for the whole apparel batch (catalog=%s)',
  async (stockMode) => {
    const saved = vi.fn()
    render(
      <NewPurchaseApparelForm
        kind="INDUMENTARIA"
        stockMode={stockMode}
        onSaved={saved}
        onBusyChange={vi.fn()}
      />,
    )
    const name = screen.getByLabelText(
      stockMode ? 'Nombre del artículo' : 'Nombre de la prenda',
    ) as HTMLInputElement
    expect(name.closest('fieldset')!.disabled).toBe(true)
    fireEvent.submit(name.closest('form')!)
    expect(vi.mocked(apiFetch).mock.calls.some(([, options]) => options?.method === 'POST')).toBe(
      false,
    )
    fireEvent.change(screen.getByRole('combobox', { name: 'Rubro' }), { target: { value: 'Rem' } })
    fireEvent.click(await screen.findByRole('option', { name: 'Remeras' }))
    expect(name.closest('fieldset')!.disabled).toBe(false)
    fireEvent.change(name, { target: { value: 'Remera algodón' } })
    fireEvent.change(screen.getByLabelText('Talle 1'), { target: { value: 'M' } })
    fireEvent.submit(name.closest('form')!)
    await waitFor(() => expect(saved).toHaveBeenCalledOnce())
    const request = vi
      .mocked(apiFetch)
      .mock.calls.find(([, options]) => options?.method === 'POST')!
    expect(request[0]).toBe(
      stockMode ? '/api/v1/operations/articles/apparel' : '/api/v1/operations/purchases/apparel',
    )
    expect(JSON.parse(String(request[1]?.body)).product.categoryId).toBe(12)
  },
)

test.each(['INDUMENTARIA', 'CALZADO'])(
  'loads the same size in different colors and groups pricing for %s',
  async (kind) => {
    const saved = vi.fn()
    render(<NewPurchaseApparelForm kind={kind} stockMode onSaved={saved} onBusyChange={vi.fn()} />)
    fireEvent.change(screen.getByRole('combobox', { name: 'Rubro' }), { target: { value: 'Rem' } })
    fireEvent.click(await screen.findByRole('option', { name: 'Remeras' }))
    const pricing = screen.getByRole('group', { name: 'Costo, flete y ganancia' })
    fireEvent.change(within(pricing).getByLabelText('Costo unitario común'), {
      target: { value: '100' },
    })
    fireEvent.change(within(pricing).getByLabelText('Flete %'), { target: { value: '10' } })
    fireEvent.change(within(pricing).getByLabelText('Ganancia %'), { target: { value: '50' } })
    expect((within(pricing).getByLabelText('Precio de venta') as HTMLInputElement).value).toBe(
      '170',
    )
    fireEvent.change(screen.getByLabelText('Nombre del artículo'), {
      target: { value: 'Producto por color' },
    })
    const size = kind === 'CALZADO' ? '38' : 'M'
    fireEvent.click(screen.getByRole('button', { name: size }))
    fireEvent.change(screen.getByLabelText('Color 1'), { target: { value: 'Azul' } })
    fireEvent.change(screen.getByLabelText('Stock talle 1'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: size }))
    expect((screen.getByLabelText('Talle 2') as HTMLInputElement).value).toBe(size)
    fireEvent.change(screen.getByLabelText('Color 2'), { target: { value: 'Azul' } })
    fireEvent.change(screen.getByLabelText('Stock talle 2'), { target: { value: '3' } })
    const form = screen.getByLabelText('Nombre del artículo').closest('form')!
    fireEvent.submit(form)
    expect(vi.mocked(apiFetch).mock.calls.some(([, options]) => options?.method === 'POST')).toBe(
      false,
    )
    fireEvent.change(screen.getByLabelText('Color 2'), { target: { value: 'Negro' } })
    fireEvent.submit(form)
    await waitFor(() => expect(saved).toHaveBeenCalledOnce())
    const command = vi
      .mocked(apiFetch)
      .mock.calls.find(([, options]) => options?.method === 'POST')!
    expect(JSON.parse(String(command[1]?.body)).variants).toMatchObject([
      { size, color: 'Azul', quantity: 2 },
      { size, color: 'Negro', quantity: 3 },
    ])
  },
)
