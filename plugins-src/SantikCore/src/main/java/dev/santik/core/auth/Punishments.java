package dev.santik.core.auth;

import dev.santik.core.SantikCore;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Punishments {
    public record P(long id, String type, String reason, String by, long created, long expires) { }

    private final SantikCore pl;
    private static final Pattern DUR = Pattern.compile("^(\\d+)([smhdw])$");

    public Punishments(SantikCore pl) { this.pl = pl; }

    /** "30m", "12h", "7d", "2w" -> миллисекунды; -1 если не срок. */
    public static long parseDuration(String s) {
        Matcher m = DUR.matcher(s.toLowerCase());
        if (!m.matches()) return -1;
        long n = Long.parseLong(m.group(1));
        return n * switch (m.group(2)) {
            case "s" -> 1000L; case "m" -> 60_000L; case "h" -> 3_600_000L;
            case "d" -> 86_400_000L; default -> 604_800_000L;
        };
    }

    public void add(String type, UUID id, String name, String reason, String by, long durationMs) throws Exception {
        long now = System.currentTimeMillis();
        pl.db().update("INSERT INTO punishments(uuid,name,type,reason,by_name,created,expires,active) VALUES(?,?,?,?,?,?,?,1)",
                id.toString(), name, type, reason, by, now, durationMs > 0 ? now + durationMs : 0L);
    }

    public P active(UUID id, String type) {
        try {
            return pl.db().one("SELECT id,type,reason,by_name,created,expires FROM punishments WHERE uuid=? AND type=? AND active=1 AND (expires=0 OR expires>?) ORDER BY id DESC LIMIT 1",
                    rs -> new P(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getLong(6)),
                    id.toString(), type, System.currentTimeMillis());
        } catch (Exception e) { return null; }
    }

    public boolean lift(UUID id, String type) throws Exception {
        return pl.db().update("UPDATE punishments SET active=0 WHERE uuid=? AND type=? AND active=1", id.toString(), type) > 0;
    }

    public List<P> history(UUID id, int limit) throws Exception {
        return pl.db().query("SELECT id,type,reason,by_name,created,expires FROM punishments WHERE uuid=? ORDER BY id DESC LIMIT ?",
                rs -> new P(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getLong(6)),
                id.toString(), limit);
    }
}
