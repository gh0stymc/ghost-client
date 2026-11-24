// Engancha el menú de Right Shift nuevo (GhostMenuScreen), la parte social y la capa en una versión del mod.
// Uso: node tools/patch-hub.js <carpeta versions/xxx>
const fs = require('fs')
const path = require('path')
const dir = process.argv[2]
const src = path.join(dir, 'src', 'main', 'java', 'io', 'github', 'axolotlclient')
const res = path.join(dir, 'src', 'main', 'resources')

function patch (file, pairs) {
  let s = fs.readFileSync(file, 'utf8')
  for (const [a, b, optional] of pairs) {
    if (s.includes(b)) continue // ya hecho
    if (!s.includes(a)) { if (!optional) console.log('NO ENCONTRADO en', path.basename(file), ':', a.slice(0, 80)); continue }
    s = s.replace(a, b)
  }
  fs.writeFileSync(file, s)
}

const setScreen = fs.readFileSync(path.join(src, 'mixin', 'TitleScreenMixin.java'), 'utf8').includes('minecraft.gui.setScreen') ? 'minecraft.gui.setScreen' : 'minecraft.setScreen'

// Right Shift abre el menú nuevo
patch(path.join(src, 'modules', 'hud', 'HudManager.java'), [
  ['new GhostModsScreen(null)', 'new io.github.axolotlclient.ghost.GhostMenuScreen(null)']
])
// Botón de la pantalla de título y del menú de pausa
patch(path.join(src, 'mixin', 'TitleScreenMixin.java'), [
  [`onPress = _ -> ${setScreen}(new GhostModsScreen(this));`, `onPress = _ -> ${setScreen}(new io.github.axolotlclient.ghost.GhostMenuScreen(this));`, true],
  [`onPress = btn -> ${setScreen}(new GhostModsScreen(this));`, `onPress = btn -> ${setScreen}(new io.github.axolotlclient.ghost.GhostMenuScreen(this));`, true],
  ['Screen hud = "mods".equals(target) ? new GhostModsScreen(this) : new HudEditScreen(this);',
    'Screen hud = switch (target) {\n\t\t\tcase "mods" -> new GhostModsScreen(this);\n\t\t\tcase "hub" -> new io.github.axolotlclient.ghost.GhostMenuScreen(this);\n\t\t\tcase "social" -> new io.github.axolotlclient.ghost.GhostSocialScreen(this);\n\t\t\tcase "wardrobe" -> new io.github.axolotlclient.ghost.GhostWardrobeScreen(this);\n\t\t\tdefault -> new HudEditScreen(this);\n\t\t};']
])
patch(path.join(src, 'mixin', 'GameMenuScreenMixin.java'), [
  [`_ -> ${setScreen}(new GhostModsScreen(this))`, `_ -> ${setScreen}(new io.github.axolotlclient.ghost.GhostMenuScreen(this))`, true],
  [`btn -> ${setScreen}(new GhostModsScreen(this))`, `btn -> ${setScreen}(new io.github.axolotlclient.ghost.GhostMenuScreen(this))`, true]
])
// Arranque de la parte social
if (!fs.readFileSync(path.join(src, 'AxolotlClient.java'), 'utf8').includes('GhostHooks.init()')) {
  patch(path.join(src, 'AxolotlClient.java'), [
    ['\t\tgetLogger().info("ghost client Initialized");', '\t\tio.github.axolotlclient.ghost.GhostHooks.init();\n\t\tgetLogger().info("ghost client Initialized");']
  ])
}
// Prueba automática en las versiones que abren las pantallas con un if
patch(path.join(src, 'mixin', 'TitleScreenMixin.java'), [
  [`minecraft.execute(() -> ${setScreen}("hud".equals(target) ? new HudEditScreen(this) : new GhostModsScreen(this)));`,
    `minecraft.execute(() -> ${setScreen}(switch (target) {\n\t\t\tcase "hud" -> new HudEditScreen(this);\n\t\t\tcase "hub" -> new io.github.axolotlclient.ghost.GhostMenuScreen(this);\n\t\t\tcase "social" -> new io.github.axolotlclient.ghost.GhostSocialScreen(this);\n\t\t\tcase "wardrobe" -> new io.github.axolotlclient.ghost.GhostWardrobeScreen(this);\n\t\t\tdefault -> new GhostModsScreen(this);\n\t\t}));`, true]
])
// La capa y la letra Inter
patch(path.join(res, 'axolotlclient.mixins.json'), [
  ['"client": [\n\t\t"GhostRoundedScreenMixin",', '"client": [\n\t\t"GhostRoundedScreenMixin",\n\t\t"GhostCapeMixin",']
])
patch(path.join(res, 'axolotlclient.mixins.json'), [
  ['\t\t"GhostCapeMixin",\n', '\t\t"GhostCapeMixin",\n\t\t"GhostFontMixin",\n\t\t"GhostFontScreenMixin",\n\t\t"GhostFontTextMixin",\n']
])
console.log('listo:', dir)
