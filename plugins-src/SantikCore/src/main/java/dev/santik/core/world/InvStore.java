package dev.santik.core.world;

import dev.santik.core.SantikCore;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * Инвентарь анархии и лобби разделены. Пока игрок в лобби/входе, его реальный инвентарь
 * лежит в БД (таблица inv), а в руках — временный (компас).
 */
public final class InvStore {
    private final SantikCore pl;

    public InvStore(SantikCore pl) {
        this.pl = pl;
        try {
            pl.db().update("CREATE TABLE IF NOT EXISTS inv(uuid TEXT PRIMARY KEY, data TEXT NOT NULL, lobby INTEGER NOT NULL)");
        } catch (Exception e) { pl.getLogger().severe("inv table: " + e); }
    }

    private static String ser(ItemStack[] c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.length; i++) {
            if (i > 0) sb.append(',');
            ItemStack s = c[i];
            if (s != null && !s.getType().isAir()) sb.append(Base64.getEncoder().encodeToString(s.serializeAsBytes()));
        }
        return sb.toString();
    }

    private static ItemStack[] des(String d, int size) {
        ItemStack[] out = new ItemStack[size];
        String[] p = d.split(",", -1);
        for (int i = 0; i < p.length && i < size; i++) {
            if (!p[i].isEmpty()) out[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(p[i]));
        }
        return out;
    }

    private boolean isLobbyMode(Player p) {
        try {
            Integer v = pl.db().one("SELECT lobby FROM inv WHERE uuid=?", rs -> rs.getInt(1), p.getUniqueId().toString());
            return v != null && v == 1;
        } catch (Exception e) { return false; }
    }

    /** Перевести в режим лобби: сохранить настоящий инвентарь (если ещё не сохранён) и очистить. */
    public boolean toLobby(Player p) {
        try {
            if (!isLobbyMode(p)) {
                pl.db().update("INSERT OR REPLACE INTO inv(uuid,data,lobby) VALUES(?,?,1)",
                        p.getUniqueId().toString(), ser(p.getInventory().getContents()));
            }
            p.getInventory().clear();
            return true;
        } catch (Exception e) {
            pl.getLogger().severe("InvStore.toLobby: " + e);
            return false;
        }
    }

    /** Вернуть настоящий инвентарь (вход в анархию). */
    public boolean toAnarchy(Player p) {
        try {
            if (!isLobbyMode(p)) return true;
            String d = pl.db().one("SELECT data FROM inv WHERE uuid=?", rs -> rs.getString(1), p.getUniqueId().toString());
            p.getInventory().clear();
            if (d != null) p.getInventory().setContents(des(d, p.getInventory().getContents().length));
            pl.db().update("UPDATE inv SET lobby=0 WHERE uuid=?", p.getUniqueId().toString());
            return true;
        } catch (Exception e) {
            pl.getLogger().severe("InvStore.toAnarchy: " + e);
            return false;
        }
    }
}
