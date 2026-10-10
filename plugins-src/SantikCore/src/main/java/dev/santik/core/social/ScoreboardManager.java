package dev.santik.core.social;

import dev.santik.core.SantikCore;
import dev.santik.core.eco.Economy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScoreboardManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int LINES = 8;
    private final SantikCore pl;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public ScoreboardManager(SantikCore pl) { this.pl = pl; }

    public void show(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("santik", Criteria.DUMMY, MM.deserialize("<#ffb7c5><bold>SANTIK"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        try { // скрыть красные номера справа (Paper 1.20.4+)
            Class<?> nf = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Object blank = nf.getMethod("blank").invoke(null);
            Objective.class.getMethod("numberFormat", nf).invoke(obj, blank);
        } catch (Throwable ignored) { }
        for (int i = 0; i < LINES; i++) {
            String entry = "\u00A7" + (char) ('0' + i);
            Team t = sb.registerNewTeam("l" + i);
            t.addEntry(entry);
            obj.getScore(entry).setScore(LINES - i);
        }
        boards.put(p.getUniqueId(), sb);
        update(p);
        p.setScoreboard(sb);
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
        try { p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); } catch (Exception ignored) { }
    }

    public void tick() {
        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && pl.auth().isAuthed(p.getUniqueId())) update(p);
        }
    }

    private void update(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) return;
        boolean ana = pl.worlds().isAnarchy(p.getWorld());
        long online = ana
                ? Bukkit.getOnlinePlayers().stream().filter(x -> pl.worlds().isAnarchy(x.getWorld())).count()
                : Bukkit.getOnlinePlayers().stream().filter(x -> pl.auth().isAuthed(x.getUniqueId())).count();
        String[] lines = {
            "",
            "<gray>Игрок: <white>" + MM.escapeTags(p.getName()),
            "<gray>Режим: <white>" + (ana ? "ANARCHY #1" : "ЛОББИ"),
            "<gray>Баланс: <#ffb7c5>" + Economy.fmt(pl.eco().balance(p.getUniqueId())) + " SC",
            "<gray>Статус: <white>" + pl.roles().of(p).title(),
            "<gray>Онлайн: <white>" + online,
            "",
            "<#ffb7c5>Сакура • Анархия"
        };
        for (int i = 0; i < LINES; i++) {
            Team t = sb.getTeam("l" + i);
            if (t != null) t.prefix(MM.deserialize("<!italic>" + lines[i]));
        }
    }
}
