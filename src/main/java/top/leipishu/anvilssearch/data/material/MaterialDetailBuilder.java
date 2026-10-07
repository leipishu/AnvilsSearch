package top.leipishu.anvilssearch.data.material;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;


import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.IMaterialRegistry;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolPartsHook;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import top.leipishu.tinkerssearch.data.FluidPartData;
import top.leipishu.tinkerssearch.recipe.MaterialCompatibility;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MaterialDetailBuilder {

    private MaterialDetailBuilder() {}

    public static MaterialDetail build(MaterialId mat, IToolPart pr, Item partItem) {
        MaterialStatsId st = (pr != null) ? pr.getStatType() : inferStatType(partItem);
        return build(mat, st, partItem);
    }

    public static MaterialDetail build(MaterialId mat, MaterialStatsId statType, Item partItem) {
        if (mat == null) return null;

        // 1) tinkerssearch 缓存（浇筑部件）
        if (partItem != null) {
            ResourceLocation partId = ForgeRegistries.ITEMS.getKey(partItem);
            if (partId != null) {
                FluidPartData.PartInfo info = PartInfoLookup.find(mat, partId);
                if (info != null) return fromPartInfo(info, mat);
            }
        }

        // 2) 直查 MaterialRegistry（非浇筑部件 / 特殊部件）
        return buildDirect(mat, statType, partItem);
    }

    // ============================================================
    // ===== 材料显示名 ===========================================
    // ============================================================

    public static Component materialNameComponent(MaterialId mat) {
        if (mat == null) return Component.literal("?");

        IMaterial m = resolveMaterial(mat);

        if (m != null && m != IMaterial.UNKNOWN) {
            for (String mn : new String[]{"getTranslationKey", "getLocalizationKey"}) {
                try {
                    Method method = m.getClass().getMethod(mn);
                    Object v = method.invoke(m);
                    if (v instanceof String s && !s.isEmpty()) {
                        return Component.translatable(

s);
                    }
                } catch (Throwable ignored) {}
            }
            try {
                Method method = m.getClass().getMethod("getDisplayName");
                Object v = method.invoke(m);
                if (v instanceof Component c) return c;
            } catch (Throwable ignored) {}
        }

        try {
            return Component.translatable(


                    "material." + mat.getNamespace() + "." + mat.getPath());
        } catch (Throwable ignored) {
            return Component.literal(mat.getPath());
        }
    }

    private static IMaterial resolveMaterial(MaterialId mat) {
        try {
            IMaterial m = MaterialRegistry.getInstance().getMaterial(mat);
            if (m != null && m != IMaterial.UNKNOWN) return m;

            String path = mat.getPath();
            int sep = path.indexOf("__");
            if (sep > 0) {
                path = path.substring(0, sep);
                try {
                    MaterialId base = new MaterialId(mat.getNamespace(), path);
                    IMaterial b = MaterialRegistry.getInstance().getMaterial(base);
                    if (b != null && b != IMaterial.UNKNOWN) return b;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ============================================================
    // ===== 来自 PartInfo ========================================
    // ============================================================

    private static MaterialDetail fromPartInfo(FluidPartData.PartInfo info, MaterialId mat) {
        Component matName = materialNameComponent(mat);
        Component partName = Component.literal("\u00A77" + info.getDisplayName());

        List<Component> stats = new ArrayList<>();
        List<MaterialDetail.TraitTag> traits = new ArrayList<>();

        if (info.properties != null) {
            if (info.properties.statLines != null) stats.addAll(info.properties.statLines);
            if (info.properties.modifiers != null) {
                for (FluidPartData.ModifierInfo m : info.properties.modifiers) {
                    Component base = (m.displayName != null)
                            ? m.displayName
                            : Component.literal(m.id != null ? m.id : "?");
                    int color = extractColor(base);

                    Component display = base;
                    if (m.level > 1) {
                        display = Component.literal("").append(base)
                                .append(Component.literal(" " + m.level));
                    }

                    traits.add(new MaterialDetail.TraitTag(display, color,
                            m.descriptionLines != null ? m.descriptionLines : new ArrayList<>()));
                }
            }
        }

        return new MaterialDetail(info.displayStack, matName, partName,
                stats, traits, info.requiredAmount);
    }

    // ============================================================
    // ===== 直查 MaterialRegistry ================================
    // ============================================================

    private static MaterialDetail buildDirect(MaterialId mat, MaterialStatsId statType,
                                              Item partItem) {
        ItemStack icon = tryDisplayStack(mat, partItem);
        Component matName = materialNameComponent(mat);

        String partDisplay = "?";
        if (partItem != null) {
            try { partDisplay = new ItemStack(partItem).getHoverName().getString(); }
            catch (Throwable ignored) {}
        }
        Component partName = Component.literal("\u00A77" + partDisplay);

        List<Component> stats = new ArrayList<>();
        List<MaterialDetail.TraitTag> traits = new ArrayList<>();

        if (statType != null) {
            IMaterialRegistry reg = MaterialRegistry.getInstance();

            try {
                Optional<IMaterialStats> opt = reg.getMaterialStats(mat, statType);
                if (opt.isPresent()) {
                    List<Component> info = opt.get().getLocalizedInfo();
                    if (info != null) stats.addAll(info);
                }
            } catch (Throwable ignored) {}

            try {
                Object tm = getTraitsManager(reg);
                if (tm != null) {
                    Method m = tm.getClass().getMethod("getTraits",
                            MaterialId.class, MaterialStatsId.class);
                    @SuppressWarnings("unchecked")
                    List<ModifierEntry> entries =
                            (List<ModifierEntry>) m.invoke(tm, mat, statType);
                    if (entries != null) {
                        for (ModifierEntry e : entries) addTrait(traits, e);
                    }
                }
            } catch (Throwable ignored) {}
        }

        return new MaterialDetail(icon, matName, partName, stats, traits, -1);
    }

    // ============================================================
    // ===== 图标 / statType =====================================
    // ============================================================

    private static MaterialStatsId inferStatType(Item item) {
        if (item instanceof IMaterialItem mi) {
            try { return MaterialCompatibility.inferStatType(mi); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static ItemStack tryDisplayStack(MaterialId mat, Item partItem) {
        if (partItem == null) return ItemStack.EMPTY;

        // ★ 非 IMaterialItem 的特殊部件：直接返回物品本身（图标不反映材料，但不崩）
        if (!(partItem instanceof IMaterialItem)) {
            try { return new ItemStack(partItem); }
            catch (Throwable ignored) { return ItemStack.EMPTY; }
        }

        IMaterialItem mi = (IMaterialItem) partItem;

        for (String mn : new String[]{"withMaterialForDisplay", "withMaterial"}) {
            try {
                Method m = mi.getClass().getMethod(mn, MaterialId.class);
                Object r = m.invoke(mi, mat);
                if (r instanceof ItemStack is && !is.isEmpty()) return is;
            } catch (Throwable ignored) {}
        }

        ItemStack s = new ItemStack(partItem);
        try { s.getOrCreateTag().putString("Material", mat.toString()); }
        catch (Throwable ignored) {}
        return s;
    }

    // ============================================================
    // ===== 词条 =================================================
    // ============================================================

    private static void addTrait(List<MaterialDetail.TraitTag> out, ModifierEntry e) {
        if (e == null) return;
        try {
            Object mod = e.getModifier();
            if (mod == null) return;

            Component base = null;
            for (String mn : new String[]{"getDisplayName", "getColoredName", "getName"}) {
                try {
                    Method m = mod.getClass().getMethod(mn);
                    Object v = m.invoke(mod);
                    if (v instanceof Component c) { base = c; break; }
                } catch (Throwable ignored) {}
            }
            if (base == null) return;

            int color = extractColor(base);
            int level = 1;
            try { level = e.getLevel(); } catch (Throwable ignored) {}

            Component display = base;
            if (level > 1) {
                display = Component.literal("").append(base)
                        .append(Component.literal(" " + level));
            }

            List<Component> desc = new ArrayList<>();
            for (String mn : new String[]{"getDescriptionList", "getDescription"}) {
                try {
                    Method m = mod.getClass().getMethod(mn);
                    Object v = m.invoke(mod);
                    if (v instanceof List<?> list) {
                        for (Object o : list) if (o instanceof Component c) desc.add(c);
                        break;
                    } else if (v instanceof Component c) {
                        desc.add(c);
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            out.add(new MaterialDetail.TraitTag(display, color, desc));
        } catch (Throwable ignored) {}
    }

    private static int extractColor(Component c) {
        if (c == null) return 0xFFFFFF;
        try {
            TextColor tc = c.getStyle().getColor();
            if (tc != null) return tc.getValue();
        } catch (Throwable ignored) {}

        try {
            if (!c.getSiblings().isEmpty()) {
                for (Component s : c.getSiblings()) {
                    try {
                        TextColor tc = s.getStyle().getColor();
                        if (tc != null) return tc.getValue();
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        return 0xFFFFFF;
    }

    private static Object getTraitsManager(IMaterialRegistry reg) {
        try {
            Class<?> c = reg.getClass();
            while (c != null && c != Object.class) {
                for (String fn : new String[]{"materialTraitsManager", "traitsManager"}) {
                    try {
                        Field f = c.getDeclaredField(fn);
                        f.setAccessible(true);
                        Object v = f.get(reg);
                        if (v != null) return v;
                    } catch (NoSuchFieldException ignored) {}
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }
}