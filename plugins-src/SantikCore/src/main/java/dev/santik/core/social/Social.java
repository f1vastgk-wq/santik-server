package dev.santik.core.social;

import dev.santik.core.SantikCore;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Social {
    public record TpaReq(UUID from, long expires) { }

    private final SantikCore pl;
    private final Map<UUID, Set<UUID>> ignores = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    public final Map<UUID, UUID> lastMsg = new ConcurrentHashMap<>();
    public final Map<UUID, TpaReq> tpa = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastChat = new ConcurrentHashMap<>();
    private final Map<UUID, String[]> lastText = new ConcurrentHashMap<>();
    private final Map<UUID, ArrayDeque<Long>> cmdTimes = new ConcurrentHashMap<>();

    public Social(SantikCore pl) { this.pl = pl; }

    private Set<UUID> load(UUID id) {
        Set<UUID> s = ConcurrentHashMap.newKeySet();
        try {
            for (String t : pl.db().query("SELECT target FROM ignores WHERE uuid=?", rs -> rs.getString(1), id.toString())) s.add(UUID.fromString(t));
        } catch (Exception e) { pl.getLogger().warning("ignores: " + e); }
        return s;
    }

    /** who игнорирует target? */
    public boolean ignores(UUID who, UUID target) { return ignores.computeIfAbsent(who, this::load).contains(target); }

    /** @return true, если теперь игнорируется. */
    public boolean toggleIgnore(UUID who, UUID target) {
        Set<UUID> s = ignores.computeIfAbsent(who, this::load);
        try {
            if (s.remove(target)) { pl.db().update("DELETE FROM ignores WHERE uuid=? AND target=?", who.toString(), target.toString()); return false; }
            s.add(target);
            pl.db().update("INSERT OR IGNORE INTO ignores(uuid,target) VALUES(?,?)", who.toString(), target.toString());
            return true;
        } catch (Exception e) { return s.contains(target); }
    }

    /** 0 — можно (и время запоминается); иначе — сколько секунд осталось. */
    public long cooldownLeft(UUID id, String key, long seconds) {
        long now = System.currentTimeMillis();
        String k = id + ":" + key;
        Long until = cooldowns.get(k);
        if (until != null && until > now) return (until - now + 999) / 1000;
        cooldowns.put(k, now + seconds * 1000L);
        return 0;
    }

    public void clearCooldown(UUID id, String key) { cooldowns.remove(id + ":" + key); }

    /** Антифлуд чата. null — можно; иначе причина. */
    public String chatFlood(UUID id, String text) {
        long now = System.currentTimeMillis();
        long delay = pl.getConfig().getLong("chat.flood-delay-ms", 1200);
        Long last = lastChat.get(id);
        if (last != null && now - last < delay) return "Не так быстро!";
        String[] lt = lastText.get(id);
        long rep = pl.getConfig().getLong("chat.repeat-block-seconds", 10) * 1000L;
        if (lt != null && lt[0].equalsIgnoreCase(text) && now - Long.parseLong(lt[1]) < rep) return "Не повторяй одно и то же сообщение.";
        lastChat.put(id, now);
        lastText.put(id, new String[]{text, "" + now});
        return null;
    }

    /** Ограничение частоты команд: true — можно. */
    public boolean commandAllowed(UUID id) {
        int max = pl.getConfig().getInt("chat.command-limit", 10);
        long now = System.currentTimeMillis();
        ArrayDeque<Long> q = cmdTimes.computeIfAbsent(id, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 5000) q.pollFirst();
            if (q.size() >= max) return false;
            q.addLast(now);
        }
        return true;
    }

    public void forget(UUID id) {
        ignores.remove(id); lastMsg.remove(id); tpa.remove(id); lastChat.remove(id); lastText.remove(id); cmdTimes.remove(id);
    }
}
