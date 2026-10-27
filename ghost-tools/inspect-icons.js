// Muestra el tamaño de los iconos del mod y guarda una vista ampliada de la notificación y la insignia.
// Uso: node tools/inspect-icons.js <carpeta-del-mod> <carpeta-de-salida>
const path = require('path')
const fs = require('fs')
const sharp = require(path.join('C:/Users/Arturo/Proyectos/mi-launcher/node_modules/sharp'))

const [root, out] = process.argv.slice(2)
const found = []
;(function walk (dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) { if (!['build', '.git', '.gradle'].includes(e.name)) walk(p) } else if (['icon.png', 'badge.png', 'axolotlclient.png'].includes(e.name) && p.includes(path.join('src', 'main', 'resources'))) found.push(p)
  }
})(root)

;(async () => {
  for (const f of found) {
    const m = await sharp(f).metadata()
    console.log(`${m.width}x${m.height}  ${path.relative(root, f)}`)
  }
  const common = path.join(root, 'common', 'src', 'main', 'resources', 'assets', 'axolotlclient', 'textures')
  await sharp(path.join(common, 'gui', 'sprites', 'toast', 'axolotlclient.png')).resize({ width: 640, kernel: 'nearest' }).toFile(path.join(out, 'toast-original.png'))
  await sharp(path.join(common, 'badge.png')).resize({ width: 256, kernel: 'nearest' }).toFile(path.join(out, 'badge-original.png'))
})()
