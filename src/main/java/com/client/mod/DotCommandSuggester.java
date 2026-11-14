package com.client.mod;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Pure-Java autocomplete engine for Sestia Client {@code .c} chat commands.
 */
public final class DotCommandSuggester {

    public record Entry(String text, String subcommand, String description) {}

    public static final List<Entry> ROOT_COMMANDS = List.of(
            new Entry(".c", "", "Send message to Sestia global chat: .c <message>"),
            new Entry(".c list", "list", "Show online Sestia users"),
            new Entry(".c msg", "msg", "Send private message: .c msg <player> <message>"),
            new Entry(".c help", "help", "Show Sestia chat command help")
    );

    private DotCommandSuggester() {
    }

    public static boolean isDotCommandInput(String upToCursor) {
        return upToCursor != null && !upToCursor.isEmpty() && upToCursor.charAt(0) == '.';
    }

    public static Suggestions buildSuggestions(
            String rawInput,
            int cursor,
            Collection<String> onlinePlayers
    ) {
        if (rawInput == null || rawInput.isEmpty()) {
            return Suggestions.empty().join();
        }
        int clampedCursor = Math.max(0, Math.min(cursor, rawInput.length()));
        String upToCursor = rawInput.substring(0, clampedCursor);
        if (!isDotCommandInput(upToCursor)) {
            return Suggestions.empty().join();
        }

        int firstWs = findFirstWhitespace(upToCursor, 0);
        if (firstWs < 0) {
            return buildRootSuggestions(rawInput, upToCursor);
        }

        String head = upToCursor.substring(0, firstWs);
        if (!head.equalsIgnoreCase(".c")) {
            // Also support typing ". l" or ". m" right after "." as root completion
            if (head.equals(".")) {
                String afterDot = upToCursor.substring(firstWs).trim().toLowerCase(Locale.ROOT);
                StringRange range = StringRange.between(0, upToCursor.length());
                List<Suggestion> list = new ArrayList<>();
                for (Entry entry : ROOT_COMMANDS) {
                    if (!entry.subcommand().isEmpty() && entry.subcommand().startsWith(afterDot)) {
                        list.add(new Suggestion(range, entry.text(), Component.literal(entry.description())));
                    }
                }
                return list.isEmpty() ? Suggestions.empty().join() : new Suggestions(range, list);
            }
            return Suggestions.empty().join();
        }

        int subStart = skipWhitespace(upToCursor, firstWs);
        if (subStart >= upToCursor.length()) {
            return buildSubcommandSuggestions(upToCursor.length(), subStart, "");
        }

        int subEnd = findFirstWhitespace(upToCursor, subStart);
        if (subEnd < 0) {
            String subPrefix = upToCursor.substring(subStart);
            return buildSubcommandSuggestions(upToCursor.length(), subStart, subPrefix);
        }

        String sub = upToCursor.substring(subStart, subEnd).toLowerCase(Locale.ROOT);
        int argStart = skipWhitespace(upToCursor, subEnd);
        int argEnd = findFirstWhitespace(upToCursor, argStart);
        if (argEnd >= 0) {
            // Past the first argument of the subcommand (e.g. typing message body in .c msg <player> <msg>)
            return Suggestions.empty().join();
        }

        String argPrefix = argStart <= upToCursor.length() ? upToCursor.substring(argStart) : "";
        return switch (sub) {
            case "msg", "dm", "w" -> buildCandidateSuggestions(upToCursor.length(), argStart, argPrefix, onlinePlayers);
            default -> Suggestions.empty().join();
        };
    }

    public static String resolveUsageHint(String upToCursor) {
        if (!isDotCommandInput(upToCursor)) {
            return "";
        }
        String trimmed = upToCursor.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith(".c msg") || trimmed.startsWith(".c dm") || trimmed.startsWith(".c w")) {
            return ".c msg <player> <message>";
        }
        if (trimmed.startsWith(".c")) {
            return ".c <message> | list | msg | help";
        }
        return "";
    }

    private static Suggestions buildRootSuggestions(String rawInput, String upToCursor) {
        String lower = upToCursor.toLowerCase(Locale.ROOT);
        String afterDot = lower.length() > 1 ? lower.substring(1) : "";
        StringRange range = StringRange.between(0, upToCursor.length());
        List<Suggestion> list = new ArrayList<>();
        for (Entry entry : ROOT_COMMANDS) {
            String candidateLower = entry.text().toLowerCase(Locale.ROOT);
            boolean directMatch = candidateLower.startsWith(lower);
            boolean shorthandMatch = !afterDot.isEmpty()
                    && !lower.startsWith(".c")
                    && !entry.subcommand().isEmpty()
                    && entry.subcommand().startsWith(afterDot);
            if (directMatch || shorthandMatch) {
                list.add(new Suggestion(range, entry.text(), Component.literal(entry.description())));
            }
        }
        return list.isEmpty() ? Suggestions.empty().join() : new Suggestions(range, list);
    }

    private static Suggestions buildSubcommandSuggestions(int cursorEnd, int subStart, String subPrefix) {
        String lowerPrefix = subPrefix == null ? "" : subPrefix.toLowerCase(Locale.ROOT);
        StringRange range = StringRange.between(subStart, cursorEnd);
        List<Suggestion> list = new ArrayList<>();
        for (Entry entry : ROOT_COMMANDS) {
            if (!entry.subcommand().isEmpty() && entry.subcommand().startsWith(lowerPrefix)) {
                list.add(new Suggestion(range, entry.subcommand(), Component.literal(entry.description())));
            }
        }
        return list.isEmpty() ? Suggestions.empty().join() : new Suggestions(range, list);
    }

    private static Suggestions buildCandidateSuggestions(
            int cursorEnd,
            int tokenStart,
            String prefix,
            Collection<String> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return Suggestions.empty().join();
        }
        String lowerPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        StringRange range = StringRange.between(tokenStart, cursorEnd);
        List<Suggestion> list = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()
                    && candidate.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                list.add(new Suggestion(range, candidate));
            }
        }
        return list.isEmpty() ? Suggestions.empty().join() : new Suggestions(range, list);
    }

    private static int findFirstWhitespace(String s, int fromIndex) {
        for (int i = Math.max(0, fromIndex); i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String s, int fromIndex) {
        int i = Math.max(0, fromIndex);
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }
}
