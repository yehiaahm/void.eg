// Procedural 3D baseball cap (6-panel, washed "dad hat") built from code — no model file needed.
// The pieces: crown (6 panels with pinched seams), curved brim with stitched rows, fabric button,
// eyelets, embroidered VOID logo, back strap with a metal slide buckle and an inner sweatband.
// Units: crown half-width ≈ 1. The cap sits on y = 0 (rim), front (brim) points to +z.
import * as THREE from 'three'

export type CapColor = 'black' | 'green' | 'yellow'

interface Palette {
  base: string
  light: string
  dark: string
  thread: string
  eyelet: string
  metal: 'brass' | 'silver'
}

const PALETTES: Record<CapColor, Palette> = {
  // base / light / dark follow the fabric in the owner's photos (washed twill, lit vs shadowed areas)
  black: { base: '#5b6060', light: '#7f8484', dark: '#434747', thread: '#1d1f22', eyelet: '#16181b', metal: 'silver' },
  green: { base: '#74a497', light: '#9cc3b8', dark: '#5a887c', thread: '#3a6457', eyelet: '#2b5f4f', metal: 'brass' },
  yellow: { base: '#c8b97c', light: '#dcd0a0', dark: '#a89a5e', thread: '#8f7d45', eyelet: '#86691a', metal: 'brass' },
}

// ---- shape constants
const A = 1.0 // half-width of the crown
const B = 1.12 // half-depth of the crown
const H = 1.04 // dome height factor
const THM = 1.75 // polar angle of the rim (rad)
const RIM_R = Math.sin(THM)
const NOTCH_W = 0.42 // half-width (rad) of the back opening
const NOTCH_D = 0.21 // how far (rad) the crown stops short of the rim there
const SEAMS = 6

const clamp = (x: number, a: number, b: number) => Math.min(b, Math.max(a, x))
const smooth = (e0: number, e1: number, x: number) => {
  const t = clamp((x - e0) / (e1 - e0), 0, 1)
  return t * t * (3 - 2 * t)
}

// ---- crown surface: u = around (0 = front centre), v = top → rim
const notchAt = (phi: number) => {
  const d = Math.abs(Math.atan2(Math.sin(phi - Math.PI), Math.cos(phi - Math.PI)))
  return NOTCH_D * (1 - smooth(NOTCH_W - 0.16, NOTCH_W + 0.04, d))
}
const thetaMax = (phi: number) => THM - notchAt(phi)

// The bottom edge of a real cap is not flat: where the curved brim is sewn on, the crown's edge follows
// the brim's arch down at the sides, then rises again toward the back strap. Crown and brim share this
// curve so the brim grows out of the crown instead of sitting under it like a separate plate.
const RIM_DROP = 0.11
function rimDrop(phi: number) {
  const a = Math.abs(Math.atan2(Math.sin(phi), Math.cos(phi)))
  const s = Math.sin(Math.min(a, Math.PI / 2))
  return RIM_DROP * s * s * (1 - smooth(1.28, 2.1, a))
}

function crownAt(phi: number, theta: number, out = new THREE.Vector3()) {
  const th = theta
  let r = Math.pow(Math.sin(th), 0.9)
  const wave = (Math.cos(SEAMS * phi) + 1) / 2 // 1 at a seam
  // flat-felled seam: a narrow raised ridge with a slight shoulder either side, like the real caps
  const ridge = Math.pow(wave, 60)
  const shoulder = Math.pow(wave, 12) - ridge
  const puff = 1 - Math.pow(wave, 2)
  const fade = smooth(0.03, 0.4, th)
  // soft, slightly uneven fabric (unstructured crown): low-frequency sag
  const sag = (0.009 * Math.sin(2 * phi + 1.3 + th * 2.1) + 0.006 * Math.sin(5 * phi - th * 3.3 + 0.7)) * smooth(0.1, 0.5, th)
  r *= 1 + (0.013 * ridge - 0.01 * shoulder + 0.016 * puff + sag) * fade
  out.x = A * r * Math.sin(phi)
  out.z = B * r * Math.cos(phi)
  out.y = H * (Math.cos(th) - Math.cos(THM)) - rimDrop(phi) * smooth(0.7, THM, th)
  return out
}

type Surf = (u: number, v: number, out: THREE.Vector3) => THREE.Vector3

/** Grid surface with analytic-style normals (finite differences) and a consistent winding. */
function surface(fn: Surf, nu: number, nv: number, uvFn?: (u: number, v: number) => [number, number], wrapU = false): THREE.BufferGeometry {
  const pos: number[] = []
  const nor: number[] = []
  const uvs: number[] = []
  const idx: number[] = []
  const p = new THREE.Vector3()
  const a = new THREE.Vector3()
  const b = new THREE.Vector3()
  const c = new THREE.Vector3()
  const d = new THREE.Vector3()
  const n = new THREE.Vector3()
  const e = 0.0015
  for (let j = 0; j <= nv; j++) {
    for (let i = 0; i <= nu; i++) {
      const u = i / nu
      const v = j / nv
      fn(u, v, p)
      pos.push(p.x, p.y, p.z)
      const v0 = clamp(v, 0.004, 0.996)
      const u0 = wrapU ? u : clamp(u, e, 1 - e)
      fn(u0 + e, v0, a)
      fn(u0 - e, v0, b)
      fn(u0, v0 + e, c)
      fn(u0, v0 - e, d)
      a.sub(b)
      c.sub(d)
      n.crossVectors(a, c).normalize()
      nor.push(n.x, n.y, n.z)
      const [uu, vv] = uvFn ? uvFn(u, v) : [u, v]
      uvs.push(uu, vv)
    }
  }
  const row = nu + 1
  for (let j = 0; j < nv; j++) {
    for (let i = 0; i < nu; i++) {
      const i0 = j * row + i
      idx.push(i0, i0 + row, i0 + 1, i0 + 1, i0 + row, i0 + row + 1)
    }
  }
  // Make the triangle winding agree with the normals (so DoubleSide lights the right face).
  const t0 = new THREE.Vector3(pos[idx[0] * 3], pos[idx[0] * 3 + 1], pos[idx[0] * 3 + 2])
  const t1 = new THREE.Vector3(pos[idx[1] * 3], pos[idx[1] * 3 + 1], pos[idx[1] * 3 + 2])
  const t2 = new THREE.Vector3(pos[idx[2] * 3], pos[idx[2] * 3 + 1], pos[idx[2] * 3 + 2])
  const face = new THREE.Vector3().subVectors(t1, t0).cross(new THREE.Vector3().subVectors(t2, t0))
  const k = idx[0] * 3
  if (face.x * nor[k] + face.y * nor[k + 1] + face.z * nor[k + 2] < 0) {
    for (let i = 0; i < idx.length; i += 3) {
      const t = idx[i + 1]
      idx[i + 1] = idx[i + 2]
      idx[i + 2] = t
    }
  }
  const g = new THREE.BufferGeometry()
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3))
  g.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3))
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2))
  g.setIndex(idx)
  return g
}

// ---- brim (visor) --------------------------------------------------------------------------------
const BRIM_PSI = 1.38 // rim arc covered by the brim (rad each side)
const BRIM_TIP = 2.5 // z of the front edge
const BRIM_END_Z = 0.5 // z where the brim outline leaves the head at the sides
const BRIM_W = 1.12
const BRIM_T = 0.032 // thickness
const BRIM_ALPHA = 1.43

function brimRim(t: number, out: THREE.Vector3) {
  const psi = t * BRIM_PSI
  out.set(A * RIM_R * Math.sin(psi), 0, B * RIM_R * Math.cos(psi))
  return out
}
function brimOutline(t: number, out: THREE.Vector3) {
  const al = t * BRIM_ALPHA
  const rim = brimRim(1, new THREE.Vector3())
  const c0 = Math.cos(BRIM_ALPHA)
  const k = Math.pow(Math.max(0, (Math.cos(al) - c0) / (1 - c0)), 0.78)
  out.set(BRIM_W * Math.sin(al) / Math.sin(BRIM_ALPHA) * 0.985, 0, BRIM_END_Z + (BRIM_TIP - BRIM_END_Z) * k)
  // keep both ends outside the rim ends
  if (Math.abs(t) > 0.999) out.x = Math.sign(t) * Math.max(Math.abs(out.x), rim.x + 0.03)
  return out
}
const tmpR = new THREE.Vector3()
const tmpO = new THREE.Vector3()
function brimAt(s: number, t: number, out: THREE.Vector3) {
  brimRim(t, tmpR)
  brimOutline(t, tmpO)
  // droop grows with the distance from the crown, so the short back corners stay tucked against it
  const d = s * tmpR.distanceTo(tmpO)
  out.lerpVectors(tmpR, tmpO, s)
  const w = out.x / BRIM_W
  out.y = -rimDrop(t * BRIM_PSI) - (0.07 * d + 0.22 * d * d * w * w) + 0.006 * Math.sin(s * Math.PI)
  return out
}

function brimGeometries() {
  const NU = 90
  const NV = 40
  const top = surface((u, v, o) => brimAt(v, u * 2 - 1, o), NU, NV, (u, v) => {
    const p = brimAt(v, u * 2 - 1, new THREE.Vector3())
    return [p.x * 0.5 + 0.5, p.z * 0.5]
  })
  // underside = same surface lowered, reversed
  const under = surface((u, v, o) => {
    brimAt(v, u * 2 - 1, o)
    o.y -= BRIM_T
    return o
  }, NU, NV, (u, v) => {
    const p = brimAt(v, u * 2 - 1, new THREE.Vector3())
    return [p.x * 0.5 + 0.5, p.z * 0.5]
  })
  // flip the underside normals so they point down
  const nn = under.getAttribute('normal') as THREE.BufferAttribute
  for (let i = 0; i < nn.count; i++) nn.setXYZ(i, -nn.getX(i), -nn.getY(i), -nn.getZ(i))
  const ix = under.getIndex()!
  for (let i = 0; i < ix.count; i += 3) {
    const a = ix.getX(i + 1)
    ix.setX(i + 1, ix.getX(i + 2))
    ix.setX(i + 2, a)
  }
  // edge band around the outline and the two short sides
  const loop: THREE.Vector3[] = []
  const p = new THREE.Vector3()
  for (let j = 0; j <= 14; j++) loop.push(brimAt(j / 14, -1, p).clone())
  for (let i = 1; i < 120; i++) loop.push(brimAt(1, (i / 120) * 2 - 1, p).clone())
  for (let j = 14; j >= 0; j--) loop.push(brimAt(j / 14, 1, p).clone())
  // rolled edge: a tube along the outline, a little fatter than the brim is thick
  const mid = loop.map((q) => new THREE.Vector3(q.x, q.y - BRIM_T / 2, q.z))
  const edge = new THREE.TubeGeometry(new THREE.CatmullRomCurve3(mid), 420, BRIM_T * 0.58, 8, false)
  return { top, under, edge }
}

/** Stitch rows on the brim (tubes slightly above both faces). */
function brimStitches(): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = []
  const rows = 4
  const p = new THREE.Vector3()
  for (let k = 0; k < rows; k++) {
    const inset = 0.045 + k * 0.043
    for (const face of [1, -1]) {
      const pts: THREE.Vector3[] = []
      for (let i = 0; i <= 240; i++) {
        const t = (i / 240) * 2 - 1
        brimRim(t, tmpR)
        brimOutline(t, tmpO)
        const len = tmpR.distanceTo(tmpO)
        const s = 1 - inset / len
        if (s < 0.3) continue
        brimAt(s, t, p)
        if (face === 1) p.y += 0.003
        else p.y -= BRIM_T + 0.003
        pts.push(p.clone())
      }
      if (pts.length > 4) out.push(stitchTube(pts, pts.length * 3, 0.0036))
    }
  }
  return out
}

/** A thread along `pts` whose UVs run in stitch lengths, so the dashed alpha map cuts it into stitches. */
const STITCH = 0.036 // one stitch + gap (~3 mm + 1 mm on a real cap)
function stitchTube(pts: THREE.Vector3[], segs: number, radius: number) {
  const curve = new THREE.CatmullRomCurve3(pts)
  const geo = new THREE.TubeGeometry(curve, segs, radius, 5, false)
  const k = curve.getLength() / STITCH
  const uv = geo.getAttribute('uv') as THREE.BufferAttribute
  for (let i = 0; i < uv.count; i++) uv.setX(i, uv.getX(i) * k)
  return geo
}

let dashTex: THREE.Texture | null = null
function dashTexture() {
  if (dashTex) return dashTex
  const cv = document.createElement('canvas')
  cv.width = 64
  cv.height = 4
  const ctx = cv.getContext('2d')!
  ctx.fillStyle = '#000'
  ctx.fillRect(0, 0, 64, 4)
  ctx.fillStyle = '#fff'
  ctx.fillRect(4, 0, 46, 4) // ~72% thread, ~28% gap
  dashTex = new THREE.CanvasTexture(cv)
  dashTex.wrapS = THREE.RepeatWrapping
  dashTex.wrapT = THREE.ClampToEdgeWrapping
  return dashTex
}

// ---- seams, hem, eyelets, strap ----------------------------------------------------------------
function seamStitches(): THREE.BufferGeometry[] {
  const out: THREE.BufferGeometry[] = []
  const p = new THREE.Vector3()
  for (let k = 0; k < SEAMS; k++) {
    const phi0 = (k * Math.PI * 2) / SEAMS
    for (const off of [-0.055, 0.055]) {
      const pts: THREE.Vector3[] = []
      for (let i = 0; i <= 60; i++) {
        const th = 0.13 + (i / 60) * (thetaMax(phi0) - 0.13 - 0.02)
        const phi = phi0 + off * (0.6 + 0.4 * Math.sin(th))
        if (Math.abs(Math.atan2(Math.sin(phi - Math.PI), Math.cos(phi - Math.PI))) < NOTCH_W - 0.05 && th > thetaMax(phi) - 0.03) continue
        crownAt(phi, th, p)
        pts.push(p.clone().addScaledVector(surfaceNormalAt(phi, th), 0.0062))
      }
      if (pts.length > 4) out.push(stitchTube(pts, 240, 0.0032))
    }
  }
  return out
}

function hemGeometry(): THREE.BufferGeometry {
  const pts: THREE.Vector3[] = []
  const N = 360
  for (let i = 0; i < N; i++) {
    const phi = (i / N) * Math.PI * 2
    pts.push(crownAt(phi, thetaMax(phi), new THREE.Vector3()))
  }
  return new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts, true), N * 2, 0.013, 6, true)
}

function surfaceNormalAt(phi: number, theta: number) {
  const e = 0.004
  const a = crownAt(phi + e, theta)
  const b = crownAt(phi - e, theta)
  const c = crownAt(phi, theta + e)
  const d = crownAt(phi, theta - e)
  const n = new THREE.Vector3().crossVectors(a.sub(b), c.sub(d)).normalize()
  const p = crownAt(phi, theta)
  // point away from the crown axis
  if (n.x * p.x + n.z * p.z + n.y * (p.y - 0.5) < 0) n.negate()
  return n
}

function eyeletMeshes(mat: THREE.Material, holeMat: THREE.Material): THREE.Object3D[] {
  const out: THREE.Object3D[] = []
  for (let k = 0; k < SEAMS; k++) {
    const phi = ((k + 0.5) * Math.PI * 2) / SEAMS
    const th = 0.86
    const p = crownAt(phi, th, new THREE.Vector3())
    const n = surfaceNormalAt(phi, th)
    const q = new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 0, 1), n)
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.032, 0.0145, 10, 32), mat)
    ring.position.copy(p).addScaledVector(n, 0.002)
    ring.quaternion.copy(q)
    ring.scale.set(1, 1, 0.55)
    const hole = new THREE.Mesh(new THREE.CircleGeometry(0.019, 24), holeMat)
    hole.position.copy(p).addScaledVector(n, 0.0015)
    hole.quaternion.copy(q)
    out.push(ring, hole)
  }
  return out
}

function strapGeometry(extra: number, from: number, to: number, w0 = 0, w1 = 1) {
  const th0 = THM - 0.215
  const th1 = THM - 0.005
  return surface((u, v, o) => {
    const phi = Math.PI + from + (to - from) * u
    const th = th0 + (th1 - th0) * (w0 + (w1 - w0) * v)
    crownAt(phi, th, o)
    const n = surfaceNormalAt(phi, th)
    return o.addScaledVector(n, extra)
  }, 80, 4, (u, v) => [u * 0.5, v * 0.12])
}

// ---- textures --------------------------------------------------------------------------------------
let fieldCache: { wash: Float32Array; grain: Float32Array; size: number } | null = null

function periodic(size: number, cells: number, seed: number) {
  const lat = new Float32Array(cells * cells)
  let s = seed
  for (let i = 0; i < lat.length; i++) {
    s = (s * 1664525 + 1013904223) >>> 0
    lat[i] = s / 4294967296
  }
  const out = new Float32Array(size * size)
  for (let y = 0; y < size; y++) {
    const fy = (y / size) * cells
    const y0 = Math.floor(fy)
    const ty = fy - y0
    const sy = ty * ty * (3 - 2 * ty)
    for (let x = 0; x < size; x++) {
      const fx = (x / size) * cells
      const x0 = Math.floor(fx)
      const tx = fx - x0
      const sx = tx * tx * (3 - 2 * tx)
      const a = lat[(y0 % cells) * cells + (x0 % cells)]
      const b = lat[(y0 % cells) * cells + ((x0 + 1) % cells)]
      const c = lat[((y0 + 1) % cells) * cells + (x0 % cells)]
      const d = lat[((y0 + 1) % cells) * cells + ((x0 + 1) % cells)]
      out[y * size + x] = (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy
    }
  }
  return out
}

function fields() {
  if (fieldCache) return fieldCache
  const size = 1024
  const n1 = periodic(size, 3, 11)
  const n2 = periodic(size, 7, 23)
  const n3 = periodic(size, 16, 37)
  const g1 = periodic(size, 128, 51)
  const g2 = periodic(size, 256, 67)
  const wash = new Float32Array(size * size)
  const grain = new Float32Array(size * size)
  for (let i = 0; i < wash.length; i++) {
    wash[i] = n1[i] * 0.5 + n2[i] * 0.3 + n3[i] * 0.2
    grain[i] = g1[i] * 0.5 + g2[i] * 0.5
  }
  fieldCache = { wash, grain, size }
  return fieldCache
}

const hexRgb = (h: string): [number, number, number] => {
  const c = new THREE.Color(h)
  return [c.r * 255, c.g * 255, c.b * 255]
}

const texCache = new Map<string, THREE.Texture>()

// The real weave: a tileable patch of the caps' twill photographed by the owner (public/assets/cap/
// weave.png, grey = 128 at the cloth's average), tiled 4× across the 1024px fabric texture. It replaces
// the procedural twill once it has loaded; the colour and the acid-wash patches stay procedural, so the
// photo's colour cast does not leak in and the large patches never repeat.
const WEAVE_URL = '/assets/cap/weave.png'
let weave: Float32Array | null = null
let weaveLoading = false
const weaveCanvases: (() => void)[] = [] // repaint callbacks
const weaveTextures: THREE.Texture[] = [] // textures (and clones) to re-upload after a repaint

function onWeave(repaint: () => void) {
  weaveCanvases.push(repaint)
  if (weaveLoading) return
  weaveLoading = true
  const img = new Image()
  img.onload = () => {
    const n = img.width
    const cv = document.createElement('canvas')
    cv.width = cv.height = n
    const ctx = cv.getContext('2d')!
    ctx.drawImage(img, 0, 0)
    const d = ctx.getImageData(0, 0, n, n).data
    const size = fields().size
    const w = new Float32Array(size * size)
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) w[y * size + x] = d[((y % n) * n + (x % n)) * 4] / 128
    weave = w
    weaveCanvases.forEach((f) => f())
    weaveTextures.forEach((t) => (t.needsUpdate = true))
    invalidate()
  }
  img.src = WEAVE_URL
}

function fabricTexture(color: CapColor): THREE.Texture {
  const key = `col-${color}`
  const hit = texCache.get(key)
  if (hit) return hit
  const size = fields().size
  const cv = document.createElement('canvas')
  cv.width = cv.height = size
  paintFabric(color, cv)
  const tex = new THREE.CanvasTexture(cv)
  tex.colorSpace = THREE.SRGBColorSpace
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping
  tex.anisotropy = 8
  texCache.set(key, tex)
  weaveTextures.push(tex)
  if (!weave) onWeave(() => paintFabric(color, cv))
  return tex
}

function paintFabric(color: CapColor, cv: HTMLCanvasElement) {
  const { wash, grain, size } = fields()
  const pal = PALETTES[color]
  const base = hexRgb(pal.base)
  const light = hexRgb(pal.light)
  const dark = hexRgb(pal.dark)
  const ctx = cv.getContext('2d')!
  const img = ctx.createImageData(size, size)
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = y * size + x
      // acid-wash mottling: stretch the noise so the fabric has both faded and dense patches
      const w = clamp((wash[i] - 0.5) * 1.0 + 0.5, 0, 1)
      let r: number, g: number, b: number
      const WASH = 0.45 // acid-wash is subtle on the real caps: faded patches, not marbling
      if (w < 0.5) {
        const t = (1 - w * 2) * WASH
        r = base[0] + (dark[0] - base[0]) * t
        g = base[1] + (dark[1] - base[1]) * t
        b = base[2] + (dark[2] - base[2]) * t
      } else {
        const t = (w - 0.5) * 2 * WASH
        r = base[0] + (light[0] - base[0]) * t
        g = base[1] + (light[1] - base[1]) * t
        b = base[2] + (light[2] - base[2]) * t
      }
      // weave: the photographed twill (or a procedural stand-in until it has loaded) + a little grain
      let k: number
      if (weave) {
        k = 1 + (weave[i] - 1) * 1.5 + (grain[i] - 0.5) * 0.06
      } else {
        const rib = ((x + y) % 10) / 10
        const tw = (rib < 0.5 ? rib : 1 - rib) * 2 // 0..1 triangle wave
        k = 0.9 + tw * 0.13 + (grain[i] - 0.5) * 0.14
      }
      const o = i * 4
      img.data[o] = clamp(r * k, 0, 255)
      img.data[o + 1] = clamp(g * k, 0, 255)
      img.data[o + 2] = clamp(b * k, 0, 255)
      img.data[o + 3] = 255
    }
  }
  ctx.putImageData(img, 0, 0)
}

function bumpTexture(): THREE.Texture {
  const hit = texCache.get('bump')
  if (hit) return hit
  const size = fields().size
  const cv = document.createElement('canvas')
  cv.width = cv.height = size
  const paint = () => {
    const { grain } = fields()
    const ctx = cv.getContext('2d')!
    const img = ctx.createImageData(size, size)
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        const i = y * size + x
        let v: number
        if (weave) {
          v = clamp(128 + (weave[i] - 1) * 380 + (grain[i] - 0.5) * 20, 0, 255)
        } else {
          const rib = ((x + y) % 10) / 10
          const tw = (rib < 0.5 ? rib : 1 - rib) * 2
          v = clamp(255 * (0.3 + tw * 0.5 + (grain[i] - 0.5) * 0.4), 0, 255)
        }
        const o = i * 4
        img.data[o] = img.data[o + 1] = img.data[o + 2] = v
        img.data[o + 3] = 255
      }
    }
    ctx.putImageData(img, 0, 0)
  }
  paint()
  const tex = new THREE.CanvasTexture(cv)
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping
  tex.anisotropy = 8
  texCache.set('bump', tex)
  weaveTextures.push(tex)
  if (!weave) onWeave(paint)
  return tex
}

// ---- the VOID embroidery -----------------------------------------------------------------------------
// The artwork is the real embroidery, cut out of the owner's photo (public/assets/cap/logo-void.png:
// white thread with alpha). It loads asynchronously; the patch stays hidden until it is there.
const LOGO_URL = '/assets/cap/logo-void.png'
let invalidate: () => void = () => {}
/** The stage registers a callback so views redraw when the logo finishes loading. */
export const setInvalidate = (fn: () => void) => {
  invalidate = fn
}

let logoCache: { map: THREE.Texture; bump: THREE.Texture; ready: boolean; waiters: (() => void)[] } | null = null

function logoTextures() {
  if (logoCache) return logoCache
  const map = new THREE.Texture()
  map.colorSpace = THREE.SRGBColorSpace
  map.anisotropy = 8
  const bump = new THREE.Texture()
  bump.anisotropy = 8
  const state = { map, bump, ready: false, waiters: [] as (() => void)[] }
  logoCache = state
  const img = new Image()
  img.onload = () => {
    map.image = img
    map.needsUpdate = true
    // raised thread: the alpha, blurred, as a height map
    const cv = document.createElement('canvas')
    cv.width = img.width
    cv.height = img.height
    const ctx = cv.getContext('2d')!
    ctx.fillStyle = '#000'
    ctx.fillRect(0, 0, cv.width, cv.height)
    ctx.filter = 'blur(3px)'
    ctx.drawImage(img, 0, 0)
    bump.image = cv
    bump.needsUpdate = true
    state.ready = true
    state.waiters.forEach((f) => f())
    invalidate()
  }
  img.src = LOGO_URL
  return state
}

const LOGO_ASPECT = 1182 / 588 // the cut-out's pixel ratio
const LOGO_THETA_C = 1.24
const LOGO_SIZE = 1.1 // physical width of the embroidery patch (arc length)

function arcLength(f: (t: number, o: THREE.Vector3) => THREE.Vector3, t0: number, t1: number) {
  const p = new THREE.Vector3()
  const q = new THREE.Vector3()
  let len = 0
  const N = 48
  f(t0, p)
  for (let i = 1; i <= N; i++) {
    f(t0 + ((t1 - t0) * i) / N, q)
    len += p.distanceTo(q)
    p.copy(q)
  }
  return len
}

/** Patch of the crown that carries the logo, sized so the letters keep their proportions. */
function logoPatch(): THREE.BufferGeometry {
  let lo = 0.1
  let hi = 1.2
  for (let i = 0; i < 24; i++) {
    const mid = (lo + hi) / 2
    const w = arcLength((t, o) => crownAt(t, LOGO_THETA_C, o), -mid, mid)
    if (w < LOGO_SIZE) lo = mid
    else hi = mid
  }
  const phiHalf = (lo + hi) / 2
  const hTarget = LOGO_SIZE / LOGO_ASPECT
  lo = 0.05
  hi = 1
  for (let i = 0; i < 24; i++) {
    const mid = (lo + hi) / 2
    const h = arcLength((t, o) => crownAt(0, t, o), LOGO_THETA_C - mid, LOGO_THETA_C + mid)
    if (h < hTarget) lo = mid
    else hi = mid
  }
  const dTheta = (lo + hi) / 2
  const off = 0.007
  return surface(
    (u, v, o) => {
      const phi = (u - 0.5) * 2 * phiHalf
      const th = LOGO_THETA_C - dTheta + 2 * dTheta * v
      crownAt(phi, th, o)
      const n = surfaceNormalAt(phi, th)
      return o.addScaledVector(n, off)
    },
    70,
    24,
    (u, v) => [u, 1 - v],
  )
}

// ---- assembly ----------------------------------------------------------------------------------------
interface Shared {
  crown: THREE.BufferGeometry
  brim: ReturnType<typeof brimGeometries>
  brimStitch: THREE.BufferGeometry[]
  seamStitch: THREE.BufferGeometry[]
  hem: THREE.BufferGeometry
  strap: THREE.BufferGeometry
  strapTail: THREE.BufferGeometry
  sweat: THREE.BufferGeometry
  logo: THREE.BufferGeometry
  button: THREE.BufferGeometry
  buckle: THREE.BufferGeometry
  buckleBar: THREE.BufferGeometry
}
let shared: Shared | null = null

function sharedGeometry(): Shared {
  if (shared) return shared
  const crown = surface((u, v, o) => crownAt(u * Math.PI * 2, v * thetaMax(u * Math.PI * 2), o), 480, 96, undefined, true)
  const sweat = surface(
    (u, v, o) => {
      const phi = u * Math.PI * 2
      const th = thetaMax(phi) - 0.34 * (1 - v)
      crownAt(phi, th, o)
      const n = surfaceNormalAt(phi, th)
      return o.addScaledVector(n, -0.011)
    },
    192,
    6,
    undefined,
    true,
  )
  const lathe = new THREE.LatheGeometry(
    [
      new THREE.Vector2(0.0, 0.05),
      new THREE.Vector2(0.04, 0.048),
      new THREE.Vector2(0.074, 0.036),
      new THREE.Vector2(0.094, 0.016),
      new THREE.Vector2(0.098, 0.0),
      new THREE.Vector2(0.0, -0.004),
    ],
    32,
  )
  const buckleShape = new THREE.Shape()
  const bw = 0.075
  const bh = 0.15
  buckleShape.moveTo(-bw, -bh)
  buckleShape.lineTo(bw, -bh)
  buckleShape.lineTo(bw, bh)
  buckleShape.lineTo(-bw, bh)
  buckleShape.closePath()
  const hole = new THREE.Path()
  const hw = 0.05
  const hh = 0.125
  hole.moveTo(-hw, -hh)
  hole.lineTo(-hw, hh)
  hole.lineTo(hw, hh)
  hole.lineTo(hw, -hh)
  hole.closePath()
  buckleShape.holes.push(hole)
  const buckle = new THREE.ExtrudeGeometry(buckleShape, { depth: 0.022, bevelEnabled: true, bevelSize: 0.006, bevelThickness: 0.006, bevelSegments: 2 })
  const buckleBar = new THREE.BoxGeometry(0.1, 0.018, 0.018)
  shared = {
    crown,
    brim: brimGeometries(),
    brimStitch: brimStitches(),
    seamStitch: seamStitches(),
    hem: hemGeometry(),
    strap: strapGeometry(0.004, -0.5, 0.5),
    strapTail: strapGeometry(0.0125, 0.1, 0.34),
    sweat,
    logo: logoPatch(),
    button: lathe,
    buckle,
    buckleBar,
  }
  return shared
}

export interface CapBuild {
  group: THREE.Group
  /** Natural bounding sphere (centre, radius) — used to frame the camera. */
  centre: THREE.Vector3
  radius: number
}

export function buildCap(color: CapColor): CapBuild {
  const g = sharedGeometry()
  const pal = PALETTES[color]
  const light = new THREE.Color(pal.light)

  const fabricTex = fabricTexture(color)
  const bump = bumpTexture()
  const crownTex = fabricTex.clone()
  crownTex.repeat.set(4.2, 1.5)
  crownTex.needsUpdate = true
  const crownBump = bump.clone()
  crownBump.repeat.copy(crownTex.repeat)
  crownBump.needsUpdate = true
  const brimTex = fabricTex.clone()
  brimTex.repeat.set(1.7, 1.7)
  brimTex.needsUpdate = true
  const brimBump = bump.clone()
  brimBump.repeat.copy(brimTex.repeat)
  brimBump.needsUpdate = true
  weaveTextures.push(crownTex, crownBump, brimTex, brimBump)

  const fabric = (map: THREE.Texture, bumpMap: THREE.Texture, tint = 0xffffff) =>
    new THREE.MeshPhysicalMaterial({
      map,
      bumpMap,
      bumpScale: 0.8,
      color: tint,
      roughness: 1,
      metalness: 0,
      envMapIntensity: 0.5,
      sheen: 0.12,
      sheenRoughness: 0.9,
      sheenColor: light,
      side: THREE.DoubleSide,
    })
  const crownMat = fabric(crownTex, crownBump)
  const brimMat = fabric(brimTex, brimBump, 0xc6c6c6)
  const brimUnderMat = fabric(brimTex, brimBump, 0xbdbdbd)
  const thread = new THREE.MeshStandardMaterial({ color: pal.thread, roughness: 1, envMapIntensity: 0.25, alphaMap: dashTexture(), alphaTest: 0.5 })
  const eyeletMat = new THREE.MeshStandardMaterial({ color: pal.eyelet, roughness: 0.65 })
  const holeMat = new THREE.MeshStandardMaterial({ color: 0x050505, roughness: 1 })
  const sweatMat = new THREE.MeshStandardMaterial({ color: color === 'black' ? 0x121315 : 0x1c1d1f, roughness: 0.95, side: THREE.DoubleSide })
  const metal =
    pal.metal === 'brass'
      ? new THREE.MeshStandardMaterial({ color: 0xb08d4a, metalness: 1, roughness: 0.34 })
      : new THREE.MeshStandardMaterial({ color: 0xcfd2d6, metalness: 1, roughness: 0.28 })
  const strapMat = fabric(brimTex, brimBump, 0xf0f0f0)
  strapMat.side = THREE.DoubleSide

  const logo = logoTextures()
  const logoMat = new THREE.MeshStandardMaterial({
    map: logo.map,
    bumpMap: logo.bump,
    bumpScale: 3,
    transparent: true,
    alphaTest: 0.04,
    roughness: 0.55,
    metalness: 0,
    side: THREE.DoubleSide,
    polygonOffset: true,
    polygonOffsetFactor: -2,
    polygonOffsetUnits: -2,
  })

  const cap = new THREE.Group()
  const add = (geo: THREE.BufferGeometry, mat: THREE.Material) => {
    const m = new THREE.Mesh(geo, mat)
    cap.add(m)
    return m
  }
  add(g.crown, crownMat)
  add(g.sweat, sweatMat)
  add(g.brim.top, brimMat)
  add(g.brim.under, brimUnderMat)
  add(g.brim.edge, brimMat)
  for (const s of g.brimStitch) add(s, thread)
  for (const s of g.seamStitch) add(s, thread)
  add(g.hem, crownMat)
  add(g.strap, strapMat)
  add(g.strapTail, strapMat)
  const logoMesh = add(g.logo, logoMat)
  logoMesh.castShadow = false
  logoMat.visible = logo.ready
  if (!logo.ready) logo.waiters.push(() => (logoMat.visible = true))

  const btn = add(g.button, crownMat)
  btn.position.y = H * (1 - Math.cos(THM)) - 0.006
  for (const e of eyeletMeshes(eyeletMat, holeMat)) cap.add(e)

  // buckle on the strap
  const phiB = Math.PI + 0.1
  const thB = THM - 0.11
  const bp = crownAt(phiB, thB, new THREE.Vector3())
  const bn = surfaceNormalAt(phiB, thB)
  const bk = add(g.buckle, metal)
  bk.position.copy(bp).addScaledVector(bn, 0.014)
  // keep the frame upright: align local Y with world up as far as possible
  const up = new THREE.Vector3(0, 1, 0)
  const right = new THREE.Vector3().crossVectors(up, bn).normalize()
  const upFix = new THREE.Vector3().crossVectors(bn, right).normalize()
  bk.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(right, upFix, bn))
  const bb = add(g.buckleBar, metal)
  bb.position.copy(bk.position).addScaledVector(bn, 0.011)
  bb.quaternion.copy(bk.quaternion)

  cap.traverse((o) => {
    if ((o as THREE.Mesh).isMesh && o !== logoMesh) {
      o.castShadow = true
      o.receiveShadow = true
    }
  })
  logoMesh.receiveShadow = true

  // overall size for framing
  cap.updateMatrixWorld(true)
  const box = new THREE.Box3().setFromObject(cap)
  const centre = box.getCenter(new THREE.Vector3())
  // tight radius: the farthest vertex from the centre (the box diagonal would be far too generous)
  let radius = 0
  const v = new THREE.Vector3()
  cap.traverse((o) => {
    const m = o as THREE.Mesh
    const pos = m.isMesh ? m.geometry.getAttribute('position') : null
    if (!pos) return
    for (let i = 0; i < pos.count; i += 2) {
      v.fromBufferAttribute(pos, i).applyMatrix4(m.matrixWorld).sub(centre)
      radius = Math.max(radius, v.length())
    }
  })

  return { group: cap, centre, radius }
}
