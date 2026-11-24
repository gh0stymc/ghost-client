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

package io.github.axolotlclient.modules.sounds;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringArrayOption;
import io.github.axolotlclient.bridge.entity.AxoEntity;
import io.github.axolotlclient.bridge.entity.AxoPlayer;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.events.types.ReceiveChatMessageEvent;
import io.github.axolotlclient.modules.AbstractCommonModule;
import io.github.axolotlclient.util.dialogs.DialogProvider;
import io.github.axolotlclient.util.options.ForceableBooleanOption;
import io.github.axolotlclient.util.options.GenericOption;
import lombok.Getter;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * ghost client: "sound effects". Suena un sonido cuando matas a alguien, cuando mueres y cuando llevas
 * una racha de muertes. Hay sonidos predeterminados y se puede subir uno propio (WAV u OGG) para cada cosa.
 */
public class SoundEffects extends AbstractCommonModule {
	@Getter
	private static final SoundEffects instance = new SoundEffects();

	/** Lo que cada versión de Minecraft sabe responder (vida de los jugadores). */
	public interface Tracker {
		boolean isDead(AxoEntity entity);

		boolean isLocalPlayerDead();
	}

	private static final String[] DEFAULTS = {"ding", "coins", "pop", "laser", "fanfare", "bass", "sad", "ghost"};
	private static final String CUSTOM = "custom", OFF = "off";
	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern DEATH_WORDS = Pattern.compile(
		"^(?:you died|¡?has muerto|you were killed|fuiste asesinado|has sido asesinado|te han matado)|^%s (?:was |died|fell|drowned|burned|blew up|hit the ground|walked into|tried to|went up|froze|starved|suffocated|withered|experienced|discovered|didn't want|ha muerto|muri|fue |se cay|se ahog|se quem)",
		Pattern.CASE_INSENSITIVE);
	/** "Rival killed Tú", "Rival mató a Tú"...: alguien te ha matado (el nombre va después). */
	private static final Pattern KILLED_ME = Pattern.compile(
		"(?:killed|slain|slew|eliminated|mató a|asesinó a|eliminó a|ha matado a|ha eliminado a)\\s+%s(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
	/** Palabras de los mensajes de muerte (inglés y español). */
	private static final Pattern DEATH_HINT = Pattern.compile(
		"killed|slain|died|eliminated|fell|void|murder|muri|muerto|asesinad|eliminad|matad|vac[ií]o|ca[ií]do");

	public enum Event {
		KILL("kill", "ding"), DEATH("death", "sad"), STREAK("streak", "fanfare");

		final String id, defaultSound;

		Event(String id, String defaultSound) {
			this.id = id;
			this.defaultSound = defaultSound;
		}
	}

	private final OptionCategory category = OptionCategory.create("soundEffects");
	public final ForceableBooleanOption enabled = new ForceableBooleanOption("enabled", false);
	private final IntegerOption volume = new IntegerOption("ghost.sounds.volume", 80, 0, 100);
	private final IntegerOption streakEvery = new IntegerOption("ghost.sounds.streak_every", 3, 2, 10);
	private final StringArrayOption[] sounds = new StringArrayOption[Event.values().length];

	private final ExecutorService player = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ghost-sound-effects");
		t.setDaemon(true);
		return t;
	});

	private Tracker tracker;
	private AxoEntity lastTarget;
	private String lastTargetName;
	private long lastTargetTime, lastKill, lastDeath;
	private boolean wasDead;
	private int streak;

	public void setTracker(Tracker tracker) {
		this.tracker = tracker;
	}

	private static String[] choices() {
		String[] all = new String[DEFAULTS.length + 2];
		for (int i = 0; i < DEFAULTS.length; i++) all[i] = "ghost.sounds.sound." + DEFAULTS[i];
		all[DEFAULTS.length] = "ghost.sounds.sound." + CUSTOM;
		all[DEFAULTS.length + 1] = "ghost.sounds.sound." + OFF;
		return all;
	}

	@Override
	public void init() {
		category.add(enabled, volume);
		for (Event e : Event.values()) {
			StringArrayOption choice = new StringArrayOption("ghost.sounds." + e.id, choices(), "ghost.sounds.sound." + e.defaultSound);
			sounds[e.ordinal()] = choice;
			category.add(choice);
			category.add(new GenericOption("ghost.sounds." + e.id + ".upload", "ghost.sounds.upload", () -> upload(e)));
			category.add(new GenericOption("ghost.sounds." + e.id + ".test", "ghost.sounds.test", () -> play(e)));
		}
		category.add(streakEvery);
		AxolotlClientCommon.getInstance().getConfig().general.add(category);

		Events.PLAYER_ATTACK.register((attacker, target) -> {
			AxoPlayer me = client.br$getPlayer();
			if (me == null || attacker != me || !(target instanceof AxoPlayer p)) return;
			lastTarget = target;
			lastTargetName = p.br$getName();
			lastTargetTime = System.currentTimeMillis();
		});
		Events.RECEIVE_CHAT_MESSAGE.register(this::onChat);
		Events.DISCONNECT.register(() -> {
			streak = 0;
			lastTarget = null;
			wasDead = false;
		});
	}

	@Override
	public void tick() {
		// Se detecta siempre (para las estadísticas de ghost client); el sonido solo suena si está activado
		if (tracker == null || client.br$getPlayer() == null) return;
		long now = System.currentTimeMillis();
		if (lastTarget != null) {
			if (now - lastTargetTime > 6000) {
				lastTarget = null;
			} else if (tracker.isDead(lastTarget)) {
				lastTarget = null;
				kill();
			}
		}
		boolean dead = tracker.isLocalPlayerDead();
		if (dead && !wasDead) death();
		wasDead = dead;
	}

	// En servidores como Hypixel no se ve morir al jugador: se usa el mensaje del chat
	private void onChat(ReceiveChatMessageEvent event) {
		if (event.isActionBar() || client.br$getPlayer() == null) return;
		String msg = FORMATTING.matcher(event.getOriginalMessage()).replaceAll("").trim();
		if (msg.startsWith("<")) return; // mensaje de un jugador
		String me = client.br$getPlayer().br$getName();
		long now = System.currentTimeMillis();
		String lower = msg.toLowerCase(Locale.ROOT);
		boolean target = lastTargetName != null && now - lastTargetTime < 8000 && containsWord(msg, lastTargetName);
		int meAt = wordIndex(msg, me);
		// Antes que nada, ¿has muerto tú? En "Tú was slain by Rival" salen los dos nombres: si se mirase primero si es un
		// asesinato, contaría como que lo has matado tú (por eso no se contaban las muertes).
		boolean died = Pattern.compile(DEATH_WORDS.pattern().replace("%s", Pattern.quote(me)), Pattern.CASE_INSENSITIVE).matcher(msg).find()
			|| Pattern.compile(KILLED_ME.pattern().replace("%s", Pattern.quote(me)), Pattern.CASE_INSENSITIVE).matcher(msg).find();
		boolean byMe = meAt >= 0 && Pattern.compile("(?:by|por)\\s+" + Pattern.quote(me) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE).matcher(msg).find();
		if (!died && !byMe && meAt >= 0 && DEATH_HINT.matcher(lower).find()) {
			// mensaje de muerte con tu nombre: si va antes que el del rival, eres tú quien muere ("Tú fue asesinado por Rival")
			int targetAt = target ? wordIndex(msg, lastTargetName) : -1;
			died = targetAt < 0 || meAt < targetAt;
		}
		if (!died && byMe && DEATH_HINT.matcher(lower).find()) {
			// "Rival was slain by Tú": lo has matado tú (aunque fuera con arco y no le hubieras pegado)
			lastTarget = null;
			lastTargetName = null;
			kill();
			return;
		}
		if (died) {
			death();
			return;
		}
		if (target && (meAt >= 0 || lower.contains(" you"))) {
			lastTarget = null;
			lastTargetName = null;
			kill();
		}
	}

	private static int wordIndex(String text, String word) {
		var m = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(word) + "(?![A-Za-z0-9_])").matcher(text);
		return m.find() ? m.start() : -1;
	}

	private static boolean containsWord(String text, String word) {
		return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(word) + "(?![A-Za-z0-9_])").matcher(text).find();
	}

	private void kill() {
		long now = System.currentTimeMillis();
		if (now - lastKill < 1200) return;
		lastKill = now;
		streak++;
		io.github.axolotlclient.ghost.GhostStats.kill();
		if (enabled.get()) play(streak % streakEvery.get() == 0 ? Event.STREAK : Event.KILL);
	}

	private void death() {
		long now = System.currentTimeMillis();
		if (now - lastDeath < 3000) return;
		lastDeath = now;
		streak = 0;
		io.github.axolotlclient.ghost.GhostStats.death();
		if (enabled.get()) play(Event.DEATH);
	}

	// ---------- Sonidos ----------

	private static Path soundsDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("ghostclient").resolve("sounds");
	}

	private static Path customFile(Event e) {
		try (Stream<Path> s = Files.exists(soundsDir()) ? Files.list(soundsDir()) : Stream.empty()) {
			return s.filter(p -> p.getFileName().toString().startsWith(e.id + ".")).findFirst().orElse(null);
		} catch (IOException ex) {
			return null;
		}
	}

	private void upload(Event e) {
		DialogProvider.IMPL.open("ghost client", Path.of(System.getProperty("user.home")), "").thenAccept(file -> file.ifPresent(p -> {
			String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
			String ext = name.endsWith(".ogg") ? "ogg" : name.endsWith(".wav") ? "wav" : null;
			if (ext == null) {
				AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("soundEffects", "ghost.sounds.bad_format");
				return;
			}
			try {
				Files.createDirectories(soundsDir());
				Path old = customFile(e);
				if (old != null) Files.deleteIfExists(old);
				Files.copy(p, soundsDir().resolve(e.id + "." + ext), StandardCopyOption.REPLACE_EXISTING);
				sounds[e.ordinal()].set("ghost.sounds.sound." + CUSTOM);
				AxolotlClientCommon.getInstance().saveConfig();
				play(e);
				AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("soundEffects", "ghost.sounds.uploaded");
			} catch (IOException ex) {
				AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("soundEffects", "ghost.sounds.error", String.valueOf(ex.getMessage()));
			}
		}));
	}

	public void play(Event e) {
		String choice = sounds[e.ordinal()] == null ? e.defaultSound : sounds[e.ordinal()].get();
		String id = choice.substring(choice.lastIndexOf('.') + 1);
		if (OFF.equals(id)) return;
		float vol = volume.get() / 100f;
		if (vol <= 0) return;
		player.execute(() -> {
			try {
				byte[] data;
				String kind;
				Path custom = CUSTOM.equals(id) ? customFile(e) : null;
				if (custom != null) {
					data = Files.readAllBytes(custom);
					kind = custom.getFileName().toString().endsWith(".ogg") ? "ogg" : "wav";
				} else {
					String res = "/assets/axolotlclient/ghostsounds/" + (CUSTOM.equals(id) ? e.defaultSound : id) + ".wav";
					try (InputStream in = SoundEffects.class.getResourceAsStream(res)) {
						if (in == null) return;
						data = in.readAllBytes();
					}
					kind = "wav";
				}
				playBytes(data, kind, vol);
			} catch (Throwable ex) {
				AxolotlClientCommon.getInstance().getLogger().warn("No se pudo reproducir el sonido", ex);
			}
		});
	}

	/**
	 * Reproduce el sonido con OpenAL, el mismo sistema de audio que usa Minecraft (el Java que trae el juego
	 * no siempre incluye el audio de Java). Se ejecuta en el hilo de "sound effects".
	 */
	private static void playBytes(byte[] data, String kind, float vol) throws Exception {
		AudioInputStream stream = "ogg".equals(kind) ? decodeOgg(data)
			: AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(data)));
		AudioFormat fmt = stream.getFormat();
		int channels = Math.min(2, fmt.getChannels());
		AudioFormat pcm16 = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, fmt.getSampleRate(), 16, channels, channels * 2, fmt.getSampleRate(), false);
		if (!fmt.matches(pcm16)) stream = AudioSystem.getAudioInputStream(pcm16, stream);
		byte[] pcm = stream.readAllBytes();
		if (pcm.length == 0 || ALC10.alcGetCurrentContext() == 0) return;

		AL10.alGetError();
		int buffer = AL10.alGenBuffers();
		ByteBuffer direct = MemoryUtil.memAlloc(pcm.length);
		try {
			direct.put(pcm).flip();
			AL10.alBufferData(buffer, channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, direct, (int) fmt.getSampleRate());
		} finally {
			MemoryUtil.nmemFree(MemoryUtil.memAddress(direct));
		}
		int source = AL10.alGenSources();
		if (AL10.alGetError() != AL10.AL_NO_ERROR) {
			AL10.alDeleteBuffers(buffer);
			return;
		}
		AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
		AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
		AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
		AL10.alSourcef(source, AL10.AL_GAIN, vol);
		AL10.alSourcePlay(source);
		long millis = (long) (pcm.length / (channels * 2.0) / fmt.getSampleRate() * 1000) + 150;
		Thread.sleep(Math.min(millis, 30_000));
		AL10.alSourceStop(source);
		AL10.alDeleteSources(source);
		AL10.alDeleteBuffers(buffer);
	}

	/** Decodifica un OGG con stb_vorbis (viene con Minecraft) a PCM de 16 bits. */
	private static AudioInputStream decodeOgg(byte[] data) throws IOException {
		ByteBuffer buf = MemoryUtil.memAlloc(data.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			buf.put(data).flip();
			IntBuffer channels = stack.mallocInt(1), rate = stack.mallocInt(1);
			ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(buf, channels, rate);
			if (pcm == null) throw new IOException("OGG no válido");
			try {
				ByteBuffer bytes = ByteBuffer.allocate(pcm.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN);
				while (pcm.hasRemaining()) bytes.putShort(pcm.get());
				AudioFormat fmt = new AudioFormat(rate.get(0), 16, channels.get(0), true, false);
				byte[] arr = bytes.array();
				return new AudioInputStream(new ByteArrayInputStream(arr), fmt, arr.length / fmt.getFrameSize());
			} finally {
				MemoryUtil.nmemFree(MemoryUtil.memAddress(pcm));
			}
		} finally {
			MemoryUtil.nmemFree(MemoryUtil.memAddress(buf));
		}
	}
}
