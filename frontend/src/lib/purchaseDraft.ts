import type { ApiRow } from '../hooks/useApiRows'
import { formatDateDisplay, todayLocal } from './format'

export type PurchaseLine = {
  articleId: number
  label: string
  quantity: number
  unitCost: number
  wholeUnits?: boolean
  quantityText?: string
  unitCostText?: string
}
export type PurchaseHeader = { documentType: string; documentNumber: string; date: string }
export type PurchaseDraft = {
  version: 1
  header: PurchaseHeader
  supplier?: ApiRow
  lines: PurchaseLine[]
  extraCosts: string
}
export const emptyPurchaseHeader = (): PurchaseHeader => ({
  documentType: 'REMITO',
  documentNumber: '',
  date: formatDateDisplay(todayLocal()),
})
export const purchaseDraftKey = (scope: string) => 'alon:purchase-draft:v1:' + scope
export function readPurchaseDraft(scope?: string): PurchaseDraft | null {
  if (!scope) return null
  try {
    const value = JSON.parse(
      localStorage.getItem(purchaseDraftKey(scope)) ?? 'null',
    ) as PurchaseDraft | null
    if (
      !value ||
      value.version !== 1 ||
      !value.header ||
      !['FACTURA', 'REMITO'].includes(value.header.documentType) ||
      typeof value.header.documentNumber !== 'string' ||
      typeof value.header.date !== 'string' ||
      typeof value.extraCosts !== 'string' ||
      !Array.isArray(value.lines) ||
      value.lines.length > 500
    )
      return null
    if (
      value.supplier &&
      (!Number.isSafeInteger(Number(value.supplier.id)) || Number(value.supplier.id) <= 0)
    )
      return null
    if (
      value.lines.some(
        (line) =>
          !Number.isSafeInteger(line.articleId) ||
          line.articleId <= 0 ||
          typeof line.label !== 'string' ||
          (line.quantityText !== undefined && typeof line.quantityText !== 'string') ||
          (line.unitCostText !== undefined && typeof line.unitCostText !== 'string') ||
          !Number.isFinite(line.quantity) ||
          !Number.isFinite(line.unitCost),
      )
    )
      return null
    return value
  } catch {
    return null
  }
}
export function validPurchaseLine(line: PurchaseLine) {
  return (
    line.quantityText !== '' &&
    line.unitCostText !== '' &&
    Number.isFinite(line.quantity) &&
    line.quantity > 0 &&
    Math.abs(line.quantity * 1000 - Math.round(line.quantity * 1000)) < 0.000001 &&
    (!line.wholeUnits || Number.isInteger(line.quantity)) &&
    Number.isFinite(line.unitCost) &&
    line.unitCost >= 0
  )
}
