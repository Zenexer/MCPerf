package com.earth2me.mcperf.managers.creative.validity;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

public class GenericMetaValidator_1_8 extends GenericMetaValidator {
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private static String versionKey;                      // Example: ".v1_8_R3"  Note the dot prefix.
    private static Class<?> craftMetaItemClass;            // org.bukkit.craftbukkit{versionKey}.inventory.CraftMetaItem
    private static Field unhandledTagsField;               // CraftMetaItem#unhandledTags : Map<String, NBTBase>
    private static Class<?> nbtTagCompoundClass;           // net.minecraft.server{versionKey}.NBTTagCompound
    private static Class<?> nbtTagListClass;               // net.minecraft.server{versionKey}.NBTTagList
    private static Method nbtTagListGetMethod;             // net.minecraft.server{versionKey}.NBTTagList#get(int) : NBTTagCompound
    private static Method nbtTagListSizeMethod;            // net.minecraft.server{versionKey}.NBTTagList#size : int
    private static Method nbtTagCompoundGetStringMethod;   // net.minecraft.server{versionKey}.NBTTagCompound#getString(String) : String
    private static Method nbtTagCompoundGetDoubleMethod;   // net.minecraft.server{versionKey}.NBTTagCompound#getDouble(String) : double
    private static Method nbtTagCompoundGetIntMethod;      // net.minecraft.server{versionKey}.NBTTagCompound#getInt(String) : int
    private static Method nbtTagCompoundGetLongMethod;     // net.minecraft.server{versionKey}.NBTTagCompound#getLong(String) : long


    @Override
    protected boolean isValidNBT(ItemStack stack, ItemMeta meta) {
        if (!prepareReflection(meta)) {
            return true;  // Indeterminate
        }

        Map<String, Object> unhandledTags;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> x = (Map<String, Object>) unhandledTagsField.get(meta);
            unhandledTags = x;
        } catch (IllegalAccessException e) {
            Bukkit.getLogger().severe("[MCPerf] Error reading unhandledTags field: " + e.getMessage());
            return true;  // Indeterminate
        }

        if (unhandledTags != null && !unhandledTags.isEmpty()) {
            Set<String> suspiciousTags = new HashSet<>();
            Set<String> bannedAttributeModifiers = getConfig().getBannedAttributeModifiers();
            Set<String> bannedTags = getConfig().getBannedTags();

            for (Map.Entry<String, Object> entry : unhandledTags.entrySet()) {
                String key = entry.getKey();

                if (bannedTags.contains(key)) {
                    onInvalid("banned tag: %s", key);
                    return false;
                }

                switch (key) {
                    case "id":
                    case "Count":
                    case "Damage":
                    case "Unbreakable":
                    case "CanDestroy":
                    case "CanPlaceOn":
                    case "BlockEntityTag":
                    case "ench":
                    case "StoredEnchantments":
                    case "RepairCost":
                    case "display":
                    case "HideFlags":
                    case "resolved":
                    case "generation":
                    case "author":
                    case "title":
                    case "pages":
                    case "SkullOwner":
                    case "ExtraType":  // pre-1.8
                    case "Owner":
                    case "Explosion":
                    case "Fireworks":
                    case "EntityTag":
                    case "map_is_scaling":
                    case "Decorations":
                        continue;

                    case "AttributeModifiers":
                        try {
                            Map<String, AttributeModifier> modifiers = getAttributeModifiers(entry.getValue());
                            if (modifiers == null || modifiers.isEmpty()) {
                                break;
                            }

                            for (AttributeModifier modifier : modifiers.values()) {
                                if (bannedAttributeModifiers.contains(modifier.getAttributeName())) {
                                    String text = String.join(", ", modifiers.values().stream().map(m -> (CharSequence) m.toString()).toArray(String[]::new));
                                    onInvalid("attribute modifiers: %s", text);
                                    return false;
                                }

                                switch (modifier.getAttributeName()) {
                                    case "zombie.spawnReinforcements":
                                    case "generic.followRange":
                                    case "generic.knockbackResistance":
                                    case "generic.maxHealth":
                                    case "generic.movementSpeed":
                                    case "generic.attackDamage":
                                    case "horse.jumpStrength":
                                        String text = String.join(", ", modifiers.values().stream().map(m -> (CharSequence) m.toString()).toArray(String[]::new));
                                        log(Level.INFO, String.format("[MCPerf] Detected %s with modifier(s): %s", stack.getType().name(), text));
                                        break;
                                }
                            }
                        } catch (Exception e) {
                            log(Level.WARNING, e);
                        }
                        continue;

                    default:
                        suspiciousTags.add(key);
                        break;
                }
            }

            if (!suspiciousTags.isEmpty()) {
                String tagText = String.join(", ", suspiciousTags);
                log(Level.WARNING, String.format("[MCPerf] Detected %s with suspicious tag(s): %s", stack.getType().name(), tagText));
            }
        }

        return true;
    }


    private Map<String, AttributeModifier> getAttributeModifiers(Object attributeModifiers) {
        if (attributeModifiers == null) {
            return null;
        }

        if (nbtTagListClass == null) {
            Class<?> type = attributeModifiers.getClass();
            if (!"NBTTagList".equals(type.getSimpleName())) {
                log(Level.WARNING, String.format("Expected %s; got: %s", "*.NBTTagList", type.getCanonicalName()));
                return null;
            }
            nbtTagListClass = type;
        } else if (nbtTagListClass != attributeModifiers.getClass()) {
            log(Level.WARNING, String.format("Expected %s; got: %s", nbtTagListClass.getCanonicalName(), attributeModifiers.getClass().getCanonicalName()));
            return null;
        }

        if (nbtTagListSizeMethod == null) {
            try {
                nbtTagListSizeMethod = nbtTagListClass.getDeclaredMethod("size");
            } catch (NoSuchMethodException e) {
                log(Level.WARNING, "Couldn't find method: NBTTagList#size()", e);
                return null;
            }
        }

        int size;
        try {
            size = (int) nbtTagListSizeMethod.invoke(attributeModifiers);
        } catch (IllegalAccessException | InvocationTargetException e) {
            log(Level.WARNING, e);
            return null;
        }

        if (nbtTagListGetMethod == null) {
            try {
                nbtTagListGetMethod = nbtTagListClass.getDeclaredMethod("get", Integer.TYPE);
            } catch (NoSuchMethodException e) {
                log(Level.WARNING, "Couldn't find method: NBTTagList#get(int)", e);
                return null;
            }
        }

        try {
            Map<String, AttributeModifier> result = new HashMap<>();

            for (int i = 0; i < size; i++) {
                Object attributeModifier = nbtTagListGetMethod.invoke(attributeModifiers, i);

                if (i == 0) {
                    if (nbtTagCompoundClass == null) {
                        Class<?> type = attributeModifier.getClass();
                        if (!"NBTTagCompound".equals(type.getSimpleName())) {
                            log(Level.WARNING, String.format("Expected %s; got: %s", "*.NBTTagCompound", type.getCanonicalName()));
                            return null;
                        }
                        nbtTagCompoundClass = type;
                    }

                    if (nbtTagCompoundGetIntMethod == null) {
                        nbtTagCompoundGetIntMethod = nbtTagCompoundClass.getDeclaredMethod("getInt", String.class);
                        nbtTagCompoundGetIntMethod.setAccessible(true);
                    }
                    if (nbtTagCompoundGetStringMethod == null) {
                        nbtTagCompoundGetStringMethod = nbtTagCompoundClass.getDeclaredMethod("getString", String.class);
                        nbtTagCompoundGetStringMethod.setAccessible(true);
                    }
                    if (nbtTagCompoundGetDoubleMethod == null) {
                        nbtTagCompoundGetDoubleMethod = nbtTagCompoundClass.getDeclaredMethod("getDouble", String.class);
                        nbtTagCompoundGetDoubleMethod.setAccessible(true);
                    }
                    if (nbtTagCompoundGetLongMethod == null) {
                        nbtTagCompoundGetLongMethod = nbtTagCompoundClass.getDeclaredMethod("getLong", String.class);
                        nbtTagCompoundGetLongMethod.setAccessible(true);
                    }
                }

                long uuidHigh = (long) nbtTagCompoundGetLongMethod.invoke(attributeModifier, "UUIDMost");
                long uuidLow = (long) nbtTagCompoundGetLongMethod.invoke(attributeModifier, "UUIDLeast");
                UUID uuid = new UUID(uuidHigh, uuidLow);
                String attributeName = (String) nbtTagCompoundGetStringMethod.invoke(attributeModifier, "AttributeName");
                String modifierName = (String) nbtTagCompoundGetStringMethod.invoke(attributeModifier, "Name");
                double value = (double) nbtTagCompoundGetDoubleMethod.invoke(attributeModifier, "Amount");
                int type = (int) nbtTagCompoundGetIntMethod.invoke(attributeModifier, "Operation");
                AttributeModifier.Operation operation = AttributeModifier.Operation.fromId(type);

                AttributeModifier modifier = new AttributeModifier(uuid, attributeName, modifierName, value, operation);
                result.put(modifier.getAttributeName(), modifier);
            }

            return result;
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            log(Level.WARNING, e);
            return null;
        }
    }

    private static <T extends ItemMeta> boolean prepareReflection(T meta) {
        if (craftMetaItemClass != null && unhandledTagsField != null) {
            // Sanity check
            if (!craftMetaItemClass.isInstance(meta)) {
                Bukkit.getLogger().severe("[MCPerf] Expected subclass of " + craftMetaItemClass.getCanonicalName() + ", but got: " + meta.getClass().getCanonicalName());
                return false;
            }

            return true;
        }

        craftMetaItemClass = null;
        unhandledTagsField = null;
        versionKey = null;

        for (Class<?> metaType = meta.getClass(); craftMetaItemClass == null; metaType = metaType.getSuperclass()) {
            if (metaType == null) {
                Bukkit.getLogger().severe("[MCPerf] Unable to retrieve CraftMetaItem type from " + meta.getClass().getCanonicalName());
                return false;
            }

            Package pkg = metaType.getPackage();
            String name = metaType.getSimpleName();
            final String prefix = "org.bukkit.craftbukkit.";
            if (pkg != null && "CraftMetaItem".equals(name) && pkg.getName().startsWith(prefix)) {
                craftMetaItemClass = metaType;

                String tmp = pkg.getName().substring(prefix.length());
                if (tmp.equals("inventory")) {
                    versionKey = "";
                    break;
                }

                int dot = tmp.indexOf('.');
                if (dot <= 0) {  // <= because we want to disallow "..", although that should be impossible
                    Bukkit.getLogger().severe("[MCPerf] Unable to retrieve CraftBukkit version key from package: " + pkg.getName());
                    return false;
                }

                versionKey = "." + tmp.substring(0, dot);
            }
        }

        try {
            unhandledTagsField = craftMetaItemClass.getDeclaredField("unhandledTags");
            for (Field i : new Field[]{
                    unhandledTagsField,
            }) {
                i.setAccessible(true);
            }
        } catch (NoSuchFieldException ex) {
            Bukkit.getLogger().severe("[MCPerf] Couldn't find a required field: " + ex.getMessage());
            return false;
        }

        return true;
    }
}
