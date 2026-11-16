import type { Product } from '../api/types'
import type { CapColor } from '../components/cap3d/capModel'

export type { CapColor }

// Caps: the shop card shows the clean cut-out photo (front, back on hover); the product page shows the
// 3D cap (components/cap3d) above the real photos. A product counts as a cap when its slug / name /
// category says so; "cap" must stand alone as a word (so "capsule" does not match).
const IS_CAP = /(^|[^a-z])(caps?|hats?)([^a-z]|$)|كاب|كابات|قبعة|طاقية/i
const GREEN = /green|olive|sage|forest|أخضر|اخضر|زيتي|زيتون/i
const YELLOW = /yellow|mustard|gold|أصفر|اصفر|خردل|عسلي/i

export function isCap(p: Product): boolean {
  return IS_CAP.test([p.slug, p.name?.en, p.name?.ar, p.category?.en, p.category?.ar].filter(Boolean).join(' '))
}

/** The 3D colour for a cap product (dark grey = "black"), or `null` when the product is not a cap. */
export function capColorOf(p: Product): CapColor | null {
  if (!isCap(p)) return null
  const colour = [p.slug, p.name?.en, p.name?.ar, p.details?.colour?.en, p.details?.colour?.ar].filter(Boolean).join(' ')
  if (GREEN.test(colour)) return 'green'
  if (YELLOW.test(colour)) return 'yellow'
  return 'black'
}
