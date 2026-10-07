package top.leipishu.anvilssearch.client.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import top.leipishu.tinkerssearch.client.animation.core.ColorUtil;

/**
 * 全局视觉词典。
 * 三个 Tab + 面板渲染都从这里取色、取尺寸、取绘制辅助。
 * 改一处即可全局统一。
 *
 * <p>1.20.1 迁移：所有绘制方法从 {@code GuiComponent + PoseStack} 改为 {@code GuiGraphics}。
 */
public final class AnvilTheme {

    // ===== 底层背景 =====
    public static final int PANEL_BG          = 0xFF161616;
    public static final int SECTION_BG        = 0xFF1B1B1B;
    public static final int SECTION_HEADER_BG = 0xFF242424;
    public static final int SECTION_BORDER    = 0xFF2E2E2E;

    // ===== 强调色 =====
    public static final int ACCENT            = 0xFFFFAA00;
    public static final int ACCENT_SOFT       = 0xFFFFDD77;
    public static final int ACCENT_DARK       = 0xFF6A5030;
    public static final int ACCENT_CYAN       = 0xFF55FFFF;

    // ===== 行叠加色 =====
    public static final int ROW_HOVER         = 0x22FFFFFF;
    public static final int ROW_SELECTED      = 0x44FFAA00;

    // ===== 卡片 =====
    public static final int CARD_BG           = 0xFF1E1E1E;
    public static final int CARD_BORDER       = 0xFF333333;
    public static final int CARD_ACCENT_BAR_W = 2;

    // ===== 槽位 =====
    public static final int SLOT_BG           = 0xFF222222;
    public static final int SLOT_BG_HOVER     = 0xFF303030;
    public static final int SLOT_BORDER       = 0xFF444444;
    public static final int SLOT_BORDER_HOVER = 0xFFAA8844;

    // ===== 按钮 =====
    public static final int BTN_BG            = 0xFF2A2A2A;
    public static final int BTN_BG_HOVER      = 0xFF3A3020;
    public static final int BTN_BG_ACTIVE     = 0xFF6A5030;
    public static final int BTN_BORDER        = 0xFF444444;
    public static final int BTN_TEXT          = 0xFFAAAAAA;
    public static final int BTN_TEXT_HOVER    = 0xFFFFFFFF;
    public static final int BTN_TEXT_ACTIVE   = 0xFFFFDD77;

    // ===== 文字 =====
    public static final int TEXT_PRIMARY      = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY    = 0xFFCCCCCC;
    public static final int TEXT_MUTED        = 0xFF888888;
    public static final int TEXT_DIM          = 0xFF666666;

    // ===== 尺寸 =====
    public static final int PAD_XS = 2;
    public static final int PAD_S  = 4;
    public static final int PAD_M  = 6;
    public static final int PAD_L  = 8;

    public static final int ROW_H      = 18;
    public static final int SUB_ROW_H  = 16;
    public static final int LINE_H     = 12;
    public static final int HEADER_H   = 16;
    public static final int CARD_PAD   = 8;
    public static final int CARD_GAP   = 6;
    public static final int SLOT_SIZE  = 18;
    public static final int SLOT_GAP   = 3;

    private AnvilTheme() {}

    // ============================================================
    // ===== 绘制辅助 =============================================
    // ============================================================

    public static void fill(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    public static void panelBg(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
    }

    /** 内容分区：深色底 + 1px 边框。 */
    public static void section(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SECTION_BG);
        g.fill(x, y, x + w, y + 1, SECTION_BORDER);
        g.fill(x, y, x + 1, y + h, SECTION_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, SECTION_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, SECTION_BORDER);
    }

    /**
     * 行：透明背景（让 section 透出），hover / selected 时叠加。
     *
     * @param hoverT    0..1 hover 插值
     * @param selectedT 0..1 selected 插值（优先于 hover）
     */
    public static void row(GuiGraphics g, int x, int y, int w, int h,
                           float hoverT, float selectedT) {
        int bg = 0;
        bg = ColorUtil.lerpARGB(bg, ROW_HOVER, hoverT);
        bg = ColorUtil.lerpARGB(bg, ROW_SELECTED, selectedT);
        if ((bg >>> 24) != 0) {
            g.fill(x, y, x + w, y + h, bg);
        }
    }

    /** 行：透明背景（让 section 透出），hover / selected 时叠加。 */
    public static void row(GuiGraphics g, int x, int y, int w, int h,
                           boolean hover, boolean selected) {
        if (selected) {
            g.fill(x, y, x + w, y + h, ROW_SELECTED);
        } else if (hover) {
            g.fill(x, y, x + w, y + h, ROW_HOVER);
        }
    }

    /** 展开行的左侧 2px 强调条。 */
    public static void rowAccentBar(GuiGraphics g, int x, int y, int h, int color) {
        g.fill(x, y, x + 2, y + h, color);
    }

    /** 卡片背景 + 边框 + 左强调条（accent 传 0 则不画强调条）。 */
    public static void cardBg(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, CARD_BG);
        g.fill(x, y, x + w, y + 1, CARD_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, CARD_BORDER);
        g.fill(x, y, x + 1, y + h, CARD_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, CARD_BORDER);
        if ((accent >>> 24) != 0) {
            g.fill(x, y, x + CARD_ACCENT_BAR_W, y + h, accent);
        }
    }

    /** 槽位背景。 */
    public static void slotBg(GuiGraphics g, int x, int y, int size, boolean hover) {
        int bg = hover ? SLOT_BG_HOVER : SLOT_BG;
        int border = hover ? SLOT_BORDER_HOVER : SLOT_BORDER;
        g.fill(x, y, x + size, y + size, bg);
        g.fill(x, y, x + size, y + 1, border);
        g.fill(x, y + size - 1, x + size, y + size, border);
        g.fill(x, y, x + 1, y + size, border);
        g.fill(x + size - 1, y, x + size, y + size, border);
    }

    // ============================================================
    // ===== 按钮（float 版，供动画系统使用）=======================
    // ============================================================

    /**
     * 通用按钮（float 版）：底 + 边框 + 居中文字 + active 底部金线。
     *
     * @param hoverT  0..1 之间的 hover 插值
     * @param activeT 0..1 之间的 active 插值
     */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h,
                              String label, float hoverT, float activeT) {
        int bg = ColorUtil.lerpARGB(BTN_BG, BTN_BG_HOVER, hoverT);
        bg = ColorUtil.lerpARGB(bg, BTN_BG_ACTIVE, activeT);
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, BTN_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, BTN_BORDER);
        g.fill(x, y, x + 1, y + h, BTN_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, BTN_BORDER);

        if (activeT > 0.01f) {
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1,
                    ColorUtil.withAlphaFactor(ACCENT, activeT));
        }

        int textColor = ColorUtil.lerpARGB(BTN_TEXT, BTN_TEXT_HOVER, hoverT);
        textColor = ColorUtil.lerpARGB(textColor, BTN_TEXT_ACTIVE, activeT);
        int tw = font.width(label);
        g.drawString(font, label, x + (w - tw) / 2,
                y + (h - font.lineHeight) / 2 + 1, textColor, false);
    }

    /** 通用按钮（boolean 兼容版）。 */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h,
                              String label, boolean hover, boolean active) {
        button(g, font, x, y, w, h, label,
                hover ? 1f : 0f, active ? 1f : 0f);
    }

    // ============================================================
    // ===== Tab 栏按钮（float 版）================================
    // ============================================================

    /**
     * Tab 栏专用按钮（float 版）：无边框，仅背景 + 文字。
     */
    public static void tabButton(GuiGraphics g, Font font, int x, int y, int w, int h,
                                 String label, float hoverT, float activeT) {
        int bg = ColorUtil.lerpARGB(BTN_BG, BTN_BG_HOVER, hoverT);
        bg = ColorUtil.lerpARGB(bg, BTN_BG_ACTIVE, activeT);
        g.fill(x, y, x + w, y + h, bg);

        int textColor = ColorUtil.lerpARGB(BTN_TEXT, BTN_TEXT_HOVER, hoverT);
        textColor = ColorUtil.lerpARGB(textColor, BTN_TEXT_ACTIVE, activeT);
        int tw = font.width(label);
        g.drawString(font, label, x + (w - tw) / 2,
                y + (h - font.lineHeight) / 2 + 1, textColor, false);
    }

    /** Tab 栏按钮（boolean 兼容版）。 */
    public static void tabButton(GuiGraphics g, Font font, int x, int y, int w, int h,
                                 String label, boolean hover, boolean active) {
        tabButton(g, font, x, y, w, h, label,
                hover ? 1f : 0f, active ? 1f : 0f);
    }

    public static int centeredTextY(int y, int h, Font font) {
        return y + (h - font.lineHeight) / 2 + 1;
    }

    /** 顶部 1px 分割线，用于 section 内的软分割。 */
    public static void dividerTop(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, SECTION_BORDER);
    }
}