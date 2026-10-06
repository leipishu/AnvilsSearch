package top.leipishu.anvilssearch.client;

import top.leipishu.anvilssearch.client.animation.controller.AnvilPanelAnimations;

/**
 * 侧边栏动画。
 *
 * <p>内部状态只有两个"目标态"字段：{@code visible} 与 {@code targetWidth}。
 * 动画在 {@link #startShow()} / {@link #startHide()} / {@link #setTargetWidth(int)}
 * 中一次性触发；渲染时通过 {@link #getCurrentWidth()} / {@link #getAnimationOffset()}
 * 纯读 {@code AnvilPanelAnimations} 的插值结果。
 *
 * <p>与 Tinkers' Search 的 {@code PanelAnimationManager} 模式一致——
 * <b>打开面板时不会启动任何无用的动画</b>（宽度未变不播宽度动画）。
 */
public final class AnvilPanelAnimation {

    public static final int WIDTH_NORMAL = 220;
    public static final int WIDTH_WIDE   = 360;

    /** 面板的目标可见状态。 */
    private boolean visible = false;

    /** 面板的目标宽度。 */
    private int targetWidth = WIDTH_NORMAL;

    // ============================================================
    // ===== 状态查询 ==============================================
    // ============================================================

    public boolean isAnimating() {
        return AnvilPanelAnimations.isAnimating();
    }

    /**
     * 面板是否完全展开（滑入进度到达 1.0）。
     */
    public boolean isExpanded() {
        return AnvilPanelAnimations.getSlideProgress() >= 1f;
    }

    /**
     * 面板当前宽度（浮点插值取整）。
     */
    public int getCurrentWidth() {
        return (int) AnvilPanelAnimations.getPanelWidth(targetWidth);
    }

    /**
     * 当前水平偏移（负值 = 向左滑出屏幕）。
     *
     * <p>公式与旧实现一致：{@code -(width * (1 - slideProgress))}。
     */
    public int getAnimationOffset() {
        float wp = AnvilPanelAnimations.getPanelWidth(targetWidth);
        float sp = AnvilPanelAnimations.getSlideProgress();
        return -(int) (wp * (1f - sp));
    }

    /** 目标宽度（供外部读取，用于布局计算）。 */
    public int getTargetWidth() {
        return targetWidth;
    }

    // ============================================================
    // ===== 控制 ==================================================
    // ============================================================

    public void startShow() {
        if (visible) return;
        visible = true;
        AnvilPanelAnimations.startSlideAnimation(true);
    }

    public void startHide() {
        if (!visible) return;
        visible = false;
        AnvilPanelAnimations.startSlideAnimation(false);
    }

    public void setTargetWidth(int target) {
        int old = this.targetWidth;
        this.targetWidth = target;
        AnvilPanelAnimations.startWidthAnimation(old, target);
    }

    /**
     * @deprecated 动画由 Tinkers' Search 的 {@code onRenderTick} 统一驱动。
     */
    @Deprecated
    public void update() {
        // no-op
    }
}