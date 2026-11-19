package com.client.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
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

import java.io.Closeable;
import java.util.function.Consumer;

/**
 * Vanilla screen replacing IAS's MicrosoftPopupScreen for Microsoft OAuth account creation.
 */
public final class SestiaMicrosoftAuthScreen extends Screen {
    private final Screen parent;
    private final Consumer<Object> handler;
    private final boolean needsPasswordPrompt;

    private Object crypt;
    private volatile boolean cancelled = false;
    private volatile boolean started = false;
    private volatile boolean failed = false;
    private volatile Component statusText = Component.translatable("ias.login.initializing").withStyle(ChatFormatting.YELLOW);
    private volatile String authUrl = "";
    private volatile String userCode = "";
    private Closeable authSession;

    private EditBox passwordEdit;
    private Button submitButton;
    private MultiLineLabel statusLabel;
    private MultiLineLabel errorNoteLabel;
    private String draftPassword = "";

    public SestiaMicrosoftAuthScreen(Screen parent, Consumer<Object> handler, boolean needsPasswordPrompt, Object fixedCrypt) {
        super(Component.translatable("ias.microsoft"));
        this.parent = parent;
        this.handler = handler;
        this.needsPasswordPrompt = needsPasswordPrompt;
        this.crypt = fixedCrypt;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2 - 100;
        int centerY = this.height / 2;
        this.statusLabel = null;
        this.errorNoteLabel = null;

        if (!this.failed && this.crypt == null && this.needsPasswordPrompt) {
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

            this.submitButton = Button.builder(CommonComponents.GUI_CONTINUE, btn -> submitPassword())
                    .bounds(centerX, centerY + 18, 200, 20)
                    .build();
            this.submitButton.active = !this.draftPassword.isBlank();
            this.addRenderableWidget(this.submitButton);

            this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, btn -> onClose())
                    .bounds(centerX, centerY + 42, 200, 20)
                    .build());
            return;
        }

        if (!this.failed && !this.authUrl.isBlank()) {
            String copyTarget = !this.userCode.isBlank() ? this.userCode : this.authUrl;
            String copyLabel = !this.userCode.isBlank() ? "Copy Code" : "Copy Link";
            this.addRenderableWidget(Button.builder(Component.literal("Open Browser"), btn -> IasBridge.openUrl(this.authUrl))
                    .bounds(centerX, centerY + 16, 98, 20)
                    .build());
            this.addRenderableWidget(Button.builder(Component.literal(copyLabel), btn -> {
                if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
                    this.minecraft.keyboardHandler.setClipboard(copyTarget);
                }
            }).bounds(centerX + 102, centerY + 16, 98, 20).build());
        }

        int cancelY = (!this.failed && !this.authUrl.isBlank()) ? centerY + 40 : centerY + 34;
        Component cancelLabel = this.failed ? CommonComponents.GUI_BACK : CommonComponents.GUI_CANCEL;
        this.addRenderableWidget(Button.builder(cancelLabel, btn -> onClose())
                .bounds(centerX, cancelY, 200, 20)
                .build());

        if (!this.started) {
            this.started = true;
            beginOAuth();
        }
    }

    private void submitPassword() {
        String val = this.passwordEdit != null ? this.passwordEdit.getValue() : "";
        if (val.isBlank()) return;
        Object created = IasBridge.passwordCrypt(val);
        this.draftPassword = "";
        if (created == null) {
            this.failed = true;
            this.statusText = Component.literal("Unable to initialize password cipher.").withStyle(ChatFormatting.RED);
            this.statusLabel = null;
            this.errorNoteLabel = null;
        } else {
            this.crypt = created;
        }
        this.init(this.width, this.height);
    }

    private void beginOAuth() {
        this.authSession = IasBridge.startMicrosoftAuth(this.crypt, new IasBridge.CreateCallback() {
            @Override
            public boolean isCancelled() {
                return SestiaMicrosoftAuthScreen.this.cancelled || !isCurrentScreen();
            }

            @Override
            public void onStage(String translationKey, Object... args) {
                Minecraft mc = SestiaMicrosoftAuthScreen.this.minecraft;
                if (mc == null) return;
                Component next = Component.translatable(translationKey, args).withStyle(ChatFormatting.YELLOW);
                mc.execute(() -> {
                    SestiaMicrosoftAuthScreen.this.statusText = next;
                    SestiaMicrosoftAuthScreen.this.statusLabel = null;
                });
            }

            @Override
            public void onCreated(Object microsoftAccount) {
                if (isCancelled()) return;
                Minecraft mc = SestiaMicrosoftAuthScreen.this.minecraft;
                if (mc == null) return;
                mc.execute(() -> {
                    if (SestiaMicrosoftAuthScreen.this.handler != null && microsoftAccount != null) {
                        SestiaMicrosoftAuthScreen.this.handler.accept(microsoftAccount);
                    }
                    onClose();
                });
            }

            @Override
            public void onError(Throwable error) {
                if (SestiaMicrosoftAuthScreen.this.cancelled) return;
                String key = IasBridge.extractFriendlyErrorKey(error);
                Component msg = Component.translatable(key).withStyle(ChatFormatting.RED);
                Minecraft mc = SestiaMicrosoftAuthScreen.this.minecraft;
                if (mc != null) {
                    mc.execute(() -> {
                        SestiaMicrosoftAuthScreen.this.failed = true;
                        SestiaMicrosoftAuthScreen.this.statusText = msg;
                        SestiaMicrosoftAuthScreen.this.statusLabel = null;
                        SestiaMicrosoftAuthScreen.this.errorNoteLabel = null;
                        if (isCurrentScreen()) {
                            SestiaMicrosoftAuthScreen.this.init(SestiaMicrosoftAuthScreen.this.width, SestiaMicrosoftAuthScreen.this.height);
                        }
                    });
                }
            }
        }, pair -> {
            Minecraft mc = SestiaMicrosoftAuthScreen.this.minecraft;
            if (mc == null || pair == null) return;
            mc.execute(() -> {
                SestiaMicrosoftAuthScreen.this.authUrl = pair.length > 0 && pair[0] != null ? pair[0] : "";
                SestiaMicrosoftAuthScreen.this.userCode = pair.length > 1 && pair[1] != null ? pair[1] : "";
                if (isCurrentScreen()) {
                    SestiaMicrosoftAuthScreen.this.init(SestiaMicrosoftAuthScreen.this.width, SestiaMicrosoftAuthScreen.this.height);
                }
            });
        });
    }

    private boolean isCurrentScreen() {
        Minecraft mc = this.minecraft;
        return mc != null && mc.gui != null && mc.gui.screen() == this;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && !this.failed && this.crypt == null && this.needsPasswordPrompt) {
            submitPassword();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        this.cancelled = true;
        closeSessionQuietly();
    }

    @Override
    public void onClose() {
        this.cancelled = true;
        closeSessionQuietly();
        if (this.minecraft != null && this.minecraft.gui != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    private void closeSessionQuietly() {
        if (this.authSession != null) {
            try {
                this.authSession.close();
            } catch (Throwable ignored) {
            }
            this.authSession = null;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.centeredText(this.font, this.title, centerX, centerY - 68, 0xFFFFFFFF);

        if (!this.failed && this.crypt == null && this.needsPasswordPrompt) {
            graphics.centeredText(this.font, Component.translatable("ias.password"), centerX, centerY - 26, 0xFFFFFFFF);
            graphics.centeredText(this.font, Component.translatable("ias.password.tip").withStyle(ChatFormatting.YELLOW), centerX, centerY + 70, 0xFFFFFF55);
            return;
        }

        if (this.statusLabel == null) {
            Component msg = this.statusText != null ? this.statusText : Component.empty();
            this.statusLabel = MultiLineLabel.create(this.font, msg, Math.max(240, Math.min(360, this.width - 40)));
        }
        int lineCount = Math.max(1, this.statusLabel.getLineCount());
        int textY = centerY - (lineCount * 9) / 2 - 16;
        this.statusLabel.visitLines(TextAlignment.CENTER, centerX, textY, 10, graphics.textRenderer());

        if (this.failed) {
            if (this.errorNoteLabel == null) {
                this.errorNoteLabel = MultiLineLabel.create(
                        this.font,
                        Component.translatable("ias.error.note").withStyle(ChatFormatting.AQUA),
                        Math.max(240, Math.min(360, this.width - 40))
                );
            }
            this.errorNoteLabel.visitLines(TextAlignment.CENTER, centerX, centerY + 62, 10, graphics.textRenderer());
        }
    }
}
