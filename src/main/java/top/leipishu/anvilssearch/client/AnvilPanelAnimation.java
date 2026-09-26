package top.leipishu.anvilssearch.client;

/**
 * 侧边栏动画。
 * 展开/收起和宽度过渡走两条独立进度，都是 350ms cubic ease-out。
 */
public final class AnvilPanelAnimation {

    public static final int DURATION = 350;
    public static final int WIDTH_NORMAL = 220;
    public static final int WIDTH_WIDE   = 360;

    private boolean slideAnimating;
    private float   slideProgress = 0f;
    private float   slideFrom, slideTo;
    private long    slideStart;

    private boolean widthAnimating;
    private float   widthProgress = WIDTH_NORMAL;
    private float   widthFrom, widthTo;
    private long    widthStart;

    public boolean isAnimating() { return slideAnimating || widthAnimating; }
    public boolean isExpanded()  { return slideProgress >= 1f; }

    public int getCurrentWidth() { return (int) widthProgress; }

    public int getAnimationOffset() {
        return -(int) (widthProgress * (1f - slideProgress));
    }

    public void startShow() {
        slideFrom = slideProgress;
        slideTo   = 1f;
        slideStart = System.currentTimeMillis();
        slideAnimating = true;
    }

    public void startHide() {
        slideFrom = slideProgress;
        slideTo   = 0f;
        slideStart = System.currentTimeMillis();
        slideAnimating = true;
    }

    public void setTargetWidth(int target) {
        if (!widthAnimating && (int) widthProgress == target) return;
        widthFrom = widthProgress;
        widthTo   = target;
        widthStart = System.currentTimeMillis();
        widthAnimating = true;
    }

    public void update() {
        long now = System.currentTimeMillis();

        if (slideAnimating) {
            float t = Math.min(1f, (now - slideStart) / (float) DURATION);
            slideProgress = slideFrom + (slideTo - slideFrom) * easeOutCubic(t);
            if (t >= 1f) { slideProgress = slideTo; slideAnimating = false; }
        }
        if (widthAnimating) {
            float t = Math.min(1f, (now - widthStart) / (float) DURATION);
            widthProgress = widthFrom + (widthTo - widthFrom) * easeOutCubic(t);
            if (t >= 1f) { widthProgress = widthTo; widthAnimating = false; }
        }
    }

    private static float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }
}