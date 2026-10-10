package dev.santik.core.cmd;

import dev.santik.core.SantikCore;
import dev.santik.core.auth.Punishments;
import dev.santik.core.auth.Role;
import dev.santik.core.eco.Economy;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class AdminCommands implements CommandExecutor {
    private record Target(UUID id, String name, Player online) { }

    private final SantikCore pl;

    public AdminCommands(SantikCore pl) { this.pl = pl; }

    private Role roleOf(CommandSender s) { return s instanceof Player p ? pl.roles().of(p) : Role.OWNER; }

    private void say(CommandSender s, String m) { s.sendMessage(pl.plain(m)); }

    private Target find(CommandSender s, String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return new Target(p.getUniqueId(), p.getName(), p);
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        if (op == null) { say(s, "Игрок не найден (он должен был хотя бы раз зайти на сервер)."); return null; }
        return new Target(op.getUniqueId(), op.getName() == null ? name : op.getName(), null);
    }

    private boolean outranks(CommandSender s, Target t) {
        Role a = roleOf(s), b = pl.roles().of(t.id());
        if (b == Role.OWNER) return false;
        if (a == Role.OWNER) return true;
        return a.ordinal() > b.ordinal();
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (s instanceof Player p && !pl.auth().isAuthed(p.getUniqueId())) return true;
        Role role = roleOf(s);
        String n = cmd.getName();
        Set<String> ownerOnly = Set.of("admin", "gm", "heal", "feed", "give", "eco", "logs", "santik");
        Role need = ownerOnly.contains(n) ? Role.OWNER : Role.ADMIN;
        if (!role.atLeast(need)) { say(s, "Недостаточно прав."); return true; }
        try {
            switch (n) {
                case "admin" -> admin(s, a);
                case "ban" -> punish(s, a, "BAN");
                case "mute" -> punish(s, a, "MUTE");
                case "unban" -> lift(s, a, "BAN");
                case "unmute" -> lift(s, a, "MUTE");
                case "kick" -> kick(s, a);
                case "warn" -> warn(s, a);
                case "history" -> history(s, a);
                case "tp" -> tp(s, a, false);
                case "tphere" -> tp(s, a, true);
                case "spec" -> spec(s);
                case "vanish" -> vanish(s);
                case "gm" -> gm(s, a);
                case "heal" -> heal(s, a, true);
                case "feed" -> heal(s, a, false);
                case "give" -> give(s, a);
                case "eco" -> eco(s, a);
                case "ahadmin" -> ahadmin(s, a);
                case "logs" -> logs(s, a);
                case "santik" -> new SantikAdmin(pl).run(s, a);
                default -> { return false; }
            }
        } catch (Exception e) {
            say(s, "Ошибка: " + e.getMessage());
            pl.getLogger().warning("admin cmd " + n + ": " + e);
        }
        return true;
    }

    private void admin(CommandSender s, String[] a) throws Exception {
        if (a.length == 0 || a[0].equalsIgnoreCase("list")) {
            List<String> names = new ArrayList<>();
            for (UUID id : pl.roles().admins()) { OfflinePlayer op = Bukkit.getOfflinePlayer(id); names.add(op.getName() == null ? id.toString() : op.getName()); }
            say(s, "Администраторы: " + (names.isEmpty() ? "нет" : String.join(", ", names)));
            return;
        }
        if (a.length != 2) { say(s, "Использование: /admin add|remove <ник> | /admin list"); return; }
        Target t = find(s, a[1]);
        if (t == null) return;
        if (pl.roles().isOwner(t.id())) { say(s, "Права владельца изменить нельзя."); return; }
        if (a[0].equalsIgnoreCase("add")) {
            pl.roles().addAdmin(t.id(), t.name());
            say(s, t.name() + " назначен администратором.");
            pl.alog(s.getName(), "ADMIN_ADD", t.name());
        } else if (a[0].equalsIgnoreCase("remove")) {
            say(s, pl.roles().removeAdmin(t.id()) ? t.name() + " больше не администратор." : "Он не администратор.");
            pl.alog(s.getName(), "ADMIN_REMOVE", t.name());
        } else say(s, "Использование: /admin add|remove <ник>");
        if (t.online() != null) t.online().updateCommands();
    }

    private void punish(CommandSender s, String[] a, String type) throws Exception {
        if (a.length < 2) { say(s, "Использование: /" + type.toLowerCase() + " <ник> [срок: 30m/12h/7d/2w] <причина>"); return; }
        Target t = find(s, a[0]);
        if (t == null) return;
        if (!outranks(s, t)) { say(s, "Нельзя наказать этого игрока."); return; }
        int ri = 1;
        long dur = Punishments.parseDuration(a[1]);
        if (dur > 0) ri = 2;
        if (a.length <= ri) { say(s, "Укажи причину наказания."); return; }
        String reason = String.join(" ", Arrays.copyOfRange(a, ri, a.length));
        pl.punishments().add(type, t.id(), t.name(), reason, s.getName(), dur > 0 ? dur : 0);
        long exp = dur > 0 ? System.currentTimeMillis() + dur : 0;
        say(s, (type.equals("BAN") ? "Бан: " : "Мут: ") + t.name() + " до " + pl.fmtTime(exp) + ". Причина: " + reason);
        pl.alog(s.getName(), type, t.name() + " | " + reason + " | до " + pl.fmtTime(exp));
        if (t.online() != null) {
            if (type.equals("BAN")) t.online().kick(pl.banScreen(new Punishments.P(0, type, reason, s.getName(), 0, exp)));
            else say(t.online(), "Тебе выдан мут до " + pl.fmtTime(exp) + ". Причина: " + reason);
        }
    }

    private void lift(CommandSender s, String[] a, String type) throws Exception {
        if (a.length != 1) { say(s, "Использование: /" + (type.equals("BAN") ? "unban" : "unmute") + " <ник>"); return; }
        Target t = find(s, a[0]);
        if (t == null) return;
        say(s, pl.punishments().lift(t.id(), type) ? "Снято: " + t.name() : "Активного наказания нет.");
        pl.alog(s.getName(), "UN" + type, t.name());
    }

    private void kick(CommandSender s, String[] a) {
        if (a.length < 1) { say(s, "Использование: /kick <ник> [причина]"); return; }
        Target t = find(s, a[0]);
        if (t == null || t.online() == null) { if (t != null) say(s, "Игрок не в сети."); return; }
        if (!outranks(s, t)) { say(s, "Нельзя кикнуть этого игрока."); return; }
        String reason = a.length > 1 ? String.join(" ", Arrays.copyOfRange(a, 1, a.length)) : "Нарушение правил";
        t.online().kick(pl.plain("Ты кикнут. Причина: " + reason));
        say(s, "Кикнут: " + t.name());
        pl.alog(s.getName(), "KICK", t.name() + " | " + reason);
    }

    private void warn(CommandSender s, String[] a) throws Exception {
        if (a.length < 2) { say(s, "Использование: /warn <ник> <причина>"); return; }
        Target t = find(s, a[0]);
        if (t == null) return;
        if (!outranks(s, t)) { say(s, "Нельзя выдать предупреждение этому игроку."); return; }
        String reason = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
        pl.punishments().add("WARN", t.id(), t.name(), reason, s.getName(), 0);
        say(s, "Предупреждение выдано: " + t.name());
        pl.alog(s.getName(), "WARN", t.name() + " | " + reason);
        if (t.online() != null) say(t.online(), "Предупреждение от администрации: " + reason);
    }

    private void history(CommandSender s, String[] a) throws Exception {
        if (a.length != 1) { say(s, "Использование: /history <ник>"); return; }
        Target t = find(s, a[0]);
        if (t == null) return;
        var list = pl.punishments().history(t.id(), 10);
        say(s, list.isEmpty() ? "Наказаний нет." : "История наказаний " + t.name() + ":");
        for (var p : list) say(s, " #" + p.id() + " " + p.type() + " | " + p.reason() + " | от " + p.by() + " | до " + pl.fmtTime(p.expires()));
    }

    private void tp(CommandSender s, String[] a, boolean here) {
        if (!(s instanceof Player me)) { say(s, "Только для игроков."); return; }
        if (a.length != 1) { say(s, "Использование: /" + (here ? "tphere" : "tp") + " <ник>"); return; }
        Target t = find(s, a[0]);
        if (t == null || t.online() == null) { if (t != null) say(s, "Игрок не в сети."); return; }
        if (here) t.online().teleportAsync(me.getLocation()); else me.teleportAsync(t.online().getLocation());
        pl.alog(s.getName(), here ? "TPHERE" : "TP", t.name());
    }

    private void spec(CommandSender s) {
        if (!(s instanceof Player p)) return;
        if (p.getGameMode() == GameMode.SPECTATOR) { p.setGameMode(GameMode.SURVIVAL); say(s, "Режим наблюдения выключен."); }
        else { p.setGameMode(GameMode.SPECTATOR); say(s, "Режим наблюдения включён."); }
        pl.alog(s.getName(), "SPEC", p.getGameMode().name());
    }

    private void vanish(CommandSender s) {
        if (!(s instanceof Player p)) return;
        boolean on = pl.toggleVanish(p);
        say(s, on ? "Невидимость включена." : "Невидимость выключена.");
    }

    private void gm(CommandSender s, String[] a) {
        if (a.length < 1) { say(s, "Использование: /gm <survival|creative|spectator|adventure> [ник]"); return; }
        GameMode m;
        switch (a[0].toLowerCase()) {
            case "s", "survival", "0" -> m = GameMode.SURVIVAL;
            case "c", "creative", "1" -> m = GameMode.CREATIVE;
            case "a", "adventure", "2" -> m = GameMode.ADVENTURE;
            case "sp", "spectator", "3" -> m = GameMode.SPECTATOR;
            default -> { say(s, "Неизвестный режим."); return; }
        }
        Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player p ? p : null);
        if (t == null) { say(s, "Игрок не найден."); return; }
        t.setGameMode(m);
        say(s, t.getName() + ": " + m.name());
        pl.alog(s.getName(), "GM", t.getName() + " " + m.name());
    }

    private void heal(CommandSender s, String[] a, boolean health) {
        Player t = a.length > 0 ? Bukkit.getPlayerExact(a[0]) : (s instanceof Player p ? p : null);
        if (t == null) { say(s, "Игрок не найден."); return; }
        if (health) t.setHealth(t.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        t.setFoodLevel(20); t.setSaturation(20f);
        say(s, "Готово: " + t.getName());
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 2) { say(s, "Использование: /give <ник> <МАТЕРИАЛ> [кол-во]"); return; }
        Player t = Bukkit.getPlayerExact(a[0]);
        Material m = Material.matchMaterial(a[1]);
        if (t == null || m == null || !m.isItem()) { say(s, "Игрок или предмет не найден."); return; }
        int n = 1;
        try { if (a.length > 2) n = Math.max(1, Math.min(2304, Integer.parseInt(a[2]))); } catch (NumberFormatException e) { say(s, "Количество — число."); return; }
        t.getInventory().addItem(new ItemStack(m, n));
        say(s, "Выдано " + m.name() + " x" + n + " игроку " + t.getName());
        pl.alog(s.getName(), "GIVE", t.getName() + " " + m.name() + " x" + n);
    }

    private void eco(CommandSender s, String[] a) {
        if (a.length != 3) { say(s, "Использование: /eco give|take|set <ник> <сумма>"); return; }
        Target t = find(s, a[1]);
        if (t == null) return;
        Long c = a[0].equalsIgnoreCase("set") && a[2].equals("0") ? Long.valueOf(0) : Economy.parse(a[2]);
        if (c == null) { say(s, "Некорректная сумма."); return; }
        boolean ok = switch (a[0].toLowerCase()) {
            case "give" -> pl.eco().add(t.id(), c, "ADMIN_GIVE", s.getName());
            case "take" -> pl.eco().take(t.id(), c, "ADMIN_TAKE", s.getName());
            case "set" -> pl.eco().set(t.id(), c, s.getName());
            default -> false;
        };
        say(s, ok ? "Готово. Баланс " + t.name() + ": " + Economy.fmt(pl.eco().balance(t.id())) + " SC" : "Не удалось (проверь сумму/баланс).");
        pl.alog(s.getName(), "ECO_" + a[0].toUpperCase(), t.name() + " " + a[2]);
    }

    private void ahadmin(CommandSender s, String[] a) {
        if (a.length != 2 || !a[0].equalsIgnoreCase("remove")) { say(s, "Использование: /ahadmin remove <id лота>"); return; }
        try {
            String err = pl.auction().adminRemove(Long.parseLong(a[1]));
            say(s, err == null ? "Лот снят, предмет возвращён продавцу." : err);
            pl.alog(s.getName(), "AH_REMOVE", a[1]);
        } catch (NumberFormatException e) { say(s, "ID лота — число."); }
    }

    private void logs(CommandSender s, String[] a) throws Exception {
        int n = 10;
        try { if (a.length > 0) n = Math.max(1, Math.min(30, Integer.parseInt(a[0]))); } catch (NumberFormatException ignored) { }
        var rows = pl.db().query("SELECT ts,actor,action,detail FROM admin_log ORDER BY id DESC LIMIT ?",
                rs -> pl.fmtTime(rs.getLong(1)) + " " + rs.getString(2) + " " + rs.getString(3) + " " + rs.getString(4), n);
        say(s, "Журнал админ-действий:");
        for (String r : rows) say(s, " " + r);
    }
}
