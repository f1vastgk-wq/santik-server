package dev.santik.core.cmd;

import dev.santik.core.SantikCore;
import dev.santik.core.auth.Role;
import dev.santik.core.eco.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public final class PlayerCommands implements CommandExecutor {
    private final SantikCore pl;

    public PlayerCommands(SantikCore pl) { this.pl = pl; }

    private boolean anarchy(Player p) {
        if (!pl.worlds().isAnarchy(p.getWorld())) { p.sendMessage(pl.plain("Эта команда работает только в мире анархии.")); return false; }
        return true;
    }

    private boolean cd(Player p, String key, long sec) {
        long left = pl.social().cooldownLeft(p.getUniqueId(), key, sec);
        if (left > 0) { p.sendMessage(pl.plain("Подожди ещё " + left + " сек.")); return false; }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Только для игроков."); return true; }
        if (!pl.auth().isAuthed(p.getUniqueId())) { p.sendMessage(pl.msg("need-auth")); return true; }
        switch (cmd.getName()) {
            case "spawn" -> spawn(p);
            case "lobby" -> pl.toLobby(p);
            case "rtp" -> rtp(p);
            case "sethome" -> setHome(p, a);
            case "home" -> home(p, a);
            case "delhome" -> delHome(p, a);
            case "homes" -> homes(p);
            case "bal" -> {
                p.sendMessage(pl.plain("Баланс: " + Economy.fmt(pl.eco().balance(p.getUniqueId())) + " SC"));
                pl.tips().once(p, "bal", "/pay <игрок> <сумма> — перевод денег другому игроку.");
            }
            case "pay" -> pay(p, a);
            case "help" -> help(p);
            case "rules" -> {
                p.sendMessage(pl.plain("Правила SANTIK:"));
                for (String r : pl.getConfig().getStringList("rules")) p.sendMessage(pl.plain(r));
            }
            case "msg" -> msg(p, a);
            case "r" -> reply(p, a);
            case "ignore" -> ignore(p, a);
            case "tpa" -> tpa(p, a);
            case "tpaccept" -> tpAccept(p);
            case "tpdeny" -> { pl.social().tpa.remove(p.getUniqueId()); p.sendMessage(pl.plain("Запрос отклонён.")); }
            case "shop" -> { if (anarchy(p)) pl.menus().openShopCats(p); }
            case "ah" -> ah(p, a);
            case "list" -> {
                List<String> names = new ArrayList<>();
                for (Player o : Bukkit.getOnlinePlayers()) if (pl.auth().isAuthed(o.getUniqueId()) && !pl.isVanished(o)) names.add(o.getName());
                p.sendMessage(pl.plain("Онлайн (" + names.size() + "): " + String.join(", ", names)));
            }
            default -> { return false; }
        }
        return true;
    }

    private void spawn(Player p) {
        if (!cd(p, "spawn", pl.getConfig().getLong("spawn-cooldown-seconds", 10))) return;
        World an = pl.worlds().anarchy();
        if (pl.worlds().isAnarchy(p.getWorld()) && an != null) p.teleportAsync(pl.worlds().spawn(an));
        else p.teleportAsync(pl.worlds().spawn(pl.worlds().lobby()));
        pl.tips().once(p, "spawn", "/rtp — случайное место, /sethome <название> — поставить дом.");
    }

    private void rtp(Player p) {
        if (!pl.worlds().isAnarchyOverworld(p.getWorld())) { p.sendMessage(pl.plain("/rtp работает только в верхнем мире анархии.")); return; }
        if (!cd(p, "rtp", pl.getConfig().getLong("rtp.cooldown-seconds", 60))) return;
        pl.rtp().teleport(p);
        pl.tips().once(p, "rtp", "Вернуться на спавн можно командой /spawn.");
    }

    private static boolean validName(String n) { return n.matches("^[\\p{L}0-9_]{1,16}$"); }

    private void setHome(Player p, String[] a) {
        if (!anarchy(p)) return;
        String name = a.length > 0 ? a[0].toLowerCase() : "home";
        if (!validName(name)) { p.sendMessage(pl.plain("Название дома: до 16 символов, только буквы, цифры и _.")); return; }
        Location l = p.getLocation();
        if (pl.worlds().inAnarchySpawn(l)) { p.sendMessage(pl.plain("На защищённом спавне дом ставить нельзя.")); return; }
        try {
            String id = p.getUniqueId().toString();
            Integer exists = pl.db().one("SELECT COUNT(*) FROM homes WHERE uuid=? AND name=?", rs -> rs.getInt(1), id, name);
            Integer total = pl.db().one("SELECT COUNT(*) FROM homes WHERE uuid=?", rs -> rs.getInt(1), id);
            int max = pl.getConfig().getInt("homes.max", 3);
            if ((exists == null || exists == 0) && total != null && total >= max) { p.sendMessage(pl.plain("Лимит домов: " + max + ". Удали ненужный: /delhome <название>.")); return; }
            pl.db().update("INSERT OR REPLACE INTO homes(uuid,name,world,x,y,z,yaw,pitch) VALUES(?,?,?,?,?,?,?,?)",
                    id, name, l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), (double) l.getYaw(), (double) l.getPitch());
            p.sendMessage(pl.plain("Дом «" + name + "» сохранён."));
            pl.tips().once(p, "sethome", "/home <название> — вернуться, /homes — список домов.");
        } catch (Exception e) { p.sendMessage(pl.plain("Ошибка базы данных.")); }
    }

    private void home(Player p, String[] a) {
        if (!anarchy(p)) return;
        String name = a.length > 0 ? a[0].toLowerCase() : "home";
        if (!cd(p, "home", pl.getConfig().getLong("homes.cooldown-seconds", 10))) return;
        try {
            Object[] h = pl.db().one("SELECT world,x,y,z,yaw,pitch FROM homes WHERE uuid=? AND name=?",
                    rs -> new Object[]{rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4), (float) rs.getDouble(5), (float) rs.getDouble(6)},
                    p.getUniqueId().toString(), name);
            if (h == null) { p.sendMessage(pl.plain("Дом «" + name + "» не найден. Список: /homes")); return; }
            World w = Bukkit.getWorld((String) h[0]);
            if (w == null) { p.sendMessage(pl.plain("Мир этого дома недоступен.")); return; }
            p.teleportAsync(new Location(w, (double) h[1], (double) h[2], (double) h[3], (float) h[4], (float) h[5]));
            p.sendMessage(pl.plain("Телепортация домой: " + name));
        } catch (Exception e) { p.sendMessage(pl.plain("Ошибка базы данных.")); }
    }

    private void delHome(Player p, String[] a) {
        if (a.length < 1) { p.sendMessage(pl.plain("Использование: /delhome <название>")); return; }
        try {
            int n = pl.db().update("DELETE FROM homes WHERE uuid=? AND name=?", p.getUniqueId().toString(), a[0].toLowerCase());
            p.sendMessage(pl.plain(n > 0 ? "Дом удалён." : "Такого дома нет."));
        } catch (Exception e) { p.sendMessage(pl.plain("Ошибка базы данных.")); }
    }

    private void homes(Player p) {
        try {
            List<String> l = pl.db().query("SELECT name FROM homes WHERE uuid=? ORDER BY name", rs -> rs.getString(1), p.getUniqueId().toString());
            p.sendMessage(pl.plain(l.isEmpty() ? "У тебя нет домов. Поставь: /sethome <название>" : "Твои дома (" + l.size() + "/" + pl.getConfig().getInt("homes.max", 3) + "): " + String.join(", ", l)));
        } catch (Exception e) { p.sendMessage(pl.plain("Ошибка базы данных.")); }
    }

    private void pay(Player p, String[] a) {
        if (a.length != 2) { p.sendMessage(pl.plain("Использование: /pay <игрок> <сумма>")); return; }
        Long cents = Economy.parse(a[1]);
        if (cents == null) { p.sendMessage(pl.plain("Некорректная сумма (положительное число, до 2 знаков после запятой).")); return; }
        long max = Economy.toCents(pl.getConfig().getDouble("economy.pay-max-single", 100000));
        if (cents > max) { p.sendMessage(pl.plain("Максимум за один перевод: " + Economy.fmt(max) + " SC.")); return; }
        Player t = Bukkit.getPlayerExact(a[0]);
        UUID tid; String tname;
        if (t != null && pl.auth().isAuthed(t.getUniqueId())) { tid = t.getUniqueId(); tname = t.getName(); }
        else {
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(a[0]);
            if (op == null) { p.sendMessage(pl.plain("Игрок не найден.")); return; }
            tid = op.getUniqueId(); tname = op.getName() == null ? a[0] : op.getName();
        }
        if (tid.equals(p.getUniqueId())) { p.sendMessage(pl.plain("Нельзя переводить самому себе.")); return; }
        if (!pl.auction().accountOldEnough(p.getUniqueId())) { p.sendMessage(pl.plain("Новые аккаунты не могут переводить деньги первые " + pl.getConfig().getLong("economy.new-account-minutes", 30) + " мин.")); return; }
        if (!cd(p, "pay", pl.getConfig().getLong("economy.pay-cooldown-seconds", 5))) return;
        if (!pl.eco().transfer(p.getUniqueId(), tid, cents, "PAY", p.getName() + " -> " + tname)) { p.sendMessage(pl.plain("Недостаточно SC.")); return; }
        p.sendMessage(pl.plain("Переведено " + Economy.fmt(cents) + " SC игроку " + tname + "."));
        Player on = Bukkit.getPlayer(tid);
        if (on != null) on.sendMessage(pl.plain(p.getName() + " перевёл тебе " + Economy.fmt(cents) + " SC."));
    }

    private void help(Player p) {
        Role r = pl.roles().of(p);
        String[] base = {
            "/spawn — на спавн   /lobby — в главное лобби", "/rtp — случайное место   /bal — баланс   /pay <игрок> <сумма>",
            "/sethome <имя>, /home <имя>, /delhome <имя>, /homes", "/ah — аукцион   /shop — магазин   /rules — правила",
            "/msg <игрок> <текст>, /r <текст>, /ignore <игрок>", "/tpa <игрок>, /tpaccept, /tpdeny   /list — онлайн"};
        p.sendMessage(pl.plain("Команды SANTIK:"));
        for (String l : base) p.sendMessage(pl.plain(l));
        if (r.atLeast(Role.ADMIN)) p.sendMessage(pl.plain("Админ: /ban /unban /kick /mute /unmute /warn /history /tp /tphere /spec /vanish /ahadmin"));
        if (r == Role.OWNER) p.sendMessage(pl.plain("Владелец: /admin /gm /heal /feed /give /eco /logs /santik"));
    }

    private void msg(Player p, String[] a) {
        if (a.length < 2) { p.sendMessage(pl.plain("Использование: /msg <игрок> <текст>")); return; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null || !pl.auth().isAuthed(t.getUniqueId()) || pl.isVanished(t)) { p.sendMessage(pl.plain("Игрок не в сети.")); return; }
        send(p, t, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
    }

    private void send(Player p, Player t, String text) {
        if (pl.punishments().active(p.getUniqueId(), "MUTE") != null) { p.sendMessage(pl.plain("Ты в муте.")); return; }
        if (pl.social().ignores(t.getUniqueId(), p.getUniqueId())) { p.sendMessage(pl.plain("Не удалось доставить сообщение.")); return; }
        p.sendMessage(pl.plain("Ты → " + t.getName() + ": " + text));
        t.sendMessage(pl.plain(p.getName() + " → тебе: " + text));
        pl.social().lastMsg.put(p.getUniqueId(), t.getUniqueId());
        pl.social().lastMsg.put(t.getUniqueId(), p.getUniqueId());
    }

    private void reply(Player p, String[] a) {
        UUID last = pl.social().lastMsg.get(p.getUniqueId());
        Player t = last == null ? null : Bukkit.getPlayer(last);
        if (a.length < 1) { p.sendMessage(pl.plain("Использование: /r <текст>")); return; }
        if (t == null) { p.sendMessage(pl.plain("Некому отвечать.")); return; }
        send(p, t, String.join(" ", a));
    }

    private void ignore(Player p, String[] a) {
        if (a.length != 1) { p.sendMessage(pl.plain("Использование: /ignore <игрок>")); return; }
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(a[0]);
        if (op == null || op.getUniqueId().equals(p.getUniqueId())) { p.sendMessage(pl.plain("Игрок не найден.")); return; }
        boolean now = pl.social().toggleIgnore(p.getUniqueId(), op.getUniqueId());
        p.sendMessage(pl.plain(now ? "Теперь ты игнорируешь " + a[0] + "." : "Ты больше не игнорируешь " + a[0] + "."));
    }

    private void tpa(Player p, String[] a) {
        if (!anarchy(p)) return;
        if (a.length != 1) { p.sendMessage(pl.plain("Использование: /tpa <игрок>")); return; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null || t.equals(p) || !pl.worlds().isAnarchy(t.getWorld()) || pl.isVanished(t)) { p.sendMessage(pl.plain("Игрок недоступен.")); return; }
        if (pl.social().ignores(t.getUniqueId(), p.getUniqueId())) { p.sendMessage(pl.plain("Игрок недоступен.")); return; }
        if (!cd(p, "tpa", pl.getConfig().getLong("tpa.cooldown-seconds", 15))) return;
        long exp = System.currentTimeMillis() + pl.getConfig().getLong("tpa.expire-seconds", 60) * 1000L;
        pl.social().tpa.put(t.getUniqueId(), new dev.santik.core.social.Social.TpaReq(p.getUniqueId(), exp));
        p.sendMessage(pl.plain("Запрос отправлен игроку " + t.getName() + "."));
        t.sendMessage(pl.plain(p.getName() + " просит телепортироваться к тебе. /tpaccept — принять, /tpdeny — отклонить."));
    }

    private void tpAccept(Player p) {
        var req = pl.social().tpa.remove(p.getUniqueId());
        if (req == null || req.expires() < System.currentTimeMillis()) { p.sendMessage(pl.plain("Нет активных запросов.")); return; }
        Player from = Bukkit.getPlayer(req.from());
        if (from == null || !pl.worlds().isAnarchy(from.getWorld())) { p.sendMessage(pl.plain("Игрок вышел.")); return; }
        from.teleportAsync(p.getLocation());
        from.sendMessage(pl.plain("Телепортация к " + p.getName() + "."));
        p.sendMessage(pl.plain("Запрос принят."));
    }

    private void ah(Player p, String[] a) {
        if (!anarchy(p)) return;
        pl.tips().once(p, "ah", "Выставить предмет из руки: /ah sell <цена>.");
        if (a.length == 0) { pl.menus().openAuction(p, "ALL", "price_asc", "", 0); return; }
        switch (a[0].toLowerCase()) {
            case "sell" -> {
                if (a.length != 2) { p.sendMessage(pl.plain("Введите команду /ah sell <цена> для выставления предмета из руки.")); return; }
                Long price = Economy.parse(a[1]);
                if (price == null) { p.sendMessage(pl.plain("Некорректная цена.")); return; }
                var hand = p.getInventory().getItemInMainHand();
                String err = pl.auction().validate(hand);
                if (err != null) { p.sendMessage(pl.plain(err)); return; }
                pl.pendingSell().put(p.getUniqueId(), new Object[]{price, hand.clone(), System.currentTimeMillis() + 30_000L});
                long fee = price * pl.getConfig().getInt("auction.fee-percent", 5) / 100;
                p.sendMessage(pl.plain("Выставить " + hand.getType().name().toLowerCase() + " x" + hand.getAmount() + " за " + Economy.fmt(price)
                        + " SC? Комиссия " + pl.getConfig().getInt("auction.fee-percent", 5) + "% (" + Economy.fmt(fee) + " SC). Подтверди: /ah confirm (30 сек)."));
            }
            case "confirm" -> {
                Object[] pend = pl.pendingSell().remove(p.getUniqueId());
                if (pend == null || (long) pend[2] < System.currentTimeMillis()) { p.sendMessage(pl.plain("Нет запроса на продажу. Начни с /ah sell <цена>.")); return; }
                var hand = p.getInventory().getItemInMainHand();
                if (!hand.equals(pend[1])) { p.sendMessage(pl.plain("Предмет в руке изменился. Повтори /ah sell <цена>.")); return; }
                String err = pl.auction().create(p, (long) pend[0]);
                p.sendMessage(pl.plain(err == null ? "Лот выставлен на аукцион!" : err));
            }
            case "search" -> {
                if (a.length < 2) { p.sendMessage(pl.plain("Использование: /ah search <название>")); return; }
                pl.menus().openAuction(p, "ALL", "price_asc", String.join(" ", Arrays.copyOfRange(a, 1, a.length)), 0);
            }
            case "mine" -> pl.menus().openAuction(p, "ALL", "price_asc", "", 0);
            case "claim" -> { if (pl.auction().deliver(p) == 0) p.sendMessage(pl.plain("Нечего получать.")); }
            case "history" -> {
                List<String> h = pl.auction().history(p.getUniqueId(), 10);
                p.sendMessage(pl.plain(h.isEmpty() ? "История пуста." : "История сделок:"));
                for (String x : h) p.sendMessage(pl.plain(" • " + x));
            }
            case "cancel" -> {
                if (a.length != 2) { p.sendMessage(pl.plain("Использование: /ah cancel <номер лота>")); return; }
                try {
                    String err = pl.auction().cancel(p, Long.parseLong(a[1]));
                    p.sendMessage(pl.plain(err == null ? "Лот снят с продажи." : err));
                } catch (NumberFormatException e) { p.sendMessage(pl.plain("Номер лота — число.")); }
            }
            default -> p.sendMessage(pl.plain("Использование: /ah [sell <цена> | confirm | search <текст> | claim | history | cancel <id>]"));
        }
    }
}
