package dev.santik.core.auth;

import dev.santik.core.SantikCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

import java.util.Set;

/** Блокирует всё до прохождения капчи и входа. */
public final class AuthListener implements Listener {
    private static final Set<String> ALLOWED = Set.of("reg", "register", "login", "l");
    private final SantikCore pl;
    private final AuthManager auth;

    public AuthListener(SantikCore pl, AuthManager auth) { this.pl = pl; this.auth = auth; }

    private boolean blocked(Player p) { return !auth.isAuthed(p.getUniqueId()); }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        String ip = e.getAddress().getHostAddress();
        if (auth.isIpLocked(ip) || !auth.connectionAllowed(ip)) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, pl.msg("ip-locked"));
            return;
        }
        var ban = pl.punishments().active(e.getUniqueId(), "BAN");
        if (ban != null) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, pl.banScreen(ban));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        e.joinMessage(null);
        auth.forget(p.getUniqueId());
        pl.beginJoin(p);
        long ticks = pl.getConfig().getLong("auth.login-timeout-seconds", 150) * 20L;
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline() && blocked(p)) p.kick(pl.msg("kick-timeout"));
        }, ticks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        e.quitMessage(null);
        pl.onQuit(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent e) {
        if (!blocked(e.getPlayer())) return;
        if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getY() != e.getTo().getY()
                || e.getFrom().getZ() != e.getTo().getZ()) {
            e.setTo(e.getFrom().clone().setDirection(e.getTo().getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!blocked(p)) return;
        String m = e.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (!auth.captchaDone(p.getUniqueId()) || !ALLOWED.contains(m)) {
            e.setCancelled(true);
            p.sendMessage(pl.msg("need-auth"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!blocked(p)) return;
        e.setCancelled(true);
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.message());
        if (!auth.captchaDone(p.getUniqueId())) pl.captcha().submit(p, text);
        else p.sendMessage(pl.msg("need-auth"));
    }

    @EventHandler(priority = EventPriority.LOWEST) public void onInteract(PlayerInteractEvent e) { if (blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onInteractEntity(PlayerInteractEntityEvent e) { if (blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onBreak(BlockBreakEvent e) { if (blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onPlace(BlockPlaceEvent e) { if (blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onDrop(PlayerDropItemEvent e) { if (blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onSwap(PlayerSwapHandItemsEvent e) { if (blocked(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent e) {
        if (blocked(e.getPlayer()) && e.getCause() != PlayerTeleportEvent.TeleportCause.UNKNOWN
                && e.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && blocked(p)) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent e) { if (e.getPlayer() instanceof Player p && blocked(p)) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p && blocked(p)) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && blocked(p)) e.setCancelled(true); }
}
