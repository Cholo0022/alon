import { useState } from 'react'
import { expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ArticlePhotosField, type ArticlePhoto } from './ArticlePhotosField'

function Editor() {
  const [photos, setPhotos] = useState<ArticlePhoto[]>([])
  return <ArticlePhotosField value={photos} onChange={setPhotos} />
}

it('rejects selecting more than four photos and preserves the current selection', async () => {
  const user = userEvent.setup()
  render(<Editor />)
  const files = Array.from(
    { length: 5 },
    (_, n) => new File(['image'], n + '.png', { type: 'image/png' }),
  )
  await user.upload(screen.getByLabelText('Agregar fotos'), files)
  expect(screen.getByRole('alert').textContent).toContain('hasta cuatro fotos')
  expect(screen.queryByRole('img')).toBeNull()
})

it('allows four photos, changes the main photo and frees a slot when one is removed', async () => {
  const user = userEvent.setup()
  render(<Editor />)
  const chooser = screen.getByLabelText('Agregar fotos')
  await user.upload(
    chooser,
    Array.from(
      { length: 4 },
      (_, n) => new File(['photo-' + n], n + '.png', { type: 'image/png' }),
    ),
  )
  await screen.findByText('Fotos del artículo · 4/4')
  const photos = screen.getAllByRole('img')
  expect(photos).toHaveLength(4)
  const last = photos[3].getAttribute('src')
  expect(chooser.hasAttribute('disabled')).toBe(true)
  await user.click(screen.getAllByRole('button', { name: 'Hacer principal' })[2])
  expect(screen.getAllByRole('img')[0].getAttribute('src')).toBe(last)
  await user.click(screen.getByRole('button', { name: 'Quitar foto 1' }))
  expect(screen.getAllByRole('img')).toHaveLength(3)
  expect(chooser.hasAttribute('disabled')).toBe(false)
})
