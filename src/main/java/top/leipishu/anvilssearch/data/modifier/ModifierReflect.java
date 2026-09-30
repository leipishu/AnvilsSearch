package top.leipishu.anvilssearch.data.modifier;

import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 反射工具集合。所有沿继承链查找方法/字段的逻辑集中在这里。
 */
final class ModifierReflect {

    private ModifierReflect() {}

    // ---- 方法调用 ----

    static Object call(Object target, String name) {
        return call(target, name, new Class<?>[0]);
    }

    static Object call(Object target, String name, Class<?> pType, Object arg) {
        return call(target, name, new Class<?>[]{pType}, arg);
    }

    static Object call(Object target, String name, Class<?>[] pTypes, Object... args) {
        if (target == null) return null;
        Method m = findMethod(target.getClass(), name, pTypes);
        if (m == null) return null;
        try {
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ---- 返回值转换 ----

    static int intOf(Object target, String name) {
        Object v = call(target, name);
        return v instanceof Number n ? n.intValue() : 0;
    }

    static boolean boolOf(Object target, String name) {
        Object v = call(target, name);
        return v instanceof Boolean b && b;
    }

    static int intField(Object target, String... names) {
        Object v = field(target, names);
        return v instanceof Number n ? n.intValue() : 0;
    }

    // ---- 查找 ----

    static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try { return c.getDeclaredMethod(name, params); }
            catch (Throwable ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }

    static Object field(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            Class<?> c = target.getClass();
            while (c != null && c != Object.class) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    Object v = f.get(target);
                    if (v != null) return v;
                } catch (Throwable ignored) {}
                c = c.getSuperclass();
            }
        }
        return null;
    }

    /** readFieldAny 是 field 的别名。 */
    static Object readFieldAny(Object obj, String... names) {
        return field(obj, names);
    }

    // ---- ItemStack 标识 ----

    static String stackKey(ItemStack stack) {
        if (stack == null) return "";
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
}