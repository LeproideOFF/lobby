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
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.world.DimensionType;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.scoreboard.Sidebar;
import net.minestom.server.scoreboard.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.instance.block.Block;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    private static final String VERSION = "1.2";
    private static final Pos SPAWN_POS = new Pos(0.5, 99, 0.5);
    private static final Map<UUID, Sidebar> sidebars = new ConcurrentHashMap<>();
    private static int lobbyId = 1;
    
    // Tags for memory-efficient data storage
    private static final Tag<String> RANK_TAG = Tag.String("rank").defaultValue("Joueur");
    private static final Tag<Boolean> DOUBLE_JUMP = Tag.Boolean("dj").defaultValue(true);
    private static final Tag<String> CUSTOM_TITLE = Tag.String("title").defaultValue("");
    private static final Tag<String> NPC_SERVER_TAG = Tag.String("npc_server");

    public static void main(String[] args) {
        // Essential properties
        System.setProperty("minestom.chunk-view-distance", "4");
        System.setProperty("minestom.entity-view-distance", "1");

        // Initialisation avec Velocity (Nouvelle API Minestom)
        String velocitySecret = "sII87EnuTLpn";
        MinecraftServer server = MinecraftServer.init(new net.minestom.server.Auth.Velocity(velocitySecret));
        System.out.println("[Velocity] Modern forwarding activé avec le secret.");

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD);
        
        String worldPath = "world/dimensions/minecraft/overworld";
        java.io.File worldFolder = new java.io.File(worldPath);
        System.out.println("Checking map at: " + worldFolder.getAbsolutePath());
        if (!worldFolder.exists()) {
            System.err.println("CRITICAL: Map folder NOT FOUND at " + worldPath);
        } else {
            System.out.println("Map folder found! Loading...");
        }

        instance.setChunkLoader(new AnvilLoader(worldPath));
        instance.setGenerator(unit -> {});

        // Team for disabling collisions
        Team lobbyTeam = MinecraftServer.getTeamManager().createTeam("lobby_team");
        lobbyTeam.setCollisionRule(net.minestom.server.network.packet.server.play.TeamsPacket.CollisionRule.NEVER);

        // Hologram
        Entity hologram = new Entity(EntityType.TEXT_DISPLAY);
        TextDisplayMeta meta = (TextDisplayMeta) hologram.getEntityMeta();
        meta.setText(Component.text("BIENVENUE SUR FORGIUM", NamedTextColor.GOLD, TextDecoration.BOLD));
        hologram.setInstance(instance, new Pos(0.5, 101, 0.5));

        // NPC Survie
        Pos npcPos = new Pos(5, 99, 4);
        Entity npcSurvie = new Entity(EntityType.VILLAGER);
        npcSurvie.setTag(NPC_SERVER_TAG, "survie");
        npcSurvie.setNoGravity(true);
        npcSurvie.setInstance(instance, npcPos);

        // Name Tag for NPC
        Entity holoSurvie = new Entity(EntityType.TEXT_DISPLAY);
        TextDisplayMeta hMetaSurvie = (TextDisplayMeta) holoSurvie.getEntityMeta();
        hMetaSurvie.setText(Component.text("SURVIE", NamedTextColor.GOLD, TextDecoration.BOLD));
        hMetaSurvie.setScale(new Vec(2, 2, 2));
        holoSurvie.setInstance(instance, npcPos.add(0, 2.3, 0));

        // Background Tasks - Optimized frequency (5 seconds)
        MinecraftServer.getSchedulerManager().submitTask(() -> {
            int online = MinecraftServer.getConnectionManager().getOnlinePlayers().size();

            for (Player p : instance.getPlayers()) {
                Sidebar sb = sidebars.get(p.getUuid());
                if (sb != null) {
                    sb.updateLineContent("players", Component.text("● Joueurs: ", NamedTextColor.GRAY).append(Component.text(online + "/15", NamedTextColor.GREEN)));
                    sb.updateLineContent("ping", Component.text("● Ping: ", NamedTextColor.GRAY).append(Component.text(p.getLatency() + "ms", NamedTextColor.GREEN)));
                }
            }
            return TaskSchedule.seconds(5);
        });

        MinecraftServer.getSchedulerManager().submitTask(() -> {
            Component msg = Component.text("[Forgium] ", NamedTextColor.GOLD).append(Component.text("Rejoignez notre Discord : discord.gg/forgium", NamedTextColor.YELLOW));
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(p -> p.sendMessage(msg));
            return TaskSchedule.minutes(3);
        });

        registerEvents(instance, lobbyTeam);
        registerCommands(instance);

        // Lobby ID and Port logic based on system property or env var
        String lobbyIdStr = System.getProperty("lobby.id", System.getenv("LOBBY_ID"));
        lobbyId = (lobbyIdStr != null) ? Integer.parseInt(lobbyIdStr) : 1;
        int port = 25570 + (lobbyId - 1);

        System.out.println("Lobby #" + lobbyId + " starting on port " + port + "...");
        server.start("0.0.0.0", port);
        System.out.println("Lobby #" + lobbyId + " started on port " + port);
        
        // Keep the main thread alive
        while (true) {
            try { Thread.sleep(10000); } catch (InterruptedException e) { break; }
        }
    }

    private static void registerEvents(InstanceContainer instance, Team lobbyTeam) {
        GlobalEventHandler handler = MinecraftServer.getGlobalEventHandler();
        
        handler.addListener(AsyncPlayerConfigurationEvent.class, e -> { 
            if (MinecraftServer.getConnectionManager().getOnlinePlayers().size() >= 15) {
                e.getPlayer().kick(Component.text("Ce lobby est plein ! (15/15)", NamedTextColor.RED));
                return;
            }

            e.setSpawningInstance(instance); 
            e.getPlayer().setRespawnPoint(SPAWN_POS);
        });

        handler.addListener(PlayerSpawnEvent.class, e -> {
            Player p = e.getPlayer();
            p.setTeam(lobbyTeam);
            
            p.setGameMode(GameMode.CREATIVE);
            if (p.getUsername().equalsIgnoreCase("Leproide_")) {
                p.setTag(RANK_TAG, "Admin");
            } else {
                p.setAllowFlying(true); // Allow double jump logic
                p.setFlying(false);
            }
            
            p.addEffect(new net.minestom.server.potion.Potion(net.minestom.server.potion.PotionEffect.NIGHT_VISION, (byte) 0, Integer.MAX_VALUE));
            
            // Sidebar
            Sidebar sb = new Sidebar(Component.text("FORGIUM", NamedTextColor.GOLD, TextDecoration.BOLD));
            sb.createLine(new Sidebar.ScoreboardLine("space1", Component.text(" "), 8));
            sb.createLine(new Sidebar.ScoreboardLine("pseudo", Component.text("● Profil: ", NamedTextColor.GRAY).append(Component.text(p.getUsername(), NamedTextColor.AQUA)), 7));
            sb.createLine(new Sidebar.ScoreboardLine("rank", Component.text("● Grade: ", NamedTextColor.GRAY).append(Component.text(p.getTag(RANK_TAG), NamedTextColor.YELLOW)), 6));
            sb.createLine(new Sidebar.ScoreboardLine("lobby", Component.text("● Lobby: ", NamedTextColor.GRAY).append(Component.text("#" + lobbyId, NamedTextColor.GREEN)), 5));
            sb.createLine(new Sidebar.ScoreboardLine("space2", Component.text("  "), 3));
            sb.createLine(new Sidebar.ScoreboardLine("players", Component.text("● Joueurs: ", NamedTextColor.GRAY).append(Component.text("1/15", NamedTextColor.GREEN)), 2));
            sb.createLine(new Sidebar.ScoreboardLine("ping", Component.text("● Ping: ", NamedTextColor.GRAY).append(Component.text("0ms", NamedTextColor.GREEN)), 1));
            sb.createLine(new Sidebar.ScoreboardLine("ip", Component.text("play.forgium.fr", NamedTextColor.YELLOW), 0));
            sb.addViewer(p);sb.createLine(new Sidebar.ScoreboardLine("version", Component.text("Version: ", NamedTextColor.GRAY).append(Component.text("v" + VERSION, NamedTextColor.WHITE)), 4));

            sidebars.put(p.getUuid(), sb);
            
            p.sendPlayerListHeaderAndFooter(
                Component.text("\nFORGIUM NETWORK\n", NamedTextColor.GOLD, TextDecoration.BOLD), 
                Component.text("\nLobby #" + lobbyId + " (v" + VERSION + ")\n\nplay.forgium.fr\ndiscord.gg/forgium\n", NamedTextColor.YELLOW)
            );
        });

        handler.addListener(PlayerDisconnectEvent.class, e -> sidebars.remove(e.getPlayer().getUuid()));
        
        handler.addListener(PlayerChatEvent.class, e -> {
            e.setCancelled(true);
            Player p = e.getPlayer();
            String msg = e.getRawMessage();
            
            // 76. Easter Egg Forgium Glow
            if (msg.toLowerCase().contains("forgium")) {
                p.setGlowing(true);
                MinecraftServer.getSchedulerManager().buildTask(() -> p.setGlowing(false)).delay(TaskSchedule.seconds(2)).schedule();
            }

            String title = p.getTag(CUSTOM_TITLE);
            Component prefix = title.isEmpty() ? Component.empty() : Component.text("[" + title + "] ", NamedTextColor.LIGHT_PURPLE);
            Component rankColor = "Admin".equals(p.getTag(RANK_TAG)) ? Component.text(p.getUsername(), NamedTextColor.RED) : Component.text(p.getUsername(), NamedTextColor.YELLOW);

            Component f = prefix.append(rankColor)
                .append(Component.text(" > ", NamedTextColor.GRAY))
                .append(Component.text(msg, NamedTextColor.WHITE));
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(pl -> pl.sendMessage(f));
        });
        
        handler.addListener(PlayerMoveEvent.class, e -> {
            Player p = e.getPlayer(); 
            if (p.getPosition().y() < 10) p.teleport(SPAWN_POS);

            if (instance.getBlock(p.getPosition()).compare(Block.LIGHT_WEIGHTED_PRESSURE_PLATE)) {
                p.setVelocity(new Vec(0, 25, 0));
                p.playSound(Sound.sound(SoundEvent.ENTITY_GENERIC_EXPLODE, Sound.Source.PLAYER, 1f, 2f));
            }
        });

        // NPC Interact Listener
        handler.addListener(PlayerEntityInteractEvent.class, e -> {
            Entity target = e.getTarget();
            if (target.hasTag(NPC_SERVER_TAG)) {
                String serverName = target.getTag(NPC_SERVER_TAG);
                e.getPlayer().sendMessage(Component.text("Connexion à " + serverName + "...", NamedTextColor.YELLOW));
                
                try {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    DataOutputStream out = new DataOutputStream(b);
                    out.writeUTF("Connect");
                    out.writeUTF(serverName);
                    e.getPlayer().sendPluginMessage("bungeecord:main", b.toByteArray());
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        // 1. Double Jump Logic
        handler.addListener(PlayerStartFlyingEvent.class, e -> {
            Player p = e.getPlayer();
            if (!"Admin".equals(p.getTag(RANK_TAG))) {
                p.setFlying(false);
                if (Boolean.TRUE.equals(p.getTag(DOUBLE_JUMP))) {
                    p.setVelocity(p.getPosition().direction().mul(15).withY(15));
                    p.playSound(Sound.sound(SoundEvent.ENTITY_BAT_TAKEOFF, Sound.Source.PLAYER, 1f, 1f));
                    p.setTag(DOUBLE_JUMP, false);
                }
            }
        });

        // Reset double jump on ground & Anti-Creative Inventory
        handler.addListener(PlayerTickEvent.class, e -> {
            Player p = e.getPlayer();
            if (!"Admin".equals(p.getTag(RANK_TAG))) {
                if (p.isOnGround() && !Boolean.TRUE.equals(p.getTag(DOUBLE_JUMP))) {
                    p.setTag(DOUBLE_JUMP, true);
                    p.setAllowFlying(true);
                }
                // Clear any item taken from creative mode
                for (int i = 0; i < p.getInventory().getSize(); i++) {
                    if (!p.getInventory().getItemStack(i).isAir()) {
                        p.getInventory().setItemStack(i, ItemStack.AIR);
                    }
                }
            }
        });

        // Anti-Build for normal players
        handler.addListener(PlayerBlockBreakEvent.class, e -> { if (!"Admin".equals(e.getPlayer().getTag(RANK_TAG))) e.setCancelled(true); });
        handler.addListener(PlayerBlockPlaceEvent.class, e -> { if (!"Admin".equals(e.getPlayer().getTag(RANK_TAG))) e.setCancelled(true); });
        handler.addListener(EntityDamageEvent.class, e -> e.setCancelled(true));
        
        // Anti-Drop
        handler.addListener(ItemDropEvent.class, e -> e.setCancelled(true));
    }

    private static void registerCommands(InstanceContainer instance) {
        var mgr = MinecraftServer.getCommandManager();
        mgr.register(new Command("spawn") {{ setDefaultExecutor((s, c) -> { if (s instanceof Player p) p.teleport(SPAWN_POS); }); }});
        
        // NPC Spawn Command
        Command npcCmd = new Command("npc");
        var serverArg = ArgumentType.Word("serveur");
        npcCmd.addSyntax((s, c) -> {
            if (s instanceof Player p && "Admin".equals(p.getTag(RANK_TAG))) {
                String serverName = c.get(serverArg);
                
                // NPC Entity (Villager for visibility)
                Entity npc = new Entity(EntityType.VILLAGER);
                npc.setTag(NPC_SERVER_TAG, serverName);
                npc.setNoGravity(true);
                npc.setInstance(instance, p.getPosition());
                
                // Hologram for NPC Name (Floating above)
                Entity holo = new Entity(EntityType.TEXT_DISPLAY);
                TextDisplayMeta hMeta = (TextDisplayMeta) holo.getEntityMeta();
                hMeta.setText(Component.text(serverName.toUpperCase(), NamedTextColor.GOLD, TextDecoration.BOLD));
                hMeta.setScale(new Vec(1.5, 1.5, 1.5));
                holo.setInstance(instance, p.getPosition().add(0, 2.4, 0));
                
                p.sendMessage(Component.text("PNJ créé avec succès pour : " + serverName, NamedTextColor.GREEN));
            }
        }, serverArg);
        mgr.register(npcCmd);

        // 31. Private Messages (/msg)
        Command msgCmd = new Command("msg");
        var targetArg = ArgumentType.Word("joueur");
        var msgArg = ArgumentType.StringArray("message");
        msgCmd.addSyntax((s, c) -> {
            if (s instanceof Player sender) {
                Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(c.get(targetArg));
                if (target != null) {
                    String m = String.join(" ", c.get(msgArg));
                    sender.sendMessage(Component.text("Moi -> " + target.getUsername() + " : " + m, NamedTextColor.GRAY));
                    target.sendMessage(Component.text(sender.getUsername() + " -> Moi : " + m, NamedTextColor.LIGHT_PURPLE));
                } else {
                    sender.sendMessage(Component.text("Joueur introuvable.", NamedTextColor.RED));
                }
            }
        }, targetArg, msgArg);
        mgr.register(msgCmd);

        // Admin command to set ranks
        Command rankCmd = new Command("setrank");
        var rankTargetArg = ArgumentType.Word("joueur");
        var rankNameArg = ArgumentType.Word("grade");
        rankCmd.addSyntax((s, c) -> {
            if (s instanceof Player p && "Admin".equals(p.getTag(RANK_TAG))) {
                Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(c.get(rankTargetArg));
                if (target != null) {
                    String newRank = c.get(rankNameArg);
                    target.setTag(RANK_TAG, newRank);
                    Sidebar sb = sidebars.get(target.getUuid());
                    if (sb != null) sb.updateLineContent("rank", Component.text("● Grade: ", NamedTextColor.GRAY).append(Component.text(newRank, NamedTextColor.YELLOW)));
                    p.sendMessage(Component.text("Grade de " + target.getUsername() + " mis à jour : " + newRank, NamedTextColor.GREEN));
                    target.sendMessage(Component.text("Vous êtes maintenant " + newRank, NamedTextColor.GOLD));
                } else {
                    p.sendMessage(Component.text("Joueur introuvable.", NamedTextColor.RED));
                }
            }
        }, rankTargetArg, rankNameArg);
        mgr.register(rankCmd);
        
        // 18. Custom Title Command
        Command titleCmd = new Command("settitle");
        var titleArg = ArgumentType.Word("titre");
        titleCmd.addSyntax((s, c) -> {
            if (s instanceof Player p && "Admin".equals(p.getTag(RANK_TAG))) {
                Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(c.get(rankTargetArg)); // Re-using target arg
                if (target != null) {
                    target.setTag(CUSTOM_TITLE, c.get(titleArg));
                    p.sendMessage(Component.text("Titre ajouté.", NamedTextColor.GREEN));
                } else {
                    p.sendMessage(Component.text("Joueur introuvable.", NamedTextColor.RED));
                }
            }
        }, rankTargetArg, titleArg);
        mgr.register(titleCmd);
    }
}
