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
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import top.leipishu.anvilssearch.client.AnvilPanelAnimation;
import top.leipishu.anvilssearch.client.AnvilSidebarPanel;
import top.leipishu.anvilssearch.client.AnvilTab;
import top.leipishu.anvilssearch.client.animation.controller.AnvilWidgetAnimations;
import top.leipishu.anvilssearch.client.theme.AnvilTheme;
import top.leipishu.anvilssearch.data.AnvilSlotAccess;
import top.leipishu.anvilssearch.data.FavoritesStore;
import top.leipishu.anvilssearch.data.modifier.ModifierIndex;
import top.leipishu.anvilssearch.data.modifier.ModifierMaterialText;
import top.leipishu.anvilssearch.data.modifier.ModifierRequirementChecker;
import top.leipishu.tinkerssearch.client.animation.core.ColorUtil;
import top.leipishu.tinkerssearch.client.gui.components.ScrollBar;
import top.leipishu.tinkerssearch.client.gui.components.SearchBox;
import top.leipishu.tinkerssearch.client.gui.components.SearchBoxStyle;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch.PinyinResult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

public class ModifierSearchTab implements AnvilTab {

    private static final int ROW_H     = AnvilTheme.ROW_H;
    private static final int SUB_ROW_H = AnvilTheme.SUB_ROW_H;
    private static final int LINE_H    = AnvilTheme.LINE_H;
    private static final int CARD_GAP  = AnvilTheme.CARD_GAP;
    private static final int SLOT_SIZE = AnvilTheme.SLOT_SIZE;
    private static final int SLOT_GAP  = AnvilTheme.SLOT_GAP;

    private static final int TOOLBAR_H   = 16;
    private static final int TOOLBAR_GAP = 3;
    private static final int TOOLBAR_ITEM_GAP = 3;
    private static final int STAR_BTN_W = 16;
    private static final int ICON_BTN_W = 16;
    private static final int SLOT_BTN_W = 80;

    private static final int STAR_W = 12;

    private static final int DROP_HEADER_H = 20;
    private static final int DROP_ITEM_H   = 16;
    private static final int DROP_BTN_H    = 14;
    private static final int DROP_BTN_PAD  = 4;

    private static final int CYCLE_TICKS = 20;

    private static final String SLOT_NONE = "__none__";
    private static final int CARD_ACCENT_ERROR = 0xFFFF5555;

    private static final String SLOT_DROPDOWN_ANIM_KEY = "mod.slotdrop";

    private final AnvilSidebarPanel panel;

    private List<ModifierIndex.Entry> allEntries = new ArrayList<>();
    private List<ModifierIndex.Entry> filtered   = new ArrayList<>();
    private List<String> slotTypes = new ArrayList<>();

    private boolean favoritesOnly = false;
    private boolean filterIncremental = false;
    private boolean filterUnlimited = false;
    private boolean filterReqMet = false;
    private final Set<String> slotFilter = new LinkedHashSet<>();
    private boolean slotDropdownOpen = false;
    private boolean slotDropdownClosing = false;
    private int slotDropdownX, slotDropdownY, slotDropdownW, slotDropdownH;

    private final Map<String, Boolean> reqMetCache = new HashMap<>();
    private ItemStack reqMetCacheItem = ItemStack.EMPTY;

    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private final ScrollBar scrollBar = new ScrollBar();
    private final SearchBox searchBox = new SearchBox(SearchBoxStyle.panel());

    private ModifierIndex.Entry selected = null;
    private int selectedIndex = -1;
    private final Set<String> expandedInList = new HashSet<>();

    private int leftListX, leftListTop, leftListW, leftListH;
    private int rightAreaX, rightAreaY, rightAreaW, rightAreaH;
    private int rightScrollOffset = 0;
    private int rightMaxScroll = 0;

    private int toolbarY;
    private int toolbarStarX, toolbarStarW;
    private int toolbarIncX, toolbarIncW;
    private int toolbarUnlX, toolbarUnlW;
    private int toolbarMetX, toolbarMetW;
    private int toolbarSlotX, toolbarSlotW;
    private int contentRightEdge;

    private ItemStack lastCenterItem = ItemStack.EMPTY;

    private ModifierIndex.Entry lastReqEntry = null;
    private int lastReqIndex = -1;
    private ItemStack lastReqItem = ItemStack.EMPTY;
    private Optional<Component> lastReqResult = null;

    public ModifierSearchTab(AnvilSidebarPanel panel) {
        this.panel = panel;
        this.scrollBar.setOnOffsetChanged(v -> scrollOffset = v);
        this.scrollBar.setThumbMinHeight(16);
        this.scrollBar.setHoverExpandX(3);
        this.scrollBar.setAnimationId("anvil.scroll.mod");

        this.searchBox.setHintText(Component.translatable(


                "gui.anvilssearch.modifier.search_hint"));
        this.searchBox.setOnTextChanged(this::applyFilter);
        this.searchBox.setAnimationId("anvil.modsearch");
    }

    @Override public Component getLabel() {
        return Component.translatable(

"gui.anvilssearch.tab.modifier");
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
        requestCloseSlotDropdown();
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
        allEntries = new ArrayList<>(ModifierIndex.getGrouped());
        slotTypes = collectSlotTypes();
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

        if (!sameStack(center, reqMetCacheItem)) {
            reqMetCache.clear();
            reqMetCacheItem = (center == null) ? ItemStack.EMPTY : center.copy();
        }

        List<ModifierIndex.Entry> out = new ArrayList<>();
        for (ModifierIndex.Entry e : allEntries) {
            if (!matches(e, k)) continue;
            if (favoritesOnly && !FavoritesStore.isFavorite(e.id)) continue;
            if (!slotFilter.isEmpty() && !matchesSlotFilter(e)) continue;

            if (filterIncremental && !isIncrementalOnly(e)) continue;
            if (filterUnlimited && !isUnlimitedOnly(e)) continue;
            if (filterReqMet && hasItem && !isRequirementsMet(e, center)) continue;

            ModifierIndex.Entry displayEntry = e;
            if (hasItem && !e.levels.isEmpty()) {
                ModifierIndex.Entry sub = filterLevelsByTool(e, center);
                if (sub == null) continue;
                displayEntry = sub;
            }
            out.add(displayEntry);
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

        if (selected != null) {
            boolean found = false;
            for (ModifierIndex.Entry e : filtered) {
                if (selected.id != null && selected.id.equals(e.id)) {
                    selected = e;
                    if (selectedIndex >= e.levels.size()) selectedIndex = -1;
                    found = true;
                    break;
                }
            }
            if (!found) {
                selected = null;
                selectedIndex = -1;
            }
        }

        lastReqEntry = null;
        lastReqIndex = -1;
        lastReqItem = ItemStack.EMPTY;
    }

    private static boolean isIncrementalOnly(ModifierIndex.Entry e) {
        return !e.levels.isEmpty()
                && e.levels.get(0).kind == ModifierIndex.RecipeKind.INCREMENTAL;
    }

    private static boolean isUnlimitedOnly(ModifierIndex.Entry e) {
        return !e.levels.isEmpty()
                && e.levels.get(0).kind == ModifierIndex.RecipeKind.UNLIMITED;
    }

    private boolean isRequirementsMet(ModifierIndex.Entry e, ItemStack tool) {
        if (tool == null || tool.isEmpty()) return true;
        if (e.levels.isEmpty()) return true;

        String key = e.id == null ? "" : e.id;
        Boolean cached = reqMetCache.get(key);
        if (cached != null) return cached;

        Object container = ModifierRequirementChecker.makeContainer(tool);
        Object recipeForCheck = e.levels.get(0).recipe != null
                ? e.levels.get(0).recipe : e.recipe;
        Optional<Component> r = ModifierRequirementChecker.checkRequirements(
                recipeForCheck, container);

        boolean met = (r == null) || !r.isPresent();
        reqMetCache.put(key, met);
        return met;
    }

    private static ModifierIndex.Entry filterLevelsByTool(ModifierIndex.Entry e, ItemStack tool) {
        List<ModifierIndex.LevelInfo> kept = new ArrayList<>();
        for (ModifierIndex.LevelInfo li : e.levels) {
            if (matchesTool(li.toolRequirement, tool)) {
                kept.add(li);
            }
        }
        if (kept.isEmpty()) return null;
        if (kept.size() == e.levels.size()) return e;

        return new ModifierIndex.Entry(
                e.modifier, e.recipe, e.id, e.registryPath,
                e.color, kept.size(), kept);
    }

    private static boolean matchesTool(Object toolRequirement, ItemStack tool) {
        if (toolRequirement == null) return true;
        if (tool == null || tool.isEmpty()) return true;

        try {
            Method test = toolRequirement.getClass().getMethod("test", ItemStack.class);
            test.setAccessible(true);
            Object result = test.invoke(toolRequirement, tool);
            if (result instanceof Boolean b) return b;
        } catch (Throwable ignored) {}
        return true;
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
        String main = entryName(e);
        if (main.toLowerCase(Locale.ROOT).contains(k)) return true;
        for (ModifierIndex.LevelInfo li : e.levels) {
            try {
                String n = li.displayName.getString();
                if (n != null && n.toLowerCase(Locale.ROOT).contains(k)) return true;
            } catch (Throwable ignored) {}
        }
        if (e.registryPath != null && e.registryPath.toLowerCase(Locale.ROOT).contains(k))
            return true;
        try {
            PinyinResult py = PinyinSearch.getPinyin(main);
            if (py.fullPinyin.contains(k) || py.initials.contains(k)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isLightningEntry(ModifierIndex.Entry e) {
        if (e.levels.isEmpty()) return false;
        ModifierIndex.RecipeKind k = e.levels.get(0).kind;
        return k == ModifierIndex.RecipeKind.INCREMENTAL
                || k == ModifierIndex.RecipeKind.UNLIMITED;
    }

    private static boolean isMultiLevelEntry(ModifierIndex.Entry e) {
        return e.levels.size() > 1;
    }

    private ModifierIndex.LevelInfo getSelectedLevelInfo() {
        if (selected == null || selected.levels.isEmpty()) return null;
        if (selectedIndex >= 0 && selectedIndex < selected.levels.size()) {
            return selected.levels.get(selectedIndex);
        }
        return selected.levels.get(0);
    }

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

    private void requestCloseSlotDropdown() {
        if (!slotDropdownOpen || slotDropdownClosing) return;
        slotDropdownClosing = true;
        AnvilWidgetAnimations.startPopupAnimation(SLOT_DROPDOWN_ANIM_KEY, false);
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
        int areaBottom   = py + contentBottom;
        int totalW = contentRight - contentLeft;

        contentRightEdge = contentRight;

        toolbarY = areaTop;
        renderToolbar(ps, font, contentLeft, areaTop, totalW, mouseX, mouseY);

        int cardsTop = areaTop + TOOLBAR_H + TOOLBAR_GAP;
        int cardsH = areaBottom - cardsTop;
        if (cardsH < 20) {
            renderSlotDropdownIfOpen(ps, font, mouseX, mouseY);
            return;
        }

        int leftW  = (int) (totalW * 0.42f);
        int rightW = totalW - leftW - 4;
        int leftX  = contentLeft;
        int rightX = leftX + leftW + 4;

        renderLeft(ps, font, leftX, cardsTop, leftW, cardsH, mouseX, mouseY);
        renderRight(ps, font, rightX, cardsTop, rightW, cardsH, mouseX, mouseY);

        renderSlotDropdownIfOpen(ps, font, mouseX, mouseY);
    }

    private void renderSlotDropdownIfOpen(PoseStack ps, Font font,
                                          int mouseX, int mouseY) {
        if (!slotDropdownOpen) return;

        // ★ 强制 flush 之前所有绘制（物品图标 / 数量数字）
        try {
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        } catch (Throwable ignored) {}

        boolean depthWas     = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMaskWas = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            renderSlotDropdown(ps, font, mouseX, mouseY);
        } finally {
            // ★ popup 结束后立即 flush，防止其内容泄漏到数字层
            try {
                Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
            } catch (Throwable ignored) {}
            RenderSystem.depthMask(depthMaskWas);
            if (depthWas) RenderSystem.enableDepthTest();
            else          RenderSystem.disableDepthTest();
        }

        if (slotDropdownClosing
                && AnvilWidgetAnimations.isPopupFadeComplete(SLOT_DROPDOWN_ANIM_KEY)) {
            slotDropdownOpen = false;
            slotDropdownClosing = false;
        }
    }

    private void renderToolbar(PoseStack ps, Font font,
                               int x, int y, int w,
                               int mouseX, int mouseY) {
        int gap = TOOLBAR_ITEM_GAP;
        int starW = STAR_BTN_W;
        int iconW = ICON_BTN_W;
        int slotW = SLOT_BTN_W;

        int fixed = starW + iconW * 3 + slotW + gap * 5;
        int searchW = w - fixed;
        if (searchW < 60) {
            slotW = Math.max(50, w / 5);
            searchW = Math.max(60, w - starW - iconW * 3 - slotW - gap * 5);
        }

        int searchX = x;
        int starX   = searchX + searchW + gap;
        int incX    = starX + starW + gap;
        int unlX    = incX + iconW + gap;
        int metX    = unlX + iconW + gap;
        int slotX   = metX + iconW + gap;

        toolbarStarX = starX;  toolbarStarW = starW;
        toolbarIncX  = incX;   toolbarIncW  = iconW;
        toolbarUnlX  = unlX;   toolbarUnlW  = iconW;
        toolbarMetX  = metX;   toolbarMetW  = iconW;
        toolbarSlotX = slotX;  toolbarSlotW = slotW;

        searchBox.setBounds(searchX, y, searchW, TOOLBAR_H);
        searchBox.render(ps, mouseX, mouseY, font);

        boolean starHover = mouseX >= starX && mouseX <= starX + starW
                && mouseY >= y && mouseY <= y + TOOLBAR_H;
        float starHoverT = AnvilWidgetAnimations.buttonHover("mod.toolbar.star", starHover);
        String starLabel = favoritesOnly ? "\u2605" : "\u2606";
        AnvilTheme.button(ps, font, starX, y, starW, TOOLBAR_H,
                starLabel, starHoverT, favoritesOnly ? 1f : 0f);

        boolean incHover = mouseX >= incX && mouseX <= incX + iconW
                && mouseY >= y && mouseY <= y + TOOLBAR_H;
        float incHoverT = AnvilWidgetAnimations.buttonHover("mod.toolbar.inc", incHover);
        AnvilTheme.button(ps, font, incX, y, iconW, TOOLBAR_H,
                "\u26A1", incHoverT, filterIncremental ? 1f : 0f);
        if (incHover) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable(


                    "gui.anvilssearch.modifier.filter.incremental_tip"));
            panel.setPendingTooltip(tip);
        }

        boolean unlHover = mouseX >= unlX && mouseX <= unlX + iconW
                && mouseY >= y && mouseY <= y + TOOLBAR_H;
        float unlHoverT = AnvilWidgetAnimations.buttonHover("mod.toolbar.unl", unlHover);
        AnvilTheme.button(ps, font, unlX, y, iconW, TOOLBAR_H,
                "\u221E", unlHoverT, filterUnlimited ? 1f : 0f);
        if (unlHover) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable(


                    "gui.anvilssearch.modifier.filter.unlimited_tip"));
            panel.setPendingTooltip(tip);
        }

        boolean metHover = mouseX >= metX && mouseX <= metX + iconW
                && mouseY >= y && mouseY <= y + TOOLBAR_H;
        float metHoverT = AnvilWidgetAnimations.buttonHover("mod.toolbar.met", metHover);
        AnvilTheme.button(ps, font, metX, y, iconW, TOOLBAR_H,
                "\u2713", metHoverT, filterReqMet ? 1f : 0f);
        if (metHover) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable(


                    "gui.anvilssearch.modifier.filter.req_met_tip"));
            panel.setPendingTooltip(tip);
        }

        boolean slotHover = mouseX >= slotX && mouseX <= slotX + slotW
                && mouseY >= y && mouseY <= y + TOOLBAR_H;
        boolean slotActive = !slotFilter.isEmpty();
        float slotHoverT = AnvilWidgetAnimations.buttonHover("mod.toolbar.slot", slotHover);

        String label;
        if (slotActive) {
            label = Component.translatable(


                    "gui.anvilssearch.modifier.filter.slots_n",
                    slotFilter.size()).getString();
        } else {
            label = Component.translatable(


                    "gui.anvilssearch.modifier.filter.slots").getString();
        }
        if (font.width(label) > slotW - 6) {
            label = font.plainSubstrByWidth(label, slotW - 10) + "...";
        }
        AnvilTheme.button(ps, font, slotX, y, slotW, TOOLBAR_H,
                label, slotHoverT, slotActive ? 1f : 0f);
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

        int listTop = y + pad;
        int listH = (y + h) - listTop - pad;
        int listW = innerW;

        leftListX = innerX;
        leftListTop = listTop;
        leftListW = listW;
        leftListH = listH;

        if (filtered.isEmpty()) {
            font.draw(ps, Component.translatable(


                            "gui.anvilssearch.modifier.empty").getString(),
                    innerX + 2, listTop + 4, AnvilTheme.TEXT_DIM);
            return;
        }

        int totalH = 0;
        for (ModifierIndex.Entry e : filtered) {
            totalH += ROW_H;
            if (isMultiLevelEntry(e)) {
                boolean expanded = expandedInList.contains(e.id);
                String ek = e.id != null ? e.id : String.valueOf(e.hashCode());
                float p = AnvilWidgetAnimations.expandProgress("mod.expand:" + ek, expanded);
                if (p > 0.01f) {
                    totalH += (int) (e.levels.size() * SUB_ROW_H * p);
                }
            }
        }
        maxScrollOffset = Math.max(0, totalH - listH);
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;

        int scrollBarW = maxScrollOffset > 0 ? SCROLL_BAR_WIDTH + SCROLL_BAR_PADDING : 0;
        int clipW = listW - scrollBarW;

        boolean scissorOk = ScissorHelper.enableScissor(innerX, listTop, clipW, listH);
        if (scissorOk) RenderSystem.disableDepthTest();
        try {
            int rowY = listTop - scrollOffset;
            for (ModifierIndex.Entry e : filtered) {
                boolean lightning = isLightningEntry(e);
                boolean multi = isMultiLevelEntry(e);
                boolean expanded = multi && expandedInList.contains(e.id);

                drawEntryRow(ps, font, innerX, rowY, clipW, e,
                        multi, expanded, lightning,
                        selected == e && selectedIndex < 0,
                        mouseX, mouseY);
                rowY += ROW_H;

                String expandKey = e.id != null ? e.id : String.valueOf(e.hashCode());
                float p = AnvilWidgetAnimations.expandProgress("mod.expand:" + expandKey, expanded);

                if (p > 0.01f) {
                    int fullH = e.levels.size() * SUB_ROW_H;
                    int visH = Math.max(1, (int) (fullH * p));

                    ScissorHelper.disableScissor();
                    try {
                        int clipTop = Math.max(rowY, listTop);
                        int clipBot = Math.min(rowY + visH, listTop + listH);
                        int clipH = clipBot - clipTop;

                        if (clipH > 0) {
                            boolean sOk = ScissorHelper.enableScissor(
                                    innerX, clipTop, clipW, clipH);
                            if (sOk) RenderSystem.disableDepthTest();
                            try {
                                int rowYY = rowY;
                                for (int i = 0; i < e.levels.size(); i++) {
                                    ModifierIndex.LevelInfo li = e.levels.get(i);
                                    drawSubRow(ps, font, innerX, rowYY, clipW, e, li,
                                            selected == e && selectedIndex == i,
                                            mouseX, mouseY);
                                    rowYY += SUB_ROW_H;
                                }
                            } finally {
                                if (sOk) ScissorHelper.disableScissor();
                            }
                        }
                    } finally {
                        boolean restored = ScissorHelper.enableScissor(
                                innerX, listTop, clipW, listH);
                        if (restored) RenderSystem.disableDepthTest();
                    }

                    rowY += fullH;
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

    private void renderSlotDropdown(PoseStack ps, Font font,
                                    int mouseX, int mouseY) {
        if (slotTypes.isEmpty()) return;

        float alpha = AnvilWidgetAnimations.getPopupFade(SLOT_DROPDOWN_ANIM_KEY, 1f);
        float scale = AnvilWidgetAnimations.getPopupScale(SLOT_DROPDOWN_ANIM_KEY, 1f);
        if (alpha <= 0.01f) return;

        int w = 120;
        int h = DROP_HEADER_H + slotTypes.size() * DROP_ITEM_H + 6;

        int x = toolbarSlotX;
        if (x + w > contentRightEdge) x = contentRightEdge - w;
        if (x < 0) x = 0;
        int y = toolbarY + TOOLBAR_H + 2;

        float cx = x + w / 2f;
        float cy = y + h / 2f;
        ps.pushPose();
        ps.translate(cx, cy, 0);
        ps.scale(scale, scale, 1f);
        ps.translate(-cx, -cy, 0);

        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        try {
            AnvilTheme.cardBg(ps, x, y, w, h, 0);

            int btnX = x + DROP_BTN_PAD;
            int btnY = y + 4;
            int btnW = w - DROP_BTN_PAD * 2;
            int btnH = DROP_BTN_H;

            boolean allSelected = !slotTypes.isEmpty() && slotFilter.containsAll(slotTypes);

            boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW
                    && mouseY >= btnY && mouseY <= btnY + btnH;

            String btnLabel = allSelected
                    ? Component.translatable(


                    "gui.anvilssearch.modifier.filter.clear").getString()
                    : Component.translatable(


                    "gui.anvilssearch.modifier.filter.select_all").getString();

            float btnHoverT = AnvilWidgetAnimations.buttonHover("mod.dropdown.all", btnHover);
            AnvilTheme.button(ps, font, btnX, btnY, btnW, btnH,
                    btnLabel, btnHoverT, allSelected ? 1f : 0f);

            GuiComponent.fill(ps, x + 2, y + DROP_HEADER_H - 1,
                    x + w - 2, y + DROP_HEADER_H, AnvilTheme.SECTION_BORDER);

            int cyRow = y + DROP_HEADER_H + 2;
            for (String type : slotTypes) {
                boolean hover = mouseX >= x + 1 && mouseX <= x + w - 1
                        && mouseY >= cyRow && mouseY <= cyRow + DROP_ITEM_H;

                float itemHoverT = AnvilWidgetAnimations.rowHover("mod.dropdown:" + type, hover);
                if (itemHoverT > 0.01f) {
                    int bgColor = ColorUtil.withAlphaFactor(0x22FFFFFF, itemHoverT);
                    GuiComponent.fill(ps, x + 1, cyRow, x + w - 1, cyRow + DROP_ITEM_H, bgColor);
                }
                boolean checked = slotFilter.contains(type);
                String box = checked ? "\u2611" : "\u2610";
                int textY = AnvilTheme.centeredTextY(cyRow, DROP_ITEM_H, font);
                font.draw(ps, box, x + 6, textY,
                        checked ? AnvilTheme.ACCENT : AnvilTheme.TEXT_MUTED);

                Component lbl;
                if (SLOT_NONE.equals(type)) {
                    lbl = Component.translatable(

"gui.anvilssearch.slot.none");
                } else {
                    lbl = Component.translatable(

"gui.anvilssearch.slot." + type);
                }
                font.draw(ps, lbl, x + 20, textY,
                        checked ? AnvilTheme.TEXT_PRIMARY : AnvilTheme.TEXT_SECONDARY);

                cyRow += DROP_ITEM_H;
            }

            slotDropdownX = x;
            slotDropdownY = y;
            slotDropdownW = w;
            slotDropdownH = h;
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            ps.popPose();
        }
    }

    private void drawEntryRow(PoseStack ps, Font font,
                              int x, int y, int clipW, ModifierIndex.Entry e,
                              boolean multi, boolean expanded, boolean lightning,
                              boolean sel, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + clipW
                && mouseY >= y && mouseY <= y + ROW_H;

        String rowKey = e.id != null ? e.id : String.valueOf(e.hashCode());
        float hoverT = AnvilWidgetAnimations.rowHover("mod:" + rowKey, hover);
        float selectedT = AnvilWidgetAnimations.rowSelected("mod:" + rowKey, sel);

        int bg = ColorUtil.withAlphaFactor(0x22FFFFFF, hoverT);
        if (selectedT > 0.01f) {
            int selBg = selectedRowBg(e.color);
            bg = ColorUtil.lerpARGB(bg, selBg, selectedT);
        }
        if ((bg >>> 24) != 0) {
            GuiComponent.fill(ps, x, y, x + clipW, y + ROW_H, bg);
        }

        int textY = AnvilTheme.centeredTextY(y, ROW_H, font);

        boolean fav = FavoritesStore.isFavorite(e.id);
        String star = fav ? "\u2605" : "\u2606";
        int starX = x + 3;
        boolean starHover = mouseX >= starX - 1 && mouseX <= starX + STAR_W + 1
                && mouseY >= y && mouseY <= y + ROW_H;
        int starColor = fav ? AnvilTheme.ACCENT
                : (starHover ? AnvilTheme.TEXT_MUTED : AnvilTheme.TEXT_DIM);

        String starKey = e.id != null ? e.id : String.valueOf(e.hashCode());
        float starScale = AnvilWidgetAnimations.starScale(starKey);
        boolean applyStarScale = Math.abs(starScale - 1f) > 0.005f;

        if (applyStarScale) {
            float scx = starX + STAR_W / 2f;
            float scy = y + ROW_H / 2f;
            ps.pushPose();
            ps.translate(scx, scy, 0);
            ps.scale(starScale, starScale, 1f);
            ps.translate(-scx, -scy, 0);
            font.draw(ps, star, starX, textY, starColor);
            ps.popPose();
        } else {
            font.draw(ps, star, starX, textY, starColor);
        }

        String right = "";
        int rightW = 0;
        int rightX = -1;
        boolean isIcon = false;
        boolean isIconUnlimited = false;

        if (multi) {
            right = expanded ? "\u25BC" : "\u25B6";
            rightW = font.width(right) + 3;
            rightX = x + clipW - rightW - 2;
        } else if (lightning && !e.levels.isEmpty()) {
            ModifierIndex.RecipeKind k = e.levels.get(0).kind;
            if (k == ModifierIndex.RecipeKind.UNLIMITED) {
                right = "\u221E";
                isIconUnlimited = true;
            } else {
                right = "\u26A1";
            }
            rightW = font.width(right) + 3;
            rightX = x + clipW - rightW - 2;
            isIcon = true;
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
            int iconColor = isIcon
                    ? AnvilTheme.ACCENT_SOFT
                    : AnvilTheme.TEXT_MUTED;
            font.draw(ps, right, rightX, textY, iconColor);
        }

        if (isIcon && rightX >= 0) {
            int iconW = font.width(right);
            if (mouseX >= rightX - 2 && mouseX <= rightX + iconW + 2
                    && mouseY >= y && mouseY <= y + ROW_H) {
                ModifierIndex.LevelInfo li = e.levels.get(0);
                List<Component> tip = new ArrayList<>();
                if (isIconUnlimited) {
                    tip.add(Component.literal("\u00A7e" + Component.translatable(


                            "gui.anvilssearch.modifier.unlimited_title").getString()));
                    tip.add(Component.literal("\u00A77" + Component.translatable(


                            "gui.anvilssearch.modifier.unlimited_hint").getString()));
                } else {
                    tip.add(Component.literal("\u00A7e" + Component.translatable(


                            "gui.anvilssearch.modifier.incremental_title").getString()));
                    tip.add(Component.literal("\u00A77" + Component.translatable(


                            "gui.anvilssearch.modifier.incremental_hint",
                            li.amountPerInput, li.neededPerLevel).getString()));
                }
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

        String subKey = (e.id != null ? e.id : String.valueOf(e.hashCode()))
                + "#" + li.level;
        float hoverT = AnvilWidgetAnimations.rowHover("mod.sub:" + subKey, hover);
        float selectedT = AnvilWidgetAnimations.rowSelected("mod.sub:" + subKey, sel);

        int bg = ColorUtil.withAlphaFactor(0x22FFFFFF, hoverT);
        if (selectedT > 0.01f) {
            bg = ColorUtil.lerpARGB(bg, selectedRowBg(e.color), selectedT);
        }
        if ((bg >>> 24) != 0) {
            GuiComponent.fill(ps, x, y, x + clipW, y + SUB_ROW_H, bg);
        }

        String name = li.displayName.getString();
        String display = font.width(name) > clipW - 26
                ? font.plainSubstrByWidth(name, clipW - 30) + "..."
                : name;

        int textY = AnvilTheme.centeredTextY(y, SUB_ROW_H, font);
        font.draw(ps, display, x + 20, textY,
                sel ? readableTint(e.color)
                        : (hover ? AnvilTheme.TEXT_PRIMARY : e.color));
    }

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

        int t1x = midX - size / 2;
        int t2x = midX - size / 2 - size - gap;
        int itemX = midX - size / 2;
        int t3x = midX + size / 2 + gap;
        int bottomTotalW = size * 2 + gap;
        int bottomStartX = midX - bottomTotalW / 2;
        int t4x = bottomStartX;
        int t5x = bottomStartX + size + gap;

        int[] slotX = new int[6];
        int[] slotY = new int[6];
        slotX[0] = itemX; slotY[0] = row2Y;
        slotX[1] = t2x;   slotY[1] = row2Y;
        slotX[2] = t1x;   slotY[2] = row1Y;
        slotX[3] = t3x;   slotY[3] = row2Y;
        slotX[4] = t4x;   slotY[4] = row3Y;
        slotX[5] = t5x;   slotY[5] = row3Y;

        ItemStack[] anvilSlots = AnvilSlotAccess.getAllSlots();

        ItemStack centerItem = (anvilSlots.length > 0) ? anvilSlots[0] : ItemStack.EMPTY;
        if (centerItem == null) centerItem = ItemStack.EMPTY;

        ModifierIndex.LevelInfo selLi = getSelectedLevelInfo();
        boolean hasSelection = (selected != null && selLi != null);

        long tick = 0;
        if (Minecraft.getInstance().level != null) {
            tick = Minecraft.getInstance().level.getGameTime();
        }
        int cycle = (int) (tick / CYCLE_TICKS);

        ItemStack[] displayIcons = new ItemStack[6];
        boolean[] slotHasItem = new boolean[6];
        boolean[] slotMatched = new boolean[6];
        boolean[] slotRequired = new boolean[6];
        boolean[] slotPreview = new boolean[6];

        for (int i = 1; i < 6; i++) {
            ItemStack it = (i < anvilSlots.length) ? anvilSlots[i] : ItemStack.EMPTY;
            if (it != null && !it.isEmpty()) {
                displayIcons[i] = it;
                slotHasItem[i] = true;
                slotRequired[i] = isSlotRequired(selLi, i);
                slotMatched[i] = hasSelection && slotRequired[i]
                        && itemMatchesEntry(it, selLi);
            } else if (hasSelection) {
                List<ItemStack> candidates = null;
                if (selLi.slotMaterials != null && i - 1 < selLi.slotMaterials.length) {
                    candidates = selLi.slotMaterials[i - 1];
                }
                if (candidates != null && !candidates.isEmpty()) {
                    displayIcons[i] = candidates.get(cycle % candidates.size());
                    slotHasItem[i] = true;
                    slotPreview[i] = true;
                    slotRequired[i] = true;
                }
            }
        }

        for (int i = 1; i < 6; i++) {
            int border = 0;
            if (hasSelection && slotHasItem[i] && !slotPreview[i]) {
                if (!slotRequired[i]) {
                    border = 0xFFFFAA00;
                } else if (slotMatched[i]) {
                    border = 0xFF44DD44;
                } else {
                    border = 0xFFDD4444;
                }
            }
            drawSlotBgWithBorder(ps, slotX[i], slotY[i], mouseX, mouseY, border);
        }
        drawSlotBg(ps, itemX, row2Y, mouseX, mouseY);

        boolean depthWas     = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMaskWas = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            for (int i = 1; i < 6; i++) {
                if (slotHasItem[i] && displayIcons[i] != null && !displayIcons[i].isEmpty()) {
                    if (slotPreview[i]) {
                        RenderSystem.setShaderColor(1f, 1f, 1f, 0.55f);
                        drawItemIcon(ps, displayIcons[i], slotX[i], slotY[i]);
                        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    } else {
                        drawItemIcon(ps, displayIcons[i], slotX[i], slotY[i]);
                    }
                } else if (!hasSelection) {
                    drawPlaceholderIcon(ps, slotX[i], slotY[i]);
                }
            }

            drawItemIcon(ps, centerItem, itemX, row2Y);
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(depthMaskWas);
            if (depthWas) RenderSystem.enableDepthTest();
            else          RenderSystem.disableDepthTest();
        }

        if (hasSelection) {
            for (int i = 1; i < 6; i++) {
                if (!slotHasItem[i] || slotPreview[i]) continue;
                boolean ok = slotRequired[i] && slotMatched[i];
                if (ok) continue;

                if (mouseX >= slotX[i] && mouseX <= slotX[i] + SLOT_SIZE
                        && mouseY >= slotY[i] && mouseY <= slotY[i] + SLOT_SIZE) {
                    List<Component> tip = new ArrayList<>();
                    if (!slotRequired[i]) {
                        tip.add(Component.translatable(


                                "gui.anvilssearch.modifier.slot.not_required"));
                    } else {
                        tip.add(Component.translatable(


                                "gui.anvilssearch.modifier.slot.expected"));
                        if (selLi.slotMaterials != null
                                && i - 1 < selLi.slotMaterials.length) {
                            List<ItemStack> cands = selLi.slotMaterials[i - 1];
                            if (cands != null) {
                                for (ItemStack c : cands) {
                                    if (c == null || c.isEmpty()) continue;
                                    tip.add(Component.literal("\u00A77- "
                                            + c.getHoverName().getString()));
                                }
                            }
                        }
                    }
                    panel.setPendingTooltip(tip);
                    break;
                }
            }
        }

        int infoTop = row3Y + size + 10;
        int infoH = (y + h) - infoTop - AnvilTheme.PAD_S;

        rightAreaX = x + 2;
        rightAreaY = infoTop;
        rightAreaW = w - 4;
        rightAreaH = infoH;

        if (selected == null || selLi == null) {
            font.draw(ps, Component.translatable(


                            "gui.anvilssearch.modifier.pick_hint").getString(),
                    x + AnvilTheme.PAD_M, infoTop + 4, AnvilTheme.TEXT_DIM);
            return;
        }

        int maxTextW = w - AnvilTheme.PAD_L * 2 - 14;
        List<Card> cards = new ArrayList<>();

        String titleText = selLi.displayName.getString();
        MutableComponent titleComp = Component.literal(titleText)
                .withStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(selected.color))
                        .withBold(true));
        int titleW = font.width(titleComp);
        int titleX = x + (w - titleW) / 2;
        int titleY = infoTop + 4;
        font.draw(ps, titleComp, titleX, titleY, selected.color);

        int afterTitleY = titleY + LINE_H + 6;

        ItemStack currentAnvilItem = centerItem;

        boolean needRecheck = (selected != lastReqEntry)
                || (selectedIndex != lastReqIndex)
                || !ItemStack.isSameItemSameTags(currentAnvilItem, lastReqItem);

        if (needRecheck) {
            Object container = ModifierRequirementChecker.makeContainer(currentAnvilItem);
            Object recipeForCheck = selLi.recipe != null ? selLi.recipe : selected.recipe;
            lastReqResult = ModifierRequirementChecker.checkRequirements(
                    recipeForCheck, container);
            lastReqEntry = selected;
            lastReqIndex = selectedIndex;
            lastReqItem = currentAnvilItem.copy();
        }

        Optional<Component> reqCheck = lastReqResult;

        if (reqCheck != null && reqCheck.isPresent()) {
            Component msg = reqCheck.get();
            String msgStr = msg.getString();
            String titleStr = Component.translatable(


                    "gui.anvilssearch.modifier.requirements_error").getString();

            List<Component> errLines = new ArrayList<>();
            if (msgStr != null && !msgStr.isEmpty() && !msgStr.equals(titleStr)) {
                List<Component> wrapped = ModifierMaterialText.wrapComponent(
                        font, msg, maxTextW);
                for (Component wc : wrapped) {
                    errLines.add(Component.literal("\u00A77" + wc.getString()));
                }
            }

            cards.add(new Card(
                    CARD_ACCENT_ERROR,
                    "\u00A7c" + titleStr,
                    errLines));
        }

        List<Component> c2 = new ArrayList<>();
        c2.add(buildSlotLine(selLi));

        if (selLi.kind == ModifierIndex.RecipeKind.INCREMENTAL
                && selLi.amountPerInput > 0 && selLi.neededPerLevel > 0) {
            Component hint = Component.literal("\u00A7e"
                    + Component.translatable(


                    "gui.anvilssearch.modifier.incremental_summary",
                    selLi.amountPerInput, selLi.neededPerLevel).getString());
            c2.addAll(ModifierMaterialText.wrapComponent(font, hint, maxTextW));
        } else if (selLi.kind == ModifierIndex.RecipeKind.UNLIMITED) {
            Component hint = Component.literal("\u00A7e"
                    + Component.translatable(


                    "gui.anvilssearch.modifier.unlimited_hint").getString());
            c2.addAll(ModifierMaterialText.wrapComponent(font, hint, maxTextW));
        }

        if (selLi.variant != null) {
            Component varLine = Component.literal("\u00A77"
                    + Component.translatable(


                    "gui.anvilssearch.modifier.variant").getString()
                    + ": " + selLi.variant.getString());
            c2.addAll(ModifierMaterialText.wrapComponent(font, varLine, maxTextW));
        }

        List<Component> materialLines = ModifierMaterialText.buildMaterialLines(selLi);

        if (!materialLines.isEmpty()) {
            c2.add(Component.literal(""));
            for (Component line : materialLines) {
                c2.addAll(ModifierMaterialText.wrapComponent(font, line, maxTextW));
            }
        } else {
            c2.add(Component.literal("\u00A78" + Component.translatable(


                    "gui.anvilssearch.modifier.no_recipe").getString()));
        }
        cards.add(new Card(AnvilTheme.ACCENT_CYAN, null, c2));

        List<Component> c3 = new ArrayList<>();
        List<Component> desc = selected.getDescriptionList(selLi.level);
        if (desc.isEmpty()) {
            c3.add(Component.literal("\u00A78" + Component.translatable(


                    "gui.anvilssearch.modifier.no_description").getString()));
        } else {
            for (Component d : desc) {
                c3.addAll(ModifierMaterialText.wrapComponent(font, d, maxTextW));
            }
        }
        cards.add(new Card(AnvilTheme.ACCENT,
                "\u00A7e" + Component.translatable(


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

    private static boolean isSlotRequired(ModifierIndex.LevelInfo li, int anvilIndex) {
        if (li == null || li.slotMaterials == null) return false;
        int idx = anvilIndex - 1;
        if (idx < 0 || idx >= li.slotMaterials.length) return false;
        List<ItemStack> candidates = li.slotMaterials[idx];
        return candidates != null && !candidates.isEmpty();
    }

    private static boolean itemMatchesEntry(ItemStack item, ModifierIndex.LevelInfo li) {
        if (item == null || item.isEmpty()) return false;
        if (li == null || li.slotMaterials == null) return false;

        String itemKey = ModifierMaterialText.stackKeyOf(item);
        for (List<ItemStack> candidates : li.slotMaterials) {
            if (candidates == null || candidates.isEmpty()) continue;
            for (ItemStack cand : candidates) {
                if (cand == null || cand.isEmpty()) continue;
                if (ModifierMaterialText.stackKeyOf(cand).equals(itemKey)) return true;
            }
        }
        return false;
    }

    private void drawSlotBgWithBorder(PoseStack ps, int x, int y,
                                      int mouseX, int mouseY, int border) {
        boolean hover = mouseX >= x && mouseX <= x + SLOT_SIZE
                && mouseY >= y && mouseY <= y + SLOT_SIZE;
        AnvilTheme.slotBg(ps, x, y, SLOT_SIZE, hover);
        if (border != 0) {
            int x2 = x + SLOT_SIZE;
            int y2 = y + SLOT_SIZE;
            GuiComponent.fill(ps, x, y, x2, y + 1, border);
            GuiComponent.fill(ps, x, y2 - 1, x2, y2, border);
            GuiComponent.fill(ps, x, y, x + 1, y2, border);
            GuiComponent.fill(ps, x2 - 1, y, x2, y2, border);
        }
    }

    private void drawPlaceholderIcon(PoseStack ps, int x, int y) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GuiComponent.fill(ps, x + 5, y + 5, x + SLOT_SIZE - 5, y + SLOT_SIZE - 5,
                0x40FFFFFF);
        RenderSystem.enableDepthTest();
    }

    private static Component buildSlotLine(ModifierIndex.LevelInfo li) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u00A7b")
                .append(Component.translatable(


                        "gui.anvilssearch.modifier.slots_label").getString())
                .append(" \u00A7f");
        boolean hasRealSlot = false;
        for (ModifierIndex.SlotRequirement sr : li.slots) {
            if (!"none".equals(sr.typeId)) { hasRealSlot = true; break; }
        }
        if (!hasRealSlot) {
            sb.append(Component.translatable(


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
        return Component.literal(sb.toString());
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

    private void drawSlotBg(PoseStack ps, int x, int y, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + SLOT_SIZE
                && mouseY >= y && mouseY <= y + SLOT_SIZE;
        AnvilTheme.slotBg(ps, x, y, SLOT_SIZE, hover);
    }

    private void drawItemIcon(PoseStack ps, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            int ox = x + (SLOT_SIZE - 16) / 2;
            int oy = y + (SLOT_SIZE - 16) / 2;
            mc.getItemRenderer().renderGuiItem(stack, ox, oy);
            mc.getItemRenderer().renderGuiItemDecorations(mc.font, stack, ox, oy, "");

            if (stack.getCount() > 1) {
                String s = String.valueOf(stack.getCount());
                Font font = mc.font;
                int tw = font.width(s);
                int tx = ox + 16 - tw - 1;
                int ty = oy + 16 - 9;
                // ★ 用 draw 而非 drawShadow，避免阴影层错位
                font.draw(ps, s, tx, ty, 0xFFFFFF);
            }
        } catch (Throwable ignored) {}
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
                requestCloseSlotDropdown();
            }
        }

        if (scrollBar.tryBeginDrag(mx, my)) return true;

        if (my >= toolbarY && my <= toolbarY + TOOLBAR_H) {
            if (mx >= toolbarStarX && mx <= toolbarStarX + toolbarStarW) {
                favoritesOnly = !favoritesOnly;
                applyFilter(panel.getSearchKeyword());
                return true;
            }
            if (mx >= toolbarIncX && mx <= toolbarIncX + toolbarIncW) {
                filterIncremental = !filterIncremental;
                applyFilter(panel.getSearchKeyword());
                return true;
            }
            if (mx >= toolbarUnlX && mx <= toolbarUnlX + toolbarUnlW) {
                filterUnlimited = !filterUnlimited;
                applyFilter(panel.getSearchKeyword());
                return true;
            }
            if (mx >= toolbarMetX && mx <= toolbarMetX + toolbarMetW) {
                filterReqMet = !filterReqMet;
                applyFilter(panel.getSearchKeyword());
                return true;
            }
            if (mx >= toolbarSlotX && mx <= toolbarSlotX + toolbarSlotW) {
                if (slotDropdownOpen) {
                    requestCloseSlotDropdown();
                } else {
                    slotDropdownOpen = true;
                    slotDropdownClosing = false;
                    AnvilWidgetAnimations.startPopupAnimation(SLOT_DROPDOWN_ANIM_KEY, true);
                }
                return true;
            }
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
                boolean multi = isMultiLevelEntry(e);
                boolean expanded = multi && expandedInList.contains(e.id);

                if (my >= rowY && my <= rowY + ROW_H) {
                    int starX = leftListX + 3;
                    if (mx >= starX - 2 && mx <= starX + STAR_W + 2) {
                        FavoritesStore.toggle(e.id);
                        AnvilWidgetAnimations.triggerStarPulse(
                                e.id != null ? e.id : String.valueOf(e.hashCode()));
                        if (favoritesOnly) applyFilter(panel.getSearchKeyword());
                        return true;
                    }
                    if (!multi) {
                        selected = e;
                        selectedIndex = -1;
                        rightScrollOffset = 0;
                        lastReqEntry = null;
                        lastReqIndex = -1;
                        lastReqItem = ItemStack.EMPTY;
                        return true;
                    }
                    if (expanded) expandedInList.remove(e.id);
                    else expandedInList.add(e.id);
                    selected = e;
                    selectedIndex = -1;
                    rightScrollOffset = 0;
                    lastReqEntry = null;
                    lastReqIndex = -1;
                    lastReqItem = ItemStack.EMPTY;
                    return true;
                }
                rowY += ROW_H;

                if (expanded) {
                    for (int i = 0; i < e.levels.size(); i++) {
                        if (my >= rowY && my <= rowY + SUB_ROW_H) {
                            selected = e;
                            selectedIndex = i;
                            rightScrollOffset = 0;
                            lastReqEntry = null;
                            lastReqIndex = -1;
                            lastReqItem = ItemStack.EMPTY;
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