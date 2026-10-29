export const currency = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})
export const textValue = (value: unknown) => String(value ?? '')
export const numberValue = (value: unknown) => Number(value ?? 0)
const businessZone = 'America/Argentina/Buenos_Aires'
function timestamp(value: unknown) {
  if (typeof value !== 'number' && !/T.*(?:Z|[+-]\d{2}:\d{2})$/.test(textValue(value))) return null
  const date = new Date(value as string | number)
  return Number.isFinite(date.getTime()) ? date : null
}
export const formatDateDisplay = (value: unknown) => {
  const instant = timestamp(value)
  if (instant)
    return new Intl.DateTimeFormat('es-AR', {
      timeZone: businessZone,
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(instant)
  const raw = textValue(value)
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(raw)
  return match ? `${match[3]}/${match[2]}/${match[1]}` : raw || '—'
}
export const formatDateTimeDisplay = (value: unknown) => {
  const instant = timestamp(value)
  if (instant)
    return (
      formatDateDisplay(value) +
      ' - ' +
      new Intl.DateTimeFormat('es-AR', {
        timeZone: businessZone,
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
      }).format(instant)
    )
  const raw = textValue(value)
  const match = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/.exec(raw)
  return match
    ? `${match[3]}/${match[2]}/${match[1]} - ${match[4]}:${match[5]}`
    : formatDateDisplay(raw)
}
export const formatMonthDisplay = (value: unknown) => {
  const raw = textValue(value)
  const match = /^(\d{4})-(\d{2})$/.exec(raw)
  return match ? `${match[2]}/${match[1]}` : raw || '—'
}
export const todayLocal = () =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Argentina/Buenos_Aires',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())

export const dateDisplayToIso = (value: unknown) => {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(textValue(value).trim())
  if (!match) return null
  const day = Number(match[1]),
    month = Number(match[2]),
    year = Number(match[3])
  const check = new Date(Date.UTC(year, month - 1, day))
  if (
    check.getUTCFullYear() !== year ||
    check.getUTCMonth() !== month - 1 ||
    check.getUTCDate() !== day
  )
    return null
  return `${match[3]}-${match[2]}-${match[1]}`
}
