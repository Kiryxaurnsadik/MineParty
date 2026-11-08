package io.github.kiryxaurnsadik.mineparty.client.ui;

import io.github.kiryxaurnsadik.mineparty.client.ClientConfig;
import io.github.kiryxaurnsadik.mineparty.client.render.PartyHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Drag the party HUD anywhere, scroll to resize, switch how much it shows. */
public class HudEditorScreen extends Screen {
    private static final String[] MODES = {"компактный", "с бронёй", "с названиями"};
    private boolean dragging;
    private double grabX;
    private double grabY;

    public HudEditorScreen() {
        super(Component.literal("HUD"));
    }

    @Override
    protected void init() {
        ClientConfig cfg = ClientConfig.get();
        int y = height - 30;
        addRenderableWidget(Button.builder(modeLabel(), b -> {
            cfg.hudMode = (cfg.hudMode + 1) % MODES.length;
            b.setMessage(modeLabel());
        }).bounds(width / 2 - 154, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Сбросить"), b -> {
            cfg.hudRight = 4;
            cfg.hudTop = 4;
            cfg.hudScale = 1f;
        }).bounds(width / 2 - 2, y, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose())
                .bounds(width / 2 + 72, y, 80, 20).build());
    }

    private Component modeLabel() {
        return Component.literal("Вид: " + MODES[Math.max(0, Math.min(2, ClientConfig.get().hudMode))]);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x40000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        PartyHud.render(g, true);
        int[] b = PartyHud.lastBounds();
        int border = dragging ? 0xFFFFD23F : 0xA0FFFFFF;
        g.fill(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1], border);
        g.fill(b[0] - 1, b[1] + b[3], b[0] + b[2] + 1, b[1] + b[3] + 1, border);
        g.fill(b[0] - 1, b[1], b[0], b[1] + b[3], border);
        g.fill(b[0] + b[2], b[1], b[0] + b[2] + 1, b[1] + b[3], border);
        g.drawCenteredString(font, "Перетащите HUD мышью, колесо — размер ("
                + Math.round(ClientConfig.get().hudScale * 100) + "%)", width / 2, height - 44, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int[] b = PartyHud.lastBounds();
        if (UiUtil.inside(mouseX, mouseY, b[0], b[1], b[2], b[3])) {
            dragging = true;
            grabX = mouseX - b[0];
            grabY = mouseY - b[1];
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            int[] b = PartyHud.lastBounds();
            int x = (int) Math.round(mouseX - grabX);
            int y = (int) Math.round(mouseY - grabY);
            // Snap to the screen edges and the centre line.
            if (x < 8) {
                x = 0;
            }
            if (Math.abs(x + b[2] / 2 - width / 2) < 6) {
                x = width / 2 - b[2] / 2;
            }
            if (y < 8) {
                y = 0;
            }
            x = Math.max(0, Math.min(width - b[2], x));
            y = Math.max(0, Math.min(height - b[3], y));
            ClientConfig cfg = ClientConfig.get();
            cfg.hudRight = width - x - b[2];
            if (cfg.hudRight < 8) {
                cfg.hudRight = 0;
            }
            cfg.hudTop = y;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ClientConfig cfg = ClientConfig.get();
        cfg.hudScale = Math.max(0.5f, Math.min(2f, cfg.hudScale + (scrollY > 0 ? 0.1f : -0.1f)));
        return true;
    }

    @Override
    public void onClose() {
        ClientConfig.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
