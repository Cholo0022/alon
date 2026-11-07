import type { KeyboardEvent } from 'react'

/** El Enter del lector avanza por los campos; guardar requiere el botón del formulario. */
export function preventImplicitArticleSubmit(event: KeyboardEvent<HTMLFormElement>) {
  if (
    event.defaultPrevented ||
    event.key !== 'Enter' ||
    event.nativeEvent.isComposing ||
    event.ctrlKey ||
    event.metaKey ||
    event.altKey
  )
    return
  const field = event.target
  if (
    !(field instanceof HTMLInputElement) ||
    ['file', 'checkbox', 'radio', 'submit', 'button'].includes(field.type)
  )
    return
  event.preventDefault()
  event.stopPropagation()
  const match = /^variants\[(\d+)\]\.code$/.exec(field.name)
  if (match) {
    const next = event.currentTarget.elements.namedItem(`variants[${Number(match[1]) + 1}].size`)
    if (next instanceof HTMLInputElement) next.focus()
    else event.currentTarget.querySelector<HTMLButtonElement>('[data-add-variant]')?.focus()
    return
  }
  const fields = Array.from(
    event.currentTarget.querySelectorAll<
      HTMLInputElement | HTMLSelectElement | HTMLButtonElement | HTMLTextAreaElement
    >('input:not([type="hidden"]),select,textarea,button'),
  ).filter(
    (element) =>
      !element.disabled && !(element instanceof HTMLButtonElement && element.type === 'submit'),
  )
  fields[fields.indexOf(field) + 1]?.focus()
}
