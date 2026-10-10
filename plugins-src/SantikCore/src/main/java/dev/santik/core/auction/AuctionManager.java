package dev.santik.core.auction;

import dev.santik.core.SantikCore;
import dev.santik.core.eco.Economy;
import dev.santik.core.util.Cats;
import dev.santik.core.util.CompassItem;
import dev.santik.core.util.RuNames;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/** Аукцион игроков. Деньги и статус лота меняются в одной SQL-транзакции; предмет выдаётся после неё. */
public final class AuctionManager {
    public record Lot(long id, String seller, String sellerName, ItemStack item, long price, long expires, String status) { }
    public record Page(int total, List<Lot> lots) { }

    private static final Set<String> BANNED = Set.of("BARRIER", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "LIGHT",
            "DEBUG_STICK", "KNOWLEDGE_BOOK", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "BEDROCK", "REINFORCED_DEEPSLATE",
            "END_PORTAL_FRAME", "COMMAND_BLOCK_MINECART", "PLAYER_HEAD_UNKNOWN");

    private final SantikCore pl;

    public AuctionManager(SantikCore pl) { this.pl = pl; }

    private static String ser(ItemStack it) { return Base64.getEncoder().encodeToString(it.serializeAsBytes()); }
    private static ItemStack des(String s) { return ItemStack.deserializeBytes(Base64.getDecoder().decode(s)); }

    private static Lot map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Lot(rs.getLong(1), rs.getString(2), rs.getString(3), des(rs.getString(4)), rs.getLong(5), rs.getLong(6), rs.getString(7));
    }

    private static final String COLS = "id,seller,seller_name,item,price,expires,status";

    public static boolean fits(Inventory inv, ItemStack it) {
        int remaining = it.getAmount();
        for (ItemStack s : inv.getStorageContents()) {
            if (s == null || s.getType().isAir()) remaining -= it.getMaxStackSize();
            else if (s.isSimilar(it)) remaining -= Math.max(0, s.getMaxStackSize() - s.getAmount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    /** null — предмет можно выставить; иначе текст ошибки. */
    public String validate(ItemStack it) {
        if (it == null || it.getType().isAir() || it.getAmount() <= 0) return "В руке нет предмета.";
        Material m = it.getType();
        String n = m.name();
        if (BANNED.contains(n) || n.contains("COMMAND_BLOCK") || n.endsWith("_SPAWN_EGG") || n.startsWith("LEGACY_"))
            return "Этот предмет нельзя выставить на аукцион.";
        if (CompassItem.is(it) || CompassItem.isCaptchaMap(it)) return "Этот предмет нельзя выставить на аукцион.";
        var shopItem = pl.shop().get(m);
        if (shopItem != null && !shopItem.auction()) return "Этот предмет нельзя выставить на аукцион.";
        if (it.hasItemMeta()) {
            ItemMeta meta = it.getItemMeta();
            if (meta.hasAttributeModifiers()) return "Предметы с изменёнными атрибутами нельзя продавать.";
            for (Map.Entry<Enchantment, Integer> e : meta.getEnchants().entrySet())
                if (e.getValue() > e.getKey().getMaxLevel()) return "Предмет с нереальными зачарованиями нельзя продавать.";
        }
        return null;
    }

    public long minPrice(ItemStack it) {
        long hard = Economy.toCents(pl.getConfig().getDouble("auction.hard-min-price", 1));
        var si = pl.shop().get(it.getType());
        if (si == null || si.sell() <= 0) return hard;
        double ratio = pl.getConfig().getDouble("auction.min-price-ratio-of-shop-sell", 0.5);
        return Math.max(hard, Math.round(si.sell() * it.getAmount() * ratio));
    }

    public long maxPrice() { return Economy.toCents(pl.getConfig().getDouble("auction.max-price", 10_000_000)); }

    public boolean accountOldEnough(UUID id) {
        try {
            long mins = pl.getConfig().getLong("economy.new-account-minutes", 30);
            return System.currentTimeMillis() - pl.db().regTime(id) >= mins * 60_000L;
        } catch (Exception e) { return false; }
    }

    public String create(Player p, long price) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        String err = validate(hand);
        if (err != null) return err;
        if (!accountOldEnough(p.getUniqueId())) return "Новые аккаунты не могут продавать на аукционе первые "
                + pl.getConfig().getLong("economy.new-account-minutes", 30) + " мин.";
        long min = minPrice(hand), max = maxPrice();
        if (price < min) return "Минимальная цена для этого предмета: " + Economy.fmt(min) + " SC.";
        if (price > max) return "Максимальная цена: " + Economy.fmt(max) + " SC.";
        try {
            Integer active = pl.db().one("SELECT COUNT(*) FROM auction WHERE seller=? AND status='ACTIVE'", rs -> rs.getInt(1), p.getUniqueId().toString());
            int maxLots = pl.getConfig().getInt("auction.max-active-lots", 10);
            if (active != null && active >= maxLots) return "Достигнут лимит активных лотов: " + maxLots + ".";
        } catch (Exception e) { return "Ошибка базы данных."; }
        ItemStack copy = hand.clone();
        p.getInventory().setItemInMainHand(null);
        try {
            long now = System.currentTimeMillis();
            long exp = now + pl.getConfig().getLong("auction.duration-hours", 48) * 3_600_000L;
            pl.db().update("INSERT INTO auction(seller,seller_name,item,material,amount,cat,search,price,created,expires,status) VALUES(?,?,?,?,?,?,?,?,?,?,'ACTIVE')",
                    p.getUniqueId().toString(), p.getName(), ser(copy), copy.getType().name(), copy.getAmount(),
                    Cats.of(copy.getType()), RuNames.haystack(pl, copy.getType()), price, now, exp);
            return null;
        } catch (Exception e) {
            p.getInventory().setItemInMainHand(copy);
            pl.getLogger().severe("auction.create: " + e);
            return "Ошибка базы данных. Предмет возвращён.";
        }
    }

    public Lot lot(long id) {
        try { return pl.db().one("SELECT " + COLS + " FROM auction WHERE id=?", AuctionManager::map, id); }
        catch (Exception e) { return null; }
    }

    public Page browse(String cat, String query, String sort, int page, int per) {
        StringBuilder where = new StringBuilder(" WHERE status='ACTIVE' AND expires>?");
        List<Object> params = new ArrayList<>();
        params.add(System.currentTimeMillis());
        if (cat != null && !cat.equals("ALL")) { where.append(" AND cat=?"); params.add(cat); }
        if (query != null && !query.isBlank()) {
            for (String stem : RuNames.stems(query)) { where.append(" AND search LIKE ?"); params.add("%" + stem + "%"); }
        }
        String order = switch (sort) {
            case "price_desc" -> "price DESC, id ASC";
            case "new" -> "created DESC";
            case "old" -> "created ASC";
            default -> "price ASC, id ASC";
        };
        try {
            Integer total = pl.db().one("SELECT COUNT(*) FROM auction" + where, rs -> rs.getInt(1), params.toArray());
            List<Object> p2 = new ArrayList<>(params);
            p2.add(per); p2.add(page * per);
            List<Lot> lots = pl.db().query("SELECT " + COLS + " FROM auction" + where + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                    AuctionManager::map, p2.toArray());
            return new Page(total == null ? 0 : total, lots);
        } catch (Exception e) {
            pl.getLogger().warning("auction.browse: " + e);
            return new Page(0, List.of());
        }
    }

    public List<Lot> mine(UUID id) {
        try {
            return pl.db().query("SELECT " + COLS + " FROM auction WHERE seller=? AND status IN ('ACTIVE','EXPIRED') ORDER BY created DESC LIMIT 45",
                    AuctionManager::map, id.toString());
        } catch (Exception e) { return List.of(); }
    }

    public int sellerSales(String uuid) {
        try {
            Integer n = pl.db().one("SELECT COUNT(*) FROM auction WHERE seller=? AND status IN ('SOLD','DONE')", rs -> rs.getInt(1), uuid);
            return n == null ? 0 : n;
        } catch (Exception e) { return 0; }
    }

    /** null — успех, иначе текст ошибки. */
    public String buy(Player p, long id) {
        UUID buyer = p.getUniqueId();
        Lot lot = lot(id);
        if (lot == null || !lot.status().equals("ACTIVE")) return "Лот уже недоступен.";
        if (lot.seller().equals(buyer.toString())) return "Нельзя купить собственный лот.";
        if (lot.expires() < System.currentTimeMillis()) return "Срок лота истёк.";
        if (!fits(p.getInventory(), lot.item())) return "Недостаточно места в инвентаре.";
        if (pl.eco().balance(buyer) < lot.price()) return "Недостаточно SC. Нужно: " + Economy.fmt(lot.price()) + ".";
        UUID seller = UUID.fromString(lot.seller());
        long fee = lot.price() * pl.getConfig().getInt("auction.fee-percent", 5) / 100;
        try {
            boolean ok = pl.db().tx(() -> {
                int n = pl.db().update("UPDATE auction SET status='SOLD', buyer=?, buyer_name=?, sold_at=?, fee=? WHERE id=? AND status='ACTIVE' AND expires>?",
                        buyer.toString(), p.getName(), System.currentTimeMillis(), fee, id, System.currentTimeMillis());
                if (n != 1) return false;
                if (!pl.eco().take(buyer, lot.price(), "AUCTION_BUY", "lot " + id)) throw new IllegalStateException("funds");
                if (lot.price() - fee > 0 && !pl.eco().add(seller, lot.price() - fee, "AUCTION_SELL", "lot " + id)) throw new IllegalStateException("credit");
                return true;
            });
            pl.eco().refresh(buyer); pl.eco().refresh(seller);
            if (!ok) return "Лот уже куплен или недоступен.";
        } catch (Exception e) {
            pl.eco().refresh(buyer); pl.eco().refresh(seller);
            return "Покупка не удалась (ничего не списано).";
        }
        // выдача предмета после фиксации транзакции
        p.getInventory().addItem(lot.item().clone());
        try { pl.db().update("UPDATE auction SET status='DONE' WHERE id=? AND status='SOLD'", id); } catch (Exception ignored) { }
        Player sp = Bukkit.getPlayer(seller);
        if (sp != null) {
            sp.sendMessage(pl.plain("Твой лот #" + id + " куплен игроком " + p.getName() + ". Зачислено: " + Economy.fmt(lot.price() - fee) + " SC (комиссия " + Economy.fmt(fee) + " SC)."));
            try { pl.db().update("UPDATE auction SET seller_notified=1 WHERE id=?", id); } catch (Exception ignored) { }
        }
        return null;
    }

    public String cancel(Player p, long id) {
        try {
            int n = pl.db().update("UPDATE auction SET status='EXPIRED' WHERE id=? AND seller=? AND status='ACTIVE'", id, p.getUniqueId().toString());
            if (n != 1) return "Лот не найден или уже продан.";
            deliver(p);
            return null;
        } catch (Exception e) { return "Ошибка базы данных."; }
    }

    public String adminRemove(long id) {
        try {
            int n = pl.db().update("UPDATE auction SET status='EXPIRED' WHERE id=? AND status='ACTIVE'", id);
            if (n != 1) return "Активный лот #" + id + " не найден.";
            Lot l = lot(id);
            if (l != null) { Player sp = Bukkit.getPlayer(UUID.fromString(l.seller())); if (sp != null) deliver(sp); }
            return null;
        } catch (Exception e) { return "Ошибка базы данных."; }
    }

    /** Выдаёт ожидающие предметы (купленные и возвращённые) и сообщает о продажах. Только в мире анархии. */
    public int deliver(Player p) {
        if (!pl.worlds().isAnarchy(p.getWorld())) return 0;
        UUID me = p.getUniqueId();
        int given = 0, waiting = 0;
        try {
            List<Lot> rows = pl.db().query("SELECT " + COLS + " FROM auction WHERE (status='SOLD' AND buyer=?) OR (status='EXPIRED' AND seller=?)",
                    AuctionManager::map, me.toString(), me.toString());
            for (Lot l : rows) {
                if (!fits(p.getInventory(), l.item())) { waiting++; continue; }
                p.getInventory().addItem(l.item().clone());
                pl.db().update("UPDATE auction SET status='DONE' WHERE id=? AND status IN ('SOLD','EXPIRED')", l.id());
                given++;
            }
            long[] sold = pl.db().one("SELECT COUNT(*), COALESCE(SUM(price-fee),0) FROM auction WHERE seller=? AND status IN ('SOLD','DONE') AND seller_notified=0 AND fee IS NOT NULL",
                    rs -> new long[]{rs.getLong(1), rs.getLong(2)}, me.toString());
            if (sold != null && sold[0] > 0) {
                p.sendMessage(pl.plain("Пока тебя не было, продано лотов: " + sold[0] + ". Зачислено: " + Economy.fmt(sold[1]) + " SC."));
                pl.db().update("UPDATE auction SET seller_notified=1 WHERE seller=? AND seller_notified=0", me.toString());
            }
        } catch (Exception e) { pl.getLogger().warning("auction.deliver: " + e); }
        if (given > 0) p.sendMessage(pl.plain("Получено предметов с аукциона: " + given + "."));
        if (waiting > 0) p.sendMessage(pl.plain("Предметов ждут места в инвентаре: " + waiting + ". Освободи слоты и введи /ah claim."));
        return given;
    }

    public void expire() {
        try { pl.db().update("UPDATE auction SET status='EXPIRED' WHERE status='ACTIVE' AND expires<=?", System.currentTimeMillis()); }
        catch (Exception e) { pl.getLogger().warning("auction.expire: " + e); }
    }

    public List<String> history(UUID id, int limit) {
        try {
            return pl.db().query("SELECT material, amount, price, seller, sold_at FROM auction WHERE (seller=? OR buyer=?) AND status IN ('SOLD','DONE') ORDER BY sold_at DESC LIMIT ?",
                    rs -> (rs.getString(4).equals(id.toString()) ? "Продано: " : "Куплено: ") + rs.getString(1).toLowerCase() + " x" + rs.getInt(2)
                            + " за " + Economy.fmt(rs.getLong(3)) + " SC",
                    id.toString(), id.toString(), limit);
        } catch (Exception e) { return List.of(); }
    }
}
