package dev.santik.core.world;

import dev.santik.core.SantikCore;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public final class WorldsManager {
    private final SantikCore pl;
    private final File dataFile;
    private final YamlConfiguration data;

    public WorldsManager(SantikCore pl) {
        this.pl = pl;
        this.dataFile = new File(pl.getDataFolder(), "data.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public YamlConfiguration data() { return data; }
    public void saveData() { try { data.save(dataFile); } catch (IOException e) { pl.getLogger().warning("data.yml: " + e); } }

    public String anarchyName() { return pl.getConfig().getString("worlds.anarchy", "anarchy_1"); }

    public World captcha() { return Bukkit.getWorld(pl.getConfig().getString("worlds.captcha", "santik_captcha")); }
    public World auth() { return Bukkit.getWorld(pl.getConfig().getString("worlds.auth", "santik_auth")); }
    public World lobby() { return Bukkit.getWorld(pl.getConfig().getString("worlds.lobby", "santik_lobby")); }
    public World anarchy() { return Bukkit.getWorld(anarchyName()); }

    public boolean isAnarchy(World w) { return w != null && w.getName().startsWith(anarchyName()); }
    public boolean isAnarchyOverworld(World w) { return w != null && w.getName().equals(anarchyName()); }

    /** Радиус защищённого спавна в мире анархии. */
    public boolean inAnarchySpawn(Location l) {
        World w = l.getWorld();
        if (!isAnarchyOverworld(w)) return false;
        int r = pl.getConfig().getInt("protection.anarchy-spawn-radius", 50);
        return l.getX() * l.getX() + l.getZ() * l.getZ() <= (double) r * r;
    }

    public Location spawn(World w) {
        if (w == null) return null;
        String s = data.getString("spawn." + w.getName());
        if (s != null) {
            String[] p = s.split(",");
            return new Location(w, Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Float.parseFloat(p[3]), 0f);
        }
        return w.getSpawnLocation();
    }

    private void saveSpawn(Location l) {
        data.set("spawn." + l.getWorld().getName(), l.getX() + "," + l.getY() + "," + l.getZ() + "," + l.getYaw());
        saveData();
    }

    private World make(String name, World.Environment env, boolean voidGen) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        WorldCreator wc = new WorldCreator(name).environment(env);
        if (voidGen) wc.generator(new VoidGenerator()).generateStructures(false);
        return wc.createWorld();
    }

    private void lock(World w) {
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setDifficulty(Difficulty.PEACEFUL);
        w.setTime(12400);
        w.setStorm(false);
    }

    public void init() {
        var c = pl.getConfig();
        World cap = make(c.getString("worlds.captcha", "santik_captcha"), World.Environment.NORMAL, true);
        World au = make(c.getString("worlds.auth", "santik_auth"), World.Environment.NORMAL, true);
        World lo = make(c.getString("worlds.lobby", "santik_lobby"), World.Environment.NORMAL, true);
        for (World w : new World[]{cap, au, lo}) if (w != null) lock(w);

        String an = anarchyName();
        World ov = make(an, World.Environment.NORMAL, false);
        World ne = make(an + "_nether", World.Environment.NETHER, false);
        World en = make(an + "_the_end", World.Environment.THE_END, false);
        double size = 2.0 * c.getInt("rtp.border-radius", 5000);
        if (ov != null) { ov.getWorldBorder().setCenter(0, 0); ov.getWorldBorder().setSize(size); }
        if (ne != null) { ne.getWorldBorder().setCenter(0, 0); ne.getWorldBorder().setSize(size / 8.0); }
        if (en != null) { en.getWorldBorder().setCenter(0, 0); en.getWorldBorder().setSize(size); }
    }

    /** Строит недостроенные площадки (один раз; повтор — /santik build). */
    public void buildMissing(boolean force) {
        build("captcha", captcha(), force);
        build("auth", auth(), force);
        build("lobby", lobby(), force);
        build("anarchy", anarchy(), force);
    }

    public void build(String which, World w, boolean force) {
        if (w == null) return;
        if (!force && data.getBoolean("built." + which, false)) return;
        pl.getLogger().info("Строю площадку: " + which + " (может занять несколько секунд)...");
        Location sp;
        switch (which) {
            case "captcha" -> sp = LobbyBuilder.plaza(w, 0, 100, 0, 7, false, 0, 2, false, 4);
            case "auth" -> sp = LobbyBuilder.plaza(w, 0, 100, 0, 10, false, 0, 3, false, 6);
            case "lobby" -> {
                sp = LobbyBuilder.plaza(w, 0, 100, 0, 22, false, 1, 6, true, 8);
                LobbyBuilder.holo(w, new Location(w, 0.5, 106, -5.5),
                        "<#ffb7c5><bold>SANTIK</bold>\n<white>Нажми ПКМ по компасу\n<gray>и выбери режим");
            }
            default -> {
                int cy = Math.max(64, w.getHighestBlockYAt(0, 0, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES));
                sp = LobbyBuilder.plaza(w, 0, cy, 0, 45, true, 2, 12, true, 16);
                LobbyBuilder.holo(w, new Location(w, 0.5, cy + 6, -13.5),
                        "<#ffb7c5><bold>SANTIK ANARCHY</bold>\n<white>/rtp — в мир   /sethome — дом\n<white>/ah — аукцион   /bal — баланс\n<gray>Спавн защищён");
            }
        }
        saveSpawn(sp);
        data.set("built." + which, true);
        saveData();
        pl.getLogger().info("Готово: " + which);
    }
}
