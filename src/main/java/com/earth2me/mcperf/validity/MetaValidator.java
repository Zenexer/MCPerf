package com.earth2me.mcperf.validity;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public abstract class MetaValidator<T extends ItemMeta> extends Validator {
    private static String versionKey;                      // Example: ".v1_8_R3"  Note the dot prefix.
    private static Class<?> craftMetaItemClass;            // org.bukkit.craftbukkit{versionKey}.inventory.CraftMetaItem
    private static Field unhandledTagsField;               // CraftMetaItem#unhandledTagsField
    private static Class<?> nbtTagCompoundClass;           // net.minecraft.server{versionKey}.NBTTagCompound
    private static Class<?> nbtTagListClass;               // net.minecraft.server{versionKey}.NBTTagList
    private static Class<?> nbtNumberClass;                // net.minecraft.server{versionKey}.NBTBase.NBTNumber
    private static Class<?> nbtTagStringClass;             // net.minecraft.server{versionKey}.NBTTagString
    private static Map<Class<?>, Method> getNumberMethods; // net.minecraft.server{versionKey}.NBTBase.NBTNumber#{obfuscated}

    static {
        getNumberMethods = new HashMap<>();
        getNumberMethods.put(Long.TYPE, null);
        getNumberMethods.put(Integer.TYPE, null);
        getNumberMethods.put(Short.TYPE, null);
        getNumberMethods.put(Byte.TYPE, null);
        getNumberMethods.put(Double.TYPE, null);
        getNumberMethods.put(Float.TYPE, null);
    }

    public abstract Class<T> getMetaType();

    @SuppressWarnings("RedundantIfStatement")
    protected boolean isValidMeta(ItemStack stack, T meta, boolean strict) {
        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName();

            if (name.length() > getConfig().getMaxNameLength() || name.isEmpty()) {
                onInvalid("display name length (%d)", name.length());
                return false;
            }

            if (!isValidText(name)) {
                onInvalid("display name text");
                return false;
            }
        }

        if (meta.hasLore()) {
            List<String> lore = meta.getLore();

            if (lore.size() > getConfig().getMaxLoreLines()) {
                onInvalid("lore line count (%d)", lore.size());
                return false;
            }

            int i = 1;
            for (String line : lore) {
                if (line.length() > getConfig().getMaxLoreLineLength()) {
                    onInvalid("lore line length (%d, line %d)", line.length(), i);
                    return false;
                }

                if (!isValidText(line)) {
                    onInvalid("lore text");
                    return false;
                }

                i++;
            }
        }

        try {
            if (!isValidNBT(stack, meta)) {
                return false;
            }
        } catch (Exception ex) {
            Bukkit.getLogger().severe("[MCPerf] isValidNBT threw an exception!\n" + ex.toString());
        }

        return true;
    }

    private boolean isValidNBT(ItemStack stack, T meta) {
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

            for (Map.Entry<String, Object> entry : unhandledTags.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                switch (key) {
                    case "id":
                    case "Count":
                    case "Damage":
                        continue;

                    case "www.wurst-client.tk":
                        onInvalid("hack client (Wurst)");
                        return false;

                    default:
                        suspiciousTags.add(key);
                        break;
                }
            }

            if (!suspiciousTags.isEmpty()) {
                String tagText = String.join(", ", suspiciousTags);
                Bukkit.getLogger().warning(String.format("[MCPerf] Detected suspicious tags: %s with tags %s", stack.getType().name(), tagText));
            }
        }

        return true;
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

                versionKey = pkg.getName().substring(0, dot);
            }
        }

        try {
            nbtTagCompoundClass = Class.forName("net.minecraft.server" + versionKey + ".NBTTagCompound");
            nbtTagListClass     = Class.forName("net.minecraft.server" + versionKey + ".NBTTagList");
            nbtNumberClass      = Class.forName("net.minecraft.server" + versionKey + ".NBTBase.NBTNumber");
            nbtTagStringClass   = Class.forName("net.minecraft.server" + versionKey + ".NBTTagString");

            unhandledTagsField = craftMetaItemClass.getDeclaredField("unhandledTags");
            for (Field i : new Field[] {
                    unhandledTagsField,
            }) {
                i.setAccessible(true);
            }

            getNumberMethods = new HashMap<>();
            for (Method method : nbtNumberClass.getDeclaredMethods()) {
                Class<?> type = method.getReturnType();
                if (method.getParameterCount() == 0 && getNumberMethods.containsKey(type) && getNumberMethods.get(type) == null) {
                    method.setAccessible(true);
                    getNumberMethods.put(type, method);
                }
            }
            for (Map.Entry<Class<?>, Method> entry : getNumberMethods.entrySet()) {
                if (entry.getValue() == null) {
                    throw new NoSuchMethodException("Missing method that returns type " + entry.getKey().getName() + " for NBTNumber");
                }
            }
        } catch (ClassNotFoundException ex) {
            Bukkit.getLogger().severe("[MCPerf] Couldn't find a required class: " + ex.getMessage());
            return false;
        } catch (NoSuchFieldException ex) {
            Bukkit.getLogger().severe("[MCPerf] Couldn't find a required field: " + ex.getMessage());
            return false;
        } catch (NoSuchMethodException ex) {
            Bukkit.getLogger().severe("[MCPerf] Couldn't find a required method: " + ex.getMessage());
            return false;
        }

        return true;
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean isValid(ItemStack stack, boolean strict) {
        if (!super.isValid(stack, strict)) {
            return false;
        }

        if (!isValidMeta(stack, getMetaType().cast(stack.getItemMeta()), strict)) {
            return false;
        }

        return true;
    }
}
