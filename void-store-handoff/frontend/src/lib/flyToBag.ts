// "Add to bag" animation: a copy of the product picture flies in an arc into the bag icon,
// then the icon pops. Skipped (pop only) for prefers-reduced-motion.

export const BAG_BUMP = 'void:bag-bump'

const bump = () => window.dispatchEvent(new Event(BAG_BUMP))

export function flyToBag(source: Element | null) {
  const target = document.querySelector<HTMLElement>('[data-bag-target]')
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (!source || !target || reduce || typeof document.body.animate !== 'function') {
    bump()
    return
  }
  const from = source.getBoundingClientRect()
  const to = target.getBoundingClientRect()
  if (from.width === 0 || to.width === 0) {
    bump()
    return
  }

  const size = Math.min(from.width, from.height, 220)
  const ghost = document.createElement('div')
  ghost.setAttribute('aria-hidden', 'true')
  Object.assign(ghost.style, {
    position: 'fixed',
    left: `${from.left + from.width / 2 - size / 2}px`,
    top: `${from.top + from.height / 2 - (size * 5) / 8}px`,
    width: `${size}px`,
    height: `${(size * 5) / 4}px`,
    zIndex: '120',
    pointerEvents: 'none',
    background: '#0B0B0E',
    border: '1px solid rgba(242,240,235,.25)',
    overflow: 'hidden',
    willChange: 'transform, opacity',
  })
  const pic = source.querySelector('img, svg')
  if (pic) {
    const clone = pic.cloneNode(true) as HTMLElement
    clone.removeAttribute('class')
    Object.assign(clone.style, { position: 'absolute', inset: '0', width: '100%', height: '100%', objectFit: 'cover', translate: 'none' })
    if (clone.tagName.toLowerCase() === 'svg') Object.assign(clone.style, { inset: '12%', width: '76%', height: '76%' })
    ghost.appendChild(clone)
  }
  document.body.appendChild(ghost)

  const dx = to.left + to.width / 2 - (from.left + from.width / 2)
  const dy = to.top + to.height / 2 - (from.top + from.height / 2)
  const end = 18 / size
  const anim = ghost.animate(
    [
      { transform: 'translate(0,0) scale(1)', opacity: 1, offset: 0 },
      { transform: `translate(${dx * 0.45}px, ${dy * 0.45 - 90}px) scale(${Math.max(end, 0.45)}) rotate(-6deg)`, opacity: 1, offset: 0.45 },
      { transform: `translate(${dx}px, ${dy}px) scale(${end})`, opacity: 0.2, offset: 1 },
    ],
    { duration: 720, easing: 'cubic-bezier(.5,0,.75,.4)', fill: 'forwards' },
  )
  // Finish once — on the animation's end, or after a timeout in case the tab is in the
  // background (animations pause there) so the ghost never gets stuck on screen.
  let done = false
  const finish = () => {
    if (done) return
    done = true
    ghost.remove()
    bump()
  }
  anim.onfinish = finish
  anim.oncancel = finish
  window.setTimeout(finish, 900)
}
