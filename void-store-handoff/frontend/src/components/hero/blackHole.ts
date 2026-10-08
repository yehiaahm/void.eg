// Canvas starfield + accretion particles, ported from the `class Component` script in
// design/store-en.reference.html (sizeCanvas / measure / seed / spawn / loop / draw).

const PARTICLES = 420
const TAU = 6.283
// Disk plane: tilted 13.5°, flattened to 0.24.
const CA = 0.97237
const SA = 0.23345
const KK = 0.24

interface Particle {
  r: number
  a: number
  k: number
  w: number
  h: number
  px: number
  py: number
  on: boolean
}
interface Star {
  x: number
  y: number
  s: number
  p: number
  f: number
}

const clamp = (v: number, a: number, b: number) => (v < a ? a : v > b ? b : v)
const mq = (q: string) => {
  try {
    return window.matchMedia(q).matches
  } catch {
    return false
  }
}

export class BlackHole {
  private ctx: CanvasRenderingContext2D | null = null
  private parts: Particle[] = []
  private stars: Star[] = []
  private cw = 0
  private ch = 0
  private dpr = 1
  private R = 0
  private cx0 = 0
  private cy0 = 0
  private frame = 0
  private last = 0
  private t0 = performance.now()
  private raf = 0
  private running = false
  private needSize = true
  private small = mq('(max-width: 900px)')
  private reduce = mq('(prefers-reduced-motion: reduce)')
  private visible = true
  /** 1 → 0: a surge of infall after something is dropped into the hole (see pulse()). */
  private surge = 0
  private io: IntersectionObserver | null = null
  private ro: ResizeObserver | null = null

  private canvas: HTMLCanvasElement
  private core: HTMLElement

  constructor(hero: HTMLElement, canvas: HTMLCanvasElement, core: HTMLElement) {
    this.canvas = canvas
    this.core = core
    this.onVis = this.onVis.bind(this)
    this.loop = this.loop.bind(this)

    this.ro = new ResizeObserver(() => {
      this.small = mq('(max-width: 900px)')
      this.needSize = true
    })
    this.ro.observe(hero)

    // Pause entirely when the hero is off-screen.
    this.io = new IntersectionObserver(([e]) => {
      this.visible = e.isIntersecting
      this.sync()
    })
    this.io.observe(hero)

    document.addEventListener('visibilitychange', this.onVis)
    document.fonts?.ready.then(() => this.measure())
    this.sync()
  }

  destroy() {
    this.running = false
    cancelAnimationFrame(this.raf)
    this.io?.disconnect()
    this.ro?.disconnect()
    document.removeEventListener('visibilitychange', this.onVis)
  }

  /** Particles rush into the hole for a moment — the waitlist form "entering the void". */
  pulse() {
    this.surge = 1
  }

  private onVis() {
    this.sync()
  }

  private sync() {
    const should = this.visible && !document.hidden
    if (should && !this.running) {
      this.running = true
      this.last = 0
      this.raf = requestAnimationFrame(this.loop)
    } else if (!should && this.running) {
      this.running = false
      cancelAnimationFrame(this.raf)
    }
  }

  private sizeCanvas() {
    const c = this.canvas
    const r = c.getBoundingClientRect()
    const dpr = Math.min(this.small ? 1.5 : 2, window.devicePixelRatio || 1)
    this.cw = r.width
    this.ch = r.height
    this.dpr = dpr
    c.width = Math.max(1, Math.round(r.width * dpr))
    c.height = Math.max(1, Math.round(r.height * dpr))
    this.ctx = c.getContext('2d')
    this.measure()
    this.seed()
  }

  private measure() {
    const a = this.canvas.getBoundingClientRect()
    const b = this.core.getBoundingClientRect()
    if (Math.abs(a.width - this.cw) > 2 || Math.abs(a.height - this.ch) > 2) {
      this.sizeCanvas()
      return
    }
    this.cx0 = b.left - a.left + b.width / 2
    this.cy0 = b.top - a.top + b.height / 2
    this.R = b.width / 2
  }

  private seed() {
    const n = Math.round(PARTICLES * (this.small ? 0.5 : 1))
    this.parts = []
    for (let i = 0; i < n; i++) this.parts.push(this.spawn(true))
    const ns = Math.round((this.cw * this.ch) / (this.small ? 2600 : 5200))
    this.stars = []
    for (let j = 0; j < ns; j++) {
      this.stars.push({
        x: Math.random(),
        y: Math.random(),
        s: Math.random() < 0.08 ? 1.6 : 0.5 + Math.random() * 0.8,
        p: Math.random() * TAU,
        f: 0.4 + Math.random() * 2.2,
      })
    }
  }

  private spawn(first: boolean): Particle {
    return {
      r: first ? 1.1 + Math.pow(Math.random(), 0.75) * 3.1 : 3.2 + Math.random() * 1.3,
      a: Math.random() * TAU,
      k: 0.55 + Math.random() * 0.9,
      w: 0.5 + Math.random() * 1.1,
      h: Math.random(),
      px: 0,
      py: 0,
      on: false,
    }
  }

  private loop(now: number) {
    if (!this.running) return
    this.raf = requestAnimationFrame(this.loop)
    const dt = this.last ? Math.min(0.05, Math.max(0, (now - this.last) / 1000)) : 0.016
    this.last = now
    this.frame++
    if (this.needSize) {
      this.needSize = false
      this.sizeCanvas()
    }
    if (this.frame % 40 === 2) this.measure()
    this.draw((now - this.t0) / 1000, dt)
  }

  private draw(t: number, dt: number) {
    const ctx = this.ctx
    if (!ctx || !this.R) return
    const W = this.cw
    const H = this.ch
    ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0)
    ctx.globalCompositeOperation = 'destination-out'
    ctx.globalAlpha = 1
    ctx.fillStyle = 'rgba(0,0,0,0.24)'
    ctx.fillRect(0, 0, W, H)
    const R = this.R
    const cx = this.cx0
    const cy = this.cy0
    const hx = cx
    const hy = cy + 0.16 * R
    const hr2 = 0.84 * R * (0.84 * R)

    const si = clamp((t - 0.2) / 1.6, 0, 1)
    ctx.globalCompositeOperation = 'source-over'
    ctx.fillStyle = '#EDEAFF'
    for (const s of this.stars) {
      const x = s.x * W
      const y = s.y * H
      const dx = x - hx
      const dy = y - hy
      if (dx * dx + dy * dy < hr2) continue
      ctx.globalAlpha = si * (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(t * s.f + s.p))) * 0.55
      ctx.fillRect(x, y, s.s, s.s)
    }

    const flow = this.reduce ? 0 : clamp((t - 1.4) / 1.8, 0, 1)
    if (flow <= 0) {
      ctx.globalAlpha = 1
      return
    }
    ctx.globalCompositeOperation = 'lighter'
    ctx.lineCap = 'round'
    const sg = this.surge
    if (sg > 0) this.surge = Math.max(0, sg - dt / 1.8)
    const speed = 1 + sg * sg * 6
    for (const p of this.parts) {
      const ir = 1 / p.r
      p.a += 0.85 * p.w * ir * Math.sqrt(ir) * dt * speed
      p.r -= 0.1 * p.k * Math.sqrt(ir) * dt * speed
      if (p.r < 0.92) {
        Object.assign(p, this.spawn(false))
        continue
      }
      const cs = Math.cos(p.a)
      const sn = Math.sin(p.a)
      const lx = cs * p.r * R
      const ly = sn * p.r * R * KK
      const px = cx + lx * CA - ly * SA
      const py = cy + lx * SA + ly * CA
      const front = sn > 0
      if (!front) {
        const ex = px - hx
        const ey = py - hy
        if (ex * ex + ey * ey < hr2) {
          p.on = false
          continue
        }
      }
      const heat = clamp(1.5 - p.r * 0.33, 0.08, 1)
      let r: number, g: number, b: number
      if (p.h > 0.93) [r, g, b] = [150, 130, 255]
      else if (p.h > 0.86) [r, g, b] = [255, 100, 180]
      else if (cs < 0) [r, g, b] = [255, 150 + 90 * heat, 60 + 150 * heat]
      else [r, g, b] = [190 + 65 * heat, 220 + 35 * heat, 255]
      if (p.on) {
        ctx.globalAlpha = Math.min(1, flow * (0.18 + 0.62 * heat) * (front ? 1 : 0.5) * (1 + sg * 0.9))
        ctx.strokeStyle = `rgb(${r | 0},${g | 0},${b | 0})`
        ctx.lineWidth = (0.5 + p.k * 0.9) * (front ? 1.1 : 0.8)
        ctx.beginPath()
        ctx.moveTo(p.px, p.py)
        ctx.lineTo(px, py)
        ctx.stroke()
      }
      p.px = px
      p.py = py
      p.on = true
    }
    ctx.globalAlpha = 1
  }
}
