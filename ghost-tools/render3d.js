// Renderizador 3D por software (sin tarjeta gráfica) para las vistas previas de los cosméticos y las capas.
// Z-buffer, color por vértice (con la oclusión ambiental ya pintada), materiales (brillo especular, reflejo de metal,
// luz de contorno), texturas, bloom para lo que brilla y sombra suave. Se dibuja a varias veces el tamaño final
// y se reduce, para que los bordes queden suaves.
//
// quad: { p: [[x,y,z] x4], color: [r,g,b,a] | colors: [[r,g,b] x4] + alpha | tex: { data, width, height, uv }, mat, glow }
// mat: { ks (fuerza del brillo), shin (lo concentrado que es), metal (0-1, refleja el entorno), rim (luz de contorno) }
// Coordenadas: y hacia arriba, z hacia la cámara cuando yaw = 0.
const sharp = require('C:/Users/Arturo/Proyectos/mi-launcher/node_modules/sharp')

const norm = (v) => { const l = Math.hypot(v[0], v[1], v[2]) || 1; return [v[0] / l, v[1] / l, v[2] / l] }
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]]
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
function rotY (p, a) { const c = Math.cos(a), s = Math.sin(a); return [p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c] }
function rotX (p, a) { const c = Math.cos(a), s = Math.sin(a); return [p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c] }
const hex = (h) => { const n = parseInt(h.replace('#', ''), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255] }

const MATTE = { ks: 0.04, shin: 8, metal: 0, rim: 0.12 }

async function render (opts) {
  const { quads, width, height, yaw = 30, pitch = 20, ss = 3, margin = 0.08, shadow = true, bloom = true } = opts
  const W = width * ss
  const H = height * ss
  const L = norm(opts.light || [-0.45, 0.75, 0.6]) // luz principal (arriba a la izquierda, por delante)
  const V = [0, 0, 1]
  const Hv = norm([L[0] + V[0], L[1] + V[1], L[2] + V[2]])
  const ya = yaw * Math.PI / 180
  const pa = pitch * Math.PI / 180
  const view = (p) => rotX(rotY(p, ya), pa)

  const tq = quads.map(q => {
    const pts = q.p.map(view)
    let n = norm(cross(sub(pts[1], pts[0]), sub(pts[2], pts[0])))
    let vns = q.vn ? q.vn.map(view) : null // normales suaves por vértice (mallas)
    if (n[2] < 0) { // la cara que mira a la cámara
      n = [-n[0], -n[1], -n[2]]
      if (vns) vns = vns.map(v => [-v[0], -v[1], -v[2]])
    }
    return { ...q, pts, n, vns }
  })
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity, minZ = Infinity, maxZ = -Infinity
  for (const q of tq) for (const p of q.pts) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]); minZ = Math.min(minZ, p[2]); maxZ = Math.max(maxZ, p[2]) }
  const depth = Math.max(maxX - minX, maxY - minY) * 4 // perspectiva suave
  const persp = (p) => { const f = depth / (depth - (p[2] - (minZ + maxZ) / 2)); return [p[0] * f, p[1] * f, p[2]] }
  for (const q of tq) q.sp = q.pts.map(persp)
  minX = Infinity; maxX = -Infinity; minY = Infinity; maxY = -Infinity
  for (const q of tq) for (const p of q.sp) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]) }
  const scale = Math.min(W * (1 - margin * 2) / (maxX - minX), H * (1 - margin * 2) * (shadow ? 0.94 : 1) / (maxY - minY))
  const ox = W / 2 - (minX + maxX) / 2 * scale
  const oy = H / 2 + (minY + maxY) / 2 * scale - (shadow ? H * 0.02 : 0)
  for (const q of tq) q.sc = q.sp.map(p => [ox + p[0] * scale, oy - p[1] * scale, p[2]])

  const color = new Float32Array(W * H * 4)
  const glowBuf = new Float32Array(W * H * 3)
  const zbuf = new Float32Array(W * H).fill(-Infinity)

  // Luz de cada cara según su material: difusa + cielo + brillo especular + reflejo (metal) + contorno
  const lightOf = (q, n = q.n) => {
    const m = q.mat || MATTE
    if (q.glow) return { diff: 1.1, spec: 0, env: 0, rim: 0 }
    const d = Math.max(0, dot(n, L))
    const sky = 0.5 + 0.5 * n[1]
    const diff = Math.min(1.15, (q.light ?? 1) * (0.5 + 0.42 * d + 0.16 * sky) + (m.lift || 0)) // lift: materiales claros que no deben verse grises
    const spec = (m.ks || 0) * Math.pow(Math.max(0, dot(n, Hv)), m.shin || 8)
    // reflejo del entorno: arriba claro (cielo de estudio), abajo oscuro
    const r = [2 * n[2] * n[0], 2 * n[2] * n[1] , 2 * n[2] * n[2] - 1]
    const env = (m.metal || 0) * (0.25 + 0.75 * Math.max(0, Math.min(1, 0.55 + r[1] * 0.6)))
    const rim = (m.rim ?? 0.12) * Math.pow(1 - Math.abs(n[2]), 2)
    return { diff, spec, env, rim }
  }

  const drawTri = (q, a, b, c, ia, ib, ic, blend) => {
    const x0 = Math.max(0, Math.floor(Math.min(a[0], b[0], c[0])))
    const x1 = Math.min(W - 1, Math.ceil(Math.max(a[0], b[0], c[0])))
    const y0 = Math.max(0, Math.floor(Math.min(a[1], b[1], c[1])))
    const y1 = Math.min(H - 1, Math.ceil(Math.max(a[1], b[1], c[1])))
    const area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
    if (Math.abs(area) < 1e-6) return
    let lt = q.lt
    const vns = q.vns
    const tex = q.tex
    const cols = q.cols
    const metal = q.mat?.metal || 0
    for (let y = y0; y <= y1; y++) {
      for (let x = x0; x <= x1; x++) {
        const px = x + 0.5, py = y + 0.5
        const w0 = ((b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)) / area
        const w1 = ((c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)) / area
        const w2 = 1 - w0 - w1
        if (w0 < -1e-4 || w1 < -1e-4 || w2 < -1e-4) continue
        const z = w0 * a[2] + w1 * b[2] + w2 * c[2]
        const i = y * W + x
        if (z <= zbuf[i]) continue
        if (vns) { // sombreado por píxel con la normal interpolada
          const A = vns[ia], B = vns[ib], C = vns[ic]
          lt = lightOf(q, norm([w0 * A[0] + w1 * B[0] + w2 * C[0], w0 * A[1] + w1 * B[1] + w2 * C[1], w0 * A[2] + w1 * B[2] + w2 * C[2]]))
        }
        let r, g, bl, al
        if (tex) {
          const u = w0 * ia[0] + w1 * ib[0] + w2 * ic[0]
          const v = w0 * ia[1] + w1 * ib[1] + w2 * ic[1]
          const tx = Math.min(tex.width - 1, Math.max(0, Math.floor(u)))
          const ty = Math.min(tex.height - 1, Math.max(0, Math.floor(v)))
          const ti = (ty * tex.width + tx) * 4
          r = tex.data[ti]; g = tex.data[ti + 1]; bl = tex.data[ti + 2]
          if (tex.data[ti + 3] < (tex.soft ? 8 : 128)) continue
          al = tex.soft ? tex.data[ti + 3] / 255 : 1
        } else {
          const A = cols[ia], B = cols[ib], C = cols[ic]
          r = w0 * A[0] + w1 * B[0] + w2 * C[0]
          g = w0 * A[1] + w1 * B[1] + w2 * C[1]
          bl = w0 * A[2] + w1 * B[2] + w2 * C[2]
          al = q.alpha
        }
        // metal: el color propio sale sobre todo del reflejo; lo demás, de la luz difusa
        const k = lt.diff * (1 - metal * 0.55) + lt.env * 1.1
        const sp = lt.spec * 255
        const tint = metal ? 0.65 : 0 // el brillo del metal toma su color
        r = r * k + sp * (1 - tint + tint * r / 255) + lt.rim * 200
        g = g * k + sp * (1 - tint + tint * g / 255) + lt.rim * 215
        bl = bl * k + sp * (1 - tint + tint * bl / 255) + lt.rim * 255
        const o = i * 4
        if (!blend) {
          zbuf[i] = z
          color[o] = r; color[o + 1] = g; color[o + 2] = bl; color[o + 3] = al
        } else {
          const iv = 1 - al
          color[o] = r * al + color[o] * iv
          color[o + 1] = g * al + color[o + 1] * iv
          color[o + 2] = bl * al + color[o + 2] * iv
          color[o + 3] = al + color[o + 3] * iv
        }
        if (q.glow) { glowBuf[i * 3] += r * al; glowBuf[i * 3 + 1] += g * al; glowBuf[i * 3 + 2] += bl * al }
      }
    }
  }
  const drawQuad = (q, blend) => {
    q.lt = lightOf(q)
    const [a, b, c, d] = q.sc
    if (q.sc.length === 3) { // triángulo de una malla
      q.cols = q.colors || [q.color, q.color, q.color]
      q.alpha = q.alpha ?? (q.color?.[3] ?? 1)
      drawTri(q, a, b, c, 0, 1, 2, blend)
    } else if (q.tex) {
      const uv = q.tex.uv
      drawTri(q, a, b, c, uv[0], uv[1], uv[2], blend)
      drawTri(q, a, c, d, uv[0], uv[2], uv[3], blend)
    } else {
      q.cols = q.colors || [q.color, q.color, q.color, q.color]
      q.alpha = q.alpha ?? (q.color?.[3] ?? 1)
      drawTri(q, a, b, c, 0, 1, 2, blend)
      drawTri(q, a, c, d, 0, 2, 3, blend)
    }
  }
  const isOpaque = (q) => q.tex ? !q.tex.soft : (q.alpha ?? q.color?.[3] ?? 1) >= 0.99
  for (const q of tq) if (isOpaque(q)) drawQuad(q, false)
  const trans = tq.filter(q => !isOpaque(q))
  trans.sort((p, q) => (p.sc.reduce((s, v) => s + v[2], 0) - q.sc.reduce((s, v) => s + v[2], 0)))
  for (const q of trans) drawQuad(q, true)

  const out = Buffer.alloc(W * H * 4)
  for (let i = 0; i < W * H; i++) {
    for (let c = 0; c < 3; c++) out[i * 4 + c] = Math.max(0, Math.min(255, Math.round(color[i * 4 + c])))
    out[i * 4 + 3] = Math.max(0, Math.min(255, Math.round(color[i * 4 + 3] * 255)))
  }
  const layers = []
  if (shadow) {
    // sombra suave: la silueta, difuminada, un poco desplazada hacia abajo
    const sh = Buffer.alloc(W * H * 4)
    for (let i = 0; i < W * H; i++) sh[i * 4 + 3] = Math.round(out[i * 4 + 3] * 0.5)
    const blurred = await sharp(sh, { raw: { width: W, height: H, channels: 4 } }).blur(Math.max(1, ss * 4)).png().toBuffer()
    layers.push({ input: blurred, left: Math.round(ss * 3), top: Math.round(ss * 5) })
  }
  layers.push({ input: await sharp(out, { raw: { width: W, height: H, channels: 4 } }).png().toBuffer(), left: 0, top: 0 })
  if (bloom && glowBuf.some(v => v > 0)) {
    // bloom: lo que brilla, difuminado y sumado encima (solo alrededor de las partes que brillan)
    const gb = Buffer.alloc(W * H * 4)
    for (let i = 0; i < W * H; i++) {
      const r = glowBuf[i * 3], g = glowBuf[i * 3 + 1], b = glowBuf[i * 3 + 2]
      const m = Math.max(r, g, b)
      if (m <= 0) continue
      gb[i * 4] = Math.min(255, r); gb[i * 4 + 1] = Math.min(255, g); gb[i * 4 + 2] = Math.min(255, b); gb[i * 4 + 3] = Math.min(255, m * 0.9)
    }
    const glow = await sharp(gb, { raw: { width: W, height: H, channels: 4 } }).blur(ss * 3.5).png().toBuffer()
    layers.push({ input: glow, left: 0, top: 0, blend: 'screen' })
  }
  const full = await sharp({ create: { width: W, height: H, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } }).composite(layers).png().toBuffer()
  return sharp(full).resize(width, height, { kernel: 'lanczos3' }).png().toBuffer()
}

// Orden de las caras y de sus esquinas (el mismo que usan el mod y el launcher para los colores por vértice).
// Cada esquina está en fracciones del bloque (0 = mínimo, 1 = máximo).
const FACES = [
  { n: [0, 1, 0], c: [[0, 1, 0], [0, 1, 1], [1, 1, 1], [1, 1, 0]] },
  { n: [0, -1, 0], c: [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]] },
  { n: [0, 0, -1], c: [[0, 0, 0], [0, 1, 0], [1, 1, 0], [1, 0, 0]] },
  { n: [0, 0, 1], c: [[0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]] },
  { n: [-1, 0, 0], c: [[0, 0, 0], [0, 0, 1], [0, 1, 1], [0, 1, 0]] },
  { n: [1, 0, 0], c: [[1, 0, 0], [1, 1, 0], [1, 1, 1], [1, 0, 1]] }
]

// Las 6 caras de un bloque (min, tamaño) con un color o con colores por esquina (24 colores en el orden de FACES)
function boxQuads (x, y, z, w, h, d, color, extra = {}) {
  const c = typeof color === 'string' ? [...hex(color), 1] : color
  return FACES.map((f, fi) => ({
    p: f.c.map(([a, b, e]) => [x + a * w, y + b * h, z + e * d]),
    color: c,
    ...(extra.corner ? { colors: extra.corner.slice(fi * 4, fi * 4 + 4), alpha: c[3] ?? 1 } : {}),
    ...extra
  }))
}

module.exports = { render, boxQuads, hex, FACES }
