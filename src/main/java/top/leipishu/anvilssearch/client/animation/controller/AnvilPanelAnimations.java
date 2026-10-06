package top.leipishu.anvilssearch.client.animation.controller;

import top.leipishu.anvilssearch.client.animation.AnvilAnimations;
import top.leipishu.tinkerssearch.client.animation.core.Animation;
import top.leipishu.tinkerssearch.client.animation.core.AnimationManager;
import top.leipishu.tinkerssearch.client.animation.core.Animator;
import top.leipishu.tinkerssearch.client.animation.core.Easing;

/**
 * 面板级动画读写封装。
 *
 * <p>滑入滑出与宽度过渡使用 {@link Animation}（固定 350ms + {@code EASE_OUT_CUBIC}），
 * 与 Tinkers' Search 引入动画系统前的行为一致：有明确完成时刻。
 *
 * <h2>关键设计：一次性触发，渲染只读</h2>
 * <p>动画只在状态切换时创建（{@link #startSlideAnimation} / {@link #startWidthAnimation}），
 * 渲染时通过 {@link #getSlideProgress()} /  纯读。
 * <b>首次创建不播无用动画</b>（如宽度未变时不启动 350ms 动画）。
 *
 * <p>hover / 指示器类动画使用 {@link Animator}（指数趋近）——连续状态，无需明确终点。
 */
public final class AnvilPanelAnimations {

    private AnvilPanelAnimations() {}

    // ============================================================
    // ===== 时长 / 缓动 ===========================================
    // ============================================================

    private static final long   PANEL_DURATION = 350L;
    private static final Easing PANEL_EASING   = Easing.EASE_OUT_CUBIC;

    /** 面板默认宽度（用于首次 slide 时的兜底）。 */
    private static final float  DEFAULT_PANEL_WIDTH = 220f;

    // ============================================================
    // ===== 时间常数（仅 hover / 指示器使用）======================
    // ============================================================

    private static final float TAB_INDICATOR_TAU = 110f;
    private static final float TAB_HOVER_TAU     = 70f;
    private static final float TABBTN_HOVER_TAU  = 70f;

    // ============================================================
    // ===== 触发：滑入滑出 ========================================
    // ============================================================

    /**
     * 启动一次滑入或滑出动画。
     *
     * <p>从当前插值位置起播，支持动画中途反向。
     * 只在面板可见状态真正改变时调用（{@code AnvilPanelAnimation.startShow/startHide}）。
     *
     * @param targetVisible true = 滑入（→1），false = 滑出（→0）
     */
    public static void startSlideAnimation(boolean targetVisible) {
        Animation existing = AnimationManager.get().get(AnvilAnimations.PANEL_SLIDE);
        float from = (existing == null) ? (targetVisible ? 0f : 1f) : existing.getValue();
        float to   = targetVisible ? 1f : 0f;

        // 已在目标态：不重复创建
        if (Math.abs(from - to) < 0.0001f) return;

        AnimationManager.get().play(
                AnvilAnimations.PANEL_SLIDE,
                from, to,
                PANEL_DURATION, 0L,
                PANEL_EASING, null);
    }

    /**
     * 只读：滑入滑出进度（0 = 完全隐藏，1 = 完全显示）。
     *
     * <p>从未启动过动画时返回 0（面板初始为隐藏态）。
     */
    public static float getSlideProgress() {
        Animation a = AnimationManager.get().get(AnvilAnimations.PANEL_SLIDE);
        return (a == null) ? 0f : a.getValue();
    }

    /**
     * 滑入滑出是否正在播放（未完成）。
     */
    public static boolean isSliding() {
        Animation a = AnimationManager.get().get(AnvilAnimations.PANEL_SLIDE);
        return a != null && !a.isComplete();
    }

    // ============================================================
    // ===== 触发：面板宽度 ========================================
    // ============================================================

    /**
     * 启动一次宽度过渡动画。
     *
     * <p>只在目标宽度真正改变时调用（{@code AnvilPanelAnimation.setTargetWidth}）。
     * 从当前插值位置起播，支持中途反向。
     *
     * @param oldWidth 旧宽度（首次创建时作为 from 起点）
     * @param newWidth 新宽度
     */
    public static void startWidthAnimation(int oldWidth, int newWidth) {
        if (oldWidth == newWidth) return;

        Animation existing = AnimationManager.get().get(AnvilAnimations.PANEL_WIDTH);
        float from = (existing == null) ? (float) oldWidth : existing.getValue();

        AnimationManager.get().play(
                AnvilAnimations.PANEL_WIDTH,
                from, (float) newWidth,
                PANEL_DURATION, 0L,
                PANEL_EASING, null);
    }

    /**
     * 只读：面板当前宽度（浮点插值）。
     *
     * @param fallback 从未启动过宽度动画时的返回值（通常传 targetWidth）
     */
    public static float getPanelWidth(int fallback) {
        Animation a = AnimationManager.get().get(AnvilAnimations.PANEL_WIDTH);
        return (a == null) ? (float) fallback : a.getValue();
    }

    /**
     * 宽度是否正在播放（未完成）。
     */
    public static boolean isWidthAnimating() {
        Animation a = AnimationManager.get().get(AnvilAnimations.PANEL_WIDTH);
        return a != null && !a.isComplete();
    }

    // ============================================================
    // ===== 综合查询 ==============================================
    // ============================================================

    /** 面板是否有任意动画在跑（滑入滑出 / 宽度）。 */
    public static boolean isAnimating() {
        return isSliding() || isWidthAnimating();
    }

    // ============================================================
    // ===== Tab 指示器 ============================================
    // ============================================================

    public static float tabIndicator(int targetX) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.TAB_INDICATOR, targetX, TAB_INDICATOR_TAU);
        a.setTarget(targetX);
        return a.getValue();
    }

    // ============================================================
    // ===== Tab hover =============================================
    // ============================================================

    public static float tabHover(int index, boolean hover) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.tabHover(index), 0f, TAB_HOVER_TAU);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== 侧边按钮 hover ========================================
    // ============================================================

    public static float tabBtnHover(boolean hover) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.TABBTN_HOVER, 0f, TABBTN_HOVER_TAU);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }
}