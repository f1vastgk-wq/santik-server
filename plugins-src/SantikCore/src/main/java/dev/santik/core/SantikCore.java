package dev.santik.core;

import dev.santik.core.auction.AuctionManager;
import dev.santik.core.auth.*;
import dev.santik.core.cmd.AdminCommands;
import dev.santik.core.cmd.PlayerCommands;
import dev.santik.core.db.Database;
import dev.santik.core.eco.Economy;
import dev.santik.core.gui.Menus;
import dev.santik.core.listener.CoreListener;
import dev.santik.core.listener.ProtectionListener;
import dev.santik.core.shop.ShopManager;
import dev.santik.core.social.ScoreboardManager;
import dev.santik.core.social.Social;
import dev.santik.core.social.TipManager;
import dev.santik.core.util.CompassItem;
import dev.santik.core.world.InvStore;
import dev.santik.core.world.Rtp;
import dev.santik.core.world.WorldsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.logging.log4j.LogManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SantikCore extends JavaPlugin {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Database db;
    private AuthManager auth;
    private Roles roles;
    private Punishments punishments;
    private Economy eco;
    private ShopManager shop;
    private AuctionManager auction;
    private WorldsManager worlds;
    private CaptchaManager captcha;
    private Social social;
    private Menus menus;
    private TipManager tips;
    private ScoreboardManager scoreboards;
    private InvStore invs;
    private Rtp rtp;
    private ProtectionListener protection;
    private YamlConfiguration messages;
    private File messagesFile;
    private final Map<UUID, String[]> searchPending = new ConcurrentHashMap<>();
    private final Map<UUID, Object[]> pendingSell = new ConcurrentHashMap<>();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    // ---------- getters ----------
    public Database db() { return db; }
    public AuthManager auth() { return auth; }
    public Roles roles() { return roles; }
    public Punishments punishments() { return punishments; }
    public Economy eco() { return eco; }
    public ShopManager shop() { return shop; }
    public AuctionManager auction() { return auction; }
    public WorldsManager worlds() { return worlds; }
    public CaptchaManager captcha() { return captcha; }
    public Social social() { return social; }
    public Menus menus() { return menus; }
    public TipManager tips() { return tips; }
    public ScoreboardManager scoreboards() { return scoreboards; }
    public InvStore invs() { return invs; }
    public Rtp rtp() { return rtp; }
    public ProtectionListener protection() { return protection; }
    public Map<UUID, String[]> searchPending() { return searchPending; }
    public Map<UUID, Object[]> pendingSell() { return pendingSell; }

    // ---------- lifecycle ----------
    @Override
    public void onEnable() {
        saveDefaultConfig();
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        CompassItem.init(this);
        try {
            db = new Database(new File(getDataFolder(), "santik.db"));
        } catch (Exception e) {
            getLogger().severe("Не удалось открыть базу данных: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addFilter(new PasswordLogFilter());

        auth = new AuthManager(this, db);
        roles = new Roles(this);
        punishments = new Punishments(this);
        eco = new Economy(this);
        shop = new ShopManager(this);
        shop.load();
        auction = new AuctionManager(this);
        worlds = new WorldsManager(this);
        social = new Social(this);
        tips = new TipManager(this);
        scoreboards = new ScoreboardManager(this);
        invs = new InvStore(this);
        rtp = new Rtp(this);
        captcha = new CaptchaManager(this);
        menus = new Menus(this);
        protection = new ProtectionListener(this);

        worlds.init();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new AuthListener(this, auth), this);
        pm.registerEvents(protection, this);
        pm.registerEvents(new CoreListener(this), this);

        AuthCommands ac = new AuthCommands(this, auth);
        reg("reg", ac); reg("login", ac);
        PlayerCommands pc = new PlayerCommands(this);
        for (String c : new String[]{"spawn", "lobby", "rtp", "sethome", "home", "delhome", "homes", "ah", "bal", "pay", "help", "rules",
                "msg", "r", "ignore", "tpa", "tpaccept", "tpdeny", "shop", "list"}) reg(c, pc);
        AdminCommands adm = new AdminCommands(this);
        for (String c : new String[]{"admin", "ban", "unban", "kick", "mute", "unmute", "warn", "history", "tp", "tphere", "spec", "vanish",
                "gm", "heal", "feed", "give", "eco", "ahadmin", "logs", "santik"}) reg(c, adm);

        var sch = Bukkit.getScheduler();
        sch.runTask(this, () -> worlds.buildMissing(false));
        sch.runTaskTimer(this, () -> captcha.tick(), 20L, 20L);
        sch.runTaskTimer(this, () -> scoreboards.tick(), 40L, 40L);
        sch.runTaskTimer(this, () -> auction.expire(), 1200L, 1200L);
        sch.runTaskTimer(this, this::saveAllPositions, 6000L, 6000L);
        long hours = getConfig().getLong("backup.interval-hours", 6);
        sch.runTaskTimerAsynchronously(this, this::backupNow, 6000L, Math.max(1, hours) * 72000L);

        if (!Bukkit.getOnlineMode())
            getLogger().warning("online-mode=false: возможна подмена ников. Для безопасности используйте online-mode=true.");
        getLogger().info("SantikCore включён.");
    }

    private void reg(String name, org.bukkit.command.CommandExecutor ex) {
        PluginCommand c = getCommand(name);
        if (c == null) { getLogger().warning("Команда не найдена в plugin.yml: " + name); return; }
        c.setExecutor(ex);
    }

    @Override
    public void onDisable() {
        if (db == null) return;
        saveAllPositions();
        db.close();
    }

    public void reloadAll() {
        reloadConfig();
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        shop.load();
    }

    // ---------- сообщения ----------
    private String prefix() { return messages.getString("prefix", ""); }

    public Component msg(String key) { return msg(key, null); }

    public Component msg(String key, Map<String, String> vars) {
        String s = messages.getString(key, key);
        if (vars != null) for (var e : vars.entrySet()) s = s.replace("{" + e.getKey() + "}", e.getValue());
        return MM.deserialize(prefix() + MM.escapeTags(s));
    }

    /** Сообщение с самозаписью значения по умолчанию в messages.yml (его можно редактировать). */
    public Component t(String key, String def, Map<String, String> vars) {
        if (!messages.contains(key)) {
            messages.set(key, def);
            try { messages.save(messagesFile); } catch (Exception ignored) { }
        }
        return msg(key, vars);
    }

    public Component plain(String s) { return MM.deserialize(prefix() + MM.escapeTags(s)); }

    public String fmtTime(long ms) {
        if (ms <= 0) return "навсегда";
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(ms));
    }

    public Component banScreen(Punishments.P b) {
        return Component.text("Ты заблокирован на SANTIK\nПричина: " + b.reason() + "\nДо: " + fmtTime(b.expires()));
    }

    public void alog(String actor, String action, String detail) {
        try { db.update("INSERT INTO admin_log(ts,actor,action,detail) VALUES(?,?,?,?)", System.currentTimeMillis(), actor, action, detail); }
        catch (Exception e) { getLogger().warning("alog: " + e); }
    }

    // ---------- вход ----------
    public void beginJoin(Player p) {
        if (!invs.toLobby(p)) { p.kick(plain("Ошибка загрузки профиля. Попробуй зайти позже.")); return; }
        p.setGameMode(GameMode.SURVIVAL);
        captcha.start(p);
    }

    public void sendAuthPrompt(Player p) {
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            boolean reg;
            try { reg = db.getHash(id) != null; } catch (Exception e) { reg = false; }
            final boolean r = reg;
            Bukkit.getScheduler().runTask(this, () -> { if (p.isOnline()) p.sendMessage(msg(r ? "login-prompt" : "register-prompt")); });
        });
    }

    public void postAuth(Player p) {
        roles.claimOwnerIfNeeded(p);
        eco.refresh(p.getUniqueId());
        World lobby = worlds.lobby();
        if (lobby != null) p.teleportAsync(worlds.spawn(lobby));
        CompassItem.giveLobby(p);
        scoreboards.show(p);
        p.updateCommands();
        applyVanish(p);
        if (!isVanished(p)) {
            Component j = MM.deserialize("<dark_gray>[<green>+<dark_gray>] <white>" + MM.escapeTags(p.getName()));
            for (Player o : Bukkit.getOnlinePlayers()) if (auth.isAuthed(o.getUniqueId())) o.sendMessage(j);
        }
        if (tips.first(p, "welcome")) tips.welcomeSequence(p);
    }

    public void onQuit(Player p) {
        UUID id = p.getUniqueId();
        boolean was = auth.isAuthed(id);
        if (was) {
            savePos(p);
            if (!isVanished(p)) {
                Component q = MM.deserialize("<dark_gray>[<red>-<dark_gray>] <white>" + MM.escapeTags(p.getName()));
                for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p) && auth.isAuthed(o.getUniqueId())) o.sendMessage(q);
            }
        }
        captcha.release(p);
        scoreboards.remove(p);
        eco.forget(id);
        social.forget(id);
        searchPending.remove(id);
        pendingSell.remove(id);
        vanished.remove(id);
        auth.forget(id);
    }

    // ---------- режимы и позиции ----------
    public void enterAnarchy(Player p) {
        World w = worlds.anarchy();
        if (w == null) { p.sendMessage(plain("Режим сейчас недоступен.")); return; }
        Location saved = loadSaved(p);
        Location spawn = worlds.spawn(w);
        if (saved == null) { go(p, spawn); return; }
        saved.getWorld().getChunkAtAsync(saved).thenAccept(ch -> Bukkit.getScheduler().runTask(this, () -> {
            if (!p.isOnline()) return;
            boolean ok = isSafe(saved);
            if (!ok) p.sendMessage(plain("Место, где ты вышел, небезопасно. Отправляю на спавн."));
            go(p, ok ? saved : spawn);
        }));
    }

    private void go(Player p, Location target) {
        p.teleportAsync(target).thenAccept(ok -> Bukkit.getScheduler().runTask(this, () -> {
            if (!p.isOnline()) return;
            if (!ok) { p.sendMessage(plain("Не удалось телепортироваться. Попробуй ещё раз.")); return; }
            if (!invs.toAnarchy(p)) { p.sendMessage(plain("Ошибка загрузки инвентаря. Сообщи администрации.")); return; }
            CompassItem.strip(p);
            auction.deliver(p);
            tips.once(p, "enter", "Используй /rtp, чтобы отправиться в случайное место мира.");
        }));
    }

    public void toLobby(Player p) {
        World lobby = worlds.lobby();
        if (lobby == null) return;
        if (worlds.isAnarchy(p.getWorld())) savePos(p);
        p.teleportAsync(worlds.spawn(lobby)).thenAccept(ok -> Bukkit.getScheduler().runTask(this, () -> {
            if (!ok || !p.isOnline()) return;
            if (invs.toLobby(p)) CompassItem.giveLobby(p);
        }));
    }

    public void savePos(Player p) {
        if (!auth.isAuthed(p.getUniqueId()) || p.isDead() || !worlds.isAnarchy(p.getWorld())) return;
        Location l = p.getLocation();
        try {
            db.update("INSERT OR REPLACE INTO positions(uuid,world,x,y,z,yaw,pitch) VALUES(?,?,?,?,?,?,?)", p.getUniqueId().toString(),
                    l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), (double) l.getYaw(), (double) l.getPitch());
        } catch (Exception e) { getLogger().warning("savePos: " + e); }
    }

    private void saveAllPositions() {
        for (Player p : Bukkit.getOnlinePlayers()) savePos(p);
    }

    private Location loadSaved(Player p) {
        try {
            Object[] r = db.one("SELECT world,x,y,z,yaw,pitch FROM positions WHERE uuid=?",
                    rs -> new Object[]{rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4), (float) rs.getDouble(5), (float) rs.getDouble(6)},
                    p.getUniqueId().toString());
            if (r == null) return null;
            World w = Bukkit.getWorld((String) r[0]);
            if (w == null || !worlds.isAnarchy(w)) return null;
            return new Location(w, (double) r[1], (double) r[2], (double) r[3], (float) r[4], (float) r[5]);
        } catch (Exception e) { return null; }
    }

    private boolean isSafe(Location l) {
        World w = l.getWorld();
        if (!w.getWorldBorder().isInside(l)) return false;
        Block feet = l.getBlock(), head = feet.getRelative(0, 1, 0), below = feet.getRelative(0, -1, 0);
        if (feet.getType().isSolid() || head.getType().isSolid()) return false;
        if (feet.getType() == Material.LAVA || head.getType() == Material.LAVA || feet.getType() == Material.FIRE) return false;
        Material b = below.getType();
        if (b == Material.LAVA || b == Material.MAGMA_BLOCK || b == Material.CACTUS || b == Material.FIRE) return false;
        return b.isSolid() || b == Material.WATER;
    }

    // ---------- невидимость ----------
    public boolean isVanished(Player p) { return vanished.contains(p.getUniqueId()); }

    public boolean toggleVanish(Player p) {
        if (vanished.remove(p.getUniqueId())) {
            for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(this, p);
            return false;
        }
        vanished.add(p.getUniqueId());
        for (Player o : Bukkit.getOnlinePlayers()) if (!roles.of(o).atLeast(Role.ADMIN)) o.hidePlayer(this, p);
        return true;
    }

    public void applyVanish(Player joiner) {
        if (roles.of(joiner).atLeast(Role.ADMIN)) return;
        for (Player o : Bukkit.getOnlinePlayers()) if (isVanished(o)) joiner.hidePlayer(this, o);
    }

    // ---------- обслуживание ----------
    public boolean backupNow() {
        try {
            File dir = new File(getDataFolder(), "backups");
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            db.backupTo(new File(dir, "santik-" + ts + ".db"));
            File[] all = dir.listFiles((d, n) -> n.endsWith(".db"));
            int keep = getConfig().getInt("backup.keep", 14);
            if (all != null && all.length > keep) {
                Arrays.sort(all, Comparator.comparingLong(File::lastModified));
                for (int i = 0; i < all.length - keep; i++) all[i].delete();
            }
            return true;
        } catch (Exception e) {
            getLogger().warning("backup: " + e);
            return false;
        }
    }

    public void scheduleRestart(int seconds) {
        final int[] left = {seconds};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            int s = left[0]--;
            if (s <= 0) { task.cancel(); Bukkit.shutdown(); return; }
            if (s == seconds || s == 60 || s == 30 || s == 10 || s <= 5)
                Bukkit.broadcast(plain("Перезапуск сервера через " + s + " сек. Позиции сохранены."));
            if (s == 5) saveAllPositions();
        }, 0L, 20L);
    }
}
