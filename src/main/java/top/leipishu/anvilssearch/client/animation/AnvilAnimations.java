package top.leipishu.anvilssearch.client.animation;

import top.leipishu.tinkerssearch.client.animation.core.AnimationManager;

/**
 * Anvil's Search 动画 key 常量与通用工具。
 *
 * <p>所有 key 以 {@code anvil.} 前缀开头，与 Tinkers' Search 的
 * {@code panel.} / {@code widget.} / {@code detail.} 完全隔离。
 *
 * <p>命名约定沿用 Tinkers' Search：{@code <模块>.<类型>[:<身份>]}。
 */
public final class AnvilAnimations {

    private AnvilAnimations() {}

    /** 全局前缀，用于批量清理。 */
    public static final String PREFIX = "anvil.";

    // ============================================================
    // ===== 面板 ==================================================
    // ============================================================

    /** 面板滑入滑出进度（0 = 完全隐藏，1 = 完全显示）。 */
    public static final String PANEL_SLIDE = "anvil.panel.slide";

    /** 面板宽度（normal 220 ↔ wide 360）。 */
    public static final String PANEL_WIDTH = "anvil.panel.width";

    // ============================================================
    // ===== Tab ===================================================
    // ============================================================

    /** Tab 指示器水平位置。 */
    public static final String TAB_INDICATOR = "anvil.tab.indicator";

    public static String tabHover(int index) {
        return "anvil.tab.hover:" + index;
    }

    // ============================================================
    // ===== 侧边开关按钮 ==========================================
    // ============================================================

    public static final String TABBTN_HOVER = "anvil.tabbtn.hover";

    // ============================================================
    // ===== 行 / 卡片 ============================================
    // ============================================================

    public static String rowHover(String key) {
        return "anvil.row.hover:" + key;
    }

    public static String rowSelected(String key) {
        return "anvil.row.selected:" + key;
    }

    /** 卡片展开量（0 = 收起，1 = 完全展开）。 */
    public static String cardExpand(String key) {
        return "anvil.card.expand:" + key;
    }

    /** 卡片内容透明度。 */
    public static String cardFade(String key) {
        return "anvil.card.fade:" + key;
    }

    // ============================================================
    // ===== 触发型 ================================================
    // ============================================================

    public static String starPulse(String key) {
        return "anvil.star.pulse:" + key;
    }

    // ============================================================
    // ===== 按钮 ==================================================
    // ============================================================

    public static String buttonHover(String key) {
        return "anvil.button.hover:" + key;
    }

    // ============================================================
    // ===== 槽位 ==================================================
    // ============================================================

    /** 槽位边框颜色（ARGB 直接存 float，插值后转回 int）。 */
    public static String slotState(String key) {
        return "anvil.slot.state:" + key;
    }

    public static String slotHover(String key) {
        return "anvil.slot.hover:" + key;
    }

    // ============================================================
    // ===== Popup =================================================
    // ============================================================

    public static String popupFade(String key) {
        return "anvil.popup.fade:" + key;
    }

    public static String popupScale(String key) {
        return "anvil.popup.scale:" + key;
    }

    // ============================================================
    // ===== 反馈文本 ==============================================
    // ============================================================

    public static final String FEEDBACK_FADE = "anvil.feedback.fade";

    // ============================================================
    // ===== 批量清理 =============================================
    // ============================================================

    /** 清空所有 {@code anvil.*} 动画（面板隐藏、世界切换等时机）。 */
    public static void clearAll() {
        AnimationManager.get().stopPrefix(PREFIX);
    }
}