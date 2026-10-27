/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
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

import java.util.function.IntSupplier;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.util.GhostLoadingScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LoadingOverlay.class)
public abstract class SplashOverlayMixin {

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private static int axolotlclient$customBackgroundColor(IntSupplier instance, Operation<Integer> original) {
		if (AxolotlClient.config().customLoadingScreenColor.get()) {
			return AxolotlClient.config().loadingScreenColor.get().toInt();
		}
		return original.call(instance);
	}

	// ghost client: en lugar del logo de Mojang, nuestra animación (o la imagen/GIF/vídeo del usuario) a pantalla
	// completa. Se dibuja aquí porque el logo se dibuja en cada fotograma (el fondo, al arrancar, es solo el color
	// con el que se borra la pantalla) y con su misma transparencia, así también se funde al terminar.
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V", ordinal = 0))
	private void ghost$drawFrames(GuiGraphics graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int w, int h, int uw, int vh, int tw, int th, int color, Operation<Void> original) {
		if (!GhostLoadingScreen.active()) {
			original.call(graphics, pipeline, texture, x, y, u, v, w, h, uw, vh, tw, th, color);
			return;
		}
		Identifier tex = GhostLoadingScreen.frame();
		int fw = GhostLoadingScreen.width(), fh = GhostLoadingScreen.height();
		int sw = graphics.guiWidth(), sh = graphics.guiHeight();
		// Cubre toda la pantalla sin deformar la imagen (se recorta lo que sobra)
		float scale = Math.max((float) sw / fw, (float) sh / fh);
		int dw = Math.round(fw * scale), dh = Math.round(fh * scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, tex, (sw - dw) / 2, (sh - dh) / 2, 0, 0, dw, dh, fw, fh, fw, fh, (color & 0xFF000000) | 0xFFFFFF);
	}

	// ghost client: la otra mitad del logo de Mojang tampoco se dibuja
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V", ordinal = 1))
	private void ghost$hideLogo(GuiGraphics graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int w, int h, int uw, int vh, int tw, int th, int color, Operation<Void> original) {
		if (!GhostLoadingScreen.active()) {
			original.call(graphics, pipeline, texture, x, y, u, v, w, h, uw, vh, tw, th, color);
		}
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setOverlay(Lnet/minecraft/client/gui/screens/Overlay;)V"))
	private void ghost$releaseFrames(CallbackInfo ci) {
		// un poco después: la textura aún se usa al terminar de dibujar este fotograma
		java.util.concurrent.CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.SECONDS)
			.execute(() -> net.minecraft.client.Minecraft.getInstance().execute(GhostLoadingScreen::release));
	}
}
