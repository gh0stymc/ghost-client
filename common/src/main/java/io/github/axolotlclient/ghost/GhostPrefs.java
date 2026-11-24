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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

/**
 * ghost client: preferencias de la parte social y de los cosméticos (config/ghostclient/social.json).
 */
public final class GhostPrefs {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ghostclient").resolve("social.json");
	private static final Gson GSON = new Gson();

	/** Ver los cosméticos de ghost client de los demás jugadores. */
	public static volatile boolean showCosmetics = true;
	/** Que los amigos vean en qué servidor juegas. */
	public static volatile boolean showServer = true;
	/** Avisos de mensajes, solicitudes y amigos que se conectan. */
	public static volatile boolean notifications = true;

	static {
		load();
	}

	private GhostPrefs() {
	}

	private static void load() {
		try {
			if (!Files.exists(FILE)) return;
			JsonObject o = GSON.fromJson(Files.readString(FILE), JsonObject.class);
			if (o == null) return;
			if (o.has("showCosmetics")) showCosmetics = o.get("showCosmetics").getAsBoolean();
			if (o.has("showServer")) showServer = o.get("showServer").getAsBoolean();
			if (o.has("notifications")) notifications = o.get("notifications").getAsBoolean();
		} catch (Exception ignored) {
		}
	}

	public static void save() {
		try {
			JsonObject o = new JsonObject();
			o.addProperty("showCosmetics", showCosmetics);
			o.addProperty("showServer", showServer);
			o.addProperty("notifications", notifications);
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(o));
		} catch (Exception ignored) {
		}
	}
}
