package com.songoda.core.nbtinjector;

import com.songoda.core.compatibility.ServerVersion;
import com.songoda.core.nbtinjector.NBTInjector.Entity;
import com.songoda.core.nms.nbt.NBTWrapper;
import com.songoda.core.utils.NMSUtils;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public class InternalInjectors {

    private static final List<String> skippedEntities = Arrays.asList("player", "fishing_bobber",
            "lightning_bolt", "trident", "end_crystal", "experience_orb", "arrow", "hopper_minecart",
            "minecart", "chest_minecart", "command_block_minecart", "small_fireball", "potion",
            "furnace_minecart", "boat", "experience_bottle", "painting", "shulker_bullet",
            "llama_spit", "tnt", "snowball", "item", "firework_rocket", "dragon_fireball",
            "egg", "ender_pearl", "eye_of_ender", "falling_block", "wither_skull", "spectral_arrow",
            "tnt_minecart", "evoker_fangs", "spawner_minecart"); // These are unnecessary, used by 1.14+
    private static final Map<String, String> classMappings = new HashMap<>();
    protected static final Map<Class<?>, Object> classToMCKey = new HashMap<>();


    private static Field field_modifiers;

    static {
        try {
            field_modifiers = Field.class.getDeclaredField("modifiers");
            field_modifiers.setAccessible(true);
        } catch (NoSuchFieldException ex) {
            try {
                // This hacky workaround is for newer jdk versions 11+?
                Method fieldGetter = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
                fieldGetter.setAccessible(true);
                Field[] fields = (Field[]) fieldGetter.invoke(Field.class, false);
                for (Field f : fields)
                    if (f.getName().equals("modifiers")) {
                        field_modifiers = f;
                        field_modifiers.setAccessible(true);
                        break;
                    }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        classMappings.put("wandering_trader", "VillagerTrader");
        classMappings.put("trader_llama", "LlamaTrader");
        classMappings.put("area_effect_cloud", "AreaEffectCloud");
        classMappings.put("donkey", "HorseDonkey");
        classMappings.put("ender_dragon", "EnderDragon");
        classMappings.put("skeleton_horse", "HorseSkeleton");
        classMappings.put("fireball", "LargeFireball");
        classMappings.put("mule", "HorseMule");
        classMappings.put("zombie_horse", "HorseZombie");
        classMappings.put("mooshroom", "MushroomCow");
        classMappings.put("wither_skeleton", "SkeletonWither");
        classMappings.put("snow_golem", "Snowman");
        classMappings.put("polar_bear", "PolarBear");
        classMappings.put("magma_cube", "MagmaCube");
        classMappings.put("armor_stand", "ArmorStand");
        classMappings.put("elder_guardian", "GuardianElder");
        classMappings.put("zombie_pigman", "PigZombie");
        classMappings.put("giant", "GiantZombie");
        classMappings.put("zombie_villager", "ZombieVillager");
        classMappings.put("husk", "ZombieHusk");
        classMappings.put("iron_golem", "IronGolem");
        classMappings.put("tropical_fish", "TropicalFish");
        classMappings.put("stray", "SkeletonStray");
        classMappings.put("illusioner", "IllagerIllusioner");
        classMappings.put("pufferfish", "PufferFish");
        classMappings.put("cave_spider", "CaveSpider");
        classMappings.put("item_frame", "ItemFrame");
        classMappings.put("leash_knot", "Leash");
        classMappings.put("zombified_piglin", "PigZombie");
        classMappings.put("piglin_brute", "PiglinBrute");
    }

    /**
     * Hidden constructor
     */
    private InternalInjectors() {

    }

    protected static void entity1v10Below(ClassPool classPool) throws ReflectiveOperationException {
        for (Map.Entry<String, Class<?>> entry : new HashSet<>(Entity.getCMap().entrySet())) {
            try {
                if (NBTWrapper.class.isAssignableFrom(entry.getValue())) {
                    continue;
                } // Already injected
                int entityId = Entity.getFMap().get(entry.getValue());

                Class<?> wrapped = ClassGenerator.wrapEntity(classPool, entry.getValue());
                Entity.getCMap().put(entry.getKey(), wrapped);
                Entity.getDMap().put(wrapped, entry.getKey());

                Entity.getEMap().put(entityId, wrapped);
                Entity.getFMap().put(wrapped, entityId);
            } catch (IOException | CannotCompileException | NotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    protected static void entity1v12Below(ClassPool classPool) throws ReflectiveOperationException {
        Object registry = Entity.getRegistry();
        Map<Object, Object> inverse = new HashMap<>();


        Class<?> registrySimpleClazz = NMSUtils.getNMSClass("RegistrySimple");
        Object entityRegistry = NMSUtils.getNMSClass("IRegistry").getField("ENTITY_TYPE").get(null);

        Method keySet = registrySimpleClazz.getMethod("keySet");
        Set<?> it = new HashSet<>((Set<?>) keySet.invoke(entityRegistry));
        Object registryId = Entity.getRegistryId(registry);
        for (Object mckey : it) {

            Class<?> entclass = (Class<?>) registrySimpleClazz.getMethod("get", Object.class).invoke(registry, mckey);

            inverse.put(entclass, mckey);
            try {
                if (NBTWrapper.class.isAssignableFrom(entclass)) {
                    continue;
                } // Already injected
                Class<?> wrapped = ClassGenerator.wrapEntity(classPool, entclass);
                registrySimpleClazz.getMethod("a", Object.class, Object.class).invoke(registry, mckey, wrapped);
                inverse.put(wrapped, mckey);
                int id = (int) registryId.getClass().getMethod("getId", new Class[]{Object.class}).invoke(registryId, entclass);
                registryId.getClass().getMethod("a", new Class[]{Object.class, int.class}).invoke(registryId, wrapped, id);
                classToMCKey.put(entclass, mckey);
            } catch (IOException | CannotCompileException | NotFoundException e) {
                e.printStackTrace();
            }
        }
        Field inverseField = registry.getClass().getDeclaredField("b");
        setFinal(registry, inverseField, inverse);
    }

    protected static void entity1v13Below(ClassPool classPool) throws ReflectiveOperationException {
        Class<?> registryMaterialsClazz = NMSUtils.getNMSClass("RegistryMaterials");
        Object entityRegistry = NMSUtils.getNMSClass("IRegistry").getField("ENTITY_TYPE").get(null);

        Method keySet = registryMaterialsClazz.getMethod("keySet");
        Set<?> registryentries = new HashSet<>((Set<?>) keySet.invoke(entityRegistry));
        for (Object mckey : registryentries) {
            Class<?> minecraftKeyClazz = NMSUtils.getNMSClass("MinecraftKey");
            Object entityTypesObj = registryMaterialsClazz.getMethod("get", minecraftKeyClazz).invoke(entityRegistry, mckey);

            Field supplierField = entityTypesObj.getClass().getDeclaredField("aT");
            Field classField = entityTypesObj.getClass().getDeclaredField("aS");
            classField.setAccessible(true);
            supplierField.setAccessible(true);
            Function<Object, Object> function = (Function<Object, Object>) supplierField.get(entityTypesObj);
            Class<?> nmsclass = (Class<?>) classField.get(entityTypesObj);
            try {
                if (NBTWrapper.class.isAssignableFrom(nmsclass)) {
                    continue;
                } // Already injected
                Class<?> wrapped = ClassGenerator.wrapEntity(classPool, nmsclass);
                setFinal(entityTypesObj, classField, wrapped);
                setFinal(entityTypesObj, supplierField, (Function<Object, Object>) t -> {
                    try {
                        return wrapped.getConstructor(NMSUtils.getNMSClass("World")).newInstance(t);
                    } catch (Exception ex) {
                        System.out.println("Error while creating custom entity instance! ");
                        return function.apply(t);// Fallback to the original one
                    }
                });
                classToMCKey.put(nmsclass, mckey);
            } catch (CannotCompileException | NotFoundException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected static void entity1v14(ClassPool classPool) throws ReflectiveOperationException {
        Class<?> registryMaterialsClazz = NMSUtils.getNMSClass("RegistryMaterials");
        Object entityRegistry = NMSUtils.getNMSClass("IRegistry").getField("ENTITY_TYPE").get(null);

        Method keySet = registryMaterialsClazz.getMethod("keySet");
        Set<?> registryentries = new HashSet<>((Set<?>) keySet.invoke(entityRegistry));
        for (Object mckey : registryentries) {
            String plain = mckey.toString().substring(10);
            System.out.println(plain);
            if (skippedEntities.contains(plain))
                continue;
            System.out.println("Not skipping");

            Class<?> minecraftKeyClazz = NMSUtils.getNMSClass("MinecraftKey");
            Object entityTypesObj = registryMaterialsClazz.getMethod("get", minecraftKeyClazz).invoke(entityRegistry, mckey);

            String creatorFieldName = ServerVersion.isServerVersionAtLeast(ServerVersion.V1_15) ? "ba" : "aZ";
            if (ServerVersion.isServerVersionAtLeast(ServerVersion.V1_16))
                creatorFieldName = "bf";
            Field creatorField = entityTypesObj.getClass().getDeclaredField(creatorFieldName);
            creatorField.setAccessible(true);
            Object creator = creatorField.get(entityTypesObj);
            Method createEntityMethod = creator.getClass().getMethod("create",
                    NMSUtils.getNMSClass("EntityTypes"),
                    NMSUtils.getNMSClass("World"));
            createEntityMethod.setAccessible(true);
            Class<?> nmsclass = null;
            try {
                nmsclass = createEntityMethod.invoke(creator, entityTypesObj, null).getClass();
            } catch (Exception ignore) {
                // ignore
            }
            if (nmsclass == null) {
                String version = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
                String name = plain.substring(0, 1).toUpperCase() + plain.substring(1);
                name = "Entity" + classMappings.getOrDefault(plain, name);
                try {
                    nmsclass = NMSUtils.getNMSClass(name);
                } catch (Exception ignored) {
                    System.out.println("Not found: " + "net.minecraft.server." + version + "." + name);
                    // ignored
                }
            }
            if (nmsclass == null) {
                System.out.println(
                        "Wasn't able to create an Entity instance, won't be able add NBT to '" + mckey + "' entities!");
                continue;
            }
            classToMCKey.put(nmsclass, mckey);
            try {
                if (NBTWrapper.class.isAssignableFrom(nmsclass)) {
                    continue;
                } // Already injected
                Class<?> wrapped = ClassGenerator.wrapEntity(classPool, nmsclass);
                setFinal(entityTypesObj, creatorField,
                        ClassGenerator.createEntityTypeWrapper(classPool, wrapped).newInstance());
            } catch (IOException | CannotCompileException | NotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public static void setFinal(Object obj, Field field, Object newValue)
            throws IllegalArgumentException, IllegalAccessException {
        field.setAccessible(true);
        makeNonFinal(field);
        field.set(obj, newValue);
    }

    public static Field makeNonFinal(Field field) throws IllegalArgumentException, IllegalAccessException {
        int mods = field.getModifiers();
        if (Modifier.isFinal(mods)) {
            field_modifiers.set(field, mods & ~Modifier.FINAL);
        }
        return field;
    }
}
