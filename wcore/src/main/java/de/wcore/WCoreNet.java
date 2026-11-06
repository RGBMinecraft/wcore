package de.wcore;

import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nickname-Austausch zwischen WCore-Nutzern ueber unsichtbare Chatnachrichten.
 *
 * Eine Nachricht besteht nur aus unsichtbaren Zeichen (Nullbreiten-Leerzeichen) und enthaelt
 * ein Typ-Zeichen und den Nickname:
 *   N = "mein Nickname ist ..."      R = "ich habe keinen Nickname mehr"
 *   Q = "ich bin neu hier (mein Nickname ist ...), wer hat einen Nickname?"
 * WCore-Nutzer blenden diese Nachrichten aus. Andere sehen eine leere Zeile.
 */
public final class WCoreNet {

    private static final String MARK = "⁣⁣";
    private static final char ZERO = '​';
    private static final char ONE = '‌';
    private static final int MAX_NICK_BYTES = 24;
    private static final Pattern SENDER_GUESS = Pattern.compile("([A-Za-z0-9_]{3,16})");
    private static final Random RANDOM = new Random();

    private static int joinDelay = -1;
    private static int replyDelay = -1;
    private static long lastReply = 0;

    private WCoreNet() {
    }

    public static boolean nickFitsNetwork(String nick) {
        return nick.getBytes(StandardCharsets.UTF_8).length <= MAX_NICK_BYTES;
    }

    private static String encode(char type, String nick) {
        byte[] data = (type + nick).getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(MARK);
        for (byte b : data) {
            for (int i = 7; i >= 0; i--) {
                sb.append(((b >> i) & 1) == 1 ? ONE : ZERO);
            }
        }
        return sb.toString();
    }

    public static void send(char type, String nick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || !nickFitsNetwork(nick)) {
            return;
        }
        mc.getConnection().sendChat(encode(type, nick));
    }

    public static void onJoin() {
        if (WCoreConfig.INSTANCE.syncNickname) {
            joinDelay = 80; // ca. 4 Sekunden nach dem Beitritt
        }
    }

    public static void tick() {
        WCoreConfig cfg = WCoreConfig.INSTANCE;
        if (joinDelay > 0 && --joinDelay == 0) {
            if (cfg.syncNickname) {
                send('Q', cfg.nickname);
            }
        }
        if (replyDelay > 0 && --replyDelay == 0) {
            if (cfg.syncNickname && !cfg.nickname.isBlank()) {
                lastReply = System.currentTimeMillis();
                send('N', cfg.nickname);
            }
        }
    }

    /**
     * Prueft, ob der Text eine WCore-Nachricht ist, und verarbeitet sie.
     *
     * @return true, wenn es eine WCore-Nachricht war (dann ausblenden)
     */
    public static boolean handle(String text, String senderName) {
        int start = text.indexOf(MARK);
        if (start < 0) {
            return false;
        }

        StringBuilder bits = new StringBuilder();
        for (int k = start + MARK.length(); k < text.length(); k++) {
            char c = text.charAt(k);
            if (c == ZERO || c == ONE) {
                bits.append(c == ONE ? '1' : '0');
            } else {
                break;
            }
        }
        if (bits.length() == 0 || bits.length() % 8 != 0) {
            return true;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < bits.length(); i += 8) {
            out.write(Integer.parseInt(bits.substring(i, i + 8), 2));
        }
        String payload = out.toString(StandardCharsets.UTF_8);
        if (payload.isEmpty()) {
            return true;
        }

        char type = payload.charAt(0);
        String nick = clean(payload.substring(1));

        if (senderName == null) {
            Matcher m = SENDER_GUESS.matcher(text.substring(0, start));
            senderName = m.find() ? m.group(1) : null;
        }
        if (senderName == null) {
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null
                && senderName.equalsIgnoreCase(mc.player.getName().getString());
        String key = senderName.toLowerCase(Locale.ROOT);

        if (!self) {
            switch (type) {
                case 'N', 'Q' -> {
                    if (!nick.isEmpty()) {
                        WCoreNames.REMOTE.put(key, nick);
                    }
                }
                case 'R' -> WCoreNames.REMOTE.remove(key);
                default -> {
                }
            }

            WCoreConfig cfg = WCoreConfig.INSTANCE;
            if (type == 'Q' && cfg.syncNickname && !cfg.nickname.isBlank()
                    && System.currentTimeMillis() - lastReply > 20_000) {
                replyDelay = 40 + RANDOM.nextInt(100); // 2-7 Sekunden warten
            }
        }
        return true;
    }

    public static String clean(String nick) {
        StringBuilder sb = new StringBuilder();
        for (char c : nick.toCharArray()) {
            if (c >= ' ' && c != 127 && c != '§') {
                sb.append(c);
            }
        }
        String result = sb.toString().trim();
        return result.length() > 16 ? result.substring(0, 16) : result;
    }
}
