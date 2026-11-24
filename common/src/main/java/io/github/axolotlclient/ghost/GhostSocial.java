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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.axolotlclient.GhostClient;
import io.github.axolotlclient.bridge.PlatformDispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ghost client: la parte social. Se conecta al servidor de ghost client para los amigos, su estado, el chat
 * y los cosméticos. Todo funciona en segundo plano; las pantallas leen el estado y miran {@link #revision()}
 * para saber si algo ha cambiado.
 */
public final class GhostSocial {
	private static final GhostSocial INSTANCE = new GhostSocial();
	private static final Logger LOG = LoggerFactory.getLogger("ghost client social");
	private static final Gson GSON = new Gson();

	public static GhostSocial get() {
		return INSTANCE;
	}

	public enum State { OFFLINE, CONNECTING, ONLINE, NO_ACCOUNT, UNREACHABLE }

	/** Cuenta de Minecraft con la que se juega (la pone cada versión del mod). */
	public record Session(String name, UUID uuid, String accessToken) {
	}

	public record UserRef(String uuid, String name) {
	}

	public record Message(long id, String from, String to, String text, long ts, String nonce) {
		public boolean pending() {
			return id < 0;
		}
	}

	public static final class Friend {
		public final String uuid;
		public volatile String name;
		public volatile boolean online;
		/** menu, singleplayer o server */
		public volatile String activity;
		public volatile String server;
		public volatile long lastSeen;
		public volatile int unread;

		Friend(String uuid) {
			this.uuid = uuid;
		}
	}

	/** Avisos para el jugador (cada versión los enseña como notificación). */
	public interface Events {
		default void friendRequest(String name) {
		}

		default void friendAdded(String name) {
		}

		default void friendOnline(String name) {
		}

		default void message(String fromName, String text) {
		}

		/** Un amigo te invita a su servidor. */
		default void invite(String fromName, String server) {
		}
	}

	/** Invitación a un servidor (se puede aceptar durante 2 minutos). */
	public record Invite(String fromUuid, String fromName, String server, long at) {
		public boolean fresh() {
			return System.currentTimeMillis() - at < 120_000;
		}
	}

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "ghost client social");
		t.setDaemon(true);
		return t;
	});

	private volatile Supplier<Session> sessions = () -> null;
	private volatile Events events = new Events() {
	};
	private volatile State state = State.OFFLINE;
	private volatile WebSocket socket;
	private volatile String token;
	private volatile String tokenFor;
	private volatile int revision;
	private volatile boolean started;
	private volatile long retryDelay = 5;
	private volatile ScheduledFuture<?> retry;
	private volatile String lastStatus;

	private volatile UserRef me;
	/** Lo que llevo puesto en cada hueco (cape, pet, hat, back, aura). */
	private final Map<String, String> mine = new ConcurrentHashMap<>();
	private final Map<String, Friend> friends = new ConcurrentHashMap<>();
	private final List<UserRef> incoming = new CopyOnWriteArrayList<>();
	private final List<UserRef> outgoing = new CopyOnWriteArrayList<>();
	private final Map<String, List<Message>> chats = new ConcurrentHashMap<>();
	private final Set<String> historyLoaded = ConcurrentHashMap.newKeySet();
	private final Map<String, Boolean> historyMore = new ConcurrentHashMap<>();
	/** Conversación abierta en pantalla: sus mensajes se marcan como leídos solos. */
	private volatile String openChat;
	/** Último aviso para enseñar en las pantallas (clave de traducción + parámetro). */
	private volatile String noticeKey;
	private volatile String noticeArg;
	private volatile long noticeAt;
	/** Última invitación recibida. */
	private volatile Invite invite;
	/** Horas jugadas (las manda el launcher al servidor): desbloquean cosméticos. */
	private volatile int hours;

	private GhostSocial() {
	}

	// ---------- Arranque y conexión ----------

	/** Lo llama cada versión del mod al iniciar: de dónde sacar la cuenta y cómo avisar. */
	public void init(Supplier<Session> sessions, Events events) {
		this.sessions = sessions;
		this.events = events;
		if (started) return;
		started = true;
		scheduler.scheduleAtFixedRate(this::keepAlive, 25, 25, TimeUnit.SECONDS);
		connectLater(2);
	}

	public State state() {
		return state;
	}

	/** La cuenta con la que se juega ahora (aunque aún no haya conexión con el servidor). */
	public Session session() {
		try {
			return sessions.get();
		} catch (Exception e) {
			return null;
		}
	}

	public int revision() {
		return revision;
	}

	private void changed() {
		revision++;
	}

	/** Volver a intentarlo ya (botón "Reintentar"). */
	public void reconnect() {
		retryDelay = 5;
		closeSocket();
		connectLater(0);
	}

	private void connectLater(long seconds) {
		if (retry != null) retry.cancel(false);
		retry = scheduler.schedule(this::connect, seconds, TimeUnit.SECONDS);
	}

	private void connect() {
		if (socket != null) return;
		Session s = sessions.get();
		if (s == null || s.accessToken() == null || s.accessToken().isBlank() || s.uuid() == null) {
			setState(State.NO_ACCOUNT);
			connectLater(30);
			return;
		}
		setState(State.CONNECTING);
		try {
			if (token == null || !s.name().equals(tokenFor)) {
				token = login(s);
				tokenFor = s.name();
			}
			if (token == null) {
				setState(State.NO_ACCOUNT);
				connectLater(Math.min(retryDelay *= 2, 300));
				return;
			}
			String ws = GhostClient.server().replaceFirst("^http", "ws") + "/v1/ws?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
			socket = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
				.buildAsync(URI.create(ws), new Listener()).get(15, TimeUnit.SECONDS);
			retryDelay = 5;
			lastStatus = null;
		} catch (Exception e) {
			LOG.warn("No se pudo conectar con el servidor de ghost client: {}", e.toString());
			socket = null;
			// Si la sesión guardada ya no vale, se pide otra
			if (String.valueOf(e).contains("401")) token = null;
			setState(State.UNREACHABLE);
			connectLater(retryDelay);
			retryDelay = Math.min(retryDelay * 2, 120);
		}
	}

	/**
	 * Demuestra que somos el dueño de la cuenta y pide una sesión al servidor.
	 * Se usa el certificado de jugador de Mojang (la clave que firma el chat): firmamos un mensaje con nuestra clave y
	 * el servidor comprueba la firma y que Mojang firmó esa clave para nuestra UUID. El token de la cuenta no sale del PC.
	 */
	private String login(Session s) throws Exception {
		String uuid = s.uuid().toString().replace("-", "");
		JsonObject body = new JsonObject();
		body.addProperty("username", s.name());
		// Solo para pruebas con el servidor en local (el servidor de verdad no lo acepta nunca)
		if (Boolean.getBoolean("ghostclient.devAuth")) {
			body.addProperty("serverId", "dev");
			body.addProperty("devUuid", uuid);
		} else {
			HttpResponse<String> certRes = http.send(HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/player/certificates"))
				.timeout(Duration.ofSeconds(15))
				.header("authorization", "Bearer " + s.accessToken())
				.POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
			if (certRes.statusCode() != 200) {
				// Cuenta sin Minecraft comprado, sesión caducada, modo offline...
				LOG.warn("Mojang no dio el certificado de jugador (HTTP {})", certRes.statusCode());
				return null;
			}
			JsonObject cert = GSON.fromJson(certRes.body(), JsonObject.class);
			JsonObject pair = cert.getAsJsonObject("keyPair");
			String publicKey = pemBody(pair.get("publicKey").getAsString());
			java.security.PrivateKey privateKey = java.security.KeyFactory.getInstance("RSA")
				.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(Base64.getDecoder().decode(pemBody(pair.get("privateKey").getAsString()))));
			long timestamp = System.currentTimeMillis();
			java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA");
			signer.initSign(privateKey);
			signer.update(("ghostclient:auth:" + uuid + ":" + timestamp).getBytes(StandardCharsets.UTF_8));
			body.addProperty("uuid", uuid);
			body.addProperty("publicKey", publicKey);
			body.addProperty("expiresAt", java.time.Instant.parse(cert.get("expiresAt").getAsString()).toEpochMilli());
			body.addProperty("keySignature", cert.get("publicKeySignatureV2").getAsString());
			body.addProperty("timestamp", timestamp);
			body.addProperty("signature", Base64.getEncoder().encodeToString(signer.sign()));
		}
		HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(GhostClient.server() + "/v1/auth"))
			.timeout(Duration.ofSeconds(15))
			.header("content-type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build(), HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() == 401) {
			LOG.warn("El servidor de ghost client no aceptó la identidad");
			return null;
		}
		if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
		return GSON.fromJson(res.body(), JsonObject.class).get("token").getAsString();
	}

	/** Contenido de un PEM (sin cabeceras ni saltos de línea). */
	private static String pemBody(String pem) {
		return pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s+", "");
	}

	private void setState(State s) {
		if (state != s) {
			state = s;
			changed();
		}
	}

	private void closeSocket() {
		WebSocket ws = socket;
		socket = null;
		if (ws != null) {
			try {
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
			} catch (Exception ignored) {
			}
		}
	}

	private void keepAlive() {
		// Si cambia la cuenta (selector de cuentas), se vuelve a conectar con la nueva
		Session s = sessions.get();
		if (s != null && tokenFor != null && !s.name().equals(tokenFor)) {
			token = null;
			friends.clear();
			chats.clear();
			historyLoaded.clear();
			reconnect();
			return;
		}
		JsonObject ping = new JsonObject();
		ping.addProperty("t", "ping");
		send(ping);
	}

	private void disconnected() {
		socket = null;
		for (Friend f : friends.values()) f.online = false;
		setState(State.UNREACHABLE);
		changed();
		connectLater(retryDelay);
		retryDelay = Math.min(retryDelay * 2, 120);
	}

	private boolean send(JsonObject o) {
		WebSocket ws = socket;
		if (ws == null || state != State.ONLINE) return false;
		try {
			ws.sendText(GSON.toJson(o), true);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static JsonObject msg(String type) {
		JsonObject o = new JsonObject();
		o.addProperty("t", type);
		return o;
	}

	private final class Listener implements WebSocket.Listener {
		private final StringBuilder buffer = new StringBuilder();

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			buffer.append(data);
			if (last) {
				String text = buffer.toString();
				buffer.setLength(0);
				try {
					handle(GSON.fromJson(text, JsonObject.class));
				} catch (Exception e) {
					LOG.warn("Mensaje del servidor no válido", e);
				}
			}
			ws.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
			if (socket == ws) disconnected();
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			LOG.warn("Conexión social perdida: {}", error.toString());
			if (socket == ws) disconnected();
		}
	}

	// ---------- Mensajes del servidor ----------

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}

	private static UserRef ref(JsonObject o) {
		return new UserRef(str(o, "uuid"), str(o, "name"));
	}

	private Friend updateFriend(JsonObject o) {
		String uuid = str(o, "uuid");
		Friend f = friends.computeIfAbsent(uuid, Friend::new);
		boolean wasOnline = f.online;
		if (o.has("name")) f.name = str(o, "name");
		if (o.has("online")) f.online = o.get("online").getAsBoolean();
		if (o.has("lastSeen")) f.lastSeen = o.get("lastSeen").getAsLong();
		if (o.has("unread")) f.unread = o.get("unread").getAsInt();
		JsonElement st = o.get("status");
		if (st != null && st.isJsonObject()) {
			f.activity = str(st.getAsJsonObject(), "activity");
			f.server = str(st.getAsJsonObject(), "server");
		} else if (o.has("status")) {
			f.activity = null;
			f.server = null;
		}
		if (!wasOnline && f.online && f.name != null && o.get("t") != null && GhostPrefs.notifications) events.friendOnline(f.name);
		return f;
	}

	private Message message(JsonObject o) {
		return new Message(o.get("id").getAsLong(), str(o, "from"), str(o, "to"), str(o, "text"), o.get("ts").getAsLong(), null);
	}

	private void handle(JsonObject m) {
		String t = str(m, "t");
		if (t == null) return;
		switch (t) {
			case "welcome" -> {
				JsonObject meObj = m.getAsJsonObject("me");
				me = new UserRef(str(meObj, "uuid"), str(meObj, "name"));
				Map<String, String> slots = GhostCosmetics.slots(meObj);
				mine.clear();
				mine.putAll(slots);
				GhostCosmetics.get().put(me.uuid(), slots, true);
				if (meObj.has("hours")) hours = meObj.get("hours").getAsInt();
				friends.clear();
				for (JsonElement e : m.getAsJsonArray("friends")) updateFriend(e.getAsJsonObject());
				incoming.clear();
				for (JsonElement e : m.getAsJsonArray("incoming")) incoming.add(ref(e.getAsJsonObject()));
				outgoing.clear();
				for (JsonElement e : m.getAsJsonArray("outgoing")) outgoing.add(ref(e.getAsJsonObject()));
				historyLoaded.clear();
				setState(State.ONLINE);
				String chat = openChat;
				if (chat != null) loadHistory(chat);
			}
			case "presence" -> updateFriend(m);
			case "friend_request" -> {
				UserRef u = ref(m.getAsJsonObject("user"));
				incoming.removeIf(x -> x.uuid().equals(u.uuid()));
				incoming.add(u);
				if (GhostPrefs.notifications) events.friendRequest(u.name());
			}
			case "request_sent" -> {
				UserRef u = ref(m.getAsJsonObject("user"));
				outgoing.removeIf(x -> x.uuid().equals(u.uuid()));
				outgoing.add(u);
				notice("ghost.social.notice.sent", u.name());
			}
			case "request_removed" -> {
				String uuid = str(m, "uuid");
				incoming.removeIf(x -> x.uuid().equals(uuid));
				outgoing.removeIf(x -> x.uuid().equals(uuid));
			}
			case "friend_added" -> {
				JsonObject f = m.getAsJsonObject("friend");
				Friend fr = updateFriend(f);
				incoming.removeIf(x -> x.uuid().equals(fr.uuid));
				outgoing.removeIf(x -> x.uuid().equals(fr.uuid));
				if (GhostPrefs.notifications) events.friendAdded(fr.name);
			}
			case "friend_removed" -> {
				String uuid = str(m, "uuid");
				friends.remove(uuid);
				chats.remove(uuid);
			}
			case "chat" -> {
				Message msg = message(m.getAsJsonObject("msg"));
				String nonce = str(m, "nonce");
				String other = me != null && msg.from().equals(me.uuid()) ? msg.to() : msg.from();
				List<Message> list = chats.computeIfAbsent(other, k -> new CopyOnWriteArrayList<>());
				if (nonce != null) list.removeIf(x -> nonce.equals(x.nonce()));
				if (list.stream().noneMatch(x -> x.id() == msg.id())) list.add(msg);
				if (me != null && !msg.from().equals(me.uuid())) {
					Friend f = friends.get(other);
					if (other.equals(openChat)) {
						markRead(other);
					} else if (f != null) {
						f.unread++;
						if (GhostPrefs.notifications) events.message(f.name, msg.text());
					}
				}
			}
			case "chat_history" -> {
				String other = str(m, "with");
				List<Message> loaded = new ArrayList<>();
				for (JsonElement e : m.getAsJsonArray("messages")) loaded.add(message(e.getAsJsonObject()));
				List<Message> list = chats.computeIfAbsent(other, k -> new CopyOnWriteArrayList<>());
				Set<Long> have = new HashSet<>();
				for (Message x : list) have.add(x.id());
				List<Message> merged = new ArrayList<>();
				for (Message x : loaded) if (!have.contains(x.id())) merged.add(x);
				merged.addAll(list);
				merged.sort(Comparator.comparingLong(x -> x.pending() ? Long.MAX_VALUE : x.id()));
				list.clear();
				list.addAll(merged);
				historyLoaded.add(other);
				historyMore.put(other, m.has("more") && m.get("more").getAsBoolean());
			}
			case "cosmetics" -> {
				JsonObject players = m.getAsJsonObject("players");
				for (Map.Entry<String, JsonElement> e : players.entrySet()) GhostCosmetics.get().putFromServer(e.getKey(), e.getValue());
			}
			case "invite" -> {
				UserRef from = ref(m.getAsJsonObject("from"));
				String server = str(m, "server");
				if (server != null && from.uuid() != null) {
					invite = new Invite(from.uuid(), from.name(), server, System.currentTimeMillis());
					events.invite(from.name(), server);
				}
			}
			case "invite_sent" -> notice("ghost.social.notice.invited", ref(m.getAsJsonObject("to")).name());
			case "hours" -> hours = m.get("hours").getAsInt();
			case "equipped" -> {
				String slot = str(m, "slot") == null ? "cape" : str(m, "slot");
				String id = str(m, "id");
				if (id == null) mine.remove(slot);
				else mine.put(slot, id);
				if (me != null) GhostCosmetics.get().put(me.uuid(), new HashMap<>(mine), true);
			}
			case "error" -> {
				String code = str(m, "code");
				notice("ghost.social.error." + code, str(m, "name"));
				// Mensaje que no se pudo enviar: se quita el pendiente
				String nonce = str(m, "nonce");
				if (nonce != null) for (List<Message> l : chats.values()) l.removeIf(x -> nonce.equals(x.nonce()));
			}
			default -> {
			}
		}
		changed();
	}

	private void notice(String key, String arg) {
		noticeKey = key;
		noticeArg = arg;
		noticeAt = System.currentTimeMillis();
	}

	/** Último aviso (error o confirmación) de los últimos 5 segundos, o null. */
	public String[] notice() {
		if (noticeKey == null || System.currentTimeMillis() - noticeAt > 5000) return null;
		return new String[]{noticeKey, noticeArg == null ? "" : noticeArg};
	}

	// ---------- Lo que pueden pedir las pantallas ----------

	public UserRef me() {
		return me;
	}

	public String myCape() {
		return mine.get("cape");
	}

	/** Lo que llevo puesto en un hueco (cape, pet, hat, back, aura), o null. */
	public String mine(String slot) {
		return mine.get(slot);
	}

	/** Amigos: primero los conectados, luego por nombre. */
	public List<Friend> friends() {
		List<Friend> list = new ArrayList<>(friends.values());
		list.sort(Comparator.comparing((Friend f) -> !f.online).thenComparing(f -> f.name == null ? "" : f.name.toLowerCase(Locale.ROOT)));
		return list;
	}

	public Friend friend(String uuid) {
		return uuid == null ? null : friends.get(uuid);
	}

	public List<UserRef> incoming() {
		return incoming;
	}

	public List<UserRef> outgoing() {
		return outgoing;
	}

	public int onlineCount() {
		int n = 0;
		for (Friend f : friends.values()) if (f.online) n++;
		return n;
	}

	public int unreadTotal() {
		int n = 0;
		for (Friend f : friends.values()) n += f.unread;
		return n + incoming.size();
	}

	public List<Message> chat(String uuid) {
		return chats.getOrDefault(uuid, List.of());
	}

	public boolean historyLoaded(String uuid) {
		return historyLoaded.contains(uuid);
	}

	public boolean hasMoreHistory(String uuid) {
		return historyMore.getOrDefault(uuid, false);
	}

	public void addFriend(String name) {
		JsonObject o = msg("friend_add");
		o.addProperty("name", name.trim());
		if (!send(o)) notice("ghost.social.error.offline", null);
	}

	public void accept(String uuid) {
		JsonObject o = msg("friend_accept");
		o.addProperty("uuid", uuid);
		send(o);
	}

	public void decline(String uuid) {
		JsonObject o = msg("friend_decline");
		o.addProperty("uuid", uuid);
		send(o);
	}

	public void cancel(String uuid) {
		JsonObject o = msg("friend_cancel");
		o.addProperty("uuid", uuid);
		send(o);
	}

	public void remove(String uuid) {
		JsonObject o = msg("friend_remove");
		o.addProperty("uuid", uuid);
		send(o);
	}

	public void sendChat(String uuid, String text) {
		String clean = text.strip();
		if (clean.isEmpty() || me == null) return;
		if (clean.length() > 256) clean = clean.substring(0, 256);
		String nonce = UUID.randomUUID().toString().substring(0, 12);
		JsonObject o = msg("chat_send");
		o.addProperty("to", uuid);
		o.addProperty("text", clean);
		o.addProperty("nonce", nonce);
		if (send(o)) {
			chats.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
				.add(new Message(-System.nanoTime(), me.uuid(), uuid, clean, System.currentTimeMillis(), nonce));
			changed();
		} else {
			notice("ghost.social.error.offline", null);
		}
	}

	public void loadHistory(String uuid) {
		JsonObject o = msg("chat_history");
		o.addProperty("with", uuid);
		send(o);
	}

	/** Mensajes anteriores al primero que tenemos. */
	public void loadOlder(String uuid) {
		List<Message> list = chat(uuid);
		long first = list.stream().filter(m -> !m.pending()).mapToLong(Message::id).min().orElse(Long.MAX_VALUE);
		JsonObject o = msg("chat_history");
		o.addProperty("with", uuid);
		o.addProperty("before", first);
		historyMore.put(uuid, false);
		send(o);
	}

	public void markRead(String uuid) {
		Friend f = friends.get(uuid);
		if (f != null && f.unread > 0) {
			f.unread = 0;
			changed();
		}
		JsonObject o = msg("chat_read");
		o.addProperty("with", uuid);
		send(o);
	}

	/** Conversación que se está viendo (null al cerrar). */
	public void setOpenChat(String uuid) {
		openChat = uuid;
		if (uuid != null) {
			markRead(uuid);
			if (!historyLoaded.contains(uuid)) loadHistory(uuid);
		}
	}

	/** Qué estás haciendo: menu, singleplayer o server (con la IP si dejas que se vea). */
	public void setStatus(String activity, String server) {
		String shown = "server".equals(activity) && GhostPrefs.showServer ? server : null;
		String key = activity + "|" + shown;
		if (key.equals(lastStatus) || state != State.ONLINE) return;
		JsonObject o = msg("status");
		o.addProperty("activity", activity);
		if (shown != null) o.addProperty("server", shown);
		if (send(o)) lastStatus = key;
	}

	/** Invita a un amigo al servidor en el que estás. */
	public void invite(String uuid, String server) {
		if (server == null || server.isBlank()) return;
		JsonObject o = msg("invite");
		o.addProperty("to", uuid);
		o.addProperty("server", server.trim());
		if (!send(o)) notice("ghost.social.error.offline", null);
	}

	/** La última invitación, si aún vale (2 minutos), o null. */
	public Invite invite() {
		Invite i = invite;
		return i != null && i.fresh() ? i : null;
	}

	public void clearInvite() {
		invite = null;
		changed();
	}

	/** Horas jugadas que conoce el servidor (para los cosméticos que se desbloquean). */
	public int hours() {
		return hours;
	}

	/** Ponerse o quitarse la capa (null = sin capa). */
	public boolean equipCape(String id) {
		return equip("cape", id);
	}

	/** Ponerse o quitarse un cosmético de un hueco (null = nada). */
	public boolean equip(String slot, String id) {
		JsonObject o = msg("equip");
		o.addProperty("slot", slot);
		if (id == null) o.add("id", null);
		else o.addProperty("id", id);
		if (!send(o)) {
			notice("ghost.social.error.offline", null);
			return false;
		}
		return true;
	}

	/** Pide los cosméticos de varios jugadores (por WebSocket si hay conexión; si no, por HTTP). */
	void requestCosmetics(Collection<String> uuids) {
		if (uuids.isEmpty()) return;
		if (state == State.ONLINE) {
			JsonObject o = msg("cosmetics_get");
			JsonArray arr = new JsonArray();
			for (String u : uuids) arr.add(u);
			o.add("uuids", arr);
			if (send(o)) return;
		}
		String url = GhostClient.server() + "/v1/cosmetics?uuids=" + String.join(",", uuids);
		http.sendAsync(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString())
			.thenAccept(res -> {
				if (res.statusCode() != 200) return;
				JsonObject players = GSON.fromJson(res.body(), JsonObject.class).getAsJsonObject("players");
				for (Map.Entry<String, JsonElement> e : players.entrySet()) GhostCosmetics.get().putFromServer(e.getKey(), e.getValue());
			}).exceptionally(e -> null);
	}

	ScheduledExecutorService scheduler() {
		return scheduler;
	}
}
