package top.leipishu.anvilssearch.client.animation.controller;

import top.leipishu.anvilssearch.client.animation.AnvilAnimations;
import top.leipishu.tinkerssearch.client.animation.core.AnimationManager;
import top.leipishu.tinkerssearch.client.animation.core.Animator;
import top.leipishu.tinkerssearch.client.animation.core.Easing;

/**
 * 组件级动画读写封装。
 *
 * <p>涵盖行 hover / 行选中 / 卡片展开 / 卡片淡入 / 星标脉冲 / 按钮 hover /
 * 槽位状态色 / Popup 出现消失 / 反馈文本淡入。
 *
 * <p>所有方法都是幂等的：同一 key 每帧调用会产生相同的目标值，无副作用。
 */
public final class AnvilWidgetAnimations {

    private AnvilWidgetAnimations() {}

    // ============================================================
    // ===== 时间常数 ==============================================
    // ============================================================

    private static final float ROW_HOVER_TAU    = 70f;
    private static final float ROW_SELECTED_TAU = 70f;
    private static final float CARD_EXPAND_TAU  = 100f;
    private static final float CARD_FADE_TAU    = 80f;
    private static final float BUTTON_HOVER_TAU = 70f;
    private static final float SLOT_STATE_TAU   = 90f;
    private static final float SLOT_HOVER_TAU   = 70f;
    private static final float POPUP_FADE_TAU   = 90f;
    private static final float POPUP_SCALE_TAU  = 90f;
    private static final float FEEDBACK_TAU     = 150f;

    // ============================================================
    // ===== 行 hover / 选中 ========================================
    // ============================================================

    /**
     * 行 hover 值。
     *
     * @param key   稳定的行身份（如 {@code "part:tconstruct:pick_head"}）
     * @param hover 是否悬停
     * @return 0 = 常态，1 = 完全悬停
     */
    public static float rowHover(String key, boolean hover) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.rowHover(key), 0f, ROW_HOVER_TAU);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    /**
     * 行选中值。
     *
     * @return 0 = 未选中，1 = 完全选中
     */
    public static float rowSelected(String key, boolean selected) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.rowSelected(key), 0f, ROW_SELECTED_TAU);
        a.setTarget(selected ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== 卡片展开 / 淡入 ========================================
    // ============================================================

    /**
     * 卡片展开量。
     *
     * @return 0 = 完全收起，1 = 完全展开
     */
    public static float cardExpand(String key, boolean expanded) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.cardExpand(key), 0f, CARD_EXPAND_TAU);
        a.setTarget(expanded ? 1f : 0f);
        return a.getValue();
    }

    /**
     * 卡片内容透明度。
     *
     * @return 0 = 不可见，1 = 完全不透明
     */
    public static float cardFade(String key, boolean visible) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.cardFade(key), 0f, CARD_FADE_TAU);
        a.setTarget(visible ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== 按钮 hover ============================================
    // ============================================================

    /**
     * 通用按钮 hover 值。
     */
    public static float buttonHover(String key, boolean hover) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.buttonHover(key), 0f, BUTTON_HOVER_TAU);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== 槽位 ==================================================
    // ============================================================

    /**
     * 槽位边框颜色（ARGB 作为 float 存储，插值后转回 int）。
     *
     * <p>ARGB 值可达 2^32，float 有效精度约 7 位十进制数，
     * 视觉上的颜色误差无法察觉。
     *
     * @param targetColor 目标 ARGB 颜色；{@code 0} 表示"无边框"
     * @return 当前插值颜色
     */
    public static int slotBorderColor(String key, int targetColor) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.slotState(key), (float) targetColor, SLOT_STATE_TAU);
        a.setTarget((float) targetColor);
        return (int) a.getValue();
    }

    /**
     * 槽位 hover 值。
     */
    public static float slotHover(String key, boolean hover) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.slotHover(key), 0f, SLOT_HOVER_TAU);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== 触发型：星标脉冲 ======================================
    // ============================================================

    /**
     * 触发一次星标点亮脉冲。链式两段：
     * <ol>
     *   <li>缩放 1.0 → 1.5（90ms，EASE_OUT_QUAD）</li>
     *   <li>缩放 1.5 → 1.0（180ms，EASE_OUT_BACK，带过冲）</li>
     * </ol>
     */
    public static void triggerStarPulse(String key) {
        String k = AnvilAnimations.starPulse(key);
        AnimationManager.get().play(
                k, 1f, 1.5f, 90L, 0L, Easing.EASE_OUT_QUAD,
                () -> AnimationManager.get().play(
                        k, 1.5f, 1f, 180L, 0L, Easing.EASE_OUT_BACK, null));
    }

    /**
     * 查询星标当前缩放。
     *
     * @return 1.0 = 常态；&gt;1 表示正在点亮脉冲
     */
    public static float starScale(String key) {
        return AnimationManager.get().getValue(AnvilAnimations.starPulse(key), 1f);
    }

    // ============================================================
    // ===== Popup 出现 / 消失 =====================================
    // ============================================================

    /**
     * Popup 透明度。
     *
     * @return 0 = 完全透明，1 = 完全不透明
     */
    public static float popupFade(String key, boolean visible) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.popupFade(key), 0f, POPUP_FADE_TAU);
        a.setTarget(visible ? 1f : 0f);
        return a.getValue();
    }

    /**
     * Popup 缩放。
     *
     * @return 0.92（隐藏）.. 1.0（显示）
     */
    public static float popupScale(String key, boolean visible) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.popupScale(key), 0.92f, POPUP_SCALE_TAU);
        a.setTarget(visible ? 1f : 0.92f);
        return a.getValue();
    }

    // ============================================================
    // ===== 反馈文本 ==============================================
    // ============================================================

    /**
     * 保存 / 导出 / 导入反馈文本的透明度。
     */
    public static float feedbackAlpha(boolean visible) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.FEEDBACK_FADE, 0f, FEEDBACK_TAU);
        a.setTarget(visible ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== 清理 ==================================================
    // ============================================================

    /** 清空某个 key 前缀的组件动画。 */
    public static void clearPrefix(String prefix) {
        AnimationManager.get().stopPrefix("anvil." + prefix);
    }
}