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

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles;
import io.github.axolotlclient.modules.hud.GhostModsScreen;
import io.github.axolotlclient.modules.hud.GhostUi;
import io.github.axolotlclient.modules.hud.HudEditScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * ghost client: lo que se abre con Right Shift. Un panel pequeño con tu nombre y tres opciones:
 * el menú social (amigos y chat), el menú de mods y el vestidor (cosméticos).
 */
public class GhostMenuScreen extends Screen {
	private static final int PW = 316, PH = 182, TILE_H = 98, GAP = 8;
	private static final String[] TILES = {"social", "mods", "wardrobe"};

	private final Screen parent;
	private final long openedAt = System.currentTimeMillis();
	private int px, py, tileW, tileY;

	public GhostMenuScreen(Screen parent) {
		super(Component.literal("ghost client"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		px = (width - PW) / 2;
		py = (height - PH) / 2;
		tileW = (PW - 28 - GAP * 2) / 3;
		tileY = py + 44;
		int by = py + PH - 28;
		Component hud = Component.translatable("ghost.mods.edit_hud"), settings = Component.translatable("ghost.mods.settings");
		int hudW = font.width(hud) + 20, settingsW = font.width(settings) + 20;
		addRenderableWidget(new GhostUi.GhostButton(px + 14, by, hudW, 20, hud,
			btn -> minecraft.setScreen(new HudEditScreen(this)), false));
		addRenderableWidget(new GhostUi.GhostButton(px + 18 + hudW, by, settingsW, 20, settings,
			btn -> minecraft.setScreen(ConfigStyles.createScreen(this, AxolotlClient.getInstance().getConfigManager().getRoot())), false));
		addRenderableWidget(new GhostUi.GhostButton(px + PW - 74, by, 60, 20, Component.translatable("close"), btn -> onClose(), false));
	}

	private void open(String tile) {
		switch (tile) {
			case "social" -> minecraft.setScreen(new GhostSocialScreen(this));
			case "mods" -> minecraft.setScreen(new GhostModsScreen(this));
			default -> minecraft.setScreen(new GhostWardrobeScreen(this));
		}
	}

	@Override
	public void onClose() {
		AxolotlClientCommon.getInstance().saveConfig();
		minecraft.setScreen(parent);
	}

	@Override
	public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) super.renderBackground(g, mouseX, mouseY, delta);
		else g.fill(0, 0, width, height, 0x50000000);
		GhostUi.roundBox(g, px, py, PW, PH, 0xE010101A, GhostUi.BORDER);
		g.fill(px, py + 12, px + 2, py + 32, GhostUi.ACCENT);
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		GhostUi.header(g, px + 12, py + 10, Component.translatable("ghost.hub.hello", minecraft.getUser().getName()));
		statusPill(g, px + PW - 14, py + 18);

		// Las tarjetas entran deslizándose un poco al abrir
		float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 260f);
		for (int i = 0; i < TILES.length; i++) {
			float ti = Math.max(0f, Math.min(1f, t * 1.6f - i * 0.25f));
			int off = Math.round((1 - (1 - (1 - ti) * (1 - ti))) * 10);
			tile(g, TILES[i], i, px + 14 + i * (tileW + GAP), tileY + off, mouseX, mouseY);
		}
	}

	/** Estado de la conexión con el servidor de ghost client, arriba a la derecha. */
	private void statusPill(GuiGraphics g, int right, int y) {
		GhostSocial.State s = GhostSocial.get().state();
		String key = switch (s) {
			case ONLINE -> "ghost.hub.status.online";
			case CONNECTING -> "ghost.hub.status.connecting";
			case NO_ACCOUNT -> "ghost.hub.status.no_account";
			default -> "ghost.hub.status.offline";
		};
		int dot = s == GhostSocial.State.ONLINE ? 0xFF22C55E : s == GhostSocial.State.CONNECTING ? 0xFFFBBF24 : 0xFF6B6B80;
		Component label = Component.translatable(key);
		int w = font.width(label) + 20;
		GhostUi.roundBox(g, right - w, y - 6, w, 16, 0xC0181824, GhostUi.BORDER);
		g.fill(right - w + 7, y, right - w + 11, y + 4, dot);
		g.drawString(font, label, right - w + 14, y - 2, GhostUi.MUTED, false);
	}

	private boolean over(int i, int mouseX, int mouseY) {
		int x = px + 14 + i * (tileW + GAP);
		return mouseX >= x && mouseX < x + tileW && mouseY >= tileY && mouseY < tileY + TILE_H;
	}

	private void tile(GuiGraphics g, String id, int i, int x, int y, int mouseX, int mouseY) {
		boolean hover = over(i, mouseX, mouseY);
		if (hover) y -= 2;
		int color = switch (id) {
			case "social" -> 0xFF8B6CFF;
			case "mods" -> 0xFF22D3EE;
			default -> 0xFFF472B6;
		};
		GhostUi.roundBox(g, x, y, tileW, TILE_H, hover ? 0xEE1C1B2A : 0xD8151520, hover ? color : 0xFF22222F);
		// Brillo del color de la tarjeta, más fuerte al pasar el ratón
		for (int k = 0; k < 6; k++) g.fill(x + 1, y + 1 + k * 5, x + tileW - 1, y + 6 + k * 5, GhostUi.alpha(color, (hover ? 30 : 14) - k * (hover ? 5 : 2)));

		int cx = x + tileW / 2;
		GhostUi.roundRect(g, cx - 19, y + 12, 38, 38, GhostUi.alpha(color, hover ? 110 : 60));
		GhostUi.roundRect(g, cx - 18, y + 13, 36, 1, GhostUi.alpha(0xFFFFFFFF, 40));
		g.blit(RenderPipelines.GUI_TEXTURED, GhostHooks.icon(id), cx - 12, y + 19, 0, 0, 24, 24, 24, 24);

		g.drawCenteredString(font, GhostFont.bold(Component.translatable("ghost.hub." + id)), cx, y + 58, GhostUi.TEXT);
		g.drawCenteredString(font, subtitle(id), cx, y + 72, GhostUi.MUTED);
		g.drawString(font, String.valueOf(i + 1), x + 6, y + 5, 0xFF4A4A5E, false);

		if ("social".equals(id)) {
			int unread = GhostSocial.get().unreadTotal();
			if (unread > 0) {
				String n = unread > 99 ? "99+" : String.valueOf(unread);
				int bw = Math.max(14, font.width(n) + 8);
				GhostUi.roundRect(g, x + tileW - bw - 5, y + 5, bw, 13, 0xFFE94F64);
				g.drawCenteredString(font, n, x + tileW - 5 - bw / 2, y + 8, 0xFFFFFFFF);
			}
		}
	}

	private Component subtitle(String id) {
		GhostSocial social = GhostSocial.get();
		return switch (id) {
			case "social" -> social.state() != GhostSocial.State.ONLINE ? Component.translatable("ghost.hub.social.offline")
				: social.onlineCount() == 1 ? Component.translatable("ghost.hub.social.online.one")
				: social.onlineCount() > 0 ? Component.translatable("ghost.hub.social.online", social.onlineCount())
				: Component.translatable("ghost.hub.social.friends", social.friends().size());
			case "mods" -> Component.translatable("ghost.hub.mods.sub");
			default -> Component.translatable("ghost.hub.wardrobe.sub");
		};
	}

	@Override
	public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() != 0) return false;
		for (int i = 0; i < TILES.length; i++) {
			if (over(i, (int) event.x(), (int) event.y())) {
				minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
				open(TILES[i]);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(@NotNull KeyEvent event) {
		int key = event.key();
		// Right Shift otra vez: cerrar
		if (key == InputConstants.KEY_RSHIFT) {
			onClose();
			return true;
		}
		if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_3) {
			open(TILES[key - InputConstants.KEY_1]);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
