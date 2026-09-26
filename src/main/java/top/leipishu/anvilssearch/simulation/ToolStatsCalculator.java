package top.leipishu.anvilssearch.simulation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.definition.PartRequirement;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.*;

public final class ToolStatsCalculator {

    public static final class Result {
        public final StatsNBT  stats;
        public final ItemStack stack;
        public final List<ModifierEntry> toolTraits;

        public Result(StatsNBT stats, ItemStack stack, List<ModifierEntry> toolTraits) {
            this.stats = stats;
            this.stack = stack;
            this.toolTraits = toolTraits != null ? toolTraits : Collections.emptyList();
        }
    }

    private ToolStatsCalculator() {}

    public static List<PartRequirement> getRequiredParts(ToolDefinition td) {
        if (td == null) return Collections.emptyList();
        try {
            if (!td.isDataLoaded()) return Collections.emptyList();
            List<PartRequirement> parts = td.getData().getParts();
            if (parts == null || parts.isEmpty()) return Collections.emptyList();
            return new ArrayList<>(parts);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    public static String getSlotDisplayName(PartRequirement pr) {
        if (pr == null) return "?";

        try {
            Item item = extractPartItem(pr);
            if (item != null) {
                String s = new ItemStack(item).getHoverName().getString();
                if (s != null && !s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {}

        try {
            Object st = pr.getStatType();
            if (st != null) {
                String path = null;
                try {
                    java.lang.reflect.Method m = st.getClass().getMethod("getLocation");
                    Object loc = m.invoke(st);
                    if (loc instanceof ResourceLocation rl) path = rl.getPath();
                } catch (Throwable ignored) {}
                if (path == null) {
                    String s = String.valueOf(st);
                    int colon = s.indexOf(':');
                    path = (colon >= 0) ? s.substring(colon + 1) : s;
                }

                String[] keys = {
                        "stat.tconstruct." + path,
                        "gui.tconstruct.material_stats." + path,
                        "material_stat.tconstruct." + path,
                        "tconstruct.material_stats." + path,
                };
                for (String k : keys) {
                    String s = new net.minecraft.network.chat.TranslatableComponent(k).getString();
                    if (s != null && !s.equals(k) && !s.isEmpty()) return s;
                }
            }
        } catch (Throwable ignored) {}

        try {
            return prettify(String.valueOf(pr.getStatType()));
        } catch (Throwable t) {
            return "?";
        }
    }

    public static Item extractPartItem(PartRequirement pr) {
        if (pr == null) return null;

        for (String mn : new String[]{"getPart", "getMaterialItem", "getItem"}) {
            try {
                java.lang.reflect.Method m = pr.getClass().getMethod(mn);
                Object v = m.invoke(pr);
                if (v instanceof slimeknights.tconstruct.library.tools.part.IMaterialItem mi) return mi.asItem();
                if (v instanceof Item item) return item;
            } catch (Throwable ignored) {}
        }

        for (String fn : new String[]{"part", "item", "materialItem"}) {
            try {
                java.lang.reflect.Field f = pr.getClass().getDeclaredField(fn);
                f.setAccessible(true);
                Object v = f.get(pr);
                if (v instanceof slimeknights.tconstruct.library.tools.part.IMaterialItem mi) return mi.asItem();
                if (v instanceof Item item) return item;
            } catch (Throwable ignored) {}
        }

        return null;
    }

    public static ResourceLocation getPartItemId(PartRequirement pr) {
        Item item = extractPartItem(pr);
        if (item == null) return null;
        return ForgeRegistries.ITEMS.getKey(item);
    }

    public static Result calculate(ToolDefinition td, Map<Integer, MaterialId> selections) {
        if (td == null) return empty();
        if (selections == null) selections = Collections.emptyMap();
        if (!td.isDataLoaded()) return empty();

        Item item = findToolItem(td);
        if (item == null) return empty();

        List<PartRequirement> parts = td.getData().getParts();
        if (parts == null || parts.isEmpty()) return empty();

        List<String> materialStrings = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            MaterialId mid = selections.get(i);
            if (mid == null) return empty();
            materialStrings.add(mid.toString());
        }

        try {
            CompoundTag tag = new CompoundTag();
            ListTag matList = new ListTag();
            for (String s : materialStrings) {
                matList.add(StringTag.valueOf(s));
            }
            tag.put("tic_materials", matList);

            ToolStack stack = ToolStack.from(item, td, tag);
            stack.ensureHasData();

            List<ModifierEntry> toolTraits = new ArrayList<>();
            try {
                ModifierNBT modNbt = stack.getModifiers();
                if (modNbt != null) {
                    toolTraits.addAll(modNbt.getModifiers());
                }
            } catch (Throwable ignored) {}

            StatsNBT stats = stack.getStats();
            ItemStack preview = stack.createStack();

            System.out.println("[Anvil's Search] built tool " + td.getId()
                    + " materials=" + materialStrings
                    + " traits=" + toolTraits.size());

            return new Result(stats, preview, toolTraits);
        } catch (Throwable t) {
            System.err.println("[Anvil's Search] ToolStack build failed: " + t);
            return empty();
        }
    }

    private static Result empty() {
        return new Result(null, ItemStack.EMPTY, Collections.emptyList());
    }

    private static Item findToolItem(ToolDefinition td) {
        ResourceLocation id = td.getId();
        if (id == null) return null;
        try {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) return null;
            return item;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String prettify(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] w = path.split("_");
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