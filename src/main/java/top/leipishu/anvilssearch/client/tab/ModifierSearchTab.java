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
import top.leipishu.anvilssearch.data.AnvilSlotAccess;
import top.leipishu.anvilssearch.data.FavoritesStore;
import top.leipishu.anvilssearch.data.ModifierIndex;
import top.leipishu.tinkerssearch.client.gui.components.CardBackground;
import top.leipishu.tinkerssearch.client.gui.components.ScrollBar;
import top.leipishu.tinkerssearch.client.gui.components.SearchBox;
import top.leipishu.tinkerssearch.client.gui.components.SearchBoxStyle;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch.PinyinResult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

public class ModifierSearchTab implements AnvilTab {

    // ===== 行高（★ 精致化：提高）=====
    private static final int ROW_H     = 16;
    private static final int SUB_ROW_H = 14;
    private static final int SEARCH_H  = 16;
    private static final int FILTER_H  = 14;

    // ===== 配方矩阵 =====
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_GAP  = 2;

    // ===== 卡片（★ 精致化：更高、更松）=====
    private static final int LINE_H     = 12;
    private static final int CARD_GAP   = 6;
    private static final int CARD_PAD_X = 8;
    private static final int CARD_PAD_Y = 8;

    // ===== 星标 =====
    private static final int STAR_W      = 12;
    private static final int STAR_BTN_W  = 14;

    private static final int ACCENT_GOLD = 0xFFFFAA00;

    private final AnvilSidebarPanel panel;

    private List<ModifierIndex.Entry> allEntries = new ArrayList<>();
    private List<ModifierIndex.Entry> filtered   = new ArrayList<>();
    private List<String> slotTypes = new ArrayList<>();

    // ===== 筛选状态 =====
    private boolean favoritesOnly = false;
    private final Set<String> slotFilter = new LinkedHashSet<>();
    private boolean slotDropdownOpen = false;
    private int slotDropdownX, slotDropdownY, slotDropdownW, slotDropdownH;

    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private final ScrollBar scrollBar = new ScrollBar();
    private final SearchBox searchBox = new SearchBox(SearchBoxStyle.panel());

    private ModifierIndex.Entry selected = null;
    private int selectedLevel = 0;

    private final Set<String> expandedInList = new HashSet<>();

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
        for (ModifierIndex.Entry e : allEntries) {
            for (ModifierIndex.LevelInfo li : e.levels) {
                for (ModifierIndex.SlotRequirement sr : li.slots) {
                    types.add(sr.typeId);
                }
            }
        }
        return new ArrayList<>(types);
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

        // 收藏置顶（stable）
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
        for (ModifierIndex.LevelInfo li : e.levels) {
            for (ModifierIndex.SlotRequirement sr : li.slots) {
                if (slotFilter.contains(sr.typeId)) return true;
            }
        }
        return false;
    }

    private static boolean matches(ModifierIndex.Entry e, String k) {
        String name = e.getDisplayName();
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
        if (e.levels.isEmpty()) return false;
        for (ModifierIndex.LevelInfo li : e.levels) {
            if (li.kind != ModifierIndex.RecipeKind.INCREMENTAL) return false;
        }
        return true;
    }

    private ModifierIndex.LevelInfo getSelectedLevelInfo() {
        if (selected == null || selected.levels.isEmpty()) return null;
        if (selectedLevel > 0) {
            for (ModifierIndex.LevelInfo li : selected.levels) {
                if (li.level == selectedLevel) return li;
            }
        }
        return selected.levels.get(0);
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

        // 浮层最后画（在上层）
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
        GuiComponent.fill(ps, x, y, x + w, y + h, 0xFF181818);
        GuiComponent.fill(ps, x, y, x + w, y + 1, 0xFF333333);

        // 搜索框
        searchBox.setBounds(x + 3, y + 3, w - 6, SEARCH_H);
        searchBox.render(ps, mouseX, mouseY, font);

        // 筛选栏
        int filterY = y + 3 + SEARCH_H + 3;
        filterBarY = filterY;
        renderFilterBar(ps, font, x + 3, filterY, w - 6, mouseX, mouseY);

        // 列表
        int listTop = filterY + FILTER_H + 3;
        int listH = h - (listTop - y) - 2;
        int listW = w - 4;

        leftListX = x + 2;
        leftListTop = listTop;
        leftListW = listW;
        leftListH = listH;

        if (filtered.isEmpty()) {
            font.draw(ps, "\u00A77" + new TranslatableComponent(
                            "gui.anvilssearch.modifier.empty").getString(),
                    x + 4, listTop + 4, 0x666666);
            return;
        }

        int totalH = 0;
        for (ModifierIndex.Entry e : filtered) {
            totalH += ROW_H;
            boolean inc = isIncrementalEntry(e);
            if (!inc && e.levels.size() > 1 && expandedInList.contains(e.id)) {
                totalH += e.levels.size() * SUB_ROW_H;
            }
        }
        maxScrollOffset = Math.max(0, totalH - listH);
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;

        int scrollBarW = maxScrollOffset > 0 ? SCROLL_BAR_WIDTH + SCROLL_BAR_PADDING : 0;
        int clipW = listW - scrollBarW;

        boolean scissorOk = ScissorHelper.enableScissor(x + 2, listTop, clipW, listH);
        try {
            if (scissorOk) RenderSystem.disableDepthTest();

            int rowY = listTop - scrollOffset;
            for (ModifierIndex.Entry e : filtered) {
                boolean inc = isIncrementalEntry(e);
                boolean multi = !inc && e.levels.size() > 1;
                boolean expanded = multi && expandedInList.contains(e.id);

                drawEntryRow(ps, font, x + 2, rowY, clipW, e,
                        multi, expanded, inc,
                        selected == e && selectedLevel == 0,
                        mouseX, mouseY);
                rowY += ROW_H;

                if (expanded) {
                    for (ModifierIndex.LevelInfo li : e.levels) {
                        drawSubRow(ps, font, x + 2, rowY, clipW, e, li,
                                selected == e && selectedLevel == li.level,
                                mouseX, mouseY);
                        rowY += SUB_ROW_H;
                    }
                }
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
        // ★ 收藏开关
        boolean starHover = mouseX >= x && mouseX <= x + STAR_BTN_W
                && mouseY >= y && mouseY <= y + FILTER_H;
        int starBg = favoritesOnly ? 0xFF6A5030
                : (starHover ? 0xFF3A3020 : 0xFF2A2A2A);
        GuiComponent.fill(ps, x, y, x + STAR_BTN_W, y + FILTER_H, starBg);
        GuiComponent.fill(ps, x, y, x + STAR_BTN_W, y + 1, 0xFF555555);
        GuiComponent.fill(ps, x, y + FILTER_H - 1, x + STAR_BTN_W, y + FILTER_H, 0xFF555555);
        GuiComponent.fill(ps, x, y, x + 1, y + FILTER_H, 0xFF555555);
        GuiComponent.fill(ps, x + STAR_BTN_W - 1, y, x + STAR_BTN_W, y + FILTER_H, 0xFF555555);
        if (favoritesOnly) {
            GuiComponent.fill(ps, x + 1, y + FILTER_H - 2,
                    x + STAR_BTN_W - 1, y + FILTER_H - 1, ACCENT_GOLD);
        }

        String star = "\u2605";
        int starTextW = font.width(star);
        font.draw(ps, star,
                x + (STAR_BTN_W - starTextW) / 2,
                y + (FILTER_H - font.lineHeight) / 2 + 1,
                favoritesOnly ? 0xFFFFDD77
                        : (starHover ? 0xFFAAAAAA : 0xFF666666));

        // ★ 槽位按钮
        int slotBtnX = x + STAR_BTN_W + 2;
        int slotBtnW = w - STAR_BTN_W - 2;
        if (slotBtnW < 10) return;

        boolean slotHover = mouseX >= slotBtnX && mouseX <= slotBtnX + slotBtnW
                && mouseY >= y && mouseY <= y + FILTER_H;
        boolean slotActive = !slotFilter.isEmpty();
        int slotBg = slotActive ? 0xFF6A5030
                : (slotHover ? 0xFF3A3020 : 0xFF2A2A2A);
        GuiComponent.fill(ps, slotBtnX, y, slotBtnX + slotBtnW, y + FILTER_H, slotBg);
        GuiComponent.fill(ps, slotBtnX, y, slotBtnX + slotBtnW, y + 1, 0xFF555555);
        GuiComponent.fill(ps, slotBtnX, y + FILTER_H - 1,
                slotBtnX + slotBtnW, y + FILTER_H, 0xFF555555);
        GuiComponent.fill(ps, slotBtnX, y, slotBtnX + 1, y + FILTER_H, 0xFF555555);
        GuiComponent.fill(ps, slotBtnX + slotBtnW - 1, y,
                slotBtnX + slotBtnW, y + FILTER_H, 0xFF555555);
        if (slotActive) {
            GuiComponent.fill(ps, slotBtnX + 1, y + FILTER_H - 2,
                    slotBtnX + slotBtnW - 1, y + FILTER_H - 1, ACCENT_GOLD);
        }

        String label;
        if (slotActive) {
            label = new TranslatableComponent(
                    "gui.anvilssearch.modifier.filter.slots_n",
                    slotFilter.size()).getString();
        } else {
            label = new TranslatableComponent(
                    "gui.anvilssearch.modifier.filter.slots").getString();
        }
        int labelW = font.width(label);
        if (labelW > slotBtnW - 6) {
            label = font.plainSubstrByWidth(label, slotBtnW - 10) + "...";
            labelW = font.width(label);
        }
        font.draw(ps, label,
                slotBtnX + (slotBtnW - labelW) / 2,
                y + (FILTER_H - font.lineHeight) / 2 + 1,
                slotActive ? 0xFFFFDD77
                        : (slotHover ? 0xFFFFFFFF : 0xFFAAAAAA));
    }

    private void renderSlotDropdown(PoseStack ps, Font font,
                                    int mouseX, int mouseY) {
        if (slotTypes.isEmpty()) return;

        int itemH = 14;
        int w = Math.min(96, leftListW);
        int h = slotTypes.size() * itemH + 6;

        int x = leftListX;
        int y = filterBarY + FILTER_H + 2;

        GuiComponent.fill(ps, x, y, x + w, y + h, 0xFF1E1E1E);
        GuiComponent.fill(ps, x, y, x + w, y + 1, 0xFF666666);
        GuiComponent.fill(ps, x, y + h - 1, x + w, y + h, 0xFF666666);
        GuiComponent.fill(ps, x, y, x + 1, y + h, 0xFF666666);
        GuiComponent.fill(ps, x + w - 1, y, x + w, y + h, 0xFF666666);

        int cy = y + 3;
        for (String type : slotTypes) {
            boolean hover = mouseX >= x + 1 && mouseX <= x + w - 1
                    && mouseY >= cy && mouseY <= cy + itemH;
            if (hover) {
                GuiComponent.fill(ps, x + 1, cy, x + w - 1, cy + itemH, 0x22FFFFFF);
            }
            boolean checked = slotFilter.contains(type);
            String box = checked ? "\u2611" : "\u2610";
            font.draw(ps, box, x + 4,
                    cy + (itemH - font.lineHeight) / 2 + 1,
                    checked ? ACCENT_GOLD : 0xFF888888);

            Component label = new TranslatableComponent(
                    "gui.anvilssearch.slot." + type);
            font.draw(ps, label, x + 18,
                    cy + (itemH - font.lineHeight) / 2 + 1,
                    checked ? 0xFFFFFFFF : 0xFFAAAAAA);

            cy += itemH;
        }

        slotDropdownX = x;
        slotDropdownY = y;
        slotDropdownW = w;
        slotDropdownH = h;
    }

    // ============================================================
    // ===== 列表行渲染（★ 精致化：星标 + 垂直居中）=====
    // ============================================================

    private void drawEntryRow(PoseStack ps, Font font,
                              int x, int y, int clipW, ModifierIndex.Entry e,
                              boolean multi, boolean expanded, boolean incremental,
                              boolean sel, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + clipW
                && mouseY >= y && mouseY <= y + ROW_H;

        if (sel || hover) {
            GuiComponent.fill(ps, x, y, x + clipW, y + ROW_H,
                    sel ? 0x44FFAA00 : 0x22FFFFFF);
        }

        int textY = y + (ROW_H - font.lineHeight) / 2 + 1;

        // ★ 星标
        boolean fav = FavoritesStore.isFavorite(e.id);
        String star = fav ? "\u2605" : "\u2606";
        int starX = x + 3;
        boolean starHover = mouseX >= starX - 1 && mouseX <= starX + STAR_W + 1
                && mouseY >= y && mouseY <= y + ROW_H;
        font.draw(ps, star, starX, textY,
                fav ? ACCENT_GOLD
                        : (starHover ? 0xFFAAAAAA : 0xFF666666));

        // 右侧标记
        String right = "";
        int rightW = 0;
        int rightX = -1;
        boolean isIconLightning = false;

        if (incremental && !e.levels.isEmpty()) {
            String icon = "\u26A1";
            right = "\u00A7e" + icon;
            rightW = font.width(icon) + 3;
            rightX = x + clipW - rightW - 2;
            isIconLightning = true;
        } else if (multi) {
            right = expanded ? "\u25BC" : "\u25B6";
            rightW = font.width(right) + 3;
            rightX = x + clipW - rightW - 2;
        }

        int textX = starX + STAR_W + 3;
        int maxNameW = clipW - (textX - x) - 6 - rightW;

        String name;
        try {
            name = e.getDisplayNameComponent(1).getString();
        } catch (Throwable t) {
            name = e.getDisplayName();
        }
        String display = font.width(name) > maxNameW
                ? font.plainSubstrByWidth(name, maxNameW - 4) + "..."
                : name;

        font.draw(ps, display, textX, textY,
                sel ? 0xFFFFDD77 : (hover ? 0xFFFFFF : e.color));

        if (rightW > 0) {
            font.draw(ps, right, rightX, textY, 0xCCCCCC);
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

    private void drawSubRow(PoseStack ps, Font font,
                            int x, int y, int clipW,
                            ModifierIndex.Entry e, ModifierIndex.LevelInfo li,
                            boolean sel, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + clipW
                && mouseY >= y && mouseY <= y + SUB_ROW_H;
        if (sel || hover) {
            GuiComponent.fill(ps, x, y, x + clipW, y + SUB_ROW_H,
                    sel ? 0x44FFAA00 : 0x1AFFFFFF);
        }

        String name = li.displayName.getString();
        String display = font.width(name) > clipW - 26
                ? font.plainSubstrByWidth(name, clipW - 30) + "..."
                : name;

        int textY = y + (SUB_ROW_H - font.lineHeight) / 2 + 1;
        font.draw(ps, display, x + 20, textY,
                sel ? 0xFFFFDD77 : (hover ? 0xFFFFFF : e.color));
    }

    // ============================================================
    // ===== 右栏：矩阵 + 卡片 ====================================
    // ============================================================

    private void renderRight(PoseStack ps, Font font,
                             int x, int y, int w, int h,
                             int mouseX, int mouseY) {
        GuiComponent.fill(ps, x, y, x + w, y + h, 0xFF181818);
        GuiComponent.fill(ps, x, y, x + w, y + 1, 0xFF333333);

        int size = SLOT_SIZE;
        int gap = SLOT_GAP;
        int midX = x + w / 2;

        int row1Y = y + 6;
        int row2Y = row1Y + size + gap + 2;
        int row3Y = row2Y + size + gap + 2;

        ItemStack[] matIcons = new ItemStack[5];
        for (int i = 0; i < 5; i++) matIcons[i] = ItemStack.EMPTY;

        ModifierIndex.LevelInfo selLi = getSelectedLevelInfo();
        if (selLi != null) {
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

        // 阶段 1：所有槽位背景
        drawSlotBg(ps, t1x, row1Y, mouseX, mouseY);
        drawSlotBg(ps, t2x, row2Y, mouseX, mouseY);
        drawSlotBg(ps, itemX, row2Y, mouseX, mouseY);
        drawSlotBg(ps, t3x, row2Y, mouseX, mouseY);
        drawSlotBg(ps, t4x, row3Y, mouseX, mouseY);
        drawSlotBg(ps, t5x, row3Y, mouseX, mouseY);

        // 阶段 2：统一画物品图标
        // ★ 父级 onScreenDrawPost 已 depthMask(false)，必须临时打开，
        //   否则附魔光效消失、同层多物品被错误剔除。
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

        int infoTop = row3Y + size + 8;
        int infoH = y + h - infoTop - 4;

        rightAreaX = x + 2;
        rightAreaY = infoTop;
        rightAreaW = w - 4;
        rightAreaH = infoH;

        if (selected == null || selLi == null) {
            font.draw(ps, "\u00A77" + new TranslatableComponent(
                            "gui.anvilssearch.modifier.pick_hint").getString(),
                    x + 6, infoTop + 4, 0x666666);
            return;
        }

        int maxTextW = w - 8 - 14;
        List<Card> cards = new ArrayList<>();

        // 标题
        String titleText;
        try {
            titleText = selLi.displayName.getString();
        } catch (Throwable t) {
            titleText = selected.getDisplayName();
        }
        MutableComponent titleComp = new TextComponent(titleText)
                .withStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(selected.color))
                        .withBold(true));
        int titleW = font.width(titleComp);
        int titleX = x + (w - titleW) / 2;
        int titleY = infoTop + 4;
        font.draw(ps, titleComp, titleX, titleY, selected.color);

        int afterTitleY = titleY + LINE_H + 6;

        // 卡片 1：槽位 + 材料
        List<Component> c2 = new ArrayList<>();
        c2.add(buildSlotLine(selLi));

        if (selLi.kind == ModifierIndex.RecipeKind.INCREMENTAL
                && selLi.amountPerInput > 0 && selLi.neededPerLevel > 0) {
            int perLevel = (int) Math.ceil(
                    (double) selLi.neededPerLevel / selLi.amountPerInput);
            int maxLevel = selected.levels.size();
            c2.add(new TextComponent("\u00A7e"
                    + new TranslatableComponent(
                    "gui.anvilssearch.modifier.incremental_summary",
                    perLevel, maxLevel).getString()));
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
        cards.add(new Card(0xFF55FFFF, null, c2));

        // 卡片 2：描述
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
        cards.add(new Card(ACCENT_GOLD,
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
                cy = drawCard(ps, font, x + 4, cy, w - 8, card);
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
        if (li.slots.isEmpty()) {
            sb.append(new TranslatableComponent(
                    "gui.anvilssearch.modifier.slots_none").getString());
        } else {
            for (int i = 0; i < li.slots.size(); i++) {
                if (i > 0) sb.append("\u00A77, \u00A7f");
                ModifierIndex.SlotRequirement sr = li.slots.get(i);
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

    // ★ 精致化：更大内边距 + 更高行高
    private static int measureCard(Card card) {
        int h = CARD_PAD_Y * 2;
        if (card.title != null) h += LINE_H + 4;
        h += card.lines.size() * LINE_H;
        return h + CARD_GAP;
    }

    private static int drawCard(PoseStack ps, Font font,
                                int x, int y, int w, Card card) {
        int cardH = measureCard(card) - CARD_GAP;
        CardBackground.draw(ps, x, y, w, cardH, 0xFF1E1E1E, 0xFF3A3A3A);

        // ★ 左侧强调条
        GuiComponent.fill(ps, x, y, x + 2, y + cardH, 0xFF000000 | card.accent);

        int cy = y + CARD_PAD_Y;
        if (card.title != null) {
            font.draw(ps, card.title, x + CARD_PAD_X + 2, cy, card.accent);
            cy += LINE_H + 4;
        }
        for (Component line : card.lines) {
            font.draw(ps, line, x + CARD_PAD_X + 2, cy, 0xCCCCCC);
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
        int bg = hover ? 0xFF3A3A3A : 0xFF222222;
        int border = hover ? 0xFFAA8844 : 0xFF555555;
        GuiComponent.fill(ps, x, y, x + SLOT_SIZE, y + SLOT_SIZE, bg);
        GuiComponent.fill(ps, x, y, x + SLOT_SIZE, y + 1, border);
        GuiComponent.fill(ps, x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, border);
        GuiComponent.fill(ps, x, y, x + 1, y + SLOT_SIZE, border);
        GuiComponent.fill(ps, x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, border);
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
                int ty = y + SLOT_SIZE - 8;
                font.drawShadow(ps, s, tx, ty, 0xFFFFFF);
            }
        } catch (Throwable ignored) {}
    }

    private boolean canApplyToCurrentItem(ModifierIndex.Entry entry) {
        if (entry == null || entry.levels.isEmpty()) return false;
        ItemStack item = AnvilSlotAccess.getCenterItem();
        if (item == null || item.isEmpty()) return false;

        ModifierIndex.LevelInfo li = entry.levels.get(0);
        if (li.toolFilter != null) {
            Boolean r = testIngredient(li.toolFilter, item);
            if (r != null) return r;
        }
        return false;
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
                for (ItemStack s : arr) if (ItemStack.isSameItemSameTags(s, stack)) return true;
                return false;
            }
            if (v instanceof Collection<?> col) {
                for (Object o : col)
                    if (o instanceof ItemStack s && ItemStack.isSameItemSameTags(s, stack))
                        return true;
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
        // 1) 下拉浮层优先
        if (slotDropdownOpen) {
            if (mx >= slotDropdownX && mx <= slotDropdownX + slotDropdownW
                    && my >= slotDropdownY && my <= slotDropdownY + slotDropdownH) {
                int itemH = 14;
                int cy = slotDropdownY + 3;
                for (String type : slotTypes) {
                    if (my >= cy && my <= cy + itemH) {
                        if (!slotFilter.remove(type)) slotFilter.add(type);
                        applyFilter(panel.getSearchKeyword());
                        return true;
                    }
                    cy += itemH;
                }
                return true;
            } else {
                slotDropdownOpen = false;
                // 不 return，继续向下（允许点到别处）
            }
        }

        if (scrollBar.tryBeginDrag(mx, my)) return true;

        // 2) 筛选栏
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

        // 3) 搜索框
        if (searchBox.mouseClicked(mx, my, button)) {
            searchBox.setFocused(true);
            return true;
        }
        if (searchBox.isFocused()) searchBox.setFocused(false);

        // 4) 列表行
        if (mx >= leftListX && mx <= leftListX + leftListW
                && my >= leftListTop && my <= leftListTop + leftListH) {

            int rowY = leftListTop - scrollOffset;
            for (ModifierIndex.Entry e : filtered) {
                boolean inc = isIncrementalEntry(e);
                boolean multi = !inc && e.levels.size() > 1;
                boolean expanded = multi && expandedInList.contains(e.id);

                if (my >= rowY && my <= rowY + ROW_H) {
                    // 星标命中？
                    int starX = leftListX + 3;
                    if (mx >= starX - 2 && mx <= starX + STAR_W + 2) {
                        FavoritesStore.toggle(e.id);
                        if (favoritesOnly) applyFilter(panel.getSearchKeyword());
                        return true;
                    }
                    // 常规点击
                    if (inc) {
                        selected = e;
                        selectedLevel = 0;
                        rightScrollOffset = 0;
                        return true;
                    }
                    if (multi) {
                        if (expanded) expandedInList.remove(e.id);
                        else expandedInList.add(e.id);
                    }
                    selected = e;
                    selectedLevel = 0;
                    rightScrollOffset = 0;
                    return true;
                }
                rowY += ROW_H;

                if (expanded) {
                    for (ModifierIndex.LevelInfo li : e.levels) {
                        if (my >= rowY && my <= rowY + SUB_ROW_H) {
                            selected = e;
                            selectedLevel = li.level;
                            rightScrollOffset = 0;
                            return true;
                        }
                        rowY += SUB_ROW_H;
                    }
                }
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