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

package io.github.axolotlclient.modules.hud;

import io.github.axolotlclient.AxolotlClientCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * ghost client: piezas visuales con el estilo de Ghost Launcher (paneles y botones redondeados).
 */
public final class GhostUi {
	public static final int PANEL = 0xD812121B;
	public static final int BORDER = 0xFF2E2E3E;
	public static final int SURFACE = 0xFF1A1A26;
	public static final int SURFACE_HOVER = 0xFF262238;
	public static final int ACCENT = 0xFF7C5CFF;
	public static final int ACCENT_HOVER = 0xFF9278FF;
	public static final int TEXT = 0xFFF1F1F6;
	public static final int MUTED = 0xFF8A8A9E;

	private GhostUi() {
	}

	/** Rectángulo con las esquinas recortadas 2 px (sin solapar, para que la transparencia sea uniforme). */
	public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x + 2, y, x + w - 2, y + 1, color);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, color);
		g.fill(x, y + 2, x + w, y + h - 2, color);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
		g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
	}

	/** Caja redondeada con borde de 1 px. */
	public static void roundBox(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
		roundRect(g, x, y, w, h, border);
		roundRect(g, x + 1, y + 1, w - 2, h - 2, fill);
	}

	/** Cabecera con el fantasma y el nombre del cliente. */
	public static void header(GuiGraphicsExtractor g, int x, int y, Component subtitle) {
		var font = Minecraft.getInstance().font;
		g.blit(RenderPipelines.GUI_TEXTURED, (Identifier) AxolotlClientCommon.BADGE_PATH, x, y, 0, 0, 24, 24, 24, 24);
		g.text(font, Component.literal("ghost client"), x + 30, y + 3, TEXT, false);
		g.text(font, subtitle, x + 30, y + 14, MUTED, false);
	}

	/** Botón con el estilo del launcher. Los principales van en morado. */
	public static class GhostButton extends Button {
		private final boolean primary;

		public GhostButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean primary) {
			super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
			this.primary = primary;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			boolean hover = isHoveredOrFocused() && isActive();
			int fill = primary ? (hover ? ACCENT_HOVER : ACCENT) : (hover ? SURFACE_HOVER : SURFACE);
			int border = primary ? fill : (hover ? ACCENT : BORDER);
			roundBox(g, getX(), getY(), getWidth(), getHeight(), fill, border);
			var font = Minecraft.getInstance().font;
			g.centeredText(font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, isActive() ? TEXT : MUTED);
		}
	}
}
