// Dev-only sandbox for the 3D cap (route /:lang/cap-lab, not registered in production builds).
// ?c=black|green|yellow&yaw=-0.6&pitch=0.25 (radians) -> one large still view next to the owner's photo
import { CapViewer } from '../components/cap3d/CapViewer'
import type { CapColor } from '../lib/cap'

const COLORS: CapColor[] = ['black', 'green', 'yellow']
const PHOTO: Record<CapColor, string> = { black: 'black', green: 'green', yellow: 'mustard' }

export function CapLab() {
  const q = new URLSearchParams(window.location.search)
  const one = q.get('c') as CapColor | null
  if (one) {
    const yaw = Number(q.get('yaw') ?? -0.6)
    const pitch = Number(q.get('pitch') ?? 0.25)
    const side = q.get('side') ?? 'front'
    return (
      <main className="s-main" style={{ padding: '8px var(--pad)', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        <div style={{ position: 'relative', aspectRatio: '1 / 1' }}>
          <CapViewer
            color={one}
            name="lab"
            interactive
            onView={(v) => {
              v?.setSpin(0)
              v?.pose(yaw, pitch)
            }}
          />
        </div>
        <div style={{ position: 'relative', aspectRatio: '1 / 1', background: '#0b0b0e' }}>
          <img src={`/assets/products/cap-${PHOTO[one]}-${side}-cut.webp`} alt="" style={{ position: 'absolute', inset: '6%', width: '88%', height: '88%', objectFit: 'contain' }} />
        </div>
      </main>
    )
  }
  return (
    <main className="s-main" style={{ padding: '24px var(--pad) 64px' }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 16 }}>
        {COLORS.map((c) => (
          <div key={c} style={{ position: 'relative', aspectRatio: '4 / 5' }}>
            <CapViewer color={c} name={`VOID cap ${c}`} />
          </div>
        ))}
      </div>
    </main>
  )
}
