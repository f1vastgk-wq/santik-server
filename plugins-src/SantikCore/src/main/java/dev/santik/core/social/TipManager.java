package dev.santik.core.social;

import dev.santik.core.SantikCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TipManager {
    private final SantikCore pl;

    public TipManager(SantikCore pl) { this.pl = pl; }

    /** true — подсказка показана впервые. */
    public boolean first(Player p, String key) {
        try { return pl.db().update("INSERT OR IGNORE INTO tips(uuid,key) VALUES(?,?)", p.getUniqueId().toString(), key) == 1; }
        catch (Exception e) { return false; }
    }

    public void once(Player p, String key, String text) {
        if (first(p, key)) p.sendMessage(pl.plain("Подсказка: " + text));
    }

    /** Последовательность подсказок для нового игрока. */
    public void welcomeSequence(Player p) {
        String[] tips = {
            "Нажми ПКМ по компасу в руке и выбери «Анархия #1».",
            "В мире используй /rtp, чтобы отправиться в случайное место.",
            "/spawn вернёт тебя на спавн, /lobby — в главное лобби.",
            "/sethome <название> ставит дом, /home <название> возвращает домой.",
            "/ah — аукцион игроков, /shop — магазин, /bal — баланс.",
            "Правила сервера: /rules. Список команд: /help."
        };
        for (int i = 0; i < tips.length; i++) {
            final String t = tips[i];
            Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) p.sendMessage(pl.plain("Подсказка: " + t)); }, 100L + i * 200L);
        }
    }
}
