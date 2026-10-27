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

package io.github.axolotlclient.util;

import io.github.axolotlclient.bridge.entity.AxoEntity;
import io.github.axolotlclient.modules.sounds.SoundEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/** ghost client: le dice a "sound effects" si un jugador (o tú) acaba de morir. */
public class GhostSoundTracker implements SoundEffects.Tracker {
	@Override
	public boolean isDead(AxoEntity entity) {
		return entity instanceof LivingEntity living && living.isDeadOrDying();
	}

	@Override
	public boolean isLocalPlayerDead() {
		var player = Minecraft.getInstance().player;
		return player != null && player.isDeadOrDying();
	}
}
