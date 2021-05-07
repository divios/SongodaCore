package com.songoda.core.nbtinjector;

import com.songoda.core.compatibility.ServerVersion;
import com.songoda.core.utils.NMSUtils;
import javassist.ClassPool;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class NBTInjector {

    private static boolean isInjected = false;

    /**
     * Hidden Constructor
     */
    private NBTInjector() {

    }


    /**
     * Replaces the vanilla classes with Wrapped classes that support custom NBT.
     * This method needs to be called during onLoad so classes are replaced before
     * worlds load. If your plugin adds a new Entity(probably during onLoad) recall
     * this method so it's class gets Wrapped.
     */
    public static void inject() {
        if (isInjected) return;
        isInjected = true;
        try {
            ClassPool classPool = ClassPool.getDefault();
            if (ServerVersion.isServerVersionAtOrBelow(ServerVersion.V1_10)) {
                InternalInjectors.entity1v10Below(classPool);
            } else if (ServerVersion.isServerVersionAtOrBelow(ServerVersion.V1_12)) {
                InternalInjectors.entity1v12Below(classPool);
            } else if (ServerVersion.isServerVersionAtOrBelow(ServerVersion.V1_13)) {
                InternalInjectors.entity1v13Below(classPool);
            } else { // 1.14+
                InternalInjectors.entity1v14(classPool);
            }
        } catch (ReflectiveOperationException e) {
            System.out.println("Injection failed. Upgrade (or downgrade) your Songoda plugins before logging in or risk losing critical data.");
            e.printStackTrace();
        }
    }

    private static Field getAccessable(Field field) {
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("unchecked")
    static class Entity {

        private static final Class<?> clazzEntityTypes = NMSUtils.getNMSClass("EntityTypes");

        /**
         * Hidden Constructor
         */
        private Entity() {
        }

        private static final Map<Class<?>, String> backupMap = new HashMap<>();

        static {
            try {
                if (ServerVersion.isServerVersionAtOrBelow(ServerVersion.V1_10)) {
                    backupMap.putAll(getDMap());
                }
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }

        static Object getRegistry() throws ReflectiveOperationException {
            return getAccessable(clazzEntityTypes.getDeclaredField("b")).get(null);
        }

        static Object getRegistryId(Object reg) throws ReflectiveOperationException {
            return getAccessable(reg.getClass().getDeclaredField("a")).get(reg);
        }

        static Map<Class<?>, String> getBackupMap() throws ReflectiveOperationException {
            return backupMap;
        }

        static Map<String, Class<?>> getCMap() throws ReflectiveOperationException {
            return (Map<String, Class<?>>) getAccessable(clazzEntityTypes.getDeclaredField("c"))
                    .get(null);
        }

        static Map<Class<?>, String> getDMap() throws ReflectiveOperationException {
            return (Map<Class<?>, String>) getAccessable(clazzEntityTypes.getDeclaredField("d"))
                    .get(null);
        }

        static Map<Integer, Class<?>> getEMap() throws ReflectiveOperationException {
            return (Map<Integer, Class<?>>) getAccessable(clazzEntityTypes.getDeclaredField("e"))
                    .get(null);
        }

        static Map<Class<?>, Integer> getFMap() throws ReflectiveOperationException {
            return (Map<Class<?>, Integer>) getAccessable(clazzEntityTypes.getDeclaredField("f"))
                    .get(null);
        }
    }
}
