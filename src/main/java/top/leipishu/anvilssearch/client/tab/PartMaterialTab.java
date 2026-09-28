package top.leipishu.anvilssearch.client.tab;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import top.leipishu.anvilssearch.client.AnvilSidebarPanel;
import top.leipishu.anvilssearch.client.AnvilTab;
import top.leipishu.anvilssearch.client.theme.AnvilTheme;
import top.leipishu.anvilssearch.data.MaterialDetail;
import top.leipishu.anvilssearch.data.MaterialDetailBuilder;
import top.leipishu.anvilssearch.data.PartMaterialIndex;
import top.leipishu.tinkerssearch.client.gui.components.ScrollBar;
import top.leipishu.tinkerssearch.client.gui.components.SearchBox;
import top.leipishu.tinkerssearch.client.gui.components.SearchBoxStyle;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch.PinyinResult;

import java.util.*;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

public class PartMaterialTab implements AnvilTab {

    private static final int ICON_SIZE       = 16;
    private static final int DETAIL_INDENT   = 12;
    private static final int PART_SEARCH_H   = 16;
    private static final int PART_SEARCH_GAP = 4;

    private final AnvilSidebarPanel panel;

    private List<Row> rows = new ArrayList<>();
    private final Set<ResourceLocation> expanded = new HashSet<>();

    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private final ScrollBar scrollBar = new ScrollBar();

    private String lastKeyword = "";
    private boolean dataDirty = true;

    private final Map<ResourceLocation, SearchBox> partSearchBoxes = new HashMap<>();
    private final Map<ResourceLocation, String> partSearchKeywords = new HashMap<>();
    private ResourceLocation focusedPartSearch = null;

    private final Map<String, MaterialDetail> expandedDetails = new HashMap<>();

    private static final class Hit {
        int x, y, w, h;
        PartMaterialIndex.PartEntry part;
        PartMaterialIndex.Entry material;
        Hit(int x, int y, int w, int h,
            PartMaterialIndex.PartEntry p, PartMaterialIndex.Entry m) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.part = p; this.material = m;
        }
    }
    private final List<Hit> materialHits = new ArrayList<>();

    private static final class PartSearchHit {
        int x, y, w, h;
        ResourceLocation partId;
        PartSearchHit(int x, int y, int w, int h, ResourceLocation id) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.partId = id;
        }
    }
    private final List<PartSearchHit> partSearchHits = new ArrayList<>();

    public PartMaterialTab(AnvilSidebarPanel panel) {
        this.panel = panel;
        this.scrollBar.setOnOffsetChanged(v -> scrollOffset = v);
        this.scrollBar.setThumbMinHeight(20);
        this.scrollBar.setHoverExpandX(3);
    }

    @Override public Component getLabel() {
        return new TranslatableComponent("gui.anvilssearch.tab.parts");
    }
    @Override public void onActivate() { dataDirty = true; }

    @Override public void onSearchChanged(String keyword) {
        this.lastKeyword = keyword == null ? "" : keyword;
        rebuildRows();
    }

    @Override public boolean wantsSearchBox() { return true; }

    @Override public boolean isAnySearchFocused() {
        return focusedPartSearch != null;
    }

    @Override public void onExternalSearchFocus() {
        if (focusedPartSearch != null) {
            SearchBox sb = partSearchBoxes.get(focusedPartSearch);
            if (sb != null) sb.setFocused(false);
            focusedPartSearch = null;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (focusedPartSearch == null) return false;
        SearchBox sb = partSearchBoxes.get(focusedPartSearch);
        if (sb == null) { focusedPartSearch = null; return false; }

        if (sb.keyPressed(keyCode, scanCode, mods)) return true;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            sb.setFocused(false);
            focusedPartSearch = null;
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (focusedPartSearch == null) return false;
        SearchBox sb = partSearchBoxes.get(focusedPartSearch);
        if (sb == null) return false;
        return sb.charTyped(c, mods);
    }

    private static String detailKey(ResourceLocation partId, MaterialId mat) {
        return partId + "|" + mat;
    }

    private SearchBox getOrCreateSearchBox(ResourceLocation partId) {
        return partSearchBoxes.computeIfAbsent(partId, k -> {
            SearchBox sb = new SearchBox(SearchBoxStyle.panel());
            sb.setHintText(new TranslatableComponent(
                    "gui.anvilssearch.parts.search_material_hint"));
            sb.setOnTextChanged(t -> partSearchKeywords.put(k, t == null ? "" : t));
            return sb;
        });
    }

    // ============================================================
    // ===== 数据 =================================================
    // ============================================================

    private void rebuildRows() {
        dataDirty = false;
        String kw = lastKeyword.trim().toLowerCase(Locale.ROOT);

        List<Row> built = new ArrayList<>();
        for (PartMaterialIndex.PartEntry pe : PartMaterialIndex.get()) {
            Row row = new Row();
            row.partId    = pe.partId;
            row.display   = pe.getDisplayName();
            row.path      = pe.partId.getPath();
            row.itemStack = new ItemStack(pe.partItem);
            row.materials = pe.materials;
            row.partEntry = pe;

            if (!kw.isEmpty() && !rowMatchesKeyword(row, kw)) continue;
            built.add(row);
        }

        built.sort(Comparator.comparing(r -> r.display, String.CASE_INSENSITIVE_ORDER));
        this.rows = built;
        this.scrollOffset = 0;
    }

    private static boolean rowMatchesKeyword(Row r, String kw) {
        if (r.display.toLowerCase(Locale.ROOT).contains(kw)) return true;
        if (r.path.toLowerCase(Locale.ROOT).contains(kw)) return true;
        try {
            PinyinResult py = PinyinSearch.getPinyin(r.display);
            if (py.fullPinyin.contains(kw) || py.initials.contains(kw)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean materialMatchesKeyword(PartMaterialIndex.Entry m, String kw) {
        String name = m.getDisplayName();
        if (name.toLowerCase(Locale.ROOT).contains(kw)) return true;
        if (m.registryPath != null && m.registryPath.toLowerCase(Locale.ROOT).contains(kw))
            return true;
        try {
            PinyinResult py = PinyinSearch.getPinyin(name);
            if (py.fullPinyin.contains(kw) || py.initials.contains(kw)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private List<PartMaterialIndex.Entry> filterMaterials(
            List<PartMaterialIndex.Entry> materials, String kw) {
        if (kw == null || kw.isEmpty()) return materials;
        List<PartMaterialIndex.Entry> result = new ArrayList<>();
        for (PartMaterialIndex.Entry m : materials) {
            if (materialMatchesKeyword(m, kw)) result.add(m);
        }
        return result;
    }

    private static final class Row {
        ResourceLocation partId;
        String display, path;
        ItemStack itemStack;
        List<PartMaterialIndex.Entry> materials;
        PartMaterialIndex.PartEntry partEntry;
    }

    // ============================================================
    // ===== 渲染 =================================================
    // ============================================================

    @Override
    public void renderContent(PoseStack ps, Font font,
                              int mouseX, int mouseY, float partialTick,
                              int px, int py, int pw, int ph,
                              int contentTop, int contentBottom) {

        if (dataDirty) rebuildRows();

        int contentLeft  = px + 5;
        int contentRight = px + pw - 5;
        int areaTop      = py + contentTop;
        int areaH        = (py + contentBottom) - areaTop;

        // ★ 统一 section 背景
        AnvilTheme.section(ps, contentLeft, areaTop,
                contentRight - contentLeft, areaH);

        // section 内 padding
        int innerLeft   = contentLeft + AnvilTheme.PAD_S;
        int innerTop    = areaTop + AnvilTheme.PAD_S;
        int innerRight  = contentRight - AnvilTheme.PAD_S
                - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING - 2;
        int innerW      = innerRight - innerLeft;
        int innerH      = areaH - AnvilTheme.PAD_S * 2;
        int detailW     = innerW - DETAIL_INDENT;

        materialHits.clear();
        partSearchHits.clear();

        int totalH = computeTotalHeight(font, detailW);
        maxScrollOffset = Math.max(0, totalH - innerH);
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;

        if (rows.isEmpty()) {
            font.draw(ps, new TranslatableComponent(
                            "gui.anvilssearch.parts.empty").getString(),
                    innerLeft + 2, innerTop + 4, AnvilTheme.TEXT_DIM);
            renderScrollBar(ps, innerRight + 2, innerTop, innerH, mouseX, mouseY);
            return;
        }

        boolean scissorOk = ScissorHelper.enableScissor(
                innerLeft, innerTop, innerW, innerH);
        try {
            if (scissorOk) RenderSystem.disableDepthTest();

            int y = innerTop - scrollOffset;

            for (Row r : rows) {
                boolean isExpanded = expanded.contains(r.partId);
                boolean hover = mouseY >= y && mouseY <= y + AnvilTheme.ROW_H
                        && mouseX >= innerLeft && mouseX <= innerRight;

                drawPartRow(ps, font, innerLeft, y, innerW, r, hover, isExpanded);
                y += AnvilTheme.ROW_H;

                if (isExpanded) {
                    SearchBox sb = getOrCreateSearchBox(r.partId);
                    int sbX = innerLeft + DETAIL_INDENT;
                    int sbW = innerW - DETAIL_INDENT;
                    sb.setBounds(sbX, y, sbW, PART_SEARCH_H);
                    sb.render(ps, mouseX, mouseY, font);

                    partSearchHits.add(new PartSearchHit(sbX, y, sbW,
                            PART_SEARCH_H, r.partId));

                    y += PART_SEARCH_H + PART_SEARCH_GAP;

                    String matKw = partSearchKeywords
                            .getOrDefault(r.partId, "").trim().toLowerCase(Locale.ROOT);
                    List<PartMaterialIndex.Entry> visible = filterMaterials(r.materials, matKw);

                    for (PartMaterialIndex.Entry m : visible) {
                        boolean mHover = mouseY >= y && mouseY <= y + AnvilTheme.SUB_ROW_H
                                && mouseX >= innerLeft && mouseX <= innerRight;
                        String key = detailKey(r.partId, m.id);
                        MaterialDetail detail = expandedDetails.get(key);

                        drawMaterialRow(ps, font, innerLeft + DETAIL_INDENT, y,
                                innerW - DETAIL_INDENT, m, mHover, detail != null);

                        materialHits.add(new Hit(innerLeft + DETAIL_INDENT, y,
                                innerW - DETAIL_INDENT, AnvilTheme.SUB_ROW_H, r.partEntry, m));

                        y += AnvilTheme.SUB_ROW_H;

                        if (detail != null) {
                            // ★ 统一卡片底 + 左侧金色强调条，然后覆盖 MaterialDetail 头部
                            int cardH = detail.measureHeight(font, detailW, true);
                            AnvilTheme.cardBg(ps, innerLeft + DETAIL_INDENT, y,
                                    detailW, cardH, AnvilTheme.ACCENT);

                            // 让 MaterialDetail 的绘制从卡片右侧起画（跳过我们画的背景）
                            List<Component> tip = detail.render(ps, font,
                                    innerLeft + DETAIL_INDENT, y, detailW,
                                    mouseX, mouseY, true, false);
                            if (tip != null && !tip.isEmpty()) {
                                panel.setPendingTooltip(tip);
                            }
                            y += cardH + AnvilTheme.CARD_GAP;
                        }
                    }
                    y += 4;
                }
                y += 2;
            }
        } finally {
            if (scissorOk) {
                ScissorHelper.disableScissor();
                RenderSystem.enableDepthTest();
            }
        }

        renderScrollBar(ps, innerRight + 2, innerTop, innerH, mouseX, mouseY);
    }

    private int computeTotalHeight(Font font, int detailW) {
        int totalH = 0;
        for (Row r : rows) {
            totalH += AnvilTheme.ROW_H;
            if (expanded.contains(r.partId)) {
                totalH += PART_SEARCH_H + PART_SEARCH_GAP;

                String matKw = partSearchKeywords
                        .getOrDefault(r.partId, "").trim().toLowerCase(Locale.ROOT);
                for (PartMaterialIndex.Entry m : filterMaterials(r.materials, matKw)) {
                    totalH += AnvilTheme.SUB_ROW_H;
                    String key = detailKey(r.partId, m.id);
                    MaterialDetail d = expandedDetails.get(key);
                    if (d != null) {
                        totalH += d.measureHeight(font, detailW, true)
                                + AnvilTheme.CARD_GAP;
                    }
                }
                totalH += 4;
            }
            totalH += 2;
        }
        return totalH;
    }

    // ============================================================
    // ===== 行绘制（★ 统一风格）=================================
    // ============================================================

    private void drawPartRow(PoseStack ps, Font font,
                             int x, int y, int w, Row r,
                             boolean hover, boolean expanded) {
        AnvilTheme.row(ps, x, y, w, AnvilTheme.ROW_H, hover, false);
        if (expanded) {
            AnvilTheme.rowAccentBar(ps, x, y, AnvilTheme.ROW_H, AnvilTheme.ACCENT);
        }

        int iconY = y + (AnvilTheme.ROW_H - ICON_SIZE) / 2;
        try {
            Minecraft.getInstance().getItemRenderer().renderGuiItem(r.itemStack, x + 4, iconY);
        } catch (Throwable ignored) {}

        int textY = AnvilTheme.centeredTextY(y, AnvilTheme.ROW_H, font);
        String name = r.partEntry != null ? r.partEntry.getDisplayName() : r.display;
        font.draw(ps, name, x + 4 + ICON_SIZE + 6, textY,
                hover || expanded ? AnvilTheme.TEXT_PRIMARY : AnvilTheme.TEXT_PRIMARY);

        String count = "(" + r.materials.size() + ")";
        int cw = font.width(count);
        font.draw(ps, count, x + w - cw - 16, textY, AnvilTheme.TEXT_MUTED);

        String arrow = expanded ? "\u25BC" : "\u25B6";
        font.draw(ps, arrow, x + w - 12, textY,
                expanded ? AnvilTheme.ACCENT_SOFT : AnvilTheme.TEXT_MUTED);
    }

    private void drawMaterialRow(PoseStack ps, Font font,
                                 int x, int y, int w,
                                 PartMaterialIndex.Entry m, boolean hover,
                                 boolean isOpen) {
        AnvilTheme.row(ps, x, y, w, AnvilTheme.SUB_ROW_H, hover || isOpen, false);
        int textY = AnvilTheme.centeredTextY(y, AnvilTheme.SUB_ROW_H, font);
        font.draw(ps, m.getDisplayName(), x + 6, textY,
                isOpen ? AnvilTheme.ACCENT_SOFT
                        : (hover ? AnvilTheme.TEXT_PRIMARY : AnvilTheme.TEXT_SECONDARY));

        String arrow = isOpen ? "\u25BC" : "\u25B6";
        font.draw(ps, arrow, x + w - 12, textY,
                isOpen ? AnvilTheme.ACCENT : AnvilTheme.TEXT_MUTED);
    }

    private void renderScrollBar(PoseStack ps, int x, int y, int h,
                                 int mouseX, int mouseY) {
        scrollBar.setBounds(x, y, SCROLL_BAR_WIDTH, h);
        scrollBar.setRange(scrollOffset, maxScrollOffset);
        scrollBar.render(ps, mouseX, mouseY);
    }

    // ============================================================
    // ===== 交互（保持不变）======================================
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (scrollBar.tryBeginDrag(mx, my)) return true;

        for (PartSearchHit h : partSearchHits) {
            if (mx >= h.x && mx <= h.x + h.w && my >= h.y && my <= h.y + h.h) {
                SearchBox sb = getOrCreateSearchBox(h.partId);
                sb.mouseClicked(mx, my, button);
                sb.setFocused(true);
                focusedPartSearch = h.partId;
                panel.setSearchFocused(false);
                return true;
            }
        }

        if (focusedPartSearch != null) {
            SearchBox sb = partSearchBoxes.get(focusedPartSearch);
            if (sb != null) sb.setFocused(false);
            focusedPartSearch = null;
        }

        for (Hit h : materialHits) {
            if (mx >= h.x && mx <= h.x + h.w && my >= h.y && my <= h.y + h.h) {
                toggleDetail(h.part, h.material);
                return true;
            }
        }

        int contentTop = SEARCH_BOX_Y + SEARCH_BOX_H + 4;
        int y = contentTop - scrollOffset;
        for (Row r : rows) {
            if (my >= y && my <= y + AnvilTheme.ROW_H) {
                toggleExpanded(r.partId);
                return true;
            }
            y += AnvilTheme.ROW_H;
            if (expanded.contains(r.partId)) {
                y += PART_SEARCH_H + PART_SEARCH_GAP;
                String matKw = partSearchKeywords
                        .getOrDefault(r.partId, "").trim().toLowerCase(Locale.ROOT);
                for (PartMaterialIndex.Entry m : filterMaterials(r.materials, matKw)) {
                    y += AnvilTheme.SUB_ROW_H;
                    String key = detailKey(r.partId, m.id);
                    MaterialDetail d = expandedDetails.get(key);
                    if (d != null) {
                        int detailW = panel.getPanelWidth() - 5 - 5
                                - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING
                                - DETAIL_INDENT;
                        y += d.measureHeight(Minecraft.getInstance().font,
                                detailW, true) + AnvilTheme.CARD_GAP;
                    }
                }
                y += 4;
            }
            y += 2;
        }
        return false;
    }

    private void toggleDetail(PartMaterialIndex.PartEntry part,
                              PartMaterialIndex.Entry material) {
        String key = detailKey(part.partId, material.id);
        if (expandedDetails.containsKey(key)) {
            expandedDetails.remove(key);
            return;
        }

        MaterialStatsId st = null;
        try {
            if (part.partItem instanceof slimeknights.tconstruct.library.tools.part.IMaterialItem mi) {
                st = top.leipishu.tinkerssearch.recipe.MaterialCompatibility.inferStatType(mi);
            }
        } catch (Throwable ignored) {}

        MaterialDetail d = MaterialDetailBuilder.build(material.id, st, part.partItem);
        if (d != null) expandedDetails.put(key, d);
    }

    private void toggleExpanded(ResourceLocation partId) {
        if (!expanded.remove(partId)) {
            expanded.add(partId);
        } else {
            String prefix = partId + "|";
            expandedDetails.keySet().removeIf(k -> k.startsWith(prefix));

            if (partId.equals(focusedPartSearch)) {
                SearchBox sb = partSearchBoxes.get(partId);
                if (sb != null) sb.setFocused(false);
                focusedPartSearch = null;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScrollOffset <= 0) return false;
        int no = scrollOffset - (int) (delta * SCROLL_SPEED);
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