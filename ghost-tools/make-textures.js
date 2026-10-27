// Genera las texturas de ghost client: icono, insignia y fondo de las notificaciones.
// Uso: node tools/make-textures.js <carpeta-del-mod> <logo.png>
const path = require('path')
const fs = require('fs')
const sharp = require(path.join('C:/Users/Arturo/Proyectos/mi-launcher/node_modules/sharp'))

const [root, logo] = process.argv.slice(2)

// Fondo de notificación (se estira por partes: los bordes no se deforman)
// Mismos colores que el launcher: fondo casi negro, borde sutil y franja morada a la izquierda
const toastSvg = `
<svg xmlns="http://www.w3.org/2000/svg" width="160" height="64" viewBox="0 0 160 64" shape-rendering="crispEdges">
  <rect x="0" y="0" width="160" height="64" rx="4" fill="#2a2a3a"/>
  <rect x="1" y="1" width="158" height="62" rx="3" fill="#12121b"/>
  <rect x="1" y="4" width="2" height="56" fill="#7c5cff"/>
</svg>`

;(async () => {
  const icon = await sharp(logo).resize(119, 119, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toBuffer()
  let n = 0
  ;(function walk (dir) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name)
      if (e.isDirectory()) { if (!['build', '.git', '.gradle'].includes(e.name)) walk(p) } else if (p.includes(path.join('src', 'main', 'resources', 'assets', 'axolotlclient')) && (e.name === 'icon.png' || e.name === 'badge.png')) {
        fs.writeFileSync(p, icon)
        n++
      }
    }
  })(root)
  const toast = path.join(root, 'common', 'src', 'main', 'resources', 'assets', 'axolotlclient', 'textures', 'gui', 'sprites', 'toast', 'axolotlclient.png')
  await sharp(Buffer.from(toastSvg)).png().toFile(toast)
  console.log(`${n} iconos sustituidos y fondo de notificación creado`)
})()
