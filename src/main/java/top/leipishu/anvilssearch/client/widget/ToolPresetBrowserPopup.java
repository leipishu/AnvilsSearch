package top.leipishu.anvilssearch.client.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;
import top.leipishu.anvilssearch.client.theme.AnvilTheme;
import top.leipishu.anvilssearch.data.tool.ToolDefinitionIndex;
import top.leipishu.anvilssearch.simulation.ToolPreset;
import top.leipishu.anvilssearch.simulation.ToolPresetStore;
import top.leipishu.anvilssearch.simulation.ToolStatsCalculator;
import top.leipishu.tinkerssearch.client.gui.components.CardBackground;
import top.leipishu.tinkerssearch.client.gui.components.ScrollBar;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;
import top.leipishu.anvilssearch.client.animation.controller.AnvilWidgetAnimations;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static top.leipishu.tinkerssearch.config.PanelConfig.SCROLL_BAR_PADDING;
import static top.leipishu.tinkerssearch.config.PanelConfig.SCROLL_BAR_WIDTH;

public class ToolPresetBrowserPopup {

    public interface OnPickPreset      { void onPick(ToolPreset preset); }
    public interface OnClipboardImport { void onImport(); }
    public interface OnFileImport      { void onImport(); }

    private static final int ROW_H           = 28;
    private static final int PAD             = 6;
    private static final int ICON_SIZE       = 16;
    private static final int BTN_H           = 18;
    private static final int BTN_W           = 76;
    private static final int BTN_GAP         = 6;

    private static final int DELETE_BTN_SIZE = 14;
    private static final int DELETE_BTN_PAD  = 4;

    /** 分割线到列表顶部的间距（越小列表越靠上）。 */
    private static final int LIST_TOP_GAP    = 4;

    private int x, y, w, h;

    private final List<ToolPreset>  presets;
    private final OnPickPreset      onPick;
    private final OnClipboardImport onClipboard;
    private final OnFileImport      onFile;

    private final ScrollBar scrollBar = new ScrollBar();
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;

    private int clipboardBtnX, fileBtnX, btnY;

    /** 缓存的预览栈（key = toolId + "@" + savedAt）。 */
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    public ToolPresetBrowserPopup(List<ToolPreset> presets,
                                  OnPickPreset onPick,
                                  OnClipboardImport onClipboard,
                                  OnFileImport onFile) {
        this.presets = presets;
        this.onPick = onPick;
        this.onClipboard = onClipboard;
        this.onFile = onFile;

        scrollBar.setOnOffsetChanged(v -> scrollOffset = v);
        scrollBar.setThumbMinHeight(12);
        scrollBar.setHoverExpandX(2);
        scrollBar.setAnimationId("anvil.presets.scroll");
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    public boolean isPointInside(double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ============================================================
    // ===== 布局辅助 =============================================
    // ============================================================

    /** 列表顶部 Y 坐标（分割线下方 LIST_TOP_GAP 像素）。 */
    private int listTopY() {
        return btnY + BTN_H + LIST_TOP_GAP;
    }

    /** 列表可用高度。 */
    private int listAreaH() {
        return (y + h - PAD) - listTopY();
    }

    /** 列表可用宽度（去掉滚动条）。 */
    private int listAreaW() {
        return w - PAD * 2 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;
    }

    // ============================================================
    // ===== 渲染 =================================================
    // ============================================================

    public void render(PoseStack ps, Font font, int mouseX, int mouseY) {
        GuiComponent.fill(ps, x, y, x + w, y + h, 0xF01A1A1A);
        CardBackground.draw(ps, x, y, w, h, 0xF01A1A1A, AnvilTheme.SECTION_BORDER);

        // 标题
        font.draw(ps, new TranslatableComponent(
                        "gui.anvilssearch.sim.browser.title").getString(),
                x + PAD, y + PAD + 1, AnvilTheme.ACCENT);

        // 顶部按钮
        btnY = y + PAD + 15;
        clipboardBtnX = x + PAD;
        fileBtnX = clipboardBtnX + BTN_W + BTN_GAP;

        boolean h1 = mouseX >= clipboardBtnX && mouseX <= clipboardBtnX + BTN_W
                && mouseY >= btnY && mouseY <= btnY + BTN_H;
        boolean h2 = mouseX >= fileBtnX && mouseX <= fileBtnX + BTN_W
                && mouseY >= btnY && mouseY <= btnY + BTN_H;

        float h1T = AnvilWidgetAnimations.buttonHover("presets.clipboard", h1);
        float h2T = AnvilWidgetAnimations.buttonHover("presets.file", h2);

        AnvilTheme.button(ps, font, clipboardBtnX, btnY, BTN_W, BTN_H,
                new TranslatableComponent(
                        "gui.anvilssearch.sim.browser.from_clipboard").getString(),
                h1T, 0f);
        AnvilTheme.button(ps, font, fileBtnX, btnY, BTN_W, BTN_H,
                new TranslatableComponent(
                        "gui.anvilssearch.sim.browser.from_file").getString(),
                h2T, 0f);

        // 分割线（紧跟按钮下方 3px）
        int dividerY = btnY + BTN_H + 3;
        GuiComponent.fill(ps, x + PAD, dividerY,
                x + w - PAD, dividerY + 1, AnvilTheme.SECTION_BORDER);

        // 列表区
        int listX = x + PAD;
        int listY = listTopY();      // ★ 紧贴分割线
        int listW = listAreaW();
        int listH = listAreaH();
        if (listH <= 0 || listW <= 0) return;

        int totalH = presets.size() * ROW_H;
        maxScrollOffset = Math.max(0, totalH - listH);
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;

        boolean scissorOk = ScissorHelper.enableScissor(listX, listY, listW, listH);
        try {
            if (scissorOk) RenderSystem.disableDepthTest();

            if (presets.isEmpty()) {
                font.draw(ps, new TranslatableComponent(
                                "gui.anvilssearch.sim.browser.empty").getString(),
                        listX + 4, listY + 2, AnvilTheme.TEXT_DIM);
            } else {
                int rowY = listY - scrollOffset;
                for (ToolPreset p : presets) {
                    if (rowY + ROW_H >= listY && rowY <= listY + listH) {
                        drawRow(ps, font, listX, rowY, listW, p, mouseX, mouseY);
                    }
                    rowY += ROW_H;
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
                    listY, SCROLL_BAR_WIDTH, listH);
            scrollBar.setRange(scrollOffset, maxScrollOffset);
            scrollBar.render(ps, mouseX, mouseY);
        }
    }

    private void drawRow(PoseStack ps, Font font, int x, int y, int w,
                         ToolPreset p, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + w
                && mouseY >= y && mouseY <= y + ROW_H;

        String rowKey = p.toolId + "@" + p.savedAt;
        float hoverT = AnvilWidgetAnimations.rowHover("preset:" + rowKey, hover);
        AnvilTheme.row(ps, x, y, w, ROW_H, hoverT, 0f);

        ToolDefinitionIndex.Entry def = findDef(p.toolId);

        // 图标：按保存的材料渲染
        ItemStack icon = buildPreviewStack(p, def);
        if (icon != null && !icon.isEmpty()) {
            try {
                Minecraft mc = Minecraft.getInstance();
                mc.getItemRenderer().renderGuiItem(icon, x + 6, y + 6);
                mc.getItemRenderer().renderGuiItemDecorations(
                        mc.font, icon, x + 6, y + 6, "");
            } catch (Throwable ignored) {}
        }

        String name = def != null ? def.getDisplayName() : p.toolId;
        String sub = p.materials.size()
                + " " + new TranslatableComponent(
                "gui.anvilssearch.sim.browser.parts_suffix").getString();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm", Locale.ROOT);
            sub += " · " + sdf.format(new Date(p.savedAt));
        } catch (Throwable ignored) {}

        // 文字区域（给删除按钮留空）
        int textMaxW = w - (6 + ICON_SIZE + 8) - (DELETE_BTN_SIZE + DELETE_BTN_PAD * 2);
        String nameDisplay = font.width(name) > textMaxW
                ? font.plainSubstrByWidth(name, textMaxW - 4) + "..."
                : name;
        String subDisplay = font.width(sub) > textMaxW
                ? font.plainSubstrByWidth(sub, textMaxW - 4) + "..."
                : sub;

        int tx = x + 6 + ICON_SIZE + 8;
        font.draw(ps, nameDisplay, tx, y + 4,
                hover ? AnvilTheme.TEXT_PRIMARY : AnvilTheme.TEXT_SECONDARY);
        font.draw(ps, "\u00A78" + subDisplay, tx, y + 4 + font.lineHeight + 2,
                AnvilTheme.TEXT_DIM);

        // 删除按钮
        int delX = x + w - DELETE_BTN_SIZE - DELETE_BTN_PAD;
        int delY = y + (ROW_H - DELETE_BTN_SIZE) / 2;
        boolean delHover = mouseX >= delX && mouseX <= delX + DELETE_BTN_SIZE
                && mouseY >= delY && mouseY <= delY + DELETE_BTN_SIZE;

        if (delHover) {
            GuiComponent.fill(ps, delX, delY,
                    delX + DELETE_BTN_SIZE, delY + DELETE_BTN_SIZE,
                    0x66FF5555);
        }
        String xGlyph = "\u2715";
        int gx = delX + (DELETE_BTN_SIZE - font.width(xGlyph)) / 2;
        int gy = delY + (DELETE_BTN_SIZE - font.lineHeight) / 2 + 1;
        font.draw(ps, xGlyph, gx, gy,
                delHover ? 0xFFFF5555 : AnvilTheme.TEXT_MUTED);
    }

    /** 用保存的材料构建带材质的预览栈（带缓存）。 */
    private ItemStack buildPreviewStack(ToolPreset preset,
                                        ToolDefinitionIndex.Entry def) {
        if (preset == null) return ItemStack.EMPTY;

        String key = preset.toolId + "@" + preset.savedAt;
        ItemStack cached = iconCache.get(key);
        if (cached != null) return cached;

        if (def != null && def.definition != null) {
            try {
                ToolStatsCalculator.Result r =
                        ToolStatsCalculator.calculate(
                                def.definition, preset.materials);
                if (r != null && r.stack != null && !r.stack.isEmpty()) {
                    iconCache.put(key, r.stack);
                    return r.stack;
                }
            } catch (Throwable t) {
                System.err.println("[Anvil's Search] preview build failed for "
                        + preset.toolId + ": " + t);
            }
        }

        if (def != null && def.item != null) {
            try {
                ItemStack blank = new ItemStack(def.item);
                iconCache.put(key, blank);
                return blank;
            } catch (Throwable ignored) {}
        }
        return ItemStack.EMPTY;
    }

    private static ToolDefinitionIndex.Entry findDef(String toolId) {
        if (toolId == null) return null;
        for (ToolDefinitionIndex.Entry e : ToolDefinitionIndex.get()) {
            if (e.id != null && e.id.toString().equals(toolId)) return e;
        }
        return null;
    }

    // ============================================================
    // ===== 交互 =================================================
    // ============================================================

    public boolean mouseClicked(double mx, double my, int button) {
        if (scrollBar.tryBeginDrag(mx, my)) return true;
        if (!isPointInside(mx, my)) return false;

        // 顶部按钮
        if (my >= btnY && my <= btnY + BTN_H) {
            if (mx >= clipboardBtnX && mx <= clipboardBtnX + BTN_W) {
                if (onClipboard != null) onClipboard.onImport();
                return true;
            }
            if (mx >= fileBtnX && mx <= fileBtnX + BTN_W) {
                if (onFile != null) onFile.onImport();
                return true;
            }
        }

        // 列表区（★ 与 render 相同的 listY 计算）
        int listX = x + PAD;
        int listY = listTopY();
        int listW = listAreaW();
        if (mx < listX || mx > listX + listW) return true;

        int rowY = listY - scrollOffset;
        for (int i = 0; i < presets.size(); i++) {
            ToolPreset p = presets.get(i);

            if (my >= rowY && my <= rowY + ROW_H) {
                // 删除按钮优先
                int delX = listX + listW - DELETE_BTN_SIZE - DELETE_BTN_PAD;
                int delY = rowY + (ROW_H - DELETE_BTN_SIZE) / 2;
                if (mx >= delX && mx <= delX + DELETE_BTN_SIZE
                        && my >= delY && my <= delY + DELETE_BTN_SIZE) {
                    doDelete(p, i);
                    return true;
                }

                // 点击行 → 应用预设
                if (onPick != null) onPick.onPick(p);
                return true;
            }
            rowY += ROW_H;
        }
        return true;
    }

    private void doDelete(ToolPreset p, int index) {
        ToolPresetStore.delete(p);
        if (index >= 0 && index < presets.size()) {
            presets.remove(index);
        }
        iconCache.remove(p.toolId + "@" + p.savedAt);
        if (scrollOffset > 0) {
            scrollOffset = Math.max(0, scrollOffset - ROW_H);
        }
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
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

    public boolean mouseDragged(double my) { return scrollBar.updateDrag(my); }
    public void mouseReleased() { scrollBar.endDrag(); }
    public boolean isDragging() { return scrollBar.isDragging(); }
}