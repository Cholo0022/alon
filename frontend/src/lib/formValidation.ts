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
    if (field.dataset.validationMessage) {
      field.setCustomValidity('')
      delete field.dataset.validationMessage
    }
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
      for (let parent = first.parentElement; parent; parent = parent.parentElement)
        if (parent instanceof HTMLDetailsElement) parent.open = true
      first.scrollIntoView?.({ block: 'center', behavior: 'smooth' })
      first.focus({ preventScroll: true })
    })
  }
  function input(event: Event) {
    if (isField(event.target)) {
      if (event.target.dataset.validationMessage) event.target.setCustomValidity('')
      if (event.target.validity.valid) clear(event.target)
    }
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

/** Valida y ubica campos sin borrar los datos ingresados. */
type Control = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement
const isControl = (field: unknown): field is Control =>
  field instanceof HTMLInputElement ||
  field instanceof HTMLSelectElement ||
  field instanceof HTMLTextAreaElement
const visible = (field: Control): Control =>
  field instanceof HTMLInputElement && field.type === 'hidden'
    ? (field
        .closest('.searchable-field')
        ?.querySelector<HTMLInputElement>('input[role="combobox"]') ?? field)
    : field
const controls = (form: HTMLFormElement) => Array.from(form.elements).filter(isControl)
const apparelNames: Record<string, string> = Object.fromEntries(
  [
    'size',
    'color',
    'brand',
    'model',
    'description',
    'material',
    'sizeSystem',
    'audience',
    'season',
    'kind',
  ].map((key) => ['apparel.' + key, 'apparel' + key[0].toUpperCase() + key.slice(1)]),
)
function focusInvalid(field: Control) {
  for (let parent = field.parentElement; parent; parent = parent.parentElement)
    if (parent instanceof HTMLDetailsElement) parent.open = true
  window.requestAnimationFrame(() => {
    if (!field.isConnected || field.disabled) return
    field.scrollIntoView?.({ block: 'center', behavior: 'smooth' })
    field.focus({ preventScroll: true })
    if (!field.dataset.validationError) field.reportValidity()
  })
}
function mark(field: Control, message: string) {
  field.setAttribute('aria-invalid', 'true')
  field.dataset.validationMessage = message
  field.setCustomValidity(message)
  field.dispatchEvent(new Event('invalid', { cancelable: true }))
}
export function clearFieldValidation(event: { target: EventTarget | null }) {
  if (!isControl(event.target)) return
  event.target.setCustomValidity('')
  if (event.target.willValidate && !event.target.validity.valid) return
  event.target.classList.remove('field-invalid')
  const errorId = event.target.dataset.validationError
  if (errorId) {
    event.target.ownerDocument.getElementById(errorId)?.remove()
    const ids = (event.target.getAttribute('aria-describedby') ?? '')
      .split(' ')
      .filter((id) => id && id !== errorId)
    if (ids.length) event.target.setAttribute('aria-describedby', ids.join(' '))
    else event.target.removeAttribute('aria-describedby')
    delete event.target.dataset.validationError
  }
  event.target.removeAttribute('aria-invalid')
  delete event.target.dataset.validationMessage
  event.target.setCustomValidity('')
}
export function showFieldErrors(
  form: HTMLFormElement,
  errors: Record<string, string>,
  aliases: Record<string, string> = {},
) {
  const marked: Control[] = []
  for (const [path, message] of Object.entries(errors)) {
    const name = aliases[path] ?? apparelNames[path] ?? path
    const element = form.elements.namedItem(name)
    if (!isControl(element)) continue
    const field = visible(element)
    if (field instanceof HTMLInputElement && field.type === 'hidden') continue
    mark(field, message)
    marked.push(field)
  }
  const first = controls(form).find((field) => marked.includes(field))
  if (first) focusInvalid(first)
  return !!first
}
export function validateForm(form: HTMLFormElement): string | null {
  const invalid = controls(form).filter((field) => field.willValidate && !field.validity.valid)
  for (const field of invalid) {
    const validity = field.validity
    const message = validity.customError
      ? field.validationMessage
      : validity.valueMissing
        ? 'Completá este campo.'
        : validity.rangeUnderflow
          ? `Ingresá un valor mayor o igual a ${field instanceof HTMLInputElement ? field.min : ''}.`
          : validity.rangeOverflow
            ? `Ingresá un valor menor o igual a ${field instanceof HTMLInputElement ? field.max : ''}.`
            : validity.stepMismatch
              ? 'Revisá los decimales; los talles requieren cantidades enteras.'
              : validity.tooLong
                ? 'El texto supera la longitud permitida.'
                : 'Revisá el formato de este campo.'
    mark(field, message)
  }
  const first = invalid[0]
  if (!first) return null
  focusInvalid(first)
  const label =
    first.getAttribute('aria-label') ||
    Array.from(first.labels?.[0]?.childNodes ?? [])
      .filter((node) => node.nodeType === Node.TEXT_NODE)
      .map((node) => node.textContent)
      .join(' ')
      .trim() ||
    first.name
  return `${label}: ${first.validationMessage}`
}
