type Field = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement
function isField(value: EventTarget | null): value is Field {
  return (
    value instanceof HTMLInputElement ||
    value instanceof HTMLSelectElement ||
    value instanceof HTMLTextAreaElement
  )
}

/** Unifica foco, desplazamiento y mensajes de error en todos los formularios. */
export function installFormValidation(root: Document = document) {
  let pending = false
  let nextId = 0
  function clear(field: Field) {
    field.classList.remove('field-invalid')
    field.removeAttribute('aria-invalid')
    const id = field.dataset.validationError
    if (id) {
      root.getElementById(id)?.remove()
      const ids = (field.getAttribute('aria-describedby') ?? '')
        .split(' ')
        .filter((value) => value && value !== id)
      if (ids.length) field.setAttribute('aria-describedby', ids.join(' '))
      else field.removeAttribute('aria-describedby')
      delete field.dataset.validationError
    }
  }
  function invalid(event: Event) {
    const field = event.target
    if (!isField(field)) return
    event.preventDefault()
    field.classList.add('field-invalid')
    field.setAttribute('aria-invalid', 'true')
    let id = field.dataset.validationError
    if (!id) {
      id = `alon-field-error-${++nextId}`
      field.dataset.validationError = id
      field.setAttribute(
        'aria-describedby',
        [field.getAttribute('aria-describedby'), id].filter(Boolean).join(' '),
      )
      const error = root.createElement('span')
      error.id = id
      error.className = 'field-error-message'
      // Se anuncia mediante aria-describedby sin cambiar el nombre del campo.
      error.setAttribute('aria-hidden', 'true')
      field.insertAdjacentElement('afterend', error)
    }
    const message = root.getElementById(id)
    if (message)
      message.textContent = field.validity.valueMissing
        ? 'Completá este campo obligatorio.'
        : field.validationMessage || 'Revisá el valor de este campo.'
    if (pending) return
    pending = true
    queueMicrotask(() => {
      pending = false
      const first =
        Array.from(field.form?.elements ?? []).find(
          (element): element is Field =>
            isField(element) && element.willValidate && !element.validity.valid,
        ) ?? field
      first.scrollIntoView?.({ block: 'center', behavior: 'smooth' })
      first.focus({ preventScroll: true })
    })
  }
  function input(event: Event) {
    if (isField(event.target) && event.target.validity.valid) clear(event.target)
  }
  function reset(event: Event) {
    if (event.target instanceof HTMLFormElement)
      event.target.querySelectorAll<Field>('.field-invalid').forEach(clear)
  }
  root.addEventListener('invalid', invalid, true)
  root.addEventListener('input', input, true)
  root.addEventListener('change', input, true)
  root.addEventListener('reset', reset, true)
  return () => {
    root.removeEventListener('invalid', invalid, true)
    root.removeEventListener('input', input, true)
    root.removeEventListener('change', input, true)
    root.removeEventListener('reset', reset, true)
  }
}
