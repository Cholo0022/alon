/** El formulario web recibe celulares argentinos con área, sin 0 ni 15. */
export function orderWhatsAppUrl(contact: string): string | null {
  const digits = contact.replace(/[^0-9]/g, '')
  const number = /^\d{10}$/.test(digits)
    ? '549' + digits
    : /^54\d{10}$/.test(digits)
      ? '549' + digits.slice(2)
      : /^549\d{10}$/.test(digits)
        ? digits
        : null
  return number ? 'https://wa.me/' + number : null
}
