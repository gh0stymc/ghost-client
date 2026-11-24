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

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;

/**
 * ghost client: dibuja la mascota, el sombrero, lo de la espalda y el aura de cada jugador.
 * Sombrero → sigue a la cabeza; espalda → al cuerpo; mascota y aura → al jugador (sin inclinarse con el cuerpo).
 */
public class GhostCosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

	public GhostCosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	private static boolean warned;

	@Override
	public void submit(PoseStack ps, SubmitNodeCollector out, int light, AvatarRenderState state, float yRot, float xRot) {
		// Un error aquí tiraría el juego entero: si algo falla, el cosmético no se dibuja y se avisa una vez en el log.
		// Se dibuja sobre una copia del PoseStack, así un fallo a medias no deja descolocado el del juego.
		PoseStack local = new PoseStack();
		local.last().set(ps.last());
		try {
			draw(local, out, light, state);
		} catch (Throwable t) {
			if (!warned) {
				warned = true;
				org.slf4j.LoggerFactory.getLogger("ghost client").warn("No se pudo dibujar un cosmético", t);
			}
		}
	}

	private void draw(PoseStack ps, SubmitNodeCollector out, int light, AvatarRenderState state) {
		if (state.isInvisible || state.isSpectator) return;
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		Entity entity = level.getEntity(state.id);
		if (entity == null) return;
		UUID uuid = entity.getUUID();
		GhostCosmetics cos = GhostCosmetics.get();
		float time = state.ageInTicks / 20f;
		GhostModels.Pose pose = new GhostModels.Pose(Math.min(1f, state.walkAnimationSpeed * 1.6f), state.isCrouching);

		GhostModels.Model hat = GhostModels.get(cos.of(uuid, "hat"));
		if (hat != null) {
			ps.pushPose();
			getParentModel().head.translateAndRotate(ps);
			GhostModels.render(hat, ps, out, light, time, pose);
			ps.popPose();
		}
		GhostModels.Model back = GhostModels.get(cos.of(uuid, "back"));
		if (back != null) {
			ps.pushPose();
			getParentModel().body.translateAndRotate(ps);
			GhostModels.render(back, ps, out, light, time, pose);
			ps.popPose();
		}
		GhostModels.Model pet = GhostModels.get(cos.of(uuid, "pet"));
		if (pet != null) GhostModels.render(pet, ps, out, light, time, pose);
		GhostModels.Model aura = GhostModels.get(cos.of(uuid, "aura"));
		if (aura != null) GhostModels.render(aura, ps, out, light, time, pose);
	}
}
