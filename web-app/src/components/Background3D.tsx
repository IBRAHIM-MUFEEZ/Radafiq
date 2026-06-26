import React, { useRef, useMemo } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { Float, MeshDistortMaterial } from '@react-three/drei';
import * as THREE from 'three';

function FloatingShape({ position, color, size, speed, distort }: {
  position: [number, number, number];
  color: string;
  size: number;
  speed: number;
  distort: number;
}) {
  const meshRef = useRef<THREE.Mesh>(null);
  const startPos = useMemo(() => new THREE.Vector3(...position), [position]);

  useFrame(({ clock }) => {
    if (!meshRef.current) return;
    const t = clock.getElapsedTime() * speed;
    meshRef.current.position.x = startPos.x + Math.sin(t * 0.7) * 0.8;
    meshRef.current.position.y = startPos.y + Math.cos(t * 0.5) * 0.8;
    meshRef.current.rotation.x = t * 0.1;
    meshRef.current.rotation.y = t * 0.15;
  });

  return (
    <mesh ref={meshRef} position={position} scale={size}>
      <icosahedronGeometry args={[1, 1]} />
      <MeshDistortMaterial
        color={color}
        emissive={color}
        emissiveIntensity={0.15}
        roughness={0.2}
        metalness={0.8}
        distort={distort}
        speed={2}
        transparent
        opacity={0.35}
      />
    </mesh>
  );
}

function FloatingRing({ position, color, size, speed }: {
  position: [number, number, number];
  color: string;
  size: number;
  speed: number;
}) {
  const meshRef = useRef<THREE.Mesh>(null);

  useFrame(({ clock }) => {
    if (!meshRef.current) return;
    const t = clock.getElapsedTime() * speed;
    meshRef.current.rotation.x = t * 0.2;
    meshRef.current.rotation.y = t * 0.3;
    meshRef.current.position.y = position[1] + Math.sin(t * 0.4) * 0.5;
  });

  return (
    <mesh ref={meshRef} position={position} scale={size}>
      <torusGeometry args={[1, 0.05, 16, 48]} />
      <meshStandardMaterial
        color={color}
        emissive={color}
        emissiveIntensity={0.1}
        transparent
        opacity={0.2}
        wireframe={false}
      />
    </mesh>
  );
}

function Scene() {
  return (
    <>
      <ambientLight intensity={0.4} />
      <pointLight position={[10, 10, 10]} intensity={0.6} />
      <pointLight position={[-10, -5, -10]} intensity={0.3} color="#5B7FFF" />

      <Float speed={1.5} rotationIntensity={0.2} floatIntensity={0.5}>
        <FloatingShape
          position={[-4, 2, -3]}
          color="#5B7FFF"
          size={0.6}
          speed={0.3}
          distort={0.3}
        />
      </Float>

      <Float speed={1.2} rotationIntensity={0.15} floatIntensity={0.4}>
        <FloatingShape
          position={[5, -1, -2]}
          color="#2DD4A0"
          size={0.5}
          speed={0.25}
          distort={0.4}
        />
      </Float>

      <Float speed={0.8} rotationIntensity={0.1} floatIntensity={0.3}>
        <FloatingShape
          position={[-2, -3, -5]}
          color="#F59E5A"
          size={0.4}
          speed={0.2}
          distort={0.5}
        />
      </Float>

      <FloatingRing
        position={[3, 2, -4]}
        color="#5B7FFF"
        size={0.8}
        speed={0.2}
      />
      <FloatingRing
        position={[-3, -2, -6]}
        color="#2DD4A0"
        size={0.6}
        speed={0.15}
      />
    </>
  );
}

export default function Background3D() {
  return (
    <div id="three-bg">
      <Canvas
        camera={{ position: [0, 0, 8], fov: 60 }}
        dpr={[1, 1.5]}
        gl={{ alpha: true, antialias: true }}
        style={{ background: 'transparent' }}
      >
        <Scene />
      </Canvas>
    </div>
  );
}
