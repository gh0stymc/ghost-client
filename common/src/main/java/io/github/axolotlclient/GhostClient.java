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

package io.github.axolotlclient;

/**
 * Identidad de ghost client.
 */
public final class GhostClient {
	/** Nombre visible del cliente. */
	public static final String NAME = "ghost client";

	/**
	 * Las funciones online (amigos, chat, insignias, noticias) usan los servidores del proyecto original.
	 * En ghost client van desactivadas y ocultas.
	 */
	public static final boolean ONLINE_FEATURES = false;

	/**
	 * ID de la aplicación de Discord "ghost client" (discord.com/developers/applications).
	 * Discord muestra el nombre y el icono de esa aplicación. Con 0, la integración con Discord queda desactivada.
	 */
	public static final long DISCORD_APP_ID = 0L;

	/**
	 * ID de Discord a usar: el que pasa Ghost Launcher al arrancar (-Dghostclient.discordAppId=...),
	 * o si no, el de {@link #DISCORD_APP_ID}.
	 */
	public static long discordAppId() {
		String fromLauncher = System.getProperty("ghostclient.discordAppId");
		if (fromLauncher != null) {
			try {
				return Long.parseLong(fromLauncher.trim());
			} catch (NumberFormatException ignored) {
			}
		}
		return DISCORD_APP_ID;
	}

	private GhostClient() {
	}
}
