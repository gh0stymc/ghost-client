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

import net.minecraft.client.gui.components.PlayerSkinWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerSkinWidget.class)
public interface PlayerSkinWidgetAccessor {
	@Accessor("rotationX")
	float ghost$getRotationX();

	@Accessor("rotationX")
	void ghost$setRotationX(float value);

	@Accessor("rotationY")
	float ghost$getRotationY();

	@Accessor("rotationY")
	void ghost$setRotationY(float value);

	@Accessor("wideModel")
	net.minecraft.client.model.player.PlayerModel ghost$getWideModel();

	@Accessor("slimModel")
	net.minecraft.client.model.player.PlayerModel ghost$getSlimModel();
}
