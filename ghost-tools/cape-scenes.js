// Escenas de las capas de temporada (Halloween y Navidad), estilo pixel art.
// Cada escena se dibuja a 80x128 (una cara de la capa a 8x) y luego se pixela a 40x64 para que parezca pixel art.
// side: 'front' (la que se ve por detrás, con el fantasma) o 'inner' (la de dentro, sin fantasma).
// t: de 0 a 1 a lo largo de la animación (tiene que dar la vuelta sin saltos).

const W = 80
const H = 128
const TAU = Math.PI * 2
const n = (x) => x.toFixed(2)

// Medidas del logo a 128 px (ver make-cape.js)
const LOGO = 128

function bat (x, y, flap, color, s = 1) {
  const w = 1 - flap * 0.6
  return `<g transform="translate(${n(x)} ${n(y)}) scale(${s} ${n(s * w)})">
    <path d="M-7,0 L-4,-2.5 L-2,0 L-1,-2 L0,-1 L1,-2 L2,0 L4,-2.5 L7,0 L4,1 L2,3 L0,1.5 L-2,3 L-4,1 Z" fill="${color}"/></g>`
}

function cross (x, y, s, color) {
  return `<g transform="translate(${x} ${y}) scale(${s})" fill="${color}"><rect x="-1.5" y="-6" width="3" height="16"/><rect x="-5" y="-2" width="10" height="3"/></g>`
}

// Copo de nieve en bloques de 2 px (para que se vea nítido al pixelar): brazos rectos y en diagonal
function flake (x, y, r, color, center) {
  const L = Math.max(2, Math.round(r / 2))
  const cx = Math.round(x / 2) * 2
  const cy = Math.round(y / 2) * 2
  const px = (i, j) => `<rect x="${cx + i * 2}" y="${cy + j * 2}" width="2" height="2"/>`
  let out = px(0, 0)
  for (let k = 1; k <= L; k++) out += px(k, 0) + px(-k, 0) + px(0, k) + px(0, -k)
  for (let k = 1; k < L; k++) out += px(k, k) + px(-k, k) + px(k, -k) + px(-k, -k)
  if (L >= 3) out += px(L - 1, 1) + px(L - 1, -1) + px(1 - L, 1) + px(1 - L, -1) + px(1, L - 1) + px(-1, L - 1) + px(1, 1 - L) + px(-1, 1 - L)
  return `<g fill="${color}">${out}</g>${center ? `<rect x="${cx}" y="${cy}" width="2" height="2" fill="${center}"/>` : ''}`
}

function sparkle (x, y, s, color, op) {
  return `<g transform="translate(${n(x)} ${n(y)}) scale(${n(s)})" fill="${color}" opacity="${n(op)}"><rect x="-0.8" y="-3" width="1.6" height="6"/><rect x="-3" y="-0.8" width="6" height="1.6"/></g>`
}

// Gorro de Papá Noel en medidas del logo de 128 px; sway mueve la punta y el pompón
function santaHat (sway) {
  const tipX = 120 + sway * 4
  const tipY = 4 + Math.abs(sway) * 2
  return `<g transform="rotate(-8 70 16)">
    <path d="M34,22 C38,-10 70,-26 100,-16 C112,-12 ${tipX - 6},${tipY - 10} ${tipX},${tipY} C108,6 104,12 104,20 Z" fill="#d81f2c"/>
    <path d="M70,-18 C88,-22 104,-14 ${tipX},${tipY} C110,4 106,8 104,14 C98,-2 86,-12 70,-18 Z" fill="#000" opacity=".22"/>
    <path d="M44,8 C52,-8 64,-14 74,-15" stroke="#ff5a5f" stroke-width="4" fill="none" opacity=".5"/>
    <rect x="26" y="10" width="84" height="20" rx="10" fill="#f6f2f8"/>
    <rect x="26" y="22" width="84" height="8" rx="4" fill="#d9d0e2"/>
    <circle cx="${tipX + 2}" cy="${tipY + 2}" r="11" fill="#f6f2f8"/>
    <circle cx="${tipX + 5}" cy="${tipY + 5}" r="6" fill="#d9d0e2" opacity=".8"/>
  </g>`
}

// ---------------- Halloween ----------------
function halloween (t, side, logoData) {
  const front = side === 'front'
  const flick = (i) => Math.sin(t * TAU * 3 + i * 1.7) * 0.5 + Math.sin(t * TAU * 5 + i * 0.9) * 0.5
  let s = ''
  // Fondo negro con brillo naranja que sube desde abajo
  s += `<rect width="${W}" height="${H}" fill="url(#hbg)"/>`
  s += `<rect width="${W}" height="${H}" fill="url(#hfire)" opacity="${n(0.75 + 0.15 * Math.sin(t * TAU * 2))}"/>`
  // Luna llena (grande y en el centro por dentro)
  const mx = front ? 58 : 40
  const my = front ? 24 : 30
  const mr = front ? 13 : 16
  s += `<ellipse cx="40" cy="34" rx="52" ry="34" fill="#4a1805" opacity=".3" filter="url(#blur)"/>`
  s += `<circle cx="${mx}" cy="${my}" r="${mr * 2}" fill="#ff7a1a" opacity="${n(0.16 + 0.06 * Math.sin(t * TAU))}"/>`
  s += `<circle cx="${mx}" cy="${my}" r="${mr}" fill="url(#hmoon)"/>`
  s += `<circle cx="${mx - mr * 0.35}" cy="${my - mr * 0.25}" r="${mr * 0.22}" fill="#c4470b" opacity=".45"/>
    <circle cx="${mx + mr * 0.3}" cy="${my + mr * 0.35}" r="${mr * 0.16}" fill="#c4470b" opacity=".4"/>
    <circle cx="${mx + mr * 0.4}" cy="${my - mr * 0.4}" r="${mr * 0.1}" fill="#c4470b" opacity=".35"/>`
  // Murciélago delante de la luna
  s += bat(mx, my + 1 + Math.sin(t * TAU) * 1.5, (Math.sin(t * TAU * 4) + 1) / 2, '#140603', front ? 1.1 : 1.5)
  // Árboles secos a los lados
  const tree = (lines) => lines.map(([w, pts]) => `<polyline points="${pts}" stroke="#0b0402" stroke-width="${w}" fill="none" stroke-linecap="round" stroke-linejoin="round"/>`).join('')
  s += tree([[5, '1,80 4,52 8,34 14,20'], [3.5, '4,52 -2,42'], [3, '8,34 4,20 5,10'], [3, '8,34 18,30 26,22'], [2.5, '14,20 20,10'], [2, '18,30 22,36'], [2, '26,22 31,20'], [2, '5,10 1,4']])
  s += tree([[4.5, '80,70 75,48 71,36'], [3, '75,48 81,40'], [2.5, '71,36 66,32 62,34'], [2.5, '71,36 73,26 77,20'], [2, '66,32 64,26']])
  // Cruces de cementerio
  s += cross(front ? 68 : 62, front ? 86 : 82, front ? 0.9 : 1.4, '#0b0402')
  s += cross(front ? 10 : 18, front ? 96 : 92, front ? 0.65 : 0.9, '#0b0402')
  // Humo naranja que sube
  for (const [x0, ph, r] of [[12, 0, 9], [64, 0.35, 8], [36, 0.6, 10], [24, 0.8, 7]]) {
    const p = (t + ph) % 1
    const y = 118 - p * 70
    const x = x0 + Math.sin((p + ph) * TAU) * 5
    s += `<ellipse cx="${n(x)}" cy="${n(y)}" rx="${n(r * (0.8 + p * 0.6))}" ry="${n(r * 0.7)}" fill="#a8400f" opacity="${n(Math.sin(p * Math.PI) * 0.35)}" filter="url(#blur)"/>`
  }
  // Murciélagos que cruzan
  for (const [ph, y0, sc] of [[0, 46, 0.9], [0.5, 60, 0.7], [0.25, 12, 0.6]]) {
    const p = (t + ph) % 1
    const x = -10 + p * (W + 20)
    const y = y0 + Math.sin(p * TAU * 2) * 4
    s += bat(x, y, (Math.sin((t * 6 + ph) * TAU) + 1) / 2, '#140603', sc)
  }
  // Fantasma (blanco, como el logo) que sube y baja, con remolinos naranjas
  if (front) {
    const bob = Math.sin(t * TAU) * 3
    const gs = 58
    const gx = 40 - gs / 2 - 2
    const gy = 44 + bob
    s += `<ellipse cx="38" cy="${n(gy + gs / 2)}" rx="38" ry="36" fill="url(#hghost)"/>`
    const dash = (t * 40).toFixed(1)
    s += `<path d="M10,${n(gy + 18)} q-6,10 2,16 q8,4 6,-6" stroke="#ff7a1a" stroke-width="2" fill="none" opacity=".55" stroke-dasharray="10 6" stroke-dashoffset="${dash}"/>`
    s += `<path d="M66,${n(gy + 40)} q8,4 4,12 q-4,6 -8,0" stroke="#ff7a1a" stroke-width="2" fill="none" opacity=".5" stroke-dasharray="10 6" stroke-dashoffset="${-dash}"/>`
    s += `<image x="${n(gx)}" y="${n(gy)}" width="${gs}" height="${gs}" href="${logoData}" filter="url(#hwarm)"/>`
  } else {
    // Por dentro, una cruz grande en el centro entre el humo
    s += cross(40, 68, 2, '#0b0402')
  }
  // Llamas abajo
  for (let i = 0; i < 11; i++) {
    const x = i * 8 - 2
    const h = 16 + 7 * flick(i) + (i % 3) * 3
    const w = 7 + (i % 2) * 2
    s += `<path d="M${x - w},${H} Q${n(x - w * 0.3)},${n(H - h * 0.6)} ${n(x + Math.sin(t * TAU * 2 + i) * 2)},${n(H - h)} Q${n(x + w * 0.4)},${n(H - h * 0.5)} ${x + w},${H} Z" fill="#ff6a0a"/>`
    s += `<path d="M${x - w * 0.5},${H} Q${n(x - w * 0.1)},${n(H - h * 0.35)} ${n(x + Math.sin(t * TAU * 2 + i) * 1.5)},${n(H - h * 0.6)} Q${n(x + w * 0.2)},${n(H - h * 0.3)} ${x + w * 0.5},${H} Z" fill="#ffb23a"/>`
  }
  // Calabaza tallada brillando entre las llamas
  const lx = front ? 40 : 40
  const ly = front ? 110 : 108
  const lg = 0.75 + 0.25 * Math.sin(t * TAU * 4)
  s += `<ellipse cx="${lx}" cy="${ly}" rx="17" ry="9" fill="#120502"/>`
  s += `<g fill="#ffc23a" opacity="${n(lg)}" filter="url(#glow)">
    <path d="M${lx - 11},${ly - 2} L${lx - 5},${ly - 2} L${lx - 8},${ly - 7} Z M${lx + 5},${ly - 2} L${lx + 11},${ly - 2} L${lx + 8},${ly - 7} Z"/>
    <path d="M${lx - 12},${ly + 1} L${lx - 8},${ly + 5} L${lx - 5},${ly + 3} L${lx - 2},${ly + 6} L${lx + 2},${ly + 3} L${lx + 5},${ly + 6} L${lx + 8},${ly + 3} L${lx + 12},${ly + 1} L${lx + 9},${ly + 7} L${lx - 9},${ly + 7} Z"/></g>`
  // Arriba: borde naranja que gotea, con gotas que caen
  const drips = [[4, 6], [12, 12], [20, 5], [27, 9], [35, 15], [44, 6], [52, 10], [60, 4], [67, 13], [75, 7]]
  s += `<rect x="0" y="0" width="${W}" height="6" fill="url(#hdrip)"/>`
  drips.forEach(([x, len], i) => {
    const grow = len + Math.sin(t * TAU + i) * 1.5
    s += `<path d="M${x - 2.5},5 L${x + 2.5},5 L${x + 1.5},${n(5 + grow)} Q${x},${n(7 + grow)} ${x - 1.5},${n(5 + grow)} Z" fill="url(#hdrip)"/>`
    if (i % 3 === 1) {
      const p = (t * 2 + i * 0.13) % 1
      s += `<rect x="${x - 1}" y="${n(6 + grow + p * 30)}" width="2" height="3" fill="#ff7a1a" opacity="${n(1 - p)}"/>`
    }
  })
  // Bordes de los lados y de abajo, en zigzag
  s += `<rect x="0" y="0" width="2" height="${H}" fill="#ff7a1a" opacity=".85"/><rect x="${W - 2}" y="0" width="2" height="${H}" fill="#ff7a1a" opacity=".85"/>`
  let zig = `M0,${H} L0,${H - 4}`
  for (let x = 0; x <= W; x += 6) zig += ` L${x + 3},${H - 1} L${Math.min(W, x + 6)},${H - 5}`
  s += `<path d="${zig} L${W},${H} Z" fill="#ff8a1f"/>`
  // Viñeta
  s += `<rect width="${W}" height="${H}" fill="url(#vig)"/>`

  const defs = `
    <radialGradient id="hbg" cx="50%" cy="45%" r="70%"><stop offset="0%" stop-color="#220b03"/><stop offset="55%" stop-color="#0c0402"/><stop offset="100%" stop-color="#060201"/></radialGradient>
    <linearGradient id="hfire" x1="0" y1="0" x2="0" y2="1"><stop offset="60%" stop-color="#ff5a00" stop-opacity="0"/><stop offset="100%" stop-color="#ff5a00" stop-opacity=".45"/></linearGradient>
    <radialGradient id="hmoon" cx="40%" cy="35%" r="70%"><stop offset="0%" stop-color="#ffc066"/><stop offset="60%" stop-color="#ff8a1f"/><stop offset="100%" stop-color="#d9560c"/></radialGradient>
    <radialGradient id="hghost" cx="50%" cy="50%" r="50%"><stop offset="0%" stop-color="#ff7a1a" stop-opacity=".35"/><stop offset="100%" stop-color="#ff7a1a" stop-opacity="0"/></radialGradient>
    <linearGradient id="hdrip" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#ff9a2e"/><stop offset="100%" stop-color="#e2550c"/></linearGradient>
    <filter id="hwarm"><feColorMatrix type="matrix" values="1 0 0 0 0.02  0 0.93 0 0 0  0 0 0.86 0 0  0 0 0 1 0"/></filter>`
  return { body: s, defs }
}

// ---------------- Navidad ----------------
function christmas (t, side, logoData) {
  const front = side === 'front'
  let s = `<rect width="${W}" height="${H}" fill="url(#xbg)"/>`
  // Copos grandes que brillan y giran despacio
  const flakes = front
    ? [[13, 28, 7, 0], [69, 76, 6, 0.4], [10, 80, 5, 0.7]]
    : [[22, 30, 9, 0], [58, 24, 7, 0.3], [54, 60, 8, 0.6], [20, 70, 7, 0.8]]
  for (const [x, y, r, ph] of flakes) {
    const tw = 0.7 + 0.3 * Math.sin((t + ph) * TAU)
    s += `<g opacity="${n(tw)}">${flake(x, y, r, '#f4eef8', '#e0303c')}</g>`
  }
  // Destellos
  for (const [x, y, ph] of [[28, 18, 0], [52, 30, 0.5], [74, 44, 0.25], [6, 50, 0.75], [30, 90, 0.4], [50, 84, 0.9]]) {
    const p = Math.sin((t * 2 + ph) * TAU) * 0.5 + 0.5
    s += sparkle(x, y, 0.5 + p * 0.5, '#f4eef8', 0.3 + p * 0.7)
  }
  // Nieve que cae
  for (let i = 0; i < 16; i++) {
    const ph = (i * 0.618) % 1
    const p = (t + ph) % 1
    const x = (i * 23 + 7) % W + Math.sin((p + ph) * TAU * 2) * 2
    const y = 6 + p * (H - 10)
    const sz = i % 3 === 0 ? 2.5 : 1.6
    s += `<rect x="${n(x)}" y="${n(y)}" width="${sz}" height="${sz}" fill="#ffffff" opacity="${n(0.5 + 0.4 * Math.sin(p * Math.PI))}"/>`
  }
  // Fantasma con gorro
  if (front) {
    const bob = Math.sin(t * TAU) * 3
    const gs = 54
    const gx = 40 - gs / 2 - 3
    const gy = 42 + bob
    const k = gs / LOGO
    s += `<ellipse cx="38" cy="${n(gy + gs / 2)}" rx="34" ry="32" fill="url(#xghost)"/>`
    s += `<image x="${n(gx)}" y="${n(gy)}" width="${gs}" height="${gs}" href="${logoData}"/>`
    s += `<g transform="translate(${n(gx)} ${n(gy)}) scale(${k.toFixed(4)})">${santaHat(Math.sin(t * TAU))}</g>`
  }
  // Bastón de caramelo y regalos
  const cane = (x, y, sc, rot) => `<g transform="translate(${x} ${y}) rotate(${rot}) scale(${sc})">
    <path d="M0,24 L0,4 Q0,-4 6,-4 Q12,-4 12,2" stroke="#f6f2f8" stroke-width="4.5" fill="none" stroke-linecap="round"/>
    <path d="M0,24 L0,4 Q0,-4 6,-4 Q12,-4 12,2" stroke="#d81f2c" stroke-width="4.5" fill="none" stroke-dasharray="3 3" stroke-dashoffset="${n(-t * 12)}"/></g>`
  const gift = (x, y, w, h, color) => `<g transform="translate(${x} ${y})">
    <rect x="0" y="0" width="${w}" height="${h}" fill="${color}"/><rect x="${w * 0.65}" y="0" width="${w * 0.35}" height="${h}" fill="#000" opacity=".2"/>
    <rect x="0" y="0" width="${w}" height="3" fill="#000" opacity=".15"/>
    <rect x="${w / 2 - 1.5}" y="0" width="3" height="${h}" fill="#f6f2f8"/><rect x="0" y="${h * 0.4}" width="${w}" height="3" fill="#f6f2f8"/>
    <path d="M${w / 2},0 q-7,-8 -8,-2 q0,4 8,2 q7,-8 8,-2 q0,4 -8,2" stroke="#f6f2f8" stroke-width="2" fill="none"/></g>`
  if (front) {
    s += cane(9, 102, 0.8, -12)
    s += gift(55, 102, 17, 13, '#c81d2a')
  } else {
    s += gift(8, 98, 18, 15, '#c81d2a')
    s += gift(26, 104, 14, 11, '#a3141f')
    s += cane(62, 76, 1.3, 14)
  }
  // Nieve acumulada abajo
  let drift = `M0,${H} L0,114`
  for (let x = 0; x <= W; x += 8) drift += ` Q${x + 4},${108 + ((x / 8) % 3) * 2} ${x + 8},${114 + ((x / 8) % 2) * 2}`
  s += `<path d="${drift} L${W},${H} Z" fill="#f4eef8"/>`
  s += `<rect x="0" y="122" width="${W}" height="6" fill="#d9d0e6"/>`
  // Arriba: nieve con carámbanos
  const icicles = [[3, 7], [10, 13], [17, 6], [24, 10], [31, 15], [38, 7], [45, 11], [52, 5], [59, 14], [66, 8], [73, 11], [79, 6]]
  s += `<rect x="0" y="0" width="${W}" height="6" fill="#f4eef8"/>`
  for (const [x, len] of icicles) s += `<path d="M${x - 3.5},5 L${x + 3.5},5 L${x},${5 + len} Z" fill="#f4eef8"/><path d="M${x},5 L${x + 3.5},5 L${x},${5 + len} Z" fill="#d9d0e6"/>`
  // Una gota de hielo que brilla recorriendo los carámbanos
  const gi = Math.floor(t * icicles.length)
  s += sparkle(icicles[gi][0], 5 + icicles[gi][1] * 0.6, 0.6, '#ffffff', 0.9)
  // Bordes de los lados
  s += `<rect x="0" y="0" width="2" height="${H}" fill="#e0303c" opacity=".7"/><rect x="${W - 2}" y="0" width="2" height="${H}" fill="#e0303c" opacity=".7"/>`
  s += `<rect width="${W}" height="${H}" fill="url(#vig)"/>`
  const defs = `
    <radialGradient id="xbg" cx="50%" cy="45%" r="70%"><stop offset="0%" stop-color="#9a1424"/><stop offset="55%" stop-color="#600a14"/><stop offset="100%" stop-color="#2a0308"/></radialGradient>
    <radialGradient id="xghost" cx="50%" cy="50%" r="50%"><stop offset="0%" stop-color="#ffffff" stop-opacity=".18"/><stop offset="100%" stop-color="#ffffff" stop-opacity="0"/></radialGradient>`
  return { body: s, defs }
}

// ---------------- Capas minimalistas (sin fantasma) ----------------
// Estrellas fijas (siempre en el mismo sitio) que titilan
function stars (t, list, color = '#ffffff') {
  return list.map(([x, y, ph], i) => {
    const p = Math.sin((t * 2 + ph) * TAU) * 0.5 + 0.5
    return `<rect x="${x}" y="${y}" width="${i % 4 === 0 ? 2 : 1.4}" height="${i % 4 === 0 ? 2 : 1.4}" fill="${color}" opacity="${n(0.25 + p * 0.75)}"/>`
  }).join('')
}
const STARS = [[8, 10, 0], [22, 26, 0.3], [60, 8, 0.6], [70, 30, 0.1], [36, 14, 0.8], [52, 40, 0.45], [12, 48, 0.7], [66, 54, 0.2], [30, 60, 0.9], [74, 70, 0.55], [6, 78, 0.35], [44, 86, 0.65]]

// Aurora: cintas de luz verde, turquesa y violeta que ondean sobre montañas
function aurora (t) {
  let s = `<rect width="${W}" height="${H}" fill="url(#abg)"/>` + stars(t, STARS)
  const bands = [[42, 9, '#3dffb0', 0], [56, 7, '#3fd8ff', 0.33], [34, 6, '#a77bff', 0.66]]
  for (const [y0, amp, color, ph] of bands) {
    let d = ''
    for (let x = -4; x <= W + 4; x += 4) {
      const y = y0 + Math.sin(x * 0.07 + (t + ph) * TAU) * amp + Math.sin(x * 0.03 - t * TAU) * 3
      d += (x === -4 ? 'M' : ' L') + `${x},${n(y)}`
    }
    s += `<path d="${d}" stroke="${color}" stroke-width="14" fill="none" opacity=".32" filter="url(#blur)"/>`
    s += `<path d="${d}" stroke="${color}" stroke-width="3" fill="none" opacity=".75" filter="url(#soft)"/>`
  }
  s += `<path d="M0,128 L0,104 L12,94 L22,100 L34,86 L46,98 L56,92 L68,100 L80,92 L80,128 Z" fill="#03050c"/>`
  s += `<path d="M34,86 L38,90 L33,91 Z M56,92 L59,95 L54,95 Z" fill="#c9d6ff" opacity=".35"/>`
  return { body: s, defs: '<linearGradient id="abg" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#03040d"/><stop offset="60%" stop-color="#081530"/><stop offset="100%" stop-color="#0b2a3c"/></linearGradient>' }
}

// Luna: negro mate, una luna creciente, tres estrellas y a veces una estrella fugaz
function moon (t) {
  let s = `<rect width="${W}" height="${H}" fill="#0b0b10"/>`
  s += `<circle cx="40" cy="48" r="${n(24 + Math.sin(t * TAU) * 1.5)}" fill="#e9e3cf" opacity=".06"/>`
  s += '<circle cx="40" cy="48" r="15" fill="#efe8d4"/><circle cx="47" cy="43" r="13.5" fill="#0b0b10"/>'
  s += stars(t, [[18, 22, 0], [62, 70, 0.4], [24, 80, 0.75], [66, 20, 0.2], [12, 58, 0.55]], '#efe8d4')
  for (const [x, y, ph] of [[60, 30, 0], [20, 40, 0.5]]) {
    const p = Math.sin((t * 2 + ph) * TAU) * 0.5 + 0.5
    s += sparkle(x, y, 0.6 + p * 0.5, '#efe8d4', 0.5 + p * 0.5)
  }
  // Estrella fugaz en la última parte del bucle
  if (t > 0.55 && t < 0.85) {
    const p = (t - 0.55) / 0.3
    const x = 70 - p * 50
    const y = 8 + p * 22
    s += `<line x1="${n(x)}" y1="${n(y)}" x2="${n(x + 12)}" y2="${n(y - 5)}" stroke="#ffffff" stroke-width="1.4" opacity="${n(Math.sin(p * Math.PI))}" stroke-linecap="round"/>`
  }
  s += '<rect x="14" y="104" width="52" height="1.5" fill="#efe8d4" opacity=".35"/>'
  s += '<rect x="6" y="6" width="68" height="116" fill="none" stroke="#efe8d4" stroke-opacity=".18" stroke-width="1.2"/>'
  return { body: s, defs: '' }
}

// Sakura: rama de cerezo sobre fondo crema, sol rojo y pétalos que caen
function sakura (t) {
  let s = `<rect width="${W}" height="${H}" fill="url(#sbg)"/>`
  s += '<circle cx="54" cy="40" r="14" fill="#e4473d"/>'
  s += '<polyline points="-2,20 14,28 30,32 44,44 58,50 70,62" stroke="#3a2828" stroke-width="3.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/>'
  s += '<polyline points="30,32 34,22 40,16" stroke="#3a2828" stroke-width="2" fill="none" stroke-linecap="round"/>'
  s += '<polyline points="44,44 42,56 46,64" stroke="#3a2828" stroke-width="1.8" fill="none" stroke-linecap="round"/>'
  s += '<polyline points="14,28 10,38" stroke="#3a2828" stroke-width="1.6" fill="none" stroke-linecap="round"/>'
  const flower = (x, y, r, c) => {
    let f = ''
    for (let i = 0; i < 5; i++) {
      const a = i * TAU / 5 - Math.PI / 2
      f += `<circle cx="${n(x + Math.cos(a) * r)}" cy="${n(y + Math.sin(a) * r)}" r="${n(r * 0.75)}" fill="${c}"/>`
    }
    return f + `<circle cx="${x}" cy="${y}" r="${n(r * 0.45)}" fill="#d9466f"/>`
  }
  for (const [x, y, r, c] of [[16, 26, 3, '#f7a8c0'], [26, 34, 3.4, '#ffc6d6'], [36, 20, 3, '#f7a8c0'], [41, 15, 2.4, '#ffc6d6'],
    [48, 44, 3.4, '#f7a8c0'], [60, 52, 3, '#ffc6d6'], [68, 62, 3.2, '#f7a8c0'], [44, 60, 2.6, '#ffc6d6'], [10, 38, 2.6, '#ffc6d6']]) s += flower(x, y, r, c)
  for (let i = 0; i < 9; i++) {
    const ph = (i * 0.37) % 1
    const p = (t + ph) % 1
    const x = (i * 17 + 6) % W + Math.sin((p * 2 + ph) * TAU) * 5
    const y = 30 + p * 104
    s += `<ellipse cx="${n(x)}" cy="${n(y)}" rx="2.2" ry="1.3" fill="${i % 2 ? '#f7a8c0' : '#ffc6d6'}" transform="rotate(${n(p * 360 + i * 40)} ${n(x)} ${n(y)})"/>`
  }
  s += '<rect x="0" y="118" width="80" height="10" fill="#efd3d3"/>'
  return { body: s, defs: '<linearGradient id="sbg" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#fbf3ec"/><stop offset="100%" stop-color="#f6dcdc"/></linearGradient>' }
}

// Atardecer retro: sol con franjas y una rejilla que avanza
function sunset (t) {
  let s = `<rect width="${W}" height="88" fill="url(#ubg)"/>`
  s += '<circle cx="40" cy="70" r="24" fill="url(#usun)"/>'
  // franjas en la mitad de abajo del sol, que bajan despacio
  for (let i = 0; i < 6; i++) {
    const y = 66 + ((i + t) % 6) * 4
    const h = 0.6 + ((i + t) % 6) * 0.45
    s += `<rect x="10" y="${n(y)}" width="60" height="${n(h)}" fill="#3b1450"/>`
  }
  s += '<rect x="0" y="88" width="80" height="40" fill="#160a2a"/>'
  s += '<rect x="0" y="87" width="80" height="1.6" fill="#ff7ab6"/>'
  for (let i = 0; i < 6; i++) {
    const p = ((i + t) / 6)
    const y = 88 + p * p * 40
    s += `<rect x="0" y="${n(y)}" width="80" height="1" fill="#ff5fb4" opacity="${n(0.25 + p * 0.5)}"/>`
  }
  for (let i = -5; i <= 5; i++) s += `<line x1="${40 + i * 3}" y1="88" x2="${40 + i * 18}" y2="128" stroke="#ff5fb4" stroke-width="1" opacity=".45"/>`
  s += stars(t, [[8, 8, 0], [70, 14, 0.4], [22, 18, 0.7], [56, 6, 0.2]])
  return {
    body: s,
    defs: `<linearGradient id="ubg" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#140b2e"/><stop offset="55%" stop-color="#4b1b5c"/><stop offset="100%" stop-color="#d9476f"/></linearGradient>
      <linearGradient id="usun" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#ffe27a"/><stop offset="100%" stop-color="#ff4f8b"/></linearGradient>`
  }
}

// Oleaje: líneas de olas azul marino sobre papel claro y un sol pequeño
function waves (t) {
  let s = `<rect width="${W}" height="${H}" fill="#f1ede3"/>`
  s += '<circle cx="40" cy="26" r="7" fill="#e4573d"/>'
  const rows = [[52, '#1f3a5f', 0], [66, '#2f5f8a', 0.25], [80, '#1f3a5f', 0.5], [94, '#2f5f8a', 0.75], [108, '#1f3a5f', 0.1]]
  for (const [y0, color, ph] of rows) {
    let d = ''
    for (let x = -4; x <= W + 4; x += 2) {
      const y = y0 + Math.sin((x / 20 + t + ph) * TAU) * 3.2
      d += (x === -4 ? 'M' : ' L') + `${x},${n(y)}`
    }
    s += `<path d="${d}" stroke="${color}" stroke-width="3" fill="none" stroke-linecap="round"/>`
  }
  s += '<rect x="6" y="6" width="68" height="116" fill="none" stroke="#1f3a5f" stroke-opacity=".25" stroke-width="1.2"/>'
  return { body: s, defs: '' }
}

// Eclipse: un anillo de luz que late y un destello que da la vuelta
function eclipse (t) {
  let s = `<rect width="${W}" height="${H}" fill="#050506"/>`
  const pulse = Math.sin(t * TAU) * 0.5 + 0.5
  s += `<circle cx="40" cy="54" r="${n(30 + pulse * 3)}" fill="url(#ecor)"/>`
  s += '<circle cx="40" cy="54" r="16" fill="#050506"/>'
  s += `<circle cx="40" cy="54" r="16" fill="none" stroke="#fff3d6" stroke-width="1.6" opacity="${n(0.75 + pulse * 0.25)}"/>`
  const a = t * TAU - Math.PI / 2
  const fx = 40 + Math.cos(a) * 16
  const fy = 54 + Math.sin(a) * 16
  s += `<circle cx="${n(fx)}" cy="${n(fy)}" r="4" fill="#ffffff" opacity=".35" filter="url(#soft)"/>` + sparkle(fx, fy, 0.8, '#ffffff', 1)
  s += stars(t, [[10, 14, 0], [68, 22, 0.3], [14, 96, 0.6], [64, 104, 0.8], [36, 112, 0.15]], '#d9d4c4')
  s += '<rect x="30" y="100" width="20" height="1.2" fill="#fff3d6" opacity=".3"/>'
  return { body: s, defs: '<radialGradient id="ecor" cx="50%" cy="50%" r="50%"><stop offset="45%" stop-color="#ffb347" stop-opacity=".75"/><stop offset="60%" stop-color="#ff7a3d" stop-opacity=".3"/><stop offset="100%" stop-color="#ff7a3d" stop-opacity="0"/></radialGradient>' }
}

// Capas de medalla (se desbloquean con las horas jugadas): una medalla grande con el fantasma, las horas debajo,
// rayos que giran detrás, un brillo que la cruza y destellos. Colores de cada medalla del launcher.
const MEDALS = {
  medal_gold: { hours: '500', light: '#fff2a8', base: '#f5c23e', dark: '#8a5a0c', bg: '#1a1206', bg2: '#3a2608', ribbon: '#c8203c' },
  medal_emerald: { hours: '750', light: '#b8ffd8', base: '#2ee87a', dark: '#0b6b3a', bg: '#03140b', bg2: '#08301b', ribbon: '#f5c23e' },
  medal_amethyst: { hours: '1000', light: '#ead9ff', base: '#a77bff', dark: '#4a1f9e', bg: '#0d0618', bg2: '#22103e', ribbon: '#f5c23e' },
  medal_diamond: { hours: '2500', light: '#e8fdff', base: '#6fe3ff', dark: '#1d6f9a', bg: '#03101a', bg2: '#0a2a40', ribbon: '#a77bff' },
  medal_legend: { hours: '5000', light: '#ffe9b0', base: '#ff5a7a', dark: '#7a0f2e', bg: '#140308', bg2: '#3a0a18', ribbon: '#f5c23e', rainbow: true }
}

function medalScene (m) {
  return (t, side, logoData) => {
    const cx = 40, cy = 50
    let s = `<rect width="${W}" height="${H}" fill="url(#mbg)"/>`
    // rayos que giran (12 rayos: el bucle cierra al girar 30º)
    let rays = ''
    for (let i = 0; i < 12; i++) rays += `<path d="M${cx},${cy} L${n(cx + Math.cos(i / 12 * TAU) * 90)},${n(cy + Math.sin(i / 12 * TAU) * 90)} L${n(cx + Math.cos((i + 0.45) / 12 * TAU) * 90)},${n(cy + Math.sin((i + 0.45) / 12 * TAU) * 90)} Z" fill="${m.base}"/>`
    s += `<g opacity=".13" transform="rotate(${n(t * 30)} ${cx} ${cy})">${rays}</g>`
    s += `<circle cx="${cx}" cy="${cy}" r="34" fill="url(#mglow)"/>`
    if (side === 'front') {
      // cintas de la medalla
      s += `<path d="M30,10 L40,30 L50,10 L44,10 L40,20 L36,10 Z" fill="${m.ribbon}"/><path d="M30,10 L36,10 L40,20 L40,30 Z" fill="#000" opacity=".25"/>`
      // medalla: borde dentado (estrella de 16 puntas), aro y disco
      let star = ''
      for (let i = 0; i < 32; i++) {
        const a = i / 32 * TAU - Math.PI / 2
        const r = i % 2 ? 19.5 : 22
        star += (i ? ' L' : 'M') + `${n(cx + Math.cos(a) * r)},${n(cy + Math.sin(a) * r)}`
      }
      s += `<path d="${star} Z" fill="${m.dark}"/>`
      s += `<circle cx="${cx}" cy="${cy}" r="19" fill="url(#mmetal)"/>`
      s += `<circle cx="${cx}" cy="${cy}" r="15.5" fill="none" stroke="${m.dark}" stroke-width="1.4" opacity=".7"/>`
      s += `<circle cx="${cx}" cy="${cy}" r="14.5" fill="${m.dark}" opacity=".35"/>`
      s += `<image x="${cx - 12}" y="${cy - 12}" width="24" height="24" href="${logoData}"/>`
      // brillo que cruza la medalla
      const sx = -30 + t * 140
      s += `<g clip-path="url(#mclip)"><rect x="${n(sx)}" y="20" width="7" height="70" fill="#ffffff" opacity=".55" transform="rotate(25 ${n(sx)} 50)"/></g>`
      // horas
      s += `<rect x="16" y="80" width="48" height="15" rx="3" fill="${m.dark}"/>`
      s += `<rect x="16" y="80" width="48" height="7" rx="3" fill="${m.base}" opacity=".35"/>`
      s += `<text x="40" y="91.5" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-weight="900" font-size="11" fill="${m.light}">${m.hours} H</text>`
    } else {
      // por dentro: medallitas repetidas
      for (let y = 10; y < H; y += 26) for (let x = 12; x < W + 14; x += 28) {
        const off = (Math.floor(y / 26) % 2) * 14
        s += `<circle cx="${x + off - 7}" cy="${y}" r="5" fill="${m.base}" opacity=".18"/><circle cx="${x + off - 7}" cy="${y}" r="3" fill="${m.dark}" opacity=".35"/>`
      }
    }
    // destellos
    for (const [x, y, ph] of [[14, 22, 0], [66, 30, 0.33], [62, 72, 0.66], [18, 70, 0.2], [40, 112, 0.5]]) {
      const p = Math.sin((t * 2 + ph) * TAU) * 0.5 + 0.5
      s += sparkle(x, y, 0.5 + p * 0.6, m.light, 0.3 + p * 0.7)
    }
    // marco
    s += `<rect x="3" y="3" width="74" height="122" fill="none" stroke="${m.base}" stroke-width="1.6" opacity=".7"/>`
    s += `<rect x="6" y="6" width="68" height="116" fill="none" stroke="${m.base}" stroke-width=".6" opacity=".35"/>`
    const stops = m.rainbow
      ? '<stop offset="0%" stop-color="#ffd34d"/><stop offset="35%" stop-color="#ff5a7a"/><stop offset="70%" stop-color="#a77bff"/><stop offset="100%" stop-color="#6fe3ff"/>'
      : `<stop offset="0%" stop-color="${m.light}"/><stop offset="45%" stop-color="${m.base}"/><stop offset="100%" stop-color="${m.dark}"/>`
    return {
      body: s,
      defs: `<linearGradient id="mbg" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="${m.bg}"/><stop offset="45%" stop-color="${m.bg2}"/><stop offset="100%" stop-color="${m.bg}"/></linearGradient>
        <radialGradient id="mglow" cx="50%" cy="50%" r="50%"><stop offset="0%" stop-color="${m.base}" stop-opacity=".45"/><stop offset="100%" stop-color="${m.base}" stop-opacity="0"/></radialGradient>
        <linearGradient id="mmetal" x1="0" y1="0" x2="1" y2="1">${stops}</linearGradient>
        <clipPath id="mclip"><circle cx="${cx}" cy="${cy}" r="19"/></clipPath>`
    }
  }
}

const SCENES = { halloween, christmas, aurora, moon, sakura, sunset, waves, eclipse }
for (const [id, m] of Object.entries(MEDALS)) SCENES[id] = medalScene(m)

function sceneSvg (theme, t, side, logoData) {
  const { body, defs } = SCENES[theme](t, side, logoData)
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <defs>${defs}
    <radialGradient id="vig" cx="50%" cy="50%" r="72%"><stop offset="65%" stop-color="#000" stop-opacity="0"/><stop offset="100%" stop-color="#000" stop-opacity=".45"/></radialGradient>
    <filter id="blur" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="3"/></filter>
    <filter id="soft" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation=".8"/></filter>
    <filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="1.2" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
  </defs>
  <clipPath id="c"><rect width="${W}" height="${H}"/></clipPath>
  <g clip-path="url(#c)">${body}</g>
</svg>`
}

// Dibuja la escena y la pixela (40x64, cada píxel = 2x2) para que se vea como pixel art
async function renderScene (sharp, theme, t, side, logoData) {
  const png = await sharp(Buffer.from(sceneSvg(theme, t, side, logoData))).png().toBuffer()
  const small = await sharp(png).resize(W / 2, H / 2, { kernel: 'lanczos3' }).png().toBuffer()
  return sharp(small).resize(W, H, { kernel: 'nearest' }).png().toBuffer()
}

module.exports = { renderScene, SCENE_W: W, SCENE_H: H }
