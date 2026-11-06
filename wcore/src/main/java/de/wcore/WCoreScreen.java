package de.wcore;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Einstellungsmenue (erreichbar ueber Mod Menu oder /wcore settings). */
public class WCoreScreen extends Screen {

    private final Screen parent;
    private EditBox nameBox;
    private EditBox nickBox;

    public WCoreScreen(Screen parent) {
        super(Component.literal("WCore Einstellungen"));
        this.parent = parent;
    }

    private static Component onOff(String label, boolean value) {
        return Component.literal(label + ": " + (value ? "AN" : "AUS"));
    }

    private Button toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
        return Button.builder(onOff(label, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            WCoreConfig.INSTANCE.save();
            b.setMessage(onOff(label, get.getAsBoolean()));
        }).bounds(x, y, w, 20).build();
    }

    @Override
    protected void init() {
        WCoreConfig cfg = WCoreConfig.INSTANCE;
        int cx = this.width / 2;
        int y = 40;

        // Spieler ausblenden
        nameBox = new EditBox(this.font, cx - 155, y, 150, 20, Component.literal("Spielername"));
        nameBox.setHint(Component.literal("Spielername"));
        nameBox.setMaxLength(16);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("Ausblenden an/aus"), b -> {
            String n = nameBox.getValue().trim();
            if (!n.isEmpty()) {
                WCoreClient.toggleIgnore(n);
                rebuildWidgets();
            }
        }).bounds(cx + 5, y, 150, 20).build());

        // Schalter
        y += 28;
        addRenderableWidget(toggle(cx - 155, y, 150, "Chat ausblenden",
                () -> cfg.hideChat, v -> cfg.hideChat = v));
        addRenderableWidget(toggle(cx + 5, y, 150, "Voicechat stumm",
                () -> cfg.muteVoice, v -> cfg.muteVoice = v));
        y += 24;
        addRenderableWidget(toggle(cx - 155, y, 310, "Nickname an andere WCore-Nutzer senden",
                () -> cfg.syncNickname, v -> cfg.syncNickname = v));

        // Nickname
        y += 28;
        nickBox = new EditBox(this.font, cx - 155, y, 150, 20, Component.literal("Nickname"));
        nickBox.setHint(Component.literal("Dein Nickname"));
        nickBox.setMaxLength(16);
        nickBox.setValue(cfg.nickname);
        addRenderableWidget(nickBox);
        addRenderableWidget(Button.builder(Component.literal("Nickname setzen"), b ->
                WCoreClient.applyNickname(nickBox.getValue())
        ).bounds(cx + 5, y, 150, 20).build());

        // Liste der ausgeblendeten Spieler (Klick entfernt den Eintrag)
        y += 40;
        List<String> names = new ArrayList<>(cfg.ignored);
        for (int i = 0; i < names.size() && i < 8; i++) {
            String name = names.get(i);
            int bx = (i % 2 == 0) ? cx - 155 : cx + 5;
            int by = y + (i / 2) * 22;
            addRenderableWidget(Button.builder(Component.literal("✕ " + name), b -> {
                WCoreClient.toggleIgnore(name);
                rebuildWidgets();
            }).bounds(bx, by, 150, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(cx - 100, this.height - 28, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, cx, 15, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.literal("Ausgeblendet (Klick = wieder einblenden):"), cx, 40 + 28 + 24 + 28 + 40 - 14, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        WCoreConfig.INSTANCE.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
