package com.client.mod;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.services.ProfileResult;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Full Vanilla-styled Account Switcher screen replacing IAS's AccountScreen.
 */
public final class SestiaAccountScreen extends Screen {
    private static final Map<UUID, PlayerSkin> SKINS = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, PlayerSkin> eldest) {
                    return size() > 64;
                }
            }
    );

    private final Screen parent;
    private EditBox searchBox;
    private AccountSelectionList accountList;
    private PlayerSkinWidget skinPreview;
    private Button loginButton;
    private Button offlineLoginButton;
    private Button editButton;
    private Button deleteButton;
    private String searchDraft = "";

    public SestiaAccountScreen(Screen parent) {
        super(Component.translatable("ias.accounts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (this.minecraft == null) return;

        if (IasBridge.isDisabled()) {
            this.minecraft.gui.setScreen(new AlertScreen(
                    this::onClose,
                    Component.translatable("ias.disabled.title").withStyle(ChatFormatting.RED),
                    Component.translatable("ias.disabled.text"),
                    CommonComponents.GUI_BACK,
                    true
            ));
            return;
        }

        if (!IasBridge.isGameDisclaimerShown()) {
            this.minecraft.gui.setScreen(new AlertScreen(
                    () -> {
                        IasBridge.markGameDisclaimerShown();
                        if (this.minecraft != null && this.minecraft.gui != null) {
                            this.minecraft.gui.setScreen(this);
                        }
                    },
                    Component.translatable("ias.disclaimer.title").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("ias.disclaimer.text"),
                    CommonComponents.GUI_CONTINUE,
                    true
            ));
            return;
        }

        int listTop = 40;
        int listBottom = Math.max(listTop + 36, this.height - 56);

        this.searchBox = new EditBox(this.font, this.width / 2 - 100, 18, 200, 16,
                this.searchBox, Component.translatable("ias.accounts.search"));
        this.searchBox.setHint(Component.translatable("ias.accounts.search").withStyle(ChatFormatting.DARK_GRAY));
        this.searchBox.setValue(this.searchDraft);
        this.addRenderableWidget(this.searchBox);

        if (this.skinPreview == null) {
            this.skinPreview = new PlayerSkinWidget(85, 120, this.minecraft.getEntityModels(), this::selectedSkin);
        }
        this.skinPreview.visible = this.width >= 440;
        this.skinPreview.setPosition(10, Math.max(listTop, this.height / 2 - 60));
        this.addRenderableWidget(this.skinPreview);

        int btnY1 = this.height - 48;
        int btnY2 = this.height - 24;
        int colLeft = this.width / 2 - 154;
        int colMid = this.width / 2 - 50;
        int colRight = this.width / 2 + 54;

        this.loginButton = Button.builder(Component.translatable("ias.accounts.login"), btn -> triggerLogin(true))
                .bounds(colLeft, btnY1, 100, 20)
                .build();
        this.addRenderableWidget(this.loginButton);

        this.offlineLoginButton = Button.builder(Component.translatable("ias.accounts.offlineLogin"), btn -> triggerLogin(false))
                .bounds(colLeft, btnY2, 100, 20)
                .build();
        this.addRenderableWidget(this.offlineLoginButton);

        this.editButton = Button.builder(Component.translatable("ias.accounts.edit"), btn -> editSelected())
                .bounds(colMid, btnY1, 100, 20)
                .build();
        this.addRenderableWidget(this.editButton);

        this.deleteButton = Button.builder(Component.translatable("ias.accounts.delete"),
                        btn -> deleteSelected(!isShiftDown()))
                .bounds(colRight, btnY1, 100, 20)
                .build();
        this.addRenderableWidget(this.deleteButton);

        this.addRenderableWidget(Button.builder(Component.translatable("ias.accounts.add"), btn -> openAddAccount())
                .bounds(colMid, btnY2, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, btn -> onClose())
                .bounds(colRight, btnY2, 100, 20)
                .build());

        if (this.accountList == null) {
            this.accountList = new AccountSelectionList(this, this.minecraft, this.width, listBottom - listTop, listTop, 26);
        } else {
            this.accountList.setRectangle(this.width, listBottom - listTop, 0, listTop);
        }
        this.addRenderableWidget(this.accountList);

        this.searchBox.setResponder(query -> {
            this.searchDraft = query != null ? query : "";
            this.accountList.refresh(this.searchDraft);
        });
        this.accountList.refresh(this.searchDraft);
        updateButtonStates();
    }

    private PlayerSkin selectedSkin() {
        AccountEntry selected = this.accountList != null ? this.accountList.getSelected() : null;
        if (selected != null) {
            return resolveSkin(selected.account);
        }
        UUID selfId = this.minecraft != null && this.minecraft.getUser() != null
                ? this.minecraft.getUser().getProfileId()
                : new UUID(0L, 0L);
        return DefaultPlayerSkin.get(selfId);
    }

    private PlayerSkin resolveSkin(Object account) {
        UUID skinUuid = IasBridge.accountSkinUuid(account);
        PlayerSkin cached = SKINS.get(skinUuid);
        if (cached != null) {
            return cached;
        }
        PlayerSkin fallback = DefaultPlayerSkin.get(skinUuid);
        SKINS.put(skinUuid, fallback);
        if (skinUuid.version() != 4 || this.minecraft == null) {
            return fallback;
        }

        Minecraft mc = this.minecraft;
        CompletableFuture.supplyAsync(() -> {
                    ProfileResult res = mc.services().sessionService().fetchProfile(skinUuid, false);
                    return res != null ? res.profile() : null;
                }, IasBridge.getExecutor())
                .thenComposeAsync(profile -> profile == null
                                ? CompletableFuture.completedFuture(Optional.<PlayerSkin>empty())
                                : mc.getSkinManager().get(profile),
                        IasBridge.getExecutor())
                .thenAcceptAsync(opt -> {
                    if (opt != null) {
                        opt.ifPresent(skin -> SKINS.put(skinUuid, skin));
                    }
                }, mc);
        return fallback;
    }

    private void updateButtonStates() {
        AccountEntry selected = this.accountList != null ? this.accountList.getSelected() : null;
        boolean hasSelection = selected != null;
        if (this.loginButton != null) {
            this.loginButton.active = hasSelection && IasBridge.canLogin(selected.account);
        }
        if (this.offlineLoginButton != null) {
            this.offlineLoginButton.active = hasSelection;
        }
        if (this.editButton != null) {
            this.editButton.active = hasSelection;
        }
        if (this.deleteButton != null) {
            this.deleteButton.active = hasSelection;
        }
    }

    private void triggerLogin(boolean online) {
        AccountEntry selected = this.accountList != null ? this.accountList.getSelected() : null;
        if (selected == null || this.minecraft == null || this.minecraft.gui == null) return;
        boolean useOnline = online && IasBridge.canLogin(selected.account);
        Screen afterLogin = IasBridge.shouldCloseOnLogin() ? this.parent : this;
        this.minecraft.gui.setScreen(new SestiaLoginScreen(afterLogin, selected.account, useOnline, null));
    }

    private void openAddAccount() {
        if (this.minecraft == null || this.minecraft.gui == null) return;
        this.minecraft.gui.setScreen(new SestiaAddAccountScreen(this, acc -> {
            if (acc == null) return;
            List<Object> accounts = IasBridge.getAccounts();
            accounts.add(acc);
            IasBridge.saveStorage();
        }));
    }

    private void editSelected() {
        AccountEntry selected = this.accountList != null ? this.accountList.getSelected() : null;
        if (selected == null || this.minecraft == null || this.minecraft.gui == null) return;
        List<Object> accounts = IasBridge.getAccounts();
        int idx = accounts.indexOf(selected.account);
        if (idx < 0) return;

        boolean isMs = IasBridge.isMicrosoftAccount(selected.account);
        String oldName = IasBridge.accountName(selected.account);
        String oldPass = IasBridge.getAutoLoginPassword(oldName);
        this.minecraft.gui.setScreen(new SestiaAddAccountScreen(this, updated -> {
            if (isMs || updated == null) {
                return;
            }
            String newName = IasBridge.accountName(updated);
            if (!oldName.equalsIgnoreCase(newName)) {
                IasBridge.saveAutoLoginPassword(oldName, "");
            }
            List<Object> list = IasBridge.getAccounts();
            if (idx >= 0 && idx < list.size()) {
                list.set(idx, updated);
            } else {
                list.add(updated);
            }
            IasBridge.saveStorage();
        }, true, isMs, oldName, oldPass));
    }

    private void deleteSelected(boolean confirm) {
        AccountEntry selected = this.accountList != null ? this.accountList.getSelected() : null;
        if (selected == null || this.minecraft == null || this.minecraft.gui == null) return;
        String deletedName = IasBridge.accountName(selected.account);

        Runnable performDelete = () -> {
            IasBridge.getAccounts().remove(selected.account);
            IasBridge.saveStorage();
            IasBridge.saveAutoLoginPassword(deletedName, "");
            if (this.accountList != null) {
                this.accountList.refresh(this.searchDraft);
            }
        };

        if (!confirm) {
            performDelete.run();
            return;
        }
        String name = IasBridge.accountName(selected.account);
        Component prompt = Component.translatable("ias.delete.confirm", Component.literal(name));
        this.minecraft.gui.setScreen(new SestiaDeleteAccountScreen(this, prompt, performDelete));
    }

    private void moveAccount(AccountEntry entry, int delta) {
        if (entry == null || this.accountList == null) return;
        List<Object> accounts = IasBridge.getAccounts();
        int from = accounts.indexOf(entry.account);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= accounts.size()) return;
        Object other = accounts.get(to);
        accounts.set(to, entry.account);
        accounts.set(from, other);
        IasBridge.saveStorage();
        this.accountList.refresh(this.searchDraft);
        this.accountList.selectAccount(entry.account);
    }

    private boolean isShiftDown() {
        return this.minecraft != null && this.minecraft.hasShiftDown();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if ((key == GLFW.GLFW_KEY_DOWN && event.hasShiftDown()) || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            moveAccount(this.accountList != null ? this.accountList.getSelected() : null, 1);
            return true;
        }
        if ((key == GLFW.GLFW_KEY_UP && event.hasShiftDown()) || key == GLFW.GLFW_KEY_PAGE_UP) {
            moveAccount(this.accountList != null ? this.accountList.getSelected() : null, -1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && event.hasControlDown() && this.searchBox != null) {
            this.setFocused(this.searchBox);
            return true;
        }
        if (key == GLFW.GLFW_KEY_C && event.hasControlDown() && this.minecraft != null) {
            AccountEntry selected = this.accountList != null ? this.accountList.getSelected() : null;
            if (selected != null) {
                String value = event.hasShiftDown()
                        ? String.valueOf(IasBridge.accountUuid(selected.account))
                        : IasBridge.accountName(selected.account);
                this.minecraft.keyboardHandler.setClipboard(value);
                return true;
            }
        }
        if (super.keyPressed(event)) {
            return true;
        }
        if (this.getFocused() instanceof EditBox && key == GLFW.GLFW_KEY_BACKSPACE) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            triggerLogin(!event.hasShiftDown());
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            deleteSelected(!event.hasShiftDown());
            return true;
        }
        if ((key == GLFW.GLFW_KEY_N && event.hasControlDown()) || key == GLFW.GLFW_KEY_INSERT) {
            openAddAccount();
            return true;
        }
        if (key == GLFW.GLFW_KEY_E && event.hasControlDown()) {
            editSelected();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && this.minecraft.gui != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    public static void disableIasDefaultWidgets() {
        try {
            Class<?> cfgCls = Class.forName("ru.vidtu.ias.config.IASConfig");
            cfgCls.getField("serversButton").setBoolean(null, false);
            cfgCls.getField("titleButton").setBoolean(null, false);
            cfgCls.getField("serversText").setBoolean(null, false);
            cfgCls.getField("titleText").setBoolean(null, false);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);
        if (this.minecraft != null && this.minecraft.getUser() != null && this.width >= 440) {
            String active = this.minecraft.getUser().getName();
            graphics.centeredText(this.font, Component.literal(active).withStyle(ChatFormatting.GRAY), 52, this.height / 2 + 66, 0xFFA0A0A0);
        }
    }

    private final class AccountSelectionList extends ObjectSelectionList<AccountEntry> {
        private final SestiaAccountScreen screen;

        AccountSelectionList(SestiaAccountScreen screen, Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
            this.screen = screen;
        }

        @Override
        public int getRowWidth() {
            return Math.min(260, Math.max(190, this.screen.width - 210));
        }

        @Override
        public void setSelected(AccountEntry entry) {
            super.setSelected(entry);
            this.screen.updateButtonStates();
        }

        void selectAccount(Object account) {
            for (AccountEntry entry : children()) {
                if (entry.account == account) {
                    setSelected(entry);
                    return;
                }
            }
        }

        void refresh(String query) {
            Object prev = getSelected() != null ? getSelected().account : null;
            List<Object> raw = new ArrayList<>(IasBridge.getAccounts());
            String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            if (!q.isEmpty()) {
                raw = raw.stream()
                        .filter(acc -> IasBridge.accountName(acc).toLowerCase(Locale.ROOT).contains(q))
                        .sorted(Comparator.comparingInt((Object acc) ->
                                IasBridge.accountName(acc).toLowerCase(Locale.ROOT).startsWith(q) ? 0 : 1))
                        .toList();
            }
            List<AccountEntry> entries = raw.stream()
                    .map(acc -> new AccountEntry(this.screen, acc))
                    .toList();
            replaceEntries(entries);
            if (prev != null) {
                selectAccount(prev);
            } else if (!entries.isEmpty()) {
                setSelected(entries.getFirst());
            }
            this.screen.updateButtonStates();
        }
    }

    private final class AccountEntry extends ObjectSelectionList.Entry<AccountEntry> {
        private final SestiaAccountScreen screen;
        private final Object account;
        private final List<FormattedCharSequence> tooltip;
        private long lastClickMs = 0L;

        AccountEntry(SestiaAccountScreen screen, Object account) {
            this.screen = screen;
            this.account = account;
            UUID uuid = IasBridge.accountUuid(account);
            Component c1 = CommonComponents.optionNameValue(
                    Component.translatable("ias.accounts.tip.nick"),
                    Component.literal(IasBridge.accountName(account))
            );
            Component c2 = CommonComponents.optionNameValue(
                    Component.translatable("ias.accounts.tip.uuid"),
                    Component.literal(uuid != null ? uuid.toString() : "-")
            );
            Component c3 = CommonComponents.optionNameValue(
                    Component.translatable("ias.accounts.tip.type"),
                    Component.translatable(IasBridge.accountTypeTipKey(account))
            );
            this.tooltip = List.of(c1.getVisualOrderText(), c2.getVisualOrderText(), c3.getVisualOrderText());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = getContentX();
            int y = getContentY();
            int w = getContentWidth();

            PlayerSkin skin = this.screen.resolveSkin(this.account);
            PlayerFaceExtractor.extractRenderState(graphics, skin, x + 2, y + 1, 20);

            String name = IasBridge.accountName(this.account);
            int nameColor = resolveNameColor(name, IasBridge.accountUuid(this.account));
            graphics.text(this.screen.font, name, x + 28, y + 2, nameColor);

            MutableComponent tagLine = Component.empty();
            if (IasBridge.isMicrosoftAccount(this.account)) {
                tagLine.append(Component.literal("[Microsoft]").withStyle(ChatFormatting.AQUA));
            } else {
                tagLine.append(Component.literal("[Offline]").withStyle(ChatFormatting.GRAY));
            }
            if (!IasBridge.getAutoLoginPassword(name).isEmpty()) {
                tagLine.append(Component.literal(" [Auto-Login]").withStyle(ChatFormatting.GREEN));
            }
            if (IasBridge.isInsecure(this.account)) {
                tagLine.append(Component.literal(" [!]").withStyle(ChatFormatting.YELLOW));
            }
            graphics.text(this.screen.font, tagLine, x + 28, y + 13, 0xFFA0A0A0);

            boolean active = hovered || this.equals(this.screen.accountList.getSelected());
            if (active) {
                List<AccountEntry> list = this.screen.accountList.children();
                boolean isFirst = !list.isEmpty() && this == list.getFirst();
                boolean isLast = !list.isEmpty() && this == list.getLast();
                int upX = x + w - 24;
                int dnX = x + w - 12;
                int upColor = isFirst ? 0xFF555555 : (mouseX >= upX && mouseX <= upX + 10 ? 0xFFFFFF55 : 0xFFCCCCCC);
                int dnColor = isLast ? 0xFF555555 : (mouseX >= dnX && mouseX <= dnX + 10 ? 0xFFFFFF55 : 0xFFCCCCCC);
                graphics.text(this.screen.font, "^", upX + 2, y + 7, upColor);
                graphics.text(this.screen.font, "v", dnX + 2, y + 7, dnColor);
            }

            if (hovered) {
                graphics.setTooltipForNextFrame(this.tooltip, mouseX, mouseY);
            }
        }

        private int resolveNameColor(String name, UUID uuid) {
            User user = this.screen.minecraft != null ? this.screen.minecraft.getUser() : null;
            if (user == null || !name.equalsIgnoreCase(user.getName())) {
                return 0xFFFFFFFF;
            }
            if (uuid != null && uuid.equals(user.getProfileId())) {
                return 0xFF55FF55;
            }
            return name.equals(user.getName()) ? 0xFFFFFF55 : 0xFFFFAA00;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            this.screen.accountList.setSelected(this);
            int right = getContentRight();
            double mx = event.x();
            if (mx >= right - 24 && mx < right - 12) {
                this.screen.moveAccount(this, -1);
                return true;
            }
            if (mx >= right - 12 && mx <= right) {
                this.screen.moveAccount(this, 1);
                return true;
            }

            long now = System.currentTimeMillis();
            if (doubleClick || (now - this.lastClickMs) < 350L) {
                this.lastClickMs = 0L;
                this.screen.triggerLogin(!this.screen.isShiftDown());
                return true;
            }
            this.lastClickMs = now;
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(IasBridge.accountName(this.account));
        }
    }
}
