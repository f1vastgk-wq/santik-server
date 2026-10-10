package dev.santik.core.auth;

import dev.santik.core.SantikCore;
import dev.santik.core.util.CompassItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.persistence.PersistentDataType;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Динамическая капча на карте: случайные символы, фон, помехи; пул карт переиспользуется. */
public final class CaptchaManager {
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Map<Character, String[]> GLYPHS = new HashMap<>();
    private static final SecureRandom RNG = new SecureRandom();

    static {
        String[] g = {
            "A.###.|#...#|#...#|#####|#...#|#...#|#...#", "B####.|#...#|#...#|####.|#...#|#...#|####.",
            "C.####|#....|#....|#....|#....|#....|.####", "D####.|#...#|#...#|#...#|#...#|#...#|####.",
            "E#####|#....|#....|####.|#....|#....|#####", "F#####|#....|#....|####.|#....|#....|#....",
            "G.####|#....|#....|#.###|#...#|#...#|.###.", "H#...#|#...#|#...#|#####|#...#|#...#|#...#",
            "J..###|...#.|...#.|...#.|...#.|#..#.|.##..", "K#...#|#..#.|#.#..|##...|#.#..|#..#.|#...#",
            "L#....|#....|#....|#....|#....|#....|#####", "M#...#|##.##|#.#.#|#.#.#|#...#|#...#|#...#",
            "N#...#|##..#|#.#.#|#..##|#...#|#...#|#...#", "P####.|#...#|#...#|####.|#....|#....|#....",
            "Q.###.|#...#|#...#|#...#|#.#.#|#..#.|.##.#", "R####.|#...#|#...#|####.|#.#..|#..#.|#...#",
            "S.####|#....|#....|.###.|....#|....#|####.", "T#####|..#..|..#..|..#..|..#..|..#..|..#..",
            "U#...#|#...#|#...#|#...#|#...#|#...#|.###.", "V#...#|#...#|#...#|#...#|#...#|.#.#.|..#..",
            "W#...#|#...#|#...#|#.#.#|#.#.#|##.##|#...#", "X#...#|#...#|.#.#.|..#..|.#.#.|#...#|#...#",
            "Y#...#|#...#|.#.#.|..#..|..#..|..#..|..#..", "Z#####|....#|...#.|..#..|.#...|#....|#####",
            "2.###.|#...#|....#|...#.|..#..|.#...|#####", "3####.|....#|....#|.###.|....#|....#|####.",
            "4...#.|..##.|.#.#.|#..#.|#####|...#.|...#.", "5#####|#....|####.|....#|....#|#...#|.###.",
            "6..##.|.#...|#....|####.|#...#|#...#|.###.", "7#####|....#|...#.|..#..|.#...|.#...|.#...",
            "8.###.|#...#|#...#|.###.|#...#|#...#|.###.", "9.###.|#...#|#...#|.####|....#|...#.|.##.."
        };
        for (String s : g) GLYPHS.put(s.charAt(0), s.substring(1).split("\\|"));
    }

    private static final class Slot {
        MapView view;
        volatile byte[] pixels = new byte[128 * 128];
        volatile UUID user;
    }

    private static final class State {
        volatile String code;
        volatile long expires;
        int attempts;
        int regens;
        Slot slot;
    }

    private final SantikCore pl;
    private final List<Slot> pool = new ArrayList<>();
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public CaptchaManager(SantikCore pl) { this.pl = pl; }

    public boolean isPending(UUID id) { return states.containsKey(id); }

    // ---------- генерация ----------
    private static String randomCode(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(CHARS.charAt(RNG.nextInt(CHARS.length())));
        return sb.toString();
    }

    private static int ri(int bound) { return RNG.nextInt(bound); }

    @SuppressWarnings("deprecation")
    private static byte[] render(String code) {
        System.setProperty("java.awt.headless", "true");
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        // фон: тёмный градиент с розовым оттенком
        int r0 = 20 + ri(15), g0 = 12 + ri(10), b0 = 22 + ri(15);
        for (int y = 0; y < 128; y++) {
            g.setColor(new Color(Math.min(255, r0 + y / 3), Math.min(255, g0 + y / 8), Math.min(255, b0 + y / 4)));
            g.fillRect(0, y, 128, 1);
        }
        for (int i = 0; i < 500; i++) {
            int v = 50 + ri(110);
            g.setColor(new Color(v, Math.max(0, v - 30 - ri(30)), v - ri(20) < 0 ? 0 : v - ri(20)));
            g.fillRect(ri(128), ri(128), 1 + ri(2), 1 + ri(2));
        }
        int n = code.length();
        int cell = 3, glyphW = 5 * cell, gap = 5;
        int total = n * glyphW + (n - 1) * gap;
        int x = (128 - total) / 2;
        Color[] pal = {new Color(255, 255, 255), new Color(255, 183, 197), new Color(190, 235, 255), new Color(255, 240, 170), new Color(210, 255, 210)};
        for (int i = 0; i < n; i++) {
            String[] gl = GLYPHS.get(code.charAt(i));
            double ang = (RNG.nextDouble() - 0.5) * 0.7;
            int cy = 64 + ri(17) - 8;
            int cx = x + glyphW / 2;
            Graphics2D gg = (Graphics2D) g.create();
            gg.rotate(ang, cx, cy);
            gg.setColor(pal[ri(pal.length)]);
            for (int row = 0; row < 7; row++)
                for (int col = 0; col < 5; col++)
                    if (gl[row].charAt(col) == '#')
                        gg.fillRect(x + col * cell, cy - (7 * cell) / 2 + row * cell, cell, cell);
            gg.dispose();
            x += glyphW + gap;
        }
        for (int i = 0; i < 6; i++) {
            g.setColor(pal[ri(pal.length)]);
            g.drawLine(ri(128), ri(128), ri(128), ri(128));
        }
        g.dispose();
        return MapPalette.imageToBytes(img);
    }

    // ---------- пул карт ----------
    private Slot acquire(UUID id) {
        for (Slot s : pool) if (s.user == null) { s.user = id; return s; }
        int max = pl.getConfig().getInt("captcha.max-concurrent", 40);
        if (pool.size() >= max) return null;
        List<Integer> ids = new ArrayList<>(pl.worlds().data().getIntegerList("captcha-maps"));
        Slot slot = new Slot();
        MapView v = null;
        int idx = pool.size();
        if (idx < ids.size()) v = Bukkit.getMap(ids.get(idx));
        if (v == null) {
            v = Bukkit.createMap(pl.worlds().captcha());
            if (idx < ids.size()) ids.set(idx, v.getId()); else ids.add(v.getId());
            pl.worlds().data().set("captcha-maps", ids);
            pl.worlds().saveData();
        }
        for (MapRenderer r : new ArrayList<>(v.getRenderers())) v.removeRenderer(r);
        v.setTrackingPosition(false);
        v.setUnlimitedTracking(false);
        v.setScale(MapView.Scale.CLOSEST);
        v.addRenderer(new MapRenderer(false) {
            @Override public void render(MapView map, MapCanvas canvas, Player player) {
                byte[] px = slot.pixels;
                for (int yy = 0; yy < 128; yy++) for (int xx = 0; xx < 128; xx++) canvas.setPixel(xx, yy, px[yy * 128 + xx]);
            }
        });
        slot.view = v;
        slot.user = id;
        pool.add(slot);
        return slot;
    }

    private void giveMap(Player p, Slot slot) {
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        MapMeta mm = (MapMeta) it.getItemMeta();
        mm.setMapView(slot.view);
        mm.displayName(net.kyori.adventure.text.Component.text("Код проверки"));
        mm.getPersistentDataContainer().set(CompassItem.mapKey(), PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(mm);
        p.getInventory().clear();
        p.getInventory().setItem(4, it);
        p.getInventory().setHeldItemSlot(4);
        p.sendMap(slot.view);
    }

    private void newCode(Player p, State st) {
        st.code = randomCode(pl.getConfig().getInt("captcha.length", 5));
        st.expires = System.currentTimeMillis() + pl.getConfig().getInt("captcha.ttl-seconds", 60) * 1000L;
        st.slot.pixels = render(st.code);
        p.sendMap(st.slot.view);
    }

    // ---------- сценарий ----------
    public void start(Player p) {
        UUID id = p.getUniqueId();
        release(p);
        Slot slot = acquire(id);
        if (slot == null) { p.kick(pl.t("captcha-busy", "Сейчас слишком много проверок. Попробуй зайти через минуту.", null)); return; }
        State st = new State();
        st.slot = slot;
        states.put(id, st);
        var cap = pl.worlds().captcha();
        if (cap != null) p.teleportAsync(pl.worlds().spawn(cap));
        giveMap(p, slot);
        newCode(p, st);
        p.sendMessage(pl.t("captcha-intro", "Проверка от ботов: введи в чат код с карты в твоей руке. Напиши «новая», чтобы получить другой код.", null));
    }

    /** Вызывать из любого потока (чат). */
    public void submit(Player p, String text) {
        Bukkit.getScheduler().runTask(pl, () -> submitSync(p, text.trim()));
    }

    private void submitSync(Player p, String text) {
        UUID id = p.getUniqueId();
        State st = states.get(id);
        if (st == null || !p.isOnline()) return;
        int maxRegen = pl.getConfig().getInt("captcha.max-regenerations", 5);
        if (text.equalsIgnoreCase("новая") || text.equalsIgnoreCase("new")) {
            if (++st.regens > maxRegen) { fail(p, "captcha-too-many-new", "Слишком много запросов нового кода."); return; }
            newCode(p, st);
            p.sendMessage(pl.t("captcha-new", "Новый код выдан. Введи его в чат.", null));
            return;
        }
        if (System.currentTimeMillis() > st.expires) {
            if (++st.regens > maxRegen) { fail(p, "captcha-too-many-new", "Время проверки истекло."); return; }
            newCode(p, st);
            p.sendMessage(pl.t("captcha-expired", "Код устарел. Выдан новый — введи его в чат.", null));
            return;
        }
        if (st.code.equalsIgnoreCase(text)) { success(p); return; }
        int max = pl.getConfig().getInt("captcha.max-attempts", 3);
        if (++st.attempts >= max) { fail(p, "captcha-failed", "Слишком много неверных попыток проверки."); return; }
        newCode(p, st); // после ошибки код меняется — перебор бессмыслен
        p.sendMessage(pl.t("captcha-wrong", "Неверный код. Попыток осталось: {left}. Код заменён на новый.", Map.of("left", "" + (max - st.attempts))));
    }

    private void fail(Player p, String key, String def) {
        String ip = p.getAddress() == null ? "unknown" : p.getAddress().getAddress().getHostAddress();
        pl.auth().lockIp(ip, pl.getConfig().getLong("captcha.ip-lock-minutes", 10));
        release(p);
        p.kick(pl.t(key, def, null));
    }

    private void success(Player p) {
        UUID id = p.getUniqueId();
        pl.auth().setCaptchaDone(id);
        release(p);
        p.getInventory().clear();
        var au = pl.worlds().auth();
        if (au != null) p.teleportAsync(pl.worlds().spawn(au));
        p.sendMessage(pl.t("captcha-ok", "Проверка пройдена!", null));
        pl.sendAuthPrompt(p);
    }

    public void release(Player p) {
        State st = states.remove(p.getUniqueId());
        if (st != null && st.slot != null) st.slot.user = null;
    }

    /** Раз в секунду: обновляет просроченные коды. */
    public void tick() {
        long now = System.currentTimeMillis();
        int maxRegen = pl.getConfig().getInt("captcha.max-regenerations", 5);
        for (Map.Entry<UUID, State> e : states.entrySet()) {
            if (now <= e.getValue().expires) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) { release0(e.getKey()); continue; }
            State st = e.getValue();
            if (++st.regens > maxRegen) { fail(p, "captcha-too-many-new", "Время проверки истекло."); continue; }
            newCode(p, st);
            p.sendMessage(pl.t("captcha-expired", "Код устарел. Выдан новый — введи его в чат.", null));
        }
    }

    private void release0(UUID id) {
        State st = states.remove(id);
        if (st != null && st.slot != null) st.slot.user = null;
    }
}
