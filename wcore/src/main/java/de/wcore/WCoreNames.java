package de.wcore;

import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Ermittelt, welcher Anzeigename fuer einen Spieler gilt. */
public final class WCoreNames {

    /** Nicknames, die andere WCore-Nutzer an uns gesendet haben: Name (klein) -> Nickname. */
    public static final Map<String, String> REMOTE = new ConcurrentHashMap<>();

    private WCoreNames() {
    }

    /** @return der Anzeigename oder null, wenn der echte Name bleiben soll. */
    public static String displayFor(String realName) {
        if (realName == null) {
            return null;
        }
        WCoreConfig cfg = WCoreConfig.INSTANCE;
        String key = realName.toLowerCase(Locale.ROOT);

        String renamed = cfg.renames.get(key);
        if (renamed != null) {
            return renamed;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !cfg.nickname.isBlank()
                && key.equals(mc.player.getName().getString().toLowerCase(Locale.ROOT))) {
            return cfg.nickname;
        }

        return REMOTE.get(key);
    }
}
