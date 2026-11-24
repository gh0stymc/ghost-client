// Iconos blancos de los menús nuevos de ghost client (los mismos que usa Ghost Launcher, de Lucide, licencia ISC).
// Uso: node tools/make-ghost-icons.js <carpeta-de-salida>
const path = require('path')
const fs = require('fs')
const { pathToFileURL } = require('url')
const LUCIDE = 'C:/Users/Arturo/Proyectos/mi-launcher/node_modules/lucide-react/dist/esm/icons'
const sharp = require('C:/Users/Arturo/Proyectos/mi-launcher/node_modules/sharp')

const ICONS = {
  social: 'users',
  mods: 'layout-grid',
  wardrobe: 'shirt',
  add: 'user-plus',
  check: 'check',
  close: 'x',
  chat: 'message-circle',
  send: 'send-horizontal',
  trash: 'user-minus',
  search: 'search',
  settings: 'settings',
  move: 'move',
  power: 'power',
  lock: 'lock'
}
const SIZE = 48

const toSvg = (node) => {
  const inner = node.map(([tag, attrs]) => `<${tag} ${Object.entries(attrs).filter(([k]) => k !== 'key').map(([k, v]) => `${k}="${v}"`).join(' ')}/>`).join('')
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${SIZE}" height="${SIZE}" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`
}

;(async () => {
  const out = process.argv[2]
  fs.mkdirSync(out, { recursive: true })
  for (const [name, lucide] of Object.entries(ICONS)) {
    const mod = await import(pathToFileURL(path.join(LUCIDE, `${lucide}.mjs`)).href)
    await sharp(Buffer.from(toSvg(mod.__iconData.node))).png().toFile(path.join(out, `${name}.png`))
  }
  console.log(`${Object.keys(ICONS).length} iconos en ${out}`)
})()
