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
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

	private static final int CARD_W = 80, CARD_H = 104, GAP = 6;
	private static final int PANEL_BG = 0xD010101A;
	private static final int CARD_BG = 0xB41A1A26;
	private static final int OFF_BG = 0xFF2A1C26, OFF_BORDER = 0xFF4A2A38, OFF_TEXT = 0xFFFF8A9A;

	private final Screen parent;
	private final List<Module> modules = new ArrayList<>();
	private List<Module> shown = List.of();
	private String group = "all";
	private EditBox search;
	private double scroll;
	private int maxScroll;
	private int px, py, pw, ph, gridX, gridY, gridW, gridH, cols, tabsY;

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

	/** Parte un texto en líneas que caben en el ancho dado; la última acaba en "…" si no cabe todo. */
	private List<String> wrap(String text, int maxWidth, int maxLines) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String attempt = line.isEmpty() ? word : line + " " + word;
			if (font.width(attempt) <= maxWidth) {
				line = new StringBuilder(attempt);
			} else {
				if (!line.isEmpty()) lines.add(line.toString());
				line = new StringBuilder(word);
			}
		}
		if (!line.isEmpty()) lines.add(line.toString());
		if (lines.size() > maxLines) {
			lines = new ArrayList<>(lines.subList(0, maxLines));
			String last = lines.get(maxLines - 1);
			while (!last.isEmpty() && font.width(last + "…") > maxWidth) last = last.substring(0, last.length() - 1);
			lines.set(maxLines - 1, last.trim() + "…");
		}
		return lines;
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

	private void filter() {
		String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		shown = modules.stream()
			.filter(m -> group.equals("all") || m.group().equals(group))
			.filter(m -> q.isEmpty() || m.name().getString().toLowerCase(Locale.ROOT).contains(q))
			.toList();
		int rows = (shown.size() + cols - 1) / Math.max(cols, 1);
		maxScroll = Math.max(0, rows * (CARD_H + GAP) - GAP - gridH);
		scroll = Math.min(scroll, maxScroll);
	}

	// ---------- Pantalla ----------

	@Override
	protected void init() {
		pw = Math.min(width - 24, 6 * (CARD_W + GAP) + GAP + 16);
		ph = height - 24;
		px = (width - pw) / 2;
		py = 12;
		tabsY = py + 40;
		gridY = tabsY + 22;
		gridH = py + ph - 36 - gridY;
		cols = Math.max(1, (pw - 16 + GAP) / (CARD_W + GAP));
		gridW = cols * (CARD_W + GAP) - GAP;
		gridX = px + (pw - gridW) / 2;

		String previous = search == null ? "" : search.getValue();
		search = new EditBox(font, px + pw - 152, py + 16, 136, 10, Component.translatable("ghost.mods.search"));
		search.setBordered(false);
		search.setMaxLength(32);
		search.setValue(previous);
		search.setResponder(s -> {
			scroll = 0;
			filter();
		});
		addRenderableWidget(search);

		int by = py + ph - 28;
		addRenderableWidget(new GhostUi.GhostButton(px + 12, by, 110, 20, Component.translatable("ghost.mods.edit_hud"),
			_ -> minecraft.gui.setScreen(new HudEditScreen(this)), true));
		addRenderableWidget(new GhostUi.GhostButton(px + 128, by, 110, 20, Component.translatable("ghost.mods.settings"),
			_ -> minecraft.gui.setScreen(ConfigStyles.createScreen(this, AxolotlClient.getInstance().getConfigManager().getRoot())), false));
		addRenderableWidget(new GhostUi.GhostButton(px + pw - 82, by, 70, 20, Component.translatable("close"),
			_ -> onClose(), false));
		filter();
	}

	@Override
	public void onClose() {
		AxolotlClientCommon.getInstance().saveConfig();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractBackground(@NotNull GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// Sin fondo borroso: dentro de un mundo se sigue viendo el juego tal cual
		if (minecraft.level == null) super.extractBackground(g, mouseX, mouseY, delta);
		GhostUi.roundBox(g, px, py, pw, ph, PANEL_BG, GhostUi.BORDER);
		g.fill(px, py + 12, px + 2, py + 32, GhostUi.ACCENT);
		GhostUi.header(g, px + 12, py + 9, Component.translatable("ghost.mods.title"));
		// Caja del buscador
		GhostUi.roundBox(g, px + pw - 158, py + 9, 146, 22, 0xC80C0C14, search != null && search.isFocused() ? GhostUi.ACCENT : GhostUi.BORDER);
	}

	@Override
	public void extractRenderState(@NotNull GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		if (search.getValue().isEmpty() && !search.isFocused()) {
			g.text(font, Component.translatable("ghost.mods.search"), search.getX(), search.getY(), GhostUi.MUTED, false);
		}

		// Pestañas de categorías
		int tx = px + 12;
		for (String gr : GROUPS) {
			Component label = Component.translatable("ghost.mods.group." + gr);
			int tw = font.width(label) + 16;
			boolean sel = gr.equals(group);
			boolean hover = mouseX >= tx && mouseX < tx + tw && mouseY >= tabsY && mouseY < tabsY + 16;
			GhostUi.roundBox(g, tx, tabsY, tw, 16, sel ? 0xFF2A2440 : (hover ? 0xC8202030 : 0x00000000), sel ? GhostUi.ACCENT : (hover ? GhostUi.BORDER : 0x00000000));
			g.text(font, label, tx + 8, tabsY + 4, sel ? GhostUi.TEXT : GhostUi.MUTED, false);
			tx += tw + 4;
		}
		g.text(font, Component.translatable("ghost.mods.count", shown.size()), px + pw - 12 - font.width(Component.translatable("ghost.mods.count", shown.size())), tabsY + 4, GhostUi.MUTED, false);

		// Tarjetas
		g.enableScissor(gridX - 2, gridY, gridX + gridW + 2, gridY + gridH);
		for (int i = 0; i < shown.size(); i++) {
			int cx = gridX + (i % cols) * (CARD_W + GAP);
			int cy = gridY + (i / cols) * (CARD_H + GAP) - (int) scroll;
			if (cy + CARD_H < gridY || cy > gridY + gridH) continue;
			drawCard(g, shown.get(i), cx, cy, mouseX, mouseY);
		}
		g.disableScissor();

		// Descripción completa del mod bajo el ratón
		if (inGrid(mouseX, mouseY)) {
			for (int i = 0; i < shown.size(); i++) {
				int cx = gridX + (i % cols) * (CARD_W + GAP);
				int cy = gridY + (i / cols) * (CARD_H + GAP) - (int) scroll;
				Module m = shown.get(i);
				if (canMove(m) && mouseX >= cx && mouseX < cx + 18 && mouseY >= cy && mouseY < cy + 18) {
					g.setTooltipForNextFrame(font, Component.translatable("ghost.mods.move"), mouseX, mouseY);
					continue;
				}
				if (m.description() != null && mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H - 24) {
					List<net.minecraft.util.FormattedCharSequence> tip = new ArrayList<>();
					tip.add(m.name().getVisualOrderText());
					tip.addAll(font.split(m.description().copy().withColor(0xA8A8BC), 170));
					g.setTooltipForNextFrame(tip, mouseX, mouseY);
				}
			}
		}

		if (shown.isEmpty()) {
			g.centeredText(font, Component.translatable("ghost.mods.empty"), px + pw / 2, gridY + gridH / 2 - 4, GhostUi.MUTED);
		}
		// Barra de desplazamiento
		if (maxScroll > 0) {
			int barH = Math.max(20, gridH * gridH / (gridH + maxScroll));
			int barY = gridY + (int) ((gridH - barH) * (scroll / maxScroll));
			g.fill(px + pw - 6, barY, px + pw - 4, barY + barH, 0x80FFFFFF);
		}
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

	private boolean inGrid(double mx, double my) {
		return mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH;
	}

	private void drawCard(GuiGraphicsExtractor g, Module m, int x, int y, int mouseX, int mouseY) {
		boolean hover = inGrid(mouseX, mouseY) && mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
		boolean on = m.enabled() == null || m.enabled().get();
		GhostUi.roundBox(g, x, y, CARD_W, CARD_H, hover ? 0xD0242036 : CARD_BG, hover ? GhostUi.ACCENT : GhostUi.BORDER);

		// Icono: un objeto de Minecraft a doble tamaño. Fuera de un mundo los objetos aún no están listos
		// en 26.x, así que entonces se muestra la inicial del mod.
		ItemStack stack = stackFor(m.icon());
		if (stack != null) {
			g.pose().pushMatrix();
			g.pose().translate(x + CARD_W / 2f - 16, y + 10);
			g.pose().scale(2f, 2f);
			g.item(stack, 0, 0);
			g.pose().popMatrix();
		} else {
			GhostUi.roundBox(g, x + CARD_W / 2 - 16, y + 10, 32, 32, 0xFF2A2440, GhostUi.ACCENT);
			String initial = m.name().getString().isEmpty() ? "?" : m.name().getString().substring(0, 1).toUpperCase(Locale.ROOT);
			g.pose().pushMatrix();
			g.pose().translate(x + CARD_W / 2f, y + 18);
			g.pose().scale(2f, 2f);
			g.centeredText(font, initial, 0, 0, GhostUi.TEXT);
			g.pose().popMatrix();
		}

		// Nombre (recortado si no cabe)
		String name = m.name().getString();
		if (font.width(name) > CARD_W - 10) {
			name = font.plainSubstrByWidth(name, CARD_W - 16) + "…";
		}
		g.centeredText(font, name, x + CARD_W / 2, y + 46, on ? GhostUi.TEXT : GhostUi.MUTED);

		// Descripción corta (letra pequeña, hasta 3 líneas)
		if (m.description() != null) {
			List<String> lines = wrap(m.description().getString(), (int) ((CARD_W - 8) / 0.7f), 3);
			g.pose().pushMatrix();
			g.pose().translate(x + CARD_W / 2f, y + 57);
			g.pose().scale(0.7f, 0.7f);
			for (int l = 0; l < lines.size(); l++) {
				g.centeredText(font, lines.get(l), 0, l * 10, 0xFFA8A8BC);
			}
			g.pose().popMatrix();
		}

		// Botón de mover por la pantalla
		if (canMove(m)) {
			boolean moveHover = hover && mouseX < x + 18 && mouseY < y + 18;
			GhostUi.roundBox(g, x + 4, y + 4, 13, 13, moveHover ? 0xFF2A2440 : 0x00000000, moveHover ? GhostUi.ACCENT : 0x00000000);
			g.centeredText(font, "✥", x + 11, y + 6, moveHover ? GhostUi.TEXT : GhostUi.MUTED);
		}

		// Engranaje de ajustes
		if (m.hasSettings()) {
			boolean gearHover = hover && mouseX >= x + CARD_W - 18 && mouseY < y + 18;
			GhostUi.roundBox(g, x + CARD_W - 17, y + 4, 13, 13, gearHover ? 0xFF2A2440 : 0x00000000, gearHover ? GhostUi.ACCENT : 0x00000000);
			g.centeredText(font, "⚙", x + CARD_W - 10, y + 6, gearHover ? GhostUi.TEXT : GhostUi.MUTED);
		}

		// Botón de activar / desactivar
		int bx = x + 5, by = y + CARD_H - 21, bw = CARD_W - 10, bh = 16;
		boolean barHover = hover && mouseY >= by;
		Component label;
		int fill, border, text;
		if (m.enabled() == null) {
			label = Component.translatable("ghost.mods.configure");
			fill = barHover ? 0xFF262238 : 0xFF1E1E2C;
			border = GhostUi.BORDER;
			text = GhostUi.TEXT;
		} else if (m.enabled() instanceof ForceableBooleanOption f && f.isForceOff()) {
			label = Component.translatable("ghost.mods.unavailable");
			fill = 0xFF1A1A22;
			border = GhostUi.BORDER;
			text = GhostUi.MUTED;
		} else if (on) {
			label = Component.translatable("ghost.mods.on");
			fill = barHover ? GhostUi.ACCENT_HOVER : GhostUi.ACCENT;
			border = fill;
			text = GhostUi.TEXT;
		} else {
			label = Component.translatable("ghost.mods.off");
			fill = barHover ? 0xFF35202C : OFF_BG;
			border = OFF_BORDER;
			text = OFF_TEXT;
		}
		GhostUi.roundBox(g, bx, by, bw, bh, fill, border);
		g.centeredText(font, label, x + CARD_W / 2, by + 4, text);
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
		// Pestañas
		int tx = px + 12;
		for (String gr : GROUPS) {
			int tw = font.width(Component.translatable("ghost.mods.group." + gr)) + 16;
			if (mx >= tx && mx < tx + tw && my >= tabsY && my < tabsY + 16) {
				group = gr;
				scroll = 0;
				filter();
				return true;
			}
			tx += tw + 4;
		}
		// Tarjetas
		if (!inGrid(mx, my)) {
			return false;
		}
		for (int i = 0; i < shown.size(); i++) {
			int x = gridX + (i % cols) * (CARD_W + GAP);
			int y = gridY + (i / cols) * (CARD_H + GAP) - (int) scroll;
			if (mx < x || mx >= x + CARD_W || my < y || my >= y + CARD_H) continue;
			Module m = shown.get(i);
			boolean onBar = my >= y + CARD_H - 21;
			if (canMove(m) && mx < x + 18 && my < y + 18) {
				minecraft.gui.setScreen(new HudEditScreen(this, m.hud()));
				return true;
			}
			boolean forcedOff = m.enabled() instanceof ForceableBooleanOption f && f.isForceOff();
			if (onBar && m.enabled() != null && !forcedOff) {
				m.enabled().set(!m.enabled().get());
				AxolotlClientCommon.getInstance().saveConfig();
			} else if (m.hasSettings() || m.enabled() == null) {
				minecraft.gui.setScreen(ConfigStyles.createScreen(this, m.category()));
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
