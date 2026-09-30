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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    /** 图标循环周期（tick）：每秒切换一次。 */
    private static final int CYCLE_TICKS = 20;

    private static final String SLOT_NONE = "__none__";
    private static final int CARD_ACCENT_ERROR = 0xFFFF5555;

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
        allEntries = new ArrayList<>(ModifierIndex.getGrouped());
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

        Object container = makeContainer(tool);
        Object recipeForCheck = e.levels.get(0).recipe != null
                ? e.levels.get(0).recipe : e.recipe;
        Optional<Component> r = checkRequirements(recipeForCheck, container);

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


    /**
     * 生成材料说明。
     * <p>规则：
     * <ul>
     *   <li>每个槽位的候选材料用 "/" 连接，例如 "丝绢/蜘蛛丝"；</li>
     *   <li>相同候选组的槽位合并数量，例如 5 个 [丝绢] 槽位 → "丝绢 ×5"；</li>
     *   <li>不同候选组的槽位分别输出。</li>
     * </ul>
     * <p>注意：候选组内多个候选 ItemStack 代表"任选其一"，
     * 数量统计按 <b>槽位数 × 该槽位需求</b> 计算，不累加候选数。
     */
    @SuppressWarnings("unchecked")
    private static List<Component> buildMaterialLines(ModifierIndex.LevelInfo li) {
        List<Component> out = new ArrayList<>();
        if (li == null) return out;

        if (li.slotMaterials == null) {
            return li.materialLines;
        }

        // key = 候选组签名（按顺序拼接 stackKey）
        // value = 该组涉及的"槽位需求数量总和"
        Map<String, Integer> groupTotal = new LinkedHashMap<>();
        Map<String, List<ItemStack>> groupRep = new LinkedHashMap<>();

        for (List<ItemStack> candidates : li.slotMaterials) {
            if (candidates == null || candidates.isEmpty()) continue;

            StringBuilder sig = new StringBuilder();
            for (ItemStack s : candidates) {
                if (s == null) continue;
                sig.append(stackKeyOf(s)).append('|');
            }
            String key = sig.toString();

            // ★ 该槽位需要的数量：取第一个非空候选的 count
            //   同一槽位内候选的数量应一致（都表示"该槽位需要 N 个"），
            //   不累加候选数量，只取一个。
            int slotNeed = 1;
            for (ItemStack s : candidates) {
                if (s != null && !s.isEmpty()) {
                    slotNeed = Math.max(1, s.getCount());
                    break;
                }
            }

            groupTotal.merge(key, slotNeed, Integer::sum);
            groupRep.putIfAbsent(key, candidates);
        }

        if (groupTotal.isEmpty()) return li.materialLines;

        for (Map.Entry<String, Integer> e : groupTotal.entrySet()) {
            List<ItemStack> reps = groupRep.get(e.getKey());
            int total = e.getValue();

            StringBuilder names = new StringBuilder();
            for (int i = 0; i < reps.size(); i++) {
                if (i > 0) names.append('/');
                names.append(reps.get(i).getHoverName().getString());
            }

            out.add(new TextComponent("\u00A77"
                    + names.toString()
                    + (total > 1 ? " \u00D7" + total : "")));
        }
        return out;
    }

    private static String stackKeyOf(ItemStack stack) {
        try {
            String regName = stack.getItem().getRegistryName() != null
                    ? stack.getItem().getRegistryName().toString()
                    : stack.getItem().toString();
            String nbt = stack.getTag() != null ? stack.getTag().toString() : "";
            return regName + "|" + nbt;
        } catch (Throwable t) {
            return stack.toString();
        }
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
            if (slotDropdownOpen) renderSlotDropdown(ps, font, mouseX, mouseY);
            return;
        }

        int leftW  = (int) (totalW * 0.42f);
        int rightW = totalW - leftW - 4;
        int leftX  = contentLeft;
        int rightX = leftX + leftW + 4;

        renderLeft(ps, font, leftX, cardsTop, leftW, cardsH, mouseX, mouseY);
        renderRight(ps, font, rightX, cardsTop, rightW, cardsH, mouseX, mouseY);

        if (slotDropdownOpen) renderSlotDropdown(ps, font, mouseX, mouseY);
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

        toolbarStarX = starX;
        toolbarStarW = starW;
        toolbarIncX = incX;
        toolbarIncW = iconW;
        toolbarUnlX = unlX;
        toolbarUnlW = iconW;
        toolbarMetX = metX;
        toolbarMetW = iconW;
        toolbarSlotX = slotX;
        toolbarSlotW = slotW;

        searchBox.setBounds(searchX, y, searchW, TOOLBAR_H);
        searchBox.render(ps, mouseX, mouseY, font);

        boolean starHover = mouseX >= starX && mouseX <= starX + starW
                && mouseY >= y && mouseY <= y + TOOLBAR_H;
        String starLabel = favoritesOnly ? "\u2605" : "\u2606";
        AnvilTheme.button(ps, font, starX, y, starW, TOOLBAR_H,
                starLabel, starHover, favoritesOnly);

        boolean incHover = mouseX >= incX && mouseX <= incX + iconW
                && mouseY >= y && mouseY <= y + TOOLBAR_H;
        AnvilTheme.button(ps, font, incX, y, iconW, TOOLBAR_H,
                "\u26A1", incHover, filterIncremental);
        if (incHover) {
            List<Component> tip = new ArrayList<>();
            tip.add(new TranslatableComponent(
                    "gui.anvilssearch.modifier.filter.incremental_tip"));
            panel.setPendingTooltip(tip);
        }

        boolean unlHover = mouseX >= unlX && mouseX <= unlX + iconW
                && mouseY >= y && mouseY <= y + TOOLBAR_H;
        AnvilTheme.button(ps, font, unlX, y, iconW, TOOLBAR_H,
                "\u221E", unlHover, filterUnlimited);
        if (unlHover) {
            List<Component> tip = new ArrayList<>();
            tip.add(new TranslatableComponent(
                    "gui.anvilssearch.modifier.filter.unlimited_tip"));
            panel.setPendingTooltip(tip);
        }

        boolean metHover = mouseX >= metX && mouseX <= metX + iconW
                && mouseY >= y && mouseY <= y + TOOLBAR_H;
        AnvilTheme.button(ps, font, metX, y, iconW, TOOLBAR_H,
                "\u2713", metHover, filterReqMet);
        if (metHover) {
            List<Component> tip = new ArrayList<>();
            tip.add(new TranslatableComponent(
                    "gui.anvilssearch.modifier.filter.req_met_tip"));
            panel.setPendingTooltip(tip);
        }

        boolean slotHover = mouseX >= slotX && mouseX <= slotX + slotW
                && mouseY >= y && mouseY <= y + TOOLBAR_H;
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
        if (font.width(label) > slotW - 6) {
            label = font.plainSubstrByWidth(label, slotW - 10) + "...";
        }
        AnvilTheme.button(ps, font, slotX, y, slotW, TOOLBAR_H,
                label, slotHover, slotActive);
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
            font.draw(ps, new TranslatableComponent(
                            "gui.anvilssearch.modifier.empty").getString(),
                    innerX + 2, listTop + 4, AnvilTheme.TEXT_DIM);
            return;
        }

        int totalH = 0;
        for (ModifierIndex.Entry e : filtered) {
            totalH += ROW_H;
            if (isMultiLevelEntry(e) && expandedInList.contains(e.id)) {
                totalH += e.levels.size() * SUB_ROW_H;
            }
        }
        maxScrollOffset = Math.max(0, totalH - listH);
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;

        int scrollBarW = maxScrollOffset > 0 ? SCROLL_BAR_WIDTH + SCROLL_BAR_PADDING : 0;
        int clipW = listW - scrollBarW;

        boolean scissorOk = ScissorHelper.enableScissor(innerX, listTop, clipW, listH);
        try {
            if (scissorOk) RenderSystem.disableDepthTest();

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

                if (expanded) {
                    for (int i = 0; i < e.levels.size(); i++) {
                        ModifierIndex.LevelInfo li = e.levels.get(i);
                        drawSubRow(ps, font, innerX, rowY, clipW, e, li,
                                selected == e && selectedIndex == i,
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

    private void renderSlotDropdown(PoseStack ps, Font font,
                                    int mouseX, int mouseY) {
        if (slotTypes.isEmpty()) return;

        int w = 120;
        int h = DROP_HEADER_H + slotTypes.size() * DROP_ITEM_H + 6;

        int x = toolbarSlotX;
        if (x + w > contentRightEdge) x = contentRightEdge - w;
        if (x < 0) x = 0;
        int y = toolbarY + TOOLBAR_H + 2;

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

    private void drawEntryRow(PoseStack ps, Font font,
                              int x, int y, int clipW, ModifierIndex.Entry e,
                              boolean multi, boolean expanded, boolean lightning,
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
                    tip.add(new TextComponent("\u00A7e" + new TranslatableComponent(
                            "gui.anvilssearch.modifier.unlimited_title").getString()));
                    tip.add(new TextComponent("\u00A77" + new TranslatableComponent(
                            "gui.anvilssearch.modifier.unlimited_hint").getString()));
                } else {
                    tip.add(new TextComponent("\u00A7e" + new TranslatableComponent(
                            "gui.anvilssearch.modifier.incremental_title").getString()));
                    tip.add(new TextComponent("\u00A77" + new TranslatableComponent(
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

        if (sel) {
            GuiComponent.fill(ps, x, y, x + clipW, y + SUB_ROW_H,
                    selectedRowBg(e.color));
        } else if (hover) {
            GuiComponent.fill(ps, x, y, x + clipW, y + SUB_ROW_H,
                    AnvilTheme.ROW_HOVER);
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

        // ★ 图标循环：每秒切换一次候选
        long tick = 0;
        if (Minecraft.getInstance().level != null) {
            tick = Minecraft.getInstance().level.getGameTime();
        }
        int cycle = (int) (tick / CYCLE_TICKS);

        if (selLi != null && selLi.slotMaterials != null) {
            for (int i = 0; i < 5; i++) {
                List<ItemStack> sl = selLi.slotMaterials[i];
                if (sl == null || sl.isEmpty()) continue;
                int idx = cycle % sl.size();
                matIcons[i] = sl.get(idx);
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

        String titleText = selLi.displayName.getString();
        MutableComponent titleComp = new TextComponent(titleText)
                .withStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(selected.color))
                        .withBold(true));
        int titleW = font.width(titleComp);
        int titleX = x + (w - titleW) / 2;
        int titleY = infoTop + 4;
        font.draw(ps, titleComp, titleX, titleY, selected.color);

        int afterTitleY = titleY + LINE_H + 6;

        ItemStack currentAnvilItem = AnvilSlotAccess.getCenterItem();
        if (currentAnvilItem == null) currentAnvilItem = ItemStack.EMPTY;

        boolean needRecheck = (selected != lastReqEntry)
                || (selectedIndex != lastReqIndex)
                || !ItemStack.isSameItemSameTags(currentAnvilItem, lastReqItem);

        if (needRecheck) {
            Object container = makeContainer(currentAnvilItem);
            Object recipeForCheck = selLi.recipe != null ? selLi.recipe : selected.recipe;
            lastReqResult = checkRequirements(recipeForCheck, container);
            lastReqEntry = selected;
            lastReqIndex = selectedIndex;
            lastReqItem = currentAnvilItem.copy();
        }

        Optional<Component> reqCheck = lastReqResult;

        if (reqCheck != null && reqCheck.isPresent()) {
            Component msg = reqCheck.get();
            String msgStr = msg.getString();
            String titleStr = new TranslatableComponent(
                    "gui.anvilssearch.modifier.requirements_error").getString();

            List<Component> errLines = new ArrayList<>();
            if (msgStr != null && !msgStr.isEmpty() && !msgStr.equals(titleStr)) {
                List<Component> wrapped = wrapComponent(font, msg, maxTextW);
                for (Component wc : wrapped) {
                    errLines.add(new TextComponent("\u00A77" + wc.getString()));
                }
            }

            cards.add(new Card(
                    CARD_ACCENT_ERROR,
                    "\u00A7c" + titleStr,
                    errLines));
        }

        List<Component> c2 = new ArrayList<>();
        c2.add(buildSlotLine(selLi));

        // ★ 黄色提示也走 wrap，避免被卡片区域切割
        if (selLi.kind == ModifierIndex.RecipeKind.INCREMENTAL
                && selLi.amountPerInput > 0 && selLi.neededPerLevel > 0) {
            Component hint = new TextComponent("\u00A7e"
                    + new TranslatableComponent(
                    "gui.anvilssearch.modifier.incremental_summary",
                    selLi.amountPerInput, selLi.neededPerLevel).getString());
            c2.addAll(wrapComponent(font, hint, maxTextW));
        } else if (selLi.kind == ModifierIndex.RecipeKind.UNLIMITED) {
            Component hint = new TextComponent("\u00A7e"
                    + new TranslatableComponent(
                    "gui.anvilssearch.modifier.unlimited_hint").getString());
            c2.addAll(wrapComponent(font, hint, maxTextW));
        }

        if (selLi.variant != null) {
            Component varLine = new TextComponent("\u00A77"
                    + new TranslatableComponent(
                    "gui.anvilssearch.modifier.variant").getString()
                    + ": " + selLi.variant.getString());
            c2.addAll(wrapComponent(font, varLine, maxTextW));
        }

        // ★ 材料文本合并
        List<Component> materialLines = buildMaterialLines(selLi);

        if (!materialLines.isEmpty()) {
            c2.add(new TextComponent(""));
            for (Component line : materialLines) {
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

    // ============================================================
    // ===== 前置条件动态检查 =====================================
    // ============================================================

    private static Object makeContainer(ItemStack toolStack) {
        if (toolStack == null || toolStack.isEmpty()) return null;
        try {
            Class<?> modifiableClass = Class.forName(
                    "slimeknights.tconstruct.library.tools.item.IModifiable");
            if (!modifiableClass.isInstance(toolStack.getItem())) return null;

            Class<?> tsClass = Class.forName(
                    "slimeknights.tconstruct.library.tools.nbt.ToolStack");
            Method from = tsClass.getMethod("from", ItemStack.class);
            from.setAccessible(true);
            Object tool = from.invoke(null, toolStack);
            if (tool == null) return null;

            Class<?> containerClass = Class.forName(
                    "slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer");
            return Proxy.newProxyInstance(
                    containerClass.getClassLoader(),
                    new Class<?>[]{containerClass},
                    (proxy, method, args) -> {
                        String mn = method.getName();
                        if ("getTinkerable".equals(mn) && method.getParameterCount() == 0)
                            return tool;
                        if ("getTinkerableStack".equals(mn) && method.getParameterCount() == 0)
                            return toolStack;
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return false;
                        if (rt == int.class) return 0;
                        if (rt == long.class) return 0L;
                        if (rt == float.class) return 0f;
                        if (rt == double.class) return 0d;
                        return null;
                    });
        } catch (Throwable t) {
            return null;
        }
    }

    private static Optional<Component> checkRequirements(Object recipe, Object container) {
        if (recipe == null || container == null) return null;

        try {
            Class<?> containerClass = Class.forName(
                    "slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer");
            if (!containerClass.isInstance(container)) return null;

            Method validateMethod = null;
            for (Method m : recipe.getClass().getMethods()) {
                if (!"getValidatedResult".equals(m.getName())) continue;
                if (m.getParameterCount() == 2) { validateMethod = m; break; }
            }
            if (validateMethod == null) {
                for (Method m : recipe.getClass().getMethods()) {
                    if (!"getValidatedResult".equals(m.getName())) continue;
                    if (m.getParameterCount() == 1) { validateMethod = m; break; }
                }
            }
            if (validateMethod == null) return null;

            Object registryAccess = Minecraft.getInstance().level != null
                    ? Minecraft.getInstance().level.registryAccess() : null;

            validateMethod.setAccessible(true);
            Object result = validateMethod.getParameterCount() == 2
                    ? validateMethod.invoke(recipe, container, registryAccess)
                    : validateMethod.invoke(recipe, container);

            if (result == null) return Optional.empty();

            Method isSuccessMethod = null;
            for (Method m : result.getClass().getMethods()) {
                if ("isSuccess".equals(m.getName()) && m.getParameterCount() == 0) {
                    isSuccessMethod = m; break;
                }
            }
            if (isSuccessMethod == null) return null;

            boolean success = (boolean) isSuccessMethod.invoke(result);
            if (success) return Optional.empty();

            Component message = extractFailureMessage(result);
            if (message != null) return Optional.of(message);
            return Optional.of(new TextComponent(""));
        } catch (Throwable t) {
            return null;
        }
    }

    private static Component extractFailureMessage(Object result) {
        try {
            Method m = result.getClass().getMethod("getMessage");
            m.setAccessible(true);
            Object v = m.invoke(result);
            if (v instanceof Component c) return c;
            if (v instanceof String s && !s.isEmpty()) return new TextComponent(s);
        } catch (Throwable ignored) {}

        try {
            Method m = result.getClass().getMethod("getMessageComponent");
            m.setAccessible(true);
            Object v = m.invoke(result);
            if (v instanceof Component c) return c;
            if (v instanceof String s && !s.isEmpty()) return new TextComponent(s);
        } catch (Throwable ignored) {}

        try {
            Method m = result.getClass().getMethod("getError");
            m.setAccessible(true);
            Object v = m.invoke(result);
            if (v instanceof Component c) return c;
            if (v instanceof String s && !s.isEmpty()) return new TextComponent(s);
        } catch (Throwable ignored) {}

        for (String fname : new String[]{"message", "error", "failureMessage", "reason"}) {
            try {
                Field f = result.getClass().getDeclaredField(fname);
                f.setAccessible(true);
                Object v = f.get(result);
                if (v instanceof Component c) return c;
                if (v instanceof String s && !s.isEmpty()) return new TextComponent(s);
            } catch (Throwable ignored) {}
        }

        return null;
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

    /**
     * 按宽度自动换行。
     * 保留 § 颜色代码：换行后每行开头重新附加当前生效的颜色。
     */
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

        // 当前生效的颜色代码（如 "§7"）
        String currentColor = "";

        int start = 0;
        int len = text.length();
        while (start < len) {
            int end = start;
            int lastSpace = -1;

            // 本行起点附加当前颜色
            String linePrefix = currentColor;

            while (end < len) {
                char c = text.charAt(end);

                // 遇到 §，记录颜色代码并跳过
                if (c == '\u00A7' && end + 1 < len) {
                    char code = Character.toLowerCase(text.charAt(end + 1));
                    if (isColorCode(code)) {
                        currentColor = "\u00A7" + text.charAt(end + 1);
                    }
                    if (font.width(linePrefix
                            + text.substring(start, Math.min(end + 2, len))) > maxW
                            && end > start) {
                        break;
                    }
                    end += 2;
                    continue;
                }

                if (font.width(linePrefix + text.substring(start, end + 1)) > maxW) {
                    break;
                }
                if (c == ' ') {
                    lastSpace = end;
                }
                end++;
            }

            if (end >= len) {
                out.add(new TextComponent(linePrefix + text.substring(start)));
                break;
            }

            int cut;
            if (lastSpace > start) {
                cut = lastSpace + 1;
            } else {
                cut = end;
            }
            if (cut <= start) cut = start + 1;

            // 取本行文本（含内部颜色代码），去掉行尾空白
            String line = text.substring(start, cut);
            out.add(new TextComponent(linePrefix + line));
            start = cut;
        }
        return out;
    }

    /** 判断是否是 § 颜色/格式代码（0-9 a-f k-o r）。 */
    private static boolean isColorCode(char c) {
        c = Character.toLowerCase(c);
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'k' && c <= 'o')
                || c == 'r';
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
                slotDropdownOpen = !slotDropdownOpen;
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