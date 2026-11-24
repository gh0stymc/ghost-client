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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * ghost client: cosméticos propios (gratis). Solo los ven los jugadores que también usan ghost client.
 * Aquí se guarda qué lleva cada jugador; el servidor de ghost client es quien lo sabe.
 */
public final class GhostCosmetics {
	private static final GhostCosmetics INSTANCE = new GhostCosmetics();

	public static GhostCosmetics get() {
		return INSTANCE;
	}

	/** Capas disponibles (id del servidor). Las texturas están en textures/ghost/cosmetics/&lt;id&gt;/. */
	public static final String GHOST_CAPE = "ghost_cape";
	public static final List<String> CAPES = List.of(GHOST_CAPE, "ghost_cape_pink", "ghost_cape_white", "ghost_cape_orange",
		"ghost_cape_red", "ghost_cape_green", "ghost_cape_blue", "ghost_cape_halloween", "ghost_cape_christmas",
		"cape_aurora", "cape_moon", "cape_sakura", "cape_sunset", "cape_waves", "cape_eclipse",
		"cape_medal_500", "cape_medal_750", "cape_medal_1000", "cape_medal_2500", "cape_medal_5000");
	/** Horas jugadas (en el launcher) que hacen falta para ponerse cada cosmético de medalla. */
	public static final Map<String, Integer> UNLOCK = Map.of("cape_medal_500", 500, "cape_medal_750", 750, "cape_medal_1000", 1000,
		"cape_medal_2500", 2500, "cape_medal_5000", 5000);
	/** Cosméticos 3D: el modelo está en ghost/models/&lt;id&gt;.json y el icono en textures/ghost/cosmetics/&lt;id&gt;/preview.png. */
	public static final List<String> PETS = List.of("pet_boo", "pet_slime", "pet_bat", "pet_dragon");
	public static final List<String> HATS = List.of("hat_crown", "hat_witch", "hat_halo", "hat_cat_ears");
	public static final List<String> BACKS = List.of("back_wings", "back_sword", "back_dragon_wings");
	public static final List<String> AURAS = List.of("aura_orbit", "aura_sparks", "aura_frost", "aura_flames");

	/** Horas que faltan para desbloquearlo (0 = ya se puede poner). */
	public static int hoursLeft(String id) {
		Integer need = id == null ? null : UNLOCK.get(id);
		if (need == null) return 0;
		return Math.max(0, need - GhostSocial.get().hours());
	}
	/** Huecos: uno de cada a la vez. */
	public static final List<String> SLOTS = List.of("cape", "pet", "hat", "back", "aura");

	public static List<String> idsFor(String slot) {
		return switch (slot) {
			case "pet" -> PETS;
			case "hat" -> HATS;
			case "back" -> BACKS;
			case "aura" -> AURAS;
			default -> CAPES;
		};
	}

	/** Lee los huecos de un objeto del servidor ({ cape, pet, hat, back, aura }). */
	public static Map<String, String> slots(JsonObject o) {
		Map<String, String> out = new HashMap<>();
		for (String slot : SLOTS) {
			JsonElement v = o.get(slot);
			if (v != null && !v.isJsonNull()) out.put(slot, v.getAsString());
		}
		return out;
	}
	/** Todas las capas son animaciones: 24 fotogramas, uno cada 85 ms (unos 2 segundos el bucle). */
	public static final int CAPE_FRAMES = 24;
	private static final long FRAME_MS = 85;

	/** ghost: ha usado ghost client alguna vez; online: lo está usando ahora mismo (icono junto al nombre). */
	private record Entry(Map<String, String> slots, boolean ghost, boolean online, long at) {
	}

	/** Vista previa en el vestidor: lo que se ve puesto en ti mientras pasas el ratón (Optional.empty() = nada). */
	private final Map<String, Optional<String>> preview = new ConcurrentHashMap<>();

	private final Map<String, Entry> cache = new ConcurrentHashMap<>();
	private final Set<String> pending = ConcurrentHashMap.newKeySet();
	private volatile boolean flushing;

	private GhostCosmetics() {
		GhostSocial.get().scheduler().scheduleAtFixedRate(this::flush, 3, 2, TimeUnit.SECONDS);
	}

	public static String key(UUID uuid) {
		return uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
	}

	public void put(String uuid, Map<String, String> slots, boolean ghostUser) {
		put(uuid, slots, ghostUser, ghostUser);
	}

	public void put(String uuid, Map<String, String> slots, boolean ghostUser, boolean online) {
		cache.put(uuid, new Entry(Map.copyOf(slots), ghostUser, online, System.currentTimeMillis()));
		pending.remove(uuid);
	}

	/** Lo que responde el servidor por cada jugador: null si nunca ha usado ghost client. */
	void putFromServer(String uuid, JsonElement v) {
		if (v == null || v.isJsonNull()) {
			put(uuid, Map.of(), false, false);
			return;
		}
		JsonObject o = v.getAsJsonObject();
		// Servidores viejos no mandan "online": entonces vale con que use ghost client
		boolean online = !o.has("online") || o.get("online").getAsBoolean();
		put(uuid, slots(o), true, online);
	}

	/** Enseña en ti un cosmético sin ponértelo (null = quitar la vista previa de ese hueco). */
	public void preview(String slot, String id, boolean active) {
		if (!active) preview.remove(slot);
		else preview.put(slot, Optional.ofNullable(id));
	}

	public void clearPreview() {
		preview.clear();
	}

	private boolean isMe(String uuid) {
		var me = GhostSocial.get().me();
		return me != null && me.uuid().equals(uuid);
	}

	/** Capa de ghost client de un jugador, o null. Lo propio se ve siempre; lo de los demás, si está activado. */
	public String capeOf(UUID uuid) {
		return of(uuid, "cape");
	}

	/** Cosmético de un jugador en un hueco (cape, pet, hat, back, aura), o null. */
	public String of(UUID uuid, String slot) {
		String k = key(uuid);
		boolean me = isMe(k);
		if (!GhostPrefs.showCosmetics && !me) return null;
		if (me) {
			Optional<String> p = preview.get(slot);
			if (p != null) return p.orElse(null);
		}
		Entry e = cache.get(k);
		return e == null ? null : e.slots().get(slot);
	}

	/** Si sabemos que ese jugador usa ghost client. */
	public boolean isGhostUser(UUID uuid) {
		Entry e = cache.get(key(uuid));
		return e != null && e.ghost();
	}

	/**
	 * Si ese jugador está usando ghost client ahora mismo: se le pone el fantasma junto al nombre
	 * (encima de la cabeza y en el tabulador). Tú siempre lo llevas.
	 */
	public boolean isGhostOnline(String uuid) {
		if (uuid == null) return false;
		String k = uuid.replace("-", "").toLowerCase(Locale.ROOT);
		if (isMe(k)) return true;
		var me = GhostSocial.get().me();
		if (me == null) {
			// Aún sin conexión con el servidor: al menos tú (la cuenta con la que juegas)
			var s = GhostSocial.get().session();
			if (s != null && s.uuid() != null && key(s.uuid()).equals(k)) return true;
		}
		Entry e = cache.get(k);
		if (e == null) {
			// Aún no se ha preguntado por él (p. ej. aparece antes de salir en el tabulador)
			try {
				want(List.of(UUID.fromString(uuid.contains("-") ? uuid : uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"))));
			} catch (IllegalArgumentException ignored) {
			}
			return false;
		}
		return e.online();
	}

	/**
	 * Jugadores que hay cerca (lista del tabulador): se pregunta por los que no conocemos o hace tiempo que no.
	 * Los de ghost client se refrescan cada minuto (por si se cambian de capa), el resto cada 5 minutos.
	 */
	public void want(Collection<UUID> players) {
		long now = System.currentTimeMillis();
		for (UUID u : players) {
			if (u.version() != 4) continue; // jugadores de verdad (los NPC suelen tener otra versión de UUID)
			String k = key(u);
			Entry e = cache.get(k);
			long maxAge = e != null && e.ghost() ? 60_000 : 300_000;
			if (e == null || now - e.at() > maxAge) pending.add(k);
		}
	}

	private void flush() {
		if (pending.isEmpty() || flushing) return;
		flushing = true;
		try {
			List<String> batch = new ArrayList<>();
			for (String k : pending) {
				batch.add(k);
				if (batch.size() >= 100) break;
			}
			// Se marcan ya como pedidos para no repetirlos si el servidor tarda
			long now = System.currentTimeMillis();
			for (String k : batch) {
				pending.remove(k);
				cache.compute(k, (kk, old) -> old == null ? new Entry(Map.of(), false, false, now) : new Entry(old.slots(), old.ghost(), old.online(), now));
			}
			GhostSocial.get().requestCosmetics(batch);
		} finally {
			flushing = false;
		}
	}

	/** Fotograma de la animación de la capa que toca ahora. */
	public static int capeFrame() {
		return (int) ((System.currentTimeMillis() / FRAME_MS) % CAPE_FRAMES);
	}
}
