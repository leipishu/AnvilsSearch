package top.leipishu.anvilssearch.client;

public final class AnvilPanelLayout {

    public static final int CONTENT_PAD_X = 5;
    public static final int CONTENT_PAD_Y = 4;

    public static final int LEFT_MIN_W  = 80;
    public static final int MID_W       = 110;
    public static final int RIGHT_MIN_W = 60;
    public static final int COL_GAP     = 4;

    public static final int TOOL_ROW_H = 16;
    public static final int PART_ROW_H = 22;
    public static final int MAT_ROW_H  = 14;

    private AnvilPanelLayout() {}

    public static final class ThreeColumn {
        public final int leftX, leftW;
        public final int midX,  midW;
        public final int rightX, rightW;

        ThreeColumn(int lx, int lw, int mx, int mw, int rx, int rw) {
            leftX = lx; leftW = lw;
            midX  = mx; midW  = mw;
            rightX = rx; rightW = rw;
        }
    }

    public static ThreeColumn computeThreeColumn(int contentLeft, int contentRight) {
        int totalW = contentRight - contentLeft;

        int midW = MID_W;
        int leftW = Math.max(LEFT_MIN_W, (totalW - midW - COL_GAP * 2) / 2);
        int rightW = totalW - leftW - midW - COL_GAP * 2;

        if (rightW < RIGHT_MIN_W) {
            rightW = RIGHT_MIN_W;
            leftW = totalW - midW - rightW - COL_GAP * 2;
            if (leftW < LEFT_MIN_W) leftW = LEFT_MIN_W;
        }

        int leftX = contentLeft;
        int midX = leftX + leftW + COL_GAP;
        int rightX = midX + midW + COL_GAP;

        return new ThreeColumn(leftX, leftW, midX, midW, rightX, rightW);
    }
}