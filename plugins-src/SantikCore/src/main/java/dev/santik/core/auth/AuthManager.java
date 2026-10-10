package dev.santik.core.auth;

import dev.santik.core.SantikCore;
import dev.santik.core.db.Database;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthManager {
    private final SantikCore plugin;
    private final Database db;
    private final PasswordHasher hasher;
    private final Set<UUID> authed = ConcurrentHashMap.newKeySet();
    private final Set<UUID> captcha = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> ipLocks = new ConcurrentHashMap<>();
    private final Map<String, ArrayDeque<Long>> conns = new ConcurrentHashMap<>();

    public AuthManager(SantikCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        this.hasher = new PasswordHasher(plugin.getConfig().getInt("auth.pbkdf2-iterations", 210000));
    }

    public boolean isAuthed(UUID id) { return authed.contains(id); }
    public void setAuthed(UUID id) { authed.add(id); failures.remove(id); }
    public boolean captchaDone(UUID id) { return captcha.contains(id); }
    public void setCaptchaDone(UUID id) { captcha.add(id); }
    public void forget(UUID id) { authed.remove(id); captcha.remove(id); failures.remove(id); }

    public Database db() { return db; }
    public PasswordHasher hasher() { return hasher; }

    public int addFailure(UUID id) { return failures.merge(id, 1, Integer::sum); }

    public void lockIp(String ip) { lockIp(ip, plugin.getConfig().getLong("auth.ip-lock-minutes", 5)); }

    public void lockIp(String ip, long minutes) {
        ipLocks.put(ip, System.currentTimeMillis() + minutes * 60_000L);
    }

    public boolean isIpLocked(String ip) {
        Long until = ipLocks.get(ip);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) { ipLocks.remove(ip); return false; }
        return true;
    }

    /** Защита от спама подключениями: true — подключение допустимо. */
    public boolean connectionAllowed(String ip) {
        int max = plugin.getConfig().getInt("auth.max-connections-per-ip-10s", 5);
        long now = System.currentTimeMillis();
        ArrayDeque<Long> q = conns.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 10_000L) q.pollFirst();
            q.addLast(now);
            if (q.size() > max) { lockIp(ip, 1); return false; }
        }
        return true;
    }
}
