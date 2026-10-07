package top.leipishu.anvilssearch.data.modifier;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

/**
 * 前置条件检查工具。
 * 通过 {@code getValidatedResult} 反射校验配方是否可应用于当前工具。
 */
public final class ModifierRequirementChecker {

    private ModifierRequirementChecker() {}

    /**
     * 用给定工具构造一个临时 {@code ITinkerStationContainer}，
     * 供 {@code getValidatedResult} 验证使用。
     */
    public static Object makeContainer(ItemStack toolStack) {
        if (toolStack == null || toolStack.isEmpty()) return null;
        try {
            Class<?> modifiableClass = Class.forName(
                    "slimeknights.tconstruct.library.tools.item.IModifiable");
            if (!modifiableClass.isInstance(toolStack.getItem())) return null;

            Class<?> tsClass = Class.forName(
                    "slimeknights.tconstruct.library.tools.nbt.ToolStack");
            Method from = tsClass.getMethod("from", ItemStack.class);
            from.setAccessible(true);
            Object tool = from.invoke(null, toolStack);
            if (tool == null) return null;

            Class<?> containerClass = Class.forName(
                    "slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer");
            return Proxy.newProxyInstance(
                    containerClass.getClassLoader(),
                    new Class<?>[]{containerClass},
                    (proxy, method, args) -> {
                        String mn = method.getName();
                        if ("getTinkerable".equals(mn) && method.getParameterCount() == 0)
                            return tool;
                        if ("getTinkerableStack".equals(mn) && method.getParameterCount() == 0)
                            return toolStack;
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return false;
                        if (rt == int.class) return 0;
                        if (rt == long.class) return 0L;
                        if (rt == float.class) return 0f;
                        if (rt == double.class) return 0d;
                        return null;
                    });
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 检查配方是否满足给定容器的前置条件。
     *
     * @return null 无法判断；Optional.empty() 满足；Optional.of(Component) 不满足及原因。
     */
    public static Optional<Component> checkRequirements(Object recipe, Object container) {
        if (recipe == null || container == null) return null;

        try {
            Class<?> containerClass = Class.forName(
                    "slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer");
            if (!containerClass.isInstance(container)) return null;

            Method validateMethod = null;
            for (Method m : recipe.getClass().getMethods()) {
                if (!"getValidatedResult".equals(m.getName())) continue;
                if (m.getParameterCount() == 2) { validateMethod = m; break; }
            }
            if (validateMethod == null) {
                for (Method m : recipe.getClass().getMethods()) {
                    if (!"getValidatedResult".equals(m.getName())) continue;
                    if (m.getParameterCount() == 1) { validateMethod = m; break; }
                }
            }
            if (validateMethod == null) return null;

            Object registryAccess = Minecraft.getInstance().level != null
                    ? Minecraft.getInstance().level.registryAccess() : null;

            validateMethod.setAccessible(true);
            Object result = validateMethod.getParameterCount() == 2
                    ? validateMethod.invoke(recipe, container, registryAccess)
                    : validateMethod.invoke(recipe, container);

            if (result == null) return Optional.empty();

            Method isSuccessMethod = null;
            for (Method m : result.getClass().getMethods()) {
                if ("isSuccess".equals(m.getName()) && m.getParameterCount() == 0) {
                    isSuccessMethod = m; break;
                }
            }
            if (isSuccessMethod == null) return null;

            boolean success = (boolean) isSuccessMethod.invoke(result);
            if (success) return Optional.empty();

            Component message = extractFailureMessage(result);
            if (message != null) return Optional.of(message);
            return Optional.of(Component.literal(""));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 多路径尝试从 RecipeResult 提取失败消息。 */
    private static Component extractFailureMessage(Object result) {
        try {
            Method m = result.getClass().getMethod("getMessage");
            m.setAccessible(true);
            Object v = m.invoke(result);
            if (v instanceof Component c) return c;
            if (v instanceof String s && !s.isEmpty()) return Component.literal(s);
        } catch (Throwable ignored) {}

        try {
            Method m = result.getClass().getMethod("getMessageComponent");
            m.setAccessible(true);
            Object v = m.invoke(result);
            if (v instanceof Component c) return c;
            if (v instanceof String s && !s.isEmpty()) return Component.literal(s);
        } catch (Throwable ignored) {}

        try {
            Method m = result.getClass().getMethod("getError");
            m.setAccessible(true);
            Object v = m.invoke(result);
            if (v instanceof Component c) return c;
            if (v instanceof String s && !s.isEmpty()) return Component.literal(s);
        } catch (Throwable ignored) {}

        for (String fname : new String[]{"message", "error", "failureMessage", "reason"}) {
            try {
                Field f = result.getClass().getDeclaredField(fname);
                f.setAccessible(true);
                Object v = f.get(result);
                if (v instanceof Component c) return c;
                if (v instanceof String s && !s.isEmpty()) return Component.literal(s);
            } catch (Throwable ignored) {}
        }

        return null;
    }
}