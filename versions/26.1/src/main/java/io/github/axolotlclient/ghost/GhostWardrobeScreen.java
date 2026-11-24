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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.github.axolotlclient.modules.hud.GhostUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.NotNull;

/**
 * ghost client: vestidor. Cosméticos gratis que solo ven los jugadores con ghost client.
 * Pestañas: capas, mascotas, sombreros, espalda y auras. A la izquierda tu personaje con lo que llevas
 * (y lo que tienes debajo del ratón); a la derecha las opciones para elegir.
 */
public class GhostWardrobeScreen extends Screen {
	private static final int CARD_W = 64, CARD_H = 92, GAP = 6;
	private static final int TAB_H = 16;
	/** Pestaña abierta (hueco: cape, pet, hat, back, aura) */
	private static String tab = "cape";

	/** Solo para pruebas (-Dghostclient.debug.wardrobe). */
	static void debugTab(String t) {
		tab = t;
	}

	/** Opciones de la pestaña: null = nada */
	private static List<String> options() {
		List<String> out = new ArrayList<>();
		out.add(null);
		out.addAll(GhostCosmetics.idsFor(tab));
		return out;
	}

	private final Screen parent;
	private Supplier<PlayerSkin> mySkin;
	private int px, py, pw, ph, prevX, prevY, prevW, prevH, gridX, gridY, gridW, gridH, cols, maxScroll;
	private double scroll;
	private String hovered;
	private boolean hovering;
	private boolean waiting;
	private String waitingFor;
	private long waitingSince;

	public GhostWardrobeScreen(Screen parent) {
		super(Component.translatable("ghost.hub.wardrobe"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		pw = Math.min(width - 24, 470);
		ph = Math.min(height - 20, 290);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		prevX = px + 10;
		prevY = py + 40;
		prevW = 132;
		prevH = ph - 50;
		gridX = prevX + prevW + 14;
		gridY = py + 58;
		gridW = px + pw - 12 - gridX;
		gridH = py + ph - 50 - gridY;
		cols = Math.max(1, (gridW + GAP) / (CARD_W + GAP));
		layoutScroll();
		mySkin = minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false);
		addRenderableWidget(new GhostUi.GhostButton(px + pw - 70, py + 10, 60, 18, Component.translatable("gui.back"), _ -> onClose(), false));
	}

	private void layoutScroll() {
		int rows = (options().size() + cols - 1) / cols;
		maxScroll = Math.max(0, rows * (CARD_H + GAP) - GAP - gridH);
		scroll = Math.min(scroll, maxScroll);
	}

	@Override
	public void removed() {
		GhostCosmetics.get().clearPreview();
		super.removed();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(@NotNull GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) super.extractBackground(g, mouseX, mouseY, delta);
		else g.fill(0, 0, width, height, 0x50000000);
		GhostUi.roundBox(g, px, py, pw, ph, 0xE010101A, GhostUi.BORDER);
		g.fill(px, py + 12, px + 2, py + 32, GhostUi.ACCENT);
		GhostUi.roundBox(g, prevX, prevY, prevW, prevH, 0x900C0C14, GhostUi.BORDER);
	}

	private String equipped() {
		return GhostSocial.get().mine(tab);
	}

	@Override
	public void extractRenderState(@NotNull GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		GhostUi.header(g, px + 12, py + 9, Component.translatable("ghost.wardrobe.subtitle"));
		if (waiting && (java.util.Objects.equals(waitingFor, equipped()) || System.currentTimeMillis() - waitingSince > 5000)) waiting = false;

		// Las tarjetas
		drawTabs(g, mouseX, mouseY);
		hovering = false;
		boolean inGrid = mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH;
		g.enableScissor(gridX - 2, gridY - 2, gridX + gridW + 2, gridY + gridH);
		List<String> options = options();
		for (int i = 0; i < options.size(); i++) {
			int x = gridX + (i % cols) * (CARD_W + GAP);
			int y = gridY + (i / cols) * (CARD_H + GAP) - (int) scroll;
			if (y + CARD_H < gridY || y > gridY + gridH) continue;
			String id = options.get(i);
			boolean hover = inGrid && mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
			if (hover) {
				hovered = id;
				hovering = true;
			}
			card(g, id, x, y, hover);
		}
		g.disableScissor();
		if (maxScroll > 0) {
			int barH = Math.max(16, gridH * gridH / (gridH + maxScroll));
			int barY = gridY + (int) ((gridH - barH) * (scroll / maxScroll));
			GhostUi.roundRect(g, px + pw - 7, barY, 3, barH, GhostUi.alpha(GhostUi.ACCENT, 160));
		}

		// Lo que tienes debajo del ratón se ve puesto en tu personaje (solo para ti, hasta que cierres el vestidor)
		GhostCosmetics.get().clearPreview();
		if (hovering) GhostCosmetics.get().preview(tab, hovered, true);
		else if (waiting) GhostCosmetics.get().preview(tab, waitingFor, true);
		String shown = hovering ? hovered : (waiting ? waitingFor : equipped());
		if (tab.equals("cape") || minecraft.player == null) drawPreview(g, shown);
		else draw3d(g, mouseX, mouseY);
		drawFooter(g, mouseX, mouseY);
	}

	private void card(GuiGraphicsExtractor g, String id, int x, int y, boolean hover) {
		boolean on = java.util.Objects.equals(id, equipped());
		boolean online = GhostSocial.get().state() == GhostSocial.State.ONLINE;
		int left = GhostCosmetics.hoursLeft(id);
		GhostUi.roundBox(g, x, y, CARD_W, CARD_H, on ? 0xF0221E34 : hover ? 0xE01E1C2C : 0xC81A1A26, on ? GhostUi.ACCENT : hover ? 0xFF4A4466 : GhostUi.BORDER);
		int cx = x + CARD_W / 2;
		if (id == null) {
			GhostUi.roundRect(g, cx - 18, y + 16, 36, 36, 0x22FFFFFF);
			g.blit(RenderPipelines.GUI_TEXTURED, GhostHooks.icon("close"), cx - 8, y + 26, 0, 0, 16, 16, 16, 16);
		} else {
			if (tab.equals("cape")) {
				// La capa renderizada en 3D (card.png, 120x180): una lámina fina con luz y sombra
				GhostUi.roundRect(g, cx - 24, y + 6, 48, 56, GhostUi.alpha(colorOf(id), on || hover ? 60 : 28));
				int lift = hover ? (int) Math.round(Math.sin(System.currentTimeMillis() / 220.0) * 1.5) - 1 : 0;
				g.blit(RenderPipelines.GUI_TEXTURED, card(id), cx - 21, y + 2 + lift, 0, 0, 42, 63, 120, 180, 120, 180);
			} else {
				GhostUi.roundRect(g, cx - 26, y + 6, 52, 52, GhostUi.alpha(colorOf(id), on || hover ? 70 : 35));
				int bob = hover ? (int) Math.round(Math.sin(System.currentTimeMillis() / 220.0) * 2) : 0;
				g.blit(RenderPipelines.GUI_TEXTURED, icon(id), cx - 22, y + 10 + bob, 0, 0, 44, 44, 128, 128, 128, 128);
			}
		}
		if (left > 0) {
			// Bloqueado: más oscuro y con un candado (al pasar el ratón se puede ver puesto igualmente)
			if (!hover) GhostUi.roundRect(g, x + 1, y + 1, CARD_W - 2, CARD_H - 30, 0x90101018);
			GhostUi.roundRect(g, x + 4, y + 4, 14, 14, 0xE0101018);
			g.blit(RenderPipelines.GUI_TEXTURED, GhostHooks.icon("lock"), x + 6, y + 6, 0, 0, 10, 10, 10, 10);
		}
		String name = Component.translatable(id == null ? (tab.equals("cape") ? "ghost.wardrobe.none" : "ghost.wardrobe.nothing") : "ghost.cosmetic." + id).getString();
		if (font.width(name) > CARD_W - 6) name = font.plainSubstrByWidth(name, CARD_W - 10) + "…";
		g.centeredText(font, GhostFont.bold(Component.literal(name)), cx, y + CARD_H - 25, GhostUi.TEXT);
		Component sub;
		int subColor = GhostUi.MUTED;
		if (waiting && java.util.Objects.equals(id, waitingFor)) sub = Component.translatable("ghost.wardrobe.applying");
		else if (on) {
			sub = Component.translatable("ghost.wardrobe.wearing");
			subColor = 0xFFB9A8FF;
		} else if (left > 0) {
			sub = Component.translatable("ghost.wardrobe.locked", GhostCosmetics.UNLOCK.get(id));
			subColor = 0xFFFFD36B;
		} else if (!online) sub = Component.translatable("ghost.wardrobe.offline");
		else sub = Component.translatable(id == null ? (tab.equals("cape") ? "ghost.wardrobe.none.sub" : "ghost.wardrobe.nothing.sub") : tagKey(id));
		String subText = sub.getString();
		if (font.width(subText) > CARD_W - 6) subText = font.plainSubstrByWidth(subText, CARD_W - 10) + "…";
		g.centeredText(font, Component.literal(subText), cx, y + CARD_H - 13, subColor);
		if (on) {
			GhostUi.roundRect(g, x + CARD_W - 16, y + 4, 12, 12, GhostUi.ACCENT);
			g.blit(RenderPipelines.GUI_TEXTURED, GhostHooks.icon("check"), x + CARD_W - 14, y + 6, 0, 0, 8, 8, 8, 8);
		}
	}

	/** Color de cada capa (para el marco de su tarjeta). */
	private static int colorOf(String id) {
		return switch (id) {
			case "ghost_cape_pink" -> 0xFFFF5FB4;
			case "ghost_cape_white" -> 0xFFDCDCF0;
			case "ghost_cape_orange" -> 0xFFFF8A1F;
			case "ghost_cape_red" -> 0xFFFF3B4F;
			case "ghost_cape_green" -> 0xFF2EE87A;
			case "ghost_cape_blue" -> 0xFF3B8BFF;
			case "ghost_cape_halloween" -> 0xFFFF7A1A;
			case "ghost_cape_christmas" -> 0xFFE8212F;
			case "cape_aurora" -> 0xFF3DFFB0;
			case "cape_moon" -> 0xFFEFE8D4;
			case "cape_sakura" -> 0xFFF7A8C0;
			case "cape_sunset" -> 0xFFFF5FB4;
			case "cape_waves" -> 0xFF3B6EA8;
			case "cape_eclipse" -> 0xFFFFB347;
			case "pet_boo" -> 0xFFDCDCF0;
			case "pet_slime" -> 0xFF7EE86F;
			case "hat_crown" -> 0xFFF4C542;
			case "hat_witch" -> 0xFF8B5CF6;
			case "back_wings" -> 0xFFDCD6F2;
			case "back_sword" -> 0xFFA9B4C4;
			case "aura_orbit" -> 0xFF6EE7FF;
			case "aura_sparks" -> 0xFFFFD36B;
			case "cape_medal_500" -> 0xFFF5C23E;
			case "cape_medal_750" -> 0xFF2EE87A;
			case "cape_medal_1000" -> 0xFFA77BFF;
			case "cape_medal_2500" -> 0xFF6FE3FF;
			case "cape_medal_5000" -> 0xFFFF5A7A;
			case "pet_bat" -> 0xFF7A4FA8;
			case "pet_dragon" -> 0xFF7C5CFF;
			case "hat_halo" -> 0xFFFFE680;
			case "hat_cat_ears" -> 0xFFF29AC0;
			case "back_dragon_wings" -> 0xFF9B6BFF;
			case "aura_frost" -> 0xFFBFEFFF;
			case "aura_flames" -> 0xFFFF8A1F;
			default -> GhostUi.ACCENT;
		};
	}

	/** Etiqueta bajo el nombre: de temporada o animada. */
	private static String tagKey(String id) {
		if (!tab.equals("cape")) return "ghost.wardrobe.tag." + tab;
		if (id.startsWith("cape_medal_")) return "ghost.wardrobe.tag.medal";
		if (id.startsWith("cape_")) return "ghost.wardrobe.tag.minimal";
		return id.endsWith("halloween") ? "ghost.wardrobe.tag.halloween" : id.endsWith("christmas") ? "ghost.wardrobe.tag.christmas" : "ghost.wardrobe.animated";
	}

	/** Tu personaje visto de espaldas (a partir de la textura de la skin) con la capa elegida encima. */
	private void drawPreview(GuiGraphicsExtractor g, String cape) {
		PlayerSkin skin = mySkin.get();
		int s = Math.max(2, Math.min(5, (prevH - 44) / 32));
		int cx = prevX + prevW / 2;
		int top = prevY + (prevH - 32 * s) / 2 - 6;

		// Brillo morado detrás y sombra en el suelo
		for (int i = 0; i < 5; i++) {
			int r = 12 + i * 9;
			GhostUi.roundRect(g, cx - r, top + 12 * s - r, r * 2, r * 2, 0x0C7C5CFF);
		}
		for (int i = 0; i < 3; i++) {
			int w = 6 * s - i * 4;
			g.fill(cx - w, top + 32 * s + 1 + i, cx + w, top + 32 * s + 3 + i, 0x30000000);
		}

		Identifier tex = skin.body().texturePath();
		boolean slim = skin.model() == PlayerModelType.SLIM;
		int aw = slim ? 3 : 4;
		// Base: cabeza, cuerpo, brazos y piernas de espaldas
		part(g, tex, 24, 8, 8, 8, cx - 4 * s, top, s);
		part(g, tex, 32, 20, 8, 12, cx - 4 * s, top + 8 * s, s);
		part(g, tex, slim ? 51 : 52, 20, aw, 12, cx + 4 * s, top + 8 * s, s); // brazo derecho (a nuestra derecha)
		part(g, tex, slim ? 43 : 44, 52, aw, 12, cx - 4 * s - aw * s, top + 8 * s, s); // brazo izquierdo
		part(g, tex, 12, 20, 4, 12, cx, top + 20 * s, s); // pierna derecha
		part(g, tex, 28, 52, 4, 12, cx - 4 * s, top + 20 * s, s); // pierna izquierda
		// Segunda capa (gorro, chaqueta, mangas, pantalón)
		part(g, tex, 56, 8, 8, 8, cx - 4 * s, top, s);
		part(g, tex, 32, 36, 8, 12, cx - 4 * s, top + 8 * s, s);
		part(g, tex, slim ? 51 : 52, 36, aw, 12, cx + 4 * s, top + 8 * s, s);
		part(g, tex, slim ? 59 : 60, 52, aw, 12, cx - 4 * s - aw * s, top + 8 * s, s);
		part(g, tex, 12, 36, 4, 12, cx, top + 20 * s, s);
		part(g, tex, 12, 52, 4, 12, cx - 4 * s, top + 20 * s, s);

		if (cape != null) {
			// La capa cuelga de los hombros: 10x16 píxeles de Minecraft
			g.fill(cx - 5 * s - 1, top + 8 * s, cx + 5 * s + 1, top + 24 * s + 1, 0x40000000);
			g.blit(RenderPipelines.GUI_TEXTURED, GhostHooks.capeTexture(cape), cx - 5 * s, top + 8 * s, 8, 8, 10 * s, 16 * s, 80, 128, 512, 256);
		}
		g.centeredText(font, minecraft.getUser().getName(), cx, prevY + prevH - 14, GhostUi.MUTED);
	}

	private static Identifier card(String id) {
		return Identifier.fromNamespaceAndPath("axolotlclient", "textures/ghost/cosmetics/" + id + "/card.png");
	}

	private static Identifier icon(String id) {
		return Identifier.fromNamespaceAndPath("axolotlclient", "textures/ghost/cosmetics/" + id + "/preview.png");
	}

	/** Tu personaje en 3D (sigue al ratón) con los cosméticos puestos; si no hay partida, el icono en grande. */
	private void draw3d(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int size = Math.min(48, (prevH - 40) / 4);
		InventoryScreen.extractEntityInInventoryFollowsMouse(g, prevX + 4, prevY + 8, prevX + prevW - 4, prevY + prevH - 20, size, 0.0625f, mouseX, mouseY, minecraft.player);
		g.centeredText(font, minecraft.getUser().getName(), prevX + prevW / 2, prevY + prevH - 14, GhostUi.MUTED);
	}

	/** Pestañas de arriba: una por tipo de cosmético. */
	private static final String[] TABS = {"cape", "pet", "hat", "back", "aura"};

	private int tabX(int i) {
		int w = (gridW - 4 * 4) / 5;
		return gridX + i * (w + 4);
	}

	private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int w = (gridW - 4 * 4) / 5;
		int y = py + 38;
		for (int i = 0; i < TABS.length; i++) {
			int x = tabX(i);
			boolean on = TABS[i].equals(tab);
			boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + TAB_H;
			GhostUi.roundBox(g, x, y, w, TAB_H, on ? GhostUi.alpha(GhostUi.ACCENT, 70) : hover ? 0xE01E1C2C : 0xC81A1A26, on ? GhostUi.ACCENT : GhostUi.BORDER);
			Component label = Component.translatable("ghost.wardrobe.tab." + TABS[i]);
			String text = label.getString();
			if (font.width(text) > w - 6) text = font.plainSubstrByWidth(text, w - 8) + "…";
			g.centeredText(font, on ? GhostFont.bold(Component.literal(text)) : Component.literal(text), x + w / 2, y + 4, on || hover ? GhostUi.TEXT : GhostUi.MUTED);
		}
	}

	private static void part(GuiGraphicsExtractor g, Identifier tex, int u, int v, int w, int h, int x, int y, int s) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w * s, h * s, w, h, 64, 64);
	}

	private void drawFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int y = py + ph - 44;
		int x = gridX;
		boolean on = GhostPrefs.showCosmetics;
		boolean hover = mouseX >= x && mouseX < px + pw - 10 && mouseY >= y && mouseY < y + 16;
		GhostUi.roundBox(g, x, y + 3, 18, 10, on ? GhostUi.ACCENT : 0xFF2A2A3A, on ? GhostUi.ACCENT : GhostUi.BORDER);
		g.fill(on ? x + 11 : x + 3, y + 5, on ? x + 16 : x + 8, y + 11, 0xFFFFFFFF);
		g.text(font, Component.translatable("ghost.wardrobe.show_others"), x + 24, y + 4, hover ? GhostUi.TEXT : GhostUi.MUTED, false);
		int ty = y + 20;
		for (FormattedCharSequence l : font.split(Component.translatable("ghost.wardrobe.note"), px + pw - 12 - x)) {
			g.text(font, l, x, ty, 0xFF5E5E72, false);
			ty += 10;
		}
	}

	@Override
	public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() != 0) return false;
		double mx = event.x(), my = event.y();
		int y = py + ph - 44;
		if (mx >= gridX && mx < px + pw - 10 && my >= y && my < y + 16) {
			GhostPrefs.showCosmetics = !GhostPrefs.showCosmetics;
			GhostPrefs.save();
			return true;
		}
		int tw = (gridW - 4 * 4) / 5;
		if (my >= py + 38 && my < py + 38 + TAB_H) for (int i = 0; i < TABS.length; i++) {
			int x = tabX(i);
			if (mx >= x && mx < x + tw && !TABS[i].equals(tab)) {
				tab = TABS[i];
				scroll = 0;
				waiting = false;
				layoutScroll();
				minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
				return true;
			}
		}
		if (mx < gridX || mx >= gridX + gridW || my < gridY || my >= gridY + gridH) return false;
		List<String> options = options();
		for (int i = 0; i < options.size(); i++) {
			int x = gridX + (i % cols) * (CARD_W + GAP);
			int cy = gridY + (i / cols) * (CARD_H + GAP) - (int) scroll;
			if (mx >= x && mx < x + CARD_W && my >= cy && my < cy + CARD_H) {
				String id = options.get(i);
				if (GhostCosmetics.hoursLeft(id) > 0) {
					// Aún bloqueado: no se manda nada al servidor
					minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1f));
					return true;
				}
				if (!java.util.Objects.equals(id, equipped()) && GhostSocial.get().equip(tab, id)) {
					waiting = true;
					waitingFor = id;
					waitingSince = System.currentTimeMillis();
				}
				minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (maxScroll > 0 && mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
			scroll = Math.max(0, Math.min(maxScroll, scroll - vertical * 20));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}
}
