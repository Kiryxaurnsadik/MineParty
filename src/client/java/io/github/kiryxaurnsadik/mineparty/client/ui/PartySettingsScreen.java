package io.github.kiryxaurnsadik.mineparty.client.ui;

import java.util.Locale;

import io.github.kiryxaurnsadik.mineparty.network.MPNetwork;
import io.github.kiryxaurnsadik.mineparty.party.Limits;
import io.github.kiryxaurnsadik.mineparty.party.MarkerSettings;
import io.github.kiryxaurnsadik.mineparty.party.PartyData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/** The gear: appearance of the party and its markers. Only the leader can open it. */
public class PartySettingsScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 236;
    private static final int SWATCH = 11;

    private final Screen parent;
    private final PartyData party;

    private String name;
    private int color;
    private boolean bold;
    private String icon;
    private String symbol;
    private int markerColor;
    private boolean throughWalls;
    private int distance;
    private boolean showName;

    private EditBox nameBox;
    private EditBox colorBox;
    private EditBox iconBox;
    private EditBox symbolBox;
    private int left;
    private int top;

    public PartySettingsScreen(Screen parent, PartyData party) {
        super(Component.literal("Настройки отряда"));
        this.parent = parent;
        this.party = party;
        this.name = party.name();
        this.color = party.color();
        this.bold = party.bold();
        this.icon = party.icon();
        MarkerSettings m = party.markers();
        this.symbol = m.symbol();
        this.markerColor = m.color();
        this.throughWalls = m.throughWalls();
        this.distance = m.maxDistance();
        this.showName = m.showName();
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static Integer parseHex(String text) {
        String s = text.startsWith("#") ? text.substring(1) : text;
        if (s.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    protected void init() {
        left = width / 2 - PANEL_W / 2;
        top = Math.max(10, height / 2 - PANEL_H / 2);
        int x = left + 70;

        nameBox = new EditBox(font, x, top + 24, 150, 16, Component.literal("name"));
        nameBox.setMaxLength(Limits.MAX_NAME_LENGTH);
        nameBox.setValue(name);
        nameBox.setResponder(v -> name = v);
        addRenderableWidget(nameBox);

        colorBox = new EditBox(font, left + PANEL_W - 70, top + 44, 60, 16, Component.literal("color"));
        colorBox.setMaxLength(7);
        colorBox.setValue(hex(color));
        colorBox.setResponder(v -> {
            Integer parsed = parseHex(v);
            if (parsed != null) {
                color = parsed;
            }
        });
        addRenderableWidget(colorBox);

        addRenderableWidget(Button.builder(boldLabel(), b -> {
            bold = !bold;
            b.setMessage(boldLabel());
        }).bounds(x + 156, top + 23, 64, 18).build());

        iconBox = new EditBox(font, x, top + 66, 24, 16, Component.literal("icon"));
        iconBox.setMaxLength(2);
        iconBox.setValue(icon);
        iconBox.setResponder(v -> {
            if (Limits.isValidIcon(v)) {
                icon = v;
            }
        });
        addRenderableWidget(iconBox);

        // Markers
        int my = top + 118;
        symbolBox = new EditBox(font, x, my, 24, 16, Component.literal("symbol"));
        symbolBox.setMaxLength(2);
        symbolBox.setValue(symbol);
        symbolBox.setResponder(v -> {
            if (Limits.isValidIcon(v)) {
                symbol = v;
            }
        });
        addRenderableWidget(symbolBox);

        addRenderableWidget(Button.builder(markerColorLabel(), b -> {
            markerColor = markerColor == -1 ? 0xFFFFFF : -1;
            b.setMessage(markerColorLabel());
        }).bounds(x, my + 22, 150, 18).build());

        addRenderableWidget(Button.builder(wallsLabel(), b -> {
            throughWalls = !throughWalls;
            b.setMessage(wallsLabel());
        }).bounds(x, my + 44, 150, 18).build());

        addRenderableWidget(Button.builder(Component.literal("-"), b -> distance = Math.max(Limits.MIN_MARKER_DISTANCE, distance - (distance > 1000 ? 500 : 100)))
                .bounds(x, my + 66, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> distance = Math.min(Limits.MAX_MARKER_DISTANCE, distance + (distance >= 1000 ? 500 : 100)))
                .bounds(x + 90, my + 66, 20, 18).build());

        addRenderableWidget(Button.builder(nameLabel(), b -> {
            showName = !showName;
            b.setMessage(nameLabel());
        }).bounds(x + 116, my + 66, 104, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> save())
                .bounds(left + PANEL_W / 2 - 104, top + PANEL_H - 24, 100, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> onClose())
                .bounds(left + PANEL_W / 2 + 4, top + PANEL_H - 24, 100, 18).build());
    }

    private Component boldLabel() {
        return Component.literal("Жирный: " + (bold ? "да" : "нет"));
    }

    private Component markerColorLabel() {
        return Component.literal(markerColor == -1 ? "Цвет: как у отряда" : "\u0426\u0432\u0435\u0442: \u0441\u0432\u043e\u0439 (\u041f\u041a\u041c \u043f\u043e \u043f\u0430\u043b\u0438\u0442\u0440\u0435)");
    }

    private Component wallsLabel() {
        return Component.literal("Сквозь стены: " + (throughWalls ? "да" : "нет"));
    }

    private Component nameLabel() {
        return Component.literal("Ник: " + (showName ? "да" : "нет"));
    }

    private void save() {
        ClientPlayNetworking.send(new MPNetwork.UpdateStyle(party.id(), Limits.cleanName(name), color, bold, icon));
        ClientPlayNetworking.send(new MPNetwork.UpdateMarkers(party.id(),
                new MarkerSettings(symbol, markerColor, throughWalls, distance, showName)));
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        UiUtil.panel(g, left, top, PANEL_W, PANEL_H, color);
        g.fill(left + 1, top + 1, left + PANEL_W - 1, top + 16, UiUtil.HEADER_BG);
        g.drawCenteredString(font, title, left + PANEL_W / 2, top + 5, 0xFFFFFFFF);

        int lx = left + 8;
        g.drawString(font, "Название", lx, top + 28, 0xFFCCCCCC, false);
        g.drawString(font, "Цвет", lx, top + 48, 0xFFCCCCCC, false);
        g.drawString(font, "Иконка", lx, top + 70, 0xFFCCCCCC, false);

        // Palette: left click = party colour, right click = marker colour
        int sx = left + 70;
        for (int i = 0; i < UiUtil.PALETTE.length; i++) {
            int x = sx + (i % 8) * (SWATCH + 2);
            int y = top + 44 + (i / 8) * (SWATCH - 3);
            g.fill(x, y, x + SWATCH, y + SWATCH - 4, UiUtil.argb(UiUtil.PALETTE[i]));
            if (UiUtil.PALETTE[i] == color) {
                g.fill(x, y + SWATCH - 4, x + SWATCH, y + SWATCH - 3, 0xFFFFFFFF);
            }
        }
        // Icon presets
        int ix = left + 100;
        for (int i = 0; i < UiUtil.PARTY_ICONS.length; i++) {
            int x = ix + i * 14;
            boolean hover = UiUtil.inside(mouseX, mouseY, x, top + 66, 13, 16);
            g.fill(x, top + 66, x + 13, top + 82, hover ? 0x60FFFFFF : 0x30FFFFFF);
            g.drawCenteredString(font, UiUtil.PARTY_ICONS[i], x + 7, top + 70, UiUtil.argb(color));
        }
        // Preview
        TextColor tc = TextColor.fromRgb(color);
        Component preview = Component.literal(icon + " " + name).withStyle(s -> s.withColor(tc).withBold(bold));
        g.drawString(font, "Предпросмотр:", lx, top + 92, 0xFF999999, false);
        g.drawString(font, preview, lx + 90, top + 92, 0xFFFFFFFF, true);

        // Markers section
        int my = top + 118;
        g.fill(left + 6, my - 10, left + PANEL_W - 6, my - 9, 0x40FFFFFF);
        g.drawString(font, "Метки (только лидер)", lx, my - 7, 0xFFFFD23F, false);
        g.drawString(font, "Символ", lx, my + 4, 0xFFCCCCCC, false);
        int px = left + 100;
        for (int i = 0; i < UiUtil.MARKER_SYMBOLS.length; i++) {
            int x = px + i * 14;
            boolean hover = UiUtil.inside(mouseX, mouseY, x, my, 13, 16);
            g.fill(x, my, x + 13, my + 16, hover ? 0x60FFFFFF : 0x30FFFFFF);
            int mc = markerColor == -1 ? color : markerColor;
            g.drawCenteredString(font, UiUtil.MARKER_SYMBOLS[i], x + 7, my + 4, UiUtil.argb(mc));
        }
        g.drawString(font, "Дистанция", lx, my + 71, 0xFFCCCCCC, false);
        g.drawCenteredString(font, distance + " бл.", left + 70 + 55, my + 71, 0xFFFFFFFF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int sx = left + 70;
        for (int i = 0; i < UiUtil.PALETTE.length; i++) {
            int x = sx + (i % 8) * (SWATCH + 2);
            int y = top + 44 + (i / 8) * (SWATCH - 3);
            if (UiUtil.inside(mouseX, mouseY, x, y, SWATCH, SWATCH - 4)) {
                if (button == 1) {
                    markerColor = UiUtil.PALETTE[i];
                    rebuildWidgets();
                } else {
                    color = UiUtil.PALETTE[i];
                    colorBox.setValue(hex(color));
                }
                return true;
            }
        }
        int ix = left + 100;
        for (int i = 0; i < UiUtil.PARTY_ICONS.length; i++) {
            if (UiUtil.inside(mouseX, mouseY, ix + i * 14, top + 66, 13, 16)) {
                icon = UiUtil.PARTY_ICONS[i];
                iconBox.setValue(icon);
                return true;
            }
        }
        int my = top + 118;
        for (int i = 0; i < UiUtil.MARKER_SYMBOLS.length; i++) {
            if (UiUtil.inside(mouseX, mouseY, left + 100 + i * 14, my, 13, 16)) {
                symbol = UiUtil.MARKER_SYMBOLS[i];
                symbolBox.setValue(symbol);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
