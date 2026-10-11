import { Canvas, useFrame } from '@react-three/fiber'
import { useRef } from 'react'
import type * as THREE from 'three'
import { Dust, ParcelModel, Studio } from './parts'

function Spinner() {
  const g = useRef<THREE.Group>(null)
  useFrame((s) => {
    if (g.current) {
      g.current.position.x = Math.sin(s.clock.elapsedTime * 0.5) * 0.12
    }
  })
  return <group ref={g}><ParcelModel spin={0.45} /></group>
}

/** A small transparent canvas with the hero parcel floating in glow-dust, used beside the login and sign-up forms. */
export default function MiniParcel() {
  return (
    <Canvas dpr={[1, 2]} camera={{ fov: 40, position: [0, 0.4, 5.4] }} gl={{ antialias: true, alpha: true }}>
      <ambientLight intensity={0.9} />
      <directionalLight position={[3, 5, 4]} intensity={1.5} color="#ffffff" />
      <Studio />
      <Dust count={420} box={[-5, 5, -3, 3, -4, 2]} size={0.03} />
      <Spinner />
    </Canvas>
  )
}
