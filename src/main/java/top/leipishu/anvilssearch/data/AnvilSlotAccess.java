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

    /** 返回中央物品槽上的物品。 */
    public static ItemStack getCenterItem() {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return ItemStack.EMPTY;

        AbstractContainerMenu menu = acs.getMenu();
        if (menu == null) return ItemStack.EMPTY;

        // 优先：menu.slots index 2
        try {
            if (menu.slots.size() > 2) {
                Slot s = menu.slots.get(2);
                if (s != null && s.hasItem()) return s.getItem().copy();
            }
        } catch (Throwable ignored) {}

        // 兜底：遍历非玩家背包的槽位，找第一个非空的
        for (Slot s : menu.slots) {
            if (s == null || !s.hasItem()) continue;
            try {
                if (s.container instanceof net.minecraft.world.entity.player.Inventory)
                    continue;
            } catch (Throwable ignored) {}
            return s.getItem().copy();
        }

        return ItemStack.EMPTY;
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
}