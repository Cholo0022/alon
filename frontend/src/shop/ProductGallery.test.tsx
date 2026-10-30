import { expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ProductGallery } from './ProductGallery'

it('shows one photo at a time and cycles through the four photos', async () => {
  const user = userEvent.setup()
  const photos = ['first', 'second', 'third', 'fourth'].map((key) => ({
    key: key + '.png',
    illustrative: false,
  }))
  render(<ProductGallery name="Artículo" photos={photos} cover="first.png" />)
  expect(screen.getAllByRole('img')).toHaveLength(1)
  expect(screen.getByRole('img').getAttribute('src')).toContain('first.png')
  for (const name of ['second', 'third', 'fourth', 'first']) {
    await user.click(screen.getByRole('button', { name: 'Foto siguiente de Artículo' }))
    expect(screen.getAllByRole('img')).toHaveLength(1)
    expect(screen.getByRole('img').getAttribute('src')).toContain(name + '.png')
  }
  await user.click(screen.getByRole('button', { name: 'Foto anterior de Artículo' }))
  expect(screen.getByRole('img').getAttribute('src')).toContain('fourth.png')
})

it('preserves an existing single photo without showing unnecessary arrows', () => {
  render(<ProductGallery name="Artículo" cover="cover.png" />)
  expect(screen.getByRole('img').getAttribute('src')).toContain('cover.png')
  expect(screen.queryByRole('button')).toBeNull()
})
