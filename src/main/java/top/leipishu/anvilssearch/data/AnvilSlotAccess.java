package top.leipishu.anvilssearch.data;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 从当前打开的工匠砧 Menu 读槽位。
 * 物品槽在 menu.slots 里通常位于 index 2（0-based），玩家背包槽位在其后。
 */
public final class AnvilSlotAccess {

    private AnvilSlotAccess() {}

    /** 返回该 menu 上的操作槽（不含玩家背包），保持原始顺序。 */
    public static List<Slot> getAnvilSlots() {
        List<Slot> out = new ArrayList<>();

        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return out;

        AbstractContainerMenu menu = acs.getMenu();
        if (menu == null) return out;

        for (Slot slot : menu.slots) {
            if (slot == null) continue;
            try {
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory)
                    continue;
            } catch (Throwable ignored) {}
            out.add(slot);
        }
        return out;
    }

    /**
     * 返回中央物品槽上的物品。
     * <p>通过 {@link #getAllSlots()} 的坐标归类确定中心槽，
     * 不再依赖固定的 menu.slots 索引，也不做"取第一个非空槽"的兜底。
     */
    public static ItemStack getCenterItem() {
        ItemStack[] all = getAllSlots();
        return (all.length > 0) ? all[0] : ItemStack.EMPTY;
    }

    /** 返回该物品对应的 ToolStack，否则 null。 */
    public static Object getCenterToolStack() {
        ItemStack stack = getCenterItem();
        if (stack.isEmpty()) return null;
        try {
            Class<?> cls = Class.forName("slimeknights.tconstruct.library.tools.nbt.ToolStack");
            java.lang.reflect.Method m = cls.getMethod("from", ItemStack.class);
            return m.invoke(null, stack);
        } catch (Throwable ignored) {
            return null;
        }
    }


    /** 工匠砧 6 个槽：0=中心，1=左中，2=上中，3=右中，4=左下，5=右下。 */
    private static final int CRAFT_SLOT_COUNT = 6;

    public static ItemStack[] getAllSlots() {
        ItemStack[] out = new ItemStack[CRAFT_SLOT_COUNT];
        for (int i = 0; i < CRAFT_SLOT_COUNT; i++) out[i] = ItemStack.EMPTY;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) return out;

            Object menu = mc.screen.getClass().getMethod("getMenu").invoke(mc.screen);
            if (menu == null) return out;

            // AbstractContainerMenu.slots 是 public final List<Slot>
            java.lang.reflect.Field slotsField = null;
            Class<?> c = menu.getClass();
            while (c != null && c != Object.class) {
                try { slotsField = c.getDeclaredField("slots"); break; }
                catch (Throwable ignored) { c = c.getSuperclass(); }
            }
            if (slotsField == null) return out;
            slotsField.setAccessible(true);
            Object slotsObj = slotsField.get(menu);
            if (!(slotsObj instanceof List<?> slots)) return out;
            if (slots.size() < CRAFT_SLOT_COUNT) return out;

            // 只取前 6 个（合成槽）
            Object[] craft = new Object[CRAFT_SLOT_COUNT];
            int[] xs = new int[CRAFT_SLOT_COUNT];
            int[] ys = new int[CRAFT_SLOT_COUNT];
            for (int i = 0; i < CRAFT_SLOT_COUNT; i++) {
                Object slot = slots.get(i);
                craft[i] = slot;
                try {
                    java.lang.reflect.Field fx = slot.getClass().getField("x");
                    java.lang.reflect.Field fy = slot.getClass().getField("y");
                    xs[i] = fx.getInt(slot);
                    ys[i] = fy.getInt(slot);
                } catch (Throwable ignored) {}
            }

            // 按 x / y 排序求三列三行的边界值
            int[] sortedX = xs.clone();
            int[] sortedY = ys.clone();
            java.util.Arrays.sort(sortedX);
            java.util.Arrays.sort(sortedY);

            int colMin = sortedX[0];
            int colMid = sortedX[2];
            int colMax = sortedX[5];
            int rowMin = sortedY[0];
            int rowMid = sortedY[2];
            int rowMax = sortedY[5];

            // 按坐标归类到 6 个视觉位置
            for (int i = 0; i < CRAFT_SLOT_COUNT; i++) {
                int xi = xs[i], yi = ys[i];
                int idx = -1;
                if (near(xi, colMid) && near(yi, rowMid))      idx = 0; // 中心
                else if (near(xi, colMin) && near(yi, rowMid)) idx = 1; // 左中
                else if (near(xi, colMid) && near(yi, rowMin)) idx = 2; // 上中
                else if (near(xi, colMax) && near(yi, rowMid)) idx = 3; // 右中
                else if (near(xi, colMin) && near(yi, rowMax)) idx = 4; // 左下
                else if (near(xi, colMax) && near(yi, rowMax)) idx = 5; // 右下

                if (idx >= 0) {
                    java.lang.reflect.Method getItem =
                            craft[i].getClass().getMethod("getItem");
                    Object v = getItem.invoke(craft[i]);
                    out[idx] = (v instanceof ItemStack s) ? s : ItemStack.EMPTY;
                }
            }
        } catch (Throwable t) {
            System.out.println("[AnvilSlotAccess] getAllSlots error: " + t);
        }
        return out;
    }

    /** 判断两个坐标是否在容差范围内相等。 */
    private static boolean near(int a, int b) {
        return Math.abs(a - b) <= 4;
    }
}