package io.github.kiryxaurnsadik.mineparty.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.kiryxaurnsadik.mineparty.MineParty;
import net.fabricmc.loader.api.FabricLoader;

/** Personal client settings (HUD position and look). Party settings live on the server. */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mineparty-client.json");
    private static ClientConfig instance = new ClientConfig();

    /** Distance from the right edge of the screen to the right side of the HUD, in GUI pixels. */
    public int hudRight = 4;
    public int hudTop = 4;
    public float hudScale = 1.0f;
    /** 0 = compact, 1 = armor icons, 2 = armor icons with item names. */
    public int hudMode = 1;
    public float hudBackground = 0.45f;
    public boolean pingAllParties = false;

    public static ClientConfig get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | RuntimeException e) {
                MineParty.LOGGER.warn("Could not read {}", FILE, e);
            }
        }
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            MineParty.LOGGER.warn("Could not save {}", FILE, e);
        }
    }
}
