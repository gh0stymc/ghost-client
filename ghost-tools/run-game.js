// Abre Minecraft con ghost client para probarlo (sin cuenta, solo pruebas locales).
// Uso: npx electron tools/run-game.js <carpeta-de-datos> <version> <ghostclient.jar> [segundos]
const path = require('path')
const fs = require('fs')
const L = 'C:/Users/Arturo/Proyectos/mi-launcher'
const { app } = require('electron')
app.setPath('userData', process.argv[2])

app.whenReady().then(async () => {
  const profiles = require(path.join(L, 'electron/profiles'))
  const modrinth = require(path.join(L, 'electron/modrinth'))
  const versions = require(path.join(L, 'electron/versions'))
  const java = require(path.join(L, 'electron/java'))
  const paths = require(path.join(L, 'electron/paths'))
  const { Client, Authenticator } = require(path.join(L, 'node_modules/minecraft-launcher-core'))
  const [, , , version, jar, seconds = '120'] = process.argv
  const log = (...a) => console.log('[test]', ...a)
  try {
    let p = profiles.list().find(x => x.name === `Ghost ${version}`)
    if (!p) {
      p = profiles.create({ name: `Ghost ${version}`, version, loader: 'fabric' })
      await modrinth.install(p, 'fabric-api')
      await modrinth.install(p, 'modmenu')
    }
    const mods = path.join(paths.instance(p.id), 'mods')
    for (const f of fs.readdirSync(mods)) if (/^GhostClient-/.test(f)) fs.rmSync(path.join(mods, f))
    for (const f of fs.readdirSync(mods)) if (/^AxolotlClient-/i.test(f)) fs.rmSync(path.join(mods, f))
    if (jar !== 'none') fs.copyFileSync(jar, path.join(mods, path.basename(jar)))
    // Idioma español y sin tutoriales para que la captura sea limpia
    const opts = path.join(paths.instance(p.id), 'options.txt')
    if (!fs.existsSync(opts)) fs.writeFileSync(opts, 'lang:es_es\nonboardAccessibility:false\ntutorialStep:none\n')
    // Si la vez anterior se cerró mal, Minecraft cambia de motor gráfico: lo evitamos para que cada prueba sea igual
    else fs.writeFileSync(opts, fs.readFileSync(opts, 'utf8').replace('startedCleanly:false', 'startedCleanly:true'))

    const javaPath = process.env.JAVA_OVERRIDE || await java.ensureJava(await versions.requiredJava(version))
    const fabric = await versions.ensureFabric(version)
    const client = new Client()
    client.on('data', line => {
      if (/ERROR|Exception|ghost client|Mod resolution/.test(line)) process.stdout.write('  | ' + line.slice(0, 400))
      if (/Sound engine started|Created: \d+x\d+/.test(line)) log('JUEGO LISTO')
    })
    const proc = await client.launch({
      authorization: Authenticator.getAuth('GhostTester'), root: paths.root(),
      version: { number: version, type: 'release', custom: fabric.id }, javaPath,
      memory: { max: '4G', min: '1G' }, window: { width: Number(process.env.GHOST_W || 1280), height: Number(process.env.GHOST_H || 720) },
      customArgs: [`-XX:ErrorFile=${process.argv[2]}/hs_err_%p.log`, ...(process.env.GHOST_JVM ? process.env.GHOST_JVM.split('|') : []), ...(process.env.GHOST_OPEN ? [`-Dghostclient.debug.open=${process.env.GHOST_OPEN}`] : [])],
      overrides: { gameDirectory: paths.instance(p.id), cwd: paths.instance(p.id), maxSockets: 16 }
    })
    proc.on('close', c => { log('cerrado', c); app.exit(0) })
    setTimeout(() => { proc.kill(); app.exit(0) }, Number(seconds) * 1000)
  } catch (e) { console.error('[test] ERROR', e); app.exit(1) }
})
