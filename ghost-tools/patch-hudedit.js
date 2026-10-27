// Cambia la pantalla de Right Shift (editor del HUD) al estilo de ghost client en las versiones 26.x.
// Uso: node tools/patch-hudedit.js <HudEditScreen.java>
const fs = require('fs')
const file = process.argv[2]
let s = fs.readFileSync(file, 'utf8')
if (s.includes('GhostUi')) { console.log('Ya aplicado:', file); process.exit(0) }

// Cómo cambia de pantalla esta versión: minecraft.gui.setScreen (26.2+) o minecraft.setScreen (26.1)
const setScreen = s.includes('minecraft.gui.setScreen') ? 'minecraft.gui.setScreen' : 'minecraft.setScreen'

const start = s.indexOf('\t\tthis.addRenderableWidget(Button.builder(Component.translatable("hud.snapping")')
// Fin del método init(): la primera llave que cierra a un solo tabulador de sangría
const close = s.indexOf('\n\t}\n', start)
const end = close + 1
if (start < 0 || close < 0) throw new Error('No encuentro los botones en ' + file)

const buttons = `\t\t// ghost client: panel propio en lugar de los botones grises de Minecraft
\t\tpanelX = width / 2 - PANEL_W / 2;
\t\tpanelY = height / 2 - PANEL_H / 2;
\t\tint bx = panelX + 12, bw = PANEL_W - 24;

\t\tthis.addRenderableWidget(new GhostUi.GhostButton(bx, panelY + 44, bw, 20, Component.translatable("hud.clientOptions"),
\t\t\t_ -> {
\t\t\t\tScreen screen = ConfigStyles.createScreen(this, AxolotlClient.getInstance().getConfigManager().getRoot());
\t\t\t\t${setScreen}(screen);
\t\t\t}, true));

\t\tthis.addRenderableWidget(new GhostUi.GhostButton(bx, panelY + 68, bw, 20, snappingText(),
\t\t\tbuttonWidget -> {
\t\t\t\tHudManager.getInstance().toggleSnapping();
\t\t\t\tbuttonWidget.setMessage(snappingText());
\t\t\t}, false));

\t\tif (parent != null)
\t\t\taddRenderableWidget(new GhostUi.GhostButton(bx, panelY + 92, bw, 20, CommonComponents.GUI_BACK,
\t\t\t\t_ -> ${setScreen}(parent), false));
\t\telse
\t\t\taddRenderableWidget(new GhostUi.GhostButton(bx, panelY + 92, bw, 20, Component.translatable("close"),
\t\t\t\t_ -> ${setScreen}(null), false));
`
s = s.slice(0, start) + buttons + s.slice(end)

// Panel de fondo (debajo de los botones y de los elementos del HUD)
const extras = `
\tprivate static final int PANEL_W = 230;
\tprivate static final int PANEL_H = 124;
\tprivate int panelX, panelY;

\tprivate static Component snappingText() {
\t\treturn Component.translatable("hud.snapping").append(": ")
\t\t\t.append(Component.translatable(HudManager.getInstance().isSnappingEnabled() ? "options.on" : "options.off"));
\t}

\t@Override
\tpublic void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
\t\tsuper.extractBackground(graphics, mouseX, mouseY, delta);
\t\tGhostUi.roundBox(graphics, panelX, panelY, PANEL_W, PANEL_H, GhostUi.PANEL, GhostUi.BORDER);
\t\tgraphics.fill(panelX, panelY + 12, panelX + 2, panelY + 32, GhostUi.ACCENT);
\t\tGhostUi.header(graphics, panelX + 12, panelY + 10, Component.translatable("ghost.hud.subtitle"));
\t\tgraphics.centeredText(font, Component.translatable("ghost.hud.hint"), width / 2, panelY + PANEL_H + 8, GhostUi.MUTED);
\t}
`
const anchor = '\tprivate void updateSnapState() {'
s = s.replace(anchor, extras.trimStart().replace(/^/, '\t').replace('\t\tprivate', '\tprivate') + '\n' + anchor)
fs.writeFileSync(file, s)
console.log('Aplicado:', file, '(', setScreen, ')')
