export type PlatformRate = { negocio_id: number; desde: string; importe: number }

export function monthNumber(value: string) {
  const [year, month] = value.slice(0, 7).split('-').map(Number)
  return year * 12 + month - 1
}

export function rateAt(rates: PlatformRate[], month: number, fallback: number) {
  for (let index = rates.length - 1; index >= 0; index--) {
    if (monthNumber(rates[index].desde) <= month) return Number(rates[index].importe)
  }
  return Number(rates[0]?.importe ?? fallback)
}
