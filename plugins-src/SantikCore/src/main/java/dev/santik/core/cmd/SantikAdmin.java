package dev.santik.core.cmd;

import dev.santik.core.SantikCore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /santik reload | build <captcha|auth|lobby|anarchy|all> | buildmode | status | backup | restart [сек] */
final class SantikAdmin {
    private final SantikCore pl;

    SantikAdmin(SantikCore pl) { this.pl = pl; }

    private void say(CommandSender s, String m) { s.sendMessage(pl.plain(m)); }

    void run(CommandSender s, String[] a) {
        if (a.length == 0) { say(s, "Использование: /santik reload | build <captcha|auth|lobby|anarchy|all> | buildmode | status | backup | restart [сек]"); return; }
        switch (a[0].toLowerCase()) {
            case "reload" -> { pl.reloadAll(); say(s, "Конфиги, сообщения и цены перезагружены."); }
            case "build" -> {
                String w = a.length > 1 ? a[1].toLowerCase() : "all";
                if (w.equals("all")) pl.worlds().buildMissing(true);
                else switch (w) {
                    case "captcha" -> pl.worlds().build("captcha", pl.worlds().captcha(), true);
                    case "auth" -> pl.worlds().build("auth", pl.worlds().auth(), true);
                    case "lobby" -> pl.worlds().build("lobby", pl.worlds().lobby(), true);
                    case "anarchy" -> pl.worlds().build("anarchy", pl.worlds().anarchy(), true);
                    default -> { say(s, "Неизвестная площадка."); return; }
                }
                say(s, "Площадка перестроена.");
            }
            case "buildmode" -> {
                if (!(s instanceof Player p)) return;
                boolean on = pl.protection().buildMode.add(p.getUniqueId());
                if (!on) pl.protection().buildMode.remove(p.getUniqueId());
                say(s, on ? "Режим строительства в защищённых зонах ВКЛЮЧЁН." : "Режим строительства выключен.");
            }
            case "status" -> {
                double[] tps = Bukkit.getTPS();
                Runtime rt = Runtime.getRuntime();
                long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
                say(s, String.format("TPS: %.2f / %.2f / %.2f | Память: %d / %d МБ | Онлайн: %d", tps[0], tps[1], tps[2], used, rt.maxMemory() / 1048576L, Bukkit.getOnlinePlayers().size()));
            }
            case "backup" -> { say(s, pl.backupNow() ? "Резервная копия базы создана в plugins/SantikCore/backups." : "Ошибка резервного копирования."); }
            case "restart" -> {
                int sec = 30;
                try { if (a.length > 1) sec = Math.max(5, Math.min(600, Integer.parseInt(a[1]))); } catch (NumberFormatException ignored) { }
                pl.scheduleRestart(sec);
                say(s, "Перезапуск через " + sec + " сек. (панель хостинга должна автоматически запускать сервер снова)");
            }
            default -> say(s, "Неизвестная подкоманда.");
        }
        pl.alog(s.getName(), "SANTIK", String.join(" ", a));
    }
}
