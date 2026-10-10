package dev.santik.core.eco;

import dev.santik.core.SantikCore;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Баланс хранится в сотых долях SC (long). Все операции — в транзакциях БД. */
public final class Economy {
    private final SantikCore pl;
    private final Map<UUID, Long> cache = new ConcurrentHashMap<>();

    public Economy(SantikCore pl) { this.pl = pl; }

    public static String fmt(long cents) {
        return String.format(Locale.US, "%,d.%02d", cents / 100, Math.abs(cents % 100)).replace(',', ' ');
    }

    public static long toCents(double sc) { return Math.round(sc * 100.0); }

    /** Разбор суммы игрока. null — некорректно. Максимум 2 знака после запятой. */
    public static Long parse(String s) {
        try {
            if (!s.matches("^\\d{1,12}([.,]\\d{1,2})?$")) return null;
            double d = Double.parseDouble(s.replace(',', '.'));
            long c = toCents(d);
            return c > 0 ? c : null;
        } catch (Exception e) { return null; }
    }

    private void ensure(UUID id) throws Exception {
        long start = toCents(pl.getConfig().getDouble("economy.start-balance", 100));
        pl.db().update("INSERT OR IGNORE INTO balances(uuid,bal) VALUES(?,?)", id.toString(), start);
    }

    private long load(UUID id) {
        try {
            ensure(id);
            Long v = pl.db().one("SELECT bal FROM balances WHERE uuid=?", rs -> rs.getLong(1), id.toString());
            return v == null ? 0 : v;
        } catch (Exception e) { pl.getLogger().severe("Economy.load: " + e); return 0; }
    }

    public long balance(UUID id) { return cache.computeIfAbsent(id, this::load); }
    public void refresh(UUID id) { cache.put(id, load(id)); }
    public void forget(UUID id) { cache.remove(id); }

    private void log(String kind, UUID from, UUID to, long amt, String note) throws Exception {
        pl.db().update("INSERT INTO tx_log(ts,kind,from_uuid,to_uuid,amount,note) VALUES(?,?,?,?,?,?)",
                System.currentTimeMillis(), kind, from == null ? null : from.toString(), to == null ? null : to.toString(), amt, note);
    }

    public boolean add(UUID id, long cents, String kind, String note) {
        if (cents <= 0) return false;
        try {
            pl.db().tx(() -> { ensure(id); pl.db().update("UPDATE balances SET bal=bal+? WHERE uuid=?", cents, id.toString()); log(kind, null, id, cents, note); return null; });
            refresh(id);
            return true;
        } catch (Exception e) { pl.getLogger().severe("Economy.add: " + e); return false; }
    }

    public boolean take(UUID id, long cents, String kind, String note) {
        if (cents <= 0) return false;
        try {
            boolean ok = pl.db().tx(() -> {
                ensure(id);
                int n = pl.db().update("UPDATE balances SET bal=bal-? WHERE uuid=? AND bal>=?", cents, id.toString(), cents);
                if (n != 1) return false;
                log(kind, id, null, cents, note);
                return true;
            });
            refresh(id);
            return ok;
        } catch (Exception e) { pl.getLogger().severe("Economy.take: " + e); return false; }
    }

    public boolean transfer(UUID from, UUID to, long cents, String kind, String note) {
        if (cents <= 0 || from.equals(to)) return false;
        try {
            boolean ok = pl.db().tx(() -> {
                ensure(from); ensure(to);
                int n = pl.db().update("UPDATE balances SET bal=bal-? WHERE uuid=? AND bal>=?", cents, from.toString(), cents);
                if (n != 1) return false;
                pl.db().update("UPDATE balances SET bal=bal+? WHERE uuid=?", cents, to.toString());
                log(kind, from, to, cents, note);
                return true;
            });
            refresh(from); refresh(to);
            return ok;
        } catch (Exception e) { pl.getLogger().severe("Economy.transfer: " + e); return false; }
    }

    public boolean set(UUID id, long cents, String actor) {
        if (cents < 0) return false;
        try {
            pl.db().tx(() -> { ensure(id); pl.db().update("UPDATE balances SET bal=? WHERE uuid=?", cents, id.toString()); log("ADMIN_SET", null, id, cents, actor); return null; });
            refresh(id);
            return true;
        } catch (Exception e) { return false; }
    }
}
