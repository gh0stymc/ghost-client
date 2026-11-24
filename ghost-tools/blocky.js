// Modelos "estilo Blockbench" para los cosméticos: pocos bloques (cubos con giro o láminas planas) y una textura
// pintada pixel a pixel, con todos los pixeles del mismo tamaño (texel, en píxeles de Minecraft). El volumen se pinta en
// la textura (claro arriba, bordes oscuros, ruido de color), como hacen los cosméticos de Lunar.
//
// Un bloque: { from: [x,y,z], size: [w,h,d], rot: [x,y,z] grados (alrededor de pivot, por defecto su centro),
//              paint: (p, n, t) → color, glow, mat, face: 'up'|'down'|'north'|'south'|'west'|'east' (solo para láminas) }
// Si un lado mide 0 es una lámina: solo se dibuja una cara (sin ocultar caras traseras se ve por los dos lados).
// paint recibe p = centro del pixel (coordenadas del modelo, antes de girar el bloque), n = normal de la cara y
// t = { u, v, fw, fh, face } (posición del pixel en la cara; v = 0 arriba en las caras de los lados).
// Devuelve '#rrggbb', [r,g,b] o [r,g,b,a] (a de 0 a 255), o null = transparente.
const { hex } = require('./render3d')

const FACE_NAMES = ['up', 'down', 'north', 'south', 'west', 'east']
// mismo orden y esquinas que render3d.FACES; o = esquina de origen (fracciones), u/v = ejes de la textura
const FACES = [
  { n: [0, 1, 0], c: [[0, 1, 0], [0, 1, 1], [1, 1, 1], [1, 1, 0]], o: [0, 1, 0], u: [1, 0, 0], v: [0, 0, 1] },
  { n: [0, -1, 0], c: [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]], o: [0, 0, 1], u: [1, 0, 0], v: [0, 0, -1] },
  { n: [0, 0, -1], c: [[0, 0, 0], [0, 1, 0], [1, 1, 0], [1, 0, 0]], o: [1, 1, 0], u: [-1, 0, 0], v: [0, -1, 0] },
  { n: [0, 0, 1], c: [[0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]], o: [0, 1, 1], u: [1, 0, 0], v: [0, -1, 0] },
  { n: [-1, 0, 0], c: [[0, 0, 0], [0, 0, 1], [0, 1, 1], [0, 1, 0]], o: [0, 1, 0], u: [0, 0, 1], v: [0, -1, 0] },
  { n: [1, 0, 0], c: [[1, 0, 0], [1, 1, 0], [1, 1, 1], [1, 0, 1]], o: [1, 1, 1], u: [0, 0, -1], v: [0, -1, 0] }
]

const rad = (d) => d * Math.PI / 180
function rotate (p, r) {
  let [x, y, z] = p
  if (r[0]) { const c = Math.cos(rad(r[0])), s = Math.sin(rad(r[0])); [y, z] = [y * c - z * s, y * s + z * c] }
  if (r[1]) { const c = Math.cos(rad(r[1])), s = Math.sin(rad(r[1])); [x, z] = [x * c + z * s, -x * s + z * c] }
  if (r[2]) { const c = Math.cos(rad(r[2])), s = Math.sin(rad(r[2])); [x, y] = [x * c - y * s, x * s + y * c] }
  return [x, y, z]
}

// ---------- Pintura ----------
function hash (...k) {
  let h = 2166136261
  for (const v of k) { h ^= Math.round(v * 977); h = Math.imul(h, 16777619); h ^= h >>> 13 }
  return ((h >>> 0) % 100000) / 100000
}
const rgb = (c) => typeof c === 'string' ? hex(c) : c
const mix = (a, b, t) => { const A = rgb(a), B = rgb(b); t = Math.max(0, Math.min(1, t)); return A.map((v, i) => v + (B[i] - v) * t) }
const mul = (c, k) => rgb(c).map(v => v * k)

// Pintura base de un material: color (o función p → color), degradado vertical (y0..y1: más claro arriba), borde de cada
// cara (fila de arriba más clara, abajo y lados más oscuros: da volumen de pixel art), cara de abajo más oscura y ruido.
// Como en el pixel art de Lunar, la sombra no es solo más oscura: tira hacia un violeta frío, y la luz hacia un
// amarillo cálido (hue shifting). hue = cuánto (0 = solo oscurecer/aclarar), cool / warm = hacia qué colores.
function shaded (base, o = {}) {
  const { hue = 0.55, cool = '#2c1f63', warm = '#fff0c4', ...rest } = o
  const plain = shadedPlain(base, rest)
  if (!hue) return plain
  const lum = (x) => 0.3 * x[0] + 0.59 * x[1] + 0.11 * x[2]
  return (p, n, t) => {
    const c = plain(p, n, t)
    if (!c) return null
    const b = rgb(typeof base === 'function' ? base(p, n, t) : base)
    if (!b) return c
    // k: cuánto más claro u oscuro ha quedado respecto al color base
    const k = lum(c) / Math.max(1, lum(b))
    const out = k < 1 ? mix(c.slice(0, 3), cool, (1 - k) * hue) : mix(c.slice(0, 3), warm, Math.min(1, (k - 1) * hue * 1.6))
    return c.length === 4 ? [...out, c[3]] : out
  }
}

function shadedPlain (base, o = {}) {
  const { y0 = 0, y1 = 8, grad = 0.2, noise = 0.06, top = 1.12, bottom = 0.8, side = 0.9, under = 0.82, seed = 1 } = o
  return (p, n, t) => {
    let c = rgb(typeof base === 'function' ? base(p, n, t) : base)
    if (!c) return null
    let k = 1 + grad * ((p[1] - y0) / ((y1 - y0) || 1) - 0.5)
    if (n[1] === 0) {
      if (t.v === 0) k *= top
      else if (t.v === t.fh - 1) k *= bottom
      if (t.u === 0 || t.u === t.fw - 1) k *= side
    } else if (n[1] > 0) {
      if (t.u === 0 || t.u === t.fw - 1 || t.v === 0 || t.v === t.fh - 1) k *= 1.06
    } else k *= under
    k *= 1 + noise * (hash(p[0], p[1], p[2], seed) * 2 - 1)
    return c.length === 4 ? [...c.slice(0, 3).map(v => v * k), c[3]] : c.map(v => v * k)
  }
}

// ---------- Construcción ----------
// model: { texel, parts: [{ pivot, rot, parent, anim, cubes: [...] }] }
// Devuelve { parts (para el JSON), atlas: { width, height, data }, quads (para el icono) }
function build (model) {
  const texel = model.texel || 0.5
  const faces = []
  model.parts.forEach((part, pi) => {
    for (const cube of part.cubes) {
      const [x0, y0, z0] = cube.from
      const size = cube.size
      const zeroAxis = size.findIndex(v => v === 0)
      const pivot = cube.pivot || [x0 + size[0] / 2, y0 + size[1] / 2, z0 + size[2] / 2]
      const rot = cube.rot || [0, 0, 0]
      const xf = (p) => { const q = rotate([p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2]], rot); return [q[0] + pivot[0], q[1] + pivot[1], q[2] + pivot[2]] }
      FACES.forEach((f, fi) => {
        const axis = f.n.findIndex(v => v !== 0)
        if (zeroAxis >= 0) { // lámina: una sola cara (la que se pida, o la del lado negativo)
          if (axis !== zeroAxis) return
          if (cube.face ? FACE_NAMES[fi] !== cube.face : f.n[axis] > 0) return
        }
        const O = f.o.map((k, a) => cube.from[a] + k * size[a])
        const extU = Math.abs(f.u[0] * size[0] + f.u[1] * size[1] + f.u[2] * size[2])
        const extV = Math.abs(f.v[0] * size[0] + f.v[1] * size[1] + f.v[2] * size[2])
        const fw = Math.max(1, Math.round(extU / texel))
        const fh = Math.max(1, Math.round(extV / texel))
        const corners = f.c.map(k => k.map((kk, a) => cube.from[a] + kk * size[a]))
        const uvT = corners.map(P => {
          const d = [P[0] - O[0], P[1] - O[1], P[2] - O[2]]
          const du = d[0] * f.u[0] + d[1] * f.u[1] + d[2] * f.u[2]
          const dv = d[0] * f.v[0] + d[1] * f.v[1] + d[2] * f.v[2]
          return [extU ? du / extU * fw : 0, extV ? dv / extV * fh : 0]
        })
        // pintar
        const px = new Uint8ClampedArray(fw * fh * 4)
        let any = false, partial = false, cut = false
        for (let j = 0; j < fh; j++) for (let i = 0; i < fw; i++) {
          const p = O.map((o, a) => o + f.u[a] * (i + 0.5) * extU / fw + f.v[a] * (j + 0.5) * extV / fh)
          let c = cube.paint(p, f.n, { u: i, v: j, fw, fh, face: FACE_NAMES[fi] })
          const k = (j * fw + i) * 4
          if (!c) { cut = true; continue }
          c = rgb(c)
          px[k] = c[0]; px[k + 1] = c[1]; px[k + 2] = c[2]
          px[k + 3] = c.length > 3 ? c[3] : 255
          if (px[k + 3] > 0) any = true
          if (px[k + 3] === 0) cut = true
          else if (px[k + 3] < 255) partial = true
        }
        if (!any) return
        faces.push({ pi, fw, fh, px, corners: corners.map(xf), n: rotate(f.n, rot), uvT, glow: !!cube.glow, mat: cube.mat || 'matte', partial, cut })
      })
    }
  })
  // textura común: estantes, con 1 pixel de margen repetido alrededor de cada cara (para que no se mezclen)
  const W = 512
  const order = faces.map((f, i) => i).sort((a, b) => faces[b].fh - faces[a].fh)
  let x = 0, y = 0, shelf = 0
  for (const i of order) {
    const f = faces[i]
    const w = f.fw + 2, h = f.fh + 2
    if (x + w > W) { x = 0; y += shelf; shelf = 0 }
    f.ax = x + 1; f.ay = y + 1
    x += w; shelf = Math.max(shelf, h)
  }
  let H = 16
  while (H < y + shelf) H *= 2
  const data = new Uint8ClampedArray(W * H * 4)
  for (const f of faces) {
    for (let j = -1; j <= f.fh; j++) for (let i = -1; i <= f.fw; i++) {
      const si = Math.max(0, Math.min(f.fw - 1, i)), sj = Math.max(0, Math.min(f.fh - 1, j))
      const s = (sj * f.fw + si) * 4, d = ((f.ay + j) * W + f.ax + i) * 4
      for (let c = 0; c < 4; c++) data[d + c] = f.px[s + c]
    }
  }
  // JSON por parte: caras agrupadas por material / brillo / transparencia
  const r3 = (v) => Math.round(v * 1000) / 1000
  const parts = model.parts.map((part, pi) => {
    const { cubes, ...rest } = part
    const groups = new Map()
    for (const f of faces.filter(f => f.pi === pi)) {
      const key = f.mat + '|' + f.glow + '|' + f.partial
      if (!groups.has(key)) groups.set(key, { mat: f.mat, g: f.glow ? 1 : 0, t: f.partial ? 1 : 0, cut: 0, q: 1, v: [], n: [], uv: [] })
      const g = groups.get(key)
      if (f.cut) g.cut = 1
      for (let k = 0; k < 4; k++) {
        g.v.push(...f.corners[k].map(r3))
        g.n.push(...f.n.map(r3))
        g.uv.push(Math.round((f.ax + f.uvT[k][0]) / W * 1e6) / 1e6, Math.round((f.ay + f.uvT[k][1]) / H * 1e6) / 1e6)
      }
    }
    return { ...rest, meshes: [...groups.values()] }
  })
  const quads = faces.map(f => ({ pi: f.pi, p: f.corners, n: f.n, glow: f.glow, mat: f.mat, soft: f.partial, uv: f.uvT.map(([u, v]) => [f.ax + u, f.ay + v]) }))
  return { parts, atlas: { width: W, height: H, data }, quads }
}

module.exports = { build, shaded, mix, mul, rgb, hash, rotate }
