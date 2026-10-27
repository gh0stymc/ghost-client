// Pantalla de carga de ghost client en las 4 versiones: copia GhostLoadingScreen y el mixin de 26.2
// (adaptando los nombres de cada versión) y añade las opciones al menú. Uso: node tools/patch-loading-screen.js
const fs = require('fs')
const path = require('path')
const base = path.join(__dirname, '..', 'mod', 'versions')
const src = (v, p) => path.join(base, v, 'src/main/java/io/github/axolotlclient', p)

const helper = fs.readFileSync(src('26.2', 'util/GhostLoadingScreen.java'), 'utf8')
const mixin = fs.readFileSync(src('26.2', 'mixin/SplashOverlayMixin.java'), 'utf8')

for (const v of ['1.21.11', '26.1', '26.2', 'latest']) {
  if (v !== '26.2') {
    fs.writeFileSync(src(v, 'util/GhostLoadingScreen.java'), helper)
    let m = mixin
    if (v === '1.21.11') {
      m = m.replaceAll('GuiGraphicsExtractor', 'GuiGraphics')
        .replaceAll('method = "extractRenderState"', 'method = "render"')
        .replace('Lnet/minecraft/client/gui/Gui;setOverlay', 'Lnet/minecraft/client/Minecraft;setOverlay')
    }
    if (v === '26.1') m = m.replace('Lnet/minecraft/client/gui/Gui;setOverlay', 'Lnet/minecraft/client/Minecraft;setOverlay')
    if (v === 'latest') {
      m = m.replace('import com.mojang.blaze3d.pipeline.RenderPipeline;', 'import com.mojang.renderpearl.api.pipeline.RenderPipeline;')
        .replaceAll('Lcom/mojang/blaze3d/pipeline/RenderPipeline;', 'Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;')
    }
    fs.writeFileSync(src(v, 'mixin/SplashOverlayMixin.java'), m)
  }

  const f = src(v, 'config/AxolotlClientConfig.java')
  let s = fs.readFileSync(f, 'utf8')
  if (s.includes('ghost.loading.mode')) { console.log('opciones ya puestas', v); continue }
  s = s.replace('\tpublic final ColorOption loadingScreenColor = new ColorOption("loadingBgColor", new Color(239, 50, 61, 255));\n',
    `\tpublic final ColorOption loadingScreenColor = new ColorOption("loadingBgColor", new Color(239, 50, 61, 255));

	// ghost client: qué pantalla de carga se ve al abrir el juego (y subir una imagen o GIF propio)
	public final StringArrayOption loadingScreenMode = new StringArrayOption("ghost.loading.mode",
		java.util.Arrays.stream(io.github.axolotlclient.util.GhostLoadingScreen.MODES).map(m -> "ghost.loading.mode." + m).toArray(String[]::new),
		"ghost.loading.mode." + io.github.axolotlclient.util.GhostLoadingScreen.gameMode(),
		v -> io.github.axolotlclient.util.GhostLoadingScreen.setGameMode(v.substring(v.lastIndexOf('.') + 1)));
	public final GenericOption uploadLoadingScreen = new GenericOption("ghost.loading.upload", "ghost.loading.upload.button", () ->
		io.github.axolotlclient.util.dialogs.DialogProvider.IMPL.open("ghost client", java.nio.file.Path.of(System.getProperty("user.home")), "").thenAccept(file -> file.ifPresent(p -> {
			try {
				int frames = io.github.axolotlclient.util.GhostLoadingScreen.importFile(p.toFile());
				loadingScreenMode.set("ghost.loading.mode.custom");
				io.github.axolotlclient.AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("ghost.loading.title", "ghost.loading.done", frames);
			} catch (Exception e) {
				io.github.axolotlclient.AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("ghost.loading.title", "ghost.loading.error", String.valueOf(e.getMessage()));
			}
		})));
`)
  s = s.replace('\t\tgeneral.add(loadingScreenColor);\n', '\t\tgeneral.add(loadingScreenColor);\n\t\tgeneral.add(loadingScreenMode);\n\t\tgeneral.add(uploadLoadingScreen);\n')
  fs.writeFileSync(f, s)
  console.log('ok', v)
}
