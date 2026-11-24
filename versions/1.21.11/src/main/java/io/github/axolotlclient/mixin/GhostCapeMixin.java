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

import io.github.axolotlclient.ghost.GhostCosmetics;
import io.github.axolotlclient.ghost.GhostHooks;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ghost client: los jugadores con una capa de ghost client la llevan puesta (también en las elytras). */
@Mixin(AbstractClientPlayer.class)
public abstract class GhostCapeMixin {

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void ghost$cape(CallbackInfoReturnable<PlayerSkin> cir) {
		String cape = GhostCosmetics.get().capeOf(((AbstractClientPlayer) (Object) this).getUUID());
		if (cape != null) cir.setReturnValue(GhostHooks.withCape(cir.getReturnValue(), cape));
	}
}
