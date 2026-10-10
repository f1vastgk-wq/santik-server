package dev.santik.core.world;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.util.Random;

/** Процедурная постройка сакурового плаца: тёмный камень, белый кварц, вишнёвые деревья, пруд с мостом, фонари. */
public final class LobbyBuilder {
    private static final String[][] LETTERS = {
        {".####", "#....", ".###.", "....#", "####."}, // S
        {".###.", "#...#", "#####", "#...#", "#...#"}, // A
        {"#...#", "##..#", "#.#.#", "#..##", "#...#"}, // N
        {"#####", "..#..", "..#..", "..#..", "..#.."}, // T
        {"#####", "..#..", "..#..", "..#..", "#####"}, // I
        {"#...#", "#..#.", "###..", "#..#.", "#...#"}  // K
    };

    private LobbyBuilder() { }

    private static void set(World w, int x, int y, int z, Material m) { w.getBlockAt(x, y, z).setType(m, false); }

    /** @return точка спавна (стоя лицом на север). */
    public static Location plaza(World w, int cx, int cy, int cz, int r, boolean grounded,
                                 int titleScale, int trees, boolean pond, int lanterns) {
        Random rnd = new Random(1337L + r);
        int pondR = Math.max(3, r / 6);
        int pcx = cx - (int) (r * 0.45), pcz = cz + (int) (r * 0.1);

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r) continue;
                int x = cx + dx, z = cz + dz;
                Material top;
                if (d > r - 1.6) top = Material.POLISHED_BLACKSTONE_BRICKS;
                else if (d < r * 0.18) top = Material.QUARTZ_BLOCK;
                else if (d < r * 0.18 + 1.2) top = Material.POLISHED_BLACKSTONE;
                else if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) top = Material.QUARTZ_BRICKS;
                else top = ((Math.floorDiv(dx, 2) + Math.floorDiv(dz, 2)) & 1) == 0 ? Material.DEEPSLATE_TILES : Material.DEEPSLATE_BRICKS;
                set(w, x, cy, z, top);
                if (grounded) {
                    for (int i = 1; i <= 8; i++) {
                        if (!w.getBlockAt(x, cy - i, z).getType().isSolid()) set(w, x, cy - i, z, Material.DIRT);
                    }
                    for (int i = 1; i <= 22; i++) {
                        if (!w.getBlockAt(x, cy + i, z).getType().isAir()) set(w, x, cy + i, z, Material.AIR);
                    }
                } else {
                    int layers = (int) Math.max(1, Math.min(5, (r - d) / (r / 5.0)));
                    for (int i = 1; i <= layers; i++) set(w, x, cy - i, z, Material.BLACKSTONE);
                }
                if (top == Material.DEEPSLATE_TILES || top == Material.DEEPSLATE_BRICKS) {
                    if (rnd.nextInt(100) < 5) set(w, x, cy + 1, z, Material.PINK_PETALS);
                }
            }
        }

        // пруд и мост
        if (pond) {
            for (int dx = -pondR - 1; dx <= pondR + 1; dx++) {
                for (int dz = -pondR - 1; dz <= pondR + 1; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d <= pondR) set(w, pcx + dx, cy, pcz + dz, Material.WATER);
                    else if (d <= pondR + 1.5) set(w, pcx + dx, cy, pcz + dz, Material.SMOOTH_QUARTZ);
                }
            }
            for (int dx = -pondR - 1; dx <= pondR + 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) set(w, pcx + dx, cy, pcz + dz, Material.DARK_OAK_PLANKS);
                if (Math.abs(dx) <= pondR) {
                    set(w, pcx + dx, cy + 1, pcz - 1, Material.DARK_OAK_FENCE);
                    set(w, pcx + dx, cy + 1, pcz + 1, Material.DARK_OAK_FENCE);
                }
            }
        }

        // сакуры
        for (int k = 0; k < trees; k++) {
            double a = 2 * Math.PI * k / trees + 0.35;
            double rad = r * 0.62;
            int tx = cx + (int) Math.round(Math.cos(a) * rad), tz = cz + (int) Math.round(Math.sin(a) * rad);
            if (pond && Math.hypot(tx - pcx, tz - pcz) < pondR + 5) continue;
            if (Math.abs(tx - cx) <= 2 || Math.abs(tz - cz) <= 2) tx += 4;
            for (int ox = -2; ox <= 2; ox++)
                for (int oz = -2; oz <= 2; oz++)
                    if (ox * ox + oz * oz <= 5) set(w, tx + ox, cy, tz + oz, Material.GRASS_BLOCK);
            w.generateTree(new Location(w, tx, cy + 1, tz), TreeType.CHERRY);
        }

        // фонари
        for (int k = 0; k < lanterns; k++) {
            double a = 2 * Math.PI * k / lanterns;
            int lx = cx + (int) Math.round(Math.cos(a) * (r - 2.5)), lz = cz + (int) Math.round(Math.sin(a) * (r - 2.5));
            set(w, lx, cy + 1, lz, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            set(w, lx, cy + 2, lz, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            set(w, lx, cy + 3, lz, Material.LANTERN);
        }

        // надпись SANTIK на земле
        int zOff = 0;
        if (titleScale > 0) {
            int s = titleScale;
            int width = 35 * s;
            int x0 = cx - width / 2;
            int zStart = cz - (int) (r * 0.28) - 2 * s;
            for (int x = x0 - s; x < x0 + width + s; x++)
                for (int z = zStart - s; z < zStart + 5 * s + s; z++) {
                    boolean edge = x == x0 - s || x == x0 + width + s - 1 || z == zStart - s || z == zStart + 5 * s + s - 1;
                    set(w, x, cy, z, edge ? Material.PINK_CONCRETE : Material.BLACK_CONCRETE);
                }
            for (int li = 0; li < 6; li++) {
                int lx0 = x0 + li * 6 * s;
                for (int row = 0; row < 5; row++)
                    for (int col = 0; col < 5; col++)
                        if (LETTERS[li][row].charAt(col) == '#')
                            for (int a = 0; a < s; a++)
                                for (int b = 0; b < s; b++)
                                    set(w, lx0 + col * s + a, cy, zStart + row * s + b, Material.WHITE_CONCRETE);
            }
            zOff = (int) (r * 0.28) + 3 * s;
        } else {
            zOff = 0;
        }

        Location spawn = new Location(w, cx + 0.5, cy + 1, cz + zOff + 0.5, 180f, 0f);
        w.setSpawnLocation(spawn);
        return spawn;
    }

    public static void holo(World w, Location loc, String mm) {
        for (Entity e : w.getEntitiesByClass(TextDisplay.class)) {
            if (e.getScoreboardTags().contains("santik_holo") && e.getLocation().distanceSquared(loc) < 4) e.remove();
        }
        w.spawn(loc, TextDisplay.class, td -> {
            td.text(MiniMessage.miniMessage().deserialize(mm));
            td.setBillboard(Display.Billboard.CENTER);
            td.addScoreboardTag("santik_holo");
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.setShadowed(true);
        });
    }
}
