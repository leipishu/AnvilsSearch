package top.leipishu.anvilssearch.simulation;

import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolPartsHook;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;

import java.util.*;

public final class ToolSimulationModel {

    private ToolDefinition selectedTool;
    private List<IToolPart> slots = Collections.emptyList();
    private final Map<Integer, MaterialId> selections = new LinkedHashMap<>();

    private StatsNBT cachedStats;
    private ItemStack cachedStack;
    private List<ModifierEntry> cachedToolTraits = Collections.emptyList();
    private boolean dirty = true;

    public ToolDefinition getSelectedTool() { return selectedTool; }
    public int getSlotCount() { return slots.size(); }
    public List<IToolPart> getSlots() { return slots; }
    public IToolPart getSlot(int i) {
        return (i >= 0 && i < slots.size()) ? slots.get(i) : null;
    }
    public MaterialId getSelection(int i) { return selections.get(i); }

    public void selectTool(ToolDefinition def) {
        this.selectedTool = def;
        this.selections.clear();
        if (def == null || !def.isDataLoaded()) {
            this.slots = Collections.emptyList();
        } else {
            // ★ 1.19.2：ToolPartsHook.parts(definition)
            List<IToolPart> p = ToolPartsHook.parts(def);
            this.slots = (p != null) ? new ArrayList<>(p) : Collections.emptyList();
        }
        System.out.println("[Anvil's Search] selectTool: "
                + (def != null ? def.getId() : "null") + " slots=" + slots.size());
        dirty = true;
    }

    public void setSelection(int index, MaterialId mat) {
        if (index < 0 || index >= slots.size()) return;
        if (mat == null) selections.remove(index);
        else             selections.put(index, mat);
        dirty = true;
    }

    public boolean isComplete() {
        if (selectedTool == null || slots.isEmpty()) return false;
        for (int i = 0; i < slots.size(); i++) {
            if (!selections.containsKey(i)) return false;
        }
        return true;
    }

    public void reset() {
        selectedTool = null;
        slots = Collections.emptyList();
        selections.clear();
        cachedStats = null;
        cachedStack = null;
        cachedToolTraits = Collections.emptyList();
        dirty = true;
    }

    public StatsNBT getStats() {
        if (dirty) rebuild();
        return cachedStats;
    }

    public ItemStack getPreviewStack() {
        if (dirty) rebuild();
        return cachedStack == null ? ItemStack.EMPTY : cachedStack;
    }

    public List<ModifierEntry> getToolTraits() {
        if (dirty) rebuild();
        return cachedToolTraits;
    }

    private void rebuild() {
        dirty = false;
        if (selectedTool == null || slots.isEmpty()) {
            cachedStats = null;
            cachedStack = null;
            cachedToolTraits = Collections.emptyList();
            return;
        }
        ToolStatsCalculator.Result r =
                ToolStatsCalculator.calculate(selectedTool, selections);
        cachedStats = r.stats;
        cachedStack = r.stack;
        cachedToolTraits = r.toolTraits;
    }
}