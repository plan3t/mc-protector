package com.mcprotector.config;

import com.mcprotector.data.FactionRole;
import net.minecraft.ChatFormatting;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FactionConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SERVER = new Server(builder);
        SERVER_SPEC = builder.build();
    }

    private FactionConfig() {
    }

    public static String getDefaultColorName() {
        return SERVER.defaultFactionColor.get();
    }

    public static String getDefaultMotd() {
        return SERVER.defaultMotd.get();
    }

    public static String getDefaultDescription() {
        return SERVER.defaultDescription.get();
    }

    public static EnumMap<FactionRole, String> getDefaultRankNames() {
        String preset = SERVER.defaultRankPreset.get();
        Map<FactionRole, String> names = getPresetNames(preset);
        EnumMap<FactionRole, String> result = new EnumMap<>(FactionRole.class);
        if (names.isEmpty()) {
            result.put(FactionRole.OWNER, "Owner");
            result.put(FactionRole.OFFICER, "Officer");
            result.put(FactionRole.MEMBER, "Member");
            return result;
        }
        result.putAll(names);
        return result;
    }

    public static Map<FactionRole, String> getPresetNames(String presetName) {
        if (presetName == null) {
            return Map.of();
        }
        String target = presetName.trim().toLowerCase(Locale.ROOT);
        for (String entry : SERVER.rankPresets.get()) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            if (!parts[0].trim().equalsIgnoreCase(target)) {
                continue;
            }
            String[] names = parts[1].split(",", -1);
            if (names.length < 3) {
                continue;
            }
            EnumMap<FactionRole, String> map = new EnumMap<>(FactionRole.class);
            map.put(FactionRole.OWNER, names[0].trim());
            map.put(FactionRole.OFFICER, names[1].trim());
            map.put(FactionRole.MEMBER, names[2].trim());
            return map;
        }
        return Map.of();
    }

    public static Map<String, String> getPresetDisplayMap() {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (String entry : SERVER.rankPresets.get()) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            result.put(parts[0].trim(), parts[1].trim());
        }
        return result;
    }

    public static ChatFormatting parseColor(String colorName) {
        if (colorName == null || colorName.isBlank()) {
            return ChatFormatting.WHITE;
        }
        ChatFormatting formatting = ChatFormatting.getByName(colorName.toLowerCase(Locale.ROOT));
        if (formatting == null || !formatting.isColor()) {
            return ChatFormatting.WHITE;
        }
        return formatting;
    }

    public static final class Server {
        public final ForgeConfigSpec.ConfigValue<String> defaultFactionColor;
        public final ForgeConfigSpec.ConfigValue<String> defaultMotd;
        public final ForgeConfigSpec.ConfigValue<String> defaultDescription;
        public final ForgeConfigSpec.ConfigValue<String> defaultRankPreset;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> rankPresets;
        public final ForgeConfigSpec.ConfigValue<Boolean> enableFactionChat;
        public final ForgeConfigSpec.ConfigValue<Boolean> enableAllyChat;
        public final ForgeConfigSpec.ConfigValue<Boolean> usePublicChatFormat;
        public final ForgeConfigSpec.ConfigValue<String> factionChatFormat;
        public final ForgeConfigSpec.ConfigValue<String> allyChatFormat;
        public final ForgeConfigSpec.ConfigValue<String> publicChatFormat;
        public final ForgeConfigSpec.ConfigValue<String> tabListFormat;
        public final ForgeConfigSpec.ConfigValue<Integer> baseClaims;
        public final ForgeConfigSpec.ConfigValue<Integer> claimsPerMember;

        private Server(ForgeConfigSpec.Builder builder) {
            builder.push("factions");
            defaultFactionColor = builder
                .comment("Default faction chat color name (e.g. red, gold, blue).")
                .define("defaultFactionColor", "gold");
            defaultMotd = builder
                .comment("Default message of the day for new factions.")
                .define("defaultMotd", "Welcome to the faction!");
            defaultDescription = builder
                .comment("Default description for new factions.")
                .define("defaultDescription", "A brave new faction.");
            defaultRankPreset = builder
                .comment("Preset name to apply for new factions.")
                .define("defaultRankPreset", "default");
            rankPresets = builder
                .comment("Rank presets in the format name=Owner,Officer,Member.")
                .defineListAllowEmpty("rankPresets", List.of(
                    "default=Owner,Officer,Member",
                    "military=Commander,Captain,Recruit",
                    "guild=Guildmaster,Officer,Member"
                ), value -> value instanceof String);
            baseClaims = builder
                .comment("Base number of claims a faction can have.")
                .define("baseClaims", 10);
            claimsPerMember = builder
                .comment("Additional claims per faction member.")
                .define("claimsPerMember", 10);
            builder.pop();

            builder.push("chat");
            enableFactionChat = builder
                .comment("Enable faction-only chat channel.")
                .define("enableFactionChat", true);
            enableAllyChat = builder
                .comment("Enable ally chat channel.")
                .define("enableAllyChat", true);
            usePublicChatFormat = builder
                .comment("Apply the public chat format to normal chat.")
                .define("usePublicChatFormat", false);
            factionChatFormat = builder
                .comment("Format for faction chat messages.")
                .define("factionChatFormat", "{faction_color}[{faction}]{reset} {role} {player}: {message}");
            allyChatFormat = builder
                .comment("Format for ally chat messages.")
                .define("allyChatFormat", "{faction_color}[Ally:{faction}]{reset} {player}: {message}");
            publicChatFormat = builder
                .comment("Format for public chat messages.")
                .define("publicChatFormat", "{player}: {message}");
            builder.pop();

            builder.push("tab_list");
            tabListFormat = builder
                .comment("Format for player list (tab) names.")
                .define("tabListFormat", "{faction_color}[{faction}]{reset} {player}");
            builder.pop();
        }
    }
}
