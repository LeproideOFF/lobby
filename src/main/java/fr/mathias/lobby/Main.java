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
import net.minestom.server.coordinate.Point;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.scoreboard.Sidebar;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.entity.PlayerSkin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Main {
    private static final Map<UUID, Point> pos1 = new HashMap<>();
    private static final Map<UUID, Point> pos2 = new HashMap<>();
    private static final Pos SPAWN_POS = new Pos(0.5, 99, 0.5);
    private static Sidebar sidebar;

    public static void main(String[] args) {
        System.setProperty("minestom.chunk-view-distance", "4");
        System.setProperty("minestom.entity-view-distance", "1");

        MinecraftServer server = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD);
        
        instance.setChunkLoader(new AnvilLoader("world/dimensions/minecraft/overworld"));
        instance.setGenerator(unit -> {});

        // Scoreboard
        sidebar = new Sidebar(Component.text("--- INFOS ---", NamedTextColor.GOLD));
        sidebar.createLine(new Sidebar.ScoreboardLine("ram", Component.text("RAM: ..."), 1));
        sidebar.createLine(new Sidebar.ScoreboardLine("players", Component.text("Players: ..."), 0));

        // Tasks
        MinecraftServer.getSchedulerManager().submitTask(() -> {
            long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
            int online = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
            sidebar.updateLineContent("ram", Component.text("RAM: ", NamedTextColor.WHITE).append(Component.text(usedMem + "MB", NamedTextColor.AQUA)));
            sidebar.updateLineContent("players", Component.text("Players: ", NamedTextColor.WHITE).append(Component.text(online, NamedTextColor.GREEN)));
            Component info = Component.text("RAM: " + usedMem + "MB", NamedTextColor.GOLD);
            for (Player p : instance.getPlayers()) p.sendActionBar(info);
            return TaskSchedule.seconds(1);
        });

        // Announcements
        MinecraftServer.getSchedulerManager().submitTask(() -> {
            Component msg = Component.text("[!] ", NamedTextColor.RED).append(Component.text("Utilise /hologram <texte> pour créer un texte volant !", NamedTextColor.GRAY));
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
            sidebar.addViewer(p);
            p.sendPlayerListHeaderAndFooter(Component.text("--- SPAWN ---", NamedTextColor.GOLD), Component.text("Optimisé 48MB", NamedTextColor.GRAY));
        });

        handler.addListener(PlayerDisconnectEvent.class, e -> sidebar.removeViewer(e.getPlayer()));
        handler.addListener(PlayerChatEvent.class, e -> {
            e.setCancelled(true);
            Component f = Component.text(e.getPlayer().getUsername(), NamedTextColor.YELLOW).append(Component.text(" > ", NamedTextColor.GRAY)).append(Component.text(e.getRawMessage(), NamedTextColor.WHITE));
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

        mgr.register(new Command("/pos1") {{ setDefaultExecutor((s, c) -> { if (s instanceof Player p) { pos1.put(p.getUuid(), p.getPosition().asVec().apply(Vec.Operator.FLOOR)); p.sendMessage(Component.text("Pos 1 OK")); } }); }});
        mgr.register(new Command("/pos2") {{ setDefaultExecutor((s, c) -> { if (s instanceof Player p) { pos2.put(p.getUuid(), p.getPosition().asVec().apply(Vec.Operator.FLOOR)); p.sendMessage(Component.text("Pos 2 OK")); } }); }});
        Command set = new Command("/set"); var block = ArgumentType.BlockState("block");
        set.addSyntax((s, c) -> { if (s instanceof Player p) { Point p1 = pos1.get(p.getUuid()); Point p2 = pos2.get(p.getUuid()); if (p1 != null && p2 != null) fill(instance, p1, p2, c.get(block)); } }, block);
        mgr.register(set);
    }

    private static void fill(InstanceContainer instance, Point p1, Point p2, Block block) {
        int minX = Math.min(p1.blockX(), p2.blockX()); int maxX = Math.max(p1.blockX(), p2.blockX());
        int minY = Math.min(p1.blockY(), p2.blockY()); int maxY = Math.max(p1.blockY(), p2.blockY());
        int minZ = Math.min(p1.blockZ(), p2.blockZ()); int maxZ = Math.max(p1.blockZ(), p2.blockZ());
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) instance.setBlock(x, y, z, block);
    }
}
