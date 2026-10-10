package dev.santik.core.listener;

import dev.santik.core.SantikCore;
import dev.santik.core.auth.Role;
import dev.santik.core.gui.MenuHolder;
import dev.santik.core.util.CompassItem;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public final class CoreListener implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Set<String> PLAYER_CMDS = Set.of("spawn", "lobby", "rtp", "sethome", "home", "delhome", "homes", "ah", "auction",
            "bal", "balance", "pay", "help", "rules", "msg", "r", "ignore", "tpa", "tpaccept", "tpdeny", "shop", "reg", "register", "login", "l", "list");
    private static final Set<String> ADMIN_CMDS = Set.of("ban", "unban", "kick", "mute", "unmute", "warn", "history", "tp", "tphere", "spec", "vanish", "ahadmin");

    private final SantikCore pl;

    public CoreListener(SantikCore pl) { this.pl = pl; }

    public static boolean allowed(Role role, String cmd) {
        String n = cmd.toLowerCase();
        if (n.startsWith("santikcore:")) n = n.substring(11);
        if (n.contains(":")) return role == Role.OWNER;
        if (PLAYER_CMDS.contains(n)) return true;
        if (role.atLeast(Role.ADMIN) && ADMIN_CMDS.contains(n)) return true;
        return role == Role.OWNER;
    }

    // ---------- компас ----------
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!CompassItem.is(e.getItem())) return;
        e.setCancelled(true);
        if (pl.auth().isAuthed(e.getPlayer().getUniqueId())) pl.menus().openModes(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) { if (CompassItem.is(e.getItemDrop().getItemStack())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (CompassItem.is(e.getMainHandItem()) || CompassItem.is(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        var top = e.getView().getTopInventory();
        if (top.getHolder() instanceof MenuHolder h) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) pl.menus().click(p, h, e);
            return;
        }
        if (CompassItem.is(e.getCurrentItem()) || CompassItem.is(e.getCursor())) { e.setCancelled(true); return; }
        if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0 && CompassItem.is(p.getInventory().getItem(e.getHotbarButton()))) e.setCancelled(true);
        if (e.getClick() == ClickType.SWAP_OFFHAND && CompassItem.is(p.getInventory().getItemInOffHand())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder || CompassItem.is(e.getOldCursor())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) { e.getDrops().removeIf(CompassItem::is); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (pl.worlds().isAnarchy(p.getWorld()) && pl.worlds().anarchy() != null) {
            e.setRespawnLocation(pl.worlds().spawn(pl.worlds().anarchy()));
            try { pl.db().update("DELETE FROM positions WHERE uuid=?", p.getUniqueId().toString()); } catch (Exception ignored) { }
        }
    }

    // ---------- чат ----------
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!pl.auth().isAuthed(p.getUniqueId())) return;
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        String[] pending = pl.searchPending().remove(p.getUniqueId());
        if (pending != null) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(pl, () -> pl.menus().openAuction(p, pending[0], pending[1], text.equalsIgnoreCase("отмена") ? "" : text, 0));
            return;
        }
        var mute = pl.punishments().active(p.getUniqueId(), "MUTE");
        if (mute != null) {
            e.setCancelled(true);
            p.sendMessage(pl.plain("Ты в муте до " + pl.fmtTime(mute.expires()) + ". Причина: " + mute.reason()));
            return;
        }
        if (!pl.roles().of(p).atLeast(Role.ADMIN)) {
            String flood = pl.social().chatFlood(p.getUniqueId(), text);
            if (flood != null) { e.setCancelled(true); p.sendMessage(pl.plain(flood)); return; }
        }
        e.viewers().removeIf(a -> a instanceof Player v && !v.equals(p) && pl.social().ignores(v.getUniqueId(), p.getUniqueId()));
        e.renderer(ChatRenderer.viewerUnaware((source, name, message) -> {
            Role r = pl.roles().of(source);
            String col = r == Role.OWNER ? "<#ff6f91>★ " : r == Role.ADMIN ? "<#ffb7c5>✦ " : "<white>";
            Component head = MM.deserialize(col + MM.escapeTags(source.getName()) + "<dark_gray> » <white>");
            return head.append(message);
        }));
    }

    // ---------- команды ----------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!pl.auth().isAuthed(p.getUniqueId())) return;
        String root = e.getMessage().substring(1).split(" ")[0];
        Role role = pl.roles().of(p);
        if (!allowed(role, root)) {
            e.setCancelled(true);
            p.sendMessage(pl.plain("Недостаточно прав."));
            return;
        }
        if (role != Role.OWNER && !pl.social().commandAllowed(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(pl.plain("Слишком много команд. Подожди пару секунд."));
        }
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent e) {
        Role role = pl.roles().of(e.getPlayer());
        e.getCommands().removeIf(c -> !allowed(role, c));
    }
}
