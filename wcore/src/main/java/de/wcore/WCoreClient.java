package de.wcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.wcore.mixin.MinecraftAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * WCore - rein clientseitig.
 *   /wcore ignore <spieler>              Spieler ausblenden (inkl. Chat und Voicechat) / wieder einblenden
 *   /wcore skinchange <spieler> <skin>   Skin von <spieler> durch den Skin von <skin> ersetzen
 *   /wcore skinchange <spieler>          Skin zuruecksetzen
 *   /wcore nickname [name]               eigenen Anzeigenamen aendern / zuruecksetzen
 *   /wcore rename <spieler> [name]       Anzeigenamen eines anderen Spielers aendern / zuruecksetzen
 *   /wcore settings                      Einstellungen oeffnen
 *   /wcore list                          aktive Einstellungen anzeigen
 */
public class WCoreClient implements ClientModInitializer {

    /** Name (kleingeschrieben) -> Ersatz-Skin. */
    public static final Map<String, PlayerSkin> SKINS = new ConcurrentHashMap<>();

    /** UUIDs der ausgeblendeten Spieler, die gerade online sind (fuer den Voicechat). */
    private static final Set<UUID> MUTED_IDS = ConcurrentHashMap.newKeySet();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private static volatile boolean openSettings = false;
    private static int tickCounter = 0;

    private static final SuggestionProvider<FabricClientCommandSource> ONLINE_PLAYERS = (ctx, builder) -> {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(
                connection.getOnlinePlayers().stream().map(p -> p.getProfile().name()),
                builder);
    };

    // ------------------------------------------------------------------ Start

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("wcore")
                        .then(literal("ignore")
                                .then(argument("player", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYERS)
                                        .executes(ctx -> ignoreCommand(ctx,
                                                StringArgumentType.getString(ctx, "player")))))
                        .then(literal("skinchange")
                                .then(argument("player", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYERS)
                                        .executes(ctx -> resetSkin(ctx,
                                                StringArgumentType.getString(ctx, "player")))
                                        .then(argument("skin", StringArgumentType.word())
                                                .executes(ctx -> changeSkin(ctx,
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "skin"))))))
                        .then(literal("nickname")
                                .executes(ctx -> nicknameCommand(ctx, ""))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> nicknameCommand(ctx,
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(literal("rename")
                                .then(argument("player", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYERS)
                                        .executes(ctx -> renameCommand(ctx,
                                                StringArgumentType.getString(ctx, "player"), ""))
                                        .then(argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> renameCommand(ctx,
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "name"))))))
                        .then(literal("settings")
                                .executes(ctx -> {
                                    openSettings = true;
                                    return 1;
                                }))
                        .then(literal("list")
                                .executes(WCoreClient::list))));

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, timestamp) ->
                allowMessage(message, sender == null ? null : sender.name()));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
                allowMessage(message, null));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            WCoreNames.REMOTE.clear();
            WCoreNet.onJoin();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WCoreNet.tick();

            if (openSettings && client.screen == null) {
                openSettings = false;
                client.setScreen(new WCoreScreen(null));
            }

            if (++tickCounter >= 10) {
                tickCounter = 0;
                refreshMutedIds(client);
            }
        });
    }

    // ------------------------------------------------------------- Ignorieren

    public static boolean isIgnored(String name) {
        return name != null && WCoreConfig.INSTANCE.ignored.contains(name.toLowerCase(Locale.ROOT));
    }

    /** @return true, wenn der Spieler jetzt ausgeblendet ist. */
    public static boolean toggleIgnore(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        boolean nowIgnored;
        if (WCoreConfig.INSTANCE.ignored.remove(key)) {
            nowIgnored = false;
        } else {
            WCoreConfig.INSTANCE.ignored.add(key);
            nowIgnored = true;
        }
        WCoreConfig.INSTANCE.save();
        refreshMutedIds(Minecraft.getInstance());
        return nowIgnored;
    }

    private static int ignoreCommand(CommandContext<FabricClientCommandSource> ctx, String name) {
        if (toggleIgnore(name)) {
            ctx.getSource().sendFeedback(Component.literal(
                    "[WCore] " + name + " wird jetzt ausgeblendet (auch Chat und Voicechat)."));
        } else {
            ctx.getSource().sendFeedback(Component.literal("[WCore] " + name + " ist wieder sichtbar."));
        }
        return 1;
    }

    private static void refreshMutedIds(Minecraft mc) {
        MUTED_IDS.clear();
        var connection = mc.getConnection();
        if (connection == null || WCoreConfig.INSTANCE.ignored.isEmpty()) {
            return;
        }
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            if (isIgnored(info.getProfile().name())) {
                MUTED_IDS.add(info.getProfile().id());
            }
        }
    }

    /** Wird vom Voicechat-Plugin aufgerufen (anderer Thread). */
    public static boolean shouldMute(UUID id) {
        return id != null && WCoreConfig.INSTANCE.muteVoice && MUTED_IDS.contains(id);
    }

    // ------------------------------------------------------------------- Chat

    private static boolean allowMessage(Component message, String senderName) {
        String text = message.getString();

        if (WCoreNet.handle(text, senderName)) {
            return false;
        }

        WCoreConfig cfg = WCoreConfig.INSTANCE;
        if (!cfg.hideChat || cfg.ignored.isEmpty()) {
            return true;
        }
        if (senderName != null && isIgnored(senderName)) {
            return false;
        }
        for (String name : cfg.ignored) {
            // Typische Chatformate: "<Name> text", "Name: text", "[Rang] Name » text"
            Pattern p = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name)
                            + "(?![A-Za-z0-9_])\\s*(?:\\[[^\\]]*\\]\\s*)?[:»>›]",
                    Pattern.CASE_INSENSITIVE);
            if (p.matcher(text).find()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- Nicknames

    public static void applyNickname(String raw) {
        WCoreConfig cfg = WCoreConfig.INSTANCE;
        cfg.nickname = WCoreNet.clean(raw);
        if (!cfg.nickname.isBlank()) {
            cfg.syncNickname = true;
        }
        cfg.save();
        if (cfg.syncNickname) {
            if (cfg.nickname.isBlank()) {
                WCoreNet.send('R', "");
            } else {
                WCoreNet.send('N', cfg.nickname);
            }
        }
    }

    private static int nicknameCommand(CommandContext<FabricClientCommandSource> ctx, String name) {
        applyNickname(name);
        WCoreConfig cfg = WCoreConfig.INSTANCE;
        if (cfg.nickname.isBlank()) {
            ctx.getSource().sendFeedback(Component.literal("[WCore] Nickname zurueckgesetzt."));
        } else {
            ctx.getSource().sendFeedback(Component.literal("[WCore] Dein Nickname ist jetzt: " + cfg.nickname));
            if (!WCoreNet.nickFitsNetwork(cfg.nickname)) {
                ctx.getSource().sendFeedback(Component.literal(
                        "[WCore] Der Nickname ist zu lang, um an andere WCore-Nutzer gesendet zu werden."));
            } else {
                ctx.getSource().sendFeedback(Component.literal(
                        "[WCore] Andere WCore-Nutzer sehen ihn ueber unsichtbare Chatnachrichten. "
                                + "Das kannst du unter /wcore settings ausschalten."));
            }
        }
        return 1;
    }

    private static int renameCommand(CommandContext<FabricClientCommandSource> ctx, String player, String name) {
        WCoreConfig cfg = WCoreConfig.INSTANCE;
        String key = player.toLowerCase(Locale.ROOT);
        String clean = WCoreNet.clean(name);
        if (clean.isBlank()) {
            cfg.renames.remove(key);
            ctx.getSource().sendFeedback(Component.literal("[WCore] Name von " + player + " zurueckgesetzt."));
        } else {
            cfg.renames.put(key, clean);
            ctx.getSource().sendFeedback(Component.literal(
                    "[WCore] " + player + " heisst fuer dich jetzt: " + clean));
        }
        cfg.save();
        return 1;
    }

    // -------------------------------------------------------------------- Skins

    private static int resetSkin(CommandContext<FabricClientCommandSource> ctx, String name) {
        if (SKINS.remove(name.toLowerCase(Locale.ROOT)) != null) {
            ctx.getSource().sendFeedback(Component.literal("[WCore] Skin von " + name + " zurueckgesetzt."));
        } else {
            ctx.getSource().sendFeedback(Component.literal("[WCore] " + name + " hat keinen ersetzten Skin."));
        }
        return 1;
    }

    /** Holt UUID bei Mojang und danach das vollstaendige Profil (mit Skin-Daten). */
    private static CompletableFuture<Optional<GameProfile>> lookupProfile(Minecraft mc, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(
                                "https://api.mojang.com/minecraft/profile/lookup/name/"
                                        + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return Optional.<GameProfile>empty();
                }
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                UUID uuid = UUID.fromString(json.get("id").getAsString().replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5"));

                ProfileResult result = ((MinecraftAccessor) mc).wcore$getServices()
                        .sessionService().fetchProfile(uuid, true);
                if (result == null || result.profile() == null) {
                    return Optional.<GameProfile>empty();
                }
                return Optional.of(result.profile());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static int changeSkin(CommandContext<FabricClientCommandSource> ctx, String target, String skinOwner) {
        FabricClientCommandSource source = ctx.getSource();
        Minecraft mc = Minecraft.getInstance();
        source.sendFeedback(Component.literal("[WCore] Lade Skin von " + skinOwner + " ..."));

        lookupProfile(mc, skinOwner)
                .thenCompose(profile -> profile.isPresent()
                        ? ((MinecraftAccessor) mc).wcore$getSkinManager().get(profile.get())
                        : CompletableFuture.completedFuture(Optional.<PlayerSkin>empty()))
                .thenAccept(skin -> mc.execute(() -> {
                    if (skin.isPresent()) {
                        SKINS.put(target.toLowerCase(Locale.ROOT), skin.get());
                        source.sendFeedback(Component.literal(
                                "[WCore] " + target + " hat jetzt den Skin von " + skinOwner + "."));
                    } else {
                        source.sendError(Component.literal(
                                "[WCore] Spieler '" + skinOwner + "' oder dessen Skin wurde nicht gefunden."));
                    }
                }))
                .exceptionally(t -> {
                    mc.execute(() -> source.sendError(
                            Component.literal("[WCore] Fehler beim Laden des Skins: " + t.getMessage())));
                    return null;
                });
        return 1;
    }

    // --------------------------------------------------------------------- Liste

    private static int list(CommandContext<FabricClientCommandSource> ctx) {
        WCoreConfig cfg = WCoreConfig.INSTANCE;
        ctx.getSource().sendFeedback(Component.literal("[WCore] Ausgeblendet: "
                + (cfg.ignored.isEmpty() ? "-" : String.join(", ", cfg.ignored))));
        ctx.getSource().sendFeedback(Component.literal("[WCore] Skin ersetzt: "
                + (SKINS.isEmpty() ? "-" : String.join(", ", SKINS.keySet()))));
        ctx.getSource().sendFeedback(Component.literal("[WCore] Umbenannt: "
                + (cfg.renames.isEmpty() ? "-" : String.join(", ", cfg.renames.keySet()))));
        return 1;
    }
}
