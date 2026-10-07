package top.leipishu.anvilssearch.data.modifier;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IncrementalModifierRecipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static top.leipishu.anvilssearch.data.modifier.ModifierReflect.*;

/**
 * 从 RecipeManager 读取匠魂强化配方，生成 ModifierIndex.Entry。
 * 适配 Tinkers' Construct 1.20.1。
 *
 * <p>1.20.1 关键变更：
 * <ul>
 *   <li>槽位信息通过 {@code AbstractModifierRecipe.getSlots()} 返回 {@code SlotCount}，
 *       使用 {@code type()} 和 {@code count()} 读取。</li>
 *   <li>SwappableModifierRecipe 引入 {@code getVariant()}，返回变体名称 Component。</li>
 * </ul>
 */
final class ModifierIndexBuilder {

    private ModifierIndexBuilder() {}

    private static final Set<Class<?>> DIAGNOSED =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    // ============================================================
    // ===== 主流程 ===============================================
    // ============================================================

    static List<ModifierIndex.Entry> build() {
        long t0 = System.currentTimeMillis();

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            System.out.println("[Anvil's Search] ModifierIndex: no connection");
            return Collections.emptyList();
        }
        RecipeManager rm = mc.getConnection().getRecipeManager();

        List<Object> candidates = new ArrayList<>();
        int scanned = 0;

        Collection<Recipe<?>> allRecipes = rm.getRecipes();
        System.out.println("[Anvil's Search] total recipes in RecipeManager: "
                + allRecipes.size());

        for (Recipe<?> recipe : allRecipes) {
            if (recipe == null) continue;
            if (!isTinkerStationRecipe(recipe)) continue;
            if (!isDisplayModifierRecipe(recipe)) continue;

            scanned++;
            Class<?> cls = recipe.getClass();
            if (DIAGNOSED.add(cls)) {
                System.out.println("[Anvil's Search] ModifierRecipe diag class = "
                        + cls.getName());
            }
            candidates.add(recipe);
        }

        System.out.println("[Anvil's Search] TINKER_STATION candidates: " + candidates.size());

        List<Object> displayRecipes = new ArrayList<>();
        for (Object r : candidates) {
            List<?> subs = tryExpandMultiRecipe(r);
            if (subs != null && !subs.isEmpty()) {
                for (Object sub : subs) {
                    if (sub != null && isDisplayModifierRecipe(sub)) {
                        displayRecipes.add(sub);
                    }
                }
            } else {
                displayRecipes.add(r);
            }
        }

        System.out.println("[Anvil's Search] after MultiRecipe expand: "
                + displayRecipes.size());

        if (displayRecipes.isEmpty()) return Collections.emptyList();

        List<ModifierIndex.Entry> result = new ArrayList<>();
        int skippedEmpty = 0;
        for (Object display : displayRecipes) {
            List<ModifierIndex.Entry> entries = buildEntriesFromDisplay(display);
            if (entries.isEmpty()) {
                skippedEmpty++;
            } else {
                result.addAll(entries);
            }
        }

        result.sort(Comparator.comparing(ModifierIndex.Entry::getDisplayName,
                String.CASE_INSENSITIVE_ORDER));

        System.out.println("[Anvil's Search] ModifierIndex built: " + result.size()
                + " entries from " + scanned + " candidates / "
                + displayRecipes.size() + " display recipes, skipped empty = "
                + skippedEmpty + " in "
                + (System.currentTimeMillis() - t0) + "ms");
        return result;
    }

    // ============================================================
    // ===== 单条 display → Entry 列表 ============================
    // ============================================================

    private static List<ModifierIndex.Entry> buildEntriesFromDisplay(Object display) {
        List<ModifierIndex.Entry> entries = new ArrayList<>();
        try {
            Object entry = call(display, "getDisplayResult");
            if (entry == null) return entries;

            Object modifier = call(entry, "getModifier");
            if (modifier == null) return entries;

            String modifierId = extractModifierId(modifier);
            if (modifierId == null || modifierId.isEmpty()) return entries;

            int baseLevel = intOf(entry, "getLevel");
            if (baseLevel <= 0) baseLevel = 1;

            int color = extractColor(modifier);

            MaterialResult mr = readMaterialsFromDisplay(display);
            if (mr.icons.isEmpty()) return entries;

            // ★ 1.20.1：槽位读取（修复）
            List<ModifierIndex.SlotRequirement> slots = readSlotsFromDisplay(display);

            Object toolRequirement = readFieldAny(display,
                    "toolRequirement", "tools", "toolFilter", "toolIngredient");

            boolean incremental = display instanceof IncrementalModifierRecipe;

            int maxLevel = readMaxLevel(display);
            boolean unlimited = (maxLevel == 0);

            if (!unlimited) {
                if (maxLevel <= 0) maxLevel = baseLevel;
                if (maxLevel < baseLevel) maxLevel = baseLevel;
            }

            ModifierIndex.RecipeKind kind;
            if (unlimited) {
                kind = ModifierIndex.RecipeKind.UNLIMITED;
            } else if (incremental) {
                kind = ModifierIndex.RecipeKind.INCREMENTAL;
            } else {
                kind = detectKindByName(display);
            }

            int amountPerInput = 0, neededPerLevel = 0;
            if (incremental) {
                amountPerInput = intField(display, "amountPerInput", "amount_per_input");
                neededPerLevel = intField(display, "neededPerLevel", "needed_per_level");
                if (amountPerInput <= 0) amountPerInput = 1;
                if (neededPerLevel <= 0) neededPerLevel = 1;
            }

            String requirementsError = null;
            if (boolOf(display, "hasRequirements")) {
                Object err = call(display, "getRequirementsError");
                if (err instanceof Component c) {
                    requirementsError = resolveTranslation(c.getString());
                } else if (err instanceof String s && !s.isEmpty()) {
                    requirementsError = resolveTranslation(s);
                }
            }

            // ★ 1.20.1：变体读取（新增）
            Component variant = readVariant(display);

            Object toolFilter = readToolRequirement(display);

            String path = modifierId;
            int colon = modifierId.indexOf(':');
            if (colon >= 0) path = modifierId.substring(colon + 1);

            if (unlimited || incremental) {
                Component dn = extractDisplayName(modifier, baseLevel);
                ModifierIndex.LevelInfo lv = new ModifierIndex.LevelInfo(
                        baseLevel, dn, slots, mr.icons, mr.lines, mr.slotIcons,
                        toolFilter, toolRequirement, display,
                        kind, amountPerInput, neededPerLevel,
                        requirementsError, variant);

                String id = modifierId + "#" + baseLevel;
                entries.add(new ModifierIndex.Entry(modifier, display, id, path, color,
                        maxLevel, Collections.singletonList(lv)));
                return entries;
            }

            if (maxLevel > baseLevel) {
                for (int level = baseLevel; level <= maxLevel; level++) {
                    Component dn = extractDisplayName(modifier, level);
                    ModifierIndex.LevelInfo lv = new ModifierIndex.LevelInfo(
                            level, dn, slots, mr.icons, mr.lines, mr.slotIcons,
                            toolFilter, toolRequirement, display,
                            kind, amountPerInput, neededPerLevel,
                            requirementsError, variant);

                    String id = modifierId + "#" + level;
                    entries.add(new ModifierIndex.Entry(modifier, display, id, path, color,
                            maxLevel, Collections.singletonList(lv)));
                }
            } else {
                Component dn = extractDisplayName(modifier, baseLevel);
                ModifierIndex.LevelInfo lv = new ModifierIndex.LevelInfo(
                        baseLevel, dn, slots, mr.icons, mr.lines, mr.slotIcons,
                        toolFilter, toolRequirement, display,
                        kind, amountPerInput, neededPerLevel,
                        requirementsError, variant);

                String id = modifierId + "#" + baseLevel;
                entries.add(new ModifierIndex.Entry(modifier, display, id, path, color,
                        maxLevel, Collections.singletonList(lv)));
            }
        } catch (Throwable t) {
            System.err.println("[Anvil's Search] failed to build entries: " + t);
        }
        return entries;
    }

    // ============================================================
    // ===== 1.20.1 槽位读取（修复）===============================
    // ============================================================

    /**
     * 1.20.1 中 {@code AbstractModifierRecipe.getSlots()} 返回 {@code SlotCount}。
     * {@code SlotCount} 是 record，包含 {@code type()} 和 {@code count()}。
     * 返回 null 表示该配方不需要槽位。
     */
    private static List<ModifierIndex.SlotRequirement> readSlotsFromDisplay(Object display) {
        Object slots = call(display, "getSlots");
        if (slots == null) {
            return Collections.singletonList(new ModifierIndex.SlotRequirement(
                    "none", Component.translatable(
                    "gui.anvilssearch.slot.none"), 0));
        }

        // SlotCount 是 record，使用 type() / count()
        Object type = call(slots, "type");
        int count = intOf(slots, "count");

        if (type == null) {
            return Collections.singletonList(new ModifierIndex.SlotRequirement(
                    "none", Component.translatable(
                    "gui.anvilssearch.slot.none"), 0));
        }

        String typeName = slotTypeName(type);
        String norm = normalizeSlotType(typeName);
        return Collections.singletonList(new ModifierIndex.SlotRequirement(
                norm, slotTypeDisplayName(norm), Math.max(0, count)));
    }

    // ============================================================
    // ===== 1.20.1 变体读取（新增）===============================
    // ============================================================

    /**
     * 读取配方的变体名称。只有 SwappableModifierRecipe 才有变体，
     * 其他配方返回 null。
     */
    private static Component readVariant(Object display) {
        try {
            Object variantObj = call(display, "getVariant");
            if (variantObj instanceof Component vc) {
                String vk = vc.getString();
                if (vk != null && !vk.isEmpty()
                        && !vk.startsWith("recipe.tconstruct")
                        && !vk.startsWith("gui.tconstruct")) {
                    return vc;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ============================================================
    // ===== 1.20.1 maxLevel 读取（保持不变）======================
    // ============================================================

    private static int readMaxLevel(Object display) {
        try {
            Class<?> c = display.getClass();
            while (c != null && c != Object.class) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField("level");
                    f.setAccessible(true);
                    Object val = f.get(display);
                    if (val instanceof slimeknights.tconstruct.library.json.IntRange range) {
                        int maxLevel = range.max();
                        if (maxLevel >= 32767) {
                            return 0;
                        }
                        return maxLevel;
                    }
                } catch (NoSuchFieldException e) {
                    // 继续向上找父类
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return 1;
    }

    // ============================================================
    // ===== 材料读取 =============================================
    // ============================================================

    private static final class MaterialResult {
        final List<ItemStack> icons;
        final List<Component> lines;
        final List<ItemStack>[] slotIcons;

        @SuppressWarnings("unchecked")
        MaterialResult(List<ItemStack> icons, List<Component> lines,
                       List<ItemStack>[] slotIcons) {
            this.icons = icons != null ? icons : Collections.emptyList();
            this.lines = lines != null ? lines : Collections.emptyList();
            this.slotIcons = slotIcons;
        }
    }

    @SuppressWarnings("unchecked")
    private static MaterialResult readMaterialsFromDisplay(Object display) {
        List<ItemStack> icons = new ArrayList<>();
        List<Component> lines = new ArrayList<>();
        List<ItemStack>[] slotIcons = new List[5];
        for (int i = 0; i < 5; i++) slotIcons[i] = Collections.emptyList();

        boolean incremental = display instanceof IncrementalModifierRecipe;

        if (incremental) {
            Object inputsObj = call(display, "getInputs");
            if (inputsObj instanceof List<?> slotList) {
                for (int slot = 0; slot < Math.min(slotList.size(), 5); slot++) {
                    Object slotItems = slotList.get(slot);
                    if (!(slotItems instanceof List<?> items) || items.isEmpty()) continue;
                    List<ItemStack> slotMats = new ArrayList<>();
                    for (Object o : items) {
                        if (!(o instanceof ItemStack stack) || stack.isEmpty()) continue;
                        ItemStack copy = stack.copy();
                        slotMats.add(copy);
                        icons.add(copy);
                        lines.add(Component.literal("\u00A77"
                                + copy.getHoverName().getString()
                                + (copy.getCount() > 1 ? " \u00D7" + copy.getCount() : "")));
                    }
                    slotIcons[slot] = slotMats;
                }
            }
            return new MaterialResult(icons, lines, slotIcons);
        }

        Object direct = call(display, "getInputs");
        if (direct == null) direct = call(display, "getDisplayInputs");
        if (direct instanceof Collection<?> col && !col.isEmpty()) {
            return materializeInputs(col, slotIcons);
        }

        for (int slot = 0; slot <= 4; slot++) {
            Object v = call(display, "getDisplayItems", int.class, slot);
            if (!(v instanceof List<?> list) || list.isEmpty()) continue;

            List<ItemStack> slotList = new ArrayList<>();
            for (Object o : list) {
                if (!(o instanceof ItemStack stack) || stack.isEmpty()) continue;
                ItemStack copy = stack.copy();
                slotList.add(copy);
                icons.add(copy);
                lines.add(Component.literal("\u00A77"
                        + copy.getHoverName().getString()
                        + (copy.getCount() > 1 ? " \u00D7" + copy.getCount() : "")));
            }
            slotIcons[slot] = slotList;
        }

        return new MaterialResult(icons, lines, slotIcons);
    }

    @SuppressWarnings("unchecked")
    private static MaterialResult materializeInputs(Collection<?> inputs,
                                                    List<ItemStack>[] slotIcons) {
        List<ItemStack> icons = new ArrayList<>();
        List<Component> lines = new ArrayList<>();
        Map<String, Integer> countMap = new LinkedHashMap<>();
        Map<String, ItemStack> repMap = new LinkedHashMap<>();

        for (Object sized : inputs) {
            if (sized == null) continue;
            ItemStack stack = extractStackFromSized(sized);
            int count = extractCount(sized);
            if (stack.isEmpty()) continue;

            ItemStack iconCopy = stack.copy();
            iconCopy.setCount(count);
            icons.add(iconCopy);

            String key = stackKey(stack);
            countMap.merge(key, count, Integer::sum);
            repMap.putIfAbsent(key, stack);
        }

        for (Map.Entry<String, Integer> e : countMap.entrySet()) {
            ItemStack rep = repMap.get(e.getKey());
            lines.add(Component.literal("\u00A77"
                    + rep.getHoverName().getString() + " \u00D7" + e.getValue()));
        }
        return new MaterialResult(icons, lines, slotIcons);
    }

    // ============================================================
    // ===== 接口检查 =============================================
    // ============================================================

    private static boolean implementsInterface(Class<?> cls, String interfaceName) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Class<?> iface : c.getInterfaces()) {
                if (iface.getSimpleName().equals(interfaceName)) return true;
                if (implementsInterface(iface, interfaceName)) return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    private static boolean isTinkerStationRecipe(Recipe<?> recipe) {
        return implementsInterface(recipe.getClass(), "ITinkerStationRecipe");
    }

    private static boolean isDisplayModifierRecipe(Object recipe) {
        if (recipe == null) return false;
        return implementsInterface(recipe.getClass(), "IDisplayModifierRecipe")
                || implementsInterface(recipe.getClass(), "IModifierRecipe");
    }

    private static boolean isMultiRecipe(Object recipe) {
        return implementsInterface(recipe.getClass(), "IMultiRecipe");
    }

    private static List<?> tryExpandMultiRecipe(Object recipe) {
        if (!isMultiRecipe(recipe)) return null;
        for (String mn : new String[]{"getRecipes", "getLevelRecipes", "getSubRecipes"}) {
            Object v = call(recipe, mn);
            if (v instanceof List<?> list && !list.isEmpty()) return list;
            if (v instanceof Collection<?> col && !col.isEmpty())
                return new ArrayList<>(col);
        }
        return null;
    }

    // ============================================================
    // ===== 名称 / 颜色 / 工具需求 ===============================
    // ============================================================

    private static String extractModifierId(Object modifier) {
        Object v = call(modifier, "getId");
        if (v == null) v = call(modifier, "getIdentifier");
        if (v == null) v = call(modifier, "getRegistryName");
        return v != null ? v.toString() : null;
    }

    private static Component extractDisplayName(Object modifier, int level) {
        Object v = call(modifier, "getDisplayName", int.class, level);
        if (v instanceof Component c) return c;
        String id = extractModifierId(modifier);
        String base = id != null ? prettify(id) : "?";
        return Component.literal(base + (level > 1 ? " " + level : ""));
    }

    private static int extractColor(Object modifier) {
        Object v = call(modifier, "getDisplayName", int.class, 1);
        if (v instanceof Component c) {
            TextColor tc = c.getStyle().getColor();
            if (tc != null) return tc.getValue();
            for (Component s : c.getSiblings()) {
                TextColor tc2 = s.getStyle().getColor();
                if (tc2 != null) return tc2.getValue();
            }
        }
        return 0xFFFFFF;
    }

    private static Object readToolRequirement(Object display) {
        Object v = call(display, "getToolRequirement");
        if (v == null) v = call(display, "getToolIngredient");
        if (v == null) v = field(display,
                "toolRequirement", "tools", "toolFilter", "toolIngredient");
        return v;
    }

    private static ModifierIndex.RecipeKind detectKindByName(Object display) {
        Class<?> c = display.getClass();
        while (c != null && c != Object.class) {
            String n = c.getSimpleName();
            if (n.contains("Incremental")) return ModifierIndex.RecipeKind.INCREMENTAL;
            if (n.contains("Multilevel") || n.contains("MultiLevel"))
                return ModifierIndex.RecipeKind.MULTILEVEL;
            if (n.contains("Swappable")) return ModifierIndex.RecipeKind.SWAPPABLE;
            c = c.getSuperclass();
        }
        return ModifierIndex.RecipeKind.SIMPLE;
    }

    // ============================================================
    // ===== 槽位类型 =============================================
    // ============================================================

    private static String slotTypeName(Object slotType) {
        if (slotType == null) return null;
        if (slotType instanceof String s) return s;
        Object v = call(slotType, "getName");
        if (v == null) v = call(slotType, "getId");
        return v != null ? String.valueOf(v) : String.valueOf(slotType);
    }

    private static String normalizeSlotType(String raw) {
        if (raw == null || raw.isEmpty()) return "none";
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("upgrade"))  return "upgrade";
        if (lower.contains("abilit"))   return "ability";
        if (lower.contains("defense"))  return "defense";
        if (lower.contains("soul"))     return "soul";
        int colon = lower.lastIndexOf(':');
        if (colon >= 0 && colon < lower.length() - 1) lower = lower.substring(colon + 1);
        return lower.isEmpty() ? "none" : lower;
    }

    private static Component slotTypeDisplayName(String typeId) {
        if (typeId == null || typeId.isEmpty()) return Component.literal("?");
        if ("none".equals(typeId)) {
            return Component.translatable("gui.anvilssearch.slot.none");
        }
        Component tc = Component.translatable("gui.anvilssearch.slot." + typeId);
        String s = tc.getString();
        if (tc.getContents() instanceof TranslatableContents contents
                && s != null && s.equals(contents.getKey())) {
            String p = typeId;
            if (!p.isEmpty()) p = Character.toUpperCase(p.charAt(0)) + p.substring(1);
            return Component.literal(p);
        }
        return tc;
    }

    // ============================================================
    // ===== ItemStack 抽取 =======================================
    // ============================================================

    private static ItemStack extractStackFromSized(Object sized) {
        if (sized == null) return ItemStack.EMPTY;
        if (sized instanceof ItemStack s) return s.isEmpty() ? ItemStack.EMPTY : s.copy();
        Object v = call(sized, "getIngredient");
        if (v == null) v = call(sized, "getInner");
        if (v == null) v = call(sized, "getItem");
        if (v == null) v = call(sized, "getStack");
        return extractFromAny(v, 2);
    }

    private static ItemStack extractFromAny(Object v, int depth) {
        if (v == null || depth < 0) return ItemStack.EMPTY;
        if (v instanceof ItemStack s) return s.isEmpty() ? ItemStack.EMPTY : s.copy();
        if (v instanceof net.minecraft.world.item.Item item) return new ItemStack(item);
        if (v instanceof ItemStack[] arr) {
            for (ItemStack s : arr) if (s != null && !s.isEmpty()) return s.copy();
        }
        if (v instanceof Object[] arr2) {
            for (Object o : arr2) {
                ItemStack s = extractFromAny(o, depth - 1);
                if (!s.isEmpty()) return s;
            }
        }
        if (v instanceof Collection<?> col) {
            for (Object o : col) {
                ItemStack s = extractFromAny(o, depth - 1);
                if (!s.isEmpty()) return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static int extractCount(Object sized) {
        Object v = call(sized, "getCount");
        if (v == null) v = call(sized, "getAmount");
        if (v == null) v = call(sized, "getNeeded");
        return v instanceof Number n ? n.intValue() : 1;
    }

    // ============================================================
    // ===== 翻译 / 美化 ==========================================
    // ============================================================

    private static String resolveTranslation(String key) {
        if (key == null || key.isEmpty()) return key;
        if (key.startsWith("recipe.tconstruct")
                || key.startsWith("gui.tconstruct")
                || key.startsWith("modifier.tconstruct")) {
            try {
                Component tc = Component.translatable(key);
                String translated = tc.getString();
                if (tc.getContents() instanceof TranslatableContents contents
                        && !translated.equals(contents.getKey())) {
                    return translated;
                }
            } catch (Throwable ignored) {}
            return Component.translatable(
                    "gui.anvilssearch.modifier.requirements_generic").getString();
        }
        return key;
    }

    private static String prettify(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] w = path.split("[_:/]");
        StringBuilder sb = new StringBuilder();
        for (String s : w) {
            if (s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(s.charAt(0)));
            if (s.length() > 1) sb.append(s.substring(1));
        }
        return sb.length() > 0 ? sb.toString() : path;
    }
}