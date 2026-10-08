import { useState } from 'react'
import { useI18n } from '../i18n'
import { useToast } from '../store/toast'
import { useWishlist } from '../store/wishlist'
import { HeartIcon } from './icons'

/** Heart toggle. Pops when a piece is saved. */
export function WishButton({ slug, name, className = 's-wish', size = 18 }: { slug: string; name: string; className?: string; size?: number }) {
  const { t } = useI18n()
  const wish = useWishlist()
  const flash = useToast()
  const [pop, setPop] = useState(0)
  const saved = wish.has(slug)
  return (
    <button
      type="button"
      className={className}
      aria-pressed={saved}
      aria-label={saved ? t.unsaveItem(name) : t.saveItem(name)}
      title={saved ? t.unsaveItem(name) : t.saveItem(name)}
      onClick={(e) => {
        e.preventDefault()
        e.stopPropagation()
        const added = wish.toggle(slug)
        if (added) setPop((n) => n + 1)
        flash(added ? t.savedToast : t.removedToast)
      }}
    >
      <span key={pop} className={pop ? 's-heartpop' : undefined} style={{ display: 'inline-flex' }}>
        <HeartIcon filled={saved} size={size} />
      </span>
    </button>
  )
}
