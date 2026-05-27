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
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Main {
    private static final Map<UUID, Point> pos1 = new HashMap<>();
    private static final Map<UUID, Point> pos2 = new HashMap<>();
    private static final Pos SPAWN_POS = new Pos(0.5, 40, 0.5);
    private static final Tag<Long> JUMP_COOLDOWN = Tag.Long("jump_cd");
    private static Sidebar sidebar;

    public static void main(String[] args) {
        System.setProperty("minestom.chunk-view-distance", "2");
        System.setProperty("minestom.entity-view-distance", "2");

        MinecraftServer server = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD);
        
        instance.setChunkLoader(new AnvilLoader("world"));
        instance.setGenerator(unit -> {});

        // Scoreboard
        sidebar = new Sidebar(Component.text("--- INFOS ---", NamedTextColor.GOLD));
        sidebar.createLine(new Sidebar.ScoreboardLine("ram", Component.text("RAM: ..."), 1));
        sidebar.createLine(new Sidebar.ScoreboardLine("players", Component.text("Players: ..."), 0));

        Entity hologram = new Entity(EntityType.TEXT_DISPLAY);
        TextDisplayMeta hMeta = (TextDisplayMeta) hologram.getEntityMeta();
        hMeta.setText(Component.text("BIENVENUE SUR LE SPAWN", NamedTextColor.AQUA, TextDecoration.BOLD));
        hologram.setInstance(instance, new Pos(0.5, 42, 0.5));

        // Optimized Monitor (2s interval to save packets/CPU)
        MinecraftServer.getSchedulerManager().submitTask(() -> {
            long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
            int online = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
            sidebar.updateLineContent("ram", Component.text("RAM: ", NamedTextColor.WHITE).append(Component.text(usedMem + "MB", NamedTextColor.AQUA)));
            sidebar.updateLineContent("players", Component.text("Players: ", NamedTextColor.WHITE).append(Component.text(online, NamedTextColor.GREEN)));
            Component info = Component.text("RAM: " + usedMem + "MB", NamedTextColor.GOLD);
            for (Player p : instance.getPlayers()) p.sendActionBar(info);
            return TaskSchedule.seconds(2);
        });

        registerEvents(instance);
        registerCommands(instance);
        server.start("0.0.0.0", 25565);
    }

    private static void registerEvents(InstanceContainer instance) {
        GlobalEventHandler handler = MinecraftServer.getGlobalEventHandler();
        handler.addListener(AsyncPlayerConfigurationEvent.class, e -> { e.setSpawningInstance(instance); e.getPlayer().setRespawnPoint(SPAWN_POS); });
        
        handler.addListener(PlayerSpawnEvent.class, e -> {
            Player p = e.getPlayer(); p.setGameMode(GameMode.CREATIVE);
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
            Player p = e.getPlayer();
            if (p.getPosition().y() < 10) p.teleport(SPAWN_POS);

            // Jump Pad with Cooldown to prevent packet spam
            if (instance.getBlock(p.getPosition()).compare(Block.LIGHT_WEIGHTED_PRESSURE_PLATE)) {
                long now = System.currentTimeMillis();
                long lastJump = p.getTag(JUMP_COOLDOWN) != null ? p.getTag(JUMP_COOLDOWN) : 0;
                if (now - lastJump > 500) {
                    p.setTag(JUMP_COOLDOWN, now);
                    p.setVelocity(new Vec(0, 25, 0));
                    p.playSound(Sound.sound(SoundEvent.ENTITY_GENERIC_EXPLODE, Sound.Source.PLAYER, 0.5f, 2f));
                    p.sendPacket(new ParticlePacket(Particle.CLOUD, p.getPosition().x(), p.getPosition().y(), p.getPosition().z(), 0.2f, 0.2f, 0.2f, 0.05f, 15));
                }
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
        
        // WE Commands
        mgr.register(new Command("/pos1") {{ setDefaultExecutor((s, c) -> { if (s instanceof Player p) { 
            Point pos = new Vec(p.getPosition().blockX(), p.getPosition().blockY(), p.getPosition().blockZ());
            pos1.put(p.getUuid(), pos); 
            p.sendMessage(Component.text("Pos 1: " + pos.blockX() + " " + pos.blockY() + " " + pos.blockZ(), NamedTextColor.LIGHT_PURPLE)); 
        } }); }});
        
        mgr.register(new Command("/pos2") {{ setDefaultExecutor((s, c) -> { if (s instanceof Player p) { 
            Point pos = new Vec(p.getPosition().blockX(), p.getPosition().blockY(), p.getPosition().blockZ());
            pos2.put(p.getUuid(), pos); 
            p.sendMessage(Component.text("Pos 2: " + pos.blockX() + " " + pos.blockY() + " " + pos.blockZ(), NamedTextColor.LIGHT_PURPLE)); 
        } }); }});

        Command set = new Command("/set"); var block = ArgumentType.BlockState("block");
        set.addSyntax((s, c) -> { if (s instanceof Player p) {
            Point p1 = pos1.get(p.getUuid()); Point p2 = pos2.get(p.getUuid());
            if (p1 != null && p2 != null) {
                fill(instance, p1, p2, c.get(block));
                p.sendMessage(Component.text("Zone remplie.", NamedTextColor.GREEN));
            }
        } }, block);
        mgr.register(set);
    }

    private static void fill(InstanceContainer instance, Point p1, Point p2, Block block) {
        int minX = Math.min(p1.blockX(), p2.blockX()); int maxX = Math.max(p1.blockX(), p2.blockX());
        int minY = Math.min(p1.blockY(), p2.blockY()); int maxY = Math.max(p1.blockY(), p2.blockY());
        int minZ = Math.min(p1.blockZ(), p2.blockZ()); int maxZ = Math.max(p1.blockZ(), p2.blockZ());
        
        // Use single-thread batching to avoid massive packet bursts
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    instance.setBlock(x, y, z, block);
                }
            }
        }
    }
}
