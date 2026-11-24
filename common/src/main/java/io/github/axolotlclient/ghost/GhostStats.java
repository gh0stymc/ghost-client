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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

/**
 * ghost client: estadísticas de cada servidor (asesinatos, muertes, mejor racha y tiempo jugado).
 * Se guardan en config/ghostclient/stats.json de cada perfil; el launcher las lee para enseñar las gráficas.
 * <pre>
 * { "version": 1,
 *   "servers": { "hypixel.net": { "kills", "deaths", "bestStreak", "seconds", "first", "last" } },
 *   "days": { "2026-11-08": { "hypixel.net": { "k", "d", "s" } } } }
 * </pre>
 */
public final class GhostStats {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ghostclient").resolve("stats.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern IP = Pattern.compile("^[\\d.]+$|^\\[?[0-9a-f:]+]?$");
	/** Un jugador en este PC. */
	public static final String SINGLEPLAYER = "singleplayer";

	private static JsonObject data;
	private static String current;
	private static int streak;
	private static boolean dirty;
	private static long lastSave;

	private GhostStats() {
	}

	/**
	 * Nombre del servidor para agrupar: sin puerto y sin subdominios (mc.hypixel.net y play.hypixel.net son el mismo).
	 */
	public static String serverKey(String ip) {
		if (ip == null || ip.isBlank()) return null;
		String host = ip.trim().toLowerCase(Locale.ROOT);
		if (host.startsWith("[")) return host.replaceAll("]:\\d+$", "]");
		host = host.replaceAll(":\\d+$", "").replaceAll("\\.$", "");
		if (IP.matcher(host).matches()) return host;
		String[] parts = host.split("\\.");
		if (parts.length <= 2) return host;
		// dominios como algo.co.uk o algo.com.br: se quedan con tres partes
		String second = parts[parts.length - 2];
		boolean shortTld = parts[parts.length - 1].length() == 2;
		int keep = shortTld && (second.equals("co") || second.equals("com") || second.equals("net") || second.equals("org")) ? 3 : 2;
		return String.join(".", java.util.Arrays.copyOfRange(parts, parts.length - keep, parts.length));
	}

	private static synchronized JsonObject data() {
		if (data != null) return data;
		try {
			if (Files.exists(FILE)) data = GSON.fromJson(Files.readString(FILE), JsonObject.class);
		} catch (Exception ignored) {
		}
		if (data == null) data = new JsonObject();
		data.addProperty("version", 1);
		if (!data.has("servers")) data.add("servers", new JsonObject());
		if (!data.has("days")) data.add("days", new JsonObject());
		return data;
	}

	private static JsonObject server(String key) {
		JsonObject servers = data().getAsJsonObject("servers");
		JsonObject s = servers.getAsJsonObject(key);
		if (s == null) {
			s = new JsonObject();
			s.addProperty("first", System.currentTimeMillis());
			servers.add(key, s);
		}
		s.addProperty("last", System.currentTimeMillis());
		return s;
	}

	private static JsonObject today(String key) {
		JsonObject days = data().getAsJsonObject("days");
		String d = LocalDate.now().toString();
		JsonObject day = days.getAsJsonObject(d);
		if (day == null) {
			day = new JsonObject();
			days.add(d, day);
		}
		JsonObject s = day.getAsJsonObject(key);
		if (s == null) {
			s = new JsonObject();
			day.add(key, s);
		}
		return s;
	}

	private static void add(JsonObject o, String field, long n) {
		o.addProperty(field, (o.has(field) ? o.get(field).getAsLong() : 0) + n);
	}

	/**
	 * Cada segundo, desde el juego: dónde estás. activity = menu, singleplayer o server.
	 * Suma el tiempo jugado en ese sitio y guarda de vez en cuando.
	 */
	public static synchronized void tick(String activity, String serverIp) {
		String key = "server".equals(activity) ? serverKey(serverIp) : "singleplayer".equals(activity) ? SINGLEPLAYER : null;
		if (key == null || !key.equals(current)) streak = 0;
		current = key;
		if (key != null) {
			add(server(key), "seconds", 1);
			add(today(key), "s", 1);
			dirty = true;
		}
		long now = System.currentTimeMillis();
		if (dirty && (key == null || now - lastSave > 30_000)) save();
	}

	/** Has matado a un jugador. */
	public static synchronized void kill() {
		if (current == null) return;
		JsonObject s = server(current);
		add(s, "kills", 1);
		add(today(current), "k", 1);
		streak++;
		if (!s.has("bestStreak") || s.get("bestStreak").getAsInt() < streak) s.addProperty("bestStreak", streak);
		dirty = true;
		save();
	}

	/** Has muerto. */
	public static synchronized void death() {
		if (current == null) return;
		add(server(current), "deaths", 1);
		add(today(current), "d", 1);
		streak = 0;
		dirty = true;
		save();
	}

	/** Racha actual (asesinatos sin morir). */
	public static int streak() {
		return streak;
	}

	private static synchronized void save() {
		if (!dirty) return;
		dirty = false;
		lastSave = System.currentTimeMillis();
		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling("stats.json.tmp");
			Files.writeString(tmp, GSON.toJson(data()));
			Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			try {
				Files.writeString(FILE, GSON.toJson(data()));
			} catch (Exception ignored) {
			}
		}
	}
}
