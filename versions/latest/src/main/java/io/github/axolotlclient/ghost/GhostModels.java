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

package io.github.axolotlclient.ghost;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * ghost client: cosméticos 3D (mascotas, sombreros, cosas de la espalda y auras): bloques con una textura pintada
 * pixel a pixel (como los modelos de Blockbench). Formato también válido: mallas suaves con color por vértice.
 * Los modelos los genera tools/make-cosmetics.js: assets/axolotlclient/ghost/models/&lt;id&gt;.json (caras con UV) y la textura
 * textures/ghost/cosmetics/&lt;id&gt;/model.png. En las mallas sin textura, al cargar se suma al color el brillo del material.
 * También se leen los modelos antiguos de bloques de un color ("boxes").
 * Medidas en píxeles, con y hacia arriba, x+ a la izquierda del jugador y z- hacia delante.
 * Todo se prepara al cargar el modelo: al dibujar solo se recorren arrays (nada de listas nuevas en cada fotograma).
 * Animaciones de fotogramas clave (los modelos hechos al estilo Blockbench, tools/make-bb-cosmetics.js): cada parte tiene
 * "kf" por estado (idle, walk, sneak) con pistas de giro, posición y escala [t, x, y, z, suavizado, ...]; se mezclan
 * según cómo se mueve el jugador (Pose).
 */
public final class GhostModels {
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath("axolotlclient", "textures/ghost/white.png");
	private static final int FULL_LIGHT = 0xF000F0;
	private static final Map<String, Optional<Model>> CACHE = new ConcurrentHashMap<>();

	/** Caras de un bloque y sus esquinas (fracciones del bloque), en el mismo orden que el generador. */
	private static final float[][] FACE_N = {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
	private static final int[][][] FACE_C = {
		{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
		{{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
		{{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}},
		{{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
		{{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
		{{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}}
	};

	record Anim(String type, float amp, float speed, float phase) {
	}

	/** Geometría lista para dibujar: grupos de 4 vértices (los triángulos repiten el último), con posición, color ARGB y normal. */
	record Mesh(float[] pos, int[] col, float[] nrm, float[] uv) {
		int vertices() {
			return col.length;
		}
	}

	/** Material: brillo especular (ks, shin) y cuánto refleja como un metal. */
	record Mat(float ks, float shin, float metal) {
	}

	/** Pistas de un estado: listas planas de 5 en 5 (tiempo, x, y, z, suavizado) o null. */
	record Clip(float[] rot, float[] pos, float[] scl) {
	}

	record Part(float[] pivot, float[] rot, int parent, Anim[] anims, Mesh solid, Mesh glow, Map<String, Clip> kf) {
	}

	/** Cómo se mueve el jugador: walk de 0 (quieto) a 1 (andando), sneak = agachado. */
	public record Pose(float walk, boolean sneak) {
		public static final Pose IDLE = new Pose(0, false);
	}

	record Aura(String type, int[] colors, float radius, float speed, int count, int trail) {
	}

	/** texture: la textura pintada del modelo (bloques con textura, como en Blockbench), o la blanca si no tiene. */
	public record Model(String id, String slot, float[] offset, float[] rot, Part[] parts, Aura aura, Identifier texture, Map<String, Float> states) {
	}

	private GhostModels() {
	}

	/** Modelo de un cosmético (se lee la primera vez), o null si no existe. */
	public static Model get(String id) {
		if (id == null) return null;
		return CACHE.computeIfAbsent(id, GhostModels::load).orElse(null);
	}

	// ---------- Carga ----------

	private static Optional<Model> load(String id) {
		try (InputStream in = GhostModels.class.getResourceAsStream("/assets/axolotlclient/ghost/models/" + id + ".json")) {
			if (in == null) return Optional.empty();
			JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			List<Part> parts = new ArrayList<>();
			Map<String, Mat> materials = new java.util.HashMap<>();
			if (o.has("materials")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("materials").entrySet()) {
				JsonObject mo = e.getValue().getAsJsonObject();
				materials.put(e.getKey(), new Mat(num(mo, "ks", 0), num(mo, "shin", 8), num(mo, "metal", 0)));
			}
			if (o.has("parts")) for (JsonElement pe : o.getAsJsonArray("parts")) {
				JsonObject p = pe.getAsJsonObject();
				List<Anim> anims = new ArrayList<>();
				if (p.has("anim")) {
					JsonElement ae = p.get("anim");
					List<JsonElement> list = new ArrayList<>();
					if (ae.isJsonArray()) ae.getAsJsonArray().forEach(list::add);
					else list.add(ae);
					for (JsonElement e : list) {
						JsonObject a = e.getAsJsonObject();
						anims.add(new Anim(a.get("type").getAsString(), num(a, "amp", 0), num(a, "speed", 1), num(a, "phase", 0)));
					}
				}
				MeshBuilder solid = new MeshBuilder(), glow = new MeshBuilder();
				if (p.has("meshes")) for (JsonElement me : p.getAsJsonArray("meshes")) {
					JsonObject mo = me.getAsJsonObject();
					boolean isGlow = mo.has("g") && mo.get("g").getAsInt() == 1;
					(isGlow ? glow : solid).mesh(mo, isGlow ? null : materials.get(mo.get("mat").getAsString()));
				}
				if (p.has("boxes")) for (JsonElement be : p.getAsJsonArray("boxes")) {
					JsonArray b = be.getAsJsonArray();
					boolean isGlow = b.size() > 8 && b.get(8).getAsBoolean();
					(isGlow ? glow : solid).box(b);
				}
				Map<String, Clip> kf = new java.util.HashMap<>();
				if (p.has("kf")) for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("kf").entrySet()) {
					JsonObject c = e.getValue().getAsJsonObject();
					kf.put(e.getKey(), new Clip(floats(c, "rot"), floats(c, "pos"), floats(c, "scl")));
				}
				parts.add(new Part(vec(p, "pivot"), vec(p, "rot"), p.has("parent") ? p.get("parent").getAsInt() : -1,
					anims.toArray(new Anim[0]), solid.build(), glow.build(), kf));
			}
			Aura aura = null;
			if (o.has("aura")) {
				JsonObject a = o.getAsJsonObject("aura");
				JsonArray cs = a.getAsJsonArray("colors");
				int[] colors = new int[cs.size()];
				for (int i = 0; i < colors.length; i++) colors[i] = 0xFF000000 | Integer.parseInt(cs.get(i).getAsString().substring(1), 16);
				aura = new Aura(a.get("type").getAsString(), colors, num(a, "radius", 10), num(a, "speed", 0.3f), (int) num(a, "count", 16), (int) num(a, "trail", 5));
			}
			Identifier texture = o.has("texture") ? Identifier.fromNamespaceAndPath("axolotlclient", "textures/ghost/cosmetics/" + id + "/model.png") : WHITE;
			Map<String, Float> states = new java.util.HashMap<>();
			if (o.has("states")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("states").entrySet()) states.put(e.getKey(), e.getValue().getAsFloat());
			return Optional.of(new Model(id, o.get("slot").getAsString(), vec(o, "offset"), vec(o, "rot"), parts.toArray(new Part[0]), aura, texture, states));
		} catch (Exception e) {
			org.slf4j.LoggerFactory.getLogger("ghost client").warn("No se pudo cargar el cosmético {}", id, e);
			return Optional.empty();
		}
	}

	/** Junta las caras de muchos bloques en arrays. */
	private static final class MeshBuilder {
		private final List<float[]> pos = new ArrayList<>();
		private final List<int[]> col = new ArrayList<>();
		private final List<float[]> nrm = new ArrayList<>();
		private final List<float[]> uvs = new ArrayList<>();
		private static final float[] DEFAULT_UV = {0, 0, 0, 1, 1, 1, 1, 0};

		void box(JsonArray b) {
			float x = b.get(0).getAsFloat(), y = b.get(1).getAsFloat(), z = b.get(2).getAsFloat();
			float w = b.get(3).getAsFloat(), h = b.get(4).getAsFloat(), d = b.get(5).getAsFloat();
			String colors = b.get(6).getAsString();
			int alpha = Math.round((b.size() > 7 ? b.get(7).getAsFloat() : 1f) * 255) << 24;
			boolean perCorner = colors.length() >= 24 * 6;
			int flat = alpha | Integer.parseInt(colors.startsWith("#") ? colors.substring(1, 7) : colors.substring(0, 6), 16);
			for (int f = 0; f < 6; f++) {
				float[] p = new float[12];
				int[] c = new int[4];
				for (int v = 0; v < 4; v++) {
					int[] k = FACE_C[f][v];
					p[v * 3] = x + k[0] * w;
					p[v * 3 + 1] = y + k[1] * h;
					p[v * 3 + 2] = z + k[2] * d;
					int i = (f * 4 + v) * 6;
					c[v] = perCorner ? alpha | Integer.parseInt(colors.substring(i, i + 6), 16) : flat;
				}
				pos.add(p);
				col.add(c);
				float[] nn = new float[12];
				for (int v = 0; v < 4; v++) System.arraycopy(FACE_N[f], 0, nn, v * 3, 3);
				nrm.add(nn);
				uvs.add(DEFAULT_UV);
			}
		}

		/**
		 * Una malla: con "q" son caras de 4 vértices seguidas (bloques con textura, con "uv"); si no, triángulos con índices "i"
		 * (cada triángulo va como un grupo de 4 vértices con el último repetido). Sin "c", el color es blanco (lo pone la textura).
		 */
		void mesh(JsonObject m, Mat mat) {
			JsonArray v = m.getAsJsonArray("v"), n = m.getAsJsonArray("n");
			String colors = m.has("c") ? m.get("c").getAsString() : null;
			JsonArray uv = m.has("uv") ? m.getAsJsonArray("uv") : null;
			int alpha = Math.round((m.has("a") ? m.get("a").getAsFloat() : 1f) * 255) << 24;
			int count = v.size() / 3;
			float[] vp = new float[count * 3], vn = new float[count * 3], vt = new float[count * 2];
			if (uv != null) for (int k = 0; k < count * 2; k++) vt[k] = uv.get(k).getAsFloat();
			if (m.has("q")) {
				for (int k = 0; k < count * 3; k++) {
					vp[k] = v.get(k).getAsFloat();
					vn[k] = n.get(k).getAsFloat();
				}
				for (int q = 0; q + 3 < count; q += 4) {
					float[] p = new float[12], nn = new float[12], t = new float[8];
					int[] c = new int[4];
					System.arraycopy(vp, q * 3, p, 0, 12);
					System.arraycopy(vn, q * 3, nn, 0, 12);
					System.arraycopy(vt, q * 2, t, 0, 8);
					java.util.Arrays.fill(c, alpha | 0xFFFFFF);
					pos.add(p);
					col.add(c);
					nrm.add(nn);
					uvs.add(t);
				}
				return;
			}
			JsonArray idx = m.getAsJsonArray("i");
			int[] vc = new int[count];
			for (int k = 0; k < count; k++) {
				for (int a = 0; a < 3; a++) {
					vp[k * 3 + a] = v.get(k * 3 + a).getAsFloat();
					vn[k * 3 + a] = n.get(k * 3 + a).getAsFloat();
				}
				float l = (float) Math.sqrt(vn[k * 3] * vn[k * 3] + vn[k * 3 + 1] * vn[k * 3 + 1] + vn[k * 3 + 2] * vn[k * 3 + 2]);
				if (l > 0) for (int a = 0; a < 3; a++) vn[k * 3 + a] /= l;
				int rgb = colors == null ? 0xFFFFFF : Integer.parseInt(colors.substring(k * 6, k * 6 + 6), 16);
				vc[k] = alpha | (colors == null ? rgb : shine(rgb, vn[k * 3], vn[k * 3 + 1], vn[k * 3 + 2], mat));
			}
			for (int t = 0; t + 2 < idx.size(); t += 3) {
				float[] p = new float[12], nn = new float[12], tt = new float[8];
				int[] c = new int[4];
				for (int k = 0; k < 4; k++) {
					int i = idx.get(t + Math.min(k, 2)).getAsInt();
					System.arraycopy(vp, i * 3, p, k * 3, 3);
					System.arraycopy(vn, i * 3, nn, k * 3, 3);
					System.arraycopy(vt, i * 2, tt, k * 2, 2);
					c[k] = vc[i];
				}
				pos.add(p);
				col.add(c);
				nrm.add(nn);
				uvs.add(uv != null ? tt : DEFAULT_UV);
			}
		}

		Mesh build() {
			if (pos.isEmpty()) return null;
			float[] p = new float[pos.size() * 12];
			int[] c = new int[pos.size() * 4];
			float[] n = new float[pos.size() * 12];
			float[] t = new float[pos.size() * 8];
			for (int q = 0; q < pos.size(); q++) {
				System.arraycopy(pos.get(q), 0, p, q * 12, 12);
				System.arraycopy(col.get(q), 0, c, q * 4, 4);
				System.arraycopy(nrm.get(q), 0, n, q * 12, 12);
				System.arraycopy(uvs.get(q), 0, t, q * 8, 8);
			}
			return new Mesh(p, c, n, t);
		}
	}

	// Luz de estudio fija al modelo (arriba, delante y un poco a un lado) para el brillo de los materiales
	private static final float[] SHINE_H;

	static {
		float[] h = {-0.35f, 0.8f, -0.5f - 1f};
		float len = (float) Math.sqrt(h[0] * h[0] + h[1] * h[1] + h[2] * h[2]);
		SHINE_H = new float[]{h[0] / len, h[1] / len, h[2] / len};
	}

	/** Añade al color el brillo del material: reflejo del metal (claro arriba, oscuro abajo) y brillo especular. */
	private static int shine(int rgb, float nx, float ny, float nz, Mat m) {
		if (m == null || (m.ks() <= 0 && m.metal() <= 0)) return rgb;
		float t = Math.max(0, Math.min(1, (ny + 0.4f) / 1.3f));
		float env = 1 + m.metal() * (0.85f + 0.5f * t * t * (3 - 2 * t) - 1);
		float spec = m.ks() * 0.8f * (float) Math.pow(Math.max(0, nx * SHINE_H[0] + ny * SHINE_H[1] + nz * SHINE_H[2]), m.shin());
		int out = 0;
		for (int s = 16; s >= 0; s -= 8) {
			float c = ((rgb >> s) & 255) * env;
			float tint = m.metal() > 0 ? 0.35f + 0.65f * Math.min(1, c / 255f) : 1f; // el brillo del metal toma su color
			c += spec * 255 * tint;
			out |= Math.max(0, Math.min(255, Math.round(c))) << s;
		}
		return out;
	}

	private static float num(JsonObject o, String k, float def) {
		return o.has(k) ? o.get(k).getAsFloat() : def;
	}

	private static float[] floats(JsonObject o, String k) {
		if (!o.has(k)) return null;
		JsonArray a = o.getAsJsonArray(k);
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
		return out;
	}

	private static float[] vec(JsonObject o, String k) {
		if (!o.has(k)) return new float[3];
		JsonArray a = o.getAsJsonArray(k);
		return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	// ---------- Dibujo ----------

	/**
	 * Dibuja un modelo. El PoseStack tiene que estar en el ancla (cuello, cabeza o cuerpo del jugador, en espacio de modelo).
	 * time = segundos (para las animaciones).
	 */
	public static void render(Model m, PoseStack ps, SubmitNodeCollector out, int light, float time) {
		render(m, ps, out, light, time, Pose.IDLE);
	}

	public static void render(Model m, PoseStack ps, SubmitNodeCollector out, int light, float time, Pose move) {
		ps.pushPose();
		ps.scale(1 / 16f, -1 / 16f, 1 / 16f); // a píxeles y con y hacia arriba
		ps.translate(m.offset()[0], m.offset()[1], m.offset()[2]);
		rotate(ps, m.rot());
		if (m.aura() != null) aura(m.aura(), ps, out, time);
		for (int i = 0; i < m.parts().length; i++) {
			Part p = m.parts()[i];
			if (p.solid() == null && p.glow() == null) continue;
			ps.pushPose();
			applyPart(ps, m, i, time, move);
			float bright = pulse(p, time);
			if (p.solid() != null) {
				Mesh mesh = p.solid();
				out.submitCustomGeometry(ps, RenderTypes.entityTranslucent(m.texture()), (pose, vc) -> emit(pose, vc, mesh, light, 1f));
			}
			if (p.glow() != null) {
				Mesh mesh = p.glow();
				out.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(m.texture()), (pose, vc) -> emit(pose, vc, mesh, FULL_LIGHT, bright));
			}
			ps.popPose();
		}
		ps.popPose();
	}

	/** Coloca una parte: primero la de su padre (se mueve con él) y luego la suya. */
	private static void applyPart(PoseStack ps, Model m, int i, float time, Pose pose) {
		Part p = m.parts()[i];
		if (p.parent() >= 0 && p.parent() != i) applyPart(ps, m, p.parent(), time, pose);
		ps.translate(p.pivot()[0], p.pivot()[1], p.pivot()[2]);
		if (p.kf().isEmpty()) {
			rotate(ps, p.rot());
		} else {
			// mezcla de estados: quieto (o agachado) y andando, según lo rápido que va el jugador
			float[] r = new float[3], pos = new float[3], scl = new float[3];
			String base = pose.sneak() && m.states().containsKey("sneak") ? "sneak" : "idle";
			float w = !pose.sneak() && m.states().containsKey("walk") ? Math.max(0, Math.min(1, pose.walk())) : 0;
			sample(m, p, base, time, 1 - w, r, pos, scl);
			if (w > 0) sample(m, p, "walk", time, w, r, pos, scl);
			ps.translate(pos[0], pos[1], pos[2]);
			rotate(ps, p.rot());
			rotate(ps, r);
			ps.scale(Math.max(1e-3f, scl[0]), Math.max(1e-3f, scl[1]), Math.max(1e-3f, scl[2])); // nunca 0: las normales se romperían
		}
		for (Anim a : p.anims()) animate(ps, a, time);
		ps.translate(-p.pivot()[0], -p.pivot()[1], -p.pivot()[2]);
	}

	/** Suma (con peso w) el giro, la posición y la escala de una parte en un estado; sin pistas, la pose de reposo. */
	private static void sample(Model m, Part p, String state, float time, float w, float[] r, float[] pos, float[] scl) {
		Clip c = p.kf().get(state);
		Float len = m.states().get(state);
		float t = len == null || len <= 0 ? 0 : time % len;
		for (int a = 0; a < 3; a++) {
			r[a] += w * track(c == null ? null : c.rot(), t, a, 0);
			pos[a] += w * track(c == null ? null : c.pos(), t, a, 0);
			scl[a] += w * track(c == null ? null : c.scl(), t, a, 1);
		}
	}

	/** Valor de una pista (eje a) en el tiempo t. El suavizado va en el fotograma de llegada. */
	private static float track(float[] k, float t, int a, float def) {
		if (k == null || k.length < 5) return def;
		if (t <= k[0]) return k[1 + a];
		for (int i = 5; i < k.length; i += 5) {
			if (t <= k[i]) {
				float t0 = k[i - 5], t1 = k[i];
				float x = t1 > t0 ? (t - t0) / (t1 - t0) : 1;
				float e = ease((int) k[i + 4], x);
				return k[i - 4 + a] + (k[i + 1 + a] - k[i - 4 + a]) * e;
			}
		}
		return k[k.length - 4 + a];
	}

	private static float ease(int type, float x) {
		return switch (type) {
			case 1 -> x < 1 ? 0 : 1; // step
			case 2 -> x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2; // smooth (cúbico)
			case 3 -> x < 0.5f ? 2 * x * x : 1 - (float) Math.pow(-2 * x + 2, 2) / 2; // quad
			case 4 -> (float) (-(Math.cos(Math.PI * x) - 1) / 2); // sine
			case 5 -> { // back: se pasa un poco y vuelve
				float c1 = 1.70158f, c3 = c1 + 1;
				yield 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
			}
			default -> x;
		};
	}

	/** Rotación fija en grados: primero X, luego Y, luego Z (igual que el generador de iconos). */
	private static void rotate(PoseStack ps, float[] r) {
		if (r[2] != 0) ps.rotate(Axis.ZP.rotationDegrees(r[2]));
		if (r[1] != 0) ps.rotate(Axis.YP.rotationDegrees(r[1]));
		if (r[0] != 0) ps.rotate(Axis.XP.rotationDegrees(r[0]));
	}

	private static float wave(Anim a, float time) {
		return (float) Math.sin((time * a.speed() + a.phase()) * Math.PI * 2);
	}

	private static float hop(Anim a, float time) {
		return (float) Math.abs(Math.sin((time * a.speed() + a.phase()) * Math.PI));
	}

	private static void animate(PoseStack ps, Anim a, float time) {
		switch (a.type()) {
			case "bob" -> ps.translate(0, a.amp() * wave(a, time), 0);
			case "hop" -> {
				// salta; al tocar el suelo se aplasta (más ancho y más bajo) y en el aire se estira un poco
				float hop = hop(a, time);
				float squash = (float) Math.pow(1 - hop, 4);
				ps.translate(0, a.amp() * hop, 0);
				ps.scale(1 + 0.14f * squash - 0.03f * hop, 1 - 0.22f * squash + 0.06f * hop, 1 + 0.14f * squash - 0.03f * hop);
			}
			case "shadowHop" -> {
				float s = 1 - a.amp() * hop(a, time);
				ps.scale(s, 1, s);
			}
			case "blink" -> {
				// parpadeo corto de vez en cuando
				float p = (float) ((time * a.speed() + a.phase()) % 1.0);
				if (p < 0.06f) ps.scale(1, 1 - 0.9f * (float) Math.sin(p / 0.06f * Math.PI), 1);
			}
			case "sway", "flap" -> ps.rotate(Axis.YP.rotationDegrees(a.amp() * wave(a, time)));
			case "flapZ" -> ps.rotate(Axis.ZP.rotationDegrees(a.amp() * wave(a, time)));
			case "spin" -> ps.rotate(Axis.YP.rotationDegrees(time * a.speed() * 360));
			default -> {
			}
		}
	}

	/** Brillo de lo que brilla en esta parte (con la animación "pulse", sube y baja suavemente). */
	private static float pulse(Part p, float time) {
		for (Anim a : p.anims()) if (a.type().equals("pulse")) return 1 + a.amp() * wave(a, time);
		return 1f;
	}

	private static void emit(PoseStack.Pose pose, VertexConsumer vc, Mesh mesh, int light, float bright) {
		float[] p = mesh.pos();
		int[] c = mesh.col();
		float[] n = mesh.nrm();
		float[] t = mesh.uv();
		for (int i = 0; i < mesh.vertices(); i++) {
			int col = bright == 1f ? c[i] : scale(c[i], bright);
			vc.addVertex(pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).setColor(col).setUv(t[i * 2], t[i * 2 + 1])
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[i * 3], n[i * 3 + 1], n[i * 3 + 2]);
		}
	}

	private static int scale(int argb, float k) {
		int r = Math.min(255, Math.round(((argb >> 16) & 255) * k));
		int g = Math.min(255, Math.round(((argb >> 8) & 255) * k));
		int b = Math.min(255, Math.round((argb & 255) * k));
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	// ---------- Auras ----------

	/** Auras: luces que giran o chispas que suben alrededor del jugador (el cuerpo va de y -24 a 0), con halo y destellos. */
	private static void aura(Aura a, PoseStack ps, SubmitNodeCollector out, float time) {
		out.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(WHITE), (pose, vc) -> {
			if (a.type().equals("orbit")) {
				double[] tilts = {0, Math.toRadians(55), Math.toRadians(-55)};
				for (int k = 0; k < 3; k++) {
					int color = a.colors()[k % a.colors().length];
					float twinkle = 1 + 0.15f * (float) Math.sin(time * 6 + k * 2);
					for (int j = 0; j <= a.trail(); j++) {
						double ang = (time * a.speed() + k / 3.0) * Math.PI * 2 - j * 0.14;
						float fade = 1 - j / (float) (a.trail() + 1);
						float size = 1.3f * (0.3f + 0.7f * fade) * (j == 0 ? twinkle : 1);
						float x = (float) (Math.cos(ang) * a.radius());
						double z0 = Math.sin(ang) * a.radius();
						float y = (float) (z0 * Math.sin(tilts[k])) - 12;
						float z = (float) (z0 * Math.cos(tilts[k]));
						cube(pose, vc, x, y, z, size, alpha(color, fade));
						if (j == 0) ball(pose, vc, x, y, z, size * 1.3f, size * 1.3f, size * 1.3f, alpha(color, 0.14f));
					}
				}
			} else {
				for (int i = 0; i < a.count(); i++) {
					float p = (float) ((time * a.speed() + i / (float) a.count()) % 1.0);
					double ang = p * Math.PI * 2.6 + i * 2.4;
					float r = a.radius() - p * 3.5f;
					float fade = (float) Math.sin(p * Math.PI);
					float size = 1.0f * (1 - p * 0.45f);
					float x = (float) (Math.cos(ang) * r), y = -24 + p * 31, z = (float) (Math.sin(ang) * r);
					int color = a.colors()[i % a.colors().length];
					cube(pose, vc, x, y, z, size, alpha(color, fade));
					ball(pose, vc, x, y, z, size * 1.2f, size * 1.2f, size * 1.2f, alpha(color, fade * 0.12f));
					// algunas son estrellitas (dos palitos cruzados)
					if (i % 4 == 0) {
						float s = size * (1.6f + 0.6f * (float) Math.sin(time * 8 + i));
						flat(pose, vc, x, y, z, s, size * 0.25f, alpha(0xFFFFFFFF, fade * 0.8f));
						flat(pose, vc, x, y, z, size * 0.25f, s, alpha(0xFFFFFFFF, fade * 0.8f));
					}
				}
			}
		});
	}

	private static int alpha(int argb, float a) {
		return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (argb & 0xFFFFFF);
	}

	/** Esfera de radio 1 en grupos de 4 vértices (posición = normal), para las partículas de las auras. */
	private static final float[] SPHERE = sphere(6, 10);

	private static float[] sphere(int rows, int cols) {
		float[] out = new float[rows * cols * 12];
		int k = 0;
		for (int i = 0; i < rows; i++) for (int j = 0; j < cols; j++) {
			int[][] c = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
			for (int[] v : c) {
				double t = Math.PI * v[0] / rows, a = 2 * Math.PI * v[1] / cols;
				out[k++] = (float) (Math.sin(t) * Math.cos(a));
				out[k++] = (float) Math.cos(t);
				out[k++] = (float) (Math.sin(t) * Math.sin(a));
			}
		}
		return out;
	}

	private static void cube(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz, float s, int argb) {
		// partícula cuadrada (estilo pixel, como las de Minecraft)
		float h = s / 2;
		for (int f = 0; f < 6; f++) {
			for (int v = 0; v < 4; v++) {
				int[] k = FACE_C[f][v];
				vc.addVertex(pose, cx - h + k[0] * s, cy - h + k[1] * s, cz - h + k[2] * s).setColor(argb).setUv(0, 0)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_LIGHT).setNormal(pose, FACE_N[f][0], FACE_N[f][1], FACE_N[f][2]);
			}
		}
	}

	private static void flat(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz, float w, float h, int argb) {
		ball(pose, vc, cx, cy, cz, w / 2, h / 2, 0.06f, argb);
	}

	private static void ball(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz, float rx, float ry, float rz, int argb) {
		for (int i = 0; i < SPHERE.length; i += 3) {
			float x = SPHERE[i], y = SPHERE[i + 1], z = SPHERE[i + 2];
			vc.addVertex(pose, cx + x * rx, cy + y * ry, cz + z * rz).setColor(argb).setUv(0, 0)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_LIGHT).setNormal(pose, x, y, z);
		}
	}
}
