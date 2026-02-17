package com.mcprotector.command;

import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionProtectionTier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class FactionCommandSuggestions {
    private FactionCommandSuggestions() {
    }

    public static CompletableFuture<Suggestions> factionNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        FactionData data = FactionData.get(context.getSource().getLevel());
        List<String> names = data.getFactions().values().stream()
            .map(faction -> faction.getName().trim())
            .filter(name -> !name.isEmpty())
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        return SharedSuggestionProvider.suggest(names, builder);
    }

    public static CompletableFuture<Suggestions> onOff(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("on", "off"), builder);
    }

    public static CompletableFuture<Suggestions> yesNo(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("yes", "no"), builder);
    }

    public static CompletableFuture<Suggestions> chatModes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("public", "faction", "ally"), builder);
    }

    public static CompletableFuture<Suggestions> protectionTiers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> tiers = Arrays.stream(FactionProtectionTier.values())
            .map(tier -> tier.name().toLowerCase(Locale.ROOT))
            .toList();
        return SharedSuggestionProvider.suggest(tiers, builder);
    }

    public static CompletableFuture<Suggestions> permissions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> permissions = Arrays.stream(FactionPermission.values())
            .map(permission -> permission.name().toLowerCase(Locale.ROOT))
            .sorted(Comparator.naturalOrder())
            .toList();
        return SharedSuggestionProvider.suggest(permissions, builder);
    }
}
