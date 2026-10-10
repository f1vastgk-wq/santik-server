package dev.santik.core.auth;

import dev.santik.core.SantikCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class AuthCommands implements CommandExecutor {
    private final SantikCore plugin;
    private final AuthManager auth;
    private SantikCore pl2() { return plugin; }

    public AuthCommands(SantikCore plugin, AuthManager auth) { this.plugin = plugin; this.auth = auth; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Только для игроков."); return true; }
        if (auth.isAuthed(p.getUniqueId())) { p.sendMessage(plugin.msg("already-authed")); return true; }
        if (cmd.getName().equals("reg")) register(p, args); else login(p, args);
        return true;
    }

    private String ip(Player p) {
        return p.getAddress() == null ? "unknown" : p.getAddress().getAddress().getHostAddress();
    }

    private void register(Player p, String[] a) {
        if (a.length != 2) { p.sendMessage(plugin.msg("usage-reg")); return; }
        if (!a[0].equals(a[1])) { p.sendMessage(plugin.msg("mismatch")); return; }
        int min = plugin.getConfig().getInt("auth.min-password-length", 6);
        int max = plugin.getConfig().getInt("auth.max-password-length", 64);
        if (a[0].length() < min) { p.sendMessage(plugin.msg("too-short", Map.of("min", "" + min))); return; }
        if (a[0].length() > max) { p.sendMessage(plugin.msg("too-long", Map.of("max", "" + max))); return; }
        if (a[0].equalsIgnoreCase(p.getName())) { p.sendMessage(plugin.msg("same-as-name")); return; }
        final UUID id = p.getUniqueId();
        final String name = p.getName(), ip = ip(p), pass = a[0];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (auth.db().getHash(id) != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(plugin.msg("already-registered")));
                    return;
                }
                boolean ok = auth.db().register(id, name, auth.hasher().hash(pass), ip);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!p.isOnline()) return;
                    if (ok) { auth.setAuthed(id); pl2().postAuth(p); p.sendMessage(plugin.msg("registered")); }
                    else p.sendMessage(plugin.msg("already-registered"));
                });
            } catch (Exception e) {
                plugin.getLogger().severe("Ошибка регистрации: " + e);
            }
        });
    }

    private void login(Player p, String[] a) {
        if (a.length != 1) { p.sendMessage(plugin.msg("usage-login")); return; }
        final UUID id = p.getUniqueId();
        final String ip = ip(p), pass = a[0];
        if (auth.isIpLocked(ip)) { p.kick(plugin.msg("ip-locked")); return; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String stored = auth.db().getHash(id);
                if (stored == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(plugin.msg("not-registered")));
                    return;
                }
                boolean ok = auth.hasher().verify(pass, stored);
                if (ok) auth.db().touchLogin(id);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!p.isOnline()) return;
                    if (ok) { auth.setAuthed(id); pl2().postAuth(p); p.sendMessage(plugin.msg("logged-in")); return; }
                    int max = plugin.getConfig().getInt("auth.max-login-attempts", 5);
                    int fails = auth.addFailure(id);
                    if (fails >= max) { auth.lockIp(ip); p.kick(plugin.msg("kick-attempts")); }
                    else p.sendMessage(plugin.msg("wrong-password", Map.of("left", "" + (max - fails))));
                });
            } catch (Exception e) {
                plugin.getLogger().severe("Ошибка входа: " + e);
            }
        });
    }
}
