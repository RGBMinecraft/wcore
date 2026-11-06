package de.wcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.wcore.mixin.MinecraftAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
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

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * WCore - rein clientseitig.
 *   /wcore ignore <spieler>              -> Spieler ausblenden / wieder einblenden (Toggle)
 *   /wcore skinchange <spieler> <skin>   -> Skin von <spieler> durch den Skin von <skin> ersetzen
 *   /wcore skinchange <spieler>          -> Skin wieder zuruecksetzen
 *   /wcore list                          -> aktive Einstellungen anzeigen
 */
public class WCoreClient implements ClientModInitializer {

    /** Namen (kleingeschrieben) der ausgeblendeten Spieler. */
    public static final Set<String> IGNORED = ConcurrentHashMap.newKeySet();

    /** Name (kleingeschrieben) -> Ersatz-Skin. */
    public static final Map<String, PlayerSkin> SKINS = new ConcurrentHashMap<>();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private static final SuggestionProvider<FabricClientCommandSource> ONLINE_PLAYERS = (ctx, builder) -> {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(
                connection.getOnlinePlayers().stream().map(p -> p.getProfile().name()),
                builder);
    };

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("wcore")
                        .then(literal("ignore")
                                .then(argument("player", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYERS)
                                        .executes(ctx -> toggleIgnore(ctx,
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
                        .then(literal("list")
                                .executes(WCoreClient::list))));
    }

    private static int toggleIgnore(CommandContext<FabricClientCommandSource> ctx, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (IGNORED.remove(key)) {
            ctx.getSource().sendFeedback(Component.literal("[WCore] " + name + " ist wieder sichtbar."));
        } else {
            IGNORED.add(key);
            ctx.getSource().sendFeedback(Component.literal("[WCore] " + name + " wird jetzt ausgeblendet."));
        }
        return 1;
    }

    private static int resetSkin(CommandContext<FabricClientCommandSource> ctx, String name) {
        if (SKINS.remove(name.toLowerCase(Locale.ROOT)) != null) {
            ctx.getSource().sendFeedback(Component.literal("[WCore] Skin von " + name + " zurueckgesetzt."));
        } else {
            ctx.getSource().sendFeedback(Component.literal("[WCore] " + name + " hat keinen ersetzten Skin."));
        }
        return 1;
    }

    /** Fragt bei Mojang die UUID zu einem Spielernamen ab. */
    private static CompletableFuture<Optional<GameProfile>> lookupProfile(String name) {
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
                String id = json.get("id").getAsString();
                String realName = json.get("name").getAsString();
                UUID uuid = UUID.fromString(id.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5"));
                return Optional.of(new GameProfile(uuid, realName));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static int changeSkin(CommandContext<FabricClientCommandSource> ctx, String target, String skinOwner) {
        FabricClientCommandSource source = ctx.getSource();
        Minecraft mc = Minecraft.getInstance();
        source.sendFeedback(Component.literal("[WCore] Lade Skin von " + skinOwner + " ..."));

        lookupProfile(skinOwner)
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

    private static int list(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Component.literal("[WCore] Ausgeblendet: "
                + (IGNORED.isEmpty() ? "-" : String.join(", ", IGNORED))));
        ctx.getSource().sendFeedback(Component.literal("[WCore] Skin ersetzt: "
                + (SKINS.isEmpty() ? "-" : String.join(", ", SKINS.keySet()))));
        return 1;
    }
}
