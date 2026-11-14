package com.client.mod;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DotCommandSuggesterTest {

    public static void main(String[] args) {
        testNonDotInputReturnsEmpty();
        testSingleDotSuggestsAllRootCommands();
        testDotCPrefixAndShorthandFilter();
        testSubcommandCompletionAfterSpace();
        testPlayerAndUrlArgumentCompletion();
        testUsageHints();
        System.out.println("ALL DotCommandSuggesterTest ASSERTIONS PASSED!");
    }

    private static void testNonDotInputReturnsEmpty() {
        assert !DotCommandSuggester.isDotCommandInput("");
        assert !DotCommandSuggester.isDotCommandInput("/client");
        assert !DotCommandSuggester.isDotCommandInput("hello");
        assert DotCommandSuggester.buildSuggestions("/help", 5, List.of("Alice"), Set.of()).isEmpty();
        assert DotCommandSuggester.buildSuggestions("normal chat", 11, List.of("Alice"), Set.of()).isEmpty();
    }

    private static void testSingleDotSuggestsAllRootCommands() {
        Suggestions s = DotCommandSuggester.buildSuggestions(".", 1, List.of("Alice"), Set.of());
        List<String> texts = s.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert texts.contains(".c") : "Expected .c in root suggestions: " + texts;
        assert texts.contains(".c list") : "Expected .c list in root suggestions: " + texts;
        assert texts.contains(".c msg") : "Expected .c msg in root suggestions: " + texts;
        assert texts.contains(".c mute") : "Expected .c mute in root suggestions: " + texts;
        assert texts.contains(".c unmute") : "Expected .c unmute in root suggestions: " + texts;
        assert texts.contains(".c status") : "Expected .c status in root suggestions: " + texts;
        assert texts.contains(".c url") : "Expected .c url in root suggestions: " + texts;
        assert texts.contains(".c help") : "Expected .c help in root suggestions: " + texts;

        // Verify Tab replacement from "." replaces range [0..1] cleanly
        Suggestion listSug = s.getList().stream().filter(x -> ".c list".equals(x.getText())).findFirst().orElseThrow();
        assert ".c list".equals(listSug.apply("."));
    }

    private static void testDotCPrefixAndShorthandFilter() {
        Suggestions dotC = DotCommandSuggester.buildSuggestions(".c", 2, List.of(), Set.of());
        assert dotC.getList().size() == DotCommandSuggester.ROOT_COMMANDS.size();

        // Typing ".l" or ". l" still surfaces ".c list"
        Suggestions dotL = DotCommandSuggester.buildSuggestions(".l", 2, List.of(), Set.of());
        assert dotL.getList().size() == 1 && ".c list".equals(dotL.getList().get(0).getText());

        Suggestions dotSpaceL = DotCommandSuggester.buildSuggestions(". l", 3, List.of(), Set.of());
        assert dotSpaceL.getList().size() == 1 && ".c list".equals(dotSpaceL.getList().get(0).getText());
    }

    private static void testSubcommandCompletionAfterSpace() {
        Suggestions afterSpace = DotCommandSuggester.buildSuggestions(".c ", 3, List.of(), Set.of());
        List<String> subs = afterSpace.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert subs.containsAll(List.of("list", "msg", "mute", "unmute", "status", "url", "help"));

        Suggestions mSub = DotCommandSuggester.buildSuggestions(".c m", 4, List.of(), Set.of());
        List<String> mTexts = mSub.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert mTexts.equals(List.of("msg", "mute")) : "Expected [msg, mute], got " + mTexts;
        assert ".c msg".equals(mSub.getList().get(0).apply(".c m"));
    }

    private static void testPlayerAndUrlArgumentCompletion() {
        List<String> online = List.of("Ahmet", "Ayse", "Mehmet");
        Set<String> muted = Set.of("TrollUser");

        Suggestions msgPlayers = DotCommandSuggester.buildSuggestions(".c msg A", 8, online, muted);
        List<String> pTexts = msgPlayers.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert pTexts.equals(List.of("Ahmet", "Ayse")) : "Expected [Ahmet, Ayse], got " + pTexts;
        assert ".c msg Ahmet".equals(msgPlayers.getList().get(0).apply(".c msg A"));

        Suggestions unmutePlayers = DotCommandSuggester.buildSuggestions(".c unmute ", 10, online, muted);
        assert unmutePlayers.getList().size() == 1 && "TrollUser".equals(unmutePlayers.getList().get(0).getText());

        Suggestions urlArgs = DotCommandSuggester.buildSuggestions(".c url d", 8, online, muted);
        assert urlArgs.getList().size() == 1 && "default".equals(urlArgs.getList().get(0).getText());

        // Once typing the message body after player name, player suggestions stop
        Suggestions msgBody = DotCommandSuggester.buildSuggestions(".c msg Ahmet selam", 18, online, muted);
        assert msgBody.isEmpty();
    }

    private static void testUsageHints() {
        assert ".c msg <player> <message>".equals(DotCommandSuggester.resolveUsageHint(".c msg Ahmet "));
        assert ".c mute <player>".equals(DotCommandSuggester.resolveUsageHint(".c mute "));
        assert ".c url <wss://...|default>".equals(DotCommandSuggester.resolveUsageHint(".c url "));
    }
}
