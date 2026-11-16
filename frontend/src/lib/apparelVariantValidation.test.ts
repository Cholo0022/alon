import { expect, test } from 'vitest'
import { clearApparelVariantValidation, showFieldErrors, validateForm } from './formValidation'

test.each(['lines', 'variants'])('changing color clears a duplicate size error in %s', (prefix) => {
  const form = document.createElement('form')
  form.innerHTML = `<input name="${prefix}[0].size" value="M"><input name="${prefix}[0].color" value="Rojo">`
  document.body.append(form)
  showFieldErrors(form, { [`${prefix}[0].size`]: 'Talle y color duplicados' })
  expect(validateForm(form)).toBeTruthy()
  const color = form.elements.namedItem(`${prefix}[0].color`) as HTMLInputElement
  color.value = 'Azul'
  clearApparelVariantValidation({ target: color })
  expect(validateForm(form)).toBeNull()
  form.remove()
})
