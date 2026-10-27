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

import io.github.axolotlclient.mixin.PlayerSkinWidgetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Tu skin en 3D en el menú principal (como Essential). Va haciendo gestos por turnos: saludar, agacharse,
 * celebrar, mirar alrededor y bailar; entre uno y otro se queda tranquila. Se puede girar arrastrando con el ratón.
 */
public class GhostSkinPreview extends PlayerSkinWidget {
	private static final float ACTION = 3.6f, CYCLE = 5.5f, PI = (float) Math.PI;
	private final int baseY;
	private long lastDrag;
	private final long start = System.currentTimeMillis();

	public GhostSkinPreview(int x, int y, int width, int height) {
		super(width, height, Minecraft.getInstance().getEntityModels(),
			Minecraft.getInstance().getSkinManager().createLookup(Minecraft.getInstance().getGameProfile(), false));
		setPosition(x, y);
		this.baseY = y;
	}

	private static float sin(float v) {
		return (float) Math.sin(v);
	}

	private void animate() {
		float t = (System.currentTimeMillis() - start) / 1000f;
		var acc = (PlayerSkinWidgetAccessor) this;
		long sinceDrag = System.currentTimeMillis() - lastDrag;
		if (sinceDrag > 2500) {
			// vuelve poco a poco a su giro suave después de moverla con el ratón
			float k = Math.min(1f, (sinceDrag - 2500) / 1500f) * 0.08f;
			float targetY = 22f + sin(t * 0.35f) * 25f;
			acc.ghost$setRotationY(acc.ghost$getRotationY() + (targetY - acc.ghost$getRotationY()) * k);
			acc.ghost$setRotationX(acc.ghost$getRotationX() + (-4f - acc.ghost$getRotationX()) * k);
		}
		pose(acc.ghost$getWideModel().root(), t);
		pose(acc.ghost$getSlimModel().root(), t);
	}

	/** Coloca brazos, piernas y cabeza según el gesto que toca ahora. */
	private static void pose(ModelPart root, float t) {
		root.getAllParts().forEach(ModelPart::resetPose);
		ModelPart head = root.getChild("head"), body = root.getChild("body");
		ModelPart rightArm = root.getChild("right_arm"), leftArm = root.getChild("left_arm");
		ModelPart rightLeg = root.getChild("right_leg"), leftLeg = root.getChild("left_leg");

		// respiración y mirada tranquila, siempre
		rightArm.zRot += 0.05f + sin(t * 1.4f) * 0.03f;
		leftArm.zRot -= 0.05f + sin(t * 1.4f) * 0.03f;
		head.yRot += sin(t * 0.7f) * 0.12f;

		int action = (int) (t / CYCLE) % 5;
		float p = (t % CYCLE) / ACTION;
		if (p >= 1) return;
		float e = sin(PI * Math.min(1f, p * 3f) / 2f) * sin(PI * Math.min(1f, (1f - p) * 3f) / 2f); // entra y sale suave

		switch (action) {
			case 0 -> { // saludar
				rightArm.zRot += 2.6f * e + sin(t * 10f) * 0.35f * e;
				rightArm.xRot -= 0.2f * e;
				head.zRot -= 0.12f * e;
				head.yRot -= 0.25f * e;
			}
			case 1 -> { // agacharse dos veces
				float c = (0.5f - 0.5f * (float) Math.cos(4 * PI * p)) * e;
				body.xRot += 0.5f * c;
				body.y += 3.2f * c;
				head.y += 4.2f * c;
				rightArm.xRot += 0.4f * c;
				leftArm.xRot += 0.4f * c;
				rightArm.y += 3.2f * c;
				leftArm.y += 3.2f * c;
				rightLeg.z += 4f * c;
				leftLeg.z += 4f * c;
				rightLeg.y += 0.2f * c;
				leftLeg.y += 0.2f * c;
			}
			case 2 -> { // celebrar con saltitos
				rightArm.zRot += 2.8f * e + sin(t * 12f) * 0.15f * e;
				leftArm.zRot -= 2.8f * e + sin(t * 12f) * 0.15f * e;
				root.y -= Math.abs(sin(t * 7f)) * 2.5f * e;
				head.xRot -= 0.25f * e;
			}
			case 3 -> { // mirar alrededor
				head.yRot += sin(2 * PI * p) * 0.8f * e;
				head.xRot += sin(4 * PI * p) * 0.15f * e;
				rightArm.xRot -= 0.15f * e;
			}
			default -> { // bailar
				float s = sin(t * 7f) * e;
				rightArm.xRot += 0.8f * s;
				leftArm.xRot -= 0.8f * s;
				rightLeg.xRot -= 0.5f * s;
				leftLeg.xRot += 0.5f * s;
				body.yRot += 0.15f * s;
				head.zRot += 0.1f * s;
			}
		}
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		animate();
		// sombra en el suelo
		int cx = getX() + getWidth() / 2, by = baseY + getHeight() + 2;
		for (int i = 0; i < 4; i++) {
			int w = getWidth() / 3 - i * 5;
			graphics.fill(cx - w, by - 1 + i / 2, cx + w, by + 1 + i / 2, 0x28000000);
		}
		super.renderWidget(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(Minecraft.getInstance().font, Component.literal(Minecraft.getInstance().getUser().getName()),
			cx, baseY + getHeight() + 8, 0xFFE8E4FF);
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
		lastDrag = System.currentTimeMillis();
		super.onDrag(event, dragX, dragY);
	}
}
