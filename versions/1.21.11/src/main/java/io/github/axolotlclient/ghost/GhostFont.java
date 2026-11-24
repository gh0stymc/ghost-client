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

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * ghost client: la letra del launcher (Inter) en todos los menús de ghost client.
 * Mientras se dibuja o se prepara una pantalla de ghost client, el texto con la letra normal pasa a Inter
 * (ver GhostFontScreenMixin, GhostFontTextMixin y GhostFontMixin). El HUD de la partida no se toca.
 */
public final class GhostFont {
	public static final FontDescription REGULAR = new FontDescription.Resource(Identifier.fromNamespaceAndPath("axolotlclient", "ghost"));
	public static final FontDescription BOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath("axolotlclient", "ghost_bold"));

	private static int depth;

	private GhostFont() {
	}

	/** Si ahora mismo se está dibujando una pantalla de ghost client. */
	public static boolean active() {
		return depth > 0;
	}

	/** Pantallas de ghost client (y de sus ajustes), salvo el editor del HUD, que enseña el HUD tal cual se ve. */
	private static boolean isGhost(Object screen) {
		if (!(screen instanceof Screen)) return false;
		String name = screen.getClass().getName();
		return name.startsWith("io.github.axolotlclient.") && !name.contains("HudEdit");
	}

	public static boolean begin(Object screen) {
		if (!isGhost(screen)) return false;
		depth++;
		return true;
	}

	public static void end(boolean began) {
		if (began && depth > 0) depth--;
	}

	public static Style style(Style s) {
		return FontDescription.DEFAULT.equals(s.getFont()) ? s.withFont(REGULAR) : s;
	}

	/** El mismo texto con Inter (lo que ya tenga otra letra se queda como está). */
	public static FormattedCharSequence wrap(FormattedCharSequence seq) {
		return sink -> seq.accept((index, style, codePoint) -> sink.accept(index, style(style), codePoint));
	}

	/** Texto en Inter negrita (títulos). */
	public static MutableComponent bold(Component c) {
		return c.copy().withStyle(s -> s.withFont(BOLD));
	}

	public static MutableComponent bold(String s) {
		return bold(Component.literal(s));
	}
}
