/** Pone en mayúscula la primera letra de cada frase, conservando el resto del texto. */
export function sentenceCase(value: string): string {
  return value.replace(
    /(^|[.!?\n]\s*)([\s“”"'«»¿¡(]*)(\p{L})/gu,
    (_match, start: string, opening: string, letter: string) =>
      start + opening + letter.toLocaleUpperCase('es-AR'),
  )
}

/** Normaliza el texto antes de que React lo reciba, conservando la selección. */
export function installSentenceCase(root: Document = document) {
  const normalize = (event: Event) => {
    const field = event.target
    if (!(field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement)) return
    if (event instanceof InputEvent && event.isComposing) return
    if (field instanceof HTMLInputElement && !['text', 'search'].includes(field.type)) return
    const identifier = [
      field.name,
      field.id,
      field.autocomplete,
      field.getAttribute('aria-label'),
    ].join(' ')
    if (
      /password|contrase|username|usuario|email|correo|url|token|c[oó]digo|code|barcode|cuit|dni/i.test(
        identifier,
      )
    )
      return
    const isSize =
      /(?:^|[.\]])size$|^apparelSize$|^talle$/i.test(field.name) ||
      /^talle(?:\s|$)/i.test(field.getAttribute('aria-label') ?? '')
    const value = isSize ? field.value.toLocaleUpperCase('es-AR') : sentenceCase(field.value)
    if (value === field.value) return
    const start = field.selectionStart,
      end = field.selectionEnd
    const prototype =
      field instanceof HTMLTextAreaElement
        ? HTMLTextAreaElement.prototype
        : HTMLInputElement.prototype
    Object.getOwnPropertyDescriptor(prototype, 'value')?.set?.call(field, value)
    if (start !== null && end !== null) field.setSelectionRange(start, end)
  }
  root.addEventListener('input', normalize, true)
  root.addEventListener('change', normalize, true)
  root.addEventListener('compositionend', normalize, true)
  return () => {
    root.removeEventListener('input', normalize, true)
    root.removeEventListener('change', normalize, true)
    root.removeEventListener('compositionend', normalize, true)
  }
}
