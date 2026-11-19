package com.client.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;

/**
 * Vanilla-styled Connecting / Account Switching screen replacing IAS's LoginPopupScreen.
 */
public final class SestiaLoginScreen extends Screen {
    private final Screen parent;
    private final Object account;
    private final boolean online;
    private final Runnable onLoginSuccess;

    private volatile boolean cancelled = false;
    private volatile boolean started = false;
    private volatile boolean failed = false;
    private volatile Component statusText = Component.translatable("ias.login.initializing").withStyle(ChatFormatting.YELLOW);
    private volatile CompletableFuture<String> passwordFuture;

    private EditBox passwordEdit;
    private Button submitButton;
    private Button cancelButton;
    private MultiLineLabel statusLabel;
    private MultiLineLabel errorNoteLabel;
    private String draftPassword = "";

    public SestiaLoginScreen(Screen parent, Object account, boolean online, Runnable onLoginSuccess) {
        super(Component.translatable("ias.login"));
        this.parent = parent;
        this.account = account;
        this.online = online;
        this.onLoginSuccess = onLoginSuccess;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2 - 100;
        int centerY = this.height / 2;

        if (this.passwordFuture != null && !this.passwordFuture.isDone()) {
            this.passwordEdit = new EditBox(this.font, centerX, centerY - 12, 200, 20, Component.translatable("ias.password"));
            this.passwordEdit.setMaxLength(32);
            this.passwordEdit.setHint(Component.translatable("ias.password.hint").withStyle(ChatFormatting.DARK_GRAY));
            this.passwordEdit.setValue(this.draftPassword);
            this.passwordEdit.addFormatter((text, offset) -> FormattedCharSequence.forward(
                    IasBridge.isPasswordEchoing() ? "*".repeat(text.length()) : "", Style.EMPTY));
            this.passwordEdit.setResponder(txt -> {
                this.draftPassword = txt != null ? txt : "";
                if (this.submitButton != null) {
                    this.submitButton.active = !this.draftPassword.isBlank();
                }
            });
            this.addRenderableWidget(this.passwordEdit);
            this.setInitialFocus(this.passwordEdit);

            this.submitButton = Button.builder(CommonComponents.GUI_DONE, btn -> submitPassword())
                    .bounds(centerX, centerY + 18, 200, 20)
                    .build();
            this.submitButton.active = !this.draftPassword.isBlank();
            this.addRenderableWidget(this.submitButton);
        }

        int cancelY = (this.passwordFuture != null && !this.passwordFuture.isDone()) ? centerY + 42 : centerY + 34;
        Component cancelLabel = this.failed ? CommonComponents.GUI_BACK : CommonComponents.GUI_CANCEL;
        this.cancelButton = Button.builder(cancelLabel, btn -> onClose())
                .bounds(centerX, cancelY, 200, 20)
                .build();
        this.addRenderableWidget(this.cancelButton);

        this.statusLabel = null;
        this.errorNoteLabel = null;

        if (!this.started) {
            this.started = true;
            beginLogin();
        }
    }

    private void beginLogin() {
        if (!this.online || !IasBridge.canLogin(this.account)) {
            this.statusText = Component.translatable("ias.login.services").withStyle(ChatFormatting.YELLOW);
        }
        IasBridge.startAccountLogin(this.account, this.online, new IasBridge.LoginCallback() {
            @Override
            public boolean isCancelled() {
                return SestiaLoginScreen.this.cancelled || !isCurrentScreen();
            }

            @Override
            public void onStage(String translationKey, Object... args) {
                Minecraft mc = SestiaLoginScreen.this.minecraft;
                if (mc == null) return;
                Component next = Component.translatable(translationKey, args).withStyle(ChatFormatting.YELLOW);
                mc.execute(() -> {
                    SestiaLoginScreen.this.statusText = next;
                    SestiaLoginScreen.this.statusLabel = null;
                });
            }

            @Override
            public CompletableFuture<String> onRequestPassword() {
                CompletableFuture<String> future = new CompletableFuture<>();
                SestiaLoginScreen.this.passwordFuture = future;
                Minecraft mc = SestiaLoginScreen.this.minecraft;
                if (mc != null) {
                    mc.execute(() -> SestiaLoginScreen.this.init(SestiaLoginScreen.this.width, SestiaLoginScreen.this.height));
                }
                return future;
            }

            @Override
            public void onSuccess(Object loginData, boolean storageChanged) {
                if (isCancelled()) return;
                if (loginData == null) {
                    Minecraft mc = SestiaLoginScreen.this.minecraft;
                    if (mc != null) {
                        mc.execute(SestiaLoginScreen.this::onClose);
                    }
                    return;
                }
                if (storageChanged) {
                    IasBridge.saveStorage();
                }
                Minecraft mc = SestiaLoginScreen.this.minecraft;
                if (mc == null) return;
                mc.execute(() -> {
                    SestiaLoginScreen.this.statusText = Component.translatable("ias.login.services").withStyle(ChatFormatting.YELLOW);
                    SestiaLoginScreen.this.statusLabel = null;
                });
                IasBridge.applyLoginData(mc, loginData).thenRunAsync(() -> {
                    if (SestiaLoginScreen.this.cancelled) return;
                    SestiaChatClient.onSessionAccountChanged();
                    if (SestiaLoginScreen.this.onLoginSuccess != null) {
                        SestiaLoginScreen.this.onLoginSuccess.run();
                    } else if (mc.gui != null) {
                        mc.gui.setScreen(SestiaLoginScreen.this.parent);
                    }
                }, mc).exceptionally(err -> {
                    onError(err);
                    return null;
                });
            }

            @Override
            public void onError(Throwable error) {
                if (SestiaLoginScreen.this.cancelled) return;
                String key = IasBridge.extractFriendlyErrorKey(error);
                Component msg = Component.translatable(key).withStyle(ChatFormatting.RED);
                Minecraft mc = SestiaLoginScreen.this.minecraft;
                if (mc != null) {
                    mc.execute(() -> {
                        SestiaLoginScreen.this.failed = true;
                        SestiaLoginScreen.this.passwordFuture = null;
                        SestiaLoginScreen.this.statusText = msg;
                        SestiaLoginScreen.this.statusLabel = null;
                        SestiaLoginScreen.this.errorNoteLabel = null;
                        if (isCurrentScreen()) {
                            SestiaLoginScreen.this.init(SestiaLoginScreen.this.width, SestiaLoginScreen.this.height);
                        }
                    });
                }
            }
        }, null);
    }

    private boolean isCurrentScreen() {
        Minecraft mc = this.minecraft;
        return mc != null && mc.gui != null && mc.gui.screen() == this;
    }

    private void submitPassword() {
        CompletableFuture<String> future = this.passwordFuture;
        if (future == null || future.isDone()) return;
        String val = this.passwordEdit != null ? this.passwordEdit.getValue() : "";
        if (val.isBlank()) return;
        this.passwordFuture = null;
        this.draftPassword = "";
        future.complete(val);
        this.init(this.width, this.height);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && this.passwordFuture != null && !this.passwordFuture.isDone()) {
            submitPassword();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        this.cancelled = true;
        CompletableFuture<String> future = this.passwordFuture;
        if (future != null && !future.isDone()) {
            future.complete(null);
        }
    }

    @Override
    public void onClose() {
        this.cancelled = true;
        CompletableFuture<String> future = this.passwordFuture;
        if (future != null && !future.isDone()) {
            future.complete(null);
        }
        if (this.minecraft != null && this.minecraft.gui != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.centeredText(this.font, this.title, centerX, centerY - 68, 0xFFFFFFFF);

        String targetName = IasBridge.accountName(this.account);
        String targetType = (this.online && IasBridge.canLogin(this.account)) ? "Microsoft" : "Offline";
        graphics.centeredText(this.font, Component.literal(targetName + " (" + targetType + ")"), centerX, centerY - 52, 0xFFA0A0A0);

        if (this.passwordFuture != null && !this.passwordFuture.isDone()) {
            graphics.centeredText(this.font, Component.translatable("ias.password"), centerX, centerY - 26, 0xFFFFFFFF);
            graphics.centeredText(this.font, Component.translatable("ias.password.tip").withStyle(ChatFormatting.YELLOW), centerX, centerY + 70, 0xFFFFFF55);
            return;
        }

        if (this.statusLabel == null) {
            Component msg = this.statusText != null ? this.statusText : Component.empty();
            this.statusLabel = MultiLineLabel.create(this.font, msg, Math.max(220, Math.min(340, this.width - 40)));
        }
        int lineCount = Math.max(1, this.statusLabel.getLineCount());
        int textY = centerY - (lineCount * 9) / 2 - 12;
        this.statusLabel.visitLines(net.minecraft.client.gui.TextAlignment.CENTER, centerX, textY, 10, graphics.textRenderer());

        if (this.failed) {
            if (this.errorNoteLabel == null) {
                this.errorNoteLabel = MultiLineLabel.create(
                        this.font,
                        Component.translatable("ias.error.note").withStyle(ChatFormatting.AQUA),
                        Math.max(240, Math.min(360, this.width - 40))
                );
            }
            this.errorNoteLabel.visitLines(net.minecraft.client.gui.TextAlignment.CENTER, centerX, centerY + 62, 10, graphics.textRenderer());
        }
    }
}
