package top.leipishu.anvilssearch.simulation;

import slimeknights.tconstruct.library.materials.definition.MaterialId;

import java.util.LinkedHashMap;
import java.util.Map;

/** 一份工具配置快照。 */
public final class ToolPreset {

    public final String toolId;
    public final Map<Integer, MaterialId> materials;
    public final long savedAt;

    public ToolPreset(String toolId,
                      Map<Integer, MaterialId> materials,
                      long savedAt) {
        this.toolId = toolId;
        this.materials = materials != null
                ? new LinkedHashMap<>(materials)
                : new LinkedHashMap<>();
        this.savedAt = savedAt;
    }
}