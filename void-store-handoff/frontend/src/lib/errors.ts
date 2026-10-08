import { ApiError } from '../api/http'
import type { Strings } from '../i18n/strings'

/** Human message for an API / network error in the current language. */
export function errorMessage(e: unknown, t: Strings): string {
  if (e instanceof ApiError) {
    switch (e.code) {
      case 'bad_credentials':
        return t.badCredentials
      case 'email_taken':
        return t.emailTaken
      case 'invalid_phone':
        return t.invalidPhone
      case 'invalid_token':
        return t.invalidLink
      case 'rate_limited':
        return t.tooMany
      case 'stock_changed':
        return t.fixBag
      case 'shipping_unavailable':
        return t.noZones
      case 'not_found':
        return t.orderNotFound
      case 'store_closed':
        return t.storeClosedError
    }
    if (e.code.startsWith('coupon_')) return t.couponError[e.code.slice(7)] ?? t.couponError.invalid
    return t.genericError
  }
  if (e instanceof TypeError) return t.networkError
  return t.genericError
}

export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/

/** Egyptian mobile: 01[0125] + 8 digits; accepts +20 / 0020 prefixes, spaces and Arabic-Indic digits. */
export function normalizePhone(raw: string): string {
  let d = raw
    .replace(/[\s\-()]/g, '')
    .replace(/[٠-٩]/g, (c) => String(c.charCodeAt(0) - 0x0660))
    .replace(/[۰-۹]/g, (c) => String(c.charCodeAt(0) - 0x06f0))
  if (d.startsWith('+20')) d = '0' + d.slice(3)
  else if (d.startsWith('0020')) d = '0' + d.slice(4)
  else if (d.startsWith('20') && d.length === 12) d = '0' + d.slice(2)
  return d
}
export const isEgMobile = (raw: string) => /^01[0125]\d{8}$/.test(normalizePhone(raw))
