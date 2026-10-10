package dev.santik.core.shop;

import dev.santik.core.SantikCore;
import dev.santik.core.eco.Economy;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public final class ShopManager {
    public record Item(Material mat, String ru, String cat, long sell, long buy, int limit, boolean auction, String note) { }

    private final SantikCore pl;
    private final Map<Material, Item> items = new LinkedHashMap<>();
    private final Map<String, List<Item>> byCat = new LinkedHashMap<>();
    private final Map<String, String> catNames = new LinkedHashMap<>();
    private final Map<String, Material> catIcons = new LinkedHashMap<>();

    public ShopManager(SantikCore pl) { this.pl = pl; }

    public void load() {
        items.clear(); byCat.clear(); catNames.clear(); catIcons.clear();
        File f = new File(pl.getDataFolder(), "shop.yml");
        if (!f.exists()) pl.saveResource("shop.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection cs = y.getConfigurationSection("categories");
        if (cs != null) for (String k : cs.getKeys(false)) {
            catNames.put(k, cs.getString(k + ".name", k));
            Material ic = Material.matchMaterial(cs.getString(k + ".icon", "CHEST"));
            catIcons.put(k, ic == null ? Material.CHEST : ic);
            byCat.put(k, new ArrayList<>());
        }
        ConfigurationSection is = y.getConfigurationSection("items");
        if (is != null) for (String id : is.getKeys(false)) {
            Material m = Material.matchMaterial(id);
            if (m == null) { pl.getLogger().warning("shop.yml: неизвестный предмет " + id); continue; }
            ConfigurationSection s = is.getConfigurationSection(id);
            String cat = s.getString("category", "other");
            Item it = new Item(m, s.getString("ru", id), cat, Economy.toCents(s.getDouble("sell", 0)),
                    Economy.toCents(s.getDouble("buy", 0)), s.getInt("limit", 64), s.getBoolean("auction", true), s.getString("note", ""));
            items.put(m, it);
            byCat.computeIfAbsent(cat, k -> new ArrayList<>()).add(it);
        }
        pl.getLogger().info("Магазин: загружено предметов — " + items.size());
    }

    public Item get(Material m) { return items.get(m); }
    public List<Item> inCat(String c) { return byCat.getOrDefault(c, List.of()); }
    public Map<String, String> categories() { return catNames; }
    public Material icon(String c) { return catIcons.getOrDefault(c, Material.CHEST); }

    /** Сколько штук игрок продал магазину за последний час (по материалам). */
    public Map<Material, Integer> soldLastHour(UUID id) {
        Map<Material, Integer> out = new HashMap<>();
        try {
            long since = System.currentTimeMillis() - 3_600_000L;
            pl.db().update("DELETE FROM sell_log WHERE ts<?", since);
            for (Object[] r : pl.db().query("SELECT item, SUM(amount) FROM sell_log WHERE uuid=? AND ts>=? GROUP BY item",
                    rs -> new Object[]{rs.getString(1), rs.getInt(2)}, id.toString(), since)) {
                Material m = Material.matchMaterial((String) r[0]);
                if (m != null) out.put(m, (Integer) r[1]);
            }
        } catch (Exception e) { pl.getLogger().warning("soldLastHour: " + e); }
        return out;
    }

    public void logSale(UUID id, Material m, int amount) {
        try { pl.db().update("INSERT INTO sell_log(uuid,item,ts,amount) VALUES(?,?,?,?)", id.toString(), m.name(), System.currentTimeMillis(), amount); }
        catch (Exception e) { pl.getLogger().warning("logSale: " + e); }
    }
}
