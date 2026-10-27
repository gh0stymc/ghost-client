// Añade los textos propios de ghost client (inglés) y crea la traducción al español de las partes principales.
// Uso: node tools/add-lang.js <carpeta-del-mod>
const fs = require('fs')
const path = require('path')
const dir = path.join(process.argv[2], 'common', 'src', 'main', 'resources', 'assets', 'axolotlclient', 'lang')

const en = {
  'ghost.hud.subtitle': 'HUD editor',
  'ghost.hud.hint': 'Drag the HUD elements to move them · Right-click one to configure it',
  'ghost.rpc.no_app': 'The Discord integration of ghost client <br>is not set up yet.'
}
const es = {
  'ghost.hud.subtitle': 'Editor del HUD',
  'ghost.hud.hint': 'Arrastra los elementos del HUD para moverlos · Clic derecho para configurarlos',
  'ghost.rpc.no_app': 'La integración de ghost client con Discord <br>todavía no está configurada.',
  'hud.clientOptions': 'Ajustes de ghost client',
  'hud.snapping': 'Ajuste magnético',
  'close': 'Cerrar',
  'config': 'Opciones de ghost client',
  'category.axolotlclient': 'ghost client',
  'key.category.axolotlclient.title': 'ghost client'
}

function merge (file, entries) {
  const p = path.join(dir, file)
  const raw = fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : '{}\n'
  const indent = (raw.match(/\n(\s+)"/) || [, '\t'])[1]
  const data = JSON.parse(raw)
  Object.assign(data, entries)
  fs.writeFileSync(p, JSON.stringify(data, null, indent) + '\n')
  console.log(`${file}: ${Object.keys(entries).length} textos`)
}

merge('en_us.json', en)
merge('es_es.json', es)
