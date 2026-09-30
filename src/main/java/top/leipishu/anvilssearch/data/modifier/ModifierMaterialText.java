package top.leipishu.anvilssearch.data.modifier;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 材料文本构建 + 宽度安全换行。
 */
public final class ModifierMaterialText {

    private ModifierMaterialText() {}

    /**
     * 生成材料说明。
     * <p>规则：
     * <ul>
     *   <li>每个槽位的候选材料用 "/" 连接，例如 "丝绢/蜘蛛丝"；</li>
     *   <li>相同候选组的槽位合并数量，例如 5 个 [丝绢] 槽位 → "丝绢 ×5"；</li>
     *   <li>不同候选组的槽位分别输出。</li>
     * </ul>
     * <p>注意：候选组内多个候选 ItemStack 代表"任选其一"，
     * 数量统计按 <b>槽位数 × 该槽位需求</b> 计算，不累加候选数。
     */
    @SuppressWarnings("unchecked")
    public static List<Component> buildMaterialLines(ModifierIndex.LevelInfo li) {
        List<Component> out = new ArrayList<>();
        if (li == null) return out;

        if (li.slotMaterials == null) {
            return li.materialLines;
        }

        // key = 候选组签名（按顺序拼接 stackKey）
        // value = 该组涉及的"槽位需求数量总和"
        Map<String, Integer> groupTotal = new LinkedHashMap<>();
        Map<String, List<ItemStack>> groupRep = new LinkedHashMap<>();

        for (List<ItemStack> candidates : li.slotMaterials) {
            if (candidates == null || candidates.isEmpty()) continue;

            StringBuilder sig = new StringBuilder();
            for (ItemStack s : candidates) {
                if (s == null) continue;
                sig.append(stackKeyOf(s)).append('|');
            }
            String key = sig.toString();

            // 该槽位需要的数量：取第一个非空候选的 count。
            // 同一槽位内候选的数量应一致（都表示"该槽位需要 N 个"），不累加候选数。
            int slotNeed = 1;
            for (ItemStack s : candidates) {
                if (s != null && !s.isEmpty()) {
                    slotNeed = Math.max(1, s.getCount());
                    break;
                }
            }

            groupTotal.merge(key, slotNeed, Integer::sum);
            groupRep.putIfAbsent(key, candidates);
        }

        if (groupTotal.isEmpty()) return li.materialLines;

        for (Map.Entry<String, Integer> e : groupTotal.entrySet()) {
            List<ItemStack> reps = groupRep.get(e.getKey());
            int total = e.getValue();

            StringBuilder names = new StringBuilder();
            for (int i = 0; i < reps.size(); i++) {
                if (i > 0) names.append('/');
                names.append(reps.get(i).getHoverName().getString());
            }

            out.add(new TextComponent("\u00A77"
                    + names.toString()
                    + (total > 1 ? " \u00D7" + total : "")));
        }
        return out;
    }

    public static String stackKeyOf(ItemStack stack) {
        try {
            String regName = stack.getItem().getRegistryName() != null
                    ? stack.getItem().getRegistryName().toString()
                    : stack.getItem().toString();
            String nbt = stack.getTag() != null ? stack.getTag().toString() : "";
            return regName + "|" + nbt;
        } catch (Throwable t) {
            return stack.toString();
        }
    }

    /**
     * 按宽度自动换行。
     * 保留 § 颜色代码：换行后每行开头重新附加当前生效的颜色。
     */
    public static List<Component> wrapComponent(Font font, Component src, int maxW) {
        List<Component> out = new ArrayList<>();
        if (src == null) return out;
        String text = src.getString();
        if (text == null || text.isEmpty()) {
            out.add(new TextComponent(""));
            return out;
        }
        if (font.width(text) <= maxW) {
            out.add(new TextComponent(text));
            return out;
        }

        // 当前生效的颜色代码（如 "§7"）
        String currentColor = "";

        int start = 0;
        int len = text.length();
        while (start < len) {
            int end = start;
            int lastSpace = -1;

            // 本行起点附加当前颜色
            String linePrefix = currentColor;

            while (end < len) {
                char c = text.charAt(end);

                // 遇到 §，记录颜色代码并跳过
                if (c == '\u00A7' && end + 1 < len) {
                    char code = Character.toLowerCase(text.charAt(end + 1));
                    if (isColorCode(code)) {
                        currentColor = "\u00A7" + text.charAt(end + 1);
                    }
                    if (font.width(linePrefix
                            + text.substring(start, Math.min(end + 2, len))) > maxW
                            && end > start) {
                        break;
                    }
                    end += 2;
                    continue;
                }

                if (font.width(linePrefix + text.substring(start, end + 1)) > maxW) {
                    break;
                }
                if (c == ' ') {
                    lastSpace = end;
                }
                end++;
            }

            if (end >= len) {
                out.add(new TextComponent(linePrefix + text.substring(start)));
                break;
            }

            int cut;
            if (lastSpace > start) {
                cut = lastSpace + 1;
            } else {
                cut = end;
            }
            if (cut <= start) cut = start + 1;

            // 取本行文本（含内部颜色代码）
            String line = text.substring(start, cut);
            out.add(new TextComponent(linePrefix + line));
            start = cut;
        }
        return out;
    }

    /** 判断是否是 § 颜色/格式代码（0-9 a-f k-o r）。 */
    private static boolean isColorCode(char c) {
        c = Character.toLowerCase(c);
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'k' && c <= 'o')
                || c == 'r';
    }
}