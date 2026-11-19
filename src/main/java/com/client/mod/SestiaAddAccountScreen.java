package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Vanilla Add Server-style Add/Edit Account screen replacing IAS's custom AddPopupScreen.
 */
public final class SestiaAddAccountScreen extends Screen {
    private final Screen parent;
    private final Consumer<Object> handler;
    private final boolean editing;
    private final boolean editingMicrosoft;

    private EditBox nameEdit;
    private EditBox passwordEdit;
    private Button typeButton;
    private Button doneButton;
    private boolean microsoftMode = false;
    private String draftName;
    private String draftPassword;

    public SestiaAddAccountScreen(Screen parent, Consumer<Object> handler) {
        this(parent, handler, false, false, "", "");
    }

    public SestiaAddAccountScreen(
            Screen parent,
            Consumer<Object> handler,
            boolean editing,
            boolean editingMicrosoft,
            String initialName,
            String initialPassword
    ) {
        super(Component.translatable(editing ? "ias.edit" : "ias.add"));
        this.parent = parent;
        this.handler = handler;
        this.editing = editing;
        this.editingMicrosoft = editing && editingMicrosoft;
        this.draftName = initialName != null ? initialName : "";
        this.draftPassword = initialPassword != null ? initialPassword : "";
    }

    @Override
    protected void init() {
        int centerX = this.width / 2 - 100;
        int editY = 60;
        int passY = 102;
        int typeY = Math.max(138, this.height / 4 + 88);

        this.nameEdit = new EditBox(this.font, centerX, editY, 200, 20, Component.literal("Username"));
        this.nameEdit.setMaxLength(16);
        this.nameEdit.setValue(this.draftName);
        this.nameEdit.setEditable(!this.microsoftMode && !this.editingMicrosoft);
        this.nameEdit.setResponder(text -> {
            this.draftName = text != null ? text : "";
            updateButtonStates();
        });
        this.addRenderableWidget(this.nameEdit);
        this.setInitialFocus(this.editingMicrosoft ? null : this.nameEdit);

        this.passwordEdit = new EditBox(this.font, centerX, passY, 200, 20, Component.literal("Server Password"));
        this.passwordEdit.setMaxLength(64);
        this.passwordEdit.setHint(Component.literal("Optional (/login & /register)"));
        this.passwordEdit.setValue(this.draftPassword);
        this.passwordEdit.setEditable(!this.microsoftMode);
        this.passwordEdit.setResponder(text -> this.draftPassword = text != null ? text : "");
        this.addRenderableWidget(this.passwordEdit);
        if (this.editingMicrosoft) {
            this.setInitialFocus(this.passwordEdit);
        }

        this.typeButton = Button.builder(typeLabel(), btn -> {
            if (this.editingMicrosoft) {
                return;
            }
            this.microsoftMode = !this.microsoftMode;
            btn.setMessage(typeLabel());
            if (this.nameEdit != null) {
                this.nameEdit.setEditable(!this.microsoftMode);
            }
            if (this.passwordEdit != null) {
                this.passwordEdit.setEditable(!this.microsoftMode);
            }
            updateButtonStates();
        }).bounds(centerX, typeY, 200, 20).build();
        this.typeButton.active = !this.editingMicrosoft;
        this.addRenderableWidget(this.typeButton);

        this.doneButton = Button.builder(CommonComponents.GUI_DONE, btn -> onDone())
                .bounds(centerX, typeY + 28, 200, 20)
                .build();
        this.addRenderableWidget(this.doneButton);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, btn -> onClose())
                .bounds(centerX, typeY + 52, 200, 20)
                .build());

        updateButtonStates();
    }

    private Component typeLabel() {
        if (this.editingMicrosoft) {
            return Component.literal("Account Type: Microsoft (Locked)");
        }
        return Component.literal("Account Type: " + (this.microsoftMode ? "Microsoft" : "Offline"));
    }

    private void updateButtonStates() {
        if (this.doneButton != null) {
            boolean valid = this.microsoftMode || this.editingMicrosoft
                    || (this.nameEdit != null && !this.nameEdit.getValue().trim().isEmpty());
            this.doneButton.active = valid;
        }
    }

    private void onDone() {
        if (this.minecraft == null || this.minecraft.gui == null) {
            return;
        }
        if (this.editingMicrosoft) {
            String password = this.passwordEdit != null ? this.passwordEdit.getValue().trim() : "";
            IasBridge.saveAutoLoginPassword(this.draftName, password);
            this.minecraft.gui.setScreen(this.parent);
            return;
        }
        if (this.microsoftMode) {
            this.minecraft.gui.setScreen(new SestiaMicrosoftCryptScreen(this.parent, this.handler));
            return;
        }

        String name = this.nameEdit != null ? this.nameEdit.getValue().trim() : "";
        if (name.isEmpty()) {
            return;
        }
        String password = this.passwordEdit != null ? this.passwordEdit.getValue().trim() : "";
        IasBridge.saveAutoLoginPassword(name, password);

        Object account = IasBridge.createOfflineAccount(name);
        if (account != null && this.handler != null) {
            this.handler.accept(account);
        }
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.doneButton != null && this.doneButton.active) {
                onDone();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && this.minecraft.gui != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 17, 0xFFFFFFFF);
        int labelColor = this.microsoftMode ? 0xFF6E6E6E : 0xFFA0A0A0;
        graphics.text(this.font, Component.literal("Username"), this.width / 2 - 100, 48, labelColor, false);
        graphics.text(this.font, Component.literal("Server Password (Auto-Login)"), this.width / 2 - 100, 90, labelColor, false);
    }
}
