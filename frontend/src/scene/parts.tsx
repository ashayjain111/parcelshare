import { Environment, Lightformer } from '@react-three/drei'
import { useFrame } from '@react-three/fiber'
import { useMemo, useRef } from 'react'
import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js'
import { seeded } from './env'

/** Ambient animation speed: 1 in full-motion mode, much lower in calm mode. Scroll-driven motion is unaffected. */
export const motion = { amb: 1 }

export const GLOW_CYAN = new THREE.Color('#67e8f9').multiplyScalar(2.4)
export const GLOW_INDIGO = new THREE.Color('#818cf8').multiplyScalar(2.2)

/** Procedural studio lighting: coloured light panels give the metal something to reflect, with no HDR download. */
export function Studio() {
  return (
    <Environment resolution={256} frames={1}>
      <Lightformer form="rect" intensity={3.2} color="#8b9cff" position={[-5, 3, 4]} scale={[6, 3, 1]} />
      <Lightformer form="rect" intensity={3.6} color="#22d3ee" position={[6, 1, -3]} rotation-y={-Math.PI / 2} scale={[8, 3, 1]} />
      <Lightformer form="ring" intensity={2.4} color="#ffffff" position={[0, 6, -2]} rotation-x={Math.PI / 2} scale={4} />
      <Lightformer form="rect" intensity={1.6} color="#c084fc" position={[0, -4, 3]} rotation-x={-Math.PI / 2} scale={[10, 4, 1]} />
    </Environment>
  )
}

/** The hero object: a glossy iridescent parcel with a glowing ribbon, glowing edges and an orbiting halo. */
export function ParcelModel({ spin = 0.32 }: { spin?: number }) {
  const body = useRef<THREE.Group>(null)
  const halo = useRef<THREE.Mesh>(null)
  const geo = useMemo(() => new RoundedBoxGeometry(1.5, 1.1, 1.1, 6, 0.14), [])
  const edges = useMemo(() => new THREE.EdgesGeometry(geo, 28), [geo])
  const label = useMemo(() => {
    // shipping-label texture drawn in code: barcode, parcel id and route
    const c = document.createElement('canvas')
    c.width = 512
    c.height = 320
    const g = c.getContext('2d')!
    g.clearRect(0, 0, 512, 320)
    g.strokeStyle = 'rgba(255,255,255,0.9)'
    g.lineWidth = 6
    g.beginPath()
    g.roundRect(8, 8, 496, 304, 28)
    g.stroke()
    g.fillStyle = '#ffffff'
    g.font = '700 54px ui-sans-serif, system-ui, sans-serif'
    g.fillText('PCL-82931', 36, 86)
    g.font = '600 28px ui-sans-serif, system-ui, sans-serif'
    g.fillStyle = 'rgba(255,255,255,0.75)'
    g.fillText('DELHI  >  LUCKNOW', 38, 134)
    let x = 38
    let seed = 11
    while (x < 470) {
      seed = (seed * 9301 + 49297) % 233280
      const w = 3 + (seed % 3) * 3
      g.fillStyle = '#ffffff'
      g.fillRect(x, 176, w, 108)
      x += w + 4 + (seed % 2) * 3
    }
    const t = new THREE.CanvasTexture(c)
    t.colorSpace = THREE.SRGBColorSpace
    t.anisotropy = 4
    return t
  }, [])

  useFrame((s, dt) => {
    if (body.current) {
      body.current.rotation.y += dt * spin * motion.amb
      body.current.rotation.x = Math.sin(s.clock.elapsedTime * 0.6 * motion.amb) * 0.12 + 0.18
      body.current.position.y = Math.sin(s.clock.elapsedTime * 0.9 * motion.amb) * 0.06
    }
    if (halo.current) {
      halo.current.rotation.z += dt * 0.5 * motion.amb
      halo.current.rotation.x = Math.PI / 2.4
    }
  })

  return (
    <group>
      <group ref={body}>
        <mesh geometry={geo}>
          <meshPhysicalMaterial
            color="#1c2370"
            metalness={0.92}
            roughness={0.24}
            clearcoat={1}
            clearcoatRoughness={0.06}
            iridescence={1}
            iridescenceIOR={1.5}
            iridescenceThicknessRange={[150, 650]}
            envMapIntensity={2.4}
          />
        </mesh>
        <lineSegments geometry={edges} scale={1.002}>
          <lineBasicMaterial color={GLOW_CYAN} toneMapped={false} />
        </lineSegments>
        {/* soft outer glow shell */}
        <mesh geometry={geo} scale={1.07}>
          <meshBasicMaterial color="#38bdf8" transparent opacity={0.07} side={THREE.BackSide} depthWrite={false} blending={THREE.AdditiveBlending} toneMapped={false} />
        </mesh>
        {/* shipping label on the front and back faces */}
        {[1, -1].map((side) => (
          <mesh key={side} position={[0.42, -0.08, 0.557 * side]} rotation-y={side === 1 ? 0 : Math.PI}>
            <planeGeometry args={[0.62, 0.388]} />
            <meshBasicMaterial map={label} transparent color={new THREE.Color(1.5, 1.5, 1.6)} toneMapped={false} />
          </mesh>
        ))}
        {/* ribbon bands, slightly proud of the body */}
        <mesh>
          <boxGeometry args={[0.2, 1.12, 1.12]} />
          <meshStandardMaterial color="#1b1f5e" emissive={GLOW_INDIGO} emissiveIntensity={0.9} toneMapped={false} />
        </mesh>
        <mesh>
          <boxGeometry args={[1.52, 1.12, 0.2]} />
          <meshStandardMaterial color="#1b1f5e" emissive={GLOW_INDIGO} emissiveIntensity={0.55} toneMapped={false} />
        </mesh>
      </group>
      <mesh ref={halo}>
        <torusGeometry args={[1.45, 0.012, 8, 120]} />
        <meshBasicMaterial color={GLOW_CYAN} toneMapped={false} />
      </mesh>
    </group>
  )
}

/** Drifting glow-dust. `box` is the volume it fills; count is kept low on phones. */
export function Dust({ count, box, size = 0.035 }: { count: number; box: [number, number, number, number, number, number]; size?: number }) {
  const ref = useRef<THREE.Points>(null)
  const { positions, colors } = useMemo(() => {
    const p = new Float32Array(count * 3)
    const c = new Float32Array(count * 3)
    const a = new THREE.Color('#818cf8')
    const b = new THREE.Color('#22d3ee')
    const w = new THREE.Color('#ffffff')
    const rnd = seeded(count * 31 + 7)
    for (let i = 0; i < count; i++) {
      p[i * 3] = box[0] + rnd() * (box[1] - box[0])
      p[i * 3 + 1] = box[2] + rnd() * (box[3] - box[2])
      p[i * 3 + 2] = box[4] + rnd() * (box[5] - box[4])
      const col = rnd() < 0.15 ? w : rnd() < 0.5 ? a : b
      c[i * 3] = col.r
      c[i * 3 + 1] = col.g
      c[i * 3 + 2] = col.b
    }
    return { positions: p, colors: c }
  }, [count, box])

  useFrame((_, dt) => {
    if (ref.current) ref.current.rotation.y += dt * 0.012 * motion.amb
  })

  return (
    <points ref={ref}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" args={[positions, 3]} />
        <bufferAttribute attach="attributes-color" args={[colors, 3]} />
      </bufferGeometry>
      <pointsMaterial size={size} sizeAttenuation vertexColors transparent opacity={0.85} depthWrite={false} blending={THREE.AdditiveBlending} />
    </points>
  )
}

/** Soft coloured light washes behind everything: gives the dark scene depth and colour like a nebula. */
export function Nebula({ spots }: { spots: { pos: [number, number, number]; color: string; size: number; opacity: number }[] }) {
  const tex = useMemo(() => {
    const c = document.createElement('canvas')
    c.width = c.height = 256
    const g = c.getContext('2d')!
    const grad = g.createRadialGradient(128, 128, 0, 128, 128, 128)
    grad.addColorStop(0, 'rgba(255,255,255,1)')
    grad.addColorStop(0.35, 'rgba(255,255,255,0.35)')
    grad.addColorStop(1, 'rgba(255,255,255,0)')
    g.fillStyle = grad
    g.fillRect(0, 0, 256, 256)
    const t = new THREE.CanvasTexture(c)
    t.colorSpace = THREE.SRGBColorSpace
    return t
  }, [])
  return (
    <>
      {spots.map((s, i) => (
        <sprite key={i} position={s.pos} scale={[s.size, s.size, 1]}>
          <spriteMaterial map={tex} color={s.color} transparent opacity={s.opacity} depthWrite={false} blending={THREE.AdditiveBlending} fog={false} />
        </sprite>
      ))}
    </>
  )
}
