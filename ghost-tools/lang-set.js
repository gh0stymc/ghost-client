// Añade o cambia textos en en_us.json y es_es.json. Uso: node tools/lang-set.js <archivo.json con {en:{}, es:{}}>
const fs = require('fs')
const path = require('path')
const dir = path.join(__dirname, '..', 'mod', 'common', 'src', 'main', 'resources', 'assets', 'axolotlclient', 'lang')
const add = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))
for (const [lang, file] of [['en', 'en_us.json'], ['es', 'es_es.json']]) {
  const f = path.join(dir, file)
  const data = JSON.parse(fs.readFileSync(f, 'utf8'))
  Object.assign(data, add[lang] || {})
  fs.writeFileSync(f, JSON.stringify(data, null, '\t') + '\n')
  console.log(file, Object.keys(data).length)
}
