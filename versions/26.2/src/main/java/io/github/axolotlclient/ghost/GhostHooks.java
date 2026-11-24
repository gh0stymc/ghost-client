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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;
import io.github.axolotlclient.util.notifications.Notifications;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * ghost client: une la parte social y los cosméticos (comunes) con esta versión de Minecraft.
 */
public final class GhostHooks {
	// Texturas de cada capa (24 fotogramas), se preparan la primera vez que hacen falta
	private static final Map<String, Identifier[]> CAPE_TEXTURES = new ConcurrentHashMap<>();
	private static final Map<String, ClientAsset.Texture[]> CAPE_ASSETS = new ConcurrentHashMap<>();
	private static final Map<UUID, Supplier<PlayerSkin>> SKINS = new ConcurrentHashMap<>();
	private static int ticks;

	private static String known(String cape) {
		return GhostCosmetics.CAPES.contains(cape) ? cape : GhostCosmetics.GHOST_CAPE;
	}

	private static Identifier[] textures(String cape) {
		return CAPE_TEXTURES.computeIfAbsent(known(cape), id -> {
			Identifier[] out = new Identifier[GhostCosmetics.CAPE_FRAMES];
			for (int i = 0; i < out.length; i++) out[i] = Identifier.fromNamespaceAndPath("axolotlclient", "textures/ghost/cosmetics/" + id + "/" + i + ".png");
			return out;
		});
	}

	private static ClientAsset.Texture[] assets(String cape) {
		return CAPE_ASSETS.computeIfAbsent(known(cape), id -> {
			Identifier[] tex = textures(id);
			ClientAsset.Texture[] out = new ClientAsset.Texture[tex.length];
			for (int i = 0; i < out.length; i++) out[i] = new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("axolotlclient", "ghost/cosmetics/" + id + "/" + i), tex[i]);
			return out;
		});
	}

	private GhostHooks() {
	}

	public static void init() {
		GhostSocial.get().init(() -> {
			var user = Minecraft.getInstance().getUser();
			return user == null ? null : new GhostSocial.Session(user.getName(), user.getProfileId(), user.getAccessToken());
		}, new GhostSocial.Events() {
			@Override
			public void friendRequest(String name) {
				toast("ghost.social.toast.request", name);
			}

			@Override
			public void friendAdded(String name) {
				toast("ghost.social.toast.added", name);
			}

			@Override
			public void friendOnline(String name) {
				toast("ghost.social.toast.online", name);
			}

			@Override
			public void invite(String fromName, String server) {
				Minecraft.getInstance().execute(() -> Notifications.getInstance().addStatus(
					Component.translatable("ghost.social.toast.invite", fromName), Component.translatable("ghost.social.toast.invite.text", server)));
			}

			@Override
			public void message(String fromName, String text) {
				Minecraft.getInstance().execute(() -> Notifications.getInstance().addStatus(
					Component.translatable("ghost.social.toast.message", fromName), Component.literal(text.length() > 60 ? text.substring(0, 57) + "…" : text)));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
	}

	private static void toast(String key, String name) {
		Minecraft.getInstance().execute(() -> Notifications.getInstance().addStatus(
			Component.literal("ghost client"), Component.translatable(key, name)));
	}

	/** Cada segundo: qué estás haciendo (para tus amigos) y qué jugadores hay cerca (para sus capas). */
	private static boolean debugWardrobeOpened;

	private static void tick() {
		if (++ticks % 20 != 0) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			GhostSocial.get().setStatus("menu", null);
			GhostStats.tick("menu", null);
		} else if (mc.isLocalServer()) {
			GhostSocial.get().setStatus("singleplayer", null);
			GhostStats.tick("singleplayer", null);
		} else {
			var server = mc.getCurrentServer();
			GhostSocial.get().setStatus("server", server == null ? null : server.ip);
			GhostStats.tick("server", server == null ? null : server.ip);
		}
		// Prueba automática: -Dghostclient.debug.thirdperson=true pone la cámara detrás para ver la capa
		if (Boolean.getBoolean("ghostclient.debug.thirdperson") && mc.level != null && mc.options.getCameraType() != net.minecraft.client.CameraType.THIRD_PERSON_BACK) {
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		}
		// Prueba automática: -Dghostclient.debug.wardrobe=<pestaña> abre el vestidor en esa pestaña al entrar al mundo
		String wardrobe = System.getProperty("ghostclient.debug.wardrobe");
		if (wardrobe != null && !debugWardrobeOpened && mc.level != null && mc.player != null && ticks > 100) {
			debugWardrobeOpened = true;
			GhostWardrobeScreen.debugTab(wardrobe);
			Minecraft minecraft = mc;
			minecraft.gui.setScreen(new GhostWardrobeScreen(null));
		}
		var connection = mc.getConnection();
		if (connection != null) GhostCosmetics.get().want(connection.getOnlinePlayerIds());
	}

	// ---------- Capas ----------

	/** Textura de una capa en el fotograma actual (para dibujarla en los menús). */
	public static Identifier capeTexture(String cape) {
		return textures(cape)[GhostCosmetics.capeFrame()];
	}

	/** La skin del jugador con la capa de ghost client puesta (si lleva una). */
	public static PlayerSkin withCape(PlayerSkin skin, String cape) {
		if (cape == null || skin == null) return skin;
		return new PlayerSkin(skin.body(), assets(cape)[GhostCosmetics.capeFrame()], skin.elytra(), skin.model(), skin.secure());
	}

	public static Identifier icon(String name) {
		return Identifier.fromNamespaceAndPath("axolotlclient", "textures/ghost/icons/" + name + ".png");
	}

	// ---------- Skins de otros jugadores (amigos) ----------

	/** Skin de un jugador por su UUID: primero la de por defecto y, en cuanto llega de Mojang, la suya. */
	public static Supplier<PlayerSkin> skin(UUID uuid, String name) {
		return SKINS.computeIfAbsent(uuid, u -> {
			Minecraft mc = Minecraft.getInstance();
			AtomicReference<Supplier<PlayerSkin>> ref = new AtomicReference<>(mc.getSkinManager().createLookup(new GameProfile(u, name == null ? "?" : name), false));
			CompletableFuture.supplyAsync(() -> mc.services().sessionService().fetchProfile(u, false), Util.nonCriticalIoPool())
				.thenAccept(result -> {
					if (result != null) ref.set(mc.getSkinManager().createLookup(result.profile(), false));
				}).exceptionally(e -> null);
			return () -> ref.get().get();
		});
	}

	// ---------- Unirse a un servidor ----------

	/** El servidor en el que estás ahora (IP tal y como la escribiste), o null. */
	public static String currentServer() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.isLocalServer()) return null;
		var server = mc.getCurrentServer();
		return server == null ? null : server.ip;
	}

	/** Sale del mundo en el que estés y entra en ese servidor (para unirse a un amigo). */
	public static void join(String ip) {
		if (ip == null || ip.isBlank()) return;
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (ip.equalsIgnoreCase(currentServer())) {
				mc.gui.setScreen(null);
				return;
			}
			if (mc.level != null) mc.disconnectFromWorld(Component.translatable("ghost.social.joining"));
			Screen parent = new TitleScreen();
			ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(ip), new ServerData(ip, ip, ServerData.Type.OTHER), false, null);
		});
	}

	public static UUID uuid(String undashed) {
		return UUID.fromString(undashed.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
	}
}
