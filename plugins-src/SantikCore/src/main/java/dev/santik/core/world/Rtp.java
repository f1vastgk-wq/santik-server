package dev.santik.core.world;

import dev.santik.core.SantikCore;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Random;
import java.util.Set;

public final class Rtp {
    private static final Set<Material> BAD = Set.of(Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE,
            Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE, Material.BEDROCK, Material.TNT, Material.COBWEB);
    private final SantikCore pl;
    private final ArrayDeque<Location> recent = new ArrayDeque<>();
    private final Random rnd = new Random();

    public Rtp(SantikCore pl) { this.pl = pl; }

    public void teleport(Player p) { attempt(p, 0); }

    private void attempt(Player p, int n) {
        World w = pl.worlds().anarchy();
        int max = pl.getConfig().getInt("rtp.max-attempts", 12);
        if (w == null || !p.isOnline()) return;
        if (n >= max) {
            pl.social().clearCooldown(p.getUniqueId(), "rtp");
            p.sendMessage(pl.plain("Не удалось найти безопасное место. Попробуй ещё раз."));
            return;
        }
        double min = pl.getConfig().getDouble("rtp.min-distance", 600);
        double mx = Math.min(pl.getConfig().getDouble("rtp.max-distance", 4800), pl.getConfig().getInt("rtp.border-radius", 5000) - 100);
        double ang = rnd.nextDouble() * Math.PI * 2, dist = min + rnd.nextDouble() * Math.max(1, mx - min);
        int x = (int) Math.round(Math.cos(ang) * dist), z = (int) Math.round(Math.sin(ang) * dist);
        if (n == 0) p.sendMessage(pl.plain("Ищу безопасное место..."));
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(ch -> Bukkit.getScheduler().runTask(pl, () -> {
            if (!p.isOnline()) return;
            Location loc = check(w, x, z);
            if (loc == null) { attempt(p, n + 1); return; }
            recent.addLast(loc);
            while (recent.size() > 30) recent.pollFirst();
            p.teleportAsync(loc).thenAccept(ok -> {
                if (ok) { p.sendMessage(pl.plain("Ты в новом месте: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ())); }
            });
        }));
    }

    private Location check(World w, int x, int z) {
        if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) return null;
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y < w.getMinHeight() + 5 || y > w.getMaxHeight() - 10) return null;
        Block ground = w.getBlockAt(x, y, z);
        Material gm = ground.getType();
        if (!gm.isSolid() || BAD.contains(gm) || ground.isLiquid()) return null;
        Block feet = w.getBlockAt(x, y + 1, z), head = w.getBlockAt(x, y + 2, z);
        if (!feet.getType().isAir() || !head.getType().isAir()) return null;
        String biome = w.getBiome(x, y, z).getKey().getKey();
        if (biome.contains("ocean") || biome.contains("river")) return null;
        Location loc = new Location(w, x + 0.5, y + 1, z + 0.5);
        if (pl.worlds().inAnarchySpawn(loc)) return null;
        double gap = pl.getConfig().getDouble("rtp.min-gap-between-points", 150);
        for (Location r : recent) if (Math.hypot(r.getX() - loc.getX(), r.getZ() - loc.getZ()) < gap) return null;
        return loc;
    }
}
