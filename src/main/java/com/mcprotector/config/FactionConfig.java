package com.mcprotector.config;

import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionProtectionTier;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FactionConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
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

    public static String getDefaultBannerColor() {
        return SERVER.defaultBannerColor.get();
    }

    public static FactionProtectionTier getDefaultProtectionTier() {
        String name = SERVER.defaultProtectionTier.get();
        try {
            return FactionProtectionTier.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return FactionProtectionTier.STANDARD;
        }
    }

    public static Map<String, String> getDefaultRoleDisplayNames() {
        String preset = SERVER.defaultRankPreset.get();
        Map<String, String> names = getPresetRoleDisplayNames(preset);
        Map<String, String> result = new LinkedHashMap<>();
        if (names.isEmpty()) {
            result.put(Faction.ROLE_OWNER, "Owner");
            result.put(Faction.ROLE_OFFICER, "Officer");
            result.put(Faction.ROLE_MEMBER, "Member");
            return result;
        }
        result.putAll(names);
        return result;
    }

    public static Map<String, String> getPresetRoleDisplayNames(String presetName) {
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
            Map<String, String> map = new LinkedHashMap<>();
            map.put(Faction.ROLE_OWNER, names[0].trim());
            map.put(Faction.ROLE_OFFICER, names[1].trim());
            map.put(Faction.ROLE_MEMBER, names[2].trim());
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
        String normalized = normalizeColorInput(colorName);
        if (normalized.isEmpty()) {
            return ChatFormatting.WHITE;
        }
        if (isHexColor(normalized)) {
            return closestVanillaColor(parseHexColor(normalized));
        }
        ChatFormatting formatting = ChatFormatting.getByName(normalized);
        if (formatting == null || !formatting.isColor()) {
            return ChatFormatting.WHITE;
        }
        return formatting;
    }

    public static String normalizeColorInput(String colorName) {
        if (colorName == null) {
            return "";
        }
        return colorName.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isHexColor(String colorName) {
        String normalized = normalizeColorInput(colorName);
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        return normalized.matches("[0-9a-f]{6}");
    }

    public static int parseHexColor(String colorName) {
        String normalized = normalizeColorInput(colorName);
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        return HexFormat.fromHexDigits(normalized);
    }

    public static int resolveRgbColor(String colorName) {
        String normalized = normalizeColorInput(colorName);
        if (isHexColor(normalized)) {
            return parseHexColor(normalized);
        }
        ChatFormatting formatting = parseColor(normalized);
        Integer rgb = formatting.getColor();
        return rgb == null ? 0xFFFFFF : rgb;
    }

    public static String toLegacyHexCode(String colorName) {
        int rgb = resolveRgbColor(colorName);
        String hex = String.format("%06X", rgb);
        StringBuilder builder = new StringBuilder("\u00A7x");
        for (char c : hex.toCharArray()) {
            builder.append("\u00A7").append(c);
        }
        return builder.toString();
    }

    private static ChatFormatting closestVanillaColor(int rgb) {
        ChatFormatting best = ChatFormatting.WHITE;
        int bestDistance = Integer.MAX_VALUE;
        for (ChatFormatting color : ChatFormatting.values()) {
            if (!color.isColor() || color.getColor() == null) {
                continue;
            }
            int candidate = color.getColor();
            int dr = ((candidate >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
            int dg = ((candidate >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
            int db = (candidate & 0xFF) - (rgb & 0xFF);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = color;
            }
        }
        return best;
    }

    public static final class Server {
        public final ModConfigSpec.ConfigValue<String> defaultFactionColor;
        public final ModConfigSpec.ConfigValue<String> defaultMotd;
        public final ModConfigSpec.ConfigValue<String> defaultDescription;
        public final ModConfigSpec.ConfigValue<String> defaultBannerColor;
        public final ModConfigSpec.ConfigValue<Integer> maxFactionNameLength;
        public final ModConfigSpec.ConfigValue<Boolean> protectOfflineFactions;
        public final ModConfigSpec.ConfigValue<Boolean> enableSieges;
        public final ModConfigSpec.ConfigValue<Boolean> enableVassals;
        public final ModConfigSpec.ConfigValue<Boolean> enableVassalBreakaways;
        public final ModConfigSpec.ConfigValue<Double> vassalBreakawayClaimPercent;
        public final ModConfigSpec.ConfigValue<String> defaultRankPreset;
        public final ModConfigSpec.ConfigValue<List<? extends String>> rankPresets;
        public final ModConfigSpec.ConfigValue<String> defaultProtectionTier;
        public final ModConfigSpec.ConfigValue<Integer> strictProtectionMinLevel;
        public final ModConfigSpec.ConfigValue<Boolean> enableFactionChat;
        public final ModConfigSpec.ConfigValue<Boolean> enableAllyChat;
        public final ModConfigSpec.ConfigValue<Boolean> usePublicChatFormat;
        public final ModConfigSpec.ConfigValue<String> factionChatFormat;
        public final ModConfigSpec.ConfigValue<String> allyChatFormat;
        public final ModConfigSpec.ConfigValue<String> publicChatFormat;
        public final ModConfigSpec.ConfigValue<String> tabListFormat;
        public final ModConfigSpec.ConfigValue<Integer> baseClaims;
        public final ModConfigSpec.ConfigValue<Integer> claimsPerMember;
        public final ModConfigSpec.ConfigValue<Integer> membersPerLevel;
        public final ModConfigSpec.ConfigValue<Integer> maxFactionLevel;
        public final ModConfigSpec.ConfigValue<Integer> bonusClaimsPerLevel;
        public final ModConfigSpec.ConfigValue<Integer> claimCooldownSeconds;
        public final ModConfigSpec.ConfigValue<Integer> claimCooldownReductionPerLevel;
        public final ModConfigSpec.ConfigValue<Double> claimCooldownOwnerMultiplier;
        public final ModConfigSpec.ConfigValue<Integer> unclaimCooldownSeconds;
        public final ModConfigSpec.ConfigValue<Integer> unclaimCooldownReductionPerLevel;
        public final ModConfigSpec.ConfigValue<Double> unclaimCooldownOwnerMultiplier;
        public final ModConfigSpec.ConfigValue<Integer> inviteExpirationMinutes;
        public final ModConfigSpec.ConfigValue<Integer> autoClaimCooldownSeconds;
        public final ModConfigSpec.ConfigValue<Boolean> allowPvpInClaims;
        public final ModConfigSpec.ConfigValue<Boolean> allowRedstoneInClaims;
        public final ModConfigSpec.ConfigValue<Boolean> allowDoorUseInClaims;
        public final ModConfigSpec.ConfigValue<Boolean> trustedAllowBuild;
        public final ModConfigSpec.ConfigValue<Boolean> allowFakePlayerActionsInClaims;
        public final ModConfigSpec.ConfigValue<Integer> adminBypassPermissionLevel;
        public final ModConfigSpec.ConfigValue<Integer> accessLogSize;
        public final ModConfigSpec.ConfigValue<Boolean> dynmapFullSyncOnStart;
        public final ModConfigSpec.ConfigValue<Integer> claimMapRadiusChunks;
        public final ModConfigSpec.ConfigValue<Boolean> claimMapFullSync;
        public final ModConfigSpec.ConfigValue<List<? extends String>> safeZoneDimensions;
        public final ModConfigSpec.ConfigValue<List<? extends String>> warZoneDimensions;

        private Server(ModConfigSpec.Builder builder) {
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
            defaultBannerColor = builder
                .comment("Default banner color name for new factions.")
                .define("defaultBannerColor", "white");
            maxFactionNameLength = builder
                .comment("Maximum length for faction names.")
                .defineInRange("maxFactionNameLength", 30, 3, 100);
            protectOfflineFactions = builder
                .comment("Prevent war actions against factions with no online members.")
                .define("protectOfflineFactions", true);
            enableSieges = builder
                .comment("Enable siege-based overtake gameplay.")
                .define("enableSieges", true);
            enableVassals = builder
                .comment("Enable vassal contract commands and relationships.")
                .define("enableVassals", true);
            enableVassalBreakaways = builder
                .comment("Allow vassals to declare breakaway wars against overlords.")
                .define("enableVassalBreakaways", true);
            vassalBreakawayClaimPercent = builder
                .comment("Percent of overlord claims a vassal must capture to break free.")
                .defineInRange("vassalBreakawayClaimPercent", 0.5, 0.1, 1.0);
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
            membersPerLevel = builder
                .comment("Members required per faction level increase.")
                .define("membersPerLevel", 3);
            maxFactionLevel = builder
                .comment("Maximum faction level.")
                .define("maxFactionLevel", 10);
            bonusClaimsPerLevel = builder
                .comment("Extra claims granted per faction level.")
                .define("bonusClaimsPerLevel", 5);
            claimCooldownSeconds = builder
                .comment("Cooldown in seconds between claim actions.")
                .define("claimCooldownSeconds", 10);
            claimCooldownReductionPerLevel = builder
                .comment("Cooldown reduction per faction level for claims.")
                .define("claimCooldownReductionPerLevel", 1);
            claimCooldownOwnerMultiplier = builder
                .comment("Cooldown multiplier for faction owners when claiming.")
                .defineInRange("claimCooldownOwnerMultiplier", 0.25, 0.0, 1.0);
            unclaimCooldownSeconds = builder
                .comment("Cooldown in seconds between unclaim actions.")
                .define("unclaimCooldownSeconds", 5);
            unclaimCooldownReductionPerLevel = builder
                .comment("Cooldown reduction per faction level for unclaims.")
                .define("unclaimCooldownReductionPerLevel", 1);
            unclaimCooldownOwnerMultiplier = builder
                .comment("Cooldown multiplier for faction owners when unclaiming.")
                .defineInRange("unclaimCooldownOwnerMultiplier", 0.25, 0.0, 1.0);
            defaultProtectionTier = builder
                .comment("Default protection tier (relaxed, standard, strict).")
                .define("defaultProtectionTier", "standard");
            strictProtectionMinLevel = builder
                .comment("Minimum faction level required to use strict protection.")
                .define("strictProtectionMinLevel", 3);
            inviteExpirationMinutes = builder
                .comment("Minutes before faction invites expire.")
                .define("inviteExpirationMinutes", 10);
            autoClaimCooldownSeconds = builder
                .comment("Cooldown in seconds between auto-claim attempts.")
                .define("autoClaimCooldownSeconds", 5);
            allowPvpInClaims = builder
                .comment("Allow player-versus-player combat inside claimed chunks.")
                .define("allowPvpInClaims", false);
            allowRedstoneInClaims = builder
                .comment("Allow redstone toggles by non-members when otherwise permitted.")
                .define("allowRedstoneInClaims", true);
            allowDoorUseInClaims = builder
                .comment("Allow door/trapdoor/fence gate use by non-members when otherwise permitted.")
                .define("allowDoorUseInClaims", true);
            trustedAllowBuild = builder
                .comment("Allow trusted outsiders to place and break blocks.")
                .define("trustedAllowBuild", false);
            allowFakePlayerActionsInClaims = builder
                .comment("Allow fake players (automation) to interact inside claimed chunks.")
                .define("allowFakePlayerActionsInClaims", false);
            adminBypassPermissionLevel = builder
                .comment("Permission level required to bypass claim protections (default 2).")
                .define("adminBypassPermissionLevel", 2);
            accessLogSize = builder
                .comment("Number of access log entries to keep per claim.")
                .define("accessLogSize", 20);
            dynmapFullSyncOnStart = builder
                .comment("Run a full Dynmap claim sync on server start.")
                .define("dynmapFullSyncOnStart", true);
            claimMapRadiusChunks = builder
                .comment("Radius in chunks for the client claim map sync.")
                .define("claimMapRadiusChunks", 8);
            claimMapFullSync = builder
                .comment("Send all claims to clients instead of only the radius (for larger map views).")
                .define("claimMapFullSync", false);
            safeZoneDimensions = builder
                .comment("Dimensions treated as safe zones (no PvP, no claim interactions).")
                .defineListAllowEmpty("safeZoneDimensions", List.of(), value -> value instanceof String);
            warZoneDimensions = builder
                .comment("Dimensions treated as war zones (claims ignored, PvP allowed).")
                .defineListAllowEmpty("warZoneDimensions", List.of(), value -> value instanceof String);
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
                .define("usePublicChatFormat", true);
            factionChatFormat = builder
                .comment("Format for faction chat messages.")
                .define("factionChatFormat", "{faction_color}[{faction}]{reset} {role} {player}: {message}");
            allyChatFormat = builder
                .comment("Format for ally chat messages.")
                .define("allyChatFormat", "{faction_color}[Ally:{faction}]{reset} {player}: {message}");
            publicChatFormat = builder
                .comment("Format for public chat messages.")
                .define("publicChatFormat", "{faction_color}[{faction}]{reset} {role} {player}: {faction_color}{message}{reset}");
            builder.pop();

            builder.push("tab_list");
            tabListFormat = builder
                .comment("Format for player list (tab) names.")
                .define("tabListFormat", "{faction_color}[{faction}]{reset} {player}");
            builder.pop();
        }
    }
}
