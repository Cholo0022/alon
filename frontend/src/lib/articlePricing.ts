// Usa enteros decimales y el mismo redondeo HALF_UP que ArticlePricing del servidor.
const decimal = (value: string | number): [bigint, bigint] => {
  const text = String(value || 0)
  if (!/^\d+(?:\.\d*)?$/.test(text)) return [0n, 1n]
  const [whole, fraction = ''] = text.split('.')
  return [BigInt(whole + fraction), 10n ** BigInt(fraction.length)]
}
const halfUp = (numerator: bigint, denominator: bigint) =>
  (numerator * 2n + denominator) / (denominator * 2n)

export function suggestedSalePrice(
  cost: string | number,
  freight: string | number,
  margin: string | number,
  roundUp: boolean,
): number {
  const [c, cs] = decimal(cost),
    [f, fs] = decimal(freight),
    [m, ms] = decimal(margin)
  const freightCents = halfUp(c * f, cs * fs)
  const landedNumerator = c * 100n + freightCents * cs
  const marginMillionths = halfUp(m * 10000n, ms)
  let cents = halfUp(landedNumerator * (1000000n + marginMillionths), cs * 1000000n)
  if (roundUp && cents > 0n) cents = ((cents + 999n) / 1000n) * 1000n
  return Number(cents) / 100
}
