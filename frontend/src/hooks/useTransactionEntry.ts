// Mantiene la cabecera a la vista y muestra las últimas filas al cargar artículos.
import { useLayoutEffect, type RefObject } from 'react'

/** Mantiene visible el formulario de compra o venta abierto y desplaza la lista hacia el último artículo agregado. */
export function useTransactionEntry(
  form: RefObject<HTMLFormElement | null>,
  open: boolean,
  version: number,
) {
  useLayoutEffect(() => {
    if (!open || !form.current) return
    form.current.scrollIntoView?.({ block: 'start' })
    const articles = form.current.querySelector<HTMLElement>('.purchase-lines .table-scroll')
    if (articles) articles.scrollTop = articles.scrollHeight
  }, [form, open, version])
}
