import type { Lang } from '../api/types'
import { STRINGS } from '../i18n/strings'

const nf = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 })

/** Price in piastres → "1,250 EGP" / "1,250 ج.م". `null` → the [PRICE] placeholder. */
export function formatPrice(piastres: number | null, lang: Lang): string {
  const t = STRINGS[lang]
  const amount = piastres == null ? t.price : nf.format(piastres / 100)
  return `${amount} ${t.currency}`
}

export const pad2 = (n: number) => (n < 10 ? '0' : '') + n

export const dropNo = (n: number) => pad2(n)

/** Delivery time as the owner typed it per governorate ("2-4", "1") → "2-4 days" / "2-4 أيام". */
export function formatEta(eta: string, lang: Lang): string {
  const e = eta.trim()
  const last = Number([...e.matchAll(/\d+/g)].pop()?.[0])
  if (lang === 'en') return `${e} ${e === '1' ? 'day' : 'days'}`
  if (e === '1') return 'يوم واحد'
  if (e === '2') return 'يومين'
  return `${e} ${Number.isFinite(last) && last > 10 ? 'يوم' : 'أيام'}`
}

/** Egyptian / international phone → digits for wa.me ("01012345678" → "201012345678"); null when it doesn't look like one. */
export function waNumber(phone: string | null | undefined): string | null {
  const d = (phone ?? '').replace(/[\s()-]/g, '').replace(/^\+/, '').replace(/^00/, '')
  if (!/^\d{10,15}$/.test(d)) return null
  return d.startsWith('0') ? `20${d.slice(1)}` : d
}

/** Day + time in Cairo time, for order history ("16 Nov, 14:30"). */
export function formatDateTime(iso: string, lang: Lang): string {
  return new Intl.DateTimeFormat(lang === 'ar' ? 'ar-EG-u-nu-latn' : 'en-GB', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Africa/Cairo',
  }).format(new Date(iso))
}
