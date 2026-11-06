package de.wcore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Einstellungen, gespeichert in config/wcore.json. */
public class WCoreConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final WCoreConfig INSTANCE = load();

    /** Ausgeblendete Spieler (kleingeschrieben). */
    public Set<String> ignored = new LinkedHashSet<>();
    /** Lokale Umbenennungen anderer Spieler: echter Name (klein) -> Anzeigename. */
    public Map<String, String> renames = new LinkedHashMap<>();
    /** Eigener Nickname (leer = keiner). */
    public String nickname = "";
    /** Chat von ausgeblendeten Spielern ausblenden. */
    public boolean hideChat = true;
    /** Ausgeblendete Spieler im Simple Voice Chat stummschalten. */
    public boolean muteVoice = true;
    /** Nickname per unsichtbarer Chatnachricht an andere WCore-Nutzer senden. */
    public boolean syncNickname = false;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("wcore.json");
    }

    private static WCoreConfig load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                try (Reader reader = Files.newBufferedReader(p)) {
                    WCoreConfig cfg = GSON.fromJson(reader, WCoreConfig.class);
                    if (cfg != null) {
                        cfg.repair();
                        return cfg;
                    }
                }
            }
        } catch (Exception e) {
            // kaputte Datei: mit Standardwerten weitermachen
        }
        return new WCoreConfig();
    }

    private void repair() {
        if (ignored == null) ignored = new LinkedHashSet<>();
        if (renames == null) renames = new LinkedHashMap<>();
        if (nickname == null) nickname = "";
    }

    public void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            try (Writer writer = Files.newBufferedWriter(p)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            // Speichern ist nicht kritisch
        }
    }
}
