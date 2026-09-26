package top.leipishu.anvilssearch.simulation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.lang.reflect.Method;

public final class ToolPreviewRenderer {

    private ToolPreviewRenderer() {}

    public static void drawIcon(PoseStack ps, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        try {
            Minecraft.getInstance().getItemRenderer().renderGuiItem(stack, x, y);
        } catch (Throwable ignored) {}
    }

    public static void drawIconWithCount(PoseStack ps, ItemStack stack,
                                         int x, int y, int count) {
        if (stack == null || stack.isEmpty()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.getItemRenderer().renderGuiItem(stack, x, y);
            if (count > 1) {
                mc.getItemRenderer().renderGuiItemDecorations(mc.font, stack, x, y,
                        String.valueOf(count));
            }
        } catch (Throwable ignored) {}
    }

    public static void drawStatLine(PoseStack ps, Font font,
                                    int x, int y,
                                    Component label, Object stats, String key) {
        String value = readStat(stats, key);
        font.draw(ps, "\u00A77" + label.getString() + ": \u00A7f" + value,
                x, y, 0xCCCCCC);
    }

    public static String readStat(Object statsObj, String key) {
        if (!(statsObj instanceof StatsNBT stats)) return "N/A";

        Object stat;
        switch (key) {
            case "attack":     stat = ToolStats.ATTACK_DAMAGE; break;
            case "speed":      stat = ToolStats.ATTACK_SPEED;  break;
            case "durability": stat = ToolStats.DURABILITY;    break;
            default: return "N/A";
        }

        Number v = invokeNumeric(stats, stat);
        if (v == null) return "N/A";

        switch (key) {
            case "attack":     return String.format("%.1f", v.floatValue());
            case "speed":      return String.format("%.2f", v.floatValue());
            case "durability": return String.valueOf(v.intValue());
        }
        return "N/A";
    }

    private static Number invokeNumeric(StatsNBT stats, Object stat) {
        if (stat == null) return null;
        for (String name : new String[]{"getFloat", "getInt", "get"}) {
            for (Method m : stats.getClass().getMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 1) continue;
                Class<?> param = m.getParameterTypes()[0];
                if (!param.isInstance(stat)) continue;
                try {
                    Object r = m.invoke(stats, stat);
                    if (r instanceof Number n) return n;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }
}