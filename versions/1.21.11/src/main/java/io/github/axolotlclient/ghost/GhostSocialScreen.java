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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import io.github.axolotlclient.modules.hud.GhostUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * ghost client: menú social. A la izquierda tus amigos (y las solicitudes); a la derecha el amigo elegido,
 * con su skin, lo que está haciendo y el chat.
 */
public class GhostSocialScreen extends Screen {
	private static final int LIST_W = 152, ROW_H = 24, HEAD_H = 14;
	private static final int ONLINE = 0xFF22C55E, DANGER = 0xFFF43F5E, BUBBLE_THEM = 0xFF262236;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM");

	/** Una fila de la lista: amigo, solicitud recibida, solicitud enviada o título de sección. */
	private record Row(String kind, String uuid, String name, int y, int h) {
	}

	private record Line(FormattedCharSequence text, boolean mine, boolean pending, boolean first, boolean last, int width, String separator) {
	}

	private final Screen parent;
	private int px, py, pw, ph, listX, listY, listH, rx, ry, rw, rh, chatX, chatY, chatW, chatH;
	private EditBox addBox, chatBox;
	private GhostUi.GhostButton retry;
	private PlayerSkinWidget skinWidget;
	private String selected;
	private double listScroll, chatScroll;
	private int listMax, chatMax;
	private final List<Row> rows = new ArrayList<>();
	private long removeArmed;
	private boolean olderRequested;
	// Botones de la barra del amigo (Unirse / Invitar) y del aviso de invitación: {x, y, w, h} o null
	private int[] joinRect, inviteRect, acceptRect, dismissRect;

	// Mensajes ya partidos en líneas (se rehacen solo si algo cambia)
	private List<Line> lines = List.of();
	private int linesRevision = -1, linesWidth = -1;
	private String linesFor;

	public GhostSocialScreen(Screen parent) {
		super(Component.translatable("ghost.hub.social"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		pw = Math.min(width - 24, 480);
		ph = Math.min(height - 20, 300);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		listX = px + 10;
		listY = py + 64;
		listH = py + ph - 34 - listY;
		rx = px + LIST_W + 18;
		ry = py + 40;
		rw = px + pw - 10 - rx;
		rh = py + ph - 10 - ry;
		chatX = rx + 88;
		chatW = rw - 96;
		chatY = ry + 30;
		chatH = rh - 62;

		String addValue = addBox == null ? "" : addBox.getValue();
		addBox = new EditBox(font, listX + 7, py + 46, LIST_W - 32, 10, Component.translatable("ghost.social.add"));
		addBox.setBordered(false);
		addBox.setMaxLength(16);
		addBox.setValue(addValue);
		addRenderableWidget(addBox);

		String chatValue = chatBox == null ? "" : chatBox.getValue();
		chatBox = new EditBox(font, chatX + 7, ry + rh - 21, chatW - 34, 10, Component.translatable("ghost.social.write"));
		chatBox.setBordered(false);
		chatBox.setMaxLength(256);
		chatBox.setValue(chatValue);
		addRenderableWidget(chatBox);

		retry = addRenderableWidget(new GhostUi.GhostButton(rx + rw / 2 - 50, ry + rh / 2 + 18, 100, 20,
			Component.translatable("ghost.social.retry"), btn -> GhostSocial.get().reconnect(), true));
		addRenderableWidget(new GhostUi.GhostButton(px + pw - 70, py + 10, 60, 18, Component.translatable("gui.back"), btn -> onClose(), false));

		if (skinWidget != null) removeWidget(skinWidget);
		skinWidget = null;
		select(selected);
	}

	private void select(String uuid) {
		if (skinWidget != null) {
			removeWidget(skinWidget);
			skinWidget = null;
		}
		selected = uuid;
		chatScroll = 0;
		olderRequested = false;
		removeArmed = 0;
		linesFor = null;
		GhostSocial.get().setOpenChat(uuid);
		GhostSocial.Friend f = GhostSocial.get().friend(uuid);
		if (f != null) {
			skinWidget = new PlayerSkinWidget(72, 104, minecraft.getEntityModels(), GhostHooks.skin(GhostHooks.uuid(f.uuid), f.name));
			skinWidget.setPosition(rx + 8, ry + 22);
			addRenderableWidget(skinWidget);
		}
	}

	@Override
	public void onClose() {
		GhostSocial.get().setOpenChat(null);
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------- Dibujo ----------

	@Override
	public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) super.renderBackground(g, mouseX, mouseY, delta);
		else g.fill(0, 0, width, height, 0x50000000);
		GhostUi.roundBox(g, px, py, pw, ph, 0xE010101A, GhostUi.BORDER);
		g.fill(px, py + 12, px + 2, py + 32, GhostUi.ACCENT);
		// Caja para añadir amigos y panel derecho
		GhostUi.roundBox(g, listX, py + 40, LIST_W, 20, 0xC80C0C14, addBox != null && addBox.isFocused() ? GhostUi.ACCENT : GhostUi.BORDER);
		GhostUi.roundBox(g, rx, ry, rw, rh, 0x900C0C14, GhostUi.BORDER);
		boolean chatting = selected != null && GhostSocial.get().state() == GhostSocial.State.ONLINE;
		if (chatting) {
			GhostUi.roundBox(g, chatX, ry + rh - 27, chatW, 21, 0xC8161622, chatBox.isFocused() ? GhostUi.ACCENT : GhostUi.BORDER);
		}
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
		GhostSocial social = GhostSocial.get();
		boolean online = social.state() == GhostSocial.State.ONLINE;
		if (selected != null && social.friend(selected) == null) select(null);
		debugSelect(social);
		boolean chatting = selected != null && online;
		chatBox.visible = chatting;
		retry.visible = !online && social.state() != GhostSocial.State.CONNECTING;
		if (skinWidget != null) skinWidget.visible = online;

		super.render(g, mouseX, mouseY, delta);

		int friendsCount = social.friends().size();
		GhostUi.header(g, px + 12, py + 9, online
			? Component.translatable("ghost.social.subtitle", social.onlineCount(), friendsCount)
			: Component.translatable("ghost.social.subtitle.offline"));

		// Añadir amigo
		if (addBox.getValue().isEmpty() && !addBox.isFocused()) g.drawString(font, Component.translatable("ghost.social.add"), addBox.getX(), addBox.getY(), GhostUi.MUTED, false);
		boolean addHover = in(mouseX, mouseY, listX + LIST_W - 20, py + 42, 16, 16);
		GhostUi.roundRect(g, listX + LIST_W - 20, py + 42, 16, 16, addHover ? GhostUi.ACCENT_HOVER : GhostUi.ACCENT);
		g.blit(RenderPipelines.GUI_TEXTURED, GhostHooks.icon("add"), listX + LIST_W - 17, py + 45, 0, 0, 10, 10, 10, 10);

		drawList(g, mouseX, mouseY, online);
		drawPrivacy(g, mouseX, mouseY);

		if (!online) drawOffline(g);
		else if (selected == null) drawEmpty(g, friendsCount);
		else drawFriend(g, mouseX, mouseY);

		drawInvite(g, mouseX, mouseY);
		drawNotice(g);
	}

	private boolean debugDone;

	private void debugSelect(GhostSocial social) {
		String name = System.getProperty("ghostclient.debug.select");
		if (debugDone || name == null || social.state() != GhostSocial.State.ONLINE) return;
		for (GhostSocial.Friend f : social.friends()) {
			if (name.equalsIgnoreCase(f.name)) {
				debugDone = true;
				select(f.uuid);
				String say = System.getProperty("ghostclient.debug.say");
				if (say != null) social.sendChat(f.uuid, say);
				return;
			}
		}
	}

	/** Recorta un texto que no cabe y le pone "…". */
	private String fit(String text, int width) {
		if (text == null) return "";
		if (font.width(text) <= width) return text;
		return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
	}

	private static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void buildRows() {
		rows.clear();
		GhostSocial social = GhostSocial.get();
		int y = 0;
		if (!social.incoming().isEmpty()) {
			rows.add(new Row("title", null, Component.translatable("ghost.social.section.requests", social.incoming().size()).getString(), y, HEAD_H));
			y += HEAD_H;
			for (GhostSocial.UserRef u : social.incoming()) {
				rows.add(new Row("incoming", u.uuid(), u.name(), y, ROW_H));
				y += ROW_H;
			}
		}
		List<GhostSocial.Friend> friends = social.friends();
		List<GhostSocial.Friend> on = friends.stream().filter(f -> f.online).toList();
		List<GhostSocial.Friend> off = friends.stream().filter(f -> !f.online).toList();
		if (!on.isEmpty()) {
			rows.add(new Row("title", null, Component.translatable("ghost.social.section.online", on.size()).getString(), y, HEAD_H));
			y += HEAD_H;
			for (GhostSocial.Friend f : on) {
				rows.add(new Row("friend", f.uuid, f.name, y, ROW_H));
				y += ROW_H;
			}
		}
		if (!off.isEmpty()) {
			rows.add(new Row("title", null, Component.translatable("ghost.social.section.offline", off.size()).getString(), y, HEAD_H));
			y += HEAD_H;
			for (GhostSocial.Friend f : off) {
				rows.add(new Row("friend", f.uuid, f.name, y, ROW_H));
				y += ROW_H;
			}
		}
		if (!social.outgoing().isEmpty()) {
			rows.add(new Row("title", null, Component.translatable("ghost.social.section.sent", social.outgoing().size()).getString(), y, HEAD_H));
			y += HEAD_H;
			for (GhostSocial.UserRef u : social.outgoing()) {
				rows.add(new Row("outgoing", u.uuid(), u.name(), y, ROW_H));
				y += ROW_H;
			}
		}
		listMax = Math.max(0, y - listH);
		listScroll = Math.max(0, Math.min(listScroll, listMax));
	}

	private void drawList(GuiGraphics g, int mouseX, int mouseY, boolean online) {
		buildRows();
		if (!online) return;
		if (rows.isEmpty()) {
			int cy = listY + 20;
			g.drawCenteredString(font, Component.translatable("ghost.social.empty.list"), listX + LIST_W / 2, cy, GhostUi.MUTED);
			int y = cy + 14;
			for (FormattedCharSequence l : font.split(Component.translatable("ghost.social.empty.list.hint"), LIST_W - 16)) {
				g.drawCenteredString(font, l, listX + LIST_W / 2, y, 0xFF5E5E72);
				y += 10;
			}
			return;
		}
		g.enableScissor(listX, listY, listX + LIST_W, listY + listH);
		int base = listY - (int) listScroll;
		for (Row r : rows) {
			int y = base + r.y();
			if (y + r.h() < listY || y > listY + listH) continue;
			if ("title".equals(r.kind())) {
				g.drawString(font, r.name(), listX + 4, y + 4, 0xFF6E6E86, false);
				continue;
			}
			boolean hover = in(mouseX, mouseY, listX, y, LIST_W, r.h()) && mouseY >= listY && mouseY < listY + listH;
			boolean sel = r.uuid().equals(selected);
			if (sel) {
				GhostUi.roundRect(g, listX, y + 1, LIST_W, r.h() - 2, 0xFF2A2440);
				g.fill(listX, y + 6, listX + 2, y + r.h() - 6, GhostUi.ACCENT);
			} else if (hover) {
				GhostUi.roundRect(g, listX, y + 1, LIST_W, r.h() - 2, 0x80262238);
			}
			GhostSocial.Friend f = GhostSocial.get().friend(r.uuid());
			boolean isOnline = f != null && f.online;
			int faceX = listX + 5, faceY = y + 5;
			PlayerFaceRenderer.draw(g, GhostHooks.skin(GhostHooks.uuid(r.uuid()), r.name()).get(), faceX, faceY, 14);
			if (f != null) {
				g.fill(faceX + 10, faceY + 10, faceX + 15, faceY + 15, 0xFF10101A);
				g.fill(faceX + 11, faceY + 11, faceX + 14, faceY + 14, isOnline ? ONLINE : 0xFF6B6B80);
			}
			int textW = LIST_W - 26 - ("friend".equals(r.kind()) ? 18 : 30);
			String name = r.name() == null ? "?" : r.name();
			g.drawString(font, fit(name, textW), listX + 24, y + 3, f == null || isOnline ? GhostUi.TEXT : 0xFFB0B0C0, false);
			Component sub = switch (r.kind()) {
				case "incoming" -> Component.translatable("ghost.social.request.incoming");
				case "outgoing" -> Component.translatable("ghost.social.request.outgoing");
				default -> statusOf(f);
			};
			String subText = sub.getString();
			g.drawString(font, fit(subText, textW), listX + 24, y + 13, isOnline ? 0xFF8FD9A8 : GhostUi.MUTED, false);

			if ("incoming".equals(r.kind())) {
				iconButton(g, "check", listX + LIST_W - 34, y + 5, mouseX, mouseY, 0xFF22A35A);
				iconButton(g, "close", listX + LIST_W - 18, y + 5, mouseX, mouseY, 0xFF4A2A38);
			} else if ("outgoing".equals(r.kind())) {
				iconButton(g, "close", listX + LIST_W - 18, y + 5, mouseX, mouseY, 0xFF4A2A38);
			} else if (f != null && f.unread > 0) {
				String n = f.unread > 9 ? "9+" : String.valueOf(f.unread);
				GhostUi.roundRect(g, listX + LIST_W - 18, y + 7, 13, 11, GhostUi.ACCENT);
				g.drawCenteredString(font, n, listX + LIST_W - 11, y + 9, 0xFFFFFFFF);
			}
		}
		g.disableScissor();
		if (listMax > 0) {
			int barH = Math.max(14, listH * listH / (listH + listMax));
			int barY = listY + (int) ((listH - barH) * (listScroll / listMax));
			g.fill(listX + LIST_W + 2, barY, listX + LIST_W + 4, barY + barH, 0x807C5CFF);
		}
	}

	private void iconButton(GuiGraphics g, String icon, int x, int y, int mouseX, int mouseY, int color) {
		boolean hover = in(mouseX, mouseY, x, y, 14, 14);
		GhostUi.roundRect(g, x, y, 14, 14, hover ? (color | 0xFF000000) : (color & 0x00FFFFFF | 0xB0000000));
		g.blit(RenderPipelines.GUI_TEXTURED, GhostHooks.icon(icon), x + 3, y + 3, 0, 0, 8, 8, 8, 8);
	}

	private Component statusOf(GhostSocial.Friend f) {
		if (f == null) return Component.empty();
		if (f.online) {
			if ("server".equals(f.activity)) {
				return f.server != null ? Component.translatable("ghost.social.status.server", f.server) : Component.translatable("ghost.social.status.multiplayer");
			}
			if ("singleplayer".equals(f.activity)) return Component.translatable("ghost.social.status.singleplayer");
			if ("menu".equals(f.activity)) return Component.translatable("ghost.social.status.menu");
			return Component.translatable("ghost.social.status.online");
		}
		return ago(f.lastSeen);
	}

	private static Component ago(long ts) {
		if (ts <= 0) return Component.translatable("ghost.social.status.offline");
		long min = Math.max(1, (System.currentTimeMillis() - ts) / 60000);
		if (min < 60) return Component.translatable("ghost.social.ago.minutes", min);
		long h = min / 60;
		if (h < 24) return Component.translatable("ghost.social.ago.hours", h);
		return Component.translatable("ghost.social.ago.days", h / 24);
	}

	private void drawPrivacy(GuiGraphics g, int mouseX, int mouseY) {
		int y = py + ph - 26;
		boolean hover = in(mouseX, mouseY, listX, y, LIST_W, 16);
		boolean on = GhostPrefs.showServer;
		GhostUi.roundBox(g, listX + 2, y + 3, 18, 10, on ? GhostUi.ACCENT : 0xFF2A2A3A, on ? GhostUi.ACCENT : GhostUi.BORDER);
		g.fill(on ? listX + 13 : listX + 5, y + 5, on ? listX + 18 : listX + 10, y + 11, 0xFFFFFFFF);
		g.drawString(font, fit(Component.translatable("ghost.social.privacy.server").getString(), LIST_W - 26), listX + 24, y + 4, hover ? GhostUi.TEXT : GhostUi.MUTED, false);
	}

	private void drawOffline(GuiGraphics g) {
		GhostSocial.State s = GhostSocial.get().state();
		int cx = rx + rw / 2, cy = ry + rh / 2 - 30;
		g.blit(RenderPipelines.GUI_TEXTURED, GhostHooks.icon("social"), cx - 12, cy - 8, 0, 0, 24, 24, 24, 24);
		String key = switch (s) {
			case CONNECTING -> "ghost.social.offline.connecting";
			case NO_ACCOUNT -> "ghost.social.offline.account";
			default -> "ghost.social.offline.server";
		};
		g.drawCenteredString(font, Component.translatable(key + ".title"), cx, cy + 24, GhostUi.TEXT);
		int y = cy + 36;
		for (FormattedCharSequence l : font.split(Component.translatable(key + ".text"), rw - 40)) {
			g.drawCenteredString(font, l, cx, y, GhostUi.MUTED);
			y += 10;
		}
	}

	private void drawEmpty(GuiGraphics g, int friends) {
		int cx = rx + rw / 2, cy = ry + rh / 2 - 34;
		GhostUi.roundRect(g, cx - 20, cy - 12, 40, 40, 0x337C5CFF);
		g.blit(RenderPipelines.GUI_TEXTURED, GhostHooks.icon(friends == 0 ? "add" : "chat"), cx - 12, cy - 4, 0, 0, 24, 24, 24, 24);
		g.drawCenteredString(font, Component.translatable(friends == 0 ? "ghost.social.empty.title" : "ghost.social.pick.title"), cx, cy + 38, GhostUi.TEXT);
		int y = cy + 52;
		for (FormattedCharSequence l : font.split(Component.translatable(friends == 0 ? "ghost.social.empty.text" : "ghost.social.pick.text"), rw - 50)) {
			g.drawCenteredString(font, l, cx, y, GhostUi.MUTED);
			y += 10;
		}
	}

	private void drawFriend(GuiGraphics g, int mouseX, int mouseY) {
		GhostSocial.Friend f = GhostSocial.get().friend(selected);
		if (f == null) return;
		// Perfil: nombre, skin y estado
		int colX = rx + 8, colW = 72;
		g.drawCenteredString(font, fit(f.name, colW + 6), colX + colW / 2, ry + 9, GhostUi.TEXT);
		int sy = ry + 132;
		GhostUi.roundRect(g, colX, sy, colW, 14, f.online ? 0x3322C55E : 0x332A2A3A);
		g.drawCenteredString(font, fit(statusOf(f).getString(), colW - 6), colX + colW / 2, sy + 3, f.online ? 0xFF8FD9A8 : GhostUi.MUTED);
		// Quitar amigo (pide confirmar con un segundo clic)
		boolean armed = System.currentTimeMillis() - removeArmed < 3000;
		int ry2 = ry + rh - 22;
		boolean hover = in(mouseX, mouseY, colX, ry2, colW, 16);
		GhostUi.roundBox(g, colX, ry2, colW, 16, armed ? 0xFF4A1A26 : hover ? 0xFF2A1C26 : 0x00000000, armed || hover ? DANGER : GhostUi.BORDER);
		g.drawCenteredString(font, Component.translatable(armed ? "ghost.social.remove.confirm" : "ghost.social.remove"), colX + colW / 2, ry2 + 4, armed || hover ? 0xFFFF8A9A : GhostUi.MUTED);
		g.fill(rx + 84, ry + 8, rx + 85, ry + rh - 8, 0x402E2E3E);

		drawActions(g, f, mouseX, mouseY);
		drawChat(g, f, mouseX, mouseY);

		if (chatBox.getValue().isEmpty() && !chatBox.isFocused()) {
			g.drawString(font, fit(Component.translatable("ghost.social.write.to", f.name).getString(), chatW - 36), chatBox.getX(), chatBox.getY(), GhostUi.MUTED, false);
		}
		boolean sendHover = in(mouseX, mouseY, chatX + chatW - 22, ry + rh - 25, 18, 17);
		boolean canSend = !chatBox.getValue().isBlank();
		GhostUi.roundRect(g, chatX + chatW - 22, ry + rh - 25, 18, 17, canSend ? (sendHover ? GhostUi.ACCENT_HOVER : GhostUi.ACCENT) : 0xFF2A2A3A);
		g.blit(RenderPipelines.GUI_TEXTURED, GhostHooks.icon("send"), chatX + chatW - 18, ry + rh - 21, 0, 0, 10, 10, 10, 10);
	}

	/** Barra encima del chat: dónde está tu amigo y los botones Unirse / Invitar. */
	private void drawActions(GuiGraphics g, GhostSocial.Friend f, int mouseX, int mouseY) {
		joinRect = null;
		inviteRect = null;
		int y = ry + 8, right = chatX + chatW;
		String here = GhostHooks.currentServer();
		boolean canJoin = f.online && "server".equals(f.activity) && f.server != null && !f.server.equalsIgnoreCase(here);
		boolean canInvite = f.online && here != null && !(f.server != null && f.server.equalsIgnoreCase(here));
		if (canJoin) {
			Component label = Component.translatable("ghost.social.join");
			int w = font.width(label) + 16;
			right -= w;
			joinRect = new int[]{right, y, w, 16};
			boolean hover = in(mouseX, mouseY, right, y, w, 16);
			GhostUi.roundRect(g, right, y, w, 16, hover ? GhostUi.ACCENT_HOVER : GhostUi.ACCENT);
			g.drawCenteredString(font, label, right + w / 2, y + 4, 0xFFFFFFFF);
			right -= 4;
		}
		if (canInvite) {
			Component label = Component.translatable("ghost.social.invite");
			int w = font.width(label) + 16;
			right -= w;
			inviteRect = new int[]{right, y, w, 16};
			boolean hover = in(mouseX, mouseY, right, y, w, 16);
			GhostUi.roundBox(g, right, y, w, 16, hover ? 0xFF2A2440 : 0x00000000, hover ? GhostUi.ACCENT : GhostUi.BORDER);
			g.drawCenteredString(font, label, right + w / 2, y + 4, hover ? GhostUi.TEXT : GhostUi.MUTED);
			right -= 4;
		}
		String where = f.online && "server".equals(f.activity) && f.server != null
			? Component.translatable("ghost.social.playing.on", f.server).getString() : statusOf(f).getString();
		g.drawString(font, fit(where, right - chatX - 8), chatX + 4, y + 4, f.online ? 0xFF8FD9A8 : GhostUi.MUTED, false);
		g.fill(chatX, y + 19, chatX + chatW, y + 20, 0x402E2E3E);
	}

	/** Aviso arriba cuando un amigo te invita a su servidor. */
	private void drawInvite(GuiGraphics g, int mouseX, int mouseY) {
		acceptRect = null;
		dismissRect = null;
		GhostSocial.Invite inv = GhostSocial.get().invite();
		if (inv == null) return;
		int x = px + 150, w = pw - 150 - 78, y = py + 10;
		if (w < 120) return;
		GhostUi.roundBox(g, x, y, w, 20, 0xF01E1A34, GhostUi.ACCENT);
		Component join = Component.translatable("ghost.social.join");
		int jw = font.width(join) + 12;
		acceptRect = new int[]{x + w - jw - 20, y + 3, jw, 14};
		dismissRect = new int[]{x + w - 17, y + 3, 14, 14};
		boolean hj = in(mouseX, mouseY, acceptRect[0], acceptRect[1], jw, 14);
		GhostUi.roundRect(g, acceptRect[0], acceptRect[1], jw, 14, hj ? GhostUi.ACCENT_HOVER : GhostUi.ACCENT);
		g.drawCenteredString(font, join, acceptRect[0] + jw / 2, y + 6, 0xFFFFFFFF);
		iconButton(g, "close", dismissRect[0], dismissRect[1], mouseX, mouseY, 0xFF4A2A38);
		String text = Component.translatable("ghost.social.invite.banner", inv.fromName(), inv.server()).getString();
		g.drawString(font, fit(text, w - jw - 32), x + 6, y + 6, GhostUi.TEXT, false);
	}

	private static boolean in(double mx, double my, int[] r) {
		return r != null && in(mx, my, r[0], r[1], r[2], r[3]);
	}

	private void layoutChat(GhostSocial.Friend f) {
		GhostSocial social = GhostSocial.get();
		int rev = social.revision();
		if (selected.equals(linesFor) && rev == linesRevision && chatW == linesWidth) return;
		linesFor = selected;
		linesRevision = rev;
		linesWidth = chatW;
		List<Line> out = new ArrayList<>();
		String me = social.me() == null ? "" : social.me().uuid();
		List<GhostSocial.Message> msgs = social.chat(selected);
		int maxW = (int) (chatW * 0.72f) - 12;
		long prevTs = 0;
		String prevFrom = null;
		for (int i = 0; i < msgs.size(); i++) {
			GhostSocial.Message m = msgs.get(i);
			String sep = null;
			if (prevTs == 0 || m.ts() - prevTs > 10 * 60_000) {
				var when = Instant.ofEpochMilli(m.ts()).atZone(ZoneId.systemDefault());
				sep = when.toLocalDate().equals(LocalDate.now()) ? when.format(TIME) : when.format(DATE) + " · " + when.format(TIME);
			}
			boolean mine = m.from().equals(me);
			GhostSocial.Message next = i + 1 < msgs.size() ? msgs.get(i + 1) : null;
			boolean groupStart = sep != null || !m.from().equals(prevFrom);
			boolean groupEnd = next == null || !next.from().equals(m.from()) || next.ts() - m.ts() > 10 * 60_000;
			List<FormattedCharSequence> wrapped = font.split(Component.literal(m.text()), maxW);
			int w = 0;
			for (FormattedCharSequence l : wrapped) w = Math.max(w, font.width(l));
			for (int k = 0; k < wrapped.size(); k++) {
				out.add(new Line(wrapped.get(k), mine, m.pending(), k == 0, k == wrapped.size() - 1, w, k == 0 && groupStart ? (sep == null ? "" : sep) : null));
			}
			if (groupEnd) out.add(new Line(null, mine, false, false, false, 0, null));
			prevTs = m.ts();
			prevFrom = m.from();
		}
		lines = out;
	}

	private void drawChat(GuiGraphics g, GhostSocial.Friend f, int mouseX, int mouseY) {
		GhostSocial social = GhostSocial.get();
		if (!social.historyLoaded(selected)) {
			g.drawCenteredString(font, Component.translatable("ghost.social.loading"), chatX + chatW / 2, chatY + chatH / 2 - 4, GhostUi.MUTED);
			return;
		}
		layoutChat(f);
		if (lines.isEmpty()) {
			int cx = chatX + chatW / 2, cy = chatY + chatH / 2 - 16;
			g.blit(RenderPipelines.GUI_TEXTURED, GhostHooks.icon("chat"), cx - 10, cy - 6, 0, 0, 20, 20, 20, 20);
			g.drawCenteredString(font, Component.translatable("ghost.social.chat.empty", f.name), cx, cy + 22, GhostUi.MUTED);
			return;
		}
		// Altura total (cada línea 10 px, más el relleno de las burbujas y los separadores)
		int total = 0;
		for (Line l : lines) total += heightOf(l);
		chatMax = Math.max(0, total - chatH);
		chatScroll = Math.max(0, Math.min(chatScroll, chatMax));
		if (chatScroll >= chatMax - 2 && social.hasMoreHistory(selected) && !olderRequested) {
			olderRequested = true;
			social.loadOlder(selected);
		}
		int y = chatY + chatH - total + (int) chatScroll;
		g.enableScissor(chatX, chatY, chatX + chatW, chatY + chatH);
		for (Line l : lines) {
			int h = heightOf(l);
			if (y + h >= chatY && y <= chatY + chatH) drawLine(g, l, y);
			y += h;
		}
		g.disableScissor();
		if (chatScroll > 4) {
			// Hay mensajes nuevos más abajo
			g.fill(chatX + chatW / 2 - 18, chatY + chatH - 3, chatX + chatW / 2 + 18, chatY + chatH - 1, 0x807C5CFF);
		}
	}

	private static int heightOf(Line l) {
		if (l.text() == null) return 4;
		int h = 10;
		if (l.first()) h += 3;
		if (l.last()) h += 3;
		if (l.separator() != null && !l.separator().isEmpty()) h += 14;
		return h;
	}

	private void drawLine(GuiGraphics g, Line l, int y) {
		if (l.text() == null) return;
		if (l.separator() != null && !l.separator().isEmpty()) {
			g.drawCenteredString(font, l.separator(), chatX + chatW / 2, y + 2, 0xFF5E5E72);
			y += 14;
		}
		// Cada mensaje es una burbuja; si ocupa varias líneas, cada línea pinta su trozo
		int bw = l.width() + 12;
		int bx = l.mine() ? chatX + chatW - bw - 4 : chatX + 4;
		int segH = 10 + (l.first() ? 3 : 0) + (l.last() ? 3 : 0);
		int color = l.mine() ? (l.pending() ? 0xFF4A3A8C : GhostUi.ACCENT) : BUBBLE_THEM;
		if (l.first() && l.last()) GhostUi.roundRect(g, bx, y, bw, segH, color);
		else if (l.first()) GhostUi.roundRect(g, bx, y, bw, segH + 3, color);
		else if (l.last()) GhostUi.roundRect(g, bx, y - 3, bw, segH + 3, color);
		else g.fill(bx, y, bx + bw, y + segH, color);
		g.drawString(font, l.text(), bx + 6, y + (l.first() ? 3 : 0), l.mine() ? 0xFFFFFFFF : GhostUi.TEXT, false);
	}

	private void drawNotice(GuiGraphics g) {
		String[] n = GhostSocial.get().notice();
		if (n == null) return;
		Component text = Component.translatable(n[0], n[1]);
		boolean error = n[0].contains(".error.");
		int w = Math.min(pw - 40, font.width(text) + 20);
		int x = px + (pw - w) / 2, y = py + ph - 8 - 18 - 4;
		GhostUi.roundBox(g, x, y - 20, w, 18, error ? 0xF02A1C26 : 0xF01E1A34, error ? DANGER : GhostUi.ACCENT);
		g.drawCenteredString(font, fit(text.getString(), w - 12), x + w / 2, y - 15, error ? 0xFFFF8A9A : GhostUi.TEXT);
	}

	// ---------- Ratón y teclado ----------

	@Override
	public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		GhostSocial social = GhostSocial.get();
		if (event.button() == 0) {
			GhostSocial.Invite inv = social.invite();
			if (inv != null && in(mx, my, acceptRect)) {
				social.clearInvite();
				onClose();
				GhostHooks.join(inv.server());
				return true;
			}
			if (inv != null && in(mx, my, dismissRect)) {
				social.clearInvite();
				return true;
			}
			if (selected != null && in(mx, my, joinRect)) {
				GhostSocial.Friend f = social.friend(selected);
				if (f != null && f.server != null) {
					onClose();
					GhostHooks.join(f.server);
				}
				return true;
			}
			if (selected != null && in(mx, my, inviteRect)) {
				social.invite(selected, GhostHooks.currentServer());
				return true;
			}
			if (in(mx, my, listX + LIST_W - 20, py + 42, 16, 16)) {
				submitAdd();
				return true;
			}
			if (in(mx, my, listX, py + ph - 26, LIST_W, 16)) {
				GhostPrefs.showServer = !GhostPrefs.showServer;
				GhostPrefs.save();
				social.setStatus("menu", null); // se vuelve a enviar el estado en el siguiente segundo
				return true;
			}
			if (selected != null && social.state() == GhostSocial.State.ONLINE) {
				if (in(mx, my, chatX + chatW - 22, ry + rh - 25, 18, 17)) {
					submitChat();
					return true;
				}
				if (in(mx, my, rx + 8, ry + rh - 22, 72, 16)) {
					if (System.currentTimeMillis() - removeArmed < 3000) {
						social.remove(selected);
						select(null);
					} else {
						removeArmed = System.currentTimeMillis();
					}
					return true;
				}
			}
			if (in(mx, my, listX, listY, LIST_W, listH)) {
				for (Row r : rows) {
					int y = listY - (int) listScroll + r.y();
					if (my < y || my >= y + r.h() || "title".equals(r.kind())) continue;
					switch (r.kind()) {
						case "incoming" -> {
							if (in(mx, my, listX + LIST_W - 34, y + 5, 14, 14)) social.accept(r.uuid());
							else if (in(mx, my, listX + LIST_W - 18, y + 5, 14, 14)) social.decline(r.uuid());
						}
						case "outgoing" -> {
							if (in(mx, my, listX + LIST_W - 18, y + 5, 14, 14)) social.cancel(r.uuid());
						}
						default -> {
							select(r.uuid());
							setFocused(chatBox);
						}
					}
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (in(mouseX, mouseY, listX, listY, LIST_W + 6, listH)) {
			listScroll = Math.max(0, Math.min(listMax, listScroll - vertical * 16));
			return true;
		}
		if (selected != null && in(mouseX, mouseY, chatX, chatY, chatW, chatH)) {
			chatScroll = Math.max(0, Math.min(chatMax, chatScroll + vertical * 16));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(@NotNull KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			if (addBox.isFocused()) {
				submitAdd();
				return true;
			}
			if (chatBox.isFocused() && chatBox.visible) {
				submitChat();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void submitAdd() {
		String name = addBox.getValue().trim();
		if (name.isEmpty()) {
			setFocused(addBox);
			return;
		}
		GhostSocial.get().addFriend(name);
		addBox.setValue("");
	}

	private void submitChat() {
		if (selected == null || chatBox.getValue().isBlank()) return;
		GhostSocial.get().sendChat(selected, chatBox.getValue());
		chatBox.setValue("");
		chatScroll = 0;
	}
}
