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
        assert texts.size() == DotCommandSuggester.ROOT_COMMANDS.size()
                : "Expected all ROOT_COMMANDS (" + DotCommandSuggester.ROOT_COMMANDS.size() + "), got " + texts.size();
        assert texts.containsAll(List.of(".c", ".c list", ".c msg", ".c help", ".coords", ".farm start", ".friend", ".panic"));

        // Verify Tab replacement from "." replaces range [0..1] cleanly
        Suggestion listSug = s.getList().stream().filter(x -> ".c list".equals(x.getText())).findFirst().orElseThrow();
        assert ".c list".equals(listSug.apply("."));

        // When client chat is disabled, .c chat commands are hidden while utility dot commands remain
        Suggestions noChat = DotCommandSuggester.buildSuggestions(".", 1, List.of("Alice"), false);
        List<String> noChatTexts = noChat.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert !noChatTexts.contains(".c") && !noChatTexts.contains(".c list")
                : "Expected .c chat entries excluded when includeChatCommands=false, got " + noChatTexts;
        assert noChatTexts.containsAll(List.of(".coords", ".farm start", ".friend", ".panic"))
                : "Expected utility commands preserved when includeChatCommands=false, got " + noChatTexts;
        assert DotCommandSuggester.buildSuggestions(".c ", 3, List.of("Alice"), false).isEmpty();
        assert !DotCommandSuggester.buildSuggestions(".farm ", 6, List.of("Alice"), false).isEmpty();
    }

    private static void testDotCPrefixAndShorthandFilter() {
        Suggestions dotC = DotCommandSuggester.buildSuggestions(".c", 2, List.of());
        List<String> dotCTexts = dotC.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert dotCTexts.equals(List.of(".c", ".c list", ".c msg", ".c help", ".coordinates", ".coords"))
                : "Expected .c/.co root commands for '.c', got " + dotCTexts;

        // Typing ".l" or ". l" still surfaces ".c list" and ".friend list" without leaking .c chat items into .farm
        Suggestions dotL = DotCommandSuggester.buildSuggestions(".l", 2, List.of());
        List<String> dotLTexts = dotL.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert dotLTexts.contains(".c list") && dotLTexts.contains(".friend list");

        Suggestions dotSpaceL = DotCommandSuggester.buildSuggestions(". l", 3, List.of());
        assert dotSpaceL.getList().stream().anyMatch(x -> ".c list".equals(x.getText()));
    }

    private static void testSubcommandCompletionAfterSpace() {
        Suggestions afterSpace = DotCommandSuggester.buildSuggestions(".c ", 3, List.of());
        List<String> subs = afterSpace.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert subs.equals(List.of("list", "msg", "help")) : "Expected [list, msg, help], got " + subs;

        Suggestions farmSpace = DotCommandSuggester.buildSuggestions(".farm ", 6, List.of());
        List<String> farmSubs = farmSpace.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert farmSubs.equals(List.of("pos1", "pos2", "set", "start", "stop", "status", "clear"))
                : "Expected farm subcommands only, got " + farmSubs;

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

        Suggestions friendArg = DotCommandSuggester.buildSuggestions(".friend A", 9, online);
        List<String> fTexts = friendArg.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert fTexts.equals(List.of("Ahmet", "Ayse")) : "Expected friend player completions, got " + fTexts;

        Suggestions friendRemove = DotCommandSuggester.buildSuggestions(".friend remove M", 16, online);
        List<String> frTexts = friendRemove.getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        assert frTexts.equals(List.of("Mehmet")) : "Expected [Mehmet], got " + frTexts;

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
        assert ".farm pos1 | pos2 | set | start | stop | status | clear".equals(DotCommandSuggester.resolveUsageHint(".farm "));
        assert ".friend <player> | list | remove <player>".equals(DotCommandSuggester.resolveUsageHint(".friend "));
        assert "".equals(DotCommandSuggester.resolveUsageHint(".coords"));
    }
}
