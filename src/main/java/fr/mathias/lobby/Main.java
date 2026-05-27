package fr.mathias.lobby;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.player.*;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.DimensionType;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.scoreboard.Sidebar;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.entity.PlayerSkin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    private static final Pos SPAWN_POS = new Pos(0.5, 99, 0.5);
    private static final Map<UUID, Sidebar> sidebars = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.setProperty("minestom.chunk-view-distance", "4");
        System.setProperty("minestom.entity-view-distance", "1");

        MinecraftServer server = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD);
        
        instance.setChunkLoader(new AnvilLoader("world/dimensions/minecraft/overworld"));
        instance.setGenerator(unit -> {});

        // Tasks - Mise à jour du scoreboard (joueurs en ligne) et Action Bar (RAM)
        MinecraftServer.getSchedulerManager().submitTask(() -> {
            int online = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
            long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
            Component ramInfo = Component.text("RAM: " + usedMem + "MB", NamedTextColor.GOLD, TextDecoration.BOLD);

            for (Player p : instance.getPlayers()) {
                p.sendActionBar(ramInfo);
                Sidebar sb = sidebars.get(p.getUuid());
                if (sb != null) {
                    sb.updateLineContent("players", Component.text("● Joueurs: ", NamedTextColor.GRAY).append(Component.text(online, NamedTextColor.GREEN)));
                    sb.updateLineContent("ping", Component.text("● Ping: ", NamedTextColor.GRAY).append(Component.text(p.getLatency() + "ms", NamedTextColor.GREEN)));
                }
            }
            return TaskSchedule.seconds(2);
        });

        // Announcements
        MinecraftServer.getSchedulerManager().submitTask(() -> {
            Component msg = Component.text("[Forgium] ", NamedTextColor.GOLD).append(Component.text("Rejoignez notre Discord : discord.gg/forgium", NamedTextColor.YELLOW));
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(p -> p.sendMessage(msg));
            return TaskSchedule.minutes(3);
        });

        registerEvents(instance);
        registerCommands(instance);
        server.start("0.0.0.0", 25565);
    }

    private static void registerEvents(InstanceContainer instance) {
        GlobalEventHandler handler = MinecraftServer.getGlobalEventHandler();
        
        handler.addListener(AsyncPlayerConfigurationEvent.class, e -> { 
            e.setSpawningInstance(instance); 
            e.getPlayer().setRespawnPoint(SPAWN_POS);
            
            // Micro Skin Restorer
            String name = e.getPlayer().getUsername();
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest req1 = HttpRequest.newBuilder().uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name)).build();
                HttpResponse<String> res1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
                if (res1.statusCode() == 200) {
                    String uuid = JsonParser.parseString(res1.body()).getAsJsonObject().get("id").getAsString();
                    HttpRequest req2 = HttpRequest.newBuilder().uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false")).build();
                    HttpResponse<String> res2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
                    if (res2.statusCode() == 200) {
                        JsonObject prop = JsonParser.parseString(res2.body()).getAsJsonObject().getAsJsonArray("properties").get(0).getAsJsonObject();
                        String texture = prop.get("value").getAsString();
                        String signature = prop.get("signature").getAsString();
                        e.getPlayer().setSkin(new PlayerSkin(texture, signature));
                    }
                }
            } catch (Exception ignored) {
            }
        });

        handler.addListener(PlayerSpawnEvent.class, e -> {
            Player p = e.getPlayer(); p.setGameMode(GameMode.CREATIVE);
            p.addEffect(new net.minestom.server.potion.Potion(net.minestom.server.potion.PotionEffect.NIGHT_VISION, (byte) 0, Integer.MAX_VALUE));
            if (instance.getBlock(0, 39, 0).isAir()) instance.setBlock(0, 39, 0, Block.DIRT);
            
            // Personnalisation Forgium Network
            Sidebar sb = new Sidebar(Component.text("FORGIUM", NamedTextColor.GOLD, TextDecoration.BOLD));
            sb.createLine(new Sidebar.ScoreboardLine("space1", Component.text(" "), 7));
            sb.createLine(new Sidebar.ScoreboardLine("pseudo", Component.text("● Profil: ", NamedTextColor.GRAY).append(Component.text(p.getUsername(), NamedTextColor.AQUA)), 6));
            sb.createLine(new Sidebar.ScoreboardLine("rank", Component.text("● Grade: ", NamedTextColor.GRAY).append(Component.text("Joueur", NamedTextColor.GRAY)), 5));
            sb.createLine(new Sidebar.ScoreboardLine("space2", Component.text("  "), 4));
            sb.createLine(new Sidebar.ScoreboardLine("players", Component.text("● Joueurs: ", NamedTextColor.GRAY).append(Component.text("1", NamedTextColor.GREEN)), 3));
            sb.createLine(new Sidebar.ScoreboardLine("ping", Component.text("● Ping: ", NamedTextColor.GRAY).append(Component.text("0ms", NamedTextColor.GREEN)), 2));
            sb.createLine(new Sidebar.ScoreboardLine("space3", Component.text("   "), 1));
            sb.createLine(new Sidebar.ScoreboardLine("ip", Component.text("play.forgium.fr", NamedTextColor.YELLOW), 0));
            sb.addViewer(p);
            sidebars.put(p.getUuid(), sb);
            
            p.sendPlayerListHeaderAndFooter(
                Component.text("\nFORGIUM NETWORK\n", NamedTextColor.GOLD, TextDecoration.BOLD), 
                Component.text("\nplay.forgium.fr\ndiscord.gg/forgium\n", NamedTextColor.YELLOW)
            );
        });

        handler.addListener(PlayerDisconnectEvent.class, e -> sidebars.remove(e.getPlayer().getUuid()));
        
        handler.addListener(PlayerChatEvent.class, e -> {
            e.setCancelled(true);
            Component f = Component.text(e.getPlayer().getUsername(), NamedTextColor.YELLOW)
                .append(Component.text(" > ", NamedTextColor.GRAY))
                .append(Component.text(e.getRawMessage(), NamedTextColor.WHITE));
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(p -> p.sendMessage(f));
        });
        
        handler.addListener(PlayerMoveEvent.class, e -> {
            Player p = e.getPlayer(); if (p.getPosition().y() < 10) p.teleport(SPAWN_POS);
            if (instance.getBlock(p.getPosition()).compare(Block.LIGHT_WEIGHTED_PRESSURE_PLATE)) {
                p.setVelocity(new Vec(0, 25, 0));
                p.playSound(Sound.sound(SoundEvent.ENTITY_GENERIC_EXPLODE, Sound.Source.PLAYER, 1f, 2f));
                p.sendPacket(new ParticlePacket(Particle.CLOUD, p.getPosition().x(), p.getPosition().y(), p.getPosition().z(), 0.1f, 0.1f, 0.1f, 0.1f, 10));
            }
        });
        
        handler.addListener(PlayerBlockBreakEvent.class, e -> { if (e.getPlayer().getPosition().distance(SPAWN_POS) < 10 && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true); });
        handler.addListener(PlayerBlockPlaceEvent.class, e -> { if (e.getPlayer().getPosition().distance(SPAWN_POS) < 10 && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true); });
        handler.addListener(EntityDamageEvent.class, e -> e.setCancelled(true));
    }

    private static void registerCommands(InstanceContainer instance) {
        var mgr = MinecraftServer.getCommandManager();
        mgr.register(new Command("save") {{ setDefaultExecutor((s, c) -> { instance.saveChunksToStorage(); s.sendMessage(Component.text("Sauvegardé.")); }); }});
        mgr.register(new Command("spawn") {{ setDefaultExecutor((s, c) -> { if (s instanceof Player p) p.teleport(SPAWN_POS); }); }});
        
        // Hologram Command
        Command holoCmd = new Command("hologram");
        var textArg = ArgumentType.StringArray("text");
        holoCmd.addSyntax((s, c) -> {
            if (s instanceof Player p) {
                String[] textArr = c.get(textArg);
                String fullText = String.join(" ", textArr);
                Entity hologram = new Entity(EntityType.TEXT_DISPLAY);
                TextDisplayMeta meta = (TextDisplayMeta) hologram.getEntityMeta();
                meta.setText(Component.text(fullText, NamedTextColor.WHITE));
                hologram.setInstance(instance, p.getPosition().add(0, 1, 0));
                p.sendMessage(Component.text("Hologramme créé !", NamedTextColor.GREEN));
            }
        }, textArg);
        mgr.register(holoCmd);
    }
}
