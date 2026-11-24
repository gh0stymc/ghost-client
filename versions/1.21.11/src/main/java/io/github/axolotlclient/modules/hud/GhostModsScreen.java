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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles;
import io.github.axolotlclient.modules.hud.gui.component.HudEntry;
import io.github.axolotlclient.util.options.ForceableBooleanOption;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

/**
 * ghost client: menú de mods (Right Shift). Una cuadrícula con todos los módulos: cada tarjeta tiene su icono,
 * su nombre, un botón para activarlo o desactivarlo y un engranaje que abre su configuración.
 */
public class GhostModsScreen extends Screen {

	private record Module(OptionCategory category, BooleanOption enabled, String group, Item icon, Component name, Component description, HudEntry hud) {
		boolean hasSettings() {
			return category.getOptions().size() > (enabled == null ? 0 : 1) || !category.getSubCategories().isEmpty();
		}
	}

	private static final String[] GROUPS = {"all", "hud", "visual", "utility", "hypixel"};
	/** Categorías que solo agrupan otras (no son mods en sí). */
	private static final Set<String> CONTAINERS = Set.of("hud", "general", "rendering", "hypixel-mods", "config",
		"storedOptions", "hudEditScreen", "api", "auth");
	/** Módulos sin interruptor propio (funcionan con una tecla), que también se muestran. */
	private static final Set<String> KEY_MODULES = Set.of("zoom", "freelook");

	private static final int SIDE_W = 120, TILE_H = 46, GAP = 6, NAV_H = 18;

	private final Screen parent;
	private final List<Module> modules = new ArrayList<>();
	private List<Module> shown = List.of();
	private String group = "all";
	private EditBox search;
	private double scroll;
	private int maxScroll;
	private int px, py, pw, ph, mainX, mainW, gridY, gridH, cols, tileW;
	private long shownAt = System.currentTimeMillis();
	private Module hovered;
	private int hoveredPart;
	private final java.util.Map<Module, Float> toggleAnim = new java.util.HashMap<>();

	public GhostModsScreen(Screen parent) {
		super(Component.literal("ghost client"));
		this.parent = parent;
		collect();
	}

	// ---------- Módulos ----------

	private void collect() {
		OptionCategory root = AxolotlClient.getInstance().getConfigManager().getRoot();
		for (OptionCategory top : root.getSubCategories()) {
			walk(top, top.getName());
		}
		modules.sort(Comparator.comparing(m -> m.name().getString().toLowerCase(Locale.ROOT)));
		if (System.getProperty("ghostclient.debug.open") != null) {
			modules.forEach(m -> AxolotlClientCommon.getInstance().getLogger().info("[ghost-mod] {} | {} | {}", m.category().getName(), m.group(), m.name().getString()));
		}
	}

	private void walk(OptionCategory cat, String top) {
		String name = cat.getName();
		if (!CONTAINERS.contains(name)) {
			BooleanOption enabled = null;
			for (Option<?> o : cat.getOptions()) {
				if (o instanceof BooleanOption b && "enabled".equals(o.getName())) {
					enabled = b;
				}
			}
			if (enabled != null || KEY_MODULES.contains(name)) {
				modules.add(new Module(cat, enabled, groupOf(top, name), iconOf(name), displayName(name), descriptionOf(name), hudOf(cat)));
			}
		}
		for (OptionCategory sub : cat.getSubCategories()) {
			walk(sub, top);
		}
	}

	/** El elemento del HUD de esta categoría, si se puede mover por la pantalla. */
	private static HudEntry hudOf(OptionCategory cat) {
		for (HudEntry e : HudManager.getInstance().getEntries()) {
			if (e.getCategory() == cat && e.movable()) {
				return e;
			}
		}
		return null;
	}

	/** Botón de mover (arriba a la izquierda de la tarjeta): solo en HUD activados. */
	private static boolean canMove(Module m) {
		return m.hud() != null && m.enabled() != null && m.enabled().get();
	}

	/** Nombre visible: sin el sufijo " HUD" (ya lo dice la pestaña). */
	private static Component displayName(String key) {
		String text = Component.translatable(key).getString().replaceAll("(?i)\\s*HUD$", "").trim();
		return Component.literal(text.isEmpty() ? key : text);
	}

	/** Descripción corta del mod (textos ghost.desc.*), o null si no tiene. */
	private static Component descriptionOf(String key) {
		String k = "ghost.desc." + key;
		return net.minecraft.locale.Language.getInstance().has(k) ? Component.translatable(k) : null;
	}

	private static String groupOf(String top, String name) {
		if (top.equals("hud")) return "hud";
		if (top.equals("hypixel-mods")) return "hypixel";
		if (top.equals("rendering") || Set.of("motionBlur", "particles", "beams", "blockOutlines", "timeChanger").contains(name)) return "visual";
		return "utility";
	}

	private static Item iconOf(String n) {
		n = n.toLowerCase(Locale.ROOT);
		if (n.contains("fps")) return Items.COMPARATOR;
		if (n.contains("cps")) return Items.STONE_BUTTON;
		if (n.contains("keystroke")) return Items.OAK_SIGN;
		if (n.contains("armor")) return Items.DIAMOND_CHESTPLATE;
		if (n.contains("potion")) return Items.POTION;
		if (n.contains("coord") || n.contains("compass")) return Items.COMPASS;
		if (n.contains("ping")) return Items.ENDER_PEARL;
		if (n.contains("irltime") || n.contains("timechanger")) return Items.CLOCK;
		if (n.contains("daycounter")) return Items.SUNFLOWER;
		if (n.contains("memory") || n.contains("debugcounter")) return Items.REPEATER;
		if (n.contains("combo")) return Items.IRON_SWORD;
		if (n.contains("reach")) return Items.LEAD;
		if (n.contains("arrow")) return Items.ARROW;
		if (n.contains("player")) return Items.PLAYER_HEAD;
		if (n.contains("iphud") || n.contains("server")) return Items.BELL;
		if (n.contains("togglesprint")) return Items.FEATHER;
		if (n.contains("crosshair")) return Items.TARGET;
		if (n.contains("scoreboard") || n.contains("pack")) return Items.PAINTING;
		if (n.contains("bossbar")) return Items.DRAGON_HEAD;
		if (n.contains("actionbar") || n.contains("tooltip")) return Items.BOOK;
		if (n.contains("hotbar") || n.contains("inventory")) return Items.CHEST;
		if (n.contains("subtitle")) return Items.JUKEBOX;
		if (n.contains("tab")) return Items.MAP;
		if (n.contains("itemupdate") || n.contains("levelhead")) return Items.EXPERIENCE_BOTTLE;
		if (n.contains("zoom")) return Items.SPYGLASS;
		if (n.contains("freelook")) return Items.ENDER_EYE;
		if (n.contains("motionblur")) return Items.PHANTOM_MEMBRANE;
		if (n.contains("particle")) return Items.BLAZE_POWDER;
		if (n.contains("beam")) return Items.BEACON;
		if (n.contains("outline")) return Items.GLASS;
		if (n.contains("screenshot")) return Items.FILLED_MAP;
		if (n.contains("tnt")) return Items.TNT;
		if (n.contains("nick") || n.contains("nametag")) return Items.NAME_TAG;
		if (n.contains("autogg")) return Items.GOLDEN_APPLE;
		if (n.contains("autotip")) return Items.GOLD_INGOT;
		if (n.contains("boop")) return Items.SLIME_BALL;
		if (n.contains("bedwars")) return Items.FIRE_CHARGE;
		if (n.contains("skyblock")) return Items.GRASS_BLOCK;
		if (n.contains("rpc")) return Items.MUSIC_DISC_CAT;
		if (n.contains("sound")) return Items.NOTE_BLOCK;
		if (n.contains("shield")) return Items.SHIELD;
		return Items.PAPER;
	}

	// ---------- Colores y textos de cada categoría ----------

	private static int colorOf(String group) {
		return switch (group) {
			case "hud" -> 0xFF8B6CFF;
			case "visual" -> 0xFFF472B6;
			case "utility" -> 0xFF22D3EE;
			case "hypixel" -> 0xFFFBBF24;
			default -> GhostUi.ACCENT;
		};
	}

	private static boolean isOn(Module m) {
		return m.enabled() == null || m.enabled().get();
	}

	private static boolean forcedOff(Module m) {
		return m.enabled() instanceof ForceableBooleanOption f && f.isForceOff();
	}

	private long count(String gr, boolean onlyOn) {
		return modules.stream().filter(m -> gr.equals("all") || m.group().equals(gr)).filter(m -> !onlyOn || (m.enabled() != null && m.enabled().get())).count();
	}

	private void filter() {
		String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		shown = modules.stream()
			.filter(m -> group.equals("all") || m.group().equals(group))
			.filter(m -> q.isEmpty() || m.name().getString().toLowerCase(Locale.ROOT).contains(q)
				|| (m.description() != null && m.description().getString().toLowerCase(Locale.ROOT).contains(q)))
			.toList();
		int rows = (shown.size() + cols - 1) / Math.max(cols, 1);
		maxScroll = Math.max(0, rows * (TILE_H + GAP) - GAP - gridH);
		scroll = Math.min(scroll, maxScroll);
		shownAt = System.currentTimeMillis();
	}

	// ---------- Pantalla ----------

	@Override
	protected void init() {
		pw = Math.min(width - 20, 600);
		ph = height - 20;
		px = (width - pw) / 2;
		py = 10;
		mainX = px + SIDE_W + 12;
		mainW = px + pw - 12 - mainX;
		gridY = py + 50;
		gridH = py + ph - 10 - gridY;
		cols = mainW >= 470 ? 3 : mainW >= 300 ? 2 : 1;
		tileW = (mainW - (cols - 1) * GAP) / cols;

		String previous = search == null ? "" : search.getValue();
		search = new EditBox(font, px + 26, py + 46, SIDE_W - 34, 10, Component.translatable("ghost.mods.search"));
		search.setBordered(false);
		search.setMaxLength(32);
		search.setValue(previous);
		search.setResponder(s -> {
			scroll = 0;
			filter();
		});
		addRenderableWidget(search);

		filter();
	}

	@Override
	public void onClose() {
		AxolotlClientCommon.getInstance().saveConfig();
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
		// Sin fondo borroso: dentro de un mundo se sigue viendo el juego tal cual
		if (minecraft.level == null) super.renderBackground(g, mouseX, mouseY, delta);
		else g.fill(0, 0, width, height, 0x50000000);
		// Panel con un brillo suave del color de la categoría arriba
		GhostUi.roundBox(g, px, py, pw, ph, 0xE60E0E16, GhostUi.BORDER);
		int glow = colorOf(group);
		for (int i = 0; i < 6; i++) {
			g.fill(px + SIDE_W + 1, py + 1 + i * 6, px + pw - 1, py + 7 + i * 6, GhostUi.alpha(glow, 16 - i * 3));
		}
		// Barra lateral
		GhostUi.roundRect(g, px + 1, py + 1, SIDE_W, ph - 2, 0xF00A0A11);
		g.fill(px + SIDE_W + 1, py + 8, px + SIDE_W + 2, py + ph - 8, 0xFF1C1C28);
		// Buscador
		boolean focused = search != null && search.isFocused();
		GhostUi.roundBox(g, px + 8, py + 40, SIDE_W - 16, 20, 0xFF14141E, focused ? GhostUi.ACCENT : 0xFF24243A);
		g.blit(RenderPipelines.GUI_TEXTURED, io.github.axolotlclient.ghost.GhostHooks.icon("search"), px + 13, py + 45, 0, 0, 10, 10, 10, 10);
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		// Logo
		g.blit(RenderPipelines.GUI_TEXTURED, (net.minecraft.resources.Identifier) AxolotlClientCommon.BADGE_PATH, px + 10, py + 10, 0, 0, 20, 20, 20, 20);
		g.drawString(font, io.github.axolotlclient.ghost.GhostFont.bold("ghost client"), px + 35, py + 11, GhostUi.TEXT, false);
		g.drawString(font, Component.translatable("ghost.mods.title"), px + 35, py + 21, GhostUi.MUTED, false);
		if (search.getValue().isEmpty() && !search.isFocused()) {
			g.drawString(font, Component.translatable("ghost.mods.search"), search.getX(), search.getY(), 0xFF6A6A80, false);
		}

		drawSidebar(g, mouseX, mouseY);
		drawHeader(g, mouseX, mouseY);

		// Tarjetas (entran deslizándose un poco)
		hovered = null;
		g.enableScissor(mainX - 2, gridY, mainX + mainW + 2, gridY + gridH);
		long t = System.currentTimeMillis() - shownAt;
		for (int i = 0; i < shown.size(); i++) {
			int x = mainX + (i % cols) * (tileW + GAP);
			int y = gridY + (i / cols) * (TILE_H + GAP) - (int) scroll;
			if (y + TILE_H < gridY || y > gridY + gridH) continue;
			float in = Math.max(0f, Math.min(1f, (t - i * 18) / 220f));
			int off = Math.round((1 - in) * (1 - in) * 10);
			drawTile(g, shown.get(i), x, y + off, mouseX, mouseY);
		}
		g.disableScissor();

		if (shown.isEmpty()) {
			int cx = mainX + mainW / 2, cy = gridY + gridH / 2 - 20;
			g.blit(RenderPipelines.GUI_TEXTURED, io.github.axolotlclient.ghost.GhostHooks.icon("search"), cx - 10, cy - 10, 0, 0, 20, 20, 20, 20);
			g.drawCenteredString(font, io.github.axolotlclient.ghost.GhostFont.bold(Component.translatable("ghost.mods.empty")), cx, cy + 18, GhostUi.TEXT);
			g.drawCenteredString(font, Component.translatable("ghost.mods.empty.hint"), cx, cy + 30, GhostUi.MUTED);
		}
		// Barra de desplazamiento
		if (maxScroll > 0) {
			int barH = Math.max(20, gridH * gridH / (gridH + maxScroll));
			int barY = gridY + (int) ((gridH - barH) * (scroll / maxScroll));
			GhostUi.roundRect(g, px + pw - 7, barY, 3, barH, GhostUi.alpha(colorOf(group), 160));
		}

		// Descripción completa del mod bajo el ratón
		if (hovered != null && hoveredPart == 0 && hovered.description() != null) {
			List<net.minecraft.util.FormattedCharSequence> tip = new ArrayList<>();
			tip.add(io.github.axolotlclient.ghost.GhostFont.bold(hovered.name()).getVisualOrderText());
			tip.addAll(font.split(hovered.description().copy().withColor(0xA8A8BC), 190));
			g.setTooltipForNextFrame(tip, mouseX, mouseY);
		} else if (hovered != null && hoveredPart == 2) {
			g.setTooltipForNextFrame(font, Component.translatable("ghost.mods.move"), mouseX, mouseY);
		} else if (hovered != null && hoveredPart == 3) {
			g.setTooltipForNextFrame(font, Component.translatable("ghost.mods.open_settings"), mouseX, mouseY);
		}
	}

	private void drawSidebar(GuiGraphics g, int mouseX, int mouseY) {
		int y = py + 68;
		g.drawString(font, Component.translatable("ghost.mods.categories"), px + 12, y, 0xFF5A5A72, false);
		y += 11;
		for (String gr : GROUPS) {
			boolean sel = gr.equals(group);
			boolean hover = mouseX >= px + 6 && mouseX < px + SIDE_W - 6 && mouseY >= y && mouseY < y + NAV_H;
			int color = colorOf(gr);
			if (sel) {
				GhostUi.roundRect(g, px + 6, y, SIDE_W - 12, NAV_H, GhostUi.alpha(color, 40));
				g.fill(px + 6, y + 4, px + 8, y + NAV_H - 4, color);
			} else if (hover) {
				GhostUi.roundRect(g, px + 6, y, SIDE_W - 12, NAV_H, 0x14FFFFFF);
			}
			dot(g, px + 14, y + (NAV_H - 6) / 2, sel || hover ? color : GhostUi.alpha(color, 150));
			int ty = y + (NAV_H - 8) / 2;
			g.drawString(font, Component.translatable("ghost.mods.group." + gr), px + 26, ty, sel ? GhostUi.TEXT : hover ? 0xFFD4D4E0 : 0xFF9A9AB0, false);
			String n = String.valueOf(count(gr, false));
			g.drawString(font, n, px + SIDE_W - 12 - font.width(n), ty, sel ? GhostUi.alpha(color, 255) : 0xFF5A5A72, false);
			y += NAV_H + 2;
		}

		// Cuántos mods hay activos, con una barra
		long on = count("all", true), total = modules.stream().filter(m -> m.enabled() != null).count();
		int my = py + ph - 26;
		if (my < y + 8) return;
		g.drawString(font, io.github.axolotlclient.ghost.GhostFont.bold(Component.literal(String.valueOf(on))), px + 12, my, GhostUi.TEXT, false);
		g.drawString(font, Component.translatable("ghost.mods.active_of", total), px + 14 + font.width(io.github.axolotlclient.ghost.GhostFont.bold(Component.literal(String.valueOf(on)))), my, GhostUi.MUTED, false);
		int bw = SIDE_W - 24;
		GhostUi.roundRect(g, px + 12, my + 13, bw, 4, 0xFF1E1E2C);
		int fill = total == 0 ? 0 : (int) (bw * on / total);
		if (fill > 2) GhostUi.roundRect(g, px + 12, my + 13, fill, 4, GhostUi.ACCENT);
	}

	private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
		int color = colorOf(group);
		// Título grande de la categoría
		g.pose().pushMatrix();
		g.pose().translate(mainX, py + 12);
		g.pose().scale(1.5f, 1.5f);
		g.drawString(font, io.github.axolotlclient.ghost.GhostFont.bold(Component.translatable("ghost.mods.group." + group)), 0, 0, GhostUi.TEXT, false);
		g.pose().popMatrix();
		g.drawString(font, Component.translatable("ghost.mods.groupdesc." + group), mainX, py + 30, GhostUi.MUTED, false);
		// Número de mods en una pastilla del color de la categoría
		Component c = Component.translatable("ghost.mods.count", shown.size());
		int cw = font.width(c) + 12;
		int titleW = (int) (font.width(io.github.axolotlclient.ghost.GhostFont.bold(Component.translatable("ghost.mods.group." + group))) * 1.5f);
		GhostUi.roundRect(g, mainX + titleW + 8, py + 13, cw, 12, GhostUi.alpha(color, 50));
		g.drawString(font, c, mainX + titleW + 14, py + 15, GhostUi.mix(color, 0xFFFFFFFF, 0.4f), false);
		drawHeaderButtons(g, mouseX, mouseY);
	}

	private static void dot(GuiGraphics g, int x, int y, int color) {
		g.fill(x + 1, y, x + 5, y + 6, color);
		g.fill(x, y + 1, x + 6, y + 5, color);
	}

	/** Botones de la cabecera (de derecha a izquierda): cerrar, ajustes generales y mover HUD. */
	private static final String[] HEADER = {"close", "settings", "move"};

	private int headerX(int i) {
		return px + pw - 26 - i * 20;
	}

	private void drawHeaderButtons(GuiGraphics g, int mouseX, int mouseY) {
		for (int i = 0; i < HEADER.length; i++) {
			int x = headerX(i), y = py + 10;
			boolean hover = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
			int fill = i == 0 ? (hover ? 0xFF3A2030 : 0x18FFFFFF) : (hover ? GhostUi.alpha(GhostUi.ACCENT, 140) : 0x18FFFFFF);
			GhostUi.roundRect(g, x, y, 16, 16, fill);
			int sz = i == 0 ? 8 : 10;
			g.blit(RenderPipelines.GUI_TEXTURED, io.github.axolotlclient.ghost.GhostHooks.icon(HEADER[i]), x + (16 - sz) / 2, y + (16 - sz) / 2, 0, 0, sz, sz, sz, sz);
			if (hover && i > 0) {
				g.setTooltipForNextFrame(font, Component.translatable(i == 1 ? "ghost.mods.settings" : "ghost.mods.edit_hud"), mouseX, mouseY);
			}
		}
	}

	private boolean inGrid(double mx, double my) {
		return mx >= mainX && mx < mainX + mainW && my >= gridY && my < gridY + gridH;
	}

	/** Animación del interruptor de cada mod (0 apagado, 1 encendido). */
	private float anim(Module m) {
		float target = isOn(m) ? 1f : 0f;
		float cur = toggleAnim.getOrDefault(m, target);
		cur += (target - cur) * 0.25f;
		if (Math.abs(target - cur) < 0.01f) cur = target;
		toggleAnim.put(m, cur);
		return cur;
	}

	private void drawTile(GuiGraphics g, Module m, int x, int y, int mouseX, int mouseY) {
		boolean hover = inGrid(mouseX, mouseY) && mouseX >= x && mouseX < x + tileW && mouseY >= y && mouseY < y + TILE_H;
		boolean on = isOn(m);
		int color = colorOf(m.group());
		float a = anim(m);

		// Fondo: si está activado, una franja de su color que se desvanece hacia la derecha
		GhostUi.roundBox(g, x, y, tileW, TILE_H, hover ? 0xF01C1B2A : 0xE0151520, hover ? GhostUi.alpha(color, 170) : 0xFF22222F);
		if (a > 0.01f) {
			int strips = 10;
			for (int s = 0; s < strips; s++) {
				int sx = x + 1 + s * (tileW / 2) / strips;
				g.fill(sx, y + 1, sx + (tileW / 2) / strips + 1, y + TILE_H - 1, GhostUi.alpha(color, Math.round((26 - s * 2.6f) * a)));
			}
			g.fill(x + 1, y + 9, x + 3, y + TILE_H - 9, GhostUi.alpha(color, Math.round(255 * a)));
		}

		// Icono en una pastilla del color de la categoría
		int ix = x + 9, iy = y + 8;
		GhostUi.roundRect(g, ix, iy, 30, 30, GhostUi.alpha(color, on ? 60 : 26));
		GhostUi.roundRect(g, ix + 1, iy + 1, 28, 1, GhostUi.alpha(0xFFFFFFFF, on ? 40 : 14));
		ItemStack stack = stackFor(m.icon());
		if (stack != null) {
			g.renderItem(stack, ix + 7, iy + 7);
		} else {
			String initial = m.name().getString().isEmpty() ? "?" : m.name().getString().substring(0, 1).toUpperCase(Locale.ROOT);
			g.drawCenteredString(font, io.github.axolotlclient.ghost.GhostFont.bold(Component.literal(initial)), ix + 15, iy + 11, GhostUi.TEXT);
		}

		// Nombre y descripción
		int tx = x + 46;
		int textW = tileW - 46 - 34 - (hover ? 30 : 0);
		g.drawString(font, io.github.axolotlclient.ghost.GhostFont.bold(Component.literal(fitBold(m.name().getString(), textW))), tx, y + 11, on ? GhostUi.TEXT : 0xFFB0B0C2, false);
		String desc = m.description() != null ? m.description().getString()
			: m.enabled() == null ? Component.translatable("ghost.mods.key_hint").getString() : "";
		g.drawString(font, fit(desc, textW), tx, y + 24, on ? 0xFF9A9AB2 : 0xFF6E6E84, false);

		// Botones que aparecen al pasar el ratón: mover (HUD) y ajustes
		int bx = x + tileW - 34 - 2;
		if (hover && m.hasSettings()) {
			bx -= 14;
			boolean h = mouseX >= bx && mouseX < bx + 13 && mouseY >= y + 16 && mouseY < y + 29;
			GhostUi.roundRect(g, bx, y + 16, 13, 13, h ? GhostUi.alpha(color, 120) : 0x22FFFFFF);
			g.blit(RenderPipelines.GUI_TEXTURED, io.github.axolotlclient.ghost.GhostHooks.icon("settings"), bx + 2, y + 18, 0, 0, 9, 9, 9, 9);
		}
		if (hover && canMove(m)) {
			bx -= 15;
			boolean h = mouseX >= bx && mouseX < bx + 13 && mouseY >= y + 16 && mouseY < y + 29;
			GhostUi.roundRect(g, bx, y + 16, 13, 13, h ? GhostUi.alpha(color, 120) : 0x22FFFFFF);
			g.blit(RenderPipelines.GUI_TEXTURED, io.github.axolotlclient.ghost.GhostHooks.icon("move"), bx + 2, y + 18, 0, 0, 9, 9, 9, 9);
		}

		// Interruptor
		int sx = x + tileW - 32, sy = y + 17;
		if (m.enabled() == null) {
			// Mods que funcionan con una tecla (zoom, freelook): sin interruptor
			GhostUi.roundRect(g, sx - 2, sy - 1, 26, 14, GhostUi.alpha(color, 40));
			g.blit(RenderPipelines.GUI_TEXTURED, io.github.axolotlclient.ghost.GhostHooks.icon("settings"), sx + 7, sy + 2, 0, 0, 9, 9, 9, 9);
		} else if (forcedOff(m)) {
			GhostUi.roundRect(g, sx, sy, 22, 12, 0xFF1E1E28);
			g.drawCenteredString(font, "✕", sx + 11, sy + 2, 0xFF6E6E84);
		} else {
			boolean sh = hover && mouseX >= sx - 3 && mouseX < sx + 25 && mouseY >= sy - 4 && mouseY < sy + 16;
			GhostUi.toggle(g, sx, sy, a, color, sh);
		}

		if (hover) {
			hovered = m;
			hoveredPart = partAt(m, x, y, mouseX, mouseY);
		}
	}

	/** Qué parte de la tarjeta hay bajo el ratón: 0 cuerpo, 1 interruptor, 2 mover, 3 ajustes. */
	private int partAt(Module m, int x, int y, double mx, double my) {
		int sx = x + tileW - 32, sy = y + 17;
		if (mx >= sx - 3 && mx < sx + 25 && my >= sy - 4 && my < sy + 16) return 1;
		int bx = x + tileW - 34 - 2;
		if (m.hasSettings()) {
			bx -= 14;
			if (mx >= bx && mx < bx + 13 && my >= y + 16 && my < y + 29) return 3;
		}
		if (canMove(m)) {
			bx -= 15;
			if (mx >= bx && mx < bx + 13 && my >= y + 16 && my < y + 29) return 2;
		}
		return 0;
	}

	/** Como fit, pero medido en negrita (los títulos). */
	private String fitBold(String text, int width) {
		java.util.function.ToIntFunction<String> w = str -> font.width(io.github.axolotlclient.ghost.GhostFont.bold(Component.literal(str)));
		if (w.applyAsInt(text) <= width) return text;
		String cut = text;
		while (!cut.isEmpty() && w.applyAsInt(cut + "…") > width) cut = cut.substring(0, cut.length() - 1);
		return cut.trim() + "…";
	}

	private String fit(String text, int width) {
		if (font.width(text) <= width) return text;
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))).trim() + "…";
	}

	private final java.util.Map<Item, ItemStack> stacks = new java.util.HashMap<>();
	private boolean itemsReady = true;

	private ItemStack stackFor(Item item) {
		if (!itemsReady) return null;
		try {
			return stacks.computeIfAbsent(item, ItemStack::new);
		} catch (RuntimeException e) {
			itemsReady = false;
			return null;
		}
	}

	private void click() {
		minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	@Override
	public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		double mx = event.x(), my = event.y();
		if (event.button() != 0) {
			return false;
		}
		// Cabecera: cerrar, ajustes y mover HUD
		for (int i = 0; i < HEADER.length; i++) {
			int x = headerX(i);
			if (mx >= x && mx < x + 16 && my >= py + 10 && my < py + 26) {
				click();
				switch (i) {
					case 0 -> onClose();
					case 1 -> minecraft.setScreen(ConfigStyles.createScreen(this, AxolotlClient.getInstance().getConfigManager().getRoot()));
					default -> minecraft.setScreen(new HudEditScreen(this));
				}
				return true;
			}
		}
		// Categorías
		int y = py + 79;
		for (String gr : GROUPS) {
			if (mx >= px + 6 && mx < px + SIDE_W - 6 && my >= y && my < y + NAV_H) {
				if (!gr.equals(group)) {
					group = gr;
					scroll = 0;
					filter();
					click();
				}
				return true;
			}
			y += NAV_H + 2;
		}
		// Tarjetas
		if (!inGrid(mx, my)) {
			return false;
		}
		for (int i = 0; i < shown.size(); i++) {
			int x = mainX + (i % cols) * (tileW + GAP);
			int ty = gridY + (i / cols) * (TILE_H + GAP) - (int) scroll;
			if (mx < x || mx >= x + tileW || my < ty || my >= ty + TILE_H) continue;
			Module m = shown.get(i);
			int part = partAt(m, x, ty, mx, my);
			if (part == 2) {
				minecraft.setScreen(new HudEditScreen(this, m.hud()));
			} else if (part == 3 || (m.enabled() == null) || (part == 0 && m.hasSettings())) {
				minecraft.setScreen(ConfigStyles.createScreen(this, m.category()));
			} else if (!forcedOff(m)) {
				m.enabled().set(!m.enabled().get());
				AxolotlClientCommon.getInstance().saveConfig();
				click();
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (inGrid(mouseX, mouseY) && maxScroll > 0) {
			scroll = Math.max(0, Math.min(maxScroll, scroll - vertical * 24));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}
}
