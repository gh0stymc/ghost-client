/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
 *
 * This file is part of AxolotlClient.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * For more information, see the LICENSE file.
 */

package io.github.axolotlclient.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Pantalla de carga de ghost client: reproduce una secuencia de fotogramas (la animación del launcher,
 * o la imagen/GIF/vídeo del usuario) en lugar del fondo y el logo de Mojang.
 * <p>
 * Modos: "ghost" (animación del launcher), "custom" (la del usuario) y "classic" (la de Minecraft).
 * El modo elegido dentro del juego se guarda en config/ghostclient/loading-mode.txt; si no hay ninguno,
 * se usa el que pasa el launcher (-Dghostclient.loading.mode).
 */
public final class GhostLoadingScreen {
	public static final String[] MODES = {"launcher", "ghost", "custom", "classic"};
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("axolotlclient", "ghost_loading_frame");
	private static final int MAX_W = 1280, MAX_H = 720, MAX_FRAMES = 360;

	private static boolean initialized;
	private static List<Path> frames = List.of();
	private static float fps = 30;
	private static long start;

	private static DynamicTexture texture;
	private static int texW, texH;
	private static int shown = -1;
	private static volatile NativeImage ready;
	private static volatile int readyIndex = -1;
	private static volatile boolean busy;
	private static ExecutorService decoder;

	private GhostLoadingScreen() {
	}

	// ---------- Dónde están las cosas ----------

	private static Path configDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("ghostclient");
	}

	/** Carpeta de la pantalla personalizada (la misma que usa el launcher, si lo hay). */
	public static Path customDir() {
		String fromLauncher = System.getProperty("ghostclient.loading.custom");
		return fromLauncher != null ? Path.of(fromLauncher) : configDir().resolve("loading");
	}

	/** Modo elegido dentro del juego ("launcher" = el del launcher). */
	public static String gameMode() {
		try {
			String m = Files.readString(configDir().resolve("loading-mode.txt")).trim();
			for (String s : MODES) {
				if (s.equals(m)) return s;
			}
		} catch (IOException ignored) {
		}
		return "launcher";
	}

	public static void setGameMode(String mode) {
		try {
			Files.createDirectories(configDir());
			Files.writeString(configDir().resolve("loading-mode.txt"), mode);
		} catch (IOException ignored) {
		}
		reset();
	}

	private static String effectiveMode() {
		String m = gameMode();
		return "launcher".equals(m) ? System.getProperty("ghostclient.loading.mode", "ghost") : m;
	}

	private static List<Path> listFrames(Path dir) {
		if (dir == null || !Files.isDirectory(dir)) return List.of();
		try (Stream<Path> s = Files.list(dir)) {
			return s.filter(p -> {
				String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
				return n.startsWith("frame_") && (n.endsWith(".jpg") || n.endsWith(".png"));
			}).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private static float readFps(Path dir) {
		try {
			JsonObject o = JsonParser.parseString(Files.readString(dir.resolve("info.json"))).getAsJsonObject();
			return Math.max(0.5f, Math.min(60f, o.get("fps").getAsFloat()));
		} catch (Exception e) {
			return 30;
		}
	}

	private static void init() {
		initialized = true;
		String mode = effectiveMode();
		Path dir = null;
		if ("custom".equals(mode)) {
			dir = customDir();
			if (listFrames(dir).isEmpty()) mode = "ghost";
		}
		if ("ghost".equals(mode)) {
			String d = System.getProperty("ghostclient.loading.default");
			dir = d == null ? null : Path.of(d);
		}
		frames = "classic".equals(mode) ? List.of() : listFrames(dir);
		if (!frames.isEmpty()) {
			fps = readFps(dir);
			start = System.currentTimeMillis();
			try {
				// el primer fotograma, ya mismo, para que no se vea un fondo vacío
				swapIn(decode(frames.getFirst()), 0);
			} catch (Throwable e) {
				frames = List.of();
			}
		}
	}

	/** Vuelve a leer el modo y los archivos la próxima vez que se muestre. */
	public static void reset() {
		release();
		initialized = false;
	}

	// ---------- Lo que usa la pantalla de carga ----------

	/** true si hay que dibujar nuestros fotogramas en lugar del logo de Mojang. */
	public static boolean active() {
		if (!initialized) {
			try {
				init();
			} catch (Throwable e) {
				frames = List.of();
			}
		}
		return !frames.isEmpty() && texture != null;
	}

	/** Prepara el fotograma que toca ahora. Devuelve la textura a dibujar. */
	public static Identifier frame() {
		int n = frames.size();
		int want = n == 1 ? 0 : (int) ((System.currentTimeMillis() - start) * fps / 1000f) % n;
		NativeImage img = ready;
		if (img != null) {
			ready = null;
			swapIn(img, readyIndex);
		}
		if (n > 1 && !busy && ready == null && shown != want) {
			busy = true;
			if (decoder == null) {
				decoder = Executors.newSingleThreadExecutor(r -> {
					Thread t = new Thread(r, "ghost-loading-screen");
					t.setDaemon(true);
					return t;
				});
			}
			final int index = want;
			decoder.execute(() -> {
				try {
					NativeImage img2 = decode(frames.get(index));
					readyIndex = index;
					ready = img2;
				} catch (Throwable ignored) {
				} finally {
					busy = false;
				}
			});
		}
		return TEXTURE;
	}

	public static int width() {
		return texW;
	}

	public static int height() {
		return texH;
	}

	private static void swapIn(NativeImage img, int index) {
		if (texture == null || img.getWidth() != texW || img.getHeight() != texH) {
			if (texture != null) Minecraft.getInstance().getTextureManager().release(TEXTURE);
			texW = img.getWidth();
			texH = img.getHeight();
			texture = new DynamicTexture(() -> "ghost loading screen", img);
			Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
		} else {
			texture.setPixels(img);
		}
		texture.upload();
		shown = index;
	}

	/** Se llama cuando termina la pantalla de carga: libera la memoria. */
	public static void release() {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(TEXTURE);
			texture = null;
		}
		NativeImage img = ready;
		ready = null;
		if (img != null) img.close();
		shown = -1;
		initialized = false;
	}

	/**
	 * Lee un PNG o JPG con stb_image (viene con Minecraft). No se usa ImageIO para JPG porque el Java
	 * que trae Minecraft no incluye su parte nativa.
	 */
	private static NativeImage decode(Path file) throws IOException {
		byte[] bytes = Files.readAllBytes(file);
		ByteBuffer buf = MemoryUtil.memAlloc(bytes.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			buf.put(bytes).flip();
			IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
			ByteBuffer px = STBImage.stbi_load_from_memory(buf, w, h, c, 4);
			if (px == null) throw new IOException("imagen no válida: " + STBImage.stbi_failure_reason());
			try {
				NativeImage img = new NativeImage(w.get(0), h.get(0), false);
				MemoryUtil.memCopy(MemoryUtil.memAddress(px), img.getPointer(), (long) w.get(0) * h.get(0) * 4);
				return img;
			} finally {
				STBImage.stbi_image_free(px);
			}
		} finally {
			MemoryUtil.memFree(buf);
		}
	}

	// ---------- Subir una imagen o GIF desde el juego ----------

	/**
	 * Convierte una imagen (PNG, JPG) o un GIF animado en fotogramas dentro de {@link #customDir()}
	 * y activa el modo personalizado. Devuelve el número de fotogramas.
	 */
	public static int importFile(File file) throws IOException {
		Path dir = customDir();
		Path tmp = dir.resolveSibling(dir.getFileName() + ".tmp");
		deleteTree(tmp);
		Files.createDirectories(tmp);
		List<BufferedImage> images = new ArrayList<>();
		float outFps = 1;
		String name = file.getName().toLowerCase(Locale.ROOT);
		if (name.endsWith(".gif")) {
			try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
				ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
				reader.setInput(in);
				int count = Math.min(MAX_FRAMES, reader.getNumImages(true));
				BufferedImage canvas = null;
				int totalDelay = 0;
				for (int i = 0; i < count; i++) {
					BufferedImage part = reader.read(i);
					IIOMetadata meta = reader.getImageMetadata(i);
					org.w3c.dom.Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
					int left = 0, top = 0, delay = 10;
					for (org.w3c.dom.Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
						if ("ImageDescriptor".equals(n.getNodeName())) {
							left = Integer.parseInt(n.getAttributes().getNamedItem("imageLeftPosition").getNodeValue());
							top = Integer.parseInt(n.getAttributes().getNamedItem("imageTopPosition").getNodeValue());
						} else if ("GraphicControlExtension".equals(n.getNodeName())) {
							delay = Math.max(2, Integer.parseInt(n.getAttributes().getNamedItem("delayTime").getNodeValue()));
						}
					}
					if (canvas == null) {
						canvas = new BufferedImage(Math.max(part.getWidth(), reader.getWidth(0)), Math.max(part.getHeight(), reader.getHeight(0)), BufferedImage.TYPE_INT_ARGB);
					}
					Graphics2D g = canvas.createGraphics();
					g.drawImage(part, left, top, null);
					g.dispose();
					images.add(copy(canvas));
					totalDelay += delay;
				}
				reader.dispose();
				outFps = images.size() / Math.max(0.01f, totalDelay / 100f);
			}
		} else {
			BufferedImage img = ImageIO.read(file);
			if (img == null) throw new IOException("Formato no compatible");
			images.add(img);
		}
		if (images.isEmpty()) throw new IOException("No se pudo leer ninguna imagen");
		List<String> files = new ArrayList<>();
		for (int i = 0; i < images.size(); i++) {
			String f = String.format(Locale.ROOT, "frame_%04d.png", i);
			ImageIO.write(fit(images.get(i)), "png", tmp.resolve(f).toFile());
			files.add(f);
		}
		JsonObject info = new JsonObject();
		info.addProperty("fps", Math.max(0.5f, Math.min(60f, outFps)));
		info.addProperty("frames", files.size());
		info.addProperty("source", "game");
		com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
		files.forEach(arr::add);
		info.add("files", arr);
		info.addProperty("loop", true);
		info.addProperty("created", System.currentTimeMillis());
		Files.writeString(tmp.resolve("info.json"), info.toString(), StandardCharsets.UTF_8);
		deleteTree(dir);
		Files.move(tmp, dir);
		setGameMode("custom");
		return files.size();
	}

	private static BufferedImage copy(BufferedImage src) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return out;
	}

	/** Reduce a 1280x720 como mucho y quita la transparencia (fondo negro). */
	private static BufferedImage fit(BufferedImage src) {
		float scale = Math.min(1f, Math.min((float) MAX_W / src.getWidth(), (float) MAX_H / src.getHeight()));
		int w = Math.max(1, Math.round(src.getWidth() * scale)), h = Math.max(1, Math.round(src.getHeight() * scale));
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setColor(java.awt.Color.BLACK);
		g.fillRect(0, 0, w, h);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	private static void deleteTree(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		try (Stream<Path> s = Files.walk(dir)) {
			for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
		}
	}
}
