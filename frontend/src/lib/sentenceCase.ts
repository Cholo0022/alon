/** Pone en mayúscula la primera letra de cada frase, conservando el resto del texto. */
export function sentenceCase(value: string): string {
  return value.replace(
    /(^|[.!?\n]\s*)([\s“”"'«»¿¡(]*)(\p{L})/gu,
    (_match, start: string, opening: string, letter: string) =>
      start + opening + letter.toLocaleUpperCase('es-AR'),
  )
}
