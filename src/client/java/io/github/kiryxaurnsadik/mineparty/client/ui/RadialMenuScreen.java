package io.github.kiryxaurnsadik.mineparty.client.ui;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.kiryxaurnsadik.mineparty.client.MinePartyClient;
import io.github.kiryxaurnsadik.mineparty.party.PingType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Hold the key, point with the mouse, release to choose. */
public class RadialMenuScreen extends Screen {
    private static final int RADIUS = 70;
    private static final String[] LABELS = {
            "⚠ Внимание",
            "! Опасность",
            "↓ Сюда",
            "+ Пригласить",
            "✉ Режим приглашения",
            "⚔ Отряд"
    };
    private static final int[] COLORS = {PingType.WARNING.color, PingType.DANGER.color, PingType.HERE.color, 0x55FFFF, 0xFFFFFF, 0xFFFFFF};

    private final InputConstants.Key key;
    private int hovered = -1;

    public RadialMenuScreen(InputConstants.Key key) {
        super(Component.literal("MineParty"));
        this.key = key;
    }

    private boolean keyHeld() {
        long window = minecraft.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }

    @Override
    public void tick() {
        if (!keyHeld()) {
            choose();
        }
    }

    private void choose() {
        int choice = hovered;
        minecraft.setScreen(null);
        switch (choice) {
            case 0 -> MinePartyClient.sendPing(PingType.WARNING);
            case 1 -> MinePartyClient.sendPing(PingType.DANGER);
            case 2 -> MinePartyClient.sendPing(PingType.HERE);
            case 3 -> MinePartyClient.inviteLookedAt();
            case 4 -> MinePartyClient.toggleInviteMode();
            case 5 -> minecraft.setScreen(new PartyScreen());
            default -> {
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        int n = LABELS.length;
        if (dx * dx + dy * dy > 15 * 15) {
            double angle = Math.atan2(dy, dx) + Math.PI / 2;
            if (angle < 0) {
                angle += Math.PI * 2;
            }
            hovered = (int) Math.round(angle / (Math.PI * 2 / n)) % n;
        } else {
            hovered = -1;
        }
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xC0FFFFFF);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n - Math.PI / 2;
            int x = cx + (int) (Math.cos(a) * RADIUS);
            int y = cy + (int) (Math.sin(a) * RADIUS);
            int w = font.width(LABELS[i]) + 10;
            boolean on = i == hovered;
            g.fill(x - w / 2, y - 8, x + w / 2, y + 8, on ? UiUtil.argb(COLORS[i], 0.55f) : 0x90000000);
            g.drawCenteredString(font, LABELS[i], x, y - 4, on ? 0xFFFFFFFF : UiUtil.argb(COLORS[i]));
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        choose();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
