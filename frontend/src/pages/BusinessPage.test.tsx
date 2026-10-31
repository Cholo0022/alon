import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { BusinessPage } from './BusinessPage'
import { defaultBrand } from '../lib/businessBrand'
import { apiFetch } from '../lib/http'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
beforeEach(() => vi.mocked(apiFetch).mockReset())
it('saves the identity and announces the persisted appearance', async () => {
  const saved = {
    ...defaultBrand,
    nombre: 'Almacén Norte',
    slogan: 'Siempre cerca',
    color_principal: '#aabbcc',
  }
  vi.mocked(apiFetch).mockResolvedValueOnce(Response.json(saved))
  const updated = vi.fn()
  window.addEventListener('alon:business-updated', updated)
  render(<BusinessPage brand={defaultBrand} />)
  fireEvent.change(screen.getByLabelText('Nombre del comercio'), {
    target: { value: saved.nombre },
  })
  fireEvent.change(screen.getByLabelText('Slogan'), { target: { value: saved.slogan } })
  fireEvent.change(screen.getByLabelText('Color principal'), {
    target: { value: saved.color_principal },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar negocio' }))
  await screen.findByText(/Negocio actualizado/)
  expect(JSON.parse(String(vi.mocked(apiFetch).mock.calls[0][1]?.body))).toMatchObject({
    name: saved.nombre,
    slogan: saved.slogan,
    primaryColor: saved.color_principal,
  })
  await waitFor(() => expect(updated).toHaveBeenCalledOnce())
  expect((updated.mock.calls[0][0] as CustomEvent).detail).toEqual(saved)
  window.removeEventListener('alon:business-updated', updated)
})

it('offers ten templates and saves the selected design and customized welcome content only on confirmation', async () => {
  const saved = {
    ...defaultBrand,
    plantilla_tienda: 'mercado',
    titulo_tienda: 'Bienvenidos a mi tienda',
    descripcion_tienda: 'Encontrá lo que necesitás.',
  }
  vi.mocked(apiFetch).mockResolvedValueOnce(Response.json(saved))
  render(<BusinessPage brand={defaultBrand} />)
  expect(screen.getAllByRole('radio')).toHaveLength(10)
  fireEvent.click(screen.getByRole('radio', { name: 'Mercado' }))
  fireEvent.change(screen.getByLabelText('Título de bienvenida de la tienda'), {
    target: { value: saved.titulo_tienda },
  })
  fireEvent.change(screen.getByLabelText('Descripción de bienvenida de la tienda'), {
    target: { value: saved.descripcion_tienda },
  })
  expect(vi.mocked(apiFetch)).not.toHaveBeenCalled()
  expect(
    screen
      .getByRole('img', { name: 'Vista previa de la plantilla de tienda' })
      .classList.contains('shop-template-mercado'),
  ).toBe(true)
  fireEvent.click(screen.getByRole('button', { name: 'Guardar negocio' }))
  await screen.findByText(/Negocio actualizado/)
  expect(JSON.parse(String(vi.mocked(apiFetch).mock.calls[0][1]?.body))).toMatchObject({
    shopTemplate: 'mercado',
    shopTitle: saved.titulo_tienda,
    shopDescription: saved.descripcion_tienda,
    primaryColor: defaultBrand.color_principal,
  })
})
