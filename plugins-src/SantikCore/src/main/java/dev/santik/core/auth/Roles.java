package dev.santik.core.auth;

import dev.santik.core.SantikCore;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Роли по UUID. Владелец — один, задаётся в config.yml (owner.uuid). Админы — в БД. */
public final class Roles {
    private final SantikCore pl;
    private final Set<UUID> admins = ConcurrentHashMap.newKeySet();

    public Roles(SantikCore pl) {
        this.pl = pl;
        try {
            pl.db().query("SELECT uuid FROM admins", rs -> rs.getString(1))
                    .forEach(s -> admins.add(UUID.fromString(s)));
        } catch (Exception e) { pl.getLogger().severe("Не удалось загрузить админов: " + e); }
    }

    public boolean isOwner(UUID id) {
        String u = pl.getConfig().getString("owner.uuid", "");
        return !u.isEmpty() && u.equalsIgnoreCase(id.toString());
    }

    public Role of(UUID id) {
        if (isOwner(id)) return Role.OWNER;
        return admins.contains(id) ? Role.ADMIN : Role.PLAYER;
    }

    public Role of(Player p) { return of(p.getUniqueId()); }

    /** Вызывается после успешного входа: первый вошедший с ником владельца закрепляет UUID. */
    public void claimOwnerIfNeeded(Player p) {
        String u = pl.getConfig().getString("owner.uuid", "");
        if (u.isEmpty() && p.getName().equalsIgnoreCase(pl.getConfig().getString("owner.name", ""))) {
            pl.getConfig().set("owner.uuid", p.getUniqueId().toString());
            pl.saveConfig();
            pl.getLogger().warning("Владелец закреплён: " + p.getName() + " / " + p.getUniqueId());
            if (!org.bukkit.Bukkit.getOnlineMode())
                pl.getLogger().warning("ВНИМАНИЕ: online-mode=false — UUID зависит от ника, возможна подмена. Используйте online-mode=true.");
        }
    }

    public boolean addAdmin(UUID id, String name) throws Exception {
        if (isOwner(id)) return false;
        pl.db().update("INSERT OR REPLACE INTO admins(uuid,name) VALUES(?,?)", id.toString(), name);
        admins.add(id);
        return true;
    }

    public boolean removeAdmin(UUID id) throws Exception {
        pl.db().update("DELETE FROM admins WHERE uuid=?", id.toString());
        return admins.remove(id);
    }

    public Set<UUID> admins() { return admins; }
}
