package com.client.mod;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;

import java.util.List;
import java.util.stream.Collectors;

public class DotCommandSuggesterTest {

    public static void main(String[] args) {
        testNonDotInputReturnsEmpty();
        testSingleDotSuggestsAllRootCommands();
        testDotCPrefixAndShorthandFilter();
        testSubcommandCompletionAfterSpace();
        testPlayerArgumentCompletion();
        testUsageHints();
        System.out.println("ALL DotCommandSuggesterTest ASSERTIONS PASSED!");
    }

    private static void testNonDotInputReturnsEmpty() {
        assert !DotCommandSuggester.isDotCommandInput("");
        assert !DotCommandSuggester.isDotCommandInput("/client");
        assert !DotCommandSuggester.isDotCommandInput("hello");
        assert DotCommandSuggester.buildSuggestions("/help", 5, List.of("Alice")).isEmpty();
        assert DotCommandSuggester.buildSuggestions("normal chat", 11, List.of("Alice")).isEmpty();
    }

    private static void testSingleDotSuggestsAllRootCommands() {
        Suggestions s = DotCommandSuggester.buildSuggestions(".", 1, List.of("Alice"));
        List<String> texts = s.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert texts.equals(List.of(".c", ".c list", ".c msg", ".c help"))
                : "Expected [.c, .c list, .c msg, .c help], got " + texts;

        // Verify Tab replacement from "." replaces range [0..1] cleanly
        Suggestion listSug = s.getList().stream().filter(x -> ".c list".equals(x.getText())).findFirst().orElseThrow();
        assert ".c list".equals(listSug.apply("."));
    }

    private static void testDotCPrefixAndShorthandFilter() {
        Suggestions dotC = DotCommandSuggester.buildSuggestions(".c", 2, List.of());
        assert dotC.getList().size() == DotCommandSuggester.ROOT_COMMANDS.size();

        // Typing ".l" or ". l" still surfaces ".c list"
        Suggestions dotL = DotCommandSuggester.buildSuggestions(".l", 2, List.of());
        assert dotL.getList().size() == 1 && ".c list".equals(dotL.getList().get(0).getText());

        Suggestions dotSpaceL = DotCommandSuggester.buildSuggestions(". l", 3, List.of());
        assert dotSpaceL.getList().size() == 1 && ".c list".equals(dotSpaceL.getList().get(0).getText());
    }

    private static void testSubcommandCompletionAfterSpace() {
        Suggestions afterSpace = DotCommandSuggester.buildSuggestions(".c ", 3, List.of());
        List<String> subs = afterSpace.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert subs.equals(List.of("list", "msg", "help")) : "Expected [list, msg, help], got " + subs;

        Suggestions mSub = DotCommandSuggester.buildSuggestions(".c m", 4, List.of());
        List<String> mTexts = mSub.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert mTexts.equals(List.of("msg")) : "Expected [msg], got " + mTexts;
        assert ".c msg".equals(mSub.getList().get(0).apply(".c m"));
    }

    private static void testPlayerArgumentCompletion() {
        List<String> online = List.of("Ahmet", "Ayse", "Mehmet");

        Suggestions msgPlayers = DotCommandSuggester.buildSuggestions(".c msg A", 8, online);
        List<String> pTexts = msgPlayers.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert pTexts.equals(List.of("Ahmet", "Ayse")) : "Expected [Ahmet, Ayse], got " + pTexts;
        assert ".c msg Ahmet".equals(msgPlayers.getList().get(0).apply(".c msg A"));

        // Removed commands should not produce argument suggestions
        assert DotCommandSuggester.buildSuggestions(".c mute ", 8, online).isEmpty();
        assert DotCommandSuggester.buildSuggestions(".c unmute ", 10, online).isEmpty();
        assert DotCommandSuggester.buildSuggestions(".c url d", 8, online).isEmpty();

        // Once typing the message body after player name, player suggestions stop
        Suggestions msgBody = DotCommandSuggester.buildSuggestions(".c msg Ahmet selam", 18, online);
        assert msgBody.isEmpty();
    }

    private static void testUsageHints() {
        assert ".c msg <player> <message>".equals(DotCommandSuggester.resolveUsageHint(".c msg Ahmet "));
        assert ".c <message> | list | msg | help".equals(DotCommandSuggester.resolveUsageHint(".c "));
    }
}
