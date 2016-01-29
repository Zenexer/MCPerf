package com.earth2me.mcperf.validity;

import lombok.Value;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

@Value
public class ValidityConfiguration {
    boolean enabled;
    int maxLoreLines;
    int maxLoreLineLength;
    int maxNameLength;
    boolean basicUnicodeAllowed;
    boolean supplementaryUnicodeAllowed;
    boolean privateUnicodeAllowed;
    boolean linebreakUnicodeAllowed;
    boolean controlUnicodeAllowed;
    boolean unusualWhitespaceUnicodeAllowed;
    boolean nullCharAllowed;
    boolean escapeCharAllowed;
    boolean formattingAllowed;
    boolean crazyTextAllowed;
    boolean enchantmentCheckingEnabled;
    boolean potionCheckingEnabled;
    boolean splashPotionCheckingEnabled;
    boolean splashPotionsBanned;
    boolean zeroQuantityBanned;
    Set<String> bannedAttributeModifiers;
    Set<String> bannedTags;

    public ValidityConfiguration(FileConfiguration config) {
        enabled = config.getBoolean("validityManager.enabled", false);
        maxLoreLineLength = config.getInt("validityManager.maxLoreLineLength", 5);
        maxLoreLines = config.getInt("validityManager.maxLoreLines", 127);
        maxNameLength = config.getInt("validityManager.maxNameLength", 63);
        basicUnicodeAllowed = config.getBoolean("validityManager.basicUnicodeAllowed", true);
        supplementaryUnicodeAllowed = config.getBoolean("validityManager.supplementaryUnicodeAllowed", true);
        privateUnicodeAllowed = config.getBoolean("validityManager.privateUnicodeAllowed", true);
        linebreakUnicodeAllowed = config.getBoolean("validityManager.linebreakUnicodeAllowed", false);
        controlUnicodeAllowed = config.getBoolean("validityManager.controlUnicodeAllowed", false);
        unusualWhitespaceUnicodeAllowed = config.getBoolean("validityManager.unusualWhitespaceUnicodeAllowed", false);
        nullCharAllowed = config.getBoolean("validityManager.nullCharAllowed", false);
        escapeCharAllowed = config.getBoolean("validityManager.escapeCharAllowed", false);
        formattingAllowed = config.getBoolean("validityManager.formattingAllowed", true);
        crazyTextAllowed = config.getBoolean("validityManager.crazyTextAllowed", false);
        enchantmentCheckingEnabled = config.getBoolean("validityManager.enchantmentCheckingEnabled", false);
        potionCheckingEnabled = config.getBoolean("validityManager.potionCheckingEnabled", false);
        splashPotionCheckingEnabled = config.getBoolean("validityManager.splashPotionCheckingEnabled", false);
        splashPotionsBanned = config.getBoolean("validityManager.splashPotionsBanned", false);
        zeroQuantityBanned = config.getBoolean("validityManager.zeroQuantityBanned", false);

        if (config.contains("validityManager.bannedAttributeModifiers")) {
            List<String> list = config.getStringList("validityManager.bannedAttributeModifiers");
            if (list == null) {
                bannedAttributeModifiers = Collections.emptySet();
            } else {
                bannedAttributeModifiers = new HashSet<>(list);
            }
        } else {
            bannedAttributeModifiers = new HashSet<>(Arrays.asList(
                    "generic.maxHealth",
                    "generic.movementSpeed",
                    "generic.attackDamage",
                    "horse.jumpStrength"
            ));
        }

        if (config.contains("validityManager.bannedTags")) {
            List<String> list = config.getStringList("validityManager.bannedTags");
            if (list == null) {
                bannedTags = Collections.emptySet();
            } else {
                bannedTags = new HashSet<>(list);
            }
        } else {
            bannedTags = new HashSet<>(Collections.singletonList(
                    "www.wurst-client.tk"
            ));
        }
    }
}
