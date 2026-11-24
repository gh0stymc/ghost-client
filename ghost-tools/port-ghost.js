// Copia el código nuevo de ghost client (paquete ghost + capa) de la versión 26.3 a las demás,
// adaptando los nombres que cambian entre versiones de Minecraft.
// Uso: node tools/port-ghost.js   (después: node tools/patch-hub.js mod/versions/<v> para cada una)
const fs = require('fs')
const path = require('path')
const root = path.join(__dirname, '..', 'mod', 'versions')
const javaDir = (v) => path.join(root, v, 'src', 'main', 'java', 'io', 'github', 'axolotlclient')

const FILES = [
  'ghost/GhostHooks.java',
  'ghost/GhostMenuScreen.java',
  'ghost/GhostSocialScreen.java',
  'ghost/GhostWardrobeScreen.java',
  'ghost/GhostFont.java',
  'mixin/GhostCapeMixin.java',
  'mixin/GhostLayerMixin.java',
  'mixin/GhostLivingRendererAccessor.java',
  'mixin/GhostThinCapeMixin.java',
  'ghost/GhostModels.java',
  'ghost/GhostCosmeticsLayer.java',
  'mixin/GhostFontMixin.java',
  'mixin/GhostFontScreenMixin.java',
  'mixin/GhostFontTextMixin.java',
  'modules/hud/GhostUi.java',
  'modules/hud/GhostModsScreen.java'
]

// Cambios por versión (de 26.3 a la versión de destino)
const RULES = {
  '26.2': [
    [/ps\.rotate\(Axis\./g, 'ps.mulPose(Axis.']
  ],
  '26.1': [
    [/ps\.rotate\(Axis\./g, 'ps.mulPose(Axis.'],
    [/minecraft\.gui\.setScreen\(/g, 'minecraft.setScreen(']
  ],
  '1.21.11': [
    [/ps\.rotate\(Axis\./g, 'ps.mulPose(Axis.'],
    [/minecraft\.gui\.setScreen\(/g, 'minecraft.setScreen('],
    [/extractRenderStateWithTooltipAndSubtitles/g, 'renderWithTooltipAndSubtitles'],
    [/method = "text\(Lnet\/minecraft\/client\/gui\/Font;/g, 'method = "drawString(Lnet/minecraft/client/gui/Font;'],
    [/protected void extractContents\(/g, 'public void renderContents('],
    [/\bg\.item\(/g, 'g.renderItem('],
    [/GuiGraphicsExtractor/g, 'GuiGraphics'],
    [/extractBackground\(/g, 'renderBackground('],
    [/extractRenderState\(@NotNull/g, 'render(@NotNull'],
    [/super\.extractRenderState\(/g, 'super.render('],
    [/PlayerFaceExtractor\.extractRenderState\(/g, 'PlayerFaceRenderer.draw('],
    [/components\.PlayerFaceExtractor/g, 'components.PlayerFaceRenderer'],
    [/\.centeredText\(/g, '.drawCenteredString('],
    [/\bg\.text\(/g, 'g.drawString('],
    [/\(_\) ->|\b_ ->/g, 'btn ->'],
    [/extractEntityInInventoryFollowsMouse/g, 'renderEntityInInventoryFollowsMouse']
  ]
}

const source = javaDir('latest')
for (const [version, rules] of Object.entries(RULES)) {
  for (const file of FILES) {
    let s = fs.readFileSync(path.join(source, file), 'utf8')
    for (const [from, to] of rules) s = s.replace(from, to)
    const dest = path.join(javaDir(version), file)
    fs.mkdirSync(path.dirname(dest), { recursive: true })
    fs.writeFileSync(dest, s)
  }
  console.log('copiado a', version)
}
