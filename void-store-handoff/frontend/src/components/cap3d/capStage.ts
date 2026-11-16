// One shared WebGL renderer draws every cap on the page (shop grid, product page, "more from" row) into
// ordinary 2D canvases. That avoids the browser's limit on simultaneous WebGL contexts and keeps the GPU
// cost flat no matter how many caps are on screen. Only views that are visible get drawn.
import * as THREE from 'three'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { buildCap, setInvalidate, type CapBuild, type CapColor } from './capModel'

export interface ViewOptions {
  canvas: HTMLCanvasElement
  color: CapColor
  /** Drag / keyboard to spin. */
  interactive: boolean
  /** Rotation speed in rad/s (0 = still). */
  spin: number
  /** Draw only once (reduced motion + not interactive). */
  still?: boolean
}

export interface CapView {
  setSpin(speed: number): void
  /** Jump to an angle (radians): yaw around the cap, pitch up/down. */
  pose(yaw: number, pitch: number): void
  tilt(): number
  destroy(): void
}

interface InternalView {
  opts: ViewOptions
  ctx: CanvasRenderingContext2D
  build: CapBuild
  root: THREE.Group
  yaw: number
  pitch: number
  pitchTarget: number
  vel: number
  spin: number
  dragging: boolean
  idleFor: number
  visible: boolean
  dirty: boolean
  cssW: number
  cssH: number
}

const PITCHES = [0.24, 1.0, -0.5]
const START_YAW = -0.62

let stage: {
  renderer: THREE.WebGLRenderer
  scene: THREE.Scene
  camera: THREE.PerspectiveCamera
  views: Set<InternalView>
  builds: Map<CapColor, CapBuild>
  raf: number
  last: number
} | null = null

function getStage() {
  if (stage) return stage
  const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'high-performance' })
  renderer.setClearColor(0x000000, 0)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFShadowMap
  renderer.toneMapping = THREE.NeutralToneMapping
  renderer.toneMappingExposure = 1.6
  const scene = new THREE.Scene()
  const pmrem = new THREE.PMREMGenerator(renderer)
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture
  scene.environmentIntensity = 0.75
  pmrem.dispose()

  // soft studio light: a strong key from the upper front-left that casts the brim's shadow onto the crown,
  // a cool back rim to separate the cap from the dark tile, and a gentle sky/ground fill
  const key = new THREE.DirectionalLight(0xfff7ee, 3.1)
  key.position.set(-2.2, 4.6, 3.6)
  key.castShadow = true
  key.shadow.mapSize.set(1024, 1024)
  key.shadow.camera.left = -2.4
  key.shadow.camera.right = 2.4
  key.shadow.camera.top = 2.4
  key.shadow.camera.bottom = -2.4
  key.shadow.camera.near = 1
  key.shadow.camera.far = 14
  key.shadow.bias = -0.0012
  key.shadow.normalBias = 0.06
  key.shadow.radius = 4
  scene.add(key)
  const rim = new THREE.DirectionalLight(0xe6edff, 1.3)
  rim.position.set(3, 2.5, -4)
  scene.add(rim)
  scene.add(new THREE.HemisphereLight(0xffffff, 0x6a6a72, 0.9))
  // soft fill from the viewer's side, like the room light in the owner's photos
  const front = new THREE.DirectionalLight(0xffffff, 0.7)
  front.position.set(0.5, 1.2, 6)
  scene.add(front)

  const camera = new THREE.PerspectiveCamera(26, 1, 0.1, 50)
  stage = { renderer, scene, camera, views: new Set(), builds: new Map(), raf: 0, last: 0 }
  setInvalidate(() => {
    for (const v of stage?.views ?? []) v.dirty = true
    ensureLoop()
  })
  return stage
}

function ensureLoop() {
  const s = stage
  if (!s || s.raf) return
  s.last = performance.now()
  s.raf = requestAnimationFrame(frame)
}

function frame(now: number) {
  const s = stage
  if (!s) return
  s.raf = 0
  const dt = Math.min(0.05, (now - s.last) / 1000)
  s.last = now
  let animating = false
  for (const v of s.views) {
    if (!v.visible) continue
    update(v, dt)
    if (needsFrame(v)) animating = true
    if (v.dirty || needsFrame(v)) draw(s, v)
  }
  if (animating && !document.hidden) s.raf = requestAnimationFrame(frame)
}

const needsFrame = (v: InternalView) =>
  v.dragging || Math.abs(v.vel) > 0.0005 || Math.abs(v.pitchTarget - v.pitch) > 0.001 || (v.spin !== 0 && !v.opts.still)

function update(v: InternalView, dt: number) {
  if (v.dragging) return
  v.idleFor += dt
  if (Math.abs(v.vel) > 0.0005) {
    v.yaw += v.vel * dt
    v.vel *= Math.pow(0.06, dt) // inertia: ~94% lost per second
  } else {
    v.vel = 0
    if (v.spin !== 0 && !v.opts.still && v.idleFor > (v.opts.interactive ? 2.2 : 0)) v.yaw += v.spin * dt
  }
  const d = v.pitchTarget - v.pitch
  if (Math.abs(d) > 0.001) v.pitch += d * Math.min(1, dt * 7)
}

function draw(s: NonNullable<typeof stage>, v: InternalView) {
  v.dirty = false
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const W = Math.max(2, Math.round(v.cssW * dpr))
  const H = Math.max(2, Math.round(v.cssH * dpr))
  const cvs = v.opts.canvas
  if (cvs.width !== W || cvs.height !== H) {
    cvs.width = W
    cvs.height = H
  }
  // The shared drawing buffer only ever grows; each view renders into the lower-left W×H corner of it.
  // (Resizing it for every view would reallocate the buffer several times per frame.)
  const size = s.renderer.getSize(new THREE.Vector2())
  if (W > size.x || H > size.y) s.renderer.setSize(Math.max(W, size.x), Math.max(H, size.y), false)
  s.renderer.setViewport(0, 0, W, H)
  s.renderer.setScissor(0, 0, W, H)
  s.renderer.setScissorTest(true)

  s.camera.aspect = W / H
  const vFov = THREE.MathUtils.degToRad(s.camera.fov)
  const hFov = 2 * Math.atan(Math.tan(vFov / 2) * s.camera.aspect)
  const fit = Math.min(vFov, hFov)
  const dist = (v.build.radius * (v.opts.interactive ? 1.08 : 1.0)) / Math.sin(fit / 2)
  s.camera.position.set(0, 0, dist)
  s.camera.lookAt(0, 0, 0)
  s.camera.near = Math.max(0.1, dist - v.build.radius * 2)
  s.camera.far = dist + v.build.radius * 2
  s.camera.updateProjectionMatrix()

  for (const o of s.views) o.root.visible = o === v
  v.root.rotation.set(v.pitch, v.yaw, 0, 'XYZ')
  s.renderer.render(s.scene, s.camera)
  v.ctx.clearRect(0, 0, W, H)
  v.ctx.drawImage(s.renderer.domElement, 0, s.renderer.domElement.height - H, W, H, 0, 0, W, H)
}

export function createView(opts: ViewOptions): CapView {
  const s = getStage()
  let build = s.builds.get(opts.color)
  if (!build) {
    build = buildCap(opts.color)
    s.builds.set(opts.color, build)
  }
  const ctx = opts.canvas.getContext('2d')
  if (!ctx) throw new Error('no 2d context')

  // Each view gets its own pivot group; geometry and materials are shared through clone().
  const root = new THREE.Group()
  const model = build.group.clone()
  model.position.copy(build.centre).multiplyScalar(-1)
  root.add(model)
  root.visible = false
  s.scene.add(root)

  const v: InternalView = {
    opts,
    ctx,
    build,
    root,
    yaw: START_YAW,
    pitch: PITCHES[0],
    pitchTarget: PITCHES[0],
    vel: 0,
    spin: opts.spin,
    dragging: false,
    idleFor: 0,
    visible: false,
    dirty: true,
    cssW: opts.canvas.clientWidth || 300,
    cssH: opts.canvas.clientHeight || 375,
  }
  s.views.add(v)

  const io = new IntersectionObserver(
    (entries) => {
      v.visible = entries.some((e) => e.isIntersecting)
      if (v.visible) {
        v.dirty = true
        ensureLoop()
      }
    },
    { rootMargin: '120px' },
  )
  io.observe(opts.canvas)
  const ro = new ResizeObserver(() => {
    v.cssW = opts.canvas.clientWidth || v.cssW
    v.cssH = opts.canvas.clientHeight || v.cssH
    v.dirty = true
    ensureLoop()
  })
  ro.observe(opts.canvas)
  const onVis = () => {
    if (!document.hidden) ensureLoop()
  }
  document.addEventListener('visibilitychange', onVis)

  // ---- interaction
  let lastX = 0
  let lastY = 0
  let lastT = 0
  const cleanups: (() => void)[] = []
  if (opts.interactive) {
    const c = opts.canvas
    const down = (e: PointerEvent) => {
      if (e.button !== 0 && e.pointerType === 'mouse') return
      v.dragging = true
      v.vel = 0
      lastX = e.clientX
      lastY = e.clientY
      lastT = e.timeStamp
      c.setPointerCapture(e.pointerId)
      c.classList.add('is-grab')
      ensureLoop()
    }
    const move = (e: PointerEvent) => {
      if (!v.dragging) return
      const dx = e.clientX - lastX
      const dy = e.clientY - lastY
      const dt = Math.max(1, e.timeStamp - lastT) / 1000
      lastX = e.clientX
      lastY = e.clientY
      lastT = e.timeStamp
      const yawDelta = dx * 0.0105
      v.yaw += yawDelta
      v.vel = clampN(yawDelta / dt, -9, 9) * 0.6 + v.vel * 0.4
      // touch keeps vertical swipes for page scrolling (touch-action: pan-y), so only a mouse/pen tilts
      if (e.pointerType !== 'touch') {
        v.pitch = clampN(v.pitch + dy * 0.008, -1.25, 1.4)
        v.pitchTarget = v.pitch
      }
      v.dirty = true
      ensureLoop()
    }
    const up = (e: PointerEvent) => {
      if (!v.dragging) return
      v.dragging = false
      v.idleFor = 0
      c.classList.remove('is-grab')
      if (c.hasPointerCapture(e.pointerId)) c.releasePointerCapture(e.pointerId)
      if (e.timeStamp - lastT > 90) v.vel = 0
      ensureLoop()
    }
    const key = (e: KeyboardEvent) => {
      const step = e.shiftKey ? 0.5 : 0.2
      if (e.key === 'ArrowLeft') v.yaw -= step
      else if (e.key === 'ArrowRight') v.yaw += step
      else if (e.key === 'ArrowUp') v.pitchTarget = clampN(v.pitchTarget - 0.2, -1.25, 1.4)
      else if (e.key === 'ArrowDown') v.pitchTarget = clampN(v.pitchTarget + 0.2, -1.25, 1.4)
      else return
      e.preventDefault()
      v.idleFor = 0
      v.dirty = true
      ensureLoop()
    }
    c.addEventListener('pointerdown', down)
    c.addEventListener('pointermove', move)
    c.addEventListener('pointerup', up)
    c.addEventListener('pointercancel', up)
    c.addEventListener('keydown', key)
    cleanups.push(() => {
      c.removeEventListener('pointerdown', down)
      c.removeEventListener('pointermove', move)
      c.removeEventListener('pointerup', up)
      c.removeEventListener('pointercancel', up)
      c.removeEventListener('keydown', key)
    })
  }

  return {
    setSpin(speed) {
      v.spin = speed
      v.idleFor = 99
      ensureLoop()
    },
    pose(yaw, pitch) {
      v.yaw = yaw
      v.pitch = v.pitchTarget = pitch
      v.vel = 0
      v.dirty = true
      ensureLoop()
    },
    /** Cycle camera height: 3/4 → from above → from below. Returns the new index. */
    tilt() {
      const i = (PITCHES.findIndex((p) => Math.abs(p - v.pitchTarget) < 0.05) + 1) % PITCHES.length
      v.pitchTarget = PITCHES[i]
      v.dirty = true
      ensureLoop()
      return i
    },
    destroy() {
      cleanups.forEach((f) => f())
      io.disconnect()
      ro.disconnect()
      document.removeEventListener('visibilitychange', onVis)
      s.views.delete(v)
      s.scene.remove(root)
    },
  }
}

const clampN = (x: number, a: number, b: number) => Math.min(b, Math.max(a, x))
