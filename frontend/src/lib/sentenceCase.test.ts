import { expect, it } from 'vitest'
import { sentenceCase } from './sentenceCase'
it('capitalizes sentence starts without changing brand spelling, accents, sizes or decimals', () => {
  expect(
    sentenceCase('remera Nike. algodón 95.5%. ¿cómo lavar? agua fría!\nno secar al sol.'),
  ).toBe('Remera Nike. Algodón 95.5%. ¿Cómo lavar? Agua fría!\nNo secar al sol.')
  expect(sentenceCase('  «calzado urbano»')).toBe('  «Calzado urbano»')
})
