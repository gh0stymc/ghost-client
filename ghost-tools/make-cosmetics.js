// Genera los cosméticos 3D de ghost client (mascotas, sombreros, cosas de la espalda y auras) en estilo Blockbench,
// como los de Lunar: pocos bloques con una textura pintada pixel a pixel (tools/blocky.js).
// Uso: node tools/make-cosmetics.js <carpeta-assets-del-mod> <carpeta-assets-del-launcher> [id...]
//   mod:      <assets>/ghost/models/<id>.json, <assets>/textures/ghost/cosmetics/<id>/model.png y preview.png
//   launcher: <launcher>/cosmetics-models/<id>.json, <launcher>/cosmetics/<id>/model.png y preview.png
// GHOST_ICON_SIZE / GHOST_ICON_YAW / GHOST_ICON_PITCH: para revisar un icono a más tamaño o desde otro ángulo.
//
// Formato del JSON (medidas en píxeles de Minecraft; y hacia arriba; x+ = izquierda del jugador; z- = delante):
//   slot: pet | hat | back | aura;  texture: 1 (usa model.png)
//   offset: dónde va respecto al ancla. hat → cabeza (de y 0 a 8, x/z de -4 a 4; con la 2ª capa, ±4.5 y hasta 8.5);
//           back → cuerpo (de y -12 a 0, la espalda en z 2); pet y aura → el jugador (los pies en y -24)
//   parts: [{ pivot, rot, parent, anim, meshes: [{ mat, g (brilla), t (tiene pixeles translúcidos), cut (tiene pixeles
//            vacíos), q: 1 (caras de 4 vértices), v, n, uv }] }]
//   anim.type: bob, hop, sway, flap (Y), flapZ, spin, blink, pulse (brillo), shadowHop
//   aura: { type: orbit | sparks, colors, ... } (partículas)
const path = require('path')
const fs = require('fs')
const { render } = require('./render3d')
const { build, shaded, mix, mul, rgb, hash, rotate } = require('./blocky')

const [modAssets, launcherAssets, ...only] = process.argv.slice(2)
if (!modAssets || !launcherAssets) { console.error('Uso: node tools/make-cosmetics.js <assets-mod> <assets-launcher> [id...]'); process.exit(1) }

// Materiales (el launcher los usa para el brillo; en el juego todo va pintado en la textura)
const MATERIALS = {
  matte: { ks: 0.05, shin: 8, metal: 0, rim: 0.1, rough: 0.9 },
  cloth: { ks: 0.03, shin: 6, metal: 0, rim: 0.12, rough: 0.95 },
  leather: { ks: 0.15, shin: 16, metal: 0, rim: 0.08, rough: 0.7 },
  feather: { ks: 0.05, shin: 10, metal: 0, rim: 0.15, rough: 0.9, lift: 0.16 },
  gold: { ks: 0.5, shin: 30, metal: 0.4, rim: 0.05, rough: 0.45 },
  steel: { ks: 0.6, shin: 50, metal: 0.4, rim: 0.06, rough: 0.35 },
  gem: { ks: 0.8, shin: 60, metal: 0, rim: 0.1, rough: 0.2, emissive: 0.6 },
  ghost: { ks: 0.08, shin: 12, metal: 0, rim: 0.2, rough: 0.8 },
  slime: { ks: 0.5, shin: 40, metal: 0, rim: 0.2, rough: 0.25 },
  glow: { ks: 0, shin: 1, metal: 0, rim: 0, rough: 1, emissive: 1 }
}

const C = (from, size, paint, o = {}) => ({ from, size, paint, ...o })
const MODELS = {}

// Pintura con detalles: la primera función que devuelva algo gana (detalles encima de la base)
const layers = (...fns) => (p, n, t) => { for (const f of fns) { const c = f(p, n, t); if (c !== undefined) return c } }

// ===================== Mascotas =====================
// Boo: fantasmita de bloques con la cabeza escalonada, bajo de "sábana" con picos de distinto largo, mofletes, boca
// con lengua, ojos que parpadean (con brillo) y manitas que saludan.
{
  const body = (p) => mix('#ffffff', '#cfc5ee', (5.5 - p[1]) / 7.5)
  const base = shaded(body, { y0: -1, y1: 7.5, grad: 0.1, noise: 0.035 })
  const front = (p, n) => {
    if (n[2] !== -1) return
    const ax = Math.abs(p[0])
    if (ax < 0.5 && p[1] > 2 && p[1] < 2.3) return '#ff6f91' // lengua
    if (ax < 0.75 && p[1] > 2 && p[1] < 2.75) return '#2b1b2e' // boca
    if (ax > 2 && ax < 3 && p[1] > 2.75 && p[1] < 3.25) return mix('#ffb3c9', '#ff9bb8', hash(p[0], p[1]))
  }
  const cubes = [
    C([-3.5, 0.5, -3.5], [7, 6, 7], layers(front, base), { mat: 'ghost' }),
    C([-3, 6.5, -3], [6, 0.5, 6], base, { mat: 'ghost' }),
    C([-2, 7, -2], [4, 0.5, 4], base, { mat: 'ghost' })
  ]
  // picos de la sábana alrededor del borde de abajo
  for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) {
    if (i > 0 && i < 3 && j > 0 && j < 3) continue
    const L = (i + j) % 2 ? 0.75 : 1.5
    cubes.push(C([-3.5 + i * 1.75, 0.5 - L, -3.5 + j * 1.75], [1.75, L, 1.75], base, { mat: 'ghost' }))
  }
  const eyePaint = (p, n, t) => (t.u <= 2 && t.v <= 2 && t.u >= 1 && t.v >= 1) ? '#ffffff' : (t.v >= t.fh - 2 ? '#2c2438' : '#15111c')
  const eye = (x) => C([x, 3.25, -3.53], [1.5, 2, 0], eyePaint, { mat: 'gem' })
  const arm = (x) => C([x, 2, -0.75], [1.5, 1.5, 1.5], base, { mat: 'ghost' })
  MODELS.pet_boo = {
    slot: 'pet', offset: [-13, 3, 4], texel: 0.25,
    parts: [
      { pivot: [0, 3, 0], anim: { type: 'bob', amp: 1.4, speed: 0.45 }, cubes },
      { pivot: [0, 4.25, -3.5], parent: 0, anim: { type: 'blink', speed: 0.28 }, cubes: [eye(-2.25), eye(0.75)] },
      { pivot: [3.5, 2.75, 0], parent: 0, anim: { type: 'flapZ', amp: 22, speed: 0.9 }, cubes: [arm(3.5)] },
      { pivot: [-3.5, 2.75, 0], parent: 0, anim: { type: 'flapZ', amp: -22, speed: 0.9, phase: 0.5 }, cubes: [arm(-5)] }
    ]
  }
}

// Gelatina: slime como el de Minecraft (caja de fuera translúcida con el borde más claro y un brillo, y un núcleo
// dentro con los ojos y la boca). El núcleo va con algo de retraso al saltar, y la sombra se encoge en el aire.
{
  const hop = { type: 'hop', amp: 6, speed: 0.75 }
  const shell = (p, n, t) => {
    const edge = t.u === 0 || t.v === 0 || t.u === t.fw - 1 || t.v === t.fh - 1
    const k = 1 + 0.06 * (hash(p[0], p[1], p[2]) * 2 - 1)
    if (n[1] >= 0 && t.u >= 1 && t.v >= 1 && t.u + t.v <= 3) return [235, 255, 230, 215] // brillo
    if (edge) return [...mul('#a6f09a', k), 225]
    return [...mul(n[1] < 0 ? '#5fc055' : '#7ad86d', k), 120]
  }
  const core = shaded('#4aa84c', { y0: 1, y1: 7, grad: 0.25, noise: 0.05 })
  const eyeBase = shaded('#173b1c', { noise: 0.04 })
  const eye = (p, n, t) => (n[2] === -1 && t.u === t.fw - 2 && t.v === 1) ? '#d9ffd5' : eyeBase(p, n, t)
  const shadow = (p) => { const d = Math.hypot(p[0], p[2]) / 4.5; return d > 1 ? null : [0, 0, 0, Math.round(110 * (1 - d * d))] }
  MODELS.pet_slime = {
    slot: 'pet', offset: [-15, -24, 1], texel: 0.5,
    parts: [
      { pivot: [0, 0, 0], anim: { type: 'shadowHop', amp: 0.45, speed: 0.75 }, cubes: [C([-4.5, 0.05, -4.5], [9, 0, 9], shadow, { face: 'up' })] },
      { pivot: [0, 0, 0], anim: hop, cubes: [C([-4, 0, -4], [8, 8, 8], shell, { mat: 'slime' })] },
      {
        pivot: [0, 0, 0], anim: { ...hop, phase: -0.035 },
        cubes: [
          C([-3, 1, -3], [6, 6, 6], core, { mat: 'slime' }),
          C([-2.75, 4, -3.6], [1.5, 1.5, 0.6], eye, { mat: 'gem' }), C([1.25, 4, -3.6], [1.5, 1.5, 0.6], eye, { mat: 'gem' }),
          C([-0.5, 2.5, -3.5], [1, 0.5, 0.5], eyeBase, { mat: 'gem' })
        ]
      }
    ]
  }
}

// ===================== Sombreros =====================
// Corona fina de oro: aro de medio pixel de grosor y 2 de alto pegado a la cabeza (con remaches grabados), puntas
// escalonadas (una alta en el centro de cada lado y una baja en cada esquina) con una perla, y gemas que brillan.
// gemas talladas en pixel: claro arriba a la izquierda, oscuro abajo a la derecha, borde oscuro
const gemPaint = (c) => (p, n, t) => {
  if (t.u === 0 && t.v === 0) return mix(c, '#ffffff', 0.7)
  if (t.u === 1 && t.v === 0 && t.fw > 2) return mix(c, '#ffffff', 0.35)
  if (t.u === t.fw - 1 || t.v === t.fh - 1) return mix(mul(c, 0.55), '#2c1f63', 0.3)
  return mul(c, 1 + 0.08 * (hash(p[0], p[1], p[2]) - 0.5))
}

// Corona real, gorda y brillante: aro de oro de 2,5 de alto con molduras arriba y abajo, puntas grandes escalonadas
// (alta en el centro de cada lado, medianas en las esquinas) rematadas con perlas, gemas grandes engastadas y un
// cojín de terciopelo rojo dentro con un orbe de oro encima.
{
  const gold = shaded('#f5c23e', { y0: 6.5, y1: 12, grad: 0.3, top: 1.25, bottom: 0.66, side: 0.82, noise: 0.05 })
  const band = layers((p, n, t) => {
    if (n[1] !== 0) return
    const y = p[1]
    if (y > 9.0) return mix('#fff2b8', '#f5c23e', 0.2) // moldura de arriba (brilla)
    if (y > 8.75) return '#9a6a14'
    if (y < 7.25) return '#b8862a' // moldura de abajo
    if (y < 7.5) return '#ffe48a'
    if (t.u % 6 === 2 && y > 7.9 && y < 8.4) return '#fff6c8' // remaches
  }, gold)
  const pearl = shaded('#fffaf0', { noise: 0.03, top: 1.1, bottom: 0.8, hue: 0.4 })
  const R = 4.75, T = 0.75
  const cubes = [
    C([-R, 6.75, -R], [2 * R, 2.5, T], band, { mat: 'gold' }), C([-R, 6.75, R - T], [2 * R, 2.5, T], band, { mat: 'gold' }),
    C([-R, 6.75, -R + T], [T, 2.5, 2 * R - 2 * T], band, { mat: 'gold' }), C([R - T, 6.75, -R + T], [T, 2.5, 2 * R - 2 * T], band, { mat: 'gold' })
  ]
  // punta escalonada sobre el aro: anchos de cada escalón (de abajo a arriba) y una perla
  const spike = (side, s, widths, pearlSize = 0.75) => {
    const out = []
    widths.forEach((w, k) => {
      const y = 9.25 + k * 0.5
      const at = { n: [s - w / 2, -R, w, T], s: [s - w / 2, R - T, w, T], w: [-R, s - w / 2, T, w], e: [R - T, s - w / 2, T, w] }[side]
      out.push(C([at[0], y, at[1]], [at[2], 0.5, at[3]], gold, { mat: 'gold' }))
    })
    const y = 9.25 + widths.length * 0.5
    const c = { n: [s, -R + T / 2], s: [s, R - T / 2], w: [-R + T / 2, s], e: [R - T / 2, s] }[side]
    out.push(C([c[0] - pearlSize / 2, y, c[1] - pearlSize / 2], [pearlSize, pearlSize, pearlSize], pearl, { mat: 'matte' }))
    return out
  }
  for (const side of ['n', 's', 'w', 'e']) {
    cubes.push(...spike(side, 0, [3, 2.25, 1.5, 0.75], 1))
    cubes.push(...spike(side, -3.1, [2, 1.25, 0.5]))
    cubes.push(...spike(side, 3.1, [2, 1.25, 0.5]))
  }
  // cojín de terciopelo rojo con un orbe de oro y una crucecita
  const velvet = shaded('#c8203c', { y0: 8.5, y1: 11.5, grad: 0.35, noise: 0.09, top: 1.15, bottom: 0.75 })
  cubes.push(C([-4, 8.5, -4], [8, 1.75, 8], velvet, { mat: 'cloth' }))
  cubes.push(C([-3, 10.25, -3], [6, 0.75, 6], velvet, { mat: 'cloth' }))
  cubes.push(C([-1.75, 11, -1.75], [3.5, 0.5, 3.5], velvet, { mat: 'cloth' }))
  cubes.push(C([-0.75, 11.5, -0.75], [1.5, 1.5, 1.5], gold, { mat: 'gold' }))
  cubes.push(C([-0.25, 13, -0.25], [0.5, 1.5, 0.5], gold, { mat: 'gold' }))
  cubes.push(C([-0.75, 13.5, -0.25], [1.5, 0.5, 0.5], gold, { mat: 'gold' }))
  const gems = [
    C([-0.75, 7.25, -R - 0.25], [1.5, 1.5, 0.25], gemPaint('#ff2d55'), { glow: true, mat: 'gem' }),
    C([-0.75, 7.25, R], [1.5, 1.5, 0.25], gemPaint('#9b6bff'), { glow: true, mat: 'gem' }),
    C([-R - 0.25, 7.25, -0.75], [0.25, 1.5, 1.5], gemPaint('#2ee87a'), { glow: true, mat: 'gem' }),
    C([R, 7.25, -0.75], [0.25, 1.5, 1.5], gemPaint('#2ee87a'), { glow: true, mat: 'gem' })
  ]
  for (const s of [-3.1, 3.1]) {
    gems.push(C([s - 0.5, 7.5, -R - 0.2], [1, 1, 0.2], gemPaint('#3fb6ff'), { glow: true, mat: 'gem' }))
    gems.push(C([s - 0.5, 7.5, R], [1, 1, 0.2], gemPaint('#3fb6ff'), { glow: true, mat: 'gem' }))
    gems.push(C([-R - 0.2, 7.5, s - 0.5], [0.2, 1, 1], gemPaint('#ffb020'), { glow: true, mat: 'gem' }))
    gems.push(C([R, 7.5, s - 0.5], [0.2, 1, 1], gemPaint('#ffb020'), { glow: true, mat: 'gem' }))
  }
  MODELS.hat_crown = {
    slot: 'hat', offset: [0, 0, 0], texel: 0.25,
    parts: [
      { pivot: [0, 8, 0], cubes },
      { pivot: [0, 8, 0], parent: 0, anim: { type: 'pulse', amp: 0.25, speed: 0.35 }, cubes: gems }
    ]
  }
}

// Gorro de bruja de tela: ala redonda (recortada en pixeles) con el borde más oscuro, copa con cinta naranja y hebilla,
// cono de escalones pequeños que se dobla hacia atrás, con un parche cosido, y una estrella en la punta.
{
  const purple = (p) => mix('#3f2268', '#6a3fa8', (p[1] - 7) / 12)
  const cloth = shaded(purple, { y0: 6.5, y1: 19, grad: 0.1, noise: 0.07, top: 1.14, bottom: 0.78 })
  const brim = (p, n, t) => {
    const r = Math.hypot(p[0], p[2])
    // ala redonda con el borde un poco ondulado
    const a = Math.atan2(p[2], p[0])
    const R = 8.3 + Math.sin(a * 5) * 0.25
    if (r > R) return null
    if (r > R - 0.6) return mul(n[1] > 0 ? '#2f1852' : '#24123f', 1 + 0.06 * (hash(p[0], p[2]) - 0.5))
    return cloth(p, n, t)
  }
  const wall = layers((p, n, t) => {
    if (n[1] !== 0 || p[1] > 8.25) return
    if (n[2] === -1 && Math.abs(p[0]) < 1.1 && p[1] > 6.9) { // hebilla
      if (Math.abs(p[0]) < 0.6 && p[1] > 7.3 && p[1] < 7.95) return '#3a2410'
      return mul('#f2c040', p[1] > 7.9 ? 1.2 : 1)
    }
    return mul('#e0742a', (p[1] > 8 ? 1.2 : 1) * (1 + 0.06 * (hash(p[0], p[1], p[2]) - 0.5)))
  }, cloth)
  const patch = layers((p, n, t) => {
    if (n[0] !== -1 || p[1] < 13 || p[1] > 13.9 || p[2] < -1.2 || p[2] > 0.4) return
    const edge = p[1] < 12.8 || p[1] > 13.3 || p[2] < -1 || p[2] > 0.2
    return edge ? ((t.u + t.v) % 2 ? '#d8c6f2' : '#7c52b8') : '#8a5cc6'
  }, cloth)
  const tier = (from, size, rx, paint = cloth) => C(from, size, paint, { mat: 'cloth', rot: [rx, 0, 0], pivot: [from[0] + size[0] / 2, from[1], from[2] + size[2] / 2] })
  const cubes = [
    C([-8.5, 6.5, -8.5], [17, 0.5, 17], brim, { mat: 'cloth' }),
    C([-5.5, 6.25, -5.5], [11, 0.25, 11], brim, { mat: 'cloth' }),
    C([-4.75, 7, -4.75], [9.5, 2.5, 9.5], wall, { mat: 'cloth' })
  ]
  // cono: muchos escalones finos (se ve casi liso) que se estrechan y, arriba, se doblan hacia atrás y cuelgan
  let tip
  const N = 16
  for (let k = 0; k < N; k++) {
    const s = k / (N - 1)
    const half = 3.9 * Math.pow(1 - s, 0.85) + 0.35
    const y = 9.5 + k * 0.72 - Math.max(0, s - 0.7) * 3.2
    const zc = Math.pow(s, 2.2) * 5.2
    cubes.push(tier([-half, y, zc - half], [half * 2, 0.85, half * 2], Math.pow(s, 2) * 62, k === 5 ? patch : cloth))
    tip = [0, y + 1.0, zc + 0.9]
  }
  const star = (p, n, t) => (t.u + t.v) % 3 === 0 ? '#fff4c2' : '#ffd23f'
  const stars = [
    C([tip[0] - 0.5, tip[1] - 0.5, tip[2] - 0.25], [1, 1, 0.5], star, { glow: true, mat: 'gem' }),
    C([tip[0] - 0.25, tip[1] + 0.5, tip[2] - 0.15], [0.5, 0.5, 0.3], star, { glow: true, mat: 'gem' }),
    C([tip[0] - 0.25, tip[1] - 1, tip[2] - 0.15], [0.5, 0.5, 0.3], star, { glow: true, mat: 'gem' }),
    C([tip[0] - 1, tip[1] - 0.25, tip[2] - 0.15], [0.5, 0.5, 0.3], star, { glow: true, mat: 'gem' }),
    C([tip[0] + 0.5, tip[1] - 0.25, tip[2] - 0.15], [0.5, 0.5, 0.3], star, { glow: true, mat: 'gem' })
  ]
  MODELS.hat_witch = {
    slot: 'hat', offset: [0, 0, 0], rot: [-5, 0, 0], texel: 0.25,
    parts: [
      { pivot: [0, 8, 0], cubes },
      { pivot: [tip[0], tip[1] - 0.8, tip[2]], parent: 0, anim: [{ type: 'sway', amp: 10, speed: 0.35 }, { type: 'pulse', amp: 0.35, speed: 0.6 }], cubes: stars }
    ]
  }
}

// ===================== Espalda =====================
// Alas de ángel (como las de Lunar): las plumas nacen a lo largo de un brazo que sube del hombro hacia fuera,
// en tres capas (largas detrás, medianas y cobertoras cortas delante), cada pluma una lámina de medio pixel pintada de
// blanco a cian pálido con la punta en pico, así el borde del ala queda dentado con cada punta separada.
// Adorno de oro con gema en el hombro.
const featherPaint = (layer, seed) => {
  const [c0, c1] = [['#ffffff', '#b9d3ee'], ['#ffffff', '#cfe2f4'], ['#ffffff', '#e6f0fa']][layer]
  const tone = 1 - hash(seed, 3) * 0.05
  return (p, n, t) => {
    if (n[1] !== 0) return null // los extremos (la punta va recortada)
    const s = t.fh > 1 ? t.v / (t.fh - 1) : 0 // 0 = base, 1 = punta
    // punta en pico: en las últimas filas solo queda el centro
    const fromTip = t.fh - 1 - t.v
    if (n[0] !== 0) return fromTip < 3 ? null : mul(mix(c0, c1, s), 0.9) // cantos
    const half = (t.fw - 1) / 2
    if (fromTip < 3 && Math.abs(t.u - half) > (fromTip + 0.5) * t.fw / 7) return null
    let c = mix(c0, c1, Math.pow(s, 1.3))
    const edge = Math.abs(t.u - half) > half - 0.6
    if (edge) c = mix(c, '#8aa6cc', 0.25 + 0.3 * s) // contorno frío, como en el pixel art
    if (Math.abs(t.u - half) < 0.6 && s < 0.8) c = mix(c, '#e2edf4', 0.5) // raquis
    return mul(c, tone * (1 + 0.035 * (hash(p[0], p[1], p[2], seed) * 2 - 1)))
  }
}
function wing (dir) {
  const back = []
  const front = []
  const X = (x) => dir > 0 ? x : -x
  // brazo del ala: del hombro hacia fuera y arriba. Las plumas nacen a lo largo del brazo: abajo cuelgan hacia el
  // suelo y, cuanto más arriba, más apuntan al cielo (así el borde de las puntas forma el arco del ala)
  const S = [0.5, -1], W = [9, 5.5]
  const arm = (t) => [S[0] + (W[0] - S[0]) * t, S[1] + (W[1] - S[1]) * t + Math.sin(t * Math.PI) * 1.6]
  let seed = 0
  const feather = (out, layer, [x, y], angle, len, w, z) => {
    const phi = (angle + 90) * (dir > 0 ? 1 : -1)
    out.push(C([X(x) - w / 2, y - len, z], [w, len, 0.5], featherPaint(layer, seed++), { mat: 'feather', rot: [0, 0, phi], pivot: [X(x), y, z + 0.25] }))
  }
  const angleAt = (t) => -72 + 158 * t
  const lenAt = (t) => 9 + 8 * Math.pow(t, 0.7) - (t > 0.9 ? (t - 0.9) * 26 : 0)
  const N = 14
  for (let i = 0; i < N; i++) { const t = i / (N - 1); feather(back, 0, arm(t), angleAt(t), lenAt(t), 2.75, 0) }
  for (let i = 0; i < 11; i++) { const t = (i + 0.5) / 11; feather(front, 1, arm(t * 0.97), angleAt(t) - 4, lenAt(t) * 0.66, 2.5, 0.5) }
  for (let i = 0; i < 9; i++) { const t = (i + 0.3) / 9; feather(front, 2, arm(t * 0.95), angleAt(t) - 8, lenAt(t) * 0.36, 2.25, 1) }
  // hueso del ala cubierto de plumón (tramos a lo largo del brazo)
  const down = shaded('#ffffff', { y0: -2, y1: 6, grad: 0.08, noise: 0.03, bottom: 0.92 })
  for (let k = 0; k < 4; k++) {
    const [ax, ay] = arm(k / 4), [bx, by] = arm((k + 1) / 4)
    const len = Math.hypot(bx - ax, by - ay)
    const ang = Math.atan2(by - ay, X(bx) - X(ax)) * 180 / Math.PI
    front.push(C([X(ax) - 0.9, ay, 1.4], [1.8, len + 0.4, 0.8], down, { mat: 'feather', rot: [0, 0, ang - 90], pivot: [X(ax), ay, 1.8] }))
  }
  const F = arm(0.15)
  front.push(C([X(F[0]) - 0.75, F[1] - 0.75, 2.1], [1.5, 1.5, 0.5], shaded('#f2c040', { top: 1.25, bottom: 0.7 }), { mat: 'gold' }))
  const gemP = (p, n, t) => (t.u === 0 && t.v === 0) ? '#e6f8ff' : '#6cc8ff'
  const gem = [C([X(F[0]) - 0.375, F[1] - 0.375, 2.6], [0.75, 0.75, 0.25], gemP, { glow: true, mat: 'gem' })]
  return { back, front, gem, F: arm(0.5) }
}
{
  const parts = []
  const move = (cubes, d) => cubes.map(c => ({ ...c, from: c.from.map((v, a) => v + d[a]), pivot: c.pivot && c.pivot.map((v, a) => v + d[a]) }))
  for (const dir of [1, -1]) {
    const w = wing(dir)
    const base = parts.length
    const d = [dir * 1.5, -3, 2.2]
    parts.push({ pivot: d, rot: [0, -24 * dir, 0], anim: { type: 'flap', amp: 14 * dir, speed: 0.5 }, cubes: move(w.front, d) })
    parts.push({ pivot: [dir * (1.5 + w.F[0]), w.F[1] - 3, 2.2], parent: base, anim: { type: 'flap', amp: 7 * dir, speed: 0.5, phase: -0.12 }, cubes: move(w.back, d) })
    parts.push({ pivot: d, parent: base, anim: { type: 'pulse', amp: 0.3, speed: 0.4 }, cubes: move(w.gem, d) })
  }
  MODELS.back_wings = { slot: 'back', iconYaw: 180, iconPitch: 10, offset: [0, 0, 0], texel: 0.25, parts }
}

// Espada a la espalda, en diagonal: hoja de acero pintada (filos claros, canal central oscuro) que acaba en punta
// escalonada, runas que brillan, guarda de oro con gavilanes y gemas, empuñadura de cuero en espiral y pomo con gema.
{
  const steel = (p, n, t) => {
    let c
    if (n[0] !== 0) c = '#eef3f8'
    else if (n[1] !== 0) c = '#dfe6ee'
    else {
      const ax = Math.abs(p[0])
      // filo claro, bisel, cuerpo y canal central oscuro (acaba antes de la punta)
      c = ax > 1.15 ? '#ffffff' : ax > 0.85 ? '#dfe7f1' : (ax < 0.35 && p[1] < 7.5 ? '#6d7d97' : '#aebbcc')
      if (ax > 1.15 && p[0] < 0) c = '#cfd9e6'
    }
    return mix(mul(c, (1 + 0.04 * (hash(p[0], p[1], p[2]) * 2 - 1)) * (1 + 0.08 * (p[1] + 3) / 8)), '#2c1f63', p[1] < 0 ? 0.08 : 0)
  }
  const gold = shaded('#f2c040', { y0: -18, y1: -10, grad: 0.15, top: 1.22, bottom: 0.7 })
  const leather = (p, n, t) => mul(((Math.floor(p[1] * 4) + t.u) % 4) < 2 ? '#6e442b' : '#3c2317', 1 + 0.06 * (hash(p[0], p[1], p[2]) - 0.5))
  const b = [
    C([-1.5, -2.5, -0.375], [3, 13, 0.75], steel, { mat: 'steel' }),
    C([-1.25, 10.5, -0.375], [2.5, 0.75, 0.75], steel, { mat: 'steel' }),
    C([-1, 11.25, -0.375], [2, 0.75, 0.75], steel, { mat: 'steel' }),
    C([-0.75, 12, -0.375], [1.5, 0.75, 0.75], steel, { mat: 'steel' }),
    C([-0.5, 12.75, -0.375], [1, 0.75, 0.75], steel, { mat: 'steel' }),
    C([-0.25, 13.5, -0.25], [0.5, 0.5, 0.5], steel, { mat: 'steel' }),
    C([-3.5, -3.75, -0.875], [7, 1.25, 1.75], gold, { mat: 'gold' }),
    C([-4.5, -3.5, -0.7], [1, 2.25, 1.4], gold, { mat: 'gold' }),
    C([3.5, -3.5, -0.7], [1, 2.25, 1.4], gold, { mat: 'gold' }),
    C([-4.5, -1.25, -0.5], [0.75, 0.75, 1], gold, { mat: 'gold' }),
    C([3.75, -1.25, -0.5], [0.75, 0.75, 1], gold, { mat: 'gold' }),
    C([-0.75, -8.5, -0.75], [1.5, 4.75, 1.5], leather, { mat: 'leather' }),
    C([-1.25, -10.25, -1.25], [2.5, 1.75, 2.5], gold, { mat: 'gold' })
  ]
  const glow = [
    C([-0.75, -3.5, -1], [1.5, 0.75, 0.125], gemPaint('#9b6bff'), { glow: true, mat: 'gem' }),
    C([-0.75, -3.5, 0.875], [1.5, 0.75, 0.125], gemPaint('#9b6bff'), { glow: true, mat: 'gem' }),
    C([-0.75, -10, -1.375], [1.5, 1.25, 0.125], gemPaint('#ff2d55'), { glow: true, mat: 'gem' })
  ]
  const rune = (p, n, t) => (t.u + t.v) % 2 ? '#e2d4ff' : '#a582ff'
  for (let y = -1; y < 7; y += 2) {
    glow.push(C([-0.25, y, -0.39], [0.5, 1, 0], rune, { glow: true, mat: 'glow', face: 'north' }))
    glow.push(C([-0.25, y, 0.39], [0.5, 1, 0], rune, { glow: true, mat: 'glow', face: 'south' }))
  }
  const mv = (cubes) => cubes.map(c => ({ ...c, from: [c.from[0], c.from[1] - 8, c.from[2] + 3.6] }))
  MODELS.back_sword = {
    slot: 'back', offset: [0, 0, 0], texel: 0.25,
    parts: [
      { pivot: [0, -6, 3.6], rot: [0, 0, 38], cubes: mv(b) },
      { pivot: [0, -6, 3.6], parent: 0, anim: { type: 'pulse', amp: 0.4, speed: 0.5 }, cubes: mv(glow) }
    ]
  }
}

// ===================== Nuevos =====================
// Murciélago: bola de pelo morada oscura con orejas grandes, ojos que brillan, colmillos y alas de membrana con
// dedos pintados que aletean deprisa.
{
  const fur = shaded((p) => mix('#4a3566', '#2a1d3d', (4 - p[1]) / 5), { y0: 0, y1: 5, grad: 0.15, noise: 0.12, top: 1.18, bottom: 0.78 })
  const face = layers((p, n, t) => {
    if (n[2] !== -1) return
    const ax = Math.abs(p[0])
    if (ax > 0.5 && ax < 1.5 && p[1] > 2.5 && p[1] < 3.5) return (t.u % 4 === 1 && t.v % 4 === 1) ? '#ffffff' : '#ff3d6e' // ojos
    if (ax < 1 && p[1] > 1.25 && p[1] < 1.75) return ax > 0.5 && p[1] < 1.5 ? '#ffffff' : '#1a0f26' // boca y colmillos
    if (ax < 1.75 && p[1] > 0.75 && p[1] < 3.9) return mix('#6b4f8f', '#4a3566', hash(p[0], p[1])) // carita más clara
  }, fur)
  const ear = shaded('#3a2852', { noise: 0.1, top: 1.2 })
  const earIn = (p, n, t) => (n[2] === -1 && t.u > 0 && t.u < t.fw - 1 && t.v > 0) ? '#c76b9a' : ear(p, n, t)
  const membrane = (p, n, t) => {
    // ala: lámina con el borde de abajo festoneado entre los dedos
    const u = t.u / (t.fw - 1), v = t.v / (t.fh - 1)
    const scallop = 0.62 + 0.38 * Math.abs(Math.cos(u * Math.PI * 2.5))
    if (v > scallop) return null
    if (Math.abs((u * 2.5) % 1 - 0.5) < 0.08 || v < 0.1) return '#2a1d3d' // dedos y brazo
    return mul(mix('#7a4fa8', '#4a2f6e', v), 1 + 0.05 * (hash(p[0], p[1], p[2]) - 0.5))
  }
  const wingL = C([2, 1.5, 0], [6, 4, 0], membrane, { mat: 'matte', face: 'north' })
  const wingR = C([-8, 1.5, 0], [6, 4, 0], membrane, { mat: 'matte', face: 'north' })
  MODELS.pet_bat = {
    slot: 'pet', offset: [-13, 4, 4], texel: 0.25,
    parts: [
      {
        pivot: [0, 2.5, 0], anim: { type: 'bob', amp: 1.8, speed: 0.6 },
        cubes: [
          C([-2, 0.5, -2], [4, 4, 4], face, { mat: 'matte' }),
          // orejas: triángulos escalonados
          C([-2, 4.5, -0.5], [1.75, 0.75, 0.75], earIn, { mat: 'matte' }), C([-1.75, 5.25, -0.5], [1.25, 0.75, 0.75], earIn, { mat: 'matte' }),
          C([-1.5, 6, -0.5], [0.75, 0.75, 0.75], ear, { mat: 'matte' }),
          C([0.25, 4.5, -0.5], [1.75, 0.75, 0.75], earIn, { mat: 'matte' }), C([0.5, 5.25, -0.5], [1.25, 0.75, 0.75], earIn, { mat: 'matte' }),
          C([0.75, 6, -0.5], [0.75, 0.75, 0.75], ear, { mat: 'matte' }),
          C([-0.75, 0, -1], [1.5, 0.5, 1.5], fur, { mat: 'matte' })
        ]
      },
      { pivot: [2, 3.5, 0], parent: 0, anim: { type: 'flapZ', amp: 40, speed: 2.2 }, cubes: [wingL] },
      { pivot: [-2, 3.5, 0], parent: 0, anim: { type: 'flapZ', amp: -40, speed: 2.2 }, cubes: [wingR] }
    ]
  }
}

// Dragoncito: cuerpo morado con escamas pintadas y barriga dorada, cabeza con morro, cuernos y ojos que brillan,
// alitas que aletean y una cola de tres tramos que se mueve.
{
  const scales = (base) => shaded((p, n, t) => {
    const c = rgb(base)
    return (t.u + (t.v % 2) * 1) % 3 === 0 && n[1] >= 0 ? mul(c, 1.12) : c
  }, { y0: 0, y1: 6, grad: 0.22, noise: 0.07, top: 1.18, bottom: 0.76 })
  const body = scales('#7c5cff')
  const belly = shaded('#f2c040', { noise: 0.06, top: 1.15 })
  const bodyP = layers((p, n, t) => (n[1] === -1 || (n[2] === -1 && p[1] < 1.6 && Math.abs(p[0]) < 1.5)) ? belly(p, n, t) : undefined, body)
  const head = layers((p, n, t) => {
    if (n[2] !== -1) return
    const ax = Math.abs(p[0])
    if (ax > 0.75 && ax < 1.75 && p[1] > 5.5 && p[1] < 6.5) return (t.u % 4 === 1 && t.v % 4 === 1) ? '#ffffff' : '#2ee8c8' // ojos
  }, body)
  const snout = layers((p, n, t) => (n[2] === -1 && Math.abs(Math.abs(p[0]) - 0.5) < 0.2 && p[1] > 5) ? '#2a1658' : undefined, body)
  const horn = shaded('#fff3d6', { noise: 0.04, top: 1.1, hue: 0.3 })
  const wing = (p, n, t) => {
    const u = t.u / (t.fw - 1), v = t.v / (t.fh - 1)
    if (v > 0.55 + 0.45 * Math.abs(Math.cos(u * Math.PI * 1.5))) return null
    if (v < 0.12) return '#4a2f9e'
    return mix('#b08cff', '#7c5cff', v)
  }
  const tail = scales('#6d4ef0')
  MODELS.pet_dragon = {
    slot: 'pet', offset: [-14, 1, 4], texel: 0.25,
    parts: [
      {
        pivot: [0, 2, 0], anim: { type: 'bob', amp: 1.2, speed: 0.4 },
        cubes: [
          C([-2.25, 0.5, -2.5], [4.5, 3.5, 5.5], bodyP, { mat: 'matte' }),
          C([-2, 3.5, -4.5], [4, 3.5, 3.5], head, { mat: 'matte' }),
          C([-1.25, 3.75, -6], [2.5, 1.75, 1.5], snout, { mat: 'matte' }),
          C([-1.75, 7, -2.5], [0.75, 1.5, 0.75], horn, { mat: 'matte', rot: [-25, 0, 0] }),
          C([1, 7, -2.5], [0.75, 1.5, 0.75], horn, { mat: 'matte', rot: [-25, 0, 0] }),
          C([-0.25, 4, 2.5], [0.5, 0.75, 1.5], horn, { mat: 'matte' }),
          C([-2, -0.5, -2], [1.25, 1, 1.25], body, { mat: 'matte' }), C([0.75, -0.5, -2], [1.25, 1, 1.25], body, { mat: 'matte' }),
          C([-2, -0.5, 1.25], [1.25, 1, 1.25], body, { mat: 'matte' }), C([0.75, -0.5, 1.25], [1.25, 1, 1.25], body, { mat: 'matte' })
        ]
      },
      { pivot: [2.25, 3.5, 0], parent: 0, anim: { type: 'flapZ', amp: 30, speed: 1.1 }, cubes: [C([2.25, 1, -1], [5, 3.5, 0], wing, { mat: 'matte', face: 'north' })] },
      { pivot: [-2.25, 3.5, 0], parent: 0, anim: { type: 'flapZ', amp: -30, speed: 1.1 }, cubes: [C([-7.25, 1, -1], [5, 3.5, 0], wing, { mat: 'matte', face: 'north' })] },
      {
        pivot: [0, 1.75, 3], parent: 0, anim: { type: 'sway', amp: 18, speed: 0.5 },
        cubes: [C([-1, 1, 3], [2, 1.5, 2.5], tail, { mat: 'matte' }), C([-0.75, 1.25, 5.5], [1.5, 1, 2], tail, { mat: 'matte' }),
          C([-0.5, 1.5, 7.5], [1, 0.75, 1.5], tail, { mat: 'matte' }), C([-0.75, 1.25, 9], [1.5, 1.25, 0.75], horn, { mat: 'matte' })]
      }
    ]
  }
}

// Aureola: aro dorado que brilla, flotando sobre la cabeza; da vueltas despacio y sube y baja.
{
  const halo = (p, n, t) => {
    const k = 1 + 0.08 * (hash(p[0], p[1], p[2]) - 0.5)
    if (n[1] > 0) return mul('#fff4b8', k)
    if (n[1] < 0) return mul('#e8a72a', k)
    return mul(t.v === 0 ? '#ffe680' : '#f5c23e', k)
  }
  const cubes = []
  const R = 3.75, w = 0.75
  // aro hecho de tramos (octógono de bloques), como en Blockbench
  for (let i = 0; i < 12; i++) {
    const a = i / 12 * Math.PI * 2
    const len = 2 * R * Math.tan(Math.PI / 12) + 0.15
    const x = Math.cos(a) * R, z = Math.sin(a) * R
    cubes.push(C([x - len / 2, 11, z - w / 2], [len, 0.5, w], halo, { glow: true, mat: 'glow', rot: [0, -a * 180 / Math.PI + 90, 0], pivot: [x, 11.25, z] }))
  }
  MODELS.hat_halo = {
    slot: 'hat', offset: [0, 0, 0], texel: 0.25, iconPitch: 42,
    parts: [{ pivot: [0, 11.25, 0], anim: [{ type: 'spin', speed: 0.12 }, { type: 'bob', amp: 0.6, speed: 0.4 }], cubes }]
  }
}

// Orejas de gato: dos orejas escalonadas negras con el interior rosa y una diadema; se mueven de vez en cuando.
{
  const furC = shaded('#2a2433', { noise: 0.1, top: 1.25, bottom: 0.8, y0: 8, y1: 12 })
  const inner = shaded('#f29ac0', { noise: 0.05, hue: 0.3 })
  const ear = (x, dir) => {
    const cubes = []
    // triángulo pixelado: cada fila más estrecha; el rosa solo en el centro de la cara de delante
    const widths = [3.5, 3, 2.5, 2, 1.5, 1, 0.5]
    widths.forEach((wd, k) => {
      const paint = (p, n, t) => (n[2] === -1 && k < 5 && Math.abs(p[0] - x) < wd / 2 - 0.6) ? inner(p, n, t) : furC(p, n, t)
      cubes.push(C([x - wd / 2, 8.25 + k * 0.5, -0.5], [wd, 0.5, 0.75], paint, { mat: 'matte' }))
    })
    return cubes
  }
  const band = shaded('#2a2433', { noise: 0.05 })
  MODELS.hat_cat_ears = {
    slot: 'hat', offset: [0, 0, 0], texel: 0.25,
    parts: [
      { pivot: [0, 8, 0], cubes: [C([-4.6, 8, -0.5], [9.2, 0.5, 1], band, { mat: 'matte' })] },
      { pivot: [2.5, 8.25, 0], parent: 0, rot: [0, 0, -10], anim: { type: 'sway', amp: 6, speed: 0.3 }, cubes: ear(2.5) },
      { pivot: [-2.5, 8.25, 0], parent: 0, rot: [0, 0, 10], anim: { type: 'sway', amp: -6, speed: 0.3, phase: 0.3 }, cubes: ear(-2.5) }
    ]
  }
}

// Alas de dragón: brazo de hueso con garra y una membrana morada con venas, con el borde festoneado entre los dedos.
{
  const bone = shaded('#2a1658', { noise: 0.06, top: 1.25 })
  const claw = shaded('#fff3d6', { noise: 0.03, hue: 0.3 })
  // membrana: triángulo que cuelga del brazo, larga junto al cuerpo y corta en la punta, con el borde de abajo
  // festoneado entre los dedos. dir: 1 = ala izquierda del jugador (x+), -1 = derecha. u = 0 junto al cuerpo
  // Dedos que salen en abanico desde la muñeca (a mitad del brazo) hasta las puntas del borde de abajo
  const L = 15, Hm = 11
  const FINGERS = [0.06, 0.34, 0.62, 0.92]
  const reachBase = (u) => 0.98 - Math.pow(u, 1.25) * 0.72 // larga junto al cuerpo, corta en la punta
  const reach = (u) => {
    let d = 1
    for (const f of FINGERS) d = Math.min(d, Math.abs(u - f))
    const bump = Math.max(0, 1 - Math.pow(d / 0.15, 2)) // festón entre dedo y dedo
    return reachBase(u) * (0.72 + 0.28 * bump)
  }
  const wrist = [0.55, 0]
  const membrane = (dir) => (p, n, t) => {
    let u = t.u / (t.fw - 1)
    if (dir < 0) u = 1 - u
    const v = t.v / (t.fh - 1)
    const r = reach(u)
    if (v > r) return null
    // ¿cerca de la línea de un dedo? (en pixeles de la textura)
    for (const f of FINGERS) {
      const ax = wrist[0] * L, ay = wrist[1] * Hm, bx = f * L, by = reach(f) * Hm
      const px = u * L, py = v * Hm
      const dx = bx - ax, dy = by - ay
      const k = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
      if (Math.hypot(px - (ax + dx * k), py - (ay + dy * k)) < 0.3 * (1.2 - k * 0.6)) return mix('#2a1658', '#4a2a9e', k * 0.5)
    }
    if (v > r - 0.05) return '#3e2290' // borde
    const c = mix('#b48cff', '#5a32b8', Math.min(1, v / Math.max(0.25, r)))
    return mul(c, 1 + 0.06 * (hash(p[0], p[1], p[2]) - 0.5))
  }
  const parts = []
  for (const dir of [1, -1]) {
    const pv = [dir * 1.5, -2, 2.4]
    const x0 = dir > 0 ? 1.5 : -1.5 - L
    // el brazo sube hacia fuera (rot z) y la membrana cuelga de él
    const memb = C([x0, -3 - Hm, 2.4], [L, Hm, 0], membrane(dir), { mat: 'matte', face: 'south' })
    const arm = C([x0, -3.1, 2.1], [L, 0.9, 0.9], bone, { mat: 'matte' })
    const tip = C([dir > 0 ? 1.5 + L - 0.25 : -1.5 - L - 1, -3.4, 2.1], [1.25, 2, 0.9], claw, { mat: 'matte' })
    const thumb = C([dir > 0 ? 1.5 + L * 0.55 - 0.4 : -1.5 - L * 0.55 - 0.4, -2.3, 2.1], [0.8, 1.4, 0.8], claw, { mat: 'matte' })
    parts.push({ pivot: pv, rot: [0, -14 * dir, 40 * dir], anim: { type: 'flap', amp: 16 * dir, speed: 0.45 }, cubes: [memb, arm, tip, thumb] })
  }
  MODELS.back_dragon_wings = { slot: 'back', iconYaw: 180, iconPitch: 10, offset: [0, 0, 0], texel: 0.25, parts }
}

// ===================== Auras =====================
MODELS.aura_frost = { slot: 'aura', aura: { type: 'orbit', colors: ['#bfefff', '#6fd3ff', '#ffffff'], radius: 11, speed: 0.22, trail: 9 } }
MODELS.aura_flames = { slot: 'aura', aura: { type: 'sparks', colors: ['#ffe066', '#ff8a1f', '#ff3b2f'], count: 24, radius: 8, speed: 0.45 } }
MODELS.aura_orbit = { slot: 'aura', aura: { type: 'orbit', colors: ['#9b7bff', '#6ee7ff', '#ff7ad9'], radius: 12, speed: 0.28, trail: 7 } }
MODELS.aura_sparks = { slot: 'aura', aura: { type: 'sparks', colors: ['#ffd36b', '#ff9d5c', '#ff6fb8'], count: 18, radius: 9, speed: 0.3 } }

// ===================== Icono (render 3D) =====================
// Punto de una parte, con sus padres, en la pose de reposo
function partPoint (m, pi, p) {
  const part = m.parts[pi]
  const pv = part.pivot || [0, 0, 0]
  let v = rotate([p[0] - pv[0], p[1] - pv[1], p[2] - pv[2]], part.rot || [0, 0, 0])
  v = [v[0] + pv[0], v[1] + pv[1], v[2] + pv[2]]
  return part.parent !== undefined ? partPoint(m, part.parent, v) : v
}

const boxQuads = (c, s, color) => [[0, 1, 0], [0, -1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]].map((n) => {
  const a = n.findIndex(v => v !== 0), b = (a + 1) % 3, d = (a + 2) % 3
  const pts = [[-1, -1], [1, -1], [1, 1], [-1, 1]].map(([i, j]) => { const p = [0, 0, 0]; p[a] = n[a] * s / 2; p[b] = i * s / 2; p[d] = j * s / 2; return [c[0] + p[0], c[1] + p[1], c[2] + p[2]] })
  return { p: pts, color, glow: true }
})

function iconQuads (m, built) {
  if (m.aura) {
    const a = m.aura
    const quads = []
    for (let i = 0; i < 24; i++) {
      const t = i / 24
      const ang = t * Math.PI * 2
      const p = a.type === 'orbit'
        ? rotate([Math.cos(ang) * a.radius, 0, Math.sin(ang) * a.radius], [[0, 55, -55][i % 3], 0, 0])
        : [Math.cos(ang * 2.5) * (a.radius - t * 3), -14 + t * 28, Math.sin(ang * 2.5) * (a.radius - t * 3)]
      quads.push(...boxQuads(p, 1.2 + (i % 3) * 0.4, [...rgb(a.colors[i % a.colors.length]), 1]))
    }
    return quads
  }
  const { width, height, data } = built.atlas
  return built.quads.map(q => {
    const p = q.p.map(v => { const w = partPoint(m, q.pi, v); return m.rot ? rotate(w, m.rot) : w })
    return { p, tex: { data, width, height, uv: q.uv, soft: q.soft }, glow: q.glow, mat: MATERIALS[q.mat] }
  })
}

async function icon (m, built) {
  const env = process.env
  const yaw = env.GHOST_ICON_YAW ? +env.GHOST_ICON_YAW : (m.iconYaw ?? (m.slot === 'back' ? 22 : 180 - 32))
  const pitch = env.GHOST_ICON_PITCH ? +env.GHOST_ICON_PITCH : (m.iconPitch ?? (m.slot === 'aura' ? 12 : 18))
  const size = +(env.GHOST_ICON_SIZE || 160)
  return render({ quads: iconQuads(m, built), width: size, height: size, yaw, pitch, ss: 3, margin: 0.1, shadow: m.slot !== 'aura' })
}

;(async () => {
  const sharp = require('C:/Users/Arturo/Proyectos/mi-launcher/node_modules/sharp')
  for (const [id, m] of Object.entries(MODELS)) {
    if (only.length && !only.includes(id)) continue
    const built = m.parts ? build(m) : null
    const png = await icon(m, built)
    const { parts, texel, ...rest } = m
    const out = { id, ...rest, materials: MATERIALS }
    if (built) { out.texture = 1; out.parts = built.parts }
    const json = JSON.stringify(out)
    const tex = built && await sharp(Buffer.from(built.atlas.data.buffer), { raw: { width: built.atlas.width, height: built.atlas.height, channels: 4 } }).png().toBuffer()
    for (const [models, textures] of [[path.join(modAssets, 'ghost', 'models'), path.join(modAssets, 'textures', 'ghost', 'cosmetics')],
      [path.join(launcherAssets, 'cosmetics-models'), path.join(launcherAssets, 'cosmetics')]]) {
      fs.mkdirSync(models, { recursive: true })
      fs.writeFileSync(path.join(models, id + '.json'), json)
      fs.mkdirSync(path.join(textures, id), { recursive: true })
      fs.writeFileSync(path.join(textures, id, 'preview.png'), png)
      if (tex) fs.writeFileSync(path.join(textures, id, 'model.png'), tex)
      else fs.rmSync(path.join(textures, id, 'model.png'), { force: true })
    }
    const nCubes = (m.parts || []).reduce((s, p) => s + p.cubes.length, 0)
    console.log(id, m.slot, nCubes, 'bloques', built ? built.quads.length + ' caras, textura ' + built.atlas.width + 'x' + built.atlas.height : '', Math.round(json.length / 1024) + ' KB')
  }
})()
