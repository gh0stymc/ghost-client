// Pone la identidad de ghost client en los fabric.mod.json del mod (todas las versiones).
// Los autores originales se mantienen como colaboradores: la licencia LGPL pide conservar los créditos.
// Uso: node tools/rebrand-modjson.js <carpeta-del-mod>
const fs = require('fs')
const path = require('path')

const root = process.argv[2]
const targets = []
for (const dir of ['common', ...fs.readdirSync(path.join(root, 'versions')).map(v => path.join('versions', v))]) {
  const f = path.join(root, dir, 'src', 'main', 'resources', 'fabric.mod.json')
  if (fs.existsSync(f)) targets.push(f)
}

for (const f of targets) {
  const raw = fs.readFileSync(f, 'utf8')
  const indent = (raw.match(/\n(\s+)"/) || [, '\t'])[1]
  const data = JSON.parse(raw)
  const isCommon = data.id === 'axolotlclient-common'
  const original = [...(data.authors || []), ...(data.contributors || [])]
    .map(a => (typeof a === 'string' ? a : a.name))
  data.name = isCommon ? 'ghost client (núcleo)' : 'ghost client'
  data.description = isCommon
    ? 'Código compartido de ghost client.'
    : 'El cliente de Ghost Launcher: HUD configurable, zoom, freelook, motion blur, utilidades PvP y más.\n\n' +
      'Versión modificada de un proyecto de código abierto (LGPL-3.0). Autores originales en Créditos.'
  data.authors = ['Ghost Launcher']
  data.contributors = [...new Set(original)]
  data.contact = { sources: 'https://codeberg.org/AxolotlClient/AxolotlClient-mod' }
  data.icon = 'assets/axolotlclient/icon.png'
  if (data.custom?.modmenu?.parent?.name) data.custom.modmenu.parent.name = 'ghost client'
  fs.writeFileSync(f, JSON.stringify(data, null, indent) + (raw.endsWith('\n') ? '\n' : ''))
  console.log('Actualizado', path.relative(root, f))
}
