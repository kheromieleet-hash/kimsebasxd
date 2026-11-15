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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Vanilla Add Server-style Add Account screen replacing IAS's custom AddPopupScreen.
 */
public final class SestiaAddAccountScreen extends Screen {
    private final Screen parent;
    private final Consumer<Object> handler;

    private EditBox nameEdit;
    private EditBox passwordEdit;
    private Button typeButton;
    private Button doneButton;
    private boolean microsoftMode = false;
    private String draftName = "";
    private String draftPassword = "";

    public SestiaAddAccountScreen(Screen parent, Consumer<Object> handler) {
        super(Component.literal("Add Account"));
        this.parent = parent;
        this.handler = handler;
    }

    @SuppressWarnings("unchecked")
    public static boolean tryReplaceAddPopup(Screen screen) {
        if (screen == null || !"ru.vidtu.ias.screen.AddPopupScreen".equals(screen.getClass().getName())) {
            return false;
        }
        try {
            Field parentField = screen.getClass().getDeclaredField("parent");
            parentField.setAccessible(true);
            Screen parent = (Screen) parentField.get(screen);

            Field handlerField = screen.getClass().getDeclaredField("handler");
            handlerField.setAccessible(true);
            Consumer<Object> handler = (Consumer<Object>) handlerField.get(screen);

            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gui != null) {
                mc.gui.setScreen(new SestiaAddAccountScreen(parent, handler));
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
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
        this.nameEdit.setEditable(!this.microsoftMode);
        this.nameEdit.setResponder(text -> {
            this.draftName = text != null ? text : "";
            updateButtonStates();
        });
        this.addRenderableWidget(this.nameEdit);
        this.setInitialFocus(this.nameEdit);

        this.passwordEdit = new EditBox(this.font, centerX, passY, 200, 20, Component.literal("Server Password"));
        this.passwordEdit.setMaxLength(64);
        this.passwordEdit.setHint(Component.literal("Optional (/login & /register)"));
        this.passwordEdit.setValue(this.draftPassword);
        this.passwordEdit.setEditable(!this.microsoftMode);
        this.passwordEdit.setResponder(text -> this.draftPassword = text != null ? text : "");
        this.addRenderableWidget(this.passwordEdit);

        this.typeButton = Button.builder(typeLabel(), btn -> {
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
        return Component.literal("Account Type: " + (this.microsoftMode ? "Microsoft" : "Offline"));
    }

    private void updateButtonStates() {
        if (this.doneButton != null) {
            boolean valid = this.microsoftMode || (this.nameEdit != null && !this.nameEdit.getValue().trim().isEmpty());
            this.doneButton.active = valid;
        }
    }

    private void onDone() {
        if (this.minecraft == null || this.minecraft.gui == null) {
            return;
        }
        if (this.microsoftMode) {
            try {
                Class<?> msCls = Class.forName("ru.vidtu.ias.screen.MicrosoftCryptPopupScreen");
                Constructor<?> ctor = msCls.getDeclaredConstructor(Screen.class, Consumer.class);
                ctor.setAccessible(true);
                Screen msScreen = (Screen) ctor.newInstance(this.parent, this.handler);
                this.minecraft.gui.setScreen(msScreen);
            } catch (Throwable t) {
                onClose();
            }
            return;
        }

        String name = this.nameEdit != null ? this.nameEdit.getValue().trim() : "";
        if (name.isEmpty()) {
            return;
        }
        String password = this.passwordEdit != null ? this.passwordEdit.getValue().trim() : "";
        ModConfig config = ModConfig.getInstance();
        String key = name.toLowerCase(Locale.ROOT);
        if (!password.isEmpty()) {
            config.accountPasswords.put(key, password);
        } else {
            config.accountPasswords.remove(key);
        }
        config.save();

        try {
            Class<?> offlineCls = Class.forName("ru.vidtu.ias.account.OfflineAccount");
            UUID uuid = (UUID) offlineCls.getMethod("uuid", String.class).invoke(null, name);
            Object account = offlineCls.getConstructor(String.class, UUID.class).newInstance(name, uuid);
            if (this.handler != null) {
                this.handler.accept(account);
            }
        } catch (Throwable ignored) {
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
