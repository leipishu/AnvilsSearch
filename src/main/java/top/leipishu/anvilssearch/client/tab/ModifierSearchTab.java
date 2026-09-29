package top.leipishu.anvilssearch.client.tab;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import top.leipishu.anvilssearch.client.AnvilPanelAnimation;
import top.leipishu.anvilssearch.client.AnvilSidebarPanel;
import top.leipishu.anvilssearch.client.AnvilTab;
import top.leipishu.anvilssearch.client.theme.AnvilTheme;
import top.leipishu.anvilssearch.data.AnvilSlotAccess;
import top.leipishu.anvilssearch.data.FavoritesStore;
import top.leipishu.anvilssearch.data.ModifierIndex;
import top.leipishu.tinkerssearch.client.gui.components.ScrollBar;
import top.leipishu.tinkerssearch.client.gui.components.SearchBox;
import top.leipishu.tinkerssearch.client.gui.components.SearchBoxStyle;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch.PinyinResult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

/**
 * 强化搜索 Tab（平铺模式）。
 * 每条 {@link ModifierIndex.Entry} 直接对应列表中的一行。
 */
public class ModifierSearchTab implements AnvilTab {

    private static final int ROW_H     = AnvilTheme.ROW_H;
    private static final int LINE_H    = AnvilTheme.LINE_H;
    private static final int CARD_GAP  = AnvilTheme.CARD_GAP;
    private static final int SLOT_SIZE = AnvilTheme.SLOT_SIZE;
    private static final int SLOT_GAP  = AnvilTheme.SLOT_GAP;

    private static final int SEARCH_H = 16;
    private static final int FILTER_H = 16;

    private static final int STAR_W     = 12;
    private static final int STAR_BTN_W = 16;

    private static final int DROP_HEADER_H = 20;
    private static final int DROP_ITEM_H   = 16;
    private static final int DROP_BTN_H    = 14;
    private static final int DROP_BTN_PAD  = 4;

    private static final String SLOT_NONE = "__none__";

    private final AnvilSidebarPanel panel;

    private List<ModifierIndex.Entry> allEntries = new ArrayList<>();
    private List<ModifierIndex.Entry> filtered   = new ArrayList<>();
    private List<String> slotTypes = new ArrayList<>();

    private boolean favoritesOnly = false;
    private final Set<String> slotFilter = new LinkedHashSet<>();
    private boolean slotDropdownOpen = false;
    private int slotDropdownX, slotDropdownY, slotDropdownW, slotDropdownH;

    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private final ScrollBar scrollBar = new ScrollBar();
    private final SearchBox searchBox = new SearchBox(SearchBoxStyle.panel());

    private ModifierIndex.Entry selected = null;

    private int leftListX, leftListTop, leftListW, leftListH;
    private int filterBarY;
    private int rightAreaX, rightAreaY, rightAreaW, rightAreaH;
    private int rightScrollOffset = 0;
    private int rightMaxScroll = 0;

    private ItemStack lastCenterItem = ItemStack.EMPTY;

    public ModifierSearchTab(AnvilSidebarPanel panel) {
        this.panel = panel;
        this.scrollBar.setOnOffsetChanged(v -> scrollOffset = v);
        this.scrollBar.setThumbMinHeight(16);
        this.scrollBar.setHoverExpandX(3);

        this.searchBox.setHintText(new TranslatableComponent(
                "gui.anvilssearch.modifier.search_hint"));
        this.searchBox.setOnTextChanged(this::applyFilter);
    }

    @Override public Component getLabel() {
        return new TranslatableComponent("gui.anvilssearch.tab.modifier");
    }

    @Override public int getPreferredWidth() {
        return AnvilPanelAnimation.WIDTH_NORMAL;
    }

    @Override public boolean wantsSearchBox() { return false; }

    @Override public void onActivate() { reloadIndex(); }

    @Override public void onDeactivate() { FavoritesStore.flush(); }

    @Override public boolean isAnySearchFocused() { return searchBox.isFocused(); }

    @Override public void onExternalSearchFocus() {
        searchBox.setFocused(false);
        slotDropdownOpen = false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (!searchBox.isFocused()) return false;
        if (searchBox.keyPressed(keyCode, scanCode, mods)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            searchBox.setFocused(false);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (!searchBox.isFocused()) return false;
        return searchBox.charTyped(c, mods);
    }

    private void reloadIndex() {
        allEntries = new ArrayList<>(ModifierIndex.get());
        slotTypes = collectSlotTypes();
        System.out.println("[Anvil's Search] ModifierSearchTab reload: "
                + allEntries.size() + " entries, slot types=" + slotTypes);
        applyFilter("");
    }

    private List<String> collectSlotTypes() {
        Set<String> types = new LinkedHashSet<>();
        boolean anyNoSlot = false;

        for (ModifierIndex.Entry e : allEntries) {
            boolean entryHasSlot = false;
            for (ModifierIndex.LevelInfo li : e.levels) {
                for (ModifierIndex.SlotRequirement sr : li.slots) {
                    if (!"none".equals(sr.typeId)) {
                        entryHasSlot = true;
                        types.add(sr.typeId);
                    }
                }
            }
            if (!entryHasSlot) anyNoSlot = true;
        }

        List<String> out = new ArrayList<>();
        if (anyNoSlot) out.add(SLOT_NONE);
        out.addAll(types);
        return out;
    }

    private void applyFilter(String kw) {
        String k = kw == null ? "" : kw.trim().toLowerCase(Locale.ROOT);

        ItemStack center = AnvilSlotAccess.getCenterItem();
        boolean hasItem = center != null && !center.isEmpty();

        List<ModifierIndex.Entry> out = new ArrayList<>();
        for (ModifierIndex.Entry e : allEntries) {
            if (!matches(e, k)) continue;
            if (favoritesOnly && !FavoritesStore.isFavorite(e.id)) continue;
            if (!slotFilter.isEmpty() && !matchesSlotFilter(e)) continue;
            if (hasItem && !canApplyToCurrentItem(e)) continue;
            out.add(e);
        }

        out.sort((a, b) -> {
            boolean fa = FavoritesStore.isFavorite(a.id);
            boolean fb = FavoritesStore.isFavorite(b.id);
            if (fa != fb) return fa ? -1 : 1;
            return 0;
        });

        filtered = out;
        scrollOffset = 0;
        rightScrollOffset = 0;
    }

    private boolean matchesSlotFilter(ModifierIndex.Entry e) {
        boolean entryHasSlot = false;
        for (ModifierIndex.LevelInfo li : e.levels) {
            for (ModifierIndex.SlotRequirement sr : li.slots) {
                if ("none".equals(sr.typeId)) continue;
                entryHasSlot = true;
                if (slotFilter.contains(sr.typeId)) return true;
            }
        }
        if (!entryHasSlot && slotFilter.contains(SLOT_NONE)) return true;
        return false;
    }

    private static String entryName(ModifierIndex.Entry e) {
        if (e.levels.isEmpty()) return e.getDisplayName();
        try {
            return e.levels.get(0).displayName.getString();
        } catch (Throwable t) {
            return e.getDisplayName();
        }
    }

    private static boolean matches(ModifierIndex.Entry e, String k) {
        String name = entryName(e);
        if (name.toLowerCase(Locale.ROOT).contains(k)) return true;
        if (e.registryPath != null && e.registryPath.toLowerCase(Locale.ROOT).contains(k))
            return true;
        try {
            PinyinResult py = PinyinSearch.getPinyin(name);
            if (py.fullPinyin.contains(k) || py.initials.contains(k)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isIncrementalEntry(ModifierIndex.Entry e) {
        return !e.levels.isEmpty()
                && e.levels.get(0).kind == ModifierIndex.RecipeKind.INCREMENTAL;
    }

    private ModifierIndex.LevelInfo getSelectedLevelInfo() {
        if (selected == null || selected.levels.isEmpty()) return null;
        return selected.levels.get(0);
    }

    // ============================================================
    // ===== 颜色工具 ============================================
    // ============================================================

    private static int readableTint(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int lum = (r * 299 + g * 587 + b * 114) / 1000;

        if (lum < 170) {
            float t = (170 - lum) / 170f;
            t = Math.min(t, 0.75f);
            r = (int) (r + (255 - r) * t);
            g = (int) (g + (255 - g) * t);
            b = (int) (b + (255 - b) * t);
        }

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int selectedRowBg(int color) {
        return (color & 0x00FFFFFF) | 0x44000000;
    }

    // ============================================================
    // ===== 渲染 =================================================
    // ============================================================

    @Override
    public void renderContent(PoseStack ps, Font font,
                              int mouseX, int mouseY, float partialTick,
                              int px, int py, int pw, int ph,
                              int contentTop, int contentBottom) {

        ItemStack now = AnvilSlotAccess.getCenterItem();
        if (!sameStack(now, lastCenterItem)) {
            lastCenterItem = now == null ? ItemStack.EMPTY : now.copy();
            applyFilter(panel.getSearchKeyword());
        }

        int contentLeft  = px + 5;
        int contentRight = px + pw - 5;
        int areaTop      = py + contentTop;
        int areaH        = contentBottom - contentTop;

        int totalW = contentRight - contentLeft;
        int leftW  = (int) (totalW * 0.42f);
        int rightW = totalW - leftW - 4;
        int leftX  = contentLeft;
        int rightX = leftX + leftW + 4;

        renderLeft(ps, font, leftX, areaTop, leftW, areaH, mouseX, mouseY);
        renderRight(ps, font, rightX, areaTop, rightW, areaH, mouseX, mouseY);

        if (slotDropdownOpen) renderSlotDropdown(ps, font, mouseX, mouseY);
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        boolean ea = (a == null || a.isEmpty());
        boolean eb = (b == null || b.isEmpty());
        if (ea && eb) return true;
        if (ea || eb) return false;
        return ItemStack.isSameItemSameTags(a, b) && a.getCount() == b.getCount();
    }

    // ============================================================
    // ===== 左栏 =================================================
    // ============================================================

    private void renderLeft(PoseStack ps, Font font,
                            int x, int y, int w, int h,
                            int mouseX, int mouseY) {
        AnvilTheme.section(ps, x, y, w, h);

        int pad = AnvilTheme.PAD_S;
        int innerX = x + pad;
        int innerW = w - pad * 2;

        searchBox.setBounds(innerX, y + pad, innerW, SEARCH_H);
        searchBox.render(ps, mouseX, mouseY, font);

        int filterY = y + pad + SEARCH_H + 3;
        filterBarY = filterY;
        renderFilterBar(ps, font, innerX, filterY, innerW, mouseX, mouseY);

        int listTop = filterY + FILTER_H + 3;
        int listH = (y + h) - listTop - pad;
        int listW = innerW;

        leftListX = innerX;
        leftListTop = listTop;
        leftListW = listW;
        leftListH = listH;

        if (filtered.isEmpty()) {
            font.draw(ps, new TranslatableComponent(
                            "gui.anvilssearch.modifier.empty").getString(),
                    innerX + 2, listTop + 4, AnvilTheme.TEXT_DIM);
            return;
        }

        int totalH = filtered.size() * ROW_H;
        maxScrollOffset = Math.max(0, totalH - listH);
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;

        int scrollBarW = maxScrollOffset > 0 ? SCROLL_BAR_WIDTH + SCROLL_BAR_PADDING : 0;
        int clipW = listW - scrollBarW;

        boolean scissorOk = ScissorHelper.enableScissor(innerX, listTop, clipW, listH);
        try {
            if (scissorOk) RenderSystem.disableDepthTest();

            int rowY = listTop - scrollOffset;
            for (ModifierIndex.Entry e : filtered) {
                drawEntryRow(ps, font, innerX, rowY, clipW, e,
                        selected == e, mouseX, mouseY);
                rowY += ROW_H;
            }
        } finally {
            if (scissorOk) {
                ScissorHelper.disableScissor();
                RenderSystem.enableDepthTest();
            }
        }

        if (maxScrollOffset > 0) {
            scrollBar.setBounds(x + w - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING,
                    listTop, SCROLL_BAR_WIDTH, listH);
            scrollBar.setRange(scrollOffset, maxScrollOffset);
            scrollBar.render(ps, mouseX, mouseY);
        }
    }

    private void renderFilterBar(PoseStack ps, Font font,
                                 int x, int y, int w,
                                 int mouseX, int mouseY) {
        boolean starHover = mouseX >= x && mouseX <= x + STAR_BTN_W
                && mouseY >= y && mouseY <= y + FILTER_H;
        String starLabel = favoritesOnly ? "\u2605" : "\u2606";
        AnvilTheme.button(ps, font, x, y, STAR_BTN_W, FILTER_H,
                starLabel, starHover, favoritesOnly);

        int slotBtnX = x + STAR_BTN_W + 2;
        int slotBtnW = w - STAR_BTN_W - 2;
        if (slotBtnW < 10) return;

        boolean slotHover = mouseX >= slotBtnX && mouseX <= slotBtnX + slotBtnW
                && mouseY >= y && mouseY <= y + FILTER_H;
        boolean slotActive = !slotFilter.isEmpty();

        String label;
        if (slotActive) {
            label = new TranslatableComponent(
                    "gui.anvilssearch.modifier.filter.slots_n",
                    slotFilter.size()).getString();
        } else {
            label = new TranslatableComponent(
                    "gui.anvilssearch.modifier.filter.slots").getString();
        }
        if (font.width(label) > slotBtnW - 6) {
            label = font.plainSubstrByWidth(label, slotBtnW - 10) + "...";
        }
        AnvilTheme.button(ps, font, slotBtnX, y, slotBtnW, FILTER_H,
                label, slotHover, slotActive);
    }

    private void renderSlotDropdown(PoseStack ps, Font font,
                                    int mouseX, int mouseY) {
        if (slotTypes.isEmpty()) return;

        int w = Math.min(120, leftListW);
        int h = DROP_HEADER_H + slotTypes.size() * DROP_ITEM_H + 6;

        int x = leftListX;
        int y = filterBarY + FILTER_H + 2;

        AnvilTheme.cardBg(ps, x, y, w, h, 0);

        int btnX = x + DROP_BTN_PAD;
        int btnY = y + 4;
        int btnW = w - DROP_BTN_PAD * 2;
        int btnH = DROP_BTN_H;

        boolean allSelected = !slotTypes.isEmpty() && slotFilter.containsAll(slotTypes);

        boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW
                && mouseY >= btnY && mouseY <= btnY + btnH;

        String btnLabel = allSelected
                ? new TranslatableComponent(
                "gui.anvilssearch.modifier.filter.clear").getString()
                : new TranslatableComponent(
                "gui.anvilssearch.modifier.filter.select_all").getString();

        AnvilTheme.button(ps, font, btnX, btnY, btnW, btnH,
                btnLabel, btnHover, allSelected);

        GuiComponent.fill(ps, x + 2, y + DROP_HEADER_H - 1,
                x + w - 2, y + DROP_HEADER_H, AnvilTheme.SECTION_BORDER);

        int cy = y + DROP_HEADER_H + 2;
        for (String type : slotTypes) {
            boolean hover = mouseX >= x + 1 && mouseX <= x + w - 1
                    && mouseY >= cy && mouseY <= cy + DROP_ITEM_H;
            if (hover) {
                GuiComponent.fill(ps, x + 1, cy, x + w - 1, cy + DROP_ITEM_H,
                        AnvilTheme.ROW_HOVER);
            }
            boolean checked = slotFilter.contains(type);
            String box = checked ? "\u2611" : "\u2610";
            int textY = AnvilTheme.centeredTextY(cy, DROP_ITEM_H, font);
            font.draw(ps, box, x + 6, textY,
                    checked ? AnvilTheme.ACCENT : AnvilTheme.TEXT_MUTED);

            Component lbl;
            if (SLOT_NONE.equals(type)) {
                lbl = new TranslatableComponent("gui.anvilssearch.slot.none");
            } else {
                lbl = new TranslatableComponent("gui.anvilssearch.slot." + type);
            }
            font.draw(ps, lbl, x + 20, textY,
                    checked ? AnvilTheme.TEXT_PRIMARY : AnvilTheme.TEXT_SECONDARY);

            cy += DROP_ITEM_H;
        }

        slotDropdownX = x;
        slotDropdownY = y;
        slotDropdownW = w;
        slotDropdownH = h;
    }

    // ============================================================
    // ===== 列表行（平铺）========================================
    // ============================================================

    private void drawEntryRow(PoseStack ps, Font font,
                              int x, int y, int clipW, ModifierIndex.Entry e,
                              boolean sel, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + clipW
                && mouseY >= y && mouseY <= y + ROW_H;

        if (sel) {
            GuiComponent.fill(ps, x, y, x + clipW, y + ROW_H,
                    selectedRowBg(e.color));
        } else if (hover) {
            GuiComponent.fill(ps, x, y, x + clipW, y + ROW_H,
                    AnvilTheme.ROW_HOVER);
        }

        int textY = AnvilTheme.centeredTextY(y, ROW_H, font);

        boolean fav = FavoritesStore.isFavorite(e.id);
        String star = fav ? "\u2605" : "\u2606";
        int starX = x + 3;
        boolean starHover = mouseX >= starX - 1 && mouseX <= starX + STAR_W + 1
                && mouseY >= y && mouseY <= y + ROW_H;
        font.draw(ps, star, starX, textY,
                fav ? AnvilTheme.ACCENT
                        : (starHover ? AnvilTheme.TEXT_MUTED : AnvilTheme.TEXT_DIM));

        boolean incremental = isIncrementalEntry(e);
        String right = "";
        int rightW = 0;
        int rightX = -1;
        boolean isIconLightning = false;

        if (incremental && !e.levels.isEmpty()) {
            String icon = "\u26A1";
            right = icon;
            rightW = font.width(icon) + 3;
            rightX = x + clipW - rightW - 2;
            isIconLightning = true;
        }

        int textX = starX + STAR_W + 4;
        int maxNameW = clipW - (textX - x) - 6 - rightW;

        String name = entryName(e);
        String display = font.width(name) > maxNameW
                ? font.plainSubstrByWidth(name, maxNameW - 4) + "..."
                : name;

        font.draw(ps, display, textX, textY,
                sel ? readableTint(e.color)
                        : (hover ? AnvilTheme.TEXT_PRIMARY : e.color));

        if (rightW > 0) {
            font.draw(ps, right, rightX, textY,
                    isIconLightning ? AnvilTheme.ACCENT_SOFT : AnvilTheme.TEXT_MUTED);
        }

        if (isIconLightning && rightX >= 0) {
            int iconW = font.width("\u26A1");
            if (mouseX >= rightX - 2 && mouseX <= rightX + iconW + 2
                    && mouseY >= y && mouseY <= y + ROW_H) {
                ModifierIndex.LevelInfo li = e.levels.get(0);
                List<Component> tip = new ArrayList<>();
                tip.add(new TextComponent("\u00A7e" + new TranslatableComponent(
                        "gui.anvilssearch.modifier.incremental_title").getString()));
                tip.add(new TextComponent("\u00A77" + new TranslatableComponent(
                        "gui.anvilssearch.modifier.incremental_hint",
                        li.amountPerInput, li.neededPerLevel).getString()));
                panel.setPendingTooltip(tip);
            }
        }
    }

    // ============================================================
    // ===== 右栏：矩阵 + 卡片 ====================================
    // ============================================================

    @SuppressWarnings("unchecked")
    private void renderRight(PoseStack ps, Font font,
                             int x, int y, int w, int h,
                             int mouseX, int mouseY) {
        AnvilTheme.section(ps, x, y, w, h);

        int size = SLOT_SIZE;
        int gap  = SLOT_GAP;
        int midX = x + w / 2;

        int row1Y = y + 8;
        int row2Y = row1Y + size + gap + 2;
        int row3Y = row2Y + size + gap + 2;

        ItemStack[] matIcons = new ItemStack[5];
        for (int i = 0; i < 5; i++) matIcons[i] = ItemStack.EMPTY;

        ModifierIndex.LevelInfo selLi = getSelectedLevelInfo();
        if (selLi != null && selLi.slotMaterials != null) {
            for (int i = 0; i < 5; i++) {
                List<ItemStack> sl = selLi.slotMaterials[i];
                if (sl != null && !sl.isEmpty()) {
                    matIcons[i] = sl.get(0);
                }
            }
        } else if (selLi != null) {
            for (int i = 0; i < Math.min(5, selLi.materials.size()); i++) {
                matIcons[i] = selLi.materials.get(i);
            }
        }

        ItemStack centerItem = AnvilSlotAccess.getCenterItem();

        int t1x = midX - size / 2;
        int t2x = midX - size / 2 - size - gap;
        int itemX = midX - size / 2;
        int t3x = midX + size / 2 + gap;
        int bottomTotalW = size * 2 + gap;
        int bottomStartX = midX - bottomTotalW / 2;
        int t4x = bottomStartX;
        int t5x = bottomStartX + size + gap;

        drawSlotBg(ps, t1x, row1Y, mouseX, mouseY);
        drawSlotBg(ps, t2x, row2Y, mouseX, mouseY);
        drawSlotBg(ps, itemX, row2Y, mouseX, mouseY);
        drawSlotBg(ps, t3x, row2Y, mouseX, mouseY);
        drawSlotBg(ps, t4x, row3Y, mouseX, mouseY);
        drawSlotBg(ps, t5x, row3Y, mouseX, mouseY);

        boolean depthWas     = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMaskWas = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            drawItemIcon(ps, matIcons[1], t1x, row1Y);
            drawItemIcon(ps, matIcons[0], t2x, row2Y);
            drawItemIcon(ps, centerItem, itemX, row2Y);
            drawItemIcon(ps, matIcons[2], t3x, row2Y);
            drawItemIcon(ps, matIcons[3], t4x, row3Y);
            drawItemIcon(ps, matIcons[4], t5x, row3Y);
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(depthMaskWas);
            if (depthWas) RenderSystem.enableDepthTest();
            else          RenderSystem.disableDepthTest();
        }

        int infoTop = row3Y + size + 10;
        int infoH = (y + h) - infoTop - AnvilTheme.PAD_S;

        rightAreaX = x + 2;
        rightAreaY = infoTop;
        rightAreaW = w - 4;
        rightAreaH = infoH;

        if (selected == null || selLi == null) {
            font.draw(ps, new TranslatableComponent(
                            "gui.anvilssearch.modifier.pick_hint").getString(),
                    x + AnvilTheme.PAD_M, infoTop + 4, AnvilTheme.TEXT_DIM);
            return;
        }

        int maxTextW = w - AnvilTheme.PAD_L * 2 - 14;
        List<Card> cards = new ArrayList<>();

        String titleText = entryName(selected);
        MutableComponent titleComp = new TextComponent(titleText)
                .withStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(selected.color))
                        .withBold(true));
        int titleW = font.width(titleComp);
        int titleX = x + (w - titleW) / 2;
        int titleY = infoTop + 4;
        font.draw(ps, titleComp, titleX, titleY, selected.color);

        int afterTitleY = titleY + LINE_H + 6;

        List<Component> c2 = new ArrayList<>();
        c2.add(buildSlotLine(selLi));

        if (selLi.requirementsError != null) {
            c2.add(new TextComponent("\u00A7c" + new TranslatableComponent(
                    "gui.anvilssearch.modifier.requirements_error").getString()));
            c2.add(new TextComponent("\u00A77" + selLi.requirementsError));
        }

        if (selLi.kind == ModifierIndex.RecipeKind.INCREMENTAL
                && selLi.amountPerInput > 0 && selLi.neededPerLevel > 0) {
            c2.add(new TextComponent("\u00A7e"
                    + new TranslatableComponent(
                    "gui.anvilssearch.modifier.incremental_summary",
                    selLi.amountPerInput, selLi.neededPerLevel).getString()));
        }

        if (selLi.variant != null) {
            c2.add(new TextComponent("\u00A77"
                    + new TranslatableComponent(
                    "gui.anvilssearch.modifier.variant").getString()
                    + ": " + selLi.variant.getString()));
        }

        if (!selLi.materialLines.isEmpty()) {
            c2.add(new TextComponent(""));
            for (Component line : selLi.materialLines) {
                c2.addAll(wrapComponent(font, line, maxTextW));
            }
        } else {
            c2.add(new TextComponent("\u00A78" + new TranslatableComponent(
                    "gui.anvilssearch.modifier.no_recipe").getString()));
        }
        cards.add(new Card(AnvilTheme.ACCENT_CYAN, null, c2));

        List<Component> c3 = new ArrayList<>();
        List<Component> desc = selected.getDescriptionList(selLi.level);
        if (desc.isEmpty()) {
            c3.add(new TextComponent("\u00A78" + new TranslatableComponent(
                    "gui.anvilssearch.modifier.no_description").getString()));
        } else {
            for (Component d : desc) {
                c3.addAll(wrapComponent(font, d, maxTextW));
            }
        }
        cards.add(new Card(AnvilTheme.ACCENT,
                "\u00A7e" + new TranslatableComponent(
                        "gui.anvilssearch.modifier.description").getString(),
                c3));

        int totalH = 0;
        for (Card card : cards) totalH += measureCard(card);
        totalH += (afterTitleY - infoTop);

        rightMaxScroll = Math.max(0, totalH - infoH);
        if (rightScrollOffset > rightMaxScroll) rightScrollOffset = rightMaxScroll;

        int clipH = infoH - (afterTitleY - infoTop);
        boolean sOk = ScissorHelper.enableScissor(x + 2, afterTitleY, w - 4, clipH);
        try {
            if (sOk) RenderSystem.disableDepthTest();
            int cy = afterTitleY - rightScrollOffset;
            for (Card card : cards) {
                cy = drawCard(ps, font, x + AnvilTheme.PAD_S, cy,
                        w - AnvilTheme.PAD_S * 2, card);
            }
        } finally {
            if (sOk) {
                ScissorHelper.disableScissor();
                RenderSystem.enableDepthTest();
            }
        }
    }

    private static Component buildSlotLine(ModifierIndex.LevelInfo li) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u00A7b")
                .append(new TranslatableComponent(
                        "gui.anvilssearch.modifier.slots_label").getString())
                .append(" \u00A7f");
        boolean hasRealSlot = false;
        for (ModifierIndex.SlotRequirement sr : li.slots) {
            if (!"none".equals(sr.typeId)) { hasRealSlot = true; break; }
        }
        if (!hasRealSlot) {
            sb.append(new TranslatableComponent(
                    "gui.anvilssearch.modifier.slots_none").getString());
        } else {
            boolean first = true;
            for (ModifierIndex.SlotRequirement sr : li.slots) {
                if ("none".equals(sr.typeId)) continue;
                if (!first) sb.append("\u00A77, \u00A7f");
                first = false;
                sb.append(sr.displayName.getString());
                if (sr.count > 1) sb.append(" \u00D7").append(sr.count);
            }
        }
        return new TextComponent(sb.toString());
    }

    private static final class Card {
        final int accent;
        final String title;
        final List<Component> lines;
        Card(int accent, String title, List<Component> lines) {
            this.accent = accent;
            this.title = title;
            this.lines = lines;
        }
    }

    private static int measureCard(Card card) {
        int h = AnvilTheme.CARD_PAD * 2;
        if (card.title != null) h += LINE_H + 4;
        h += card.lines.size() * LINE_H;
        return h + CARD_GAP;
    }

    private static int drawCard(PoseStack ps, Font font,
                                int x, int y, int w, Card card) {
        int cardH = measureCard(card) - CARD_GAP;
        AnvilTheme.cardBg(ps, x, y, w, cardH, card.accent);

        int cy = y + AnvilTheme.CARD_PAD;
        if (card.title != null) {
            font.draw(ps, card.title, x + AnvilTheme.CARD_PAD + 2, cy, card.accent);
            cy += LINE_H + 4;
        }
        for (Component line : card.lines) {
            font.draw(ps, line, x + AnvilTheme.CARD_PAD + 2, cy,
                    AnvilTheme.TEXT_SECONDARY);
            cy += LINE_H;
        }
        return y + cardH + CARD_GAP;
    }

    private static List<Component> wrapComponent(Font font, Component src, int maxW) {
        List<Component> out = new ArrayList<>();
        if (src == null) return out;
        String text = src.getString();
        if (text == null || text.isEmpty()) {
            out.add(new TextComponent(""));
            return out;
        }
        if (font.width(text) <= maxW) {
            out.add(new TextComponent(text));
            return out;
        }
        int start = 0;
        int len = text.length();
        while (start < len) {
            int end = start + 1;
            while (end <= len && font.width(text.substring(start, end)) <= maxW) end++;
            end--;
            if (end <= start) end = start + 1;
            out.add(new TextComponent(text.substring(start, Math.min(end, len))));
            start = Math.min(end, len);
        }
        return out;
    }

    private void drawSlotBg(PoseStack ps, int x, int y, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + SLOT_SIZE
                && mouseY >= y && mouseY <= y + SLOT_SIZE;
        AnvilTheme.slotBg(ps, x, y, SLOT_SIZE, hover);
    }

    private void drawItemIcon(PoseStack ps, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.getItemRenderer().renderGuiItem(stack, x, y);
            mc.getItemRenderer().renderGuiItemDecorations(mc.font, stack, x, y, "");

            if (stack.getCount() > 1) {
                String s = String.valueOf(stack.getCount());
                Font font = mc.font;
                int tw = font.width(s);
                int tx = x + SLOT_SIZE - tw - 1;
                int ty = y + SLOT_SIZE - 9;
                font.drawShadow(ps, s, tx, ty, 0xFFFFFF);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 照搬 JEI：不做工具过滤，列表显示全部强化。
     */
    private boolean canApplyToCurrentItem(ModifierIndex.Entry entry) {
        if (entry == null || entry.levels.isEmpty()) return true;

        ModifierIndex.LevelInfo li = entry.levels.get(0);
        if (li.toolFilter == null) return true;

        ItemStack item = AnvilSlotAccess.getCenterItem();
        if (item == null || item.isEmpty()) return true;

        Boolean r = testIngredient(li.toolFilter, item);
        if (r == null) return true;
        return r;
    }

    private static Boolean testIngredient(Object ingredient, ItemStack stack) {
        if (ingredient == null || stack == null || stack.isEmpty()) return null;

        try {
            Method m = ingredient.getClass().getMethod("test", ItemStack.class);
            m.setAccessible(true);
            Object r = m.invoke(ingredient, stack);
            if (r instanceof Boolean b) return b;
        } catch (Throwable ignored) {}

        try {
            Method m = ingredient.getClass().getMethod("getItems");
            m.setAccessible(true);
            Object v = m.invoke(ingredient);
            if (v instanceof ItemStack[] arr) {
                for (ItemStack s : arr) {
                    if (ItemStack.isSameItemSameTags(s, stack)) return true;
                }
                return false;
            }
            if (v instanceof Collection<?> col) {
                for (Object o : col) {
                    if (o instanceof ItemStack s
                            && ItemStack.isSameItemSameTags(s, stack)) {
                        return true;
                    }
                }
                return false;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    // ============================================================
    // ===== 交互 =================================================
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (slotDropdownOpen) {
            if (mx >= slotDropdownX && mx <= slotDropdownX + slotDropdownW
                    && my >= slotDropdownY && my <= slotDropdownY + slotDropdownH) {

                int btnX = slotDropdownX + DROP_BTN_PAD;
                int btnY = slotDropdownY + 4;
                int btnW = slotDropdownW - DROP_BTN_PAD * 2;
                int btnH = DROP_BTN_H;

                if (my >= btnY && my <= btnY + btnH
                        && mx >= btnX && mx <= btnX + btnW) {
                    boolean allSelected = !slotTypes.isEmpty()
                            && slotFilter.containsAll(slotTypes);
                    if (allSelected) {
                        slotFilter.clear();
                    } else {
                        slotFilter.clear();
                        slotFilter.addAll(slotTypes);
                    }
                    applyFilter(panel.getSearchKeyword());
                    return true;
                }

                if (my >= slotDropdownY + DROP_HEADER_H - 1
                        && my <= slotDropdownY + DROP_HEADER_H + 2) {
                    return true;
                }

                int cy = slotDropdownY + DROP_HEADER_H + 2;
                for (String type : slotTypes) {
                    if (my >= cy && my <= cy + DROP_ITEM_H) {
                        if (!slotFilter.remove(type)) slotFilter.add(type);
                        applyFilter(panel.getSearchKeyword());
                        return true;
                    }
                    cy += DROP_ITEM_H;
                }
                return true;
            } else {
                slotDropdownOpen = false;
            }
        }

        if (scrollBar.tryBeginDrag(mx, my)) return true;

        if (my >= filterBarY && my <= filterBarY + FILTER_H
                && mx >= leftListX && mx <= leftListX + leftListW) {
            if (mx >= leftListX && mx <= leftListX + STAR_BTN_W) {
                favoritesOnly = !favoritesOnly;
                applyFilter(panel.getSearchKeyword());
                return true;
            }
            int slotBtnX = leftListX + STAR_BTN_W + 2;
            if (mx >= slotBtnX && mx <= leftListX + leftListW) {
                slotDropdownOpen = !slotDropdownOpen;
                return true;
            }
            return true;
        }

        if (searchBox.mouseClicked(mx, my, button)) {
            searchBox.setFocused(true);
            return true;
        }
        if (searchBox.isFocused()) searchBox.setFocused(false);

        if (mx >= leftListX && mx <= leftListX + leftListW
                && my >= leftListTop && my <= leftListTop + leftListH) {

            int rowY = leftListTop - scrollOffset;
            for (ModifierIndex.Entry e : filtered) {
                if (my >= rowY && my <= rowY + ROW_H) {
                    int starX = leftListX + 3;
                    if (mx >= starX - 2 && mx <= starX + STAR_W + 2) {
                        FavoritesStore.toggle(e.id);
                        if (favoritesOnly) applyFilter(panel.getSearchKeyword());
                        return true;
                    }
                    selected = e;
                    rightScrollOffset = 0;
                    return true;
                }
                rowY += ROW_H;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= rightAreaX && mx <= rightAreaX + rightAreaW
                && my >= rightAreaY && my <= rightAreaY + rightAreaH) {
            if (rightMaxScroll <= 0) return false;
            int no = rightScrollOffset - (int) (delta * 12);
            no = Math.max(0, Math.min(no, rightMaxScroll));
            if (no != rightScrollOffset) {
                rightScrollOffset = no;
                return true;
            }
            return true;
        }
        if (maxScrollOffset <= 0) return false;
        int no = scrollOffset - (int) (delta * 12);
        no = Math.max(0, Math.min(no, maxScrollOffset));
        if (no != scrollOffset) {
            scrollOffset = no;
            scrollBar.setRange(scrollOffset, maxScrollOffset);
            return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(double mx, double my) { return scrollBar.updateDrag(my); }
    @Override public void mouseReleased() { scrollBar.endDrag(); }
    @Override public boolean isDraggingScrollBar() { return scrollBar.isDragging(); }
}