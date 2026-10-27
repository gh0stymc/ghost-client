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

package io.github.axolotlclient.mixin;

import io.github.axolotlclient.AxolotlClientConfig.impl.ui.rounded.screen.RoundedScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ghost client: el título de las pantallas de ajustes (el nombre del mod) va centrado en la cabecera.
 */
@Mixin(value = RoundedScreen.class, remap = false)
public abstract class GhostRoundedScreenMixin {

	@Shadow
	private AbstractWidget title;

	@Shadow
	protected abstract int left();

	@Shadow
	protected abstract int bgWidth();

	@Inject(method = "repositionHeader", at = @At("RETURN"))
	private void ghost$centerTitle(CallbackInfoReturnable<Integer> cir) {
		if (title == null) {
			return;
		}
		// Dejamos sitio al buscador de la derecha: como mucho un tercio del ancho
		((StringWidget) title).setMaxWidth(bgWidth() / 3, StringWidget.TextOverflow.SCROLLING);
		title.setX(left() + bgWidth() / 2 - title.getWidth() / 2);
	}
}
