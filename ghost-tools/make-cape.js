// Genera las capas de ghost client: la capa ghost en varios colores y dos de temporada (Halloween y Navidad).
// Cada capa son 24 fotogramas (textura de capa de Minecraft a 8x: 512x256) que el mod va cambiando, y una vista previa.
// Uso: node tools/make-cape.js <logo.png> <carpeta-del-mod> <carpeta-del-launcher> [id ...]
//   Se crea <carpeta>/<id>/0..23.png y preview.png para cada capa (o solo las indicadas).
const path = require('path')
const fs = require('fs')
const sharp = require(path.join('C:/Users/Arturo/Proyectos/mi-launcher/node_modules/sharp'))
const { renderScene } = require('./cape-scenes')
const { render } = require('./render3d')

// Tarjeta 3D de la capa (card.png): la capa como una lámina fina vista un poco de lado, con luz y sombra suave.
// Usa la textura del primer fotograma con las mismas zonas que Minecraft (a 8x): fuera, dentro, lados, arriba y abajo.
async function capeCard (frame) {
  const { data, info } = await sharp(frame).ensureAlpha().raw().toBuffer({ resolveWithObject: true })
  const tex = (uv) => ({ data, width: info.width, height: info.height, uv })
  const w = 40, h = 64, d = 2.6 // medio ancho, medio alto, grosor
  const e = 0.02
  const quads = [
    // fuera (la que se ve por detrás del jugador): u 8-88, v 8-136
    { p: [[-w, h, d], [w, h, d], [w, -h, d], [-w, -h, d]], tex: tex([[8 + e, 8 + e], [88 - e, 8 + e], [88 - e, 136 - e], [8 + e, 136 - e]]) },
    // dentro: u 96-176
    { p: [[w, h, -d], [-w, h, -d], [-w, -h, -d], [w, -h, -d]], tex: tex([[96 + e, 8 + e], [176 - e, 8 + e], [176 - e, 136 - e], [96 + e, 136 - e]]) },
    // lado derecho: u 88-96
    { p: [[w, h, d], [w, h, -d], [w, -h, -d], [w, -h, d]], tex: tex([[88 + e, 8 + e], [96 - e, 8 + e], [96 - e, 136 - e], [88 + e, 136 - e]]) },
    // lado izquierdo: u 0-8
    { p: [[-w, h, -d], [-w, h, d], [-w, -h, d], [-w, -h, -d]], tex: tex([[0 + e, 8 + e], [8 - e, 8 + e], [8 - e, 136 - e], [0 + e, 136 - e]]) },
    // arriba: u 8-88, v 0-8
    { p: [[-w, h, -d], [w, h, -d], [w, h, d], [-w, h, d]], tex: tex([[8 + e, 0 + e], [88 - e, 0 + e], [88 - e, 8 - e], [8 + e, 8 - e]]) },
    // abajo: u 88-168, v 0-8
    { p: [[-w, -h, d], [w, -h, d], [w, -h, -d], [-w, -h, -d]], tex: tex([[88 + e, 0 + e], [168 - e, 0 + e], [168 - e, 8 - e], [88 + e, 8 - e]]) }
  ]
  return render({ quads, width: 120, height: 180, yaw: -24, pitch: 7, ss: 3, margin: 0.09, light: [-0.35, 0.55, 0.85] })
}

const [logo, modDir, launcherDir, ...only] = process.argv.slice(2)
if (!logo || !modDir || !launcherDir) { console.error('Uso: node tools/make-cape.js <logo.png> <carpeta-mod> <carpeta-launcher> [id...]'); process.exit(1) }

const S = 8 // escala: 1 píxel de la textura de 64x32 = 8 píxeles
const W = 64 * S
const H = 32 * S
const FRAMES = 24
const GHOST_W = 54 // tamaño del fantasma en la cara de fuera
const LOGO = 128 // el logo se usa a 128x128; las medidas de abajo están en ese tamaño

// Colores de cada capa. light = tela clara (la capa blanca).
const VARIANTS = {
  ghost_cape: { accent: '#7c5cff', spark: '#c9b8ff', fabric: '#0b0b11', weave: '#16141f', inner: '#13121c', innerWeave: '#1d1b2b', edge: '#0a0a10' },
  ghost_cape_pink: { accent: '#ff5fb4', spark: '#ffc4e4', fabric: '#13080f', weave: '#22111c', inner: '#1b0e17', innerWeave: '#2a1624', edge: '#0f070c' },
  ghost_cape_white: { accent: '#8f9dff', spark: '#ffffff', fabric: '#ecebf4', weave: '#d6d5e4', inner: '#dcdbe8', innerWeave: '#c9c8da', edge: '#d3d2e0', light: true },
  ghost_cape_orange: { accent: '#ff8a1f', spark: '#ffd3a3', fabric: '#130c06', weave: '#22160c', inner: '#1b120a', innerWeave: '#2a1c10', edge: '#0f0905' },
  ghost_cape_red: { accent: '#ff3b4f', spark: '#ffb7bf', fabric: '#14070a', weave: '#230f13', inner: '#1c0b0e', innerWeave: '#2c1217', edge: '#100608' },
  ghost_cape_green: { accent: '#2ee87a', spark: '#b8ffd4', fabric: '#06110b', weave: '#0f1f15', inner: '#0b1810', innerWeave: '#13271a', edge: '#050d08' },
  ghost_cape_blue: { accent: '#3b8bff', spark: '#b9d7ff', fabric: '#060b16', weave: '#0f1624', inner: '#0b111d', innerWeave: '#131c2e', edge: '#050911' },
  ghost_cape_halloween: { theme: 'halloween', accent: '#ff7a1a', spark: '#ffb36b', fabric: '#0d0712', weave: '#1b0f22', inner: '#140b1a', innerWeave: '#221329', edge: '#3a1405', second: '#8b3dff' },
  ghost_cape_christmas: { theme: 'christmas', accent: '#ffcf5a', spark: '#ffffff', fabric: '#2a070d', weave: '#3a0d15', inner: '#1f050a', innerWeave: '#2e0a11', edge: '#6e0b16', second: '#2fd36b', red: '#e8212f' },
  // Minimalistas (sin fantasma): la escena entera sale de cape-scenes.js
  cape_aurora: { theme: 'aurora', accent: '#3dffb0', spark: '#ffffff', fabric: '#03040d', weave: '#03040d', inner: '#081530', innerWeave: '#081530', edge: '#081530' },
  cape_moon: { theme: 'moon', accent: '#efe8d4', spark: '#ffffff', fabric: '#0b0b10', weave: '#0b0b10', inner: '#0b0b10', innerWeave: '#0b0b10', edge: '#16161d' },
  cape_sakura: { theme: 'sakura', accent: '#f7a8c0', spark: '#ffffff', fabric: '#fbf3ec', weave: '#fbf3ec', inner: '#f6dcdc', innerWeave: '#f6dcdc', edge: '#efd3d3', light: true },
  cape_sunset: { theme: 'sunset', accent: '#ff5fb4', spark: '#ffffff', fabric: '#160a2a', weave: '#160a2a', inner: '#160a2a', innerWeave: '#160a2a', edge: '#2a1240' },
  cape_waves: { theme: 'waves', accent: '#1f3a5f', spark: '#ffffff', fabric: '#f1ede3', weave: '#f1ede3', inner: '#f1ede3', innerWeave: '#f1ede3', edge: '#1f3a5f', light: true },
  cape_eclipse: { theme: 'eclipse', accent: '#ffb347', spark: '#ffffff', fabric: '#050506', weave: '#050506', inner: '#050506', innerWeave: '#050506', edge: '#121214' },
  // De medalla: se desbloquean con las horas jugadas (500, 750, 1000, 2500 y 5000)
  cape_medal_500: { theme: 'medal_gold', accent: '#f5c23e', spark: '#fff2a8', fabric: '#1a1206', weave: '#1a1206', inner: '#1a1206', innerWeave: '#1a1206', edge: '#3a2608' },
  cape_medal_750: { theme: 'medal_emerald', accent: '#2ee87a', spark: '#b8ffd8', fabric: '#03140b', weave: '#03140b', inner: '#03140b', innerWeave: '#03140b', edge: '#08301b' },
  cape_medal_1000: { theme: 'medal_amethyst', accent: '#a77bff', spark: '#ead9ff', fabric: '#0d0618', weave: '#0d0618', inner: '#0d0618', innerWeave: '#0d0618', edge: '#22103e' },
  cape_medal_2500: { theme: 'medal_diamond', accent: '#6fe3ff', spark: '#e8fdff', fabric: '#03101a', weave: '#03101a', inner: '#03101a', innerWeave: '#03101a', edge: '#0a2a40' },
  cape_medal_5000: { theme: 'medal_legend', accent: '#ff5a7a', spark: '#ffe9b0', fabric: '#140308', weave: '#140308', inner: '#140308', innerWeave: '#140308', edge: '#3a0a18', second: '#f5c23e' }
}

const FRONT = { x: 1, y: 1, w: 10, h: 16 } // la cara de fuera (la que se ve por detrás)
const BACK = { x: 12, y: 1, w: 10, h: 16 } // la de dentro
const r = (z) => ({ x: z.x * S, y: z.y * S, w: z.w * S, h: z.h * S })

// Ruido fijo para la tela (siempre igual en todos los fotogramas)
function weave (z, seed, base, light) {
  let s = seed
  const rand = () => { s = (s * 1103515245 + 12345) & 0x7fffffff; return s / 0x7fffffff }
  let out = ''
  const line = light ? '#000000' : '#ffffff'
  for (let y = z.y; y < z.y + z.h; y += 2) {
    const a = ((light ? 0.02 : 0.025) + rand() * 0.03).toFixed(3)
    out += `<rect x="${z.x}" y="${y}" width="${z.w}" height="1" fill="${line}" opacity="${a}"/>`
  }
  for (let i = 0; i < z.w * z.h / 40; i++) {
    const x = z.x + Math.floor(rand() * z.w)
    const y = z.y + Math.floor(rand() * z.h)
    out += `<rect x="${x}" y="${y}" width="1" height="1" fill="${base}" opacity="${(0.3 + rand() * 0.4).toFixed(2)}"/>`
  }
  return out
}

// Murciélago (unos 14x6 px), con las alas más o menos abiertas
function bat (x, y, flap, color) {
  const w = 1 - flap * 0.55
  return `<g transform="translate(${x.toFixed(2)} ${y.toFixed(2)}) scale(1 ${w.toFixed(2)})">
    <path d="M-7,0 L-4,-2 L-2,0 L-1,-2 L0,-1 L1,-2 L2,0 L4,-2 L7,0 L4,1 L2,3 L0,1.5 L-2,3 L-4,1 Z" fill="${color}"/></g>`
}

// Calabaza pequeña con la cara iluminada
function pumpkin (x, y, glow) {
  return `<g transform="translate(${x} ${y})">
    <ellipse cx="0" cy="0" rx="9" ry="7" fill="#e8650f"/>
    <ellipse cx="-4" cy="0" rx="4" ry="7" fill="#f47c1c"/>
    <ellipse cx="4" cy="0" rx="4" ry="7" fill="#d9590c"/>
    <rect x="-1" y="-10" width="2.5" height="4" rx="1" fill="#3c6b1e"/>
    <path d="M-5,-2 L-2,-2 L-3.5,-4.5 Z M2,-2 L5,-2 L3.5,-4.5 Z M-5,2 L-3,4 L-1,2.5 L1,4 L3,2.5 L5,2 L3,5 L-3,5 Z" fill="#ffd36b" opacity="${glow.toFixed(2)}"/>
  </g>`
}

// Gorro de Papá Noel, en las medidas del logo de 128 px
function santaHat (red) {
  return `<g transform="rotate(-10 70 16)">
    <path d="M38,18 C46,-14 84,-26 116,-6 C108,-2 102,4 100,16 Z" fill="${red}"/>
    <path d="M70,-14 C86,-20 104,-16 116,-6 C108,-2 103,2 101,8 C96,-4 84,-12 70,-14 Z" fill="#000" opacity=".18"/>
    <rect x="32" y="10" width="74" height="15" rx="7.5" fill="#f4f4f8"/>
    <rect x="32" y="19" width="74" height="6" rx="3" fill="#d8d8e4"/>
    <circle cx="117" cy="-6" r="9" fill="#f4f4f8"/>
    <circle cx="119" cy="-4" r="5" fill="#d8d8e4" opacity=".7"/>
  </g>`
}

function frameSvg (v, f, logoData) {
  const t = f / FRAMES
  const wave = Math.sin(t * Math.PI * 2)
  const front = r(FRONT)
  const back = r(BACK)
  const cx = front.x + front.w / 2
  const light = !!v.light

  // El fantasma: sube y baja y brilla más arriba
  const bob = -wave * 4.5
  const gx = cx - GHOST_W / 2
  const gy = front.y + 30 + bob
  const k = GHOST_W / LOGO // de medidas del logo a píxeles de la capa
  const glow = (light ? 0.34 : 0.42) + 0.2 * (wave * 0.5 + 0.5)
  const shadowW = 30 - wave * 5
  const shadowY = front.y + 30 + GHOST_W + 10

  // Chispas que suben despacio (o nieve que cae en la de Navidad)
  let particles = ''
  if (v.theme === 'christmas') {
    const flakes = [[-30, 0.0, 1.0], [-14, 0.4, 1.3], [4, 0.15, 1.0], [20, 0.7, 1.5], [32, 0.3, 1.0], [-24, 0.85, 1.2], [12, 0.55, 1.0], [-2, 0.95, 1.4]]
    for (const [dx, phase, size] of flakes) {
      const p = (t + phase) % 1
      const sy = front.y + 2 + p * (front.h - 4)
      const sx = cx + dx + Math.sin((p + phase) * Math.PI * 4) * 2
      particles += `<circle cx="${sx.toFixed(2)}" cy="${sy.toFixed(2)}" r="${size}" fill="#ffffff" opacity="${(0.55 + 0.35 * Math.sin(p * Math.PI)).toFixed(2)}"/>`
    }
  } else {
    const sparkDefs = [[-22, 0.0], [20, 0.33], [-6, 0.66], [26, 0.85], [-28, 0.5]]
    for (const [dx, phase] of sparkDefs) {
      const p = (t + phase) % 1
      const sy = front.y + front.h - 14 - p * 96
      const op = Math.sin(p * Math.PI) * (light ? 0.7 : 0.85)
      const size = p < 0.5 ? 2 : 1.5
      particles += `<rect x="${(cx + dx).toFixed(2)}" y="${sy.toFixed(2)}" width="${size}" height="${size}" fill="${light ? v.accent : v.spark}" opacity="${op.toFixed(2)}"/>`
    }
  }

  // Detalles de cada temporada
  let behind = '' // detrás del fantasma
  let over = '' // delante del fantasma (se mueven con él)
  let top = '' // encima de todo
  let ghostFilter = light ? 'url(#lift)' : ''
  if (v.theme === 'halloween') {
    // Luna, murciélagos que cruzan y una calabaza abajo
    behind += `<circle cx="${front.x + 62}" cy="${front.y + 18}" r="9" fill="#ffe2a8" opacity=".85"/>
      <circle cx="${front.x + 66}" cy="${front.y + 15}" r="8" fill="${v.fabric}" opacity=".9"/>`
    for (const [phase, y0, size] of [[0, 26, 1], [0.45, 44, 0.8], [0.75, 16, 0.7]]) {
      const p = (t + phase) % 1
      const bx = front.x - 10 + p * (front.w + 20)
      const by = front.y + y0 + Math.sin(p * Math.PI * 6) * 3
      const flap = (Math.sin((t * 4 + phase) * Math.PI * 2) + 1) / 2
      behind += `<g transform="translate(${bx.toFixed(2)} ${by.toFixed(2)}) scale(${size})">${bat(0, 0, flap, '#1a0d24')}</g>`
    }
    top += pumpkin(front.x + 16, front.y + front.h - 20, 0.75 + 0.25 * Math.sin(t * Math.PI * 4))
    // Telaraña en una esquina
    top += `<g opacity=".35" stroke="#cfc6e0" stroke-width=".7" fill="none">
      <path d="M${front.x + front.w - 6},${front.y + 6} l-22,0 M${front.x + front.w - 6},${front.y + 6} l0,22 M${front.x + front.w - 6},${front.y + 6} l-16,16"/>
      <path d="M${front.x + front.w - 14},${front.y + 6} q-1,7 ${8},${8} M${front.x + front.w - 21},${front.y + 6} q-1,13 ${15},${15}"/></g>`
    // Fantasma pálido y verdoso con ojos rojos que laten
    ghostFilter = 'url(#spooky)'
    const eye = 0.65 + 0.35 * Math.sin(t * Math.PI * 4)
    for (const [ex, ey] of [[50, 54], [77, 57]]) {
      over += `<circle cx="${(gx + ex * k).toFixed(2)}" cy="${(gy + ey * k).toFixed(2)}" r="5.5" fill="url(#eyeglow)" opacity="${eye.toFixed(2)}"/>
        <circle cx="${(gx + ex * k).toFixed(2)}" cy="${(gy + ey * k).toFixed(2)}" r="1.6" fill="#ff2a2a"/>`
    }
  } else if (v.theme === 'christmas') {
    // Gorro de Papá Noel (se mueve con el fantasma) y luces de colores arriba
    over += `<g transform="translate(${gx.toFixed(2)} ${gy.toFixed(2)}) scale(${k.toFixed(4)})">${santaHat(v.red)}</g>`
    const colors = [v.red, v.accent, v.second, '#5aa8ff', v.red, v.accent]
    let lights = `<path d="M${front.x + 4},${front.y + 9} Q${cx},${front.y + 17} ${front.x + front.w - 4},${front.y + 9}" stroke="#1b3d1f" stroke-width="1" fill="none"/>`
    colors.forEach((c, i) => {
      const u = (i + 0.5) / colors.length
      const lx = front.x + 4 + u * (front.w - 8)
      const ly = front.y + 9 + Math.sin(u * Math.PI) * 6 + 2
      const on = ((Math.floor(t * 8) + i) % 3) !== 0
      lights += `<circle cx="${lx.toFixed(2)}" cy="${ly.toFixed(2)}" r="${on ? 4 : 2.5}" fill="${c}" opacity="${on ? 0.35 : 0}"/>
        <ellipse cx="${lx.toFixed(2)}" cy="${ly.toFixed(2)}" rx="1.6" ry="2.2" fill="${c}" opacity="${on ? 1 : 0.45}"/>`
    })
    top += lights
    // Muñeco de nieve pequeño abajo
    top += `<g transform="translate(${front.x + 16} ${front.y + front.h - 16})">
      <circle cx="0" cy="0" r="6" fill="#f4f4f8"/><circle cx="0" cy="-8" r="4.2" fill="#f4f4f8"/>
      <circle cx="-1.4" cy="-8.8" r=".7" fill="#222"/><circle cx="1.4" cy="-8.8" r=".7" fill="#222"/>
      <path d="M0,-7.8 l3,1 l-3,.4 Z" fill="#ff8a1f"/><rect x="-4.5" y="-5" width="9" height="1.6" fill="${v.second}"/></g>`
  }

  // Ribete: de bastón de caramelo en Navidad, del color de la capa en las demás
  const trim = v.theme === 'christmas'
    ? `<rect x="${front.x + 4}" y="${front.y + 4}" width="${front.w - 8}" height="${front.h - 8}" fill="none" stroke="#f4f4f8" stroke-width="1.6"/>
       <rect x="${front.x + 4}" y="${front.y + 4}" width="${front.w - 8}" height="${front.h - 8}" fill="none" stroke="${v.red}" stroke-width="1.6" stroke-dasharray="4 4"/>`
    : `<rect x="${front.x + 4}" y="${front.y + 4}" width="${front.w - 8}" height="${front.h - 8}" fill="none" stroke="${v.accent}" stroke-opacity="${light ? 0.75 : 0.6}" stroke-width="1.5"/>
       <rect x="${front.x + 7}" y="${front.y + front.h - 13}" width="${front.w - 14}" height="1" fill="${v.accent}" opacity=".35"/>`

  const glowColor = v.theme === 'halloween' ? v.accent : v.accent
  return `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <defs>
    <radialGradient id="glow" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="${glowColor}" stop-opacity="${glow.toFixed(3)}"/>
      <stop offset="55%" stop-color="${glowColor}" stop-opacity="${(glow * 0.35).toFixed(3)}"/>
      <stop offset="100%" stop-color="${glowColor}" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="eyeglow" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="#ff3030" stop-opacity=".9"/>
      <stop offset="100%" stop-color="#ff0000" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="shade" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#000" stop-opacity="0"/>
      <stop offset="100%" stop-color="#000" stop-opacity="${light ? 0.12 : 0.45}"/>
    </linearGradient>
    <radialGradient id="vig" cx="50%" cy="45%" r="75%">
      <stop offset="60%" stop-color="#000" stop-opacity="0"/>
      <stop offset="100%" stop-color="#000" stop-opacity="${light ? 0.15 : 0.55}"/>
    </radialGradient>
    ${v.theme === 'halloween' ? `<linearGradient id="haze" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="${v.second}" stop-opacity=".25"/><stop offset="60%" stop-color="${v.second}" stop-opacity="0"/>
      <stop offset="100%" stop-color="${v.accent}" stop-opacity=".22"/></linearGradient>` : ''}
    <filter id="spooky"><feColorMatrix type="matrix" values="0.55 0.25 0.1 0 0  0.35 0.75 0.1 0 0.05  0.3 0.3 0.35 0 0  0 0 0 1 0"/></filter>
    <filter id="lift" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="2" stdDeviation="2.5" flood-color="#3a3560" flood-opacity=".55"/></filter>
    <clipPath id="front"><rect x="${front.x}" y="${front.y}" width="${front.w}" height="${front.h}"/></clipPath>
  </defs>

  <!-- Bordes, arriba y abajo de la capa -->
  <rect x="0" y="0" width="${22 * S}" height="${17 * S}" fill="${v.edge}"/>
  <!-- Elytra a juego (zona 22,0 - 46,22) -->
  <rect x="${22 * S}" y="0" width="${24 * S}" height="${22 * S}" fill="${v.edge}"/>
  <rect x="${36 * S}" y="${2 * S}" width="${S}" height="${20 * S}" fill="${v.theme === 'christmas' ? v.red : v.accent}" opacity=".55"/>
  <rect x="${23 * S}" y="${2 * S}" width="${S / 2}" height="${20 * S}" fill="${v.second || v.accent}" opacity=".35"/>

  <!-- Cara de dentro -->
  <rect x="${back.x}" y="${back.y}" width="${back.w}" height="${back.h}" fill="${v.inner}"/>
  ${weave(back, 7, v.innerWeave, light)}
  <rect x="${back.x}" y="${back.y}" width="${back.w}" height="${back.h}" fill="url(#shade)"/>

  <!-- Cara de fuera -->
  <g clip-path="url(#front)">
    <rect x="${front.x}" y="${front.y}" width="${front.w}" height="${front.h}" fill="${v.fabric}"/>
    ${weave(front, 3, v.weave, light)}
    ${v.theme === 'halloween' ? `<rect x="${front.x}" y="${front.y}" width="${front.w}" height="${front.h}" fill="url(#haze)"/>` : ''}
    ${behind}
    <ellipse cx="${cx}" cy="${(gy + GHOST_W / 2).toFixed(2)}" rx="62" ry="62" fill="url(#glow)"/>
    <ellipse cx="${cx}" cy="${shadowY.toFixed(2)}" rx="${(shadowW / 2).toFixed(2)}" ry="3" fill="${light ? '#3a3560' : v.accent}" opacity="${(0.18 - wave * 0.05).toFixed(3)}"/>
    ${particles}
    <image x="${gx.toFixed(2)}" y="${gy.toFixed(2)}" width="${GHOST_W}" height="${GHOST_W}" xlink:href="${logoData}" ${ghostFilter ? `filter="${ghostFilter}"` : ''}/>
    ${over}
    <rect x="${front.x}" y="${front.y}" width="${front.w}" height="${front.h}" fill="url(#vig)"/>
    <rect x="${front.x}" y="${front.y}" width="${front.w}" height="${front.h}" fill="url(#shade)" opacity=".7"/>
    ${top}
    ${trim}
  </g>
</svg>`
}

;(async () => {
  const logoPng = await sharp(logo).resize(LOGO, LOGO, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toBuffer()
  const logoData = `data:image/png;base64,${logoPng.toString('base64')}`
  const ids = only.length ? only : Object.keys(VARIANTS)
  for (const id of ids) {
    const v = VARIANTS[id]
    if (!v) { console.error('No existe la capa', id); continue }
    const frames = []
    for (let f = 0; f < FRAMES; f++) {
      let img = sharp(Buffer.from(frameSvg(v, f, logoData)))
      // Capas de temporada: escena en pixel art por las dos caras (ver cape-scenes.js)
      if (v.theme) {
        const base = await img.png().toBuffer()
        const outer = await renderScene(sharp, v.theme, f / FRAMES, 'front', logoData)
        const inner = await renderScene(sharp, v.theme, f / FRAMES, 'inner', logoData)
        img = sharp(base).composite([{ input: outer, left: FRONT.x * S, top: FRONT.y * S }, { input: inner, left: BACK.x * S, top: BACK.y * S }])
      }
      frames.push(await img.png({ compressionLevel: 9 }).toBuffer())
    }
    // Vista previa (para menús): la cara de fuera, a 80x128
    const preview = await sharp(frames[0]).extract({ left: FRONT.x * S, top: FRONT.y * S, width: FRONT.w * S, height: FRONT.h * S }).png().toBuffer()
    const card = await capeCard(frames[0])
    for (const dir of [modDir, launcherDir]) {
      const out = path.join(dir, id)
      fs.mkdirSync(out, { recursive: true })
      frames.forEach((buf, i) => fs.writeFileSync(path.join(out, `${i}.png`), buf))
      fs.writeFileSync(path.join(out, 'preview.png'), preview)
      fs.writeFileSync(path.join(out, 'card.png'), card)
    }
    console.log(id, Math.round(frames.reduce((n, b) => n + b.length, 0) / 1024), 'KB')
  }
})()
