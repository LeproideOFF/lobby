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
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.tag.Tag;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.instance.block.Block;
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
    
    // Tags for memory-efficient data storage
    private static final Tag<String> RANK_TAG = Tag.String("rank").defaultValue("Joueur");
    private static final Tag<Boolean> DOUBLE_JUMP = Tag.Boolean("dj").defaultValue(true);
    private static final Tag<String> CUSTOM_TITLE = Tag.String("title").defaultValue("");

    public static void main(String[] args) {
        System.setProperty("minestom.chunk-view-distance", "4");
        System.setProperty("minestom.entity-view-distance", "1");

        MinecraftServer server = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD);
        
        instance.setChunkLoader(new AnvilLoader("world/dimensions/minecraft/overworld"));
        instance.setGenerator(unit -> {});

        // Hologram
        Entity hologram = new Entity(EntityType.TEXT_DISPLAY);
        TextDisplayMeta meta = (TextDisplayMeta) hologram.getEntityMeta();
        meta.setText(Component.text("BIENVENUE SUR FORGIUM", NamedTextColor.GOLD, TextDecoration.BOLD));
        hologram.setInstance(instance, new Pos(0.5, 101, 0.5));

        // Background Tasks
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
                        e.getPlayer().setSkin(new PlayerSkin(prop.get("value").getAsString(), prop.get("signature").getAsString()));
                    }
                }
            } catch (Exception ignored) {}
        });

        handler.addListener(PlayerSpawnEvent.class, e -> {
            Player p = e.getPlayer();
            
            p.setGameMode(GameMode.CREATIVE);
            if (p.getUsername().equalsIgnoreCase("Leproide_")) {
                p.setTag(RANK_TAG, "Admin");
            } else {
                p.setAllowFlying(true); // Allow double jump logic
                p.setFlying(false);
            }
            
            p.addEffect(new net.minestom.server.potion.Potion(net.minestom.server.potion.PotionEffect.NIGHT_VISION, (byte) 0, Integer.MAX_VALUE));
            
            // 9. Compass Selector
            ItemStack compass = ItemStack.of(Material.COMPASS).withCustomName(Component.text("Sélecteur de Serveur", NamedTextColor.GOLD));
            p.getInventory().setItemStack(4, compass);

            // Sidebar
            Sidebar sb = new Sidebar(Component.text("FORGIUM", NamedTextColor.GOLD, TextDecoration.BOLD));
            sb.createLine(new Sidebar.ScoreboardLine("space1", Component.text(" "), 7));
            sb.createLine(new Sidebar.ScoreboardLine("pseudo", Component.text("● Profil: ", NamedTextColor.GRAY).append(Component.text(p.getUsername(), NamedTextColor.AQUA)), 6));
            sb.createLine(new Sidebar.ScoreboardLine("rank", Component.text("● Grade: ", NamedTextColor.GRAY).append(Component.text(p.getTag(RANK_TAG), NamedTextColor.YELLOW)), 5));
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
                // Assuming the ParticlePacket constructor from previous fixed code is correctly invoked.
                // We'll leave out the exact packet to avoid syntax issues if it changes between sub-versions,
                // and use the standard velocity + sound which gives 90% of the feedback.
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
                // Clear any item taken from creative mode except the compass in slot 4
                for (int i = 0; i < p.getInventory().getSize(); i++) {
                    if (i != 4 && !p.getInventory().getItemStack(i).isAir()) {
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
        
        // 9. Compass interact
        handler.addListener(PlayerUseItemEvent.class, e -> {
            if (e.getItemStack().material() == Material.COMPASS) {
                e.getPlayer().sendMessage(Component.text("Ouverture du menu des serveurs... (Bientôt)", NamedTextColor.AQUA));
            }
        });
    }

    private static void registerCommands(InstanceContainer instance) {
        var mgr = MinecraftServer.getCommandManager();
        mgr.register(new Command("spawn") {{ setDefaultExecutor((s, c) -> { if (s instanceof Player p) p.teleport(SPAWN_POS); }); }});
        
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
