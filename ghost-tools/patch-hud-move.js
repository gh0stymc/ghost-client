// Añade el botón "mover" a las tarjetas de HUD del menú de mods y hace que el editor del HUD
// resalte el elemento elegido. Uso: node tools/patch-hud-move.js
const fs = require('fs')
const path = require('path')
const base = path.join(__dirname, '..', 'mod', 'versions')
const pkg = 'src/main/java/io/github/axolotlclient/modules/hud'

function rep (s, a, b, file) {
  if (!s.includes(a)) throw new Error(`No encontrado en ${file}: ${a.slice(0, 60)}`)
  return s.replace(a, b)
}

for (const v of ['1.21.11', '26.1', '26.2', 'latest']) {
  // ---------- Menú de mods ----------
  let f = path.join(base, v, pkg, 'GhostModsScreen.java')
  let s = fs.readFileSync(f, 'utf8')
  if (s.includes('ghost.mods.move')) { console.log('ya aplicado', v); continue }
  const setScreen = s.includes('minecraft.gui.setScreen(') ? 'minecraft.gui.setScreen' : 'minecraft.setScreen'
  const centered = s.includes('g.drawCenteredString(') ? 'g.drawCenteredString' : 'g.centeredText'

  s = rep(s, 'import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles;\n',
    'import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles;\nimport io.github.axolotlclient.modules.hud.gui.component.HudEntry;\n', f)
  s = rep(s, 'private record Module(OptionCategory category, BooleanOption enabled, String group, Item icon, Component name, Component description) {',
    'private record Module(OptionCategory category, BooleanOption enabled, String group, Item icon, Component name, Component description, HudEntry hud) {', f)
  s = rep(s, 'modules.add(new Module(cat, enabled, groupOf(top, name), iconOf(name), displayName(name), descriptionOf(name)));',
    'modules.add(new Module(cat, enabled, groupOf(top, name), iconOf(name), displayName(name), descriptionOf(name), hudOf(cat)));', f)
  s = rep(s, '\t/** Nombre visible: sin el sufijo " HUD"',
    `\t/** El elemento del HUD de esta categoría, si se puede mover por la pantalla. */
	private static HudEntry hudOf(OptionCategory cat) {
		for (HudEntry e : HudManager.getInstance().getEntries()) {
			if (e.getCategory() == cat && e.movable()) {
				return e;
			}
		}
		return null;
	}

	/** Botón de mover (arriba a la izquierda de la tarjeta): solo en HUD activados. */
	private static boolean canMove(Module m) {
		return m.hud() != null && m.enabled() != null && m.enabled().get();
	}

	/** Nombre visible: sin el sufijo " HUD"`, f)
  s = rep(s, '\t\t// Engranaje de ajustes\n',
    `\t\t// Botón de mover por la pantalla
		if (canMove(m)) {
			boolean moveHover = hover && mouseX < x + 18 && mouseY < y + 18;
			GhostUi.roundBox(g, x + 4, y + 4, 13, 13, moveHover ? 0xFF2A2440 : 0x00000000, moveHover ? GhostUi.ACCENT : 0x00000000);
			${centered}(font, "✥", x + 11, y + 6, moveHover ? GhostUi.TEXT : GhostUi.MUTED);
		}

		// Engranaje de ajustes
`, f)
  s = rep(s, '\t\t\tboolean onBar = my >= y + CARD_H - 21;\n',
    `\t\t\tboolean onBar = my >= y + CARD_H - 21;
			if (canMove(m) && mx < x + 18 && my < y + 18) {
				${setScreen}(new HudEditScreen(this, m.hud()));
				return true;
			}
`, f)
  fs.writeFileSync(f, s)

  // ---------- Editor del HUD ----------
  f = path.join(base, v, pkg, 'HudEditScreen.java')
  s = fs.readFileSync(f, 'utf8')
  s = rep(s, `	public HudEditScreen(Screen parent) {
		super(Component.empty());`, `	/** Elemento a resaltar al abrir el editor (el que se eligió en el menú de mods). */
	private HudEntry focusOnOpen;

	public HudEditScreen(Screen parent, HudEntry focus) {
		this(parent);
		this.focusOnOpen = focus;
	}

	public HudEditScreen(Screen parent) {
		super(Component.empty());`, f)
  s = rep(s, '\t\tHudManager.getInstance().getMoveableEntries().forEach(e -> addRenderableWidget(new HudEntryWidget(e)));\n',
    `\t\tfor (HudEntry e : HudManager.getInstance().getMoveableEntries()) {
			HudEntryWidget widget = addRenderableWidget(new HudEntryWidget(e));
			if (e == focusOnOpen) {
				setFocused(widget);
			}
		}
		focusOnOpen = null;
`, f)
  fs.writeFileSync(f, s)
  console.log('ok', v)
}
