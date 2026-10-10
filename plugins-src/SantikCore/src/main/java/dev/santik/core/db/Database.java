package dev.santik.core.db;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** SQLite. Всё синхронизировано на одном соединении; транзакции вложенные-безопасные. */
public final class Database {
    public interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }
    public interface TxBody<T> { T run() throws Exception; }

    private final Connection conn;
    private int depth = 0;

    public Database(File file) throws Exception {
        file.getParentFile().mkdirs();
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=FULL");
            st.execute("CREATE TABLE IF NOT EXISTS users(uuid TEXT PRIMARY KEY, name TEXT NOT NULL, hash TEXT NOT NULL, reg_ip TEXT, reg_time INTEGER NOT NULL, last_login INTEGER)");
            st.execute("CREATE TABLE IF NOT EXISTS balances(uuid TEXT PRIMARY KEY, bal INTEGER NOT NULL CHECK(bal>=0))");
            st.execute("CREATE TABLE IF NOT EXISTS tx_log(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, kind TEXT NOT NULL, from_uuid TEXT, to_uuid TEXT, amount INTEGER NOT NULL, note TEXT)");
            st.execute("CREATE TABLE IF NOT EXISTS homes(uuid TEXT NOT NULL, name TEXT NOT NULL, world TEXT NOT NULL, x REAL, y REAL, z REAL, yaw REAL, pitch REAL, PRIMARY KEY(uuid,name))");
            st.execute("CREATE TABLE IF NOT EXISTS positions(uuid TEXT PRIMARY KEY, world TEXT NOT NULL, x REAL, y REAL, z REAL, yaw REAL, pitch REAL)");
            st.execute("CREATE TABLE IF NOT EXISTS admins(uuid TEXT PRIMARY KEY, name TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS punishments(id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, reason TEXT, by_name TEXT, created INTEGER NOT NULL, expires INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1)");
            st.execute("CREATE TABLE IF NOT EXISTS admin_log(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, actor TEXT, action TEXT, detail TEXT)");
            st.execute("CREATE TABLE IF NOT EXISTS sell_log(uuid TEXT NOT NULL, item TEXT NOT NULL, ts INTEGER NOT NULL, amount INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS tips(uuid TEXT NOT NULL, key TEXT NOT NULL, PRIMARY KEY(uuid,key))");
            st.execute("CREATE TABLE IF NOT EXISTS ignores(uuid TEXT NOT NULL, target TEXT NOT NULL, PRIMARY KEY(uuid,target))");
            st.execute("CREATE TABLE IF NOT EXISTS auction(id INTEGER PRIMARY KEY AUTOINCREMENT, seller TEXT NOT NULL, seller_name TEXT NOT NULL, item TEXT NOT NULL, material TEXT NOT NULL, amount INTEGER NOT NULL, cat TEXT NOT NULL, search TEXT NOT NULL, price INTEGER NOT NULL, created INTEGER NOT NULL, expires INTEGER NOT NULL, status TEXT NOT NULL, buyer TEXT, buyer_name TEXT, sold_at INTEGER, fee INTEGER, seller_notified INTEGER NOT NULL DEFAULT 0)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_auc ON auction(status, expires)");
        }
    }

    private static void bind(PreparedStatement ps, Object[] p) throws SQLException {
        for (int i = 0; i < p.length; i++) ps.setObject(i + 1, p[i]);
    }

    public synchronized int update(String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) { bind(ps, p); return ps.executeUpdate(); }
    }

    public synchronized <T> List<T> query(String sql, RowMapper<T> m, Object... p) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(m.map(rs));
                return out;
            }
        }
    }

    public synchronized <T> T one(String sql, RowMapper<T> m, Object... p) throws SQLException {
        List<T> l = query(sql, m, p);
        return l.isEmpty() ? null : l.get(0);
    }

    public synchronized <T> T tx(TxBody<T> body) throws Exception {
        if (depth > 0) return body.run();
        depth++;
        conn.setAutoCommit(false);
        try {
            T r = body.run();
            conn.commit();
            return r;
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            depth--;
            conn.setAutoCommit(true);
        }
    }

    // ---- auth helpers ----
    public String getHash(UUID id) throws SQLException {
        return one("SELECT hash FROM users WHERE uuid=?", rs -> rs.getString(1), id.toString());
    }

    public boolean register(UUID id, String name, String hash, String ip) throws SQLException {
        long now = System.currentTimeMillis();
        return update("INSERT OR IGNORE INTO users(uuid,name,hash,reg_ip,reg_time,last_login) VALUES(?,?,?,?,?,?)",
                id.toString(), name, hash, ip, now, now) == 1;
    }

    public void touchLogin(UUID id) throws SQLException {
        update("UPDATE users SET last_login=? WHERE uuid=?", System.currentTimeMillis(), id.toString());
    }

    public long regTime(UUID id) throws SQLException {
        Long v = one("SELECT reg_time FROM users WHERE uuid=?", rs -> rs.getLong(1), id.toString());
        return v == null ? System.currentTimeMillis() : v;
    }

    public synchronized void backupTo(File dest) throws SQLException {
        dest.getParentFile().mkdirs();
        if (dest.exists()) dest.delete();
        try (Statement st = conn.createStatement()) {
            st.execute("VACUUM INTO '" + dest.getAbsolutePath().replace("'", "''") + "'");
        }
    }

    public synchronized void close() {
        try { conn.close(); } catch (SQLException ignored) { }
    }
}
