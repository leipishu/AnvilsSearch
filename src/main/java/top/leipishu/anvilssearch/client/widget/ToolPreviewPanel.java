package top.leipishu.anvilssearch.client.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.library.tools.stat.IToolStat;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import top.leipishu.anvilssearch.data.material.MaterialDetail;
import top.leipishu.anvilssearch.data.tool.ToolDefinitionIndex;
import top.leipishu.anvilssearch.simulation.ToolPreviewRenderer;
import top.leipishu.anvilssearch.simulation.ToolSimulationModel;
import top.leipishu.tinkerssearch.client.gui.components.ScrollBar;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static top.leipishu.tinkerssearch.config.PanelConfig.SCROLL_BAR_PADDING;
import static top.leipishu.tinkerssearch.config.PanelConfig.SCROLL_BAR_WIDTH;

public class ToolPreviewPanel {

    private static final int ICON_SIZE = 16;
    private static final int LINE_H    = 11;
    private static final int TAG_H     = 14;
    private static final int TAG_GAP   = 3;

    private int x, y, w, h;

    private final ScrollBar scrollBar = new ScrollBar();
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;

    private List<Component> pendingTooltip = null;

    private final Set<Integer> expandedSlots = new HashSet<>();

    private static final class HeaderHit {
        int x, y, w, h, slotIndex;
        HeaderHit(int x, int y, int w, int h, int idx) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.slotIndex = idx;
        }
    }
    private final List<HeaderHit> headerHits = new ArrayList<>();

    public ToolPreviewPanel() {
        scrollBar.setOnOffsetChanged(v -> scrollOffset = v);
        scrollBar.setThumbMinHeight(16);
        scrollBar.setHoverExpandX(3);
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    public List<Component> getPendingTooltip() { return pendingTooltip; }

    public boolean isPointInside(double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        for (HeaderHit hit : headerHits) {
            if (mx >= hit.x && mx <= hit.x + hit.w
                    && my >= hit.y && my <= hit.y + hit.h) {
                if (expandedSlots.contains(hit.slotIndex)) {
                    expandedSlots.remove(hit.slotIndex);
                } else {
                    expandedSlots.add(hit.slotIndex);
                }
                return true;
            }
        }
        return false;
    }

    public void setSlotExpanded(int slotIndex, boolean expanded) {
        if (expanded) expandedSlots.add(slotIndex);
        else expandedSlots.remove(slotIndex);
    }

    public void clearExpandState() {
        expandedSlots.clear();
    }

    // ============================================================
    // ===== 渲染 =================================================
    // ============================================================

    public void render(PoseStack ps, Font font,
                       ToolSimulationModel model,
                       Map<Integer, MaterialDetail> cardDetails,
                       int mouseX, int mouseY) {

        pendingTooltip = null;
        headerHits.clear();

        GuiComponent.fill(ps, x, y, x + w, y + h, 0xFF181818);
        GuiComponent.fill(ps, x, y, x + w, y + 1, 0xFF333333);

        if (model.getSelectedTool() == null) {
            font.draw(ps, "\u00A77" + new TranslatableComponent(
                            "gui.anvilssearch.sim.no_tool").getString(),
                    x + 4, y + 4, 0x666666);
            return;
        }

        int areaH = h - 4;
        int areaW = w - 4;

        int totalH = computeContentHeight(font, model, cardDetails, areaW);
        maxScrollOffset = Math.max(0, totalH - areaH);
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;

        boolean hasScroll = maxScrollOffset > 0;
        int clipW = areaW - (hasScroll ? SCROLL_BAR_WIDTH + SCROLL_BAR_PADDING : 0);

        boolean scissorOk = ScissorHelper.enableScissor(x + 2, y + 2, clipW, areaH);
        try {
            if (scissorOk) RenderSystem.disableDepthTest();

            int cx = x + 4;
            int cy = y + 4 - scrollOffset;

            // ===== 工具预览 =====
            ItemStack stack = model.getPreviewStack();
            ToolPreviewRenderer.drawIcon(ps, stack, cx, cy);

            String toolName = getToolDisplayName(model.getSelectedTool());
            font.draw(ps, "\u00A7f" + toolName, cx + ICON_SIZE + 4, cy + 4, 0xFFFFFF);
            cy += ICON_SIZE + 6;

            // ===== 属性行 =====
            Object statsObj = model.getStats();
            if (statsObj instanceof StatsNBT stats) {
                for (Component line : collectStatLines(stats)) {
                    font.draw(ps, line, cx, cy, 0xCCCCCC);
                    cy += LINE_H;
                }
            }

            // ===== 工具级词条 =====
            List<ModifierEntry> toolTraits = model.getToolTraits();
            if (toolTraits != null && !toolTraits.isEmpty()) {
                // ★ 分隔线 1 只在有词条时画
                cy += 3;
                GuiComponent.fill(ps, cx, cy, cx + clipW - 4, cy + 1, 0xFF444444);
                cy += 4;

                font.draw(ps, "\u00A7b" + new TranslatableComponent(
                                "gui.anvilssearch.sim.tool_traits").getString(),
                        cx, cy, 0x55FFFF);
                cy += LINE_H;

                int tagX = cx;
                int maxW = clipW - 8;
                for (ModifierEntry e : toolTraits) {
                    Component nameBase = MaterialDetail.resolveTranslation(traitName(e));
                    int color = traitColor(traitName(e));
                    int level = readLevel(e);

                    Component display = nameBase;
                    if (level > 1) {
                        display = new TextComponent("").append(nameBase)
                                .append(new TextComponent(" " + level));
                    }

                    int tw = font.width(display) + 8;
                    if (tagX > cx && tagX + tw > cx + maxW) {
                        tagX = cx;
                        cy += TAG_H + TAG_GAP;
                    }
                    int bg = 0xFF000000 | (color & 0x303030);
                    int border = 0xFF000000 | color;
                    GuiComponent.fill(ps, tagX, cy, tagX + tw, cy + TAG_H, bg);
                    GuiComponent.fill(ps, tagX, cy, tagX + tw, cy + 1, border);
                    GuiComponent.fill(ps, tagX, cy + TAG_H - 1, tagX + tw, cy + TAG_H, border);
                    GuiComponent.fill(ps, tagX, cy, tagX + 1, cy + TAG_H, border);
                    GuiComponent.fill(ps, tagX + tw - 1, cy, tagX + tw, cy + TAG_H, border);
                    font.draw(ps, display, tagX + 4, cy + 3, color);

                    if (mouseX >= tagX && mouseX <= tagX + tw
                            && mouseY >= cy && mouseY <= cy + TAG_H) {
                        List<Component> tip = new ArrayList<>();
                        tip.add(MaterialDetail.resolveTranslation(display));
                        List<Component> desc = traitDescription(e);
                        if (desc.isEmpty()) {
                            tip.add(new TranslatableComponent("gui.anvilssearch.detail.no_desc"));
                        } else {
                            for (Component d : desc) {
                                tip.add(MaterialDetail.resolveTranslation(d));
                            }
                        }
                        pendingTooltip = tip;
                    }

                    tagX += tw + TAG_GAP;
                }
                cy += TAG_H + 2;
            }

            // ===== 部件卡片列表 =====
            boolean hasCards = cardDetails != null && !cardDetails.isEmpty();
            if (hasCards) {
                // ★ 分隔线 2 只在有卡片时画
                cy += 2;
                GuiComponent.fill(ps, cx, cy, cx + clipW - 4, cy + 1, 0xFF444444);
                cy += 4;

                for (int i = 0; i < model.getSlotCount(); i++) {
                    MaterialDetail d = cardDetails.get(i);
                    if (d == null) continue;

                    boolean expanded = expandedSlots.contains(i);
                    int cardH = d.measureHeight(font, clipW - 4, expanded);

                    headerHits.add(new HeaderHit(cx, cy, clipW - 4,
                            MaterialDetail.HEADER_H, i));

                    List<Component> tip = d.render(ps, font, cx, cy,
                            clipW - 4, mouseX, mouseY, expanded, true);
                    if (tip != null) pendingTooltip = tip;
                    cy += cardH + 4;
                }
            } else {
                cy += 2;
                font.draw(ps, "\u00A78" + new TranslatableComponent(
                                "gui.anvilssearch.sim.select_materials").getString(),
                        cx, cy, 0x666666);
            }
        } finally {
            if (scissorOk) {
                ScissorHelper.disableScissor();
                RenderSystem.enableDepthTest();
            }
        }

        if (hasScroll) {
            scrollBar.setBounds(x + w - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING,
                    y + 2, SCROLL_BAR_WIDTH, areaH);
            scrollBar.setRange(scrollOffset, maxScrollOffset);
            scrollBar.render(ps, mouseX, mouseY);
        }
    }

    // ============================================================
    // ===== 高度 =================================================
    // ============================================================

    private int computeContentHeight(Font font, ToolSimulationModel model,
                                     Map<Integer, MaterialDetail> cardDetails,
                                     int areaW) {
        int effectiveW = areaW - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;

        int totalH = 0;
        totalH += ICON_SIZE + 6;

        Object statsObj = model.getStats();
        if (statsObj instanceof StatsNBT stats) {
            totalH += collectStatLines(stats).size() * LINE_H;
        }

        List<ModifierEntry> toolTraits = model.getToolTraits();
        if (toolTraits != null && !toolTraits.isEmpty()) {
            totalH += 3 + 1 + 4;                 // 分隔线 1 + 边距
            totalH += LINE_H;                    // "Tool Traits" 标题
            totalH += measureToolTraitsHeight(font, toolTraits, effectiveW - 8);
            totalH += 2;
        }

        boolean hasCards = cardDetails != null && !cardDetails.isEmpty();
        if (hasCards) {
            totalH += 2 + 1 + 4;                 // 分隔线 2 + 边距
            for (int i = 0; i < model.getSlotCount(); i++) {
                MaterialDetail d = cardDetails.get(i);
                if (d == null) continue;
                boolean expanded = expandedSlots.contains(i);
                totalH += d.measureHeight(font, effectiveW, expanded) + 4;
            }
        } else {
            totalH += 2 + LINE_H;
        }
        return totalH;
    }

    private int measureToolTraitsHeight(Font font, List<ModifierEntry> toolTraits, int maxW) {
        if (toolTraits == null || toolTraits.isEmpty()) return 0;

        int rows = 1;
        int curW = 0;
        for (ModifierEntry e : toolTraits) {
            Component name = MaterialDetail.resolveTranslation(traitName(e));
            int level = readLevel(e);
            Component display = name;
            if (level > 1) {
                display = new TextComponent("").append(name)
                        .append(new TextComponent(" " + level));
            }
            int tw = font.width(display) + 8;
            if (curW > 0 && curW + tw > maxW) {
                rows++;
                curW = 0;
            }
            curW += tw + TAG_GAP;
        }

        return rows * TAG_H + Math.max(0, rows - 1) * TAG_GAP;
    }

    // ============================================================
    // ===== 属性行 ===============================================
    // ============================================================

    private static List<Component> collectStatLines(StatsNBT stats) {
        List<Component> out = new ArrayList<>();
        for (IToolStat<?> stat : ToolStats.getAllStats()) {
            if (!statsHas(stats, stat)) continue;
            Number v = readValue(stats, stat);
            if (v == null) continue;

            Component nameComp = statDisplayName(stat);
            String valueStr = formatValue(v);

            out.add(new TextComponent("")
                    .append(nameComp)
                    .append(new TextComponent(": \u00A7f" + valueStr)));
        }
        return out;
    }

    private static boolean statsHas(StatsNBT stats, IToolStat<?> stat) {
        for (Method m : stats.getClass().getMethods()) {
            String n = m.getName();
            if (!n.equals("contains") && !n.equals("hasStat")) continue;
            if (m.getParameterCount() != 1) continue;
            try {
                Object r = m.invoke(stats, stat);
                if (r instanceof Boolean b) return b;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static Number readValue(StatsNBT stats, IToolStat<?> stat) {
        for (Method m : stats.getClass().getMethods()) {
            String n = m.getName();
            if (!n.equals("getFloat") && !n.equals("getInt") && !n.equals("get")) continue;
            if (m.getParameterCount() != 1) continue;
            try {
                Object r = m.invoke(stats, stat);
                if (r instanceof Number num) return num;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Component statDisplayName(IToolStat<?> stat) {
        // 1) getDisplayName
        try {
            Method m = stat.getClass().getMethod("getDisplayName");
            Object v = m.invoke(stat);
            if (v instanceof Component c) {
                if (isTranslated(c)) return c;
            }
        } catch (Throwable ignored) {}

        // 2) getTranslationKey / getLocalizationKey
        for (String mn : new String[]{"getTranslationKey", "getLocalizationKey", "getLocalizedKey"}) {
            try {
                Method m = stat.getClass().getMethod(mn);
                Object v = m.invoke(stat);
                if (v instanceof String s && !s.isEmpty()) {
                    Component c = new TranslatableComponent(s);
                    if (isTranslated(c)) return c;
                }
            } catch (Throwable ignored) {}
        }

        // 3) 从 getName() 拿 ns:path
        String ns = null, path = null;
        try {
            Method m = stat.getClass().getMethod("getName");
            Object idObj = m.invoke(stat);
            if (idObj != null) {
                ns = invokeString(idObj, "getNamespace");
                path = invokeString(idObj, "getPath");
                if (ns == null || path == null) {
                    String s = String.valueOf(idObj);
                    int colon = s.indexOf(':');
                    if (colon > 0) {
                        ns = s.substring(0, colon);
                        path = s.substring(colon + 1);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 4) 用 ns:path 尝试翻译
        if (ns != null && path != null) {
            for (String k : new String[]{
                    "stat." + ns + "." + path,
                    "tool_stat." + ns + "." + path,
                    "toolstat." + ns + "." + path}) {
                Component c = new TranslatableComponent(k);
                if (isTranslated(c)) return c;
            }
            return new TextComponent("\u00A77" + prettifyPath(path));
        }

        // 5) 最终兜底：从 stat.toString() 里正则提取 namespace:path
        String s = String.valueOf(stat);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([a-z0-9_]+):([a-z0-9_/]+)")
                .matcher(s);
        if (matcher.find()) {
            String ns2 = matcher.group(1);
            String path2 = matcher.group(2);
            for (String k : new String[]{
                    "stat." + ns2 + "." + path2,
                    "tool_stat." + ns2 + "." + path2,
                    "toolstat." + ns2 + "." + path2}) {
                Component c = new TranslatableComponent(k);
                if (isTranslated(c)) return c;
            }
            return new TextComponent("\u00A77" + prettifyPath(path2));
        }

        return new TextComponent("\u00A77" + s);
    }

    private static String prettifyPath(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] w = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String x : w) {
            if (x.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(x.charAt(0)));
            if (x.length() > 1) sb.append(x.substring(1));
        }
        return sb.length() > 0 ? sb.toString() : path;
    }

    private static boolean isTranslated(Component c) {
        String s = c.getString();
        if (s == null || s.isEmpty()) return false;
        if (c instanceof TranslatableComponent tc) {
            return !s.equals(tc.getKey());
        }
        return true;
    }

    private static String invokeString(Object obj, String mn) {
        try {
            Method m = obj.getClass().getMethod(mn);
            Object v = m.invoke(obj);
            if (v instanceof String s) return s;
        } catch (Throwable ignored) {}
        return null;
    }

    private static String formatValue(Number v) {
        float f = v.floatValue();
        if (Math.abs(f - Math.round(f)) < 0.001f) return String.valueOf(Math.round(f));
        return String.format("%.2f", f);
    }

    // ============================================================
    // ===== 词条辅助 =============================================
    // ============================================================

    private static Component traitName(ModifierEntry e) {
        try {
            Object mod = e.getModifier();
            for (String mn : new String[]{"getDisplayName", "getColoredName", "getName"}) {
                try {
                    Method m = mod.getClass().getMethod(mn);
                    Object v = m.invoke(mod);
                    if (v instanceof Component c) return c;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return new TextComponent(String.valueOf(e.getModifier()));
    }

    private static int readLevel(ModifierEntry e) {
        try { return e.getLevel(); } catch (Throwable ignored) { return 1; }
    }

    private static List<Component> traitDescription(ModifierEntry e) {
        List<Component> out = new ArrayList<>();
        try {
            Object mod = e.getModifier();
            for (String mn : new String[]{"getDescriptionList", "getDescription"}) {
                try {
                    Method m = mod.getClass().getMethod(mn);
                    Object v = m.invoke(mod);
                    if (v instanceof List<?> list) {
                        for (Object o : list) if (o instanceof Component c) out.add(c);
                        break;
                    } else if (v instanceof Component c) {
                        out.add(c);
                        break;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static int traitColor(Component c) {
        try {
            TextColor tc = c.getStyle().getColor();
            if (tc != null) return tc.getValue();
        } catch (Throwable ignored) {}
        try {
            if (!c.getSiblings().isEmpty()) {
                for (Component s : c.getSiblings()) {
                    TextColor tc = s.getStyle().getColor();
                    if (tc != null) return tc.getValue();
                }
            }
        } catch (Throwable ignored) {}
        return 0xFFFFFF;
    }

    private static String getToolDisplayName(Object def) {
        if (def == null) return "";
        for (ToolDefinitionIndex.Entry e : ToolDefinitionIndex.get()) {
            if (e.definition == def) return e.getDisplayName();
        }
        return "";
    }

    // ============================================================
    // ===== 交互 =================================================
    // ============================================================

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!isPointInside(mx, my)) return false;
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

    public boolean tryBeginDrag(double mx, double my) {
        return scrollBar.tryBeginDrag(mx, my);
    }

    public boolean mouseDragged(double my) {
        return scrollBar.updateDrag(my);
    }

    public void mouseReleased() {
        scrollBar.endDrag();
    }

    public boolean isDragging() {
        return scrollBar.isDragging();
    }
}