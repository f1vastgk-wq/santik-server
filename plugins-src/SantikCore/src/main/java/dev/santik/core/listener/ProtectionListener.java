package dev.santik.core.listener;

import dev.santik.core.SantikCore;
import dev.santik.core.util.CompassItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.Block;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Защита лобби (весь мир) и спавна анархии (радиус). Блокировка на уровне событий сервера. */
public final class ProtectionListener implements Listener {
    private final SantikCore pl;
    public final Set<UUID> buildMode = ConcurrentHashMap.newKeySet();

    public ProtectionListener(SantikCore pl) { this.pl = pl; }

    private boolean prot(Location l) {
        World w = l.getWorld();
        if (!pl.worlds().isAnarchy(w)) return true;
        return pl.worlds().inAnarchySpawn(l);
    }

    private boolean bypass(Player p) { return buildMode.contains(p.getUniqueId()) && pl.roles().isOwner(p.getUniqueId()); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (prot(e.getBlock().getLocation()) && !bypass(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (prot(e.getBlock().getLocation()) && !bypass(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) { if (prot(e.getBlock().getLocation()) && !bypass(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) { if (prot(e.getBlock().getLocation()) && !bypass(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Location at = e.getClickedBlock() != null ? e.getClickedBlock().getLocation() : p.getLocation();
        if (!prot(at) || bypass(p)) return;
        if (e.getAction() == Action.PHYSICAL) { e.setCancelled(true); return; }
        if (e.getClickedBlock() != null) e.setUseInteractedBlock(Event.Result.DENY);
        var item = e.getItem();
        if (item == null || CompassItem.is(item)) return;
        String n = item.getType().name();
        boolean lobbyWorld = !pl.worlds().isAnarchy(p.getWorld());
        if (lobbyWorld || n.endsWith("BUCKET") || n.equals("FLINT_AND_STEEL") || n.equals("FIRE_CHARGE")
                || n.equals("END_CRYSTAL") || n.endsWith("MINECART") || n.equals("ARMOR_STAND") || n.endsWith("_BOAT") || n.endsWith("_RAFT")
                || n.endsWith("_SPAWN_EGG") || n.equals("TNT") || n.equals("LAVA_BUCKET")) {
            e.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (bypass(e.getPlayer())) return;
        Entity en = e.getRightClicked();
        if (!prot(en.getLocation())) return;
        if (!pl.worlds().isAnarchy(en.getWorld()) || en instanceof ItemFrame || en instanceof ArmorStand || en instanceof Painting
                || en instanceof Vehicle || en instanceof Display) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) { if (prot(e.getRightClicked().getLocation()) && !bypass(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) { if (e.getPlayer() != null && prot(e.getEntity().getLocation()) && !bypass(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!prot(e.getEntity().getLocation())) return;
        if (e.getRemover() instanceof Player p && bypass(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) { e.blockList().removeIf(b -> prot(b.getLocation())); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { e.blockList().removeIf(b -> prot(b.getLocation())); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity v = e.getEntity();
        World w = v.getWorld();
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.VOID || c == EntityDamageEvent.DamageCause.KILL || c == EntityDamageEvent.DamageCause.SUICIDE) return;
        if (!pl.worlds().isAnarchy(w)) { e.setCancelled(true); return; }
        if (v instanceof Player && pl.worlds().inAnarchySpawn(v.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) { if (!pl.worlds().isAnarchy(e.getEntity().getWorld())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!pl.worlds().isAnarchy(e.getLocation().getWorld())) { e.setCancelled(true); return; }
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL && pl.worlds().inAnarchySpawn(e.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { if (prot(e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) { if (prot(e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) { if (prot(e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) { if (prot(e.getToBlock().getLocation()) && !prot(e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) { if (prot(e.getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) { if (prot(e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (prot(e.getBlock().getLocation())) { e.setCancelled(true); return; }
        for (Block b : e.getBlocks()) if (prot(b.getRelative(e.getDirection()).getLocation()) || prot(b.getLocation())) { e.setCancelled(true); return; }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (prot(e.getBlock().getLocation())) { e.setCancelled(true); return; }
        for (Block b : e.getBlocks()) if (prot(b.getLocation())) { e.setCancelled(true); return; }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) { if (!pl.worlds().isAnarchy(e.getWorld()) && e.toWeatherState()) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo().getBlockY() == e.getFrom().getBlockY() || e.getTo().getY() > -20) return;
        World w = e.getTo().getWorld();
        if (!pl.worlds().isAnarchy(w)) e.getPlayer().teleportAsync(pl.worlds().spawn(w));
    }
}
