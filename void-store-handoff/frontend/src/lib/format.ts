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
