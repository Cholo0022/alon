import { expect, it } from 'vitest'
import { sentenceCase } from './sentenceCase'
it('capitalizes sentence starts without changing brand spelling, accents, sizes or decimals', () => {
  expect(
    sentenceCase('remera Nike. algodón 95.5%. ¿cómo lavar? agua fría!\nno secar al sol.'),
  ).toBe('Remera Nike. Algodón 95.5%. ¿Cómo lavar? Agua fría!\nNo secar al sol.')
  expect(sentenceCase('  «calzado urbano»')).toBe('  «Calzado urbano»')
})

import { fireEvent } from '@testing-library/react'
import { installSentenceCase } from './sentenceCase'
it('capitalizes even single letters and updates controlled state while preserving identifiers', () => {
  const uninstall = installSentenceCase()
  const input = document.createElement('input')
  input.name = 'size'
  document.body.append(input)
  fireEvent.input(input, { target: { value: 'm' } })
  expect(input.value).toBe('M')
  for (const name of ['size', 'apparelSize', 'apparel.size', 'lines[0].size', 'variants[0].size']) {
    input.name = name
    fireEvent.input(input, { target: { value: 'xxl' } })
    expect(input.value).toBe('XXL')
  }
  input.name = 'color'
  fireEvent.input(input, { target: { value: 'azul oscuro' } })
  expect(input.value).toBe('Azul oscuro')
  input.name = 'email'
  fireEvent.input(input, { target: { value: 'nombre@correo.com' } })
  expect(input.value).toBe('nombre@correo.com')
  input.remove()
  uninstall()
})
