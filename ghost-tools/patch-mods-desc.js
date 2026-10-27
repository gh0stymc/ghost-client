// Menú de mods: descripciones, buscador centrado, sin desenfoque de fondo y textos mejor centrados.
// Uso: node tools/patch-mods-desc.js <GhostModsScreen.java>
const fs = require('fs')
const file = process.argv[2]
let s = fs.readFileSync(file, 'utf8')
const rep = (a, b) => {
  if (!s.includes(a)) throw new Error('No encuentro: ' + a.slice(0, 80))
  s = s.replace(a, b)
}

// Tarjetas un poco más altas para la descripción
rep('private static final int CARD_W = 80, CARD_H = 88, GAP = 6;', 'private static final int CARD_W = 80, CARD_H = 104, GAP = 6;')

// La descripción viaja con cada módulo
rep('private record Module(OptionCategory category, BooleanOption enabled, String group, Item icon, Component name) {',
  'private record Module(OptionCategory category, BooleanOption enabled, String group, Item icon, Component name, Component description) {')
rep('modules.add(new Module(cat, enabled, groupOf(top, name), iconOf(name), displayName(name)));',
  'modules.add(new Module(cat, enabled, groupOf(top, name), iconOf(name), displayName(name), descriptionOf(name)));')
rep('\tprivate static String groupOf(String top, String name) {', `\t/** Descripción corta del mod (textos ghost.desc.*), o null si no tiene. */
\tprivate static Component descriptionOf(String key) {
\t\tString k = "ghost.desc." + key;
\t\treturn net.minecraft.locale.Language.getInstance().has(k) ? Component.translatable(k) : null;
\t}

\t/** Parte un texto en líneas que caben en el ancho dado; la última acaba en "…" si no cabe todo. */
\tprivate List<String> wrap(String text, int maxWidth, int maxLines) {
\t\tList<String> lines = new ArrayList<>();
\t\tStringBuilder line = new StringBuilder();
\t\tfor (String word : text.split(" ")) {
\t\t\tString attempt = line.isEmpty() ? word : line + " " + word;
\t\t\tif (font.width(attempt) <= maxWidth) {
\t\t\t\tline = new StringBuilder(attempt);
\t\t\t} else {
\t\t\t\tif (!line.isEmpty()) lines.add(line.toString());
\t\t\t\tline = new StringBuilder(word);
\t\t\t}
\t\t}
\t\tif (!line.isEmpty()) lines.add(line.toString());
\t\tif (lines.size() > maxLines) {
\t\t\tlines = new ArrayList<>(lines.subList(0, maxLines));
\t\t\tString last = lines.get(maxLines - 1);
\t\t\twhile (!last.isEmpty() && font.width(last + "…") > maxWidth) last = last.substring(0, last.length() - 1);
\t\t\tlines.set(maxLines - 1, last.trim() + "…");
\t\t}
\t\treturn lines;
\t}

\tprivate static String groupOf(String top, String name) {`)

// Buscador: texto centrado en vertical dentro de su caja (la caja va de py+9 a py+31)
rep('search = new EditBox(font, px + pw - 152, py + 14, 136, 14, Component.translatable("ghost.mods.search"));',
  'search = new EditBox(font, px + pw - 152, py + 16, 136, 10, Component.translatable("ghost.mods.search"));')

// Sin desenfoque: dentro de un mundo no dibujamos el fondo borroso de Minecraft
rep(/\t\tsuper\.(extractBackground|renderBackground)\(g, mouseX, mouseY, delta\);\n\t\tGhostUi\.roundBox\(g, px, py, pw, ph, PANEL_BG, GhostUi\.BORDER\);/.exec(s)[0],
  /\t\tsuper\.(extractBackground|renderBackground)/.exec(s)[0].replace(/^\t\t/, '\t\t// Sin fondo borroso: dentro de un mundo se sigue viendo el juego tal cual\n\t\tif (minecraft.level == null) ' ) + '(g, mouseX, mouseY, delta);\n\t\tGhostUi.roundBox(g, px, py, pw, ph, PANEL_BG, GhostUi.BORDER);')

// Nombre un poco más arriba y descripción debajo
const nameLine = /\t\tg\.(centeredText|drawCenteredString)\(font, name, x \+ CARD_W \/ 2, y \+ 48, on \? GhostUi\.TEXT : GhostUi\.MUTED\);/.exec(s)
if (!nameLine) throw new Error('No encuentro el nombre de la tarjeta')
const centered = nameLine[1]
s = s.replace(nameLine[0], `\t\tg.${centered}(font, name, x + CARD_W / 2, y + 46, on ? GhostUi.TEXT : GhostUi.MUTED);

\t\t// Descripción corta (letra pequeña, hasta 3 líneas)
\t\tif (m.description() != null) {
\t\t\tList<String> lines = wrap(m.description().getString(), (int) ((CARD_W - 8) / 0.7f), 3);
\t\t\tg.pose().pushMatrix();
\t\t\tg.pose().translate(x + CARD_W / 2f, y + 57);
\t\t\tg.pose().scale(0.7f, 0.7f);
\t\t\tfor (int l = 0; l < lines.size(); l++) {
\t\t\t\tg.${centered}(font, lines.get(l), 0, l * 10, 0xFFA8A8BC);
\t\t\t}
\t\t\tg.pose().popMatrix();
\t\t}`)

// Al pasar el ratón: descripción completa en un tooltip
rep('\t\tg.disableScissor();\n', `\t\tg.disableScissor();

\t\t// Descripción completa del mod bajo el ratón
\t\tif (inGrid(mouseX, mouseY)) {
\t\t\tfor (int i = 0; i < shown.size(); i++) {
\t\t\t\tint cx = gridX + (i % cols) * (CARD_W + GAP);
\t\t\t\tint cy = gridY + (i / cols) * (CARD_H + GAP) - (int) scroll;
\t\t\t\tModule m = shown.get(i);
\t\t\t\tif (m.description() != null && mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H - 24) {
\t\t\t\t\tList<net.minecraft.util.FormattedCharSequence> tip = new ArrayList<>();
\t\t\t\t\ttip.add(m.name().getVisualOrderText());
\t\t\t\t\ttip.addAll(font.split(m.description().copy().withColor(0xA8A8BC), 170));
\t\t\t\t\tg.setTooltipForNextFrame(tip, mouseX, mouseY);
\t\t\t\t}
\t\t\t}
\t\t}
`)
fs.writeFileSync(file, s)
console.log('Aplicado:', file)
