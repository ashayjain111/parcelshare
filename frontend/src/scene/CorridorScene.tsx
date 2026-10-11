import { Sparkles } from '@react-three/drei'
import { Canvas, useFrame } from '@react-three/fiber'
import { useEffect, useMemo, useRef } from 'react'
import type { MutableRefObject, RefObject } from 'react'
import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js'
import { seeded } from './env'
import { Dust, GLOW_CYAN, GLOW_INDIGO, Nebula, ParcelModel, Studio, motion } from './parts'

/**
 * Scroll-driven fly-through. One parcel travels a curve through four stages that match the story on the page:
 * a city network (matching), a QR handover gate, a hash-chain of linked blocks (chain of custody), and an arrival
 * burst (delivery and payment). The camera trails the parcel. Everything is generated in code: no models, no textures.
 */

const PATH = new THREE.CatmullRomCurve3(
  [
    new THREE.Vector3(0, 0, 0),
    new THREE.Vector3(0.6, 0.3, -7),
    new THREE.Vector3(-3.5, 1.2, -13),
    new THREE.Vector3(3.5, 0.8, -18),
    new THREE.Vector3(1.2, 0, -24),
    new THREE.Vector3(0, 0, -31),
    new THREE.Vector3(0, 0, -40),
    new THREE.Vector3(0, 0.2, -47),
  ],
  false,
  'catmullrom',
  0.4,
)

const clamp = (v: number, a = 0, b = 1) => Math.min(b, Math.max(a, v))
const sstep = (a: number, b: number, x: number) => {
  const t = clamp((x - a) / (b - a))
  return t * t * (3 - 2 * t)
}
const windowAt = (t: number, centre: number, half: number, soft: number) => 1 - sstep(half, half + soft, Math.abs(t - centre))

const mouse = { x: 0, y: 0 }

/** Fades every material under `root` by factor f (remembering each material's original opacity). */
function setFade(root: THREE.Object3D | null, f: number) {
  if (!root) return
  root.visible = f > 0.01
  if (!root.visible) return
  root.traverse((o) => {
    const m = (o as THREE.Mesh).material as THREE.Material | THREE.Material[] | undefined
    if (!m) return
    for (const mat of Array.isArray(m) ? m : [m]) {
      if (mat.userData.base === undefined) mat.userData.base = mat.opacity
      mat.transparent = true
      mat.opacity = (mat.userData.base as number) * f
    }
  })
}

// ------------------------------------------------------------------ stage 1: city network

const NODES: [number, number, number][] = [
  [-3.5, 1.2, -13], [3.5, 0.8, -18], // the two anchors the parcel flies between
  [-8, 2.5, -11], [-6, -2.5, -15], [-9, 0, -19], [-2, 3.4, -17], [0.5, -2.8, -12], [2.5, 2.8, -11.5],
  [6.5, -1.5, -14.5], [8.8, 1.8, -17], [5, -3, -20], [-4, -0.5, -21], [0, 0.5, -20.5], [-7, 3, -22], [8, -3, -9.5], [3, 0.2, -9],
]

function buildArcs() {
  const seen = new Set<string>()
  const arcs: THREE.QuadraticBezierCurve3[] = []
  const add = (i: number, j: number) => {
    const key = i < j ? `${i}-${j}` : `${j}-${i}`
    if (seen.has(key)) return
    seen.add(key)
    const a = new THREE.Vector3(...NODES[i])
    const b = new THREE.Vector3(...NODES[j])
    const mid = a.clone().add(b).multiplyScalar(0.5)
    mid.y += a.distanceTo(b) * 0.28
    arcs.push(new THREE.QuadraticBezierCurve3(a, mid, b))
  }
  add(0, 1)
  NODES.forEach((n, i) => {
    NODES.map((m, j) => ({ j, d: Math.hypot(n[0] - m[0], n[1] - m[1], n[2] - m[2]) }))
      .filter((x) => x.j !== i)
      .sort((p, q) => p.d - q.d)
      .slice(0, 2)
      .forEach((x) => add(i, x.j))
  })
  return arcs
}

function NetworkStage({ groupRef }: { groupRef: RefObject<THREE.Group | null> }) {
  const arcs = useMemo(() => buildArcs(), [])
  const lines = useMemo(
    () =>
      arcs.map((c, i) => {
        const g = new THREE.BufferGeometry().setFromPoints(c.getPoints(56))
        const m = new THREE.LineBasicMaterial({
          color: i === 0 ? GLOW_CYAN : GLOW_INDIGO,
          transparent: true,
          opacity: i === 0 ? 0.95 : 0.5,
          toneMapped: false,
        })
        return new THREE.Line(g, m)
      }),
    [arcs],
  )
  const pulses = useRef<(THREE.Mesh | null)[]>([])

  useFrame((s) => {
    const t = s.clock.elapsedTime * (0.2 + 0.8 * motion.amb)
    arcs.forEach((c, i) => {
      const p = pulses.current[i]
      if (p) p.position.copy(c.getPoint((t * (0.12 + (i % 5) * 0.03) + i * 0.37) % 1))
    })
  })

  return (
    <group ref={groupRef}>
      {lines.map((l, i) => <primitive key={i} object={l} />)}
      {arcs.map((_, i) => (
        <mesh key={i} ref={(el) => { pulses.current[i] = el }}>
          <sphereGeometry args={[0.07, 10, 10]} />
          <meshBasicMaterial color="#fb7185" toneMapped={false} transparent />
        </mesh>
      ))}
      {NODES.map((n, i) => (
        <group key={i} position={n} scale={i < 2 ? 1.9 : 1}>
          <mesh>
            <sphereGeometry args={[0.11, 16, 16]} />
            <meshBasicMaterial color={i < 2 ? GLOW_CYAN : GLOW_INDIGO} toneMapped={false} transparent />
          </mesh>
          <mesh rotation-x={Math.PI / 2}>
            <ringGeometry args={[0.2, 0.225, 40]} />
            <meshBasicMaterial color={GLOW_INDIGO} toneMapped={false} transparent opacity={0.7} side={THREE.DoubleSide} />
          </mesh>
        </group>
      ))}
    </group>
  )
}

// ------------------------------------------------------------------ stage 2: QR handover gate

function seededRandom(seed: number) {
  let s = seed
  return () => {
    s = (s * 1664525 + 1013904223) % 4294967296
    return s / 4294967296
  }
}

function HandoverStage({ groupRef }: { groupRef: RefObject<THREE.Group | null> }) {
  const ring0 = useRef<THREE.Mesh>(null)
  const ring1 = useRef<THREE.Mesh>(null)
  const ring2 = useRef<THREE.Mesh>(null)
  const grid = useRef<THREE.InstancedMesh>(null)
  const beam = useRef<THREE.Mesh>(null)
  const qr = useRef<THREE.Group>(null)

  const cells = useMemo(() => {
    const N = 13
    const rnd = seededRandom(7)
    const out: [number, number][] = []
    const inFinder = (x: number, y: number) => (x < 7 && y < 7) || (x > N - 8 && y < 7) || (x < 7 && y > N - 8)
    const finderOn = (x: number, y: number) => {
      const fx = x < 7 ? x : x - (N - 7)
      const fy = y < 7 ? y : y - (N - 7)
      const edge = fx === 0 || fx === 6 || fy === 0 || fy === 6
      const core = fx >= 2 && fx <= 4 && fy >= 2 && fy <= 4
      return edge || core
    }
    for (let y = 0; y < N; y++) {
      for (let x = 0; x < N; x++) {
        const on = inFinder(x, y) ? finderOn(x, y) : rnd() > 0.52
        if (on) out.push([x - (N - 1) / 2, -(y - (N - 1) / 2)])
      }
    }
    return out
  }, [])

  useEffect(() => {
    const m = grid.current
    if (!m) return
    const d = new THREE.Object3D()
    cells.forEach(([x, y], i) => {
      d.position.set(x * 0.13, y * 0.13, 0)
      d.updateMatrix()
      m.setMatrixAt(i, d.matrix)
    })
    m.instanceMatrix.needsUpdate = true
  }, [cells])

  useFrame((s, dt) => {
    const a = motion.amb
    if (ring0.current) ring0.current.rotation.x += dt * 0.7 * a
    if (ring1.current) ring1.current.rotation.y += dt * 0.55 * a
    if (ring2.current) ring2.current.rotation.z += dt * 0.35 * a
    const t = s.clock.elapsedTime * (0.2 + 0.8 * a)
    if (beam.current) beam.current.position.y = Math.sin(t * 1.7) * 0.85
    if (qr.current) qr.current.position.y = 0.2 + Math.sin(t * 0.8) * 0.06
  })

  return (
    <group ref={groupRef} position={[1.2, 0, -24]}>
      <mesh ref={ring0}><torusGeometry args={[1.9, 0.014, 8, 140]} /><meshBasicMaterial color={GLOW_CYAN} toneMapped={false} transparent /></mesh>
      <mesh ref={ring1}><torusGeometry args={[2.3, 0.012, 8, 140]} /><meshBasicMaterial color={GLOW_INDIGO} toneMapped={false} transparent /></mesh>
      <mesh ref={ring2}><torusGeometry args={[2.75, 0.01, 8, 140]} /><meshBasicMaterial color={GLOW_CYAN} toneMapped={false} transparent opacity={0.6} /></mesh>
      <group ref={qr} position={[0.1, 0.2, -3.6]} scale={1.9}>
        <instancedMesh ref={grid} args={[undefined, undefined, cells.length]}>
          <boxGeometry args={[0.105, 0.105, 0.04]} />
          <meshBasicMaterial color="#1d4ed8" toneMapped={false} transparent opacity={0.5} />
        </instancedMesh>
        <mesh ref={beam} position={[0, 0, 0.06]}>
          <planeGeometry args={[1.5, 0.018]} />
          <meshBasicMaterial color={GLOW_CYAN} toneMapped={false} transparent />
        </mesh>
      </group>
      <pointLight color="#9ccbff" intensity={14} distance={9} />
    </group>
  )
}

// ------------------------------------------------------------------ stage 3: hash chain

const CHAIN_Z = Array.from({ length: 9 }, (_, i) => -28.8 - i * 1.4)

function ChainStage({ groupRef, tRef }: { groupRef: RefObject<THREE.Group | null>; tRef: MutableRefObject<number> }) {
  const geo = useMemo(() => new RoundedBoxGeometry(0.95, 0.5, 0.7, 4, 0.08), [])
  const edgeGeo = useMemo(() => new THREE.EdgesGeometry(geo, 28), [geo])
  const mats = useRef<(THREE.LineBasicMaterial | null)[]>([])
  const dim = useMemo(() => new THREE.Color('#a9bfe6'), [])

  useFrame(() => {
    const pz = PATH.getPoint(tRef.current).z
    CHAIN_Z.forEach((z, i) => {
      const m = mats.current[i]
      if (!m) return
      const reached = pz < z + 0.8
      m.color.lerp(reached ? GLOW_CYAN : dim, 0.12)
    })
  })

  return (
    <group ref={groupRef}>
      {CHAIN_Z.map((z, i) => (
        <group key={i} position={[0, -1.4, z]}>
          <mesh geometry={geo}>
            <meshPhysicalMaterial color="#d3e1fb" metalness={0.35} roughness={0.2} clearcoat={1} envMapIntensity={1.2} transparent />
          </mesh>
          <lineSegments geometry={edgeGeo}>
            <lineBasicMaterial ref={(el) => { mats.current[i] = el }} color={dim} toneMapped={false} transparent />
          </lineSegments>
          {i < CHAIN_Z.length - 1 && (
            <mesh position={[0, 0, -0.7]} rotation-x={Math.PI / 2}>
              <cylinderGeometry args={[0.035, 0.035, 0.7, 8]} />
              <meshBasicMaterial color={GLOW_INDIGO} toneMapped={false} transparent opacity={0.8} />
            </mesh>
          )}
        </group>
      ))}
      <pointLight color="#9ccbff" intensity={10} distance={14} position={[0, 0.5, -34]} />
    </group>
  )
}

// ------------------------------------------------------------------ stage 4: arrival

function ArrivalStage({ groupRef, tRef, count }: { groupRef: RefObject<THREE.Group | null>; tRef: MutableRefObject<number>; count: number }) {
  const ripples = useRef<(THREE.Mesh | null)[]>([])
  const pts = useRef<THREE.Points>(null)
  const { dirs, speeds, positions } = useMemo(() => {
    const d = new Float32Array(count * 3)
    const sp = new Float32Array(count)
    const rnd = seeded(count * 17 + 3)
    for (let i = 0; i < count; i++) {
      const v = new THREE.Vector3(rnd() - 0.5, rnd() - 0.5, rnd() - 0.5).normalize()
      d.set([v.x, v.y, v.z], i * 3)
      sp[i] = 0.4 + rnd() * 0.9
    }
    return { dirs: d, speeds: sp, positions: new Float32Array(count * 3) }
  }, [count])

  useFrame((s) => {
    const time = s.clock.elapsedTime
    ripples.current.forEach((r, i) => {
      if (!r) return
      const k = (time * 0.28 * (0.25 + 0.75 * motion.amb) + i / 4) % 1
      r.scale.setScalar(0.4 + k * 6)
      ;(r.material as THREE.MeshBasicMaterial).opacity = (1 - k) * 0.3
    })
    const burst = sstep(0.93, 1, tRef.current)
    if (pts.current) {
      const attr = pts.current.geometry.getAttribute('position') as THREE.BufferAttribute
      for (let i = 0; i < count; i++) {
        const r = burst * 7 * speeds[i] + Math.sin(time + i) * 0.05
        attr.setXYZ(i, dirs[i * 3] * r, dirs[i * 3 + 1] * r, dirs[i * 3 + 2] * r)
      }
      attr.needsUpdate = true
      ;(pts.current.material as THREE.PointsMaterial).opacity = burst * 0.95
    }
  })

  return (
    <group ref={groupRef} position={[0, 0.2, -47]}>
      {[0, 1, 2, 3].map((i) => (
        <mesh key={i} ref={(el) => { ripples.current[i] = el }} rotation-x={0.95}>
          <ringGeometry args={[0.95, 1, 80]} />
          <meshBasicMaterial color={GLOW_CYAN} toneMapped={false} transparent side={THREE.DoubleSide} />
        </mesh>
      ))}
      <points ref={pts}>
        <bufferGeometry>
          <bufferAttribute attach="attributes-position" args={[positions, 3]} />
        </bufferGeometry>
        <pointsMaterial color="#f59e0b" size={0.09} sizeAttenuation transparent opacity={0} depthWrite={false} toneMapped={false} />
      </points>
      <pointLight color="#9ccbff" intensity={18} distance={12} />
    </group>
  )
}

// ------------------------------------------------------------------ rig: parcel + camera

function Rig({ progress, mobile, calm }: { progress: MutableRefObject<number>; mobile: boolean; calm: boolean }) {
  const tRef = useRef(0)
  const parcel = useRef<THREE.Group>(null)
  const net = useRef<THREE.Group>(null)
  const hand = useRef<THREE.Group>(null)
  const chain = useRef<THREE.Group>(null)
  const arrival = useRef<THREE.Group>(null)
  const intro = useRef(0)
  const camPos = useRef(new THREE.Vector3(0, 0.3, 9))
  const look = useRef(new THREE.Vector3())
  const tmpA = useMemo(() => new THREE.Vector3(), [])
  const tmpB = useMemo(() => new THREE.Vector3(), [])
  const want = useMemo(() => new THREE.Vector3(), [])

  useFrame((state, dt) => {
    const d = Math.min(dt, 0.05)
    intro.current = Math.min(1, intro.current + d / 1.7)
    const target = clamp(progress.current)
    tRef.current += (target - tRef.current) * (1 - Math.exp(-d * 3.4))
    const t = tRef.current

    PATH.getPoint(t, tmpA)
    PATH.getPoint(Math.min(1, t + 0.03), tmpB)

    if (parcel.current) {
      parcel.current.position.copy(tmpA)
      // the parcel leans toward the pointer, like it is curious about you
      const lean = 1 - Math.exp(-d * 4)
      parcel.current.rotation.x += (-mouse.y * 0.2 * motion.amb - parcel.current.rotation.x) * lean
      parcel.current.rotation.y += (mouse.x * 0.3 * motion.amb - parcel.current.rotation.y) * lean
      const e = 1 - Math.pow(1 - intro.current, 3)
      const back = 1 + 1.70158 * Math.pow(intro.current - 1, 3) + 1.70158 * Math.pow(intro.current - 1, 2)
      parcel.current.scale.setScalar(Math.max(0.001, (mobile ? 1.0 : 1.45) * (1 - 0.38 * sstep(0, 0.12, t)) * (e < 1 ? back : 1)))
    }

    const hero = 1 - sstep(0, 0.1, t)
    const heroShift = state.size.width >= 1200 ? 2.1 : 3.0
    const swayX = Math.sin(t * 9) * 0.35 * (1 - hero)
    want.set(
      tmpA.x + swayX + (mobile ? 0 : -heroShift * hero) + mouse.x * 0.7,
      tmpA.y + 0.5 + mouse.y * 0.35 + (mobile ? -0.2 * hero : 0),
      tmpA.z + (mobile ? 13.5 : 6.9) - 2.4 * (1 - intro.current),
    )
    camPos.current.lerp(want, 1 - Math.exp(-d * 2.8))
    state.camera.position.copy(camPos.current)
    look.current.lerp(
      tmpA.clone().lerp(tmpB, 0.18).add(new THREE.Vector3(mobile ? 0 : -heroShift * hero, (mobile ? -2.6 * hero : 0) - (mobile ? 1.2 : 1.9) * sstep(0.93, 1, t), 0)),
      1 - Math.exp(-d * 4),
    )
    state.camera.lookAt(look.current)

    setFade(net.current, windowAt(t, 0.3, 0.13, 0.12))
    setFade(hand.current, windowAt(t, 0.57, 0.09, 0.09))
    setFade(chain.current, windowAt(t, 0.79, 0.1, 0.09))
    setFade(arrival.current, sstep(0.8, 0.92, t))
  })

  return (
    <>
      <group ref={parcel}>
        <ParcelModel />
        <Sparkles count={mobile ? 22 : 48} scale={[3.6, 2.8, 3.6]} size={mobile ? 1.6 : 2.2} speed={calm ? 0.04 : 0.4} opacity={0.95} noise={0.5} color="#3b82f6" />
      </group>
      <NetworkStage groupRef={net} />
      <HandoverStage groupRef={hand} />
      <ChainStage groupRef={chain} tRef={tRef} />
      <ArrivalStage groupRef={arrival} tRef={tRef} count={mobile ? 220 : 420} />
    </>
  )
}

export default function CorridorScene({ progress, mobile, calm }: { progress: MutableRefObject<number>; mobile: boolean; calm: boolean }) {
  useEffect(() => {
    motion.amb = calm ? 0.12 : 1
  }, [calm])

  useEffect(() => {
    const move = (e: PointerEvent) => {
      mouse.x = (e.clientX / window.innerWidth - 0.5) * 2
      mouse.y = -(e.clientY / window.innerHeight - 0.5) * 2
    }
    window.addEventListener('pointermove', move, { passive: true })
    return () => window.removeEventListener('pointermove', move)
  }, [])

  const dust = useMemo<[number, number, number, number, number, number]>(() => [-16, 16, -9, 9, -62, 12], [])

  return (
    <Canvas
      dpr={[1, mobile ? 1.5 : 2]}
      camera={{ fov: 42, position: [0, 0.3, 9], near: 0.1, far: 90 }}
      gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
    >
      <fog attach="fog" args={['#dbe7ff', 18, 58]} />
      <ambientLight intensity={0.9} />
      <directionalLight position={[4, 6, 5]} intensity={1.5} color="#ffffff" />
      <Studio />
      <Nebula
        spots={[
          { pos: [-6, 2, -8], color: '#7db4ff', size: 26, opacity: 0.34 },
          { pos: [8, -2, -22], color: '#5ec9f5', size: 30, opacity: 0.3 },
          { pos: [-5, 3, -36], color: '#93b4ff', size: 28, opacity: 0.3 },
          { pos: [3, 0, -52], color: '#7db4ff', size: 34, opacity: 0.34 },
        ]}
      />
      <Dust count={mobile ? 900 : 2300} box={dust} />
      <Rig progress={progress} mobile={mobile} calm={calm} />
    </Canvas>
  )
}
