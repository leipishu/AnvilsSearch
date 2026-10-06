package top.leipishu.anvilssearch.client.animation.controller;

import top.leipishu.anvilssearch.client.animation.AnvilAnimations;
import top.leipishu.tinkerssearch.client.animation.core.Animation;
import top.leipishu.tinkerssearch.client.animation.core.AnimationManager;
import top.leipishu.tinkerssearch.client.animation.core.Animator;
import top.leipishu.tinkerssearch.client.animation.core.Easing;

public final class AnvilWidgetAnimations {

    private AnvilWidgetAnimations() {}

    // ===== 时间常数 =====
    private static final float ROW_HOVER_TAU    = 70f;
    private static final float ROW_SELECTED_TAU = 70f;
    private static final float BUTTON_HOVER_TAU = 70f;
    private static final float SLOT_STATE_TAU   = 90f;
    private static final float SLOT_HOVER_TAU   = 70f;
    private static final float FEEDBACK_TAU     = 150f;

    /** 展开 / 收起固定时长（毫秒）。 */
    private static final long   EXPAND_DURATION = 200L;
    private static final Easing EXPAND_EASING   = Easing.EASE_OUT_QUAD;

    private static final long   POPUP_DURATION  = 160L;
    private static final Easing POPUP_EASING    = Easing.EASE_OUT_QUAD;

    // ============================================================
    // ===== 行 hover / 选中 ========================================
    // ============================================================

    public static float rowHover(String key, boolean hover) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.rowHover(key), 0f, ROW_HOVER_TAU);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    public static float rowSelected(String key, boolean selected) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.rowSelected(key), 0f, ROW_SELECTED_TAU);
        a.setTarget(selected ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== 展开进度（固定时长）===================================
    // ============================================================

    /**
     * 展开进度：0 = 完全收起，1 = 完全展开。
     *
     * <p>用 {@link Animation}（固定 200ms + {@code EASE_OUT_QUAD}），
     * 有明确完成时刻，不会像 {@link Animator} 那样有指数趋近的"长尾卡顿"。
     *
     * <p>驱动逻辑：目标改变时才起播新动画；目标不变则只读当前插值。
     */
    public static float expandProgress(String key, boolean expanded) {
        String k = AnvilAnimations.cardFade(key);
        float target = expanded ? 1f : 0f;

        Animation existing = AnimationManager.get().get(k);

        if (existing == null) {
            // 首次：只有展开才播动画，收起态直接返回 0
            if (!expanded) return 0f;
            AnimationManager.get().play(
                    k, 0f, 1f,
                    EXPAND_DURATION, 0L, EXPAND_EASING, null);
            Animation created = AnimationManager.get().get(k);
            return (created == null) ? 0f : created.getValue();
        }

        // 目标变了：从当前插值位置起播
        if (Math.abs(existing.getTo() - target) > 0.0001f) {
            float from = existing.getValue();
            if (Math.abs(from - target) > 0.0001f) {
                AnimationManager.get().play(
                        k, from, target,
                        EXPAND_DURATION, 0L, EXPAND_EASING, null);
            }
        }

        Animation a = AnimationManager.get().get(k);
        return (a == null) ? target : a.getValue();
    }

    /** 兼容旧名。 */
    public static float cardFade(String key, boolean visible) {
        return expandProgress(key, visible);
    }

    // ============================================================
    // ===== 按钮 hover ============================================
    // ============================================================

    public static float buttonHover(String key, boolean hover) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.buttonHover(key), 0f, BUTTON_HOVER_TAU);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== 槽位 ==================================================
    // ============================================================

    public static int slotBorderColor(String key, int targetColor) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.slotState(key), (float) targetColor, SLOT_STATE_TAU);
        a.setTarget((float) targetColor);
        return (int) a.getValue();
    }

    public static float slotHover(String key, boolean hover) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.slotHover(key), 0f, SLOT_HOVER_TAU);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== 星标脉冲 ==============================================
    // ============================================================

    public static void triggerStarPulse(String key) {
        String k = AnvilAnimations.starPulse(key);
        AnimationManager.get().play(
                k, 1f, 1.5f, 90L, 0L, Easing.EASE_OUT_QUAD,
                () -> AnimationManager.get().play(
                        k, 1.5f, 1f, 180L, 0L, Easing.EASE_OUT_BACK, null));
    }

    public static float starScale(String key) {
        return AnimationManager.get().getValue(AnvilAnimations.starPulse(key), 1f);
    }

    // ============================================================
    // ===== Popup 出现 / 消失 =====================================
    // ============================================================

    public static void startPopupAnimation(String key, boolean visible) {
        Animation fe = AnimationManager.get().get(AnvilAnimations.popupFade(key));
        float ff = (fe == null) ? (visible ? 0f : 1f) : fe.getValue();
        float ft = visible ? 1f : 0f;
        if (Math.abs(ff - ft) > 0.0001f) {
            AnimationManager.get().play(
                    AnvilAnimations.popupFade(key),
                    ff, ft, POPUP_DURATION, 0L, POPUP_EASING, null);
        }

        Animation se = AnimationManager.get().get(AnvilAnimations.popupScale(key));
        float sf = (se == null) ? (visible ? 0.92f : 1.0f) : se.getValue();
        float st = visible ? 1.0f : 0.92f;
        if (Math.abs(sf - st) > 0.0001f) {
            AnimationManager.get().play(
                    AnvilAnimations.popupScale(key),
                    sf, st, POPUP_DURATION, 0L, POPUP_EASING, null);
        }
    }

    public static float getPopupFade(String key, float fallback) {
        Animation a = AnimationManager.get().get(AnvilAnimations.popupFade(key));
        return (a == null) ? fallback : a.getValue();
    }

    public static float getPopupScale(String key, float fallback) {
        Animation a = AnimationManager.get().get(AnvilAnimations.popupScale(key));
        return (a == null) ? fallback : a.getValue();
    }

    public static boolean isPopupFadeComplete(String key) {
        Animation a = AnimationManager.get().get(AnvilAnimations.popupFade(key));
        return a == null || a.isComplete();
    }

    // ============================================================
    // ===== 反馈文本 ==============================================
    // ============================================================

    public static float feedbackAlpha(boolean visible) {
        Animator a = AnimationManager.get().animator(
                AnvilAnimations.FEEDBACK_FADE, 0f, FEEDBACK_TAU);
        a.setTarget(visible ? 1f : 0f);
        return a.getValue();
    }

    public static void clearPrefix(String prefix) {
        AnimationManager.get().stopPrefix("anvil." + prefix);
    }
}