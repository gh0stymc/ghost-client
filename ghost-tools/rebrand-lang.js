// Cambia "AxolotlClient" por "ghost client" en todos los textos de idioma del mod.
// Uso: node tools/rebrand-lang.js <carpeta-del-mod>
const fs = require('fs')
const path = require('path')

const root = process.argv[2]
const files = []
;(function walk (dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) {
      if (e.name !== 'build' && e.name !== '.git' && e.name !== '.gradle') walk(p)
    } else if (path.basename(path.dirname(p)) === 'lang' && p.endsWith('.json')) {
      files.push(p)
    }
  }
})(root)

let changed = 0
const left = []
for (const f of files) {
  const raw = fs.readFileSync(f, 'utf8')
  const indent = (raw.match(/\n(\s+)"/) || [, '\t'])[1]
  let data
  try { data = JSON.parse(raw) } catch { console.log('No se pudo leer', f); continue }
  let n = 0
  for (const [k, v] of Object.entries(data)) {
    if (typeof v !== 'string') continue
    const nv = v
      .replace(/Please post your current game log in AxolotlClient's discord to get this fixed!/, 'Unknown message.')
      .replace(/Axolotl ?Client/g, 'ghost client')
    if (nv !== v) { data[k] = nv; n++ }
    if (/xolotl/i.test(nv)) left.push(`${path.relative(root, f)}: ${k} = ${nv.slice(0, 100)}`)
  }
  if (n) {
    fs.writeFileSync(f, JSON.stringify(data, null, indent) + (raw.endsWith('\n') ? '\n' : ''))
    changed += n
  }
}
console.log(`${files.length} archivos de idioma, ${changed} textos cambiados`)
console.log(`Quedan con "axolotl": ${left.length}`)
left.slice(0, 15).forEach(l => console.log('  ' + l))
