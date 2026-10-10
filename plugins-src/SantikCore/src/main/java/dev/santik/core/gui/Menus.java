package dev.santik.core.gui;

import dev.santik.core.SantikCore;
import dev.santik.core.auction.AuctionManager;
import dev.santik.core.auction.AuctionManager.Lot;
import dev.santik.core.eco.Economy;
import dev.santik.core.shop.ShopManager;
import dev.santik.core.util.Cats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class Menus {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String[] SORTS = {"price_asc", "price_desc", "new", "old"};
    private static final Map<String, String> SORT_NAMES = Map.of("price_asc", "Цена ↑", "price_desc", "Цена ↓", "new", "Сначала новые", "old", "Сначала старые");

    private final SantikCore pl;

    public Menus(SantikCore pl) { this.pl = pl; }

    // ---------- helpers ----------
    private static Component c(String s) { return MM.deserialize("<!italic>" + s); }
    private static String esc(String s) { return MM.escapeTags(s); }

    private static ItemStack it(Material m, String name, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(c(name));
        List<Component> l = new ArrayList<>();
        for (String x : lore) l.add(c(x));
        meta.lore(l);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        s.setItemMeta(meta);
        return s;
    }

    private static ItemStack filler() { return it(Material.BLACK_STAINED_GLASS_PANE, " "); }

    private static void fill(Inventory inv) { for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler()); }

    private static String timeLeft(long ms) {
        long m = Math.max(0, ms) / 60_000;
        return (m / 60) + " ч " + (m % 60) + " мин";
    }

    // ---------- режимы ----------
    public void openModes(Player p) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.MODES);
        Inventory inv = Bukkit.createInventory(h, 27, c("<#ffb7c5>SANTIK <dark_gray>• <white>Режимы"));
        h.inv = inv;
        fill(inv);
        boolean on = pl.getConfig().getBoolean("modes.anarchy1-enabled", true);
        int online = pl.worlds().anarchy() == null ? 0 : (int) Bukkit.getOnlinePlayers().stream().filter(x -> pl.worlds().isAnarchy(x.getWorld())).count();
        inv.setItem(11, it(Material.NETHERITE_SWORD, "<#ffb7c5><bold>Анархия #1",
                "<gray>Основной игровой мир. Добывай ресурсы,", "<gray>создавай базу, торгуй и выживай.", "",
                on ? "<white>Статус: <green>Работает" : "<white>Статус: <red>Недоступно", "<white>Онлайн: <#ffb7c5>" + online, "", "<yellow>Нажми, чтобы войти"));
        inv.setItem(13, it(Material.NETHERITE_AXE, "<#ffb7c5><bold>Анархия #2", "<gray>Новый игровой мир.", "", "<white>Статус: <red>В разработке"));
        inv.setItem(15, it(Material.OBSIDIAN, "<#ffb7c5><bold>Анархия #3", "<gray>Новый игровой мир.", "", "<white>Статус: <red>В разработке"));
        p.openInventory(inv);
    }

    // ---------- магазин ----------
    public void openShopCats(Player p) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.SHOP_CATS);
        Inventory inv = Bukkit.createInventory(h, 27, c("<#ffb7c5>SANTIK <dark_gray>• <white>Магазин"));
        h.inv = inv;
        fill(inv);
        int slot = 10;
        for (Map.Entry<String, String> e : pl.shop().categories().entrySet()) {
            if (slot > 16) break;
            inv.setItem(slot, it(pl.shop().icon(e.getKey()), "<#ffb7c5>" + esc(e.getValue()), "<gray>Предметов: <white>" + pl.shop().inCat(e.getKey()).size(), "", "<yellow>Нажми, чтобы открыть"));
            h.slots[slot] = e.getKey();
            slot++;
        }
        inv.setItem(22, it(Material.GOLD_INGOT, "<gray>Баланс: <white>" + Economy.fmt(pl.eco().balance(p.getUniqueId())) + " SC"));
        p.openInventory(inv);
    }

    private void openShopItems(Player p, String cat, int page) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.SHOP_ITEMS);
        h.shopCat = cat;
        h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, c("<#ffb7c5>Магазин <dark_gray>• <white>" + esc(pl.shop().categories().getOrDefault(cat, cat))));
        h.inv = inv;
        renderShop(h, p);
        p.openInventory(inv);
    }

    private void renderShop(MenuHolder h, Player p) {
        Inventory inv = h.inv;
        fill(inv);
        Arrays.fill(h.slots, null);
        List<ShopManager.Item> list = pl.shop().inCat(h.shopCat);
        int pages = Math.max(1, (list.size() + 44) / 45);
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        Map<Material, Integer> sold = pl.shop().soldLastHour(p.getUniqueId());
        for (int i = 0; i < 45; i++) {
            int idx = h.page * 45 + i;
            if (idx >= list.size()) break;
            ShopManager.Item s = list.get(idx);
            ItemStack stack = new ItemStack(s.mat());
            ItemMeta meta = stack.getItemMeta();
            int used = sold.getOrDefault(s.mat(), 0);
            List<Component> lore = new ArrayList<>();
            lore.add(c("<white>" + esc(s.ru())));
            lore.add(c(s.buy() > 0 ? "<gray>Купить: <white>" + Economy.fmt(s.buy()) + " SC <dark_gray>(ЛКМ 1 • Shift+ЛКМ 16)" : "<gray>Купить: <red>не продаётся"));
            lore.add(c("<gray>Продать: <white>" + Economy.fmt(s.sell()) + " SC <dark_gray>(ПКМ 1 • Shift+ПКМ всё)"));
            lore.add(c("<gray>Лимит продажи в час: <white>" + used + "/" + s.limit()));
            meta.lore(lore);
            stack.setItemMeta(meta);
            inv.setItem(i, stack);
            h.slots[i] = s;
        }
        if (h.page > 0) inv.setItem(45, it(Material.ARROW, "<white>← Назад"));
        inv.setItem(47, it(Material.GOLD_INGOT, "<gray>Баланс: <white>" + Economy.fmt(pl.eco().balance(p.getUniqueId())) + " SC"));
        inv.setItem(49, it(Material.BARRIER, "<red>К категориям"));
        if (h.page < pages - 1) inv.setItem(53, it(Material.ARROW, "<white>Вперёд →"));
    }

    private int count(Player p, Material m) {
        ItemStack plain = new ItemStack(m);
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) if (s != null && s.isSimilar(plain)) n += s.getAmount();
        return n;
    }

    private void remove(Player p, Material m, int amount) {
        ItemStack plain = new ItemStack(m);
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < c.length && amount > 0; i++) {
            if (c[i] != null && c[i].isSimilar(plain)) {
                int take = Math.min(amount, c[i].getAmount());
                c[i].setAmount(c[i].getAmount() - take);
                if (c[i].getAmount() <= 0) c[i] = null;
                amount -= take;
            }
        }
        p.getInventory().setStorageContents(c);
    }

    private void shopBuy(Player p, ShopManager.Item s, int qty) {
        if (s.buy() <= 0) { p.sendMessage(pl.plain("Этот предмет магазин не продаёт.")); return; }
        qty = Math.min(qty, s.mat().getMaxStackSize());
        ItemStack give = new ItemStack(s.mat(), qty);
        if (!AuctionManager.fits(p.getInventory(), give)) { p.sendMessage(pl.plain("Недостаточно места в инвентаре.")); return; }
        long cost = s.buy() * qty;
        if (!pl.eco().take(p.getUniqueId(), cost, "SHOP_BUY", s.mat().name() + " x" + qty)) {
            p.sendMessage(pl.plain("Недостаточно SC. Нужно: " + Economy.fmt(cost) + "."));
            return;
        }
        p.getInventory().addItem(give);
        p.sendMessage(pl.plain("Куплено: " + s.ru() + " x" + qty + " за " + Economy.fmt(cost) + " SC."));
    }

    private void shopSell(Player p, ShopManager.Item s, boolean all) {
        int have = count(p, s.mat());
        if (have <= 0) { p.sendMessage(pl.plain("У тебя нет такого предмета (зачарованные и переименованные не принимаются).")); return; }
        int used = pl.shop().soldLastHour(p.getUniqueId()).getOrDefault(s.mat(), 0);
        int left = s.limit() - used;
        if (left <= 0) { p.sendMessage(pl.plain("Лимит продажи этого предмета в час исчерпан. Продай игрокам через /ah.")); return; }
        int qty = Math.min(all ? have : 1, Math.min(have, left));
        long pay = s.sell() * qty;
        if (pay <= 0) { p.sendMessage(pl.plain("Магазин не скупает этот предмет.")); return; }
        remove(p, s.mat(), qty);
        if (!pl.eco().add(p.getUniqueId(), pay, "SHOP_SELL", s.mat().name() + " x" + qty)) {
            p.getInventory().addItem(new ItemStack(s.mat(), qty));
            p.sendMessage(pl.plain("Ошибка зачисления. Предметы возвращены."));
            return;
        }
        pl.shop().logSale(p.getUniqueId(), s.mat(), qty);
        p.sendMessage(pl.plain("Продано: " + s.ru() + " x" + qty + " за " + Economy.fmt(pay) + " SC."));
    }

    // ---------- аукцион ----------
    public void openAuction(Player p, String cat, String sort, String query, int page) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.AH);
        h.cat = cat; h.sort = sort; h.query = query; h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, c("<#ffb7c5>Аукцион <dark_gray>• <white>SANTIK"));
        h.inv = inv;
        renderAh(h, p);
        p.openInventory(inv);
    }

    private void renderAh(MenuHolder h, Player p) {
        Inventory inv = h.inv;
        fill(inv);
        Arrays.fill(h.slots, null);
        AuctionManager.Page pg = pl.auction().browse(h.cat, h.query, h.sort, h.page, 45);
        int pages = Math.max(1, (pg.total() + 44) / 45);
        if (h.page >= pages) { h.page = pages - 1; pg = pl.auction().browse(h.cat, h.query, h.sort, h.page, 45); }
        for (int i = 0; i < pg.lots().size() && i < 45; i++) {
            Lot l = pg.lots().get(i);
            ItemStack d = l.item().clone();
            ItemMeta meta = d.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(c(""));
            lore.add(c("<gray>Цена: <#ffb7c5>" + Economy.fmt(l.price()) + " SC"));
            lore.add(c("<gray>Продавец: <white>" + esc(l.sellerName())));
            lore.add(c("<gray>Осталось: <white>" + timeLeft(l.expires() - System.currentTimeMillis())));
            lore.add(c("<dark_gray>Лот #" + l.id()));
            lore.add(c("<yellow>Нажми, чтобы купить"));
            meta.lore(lore);
            d.setItemMeta(meta);
            inv.setItem(i, d);
            h.slots[i] = l.id();
        }
        if (h.page > 0) inv.setItem(45, it(Material.ARROW, "<white>← Назад"));
        inv.setItem(46, it(Material.CHEST, "<#ffb7c5>Мои лоты", "<gray>Отмена и возврат предметов"));
        inv.setItem(47, it(Material.HOPPER, "<white>Сортировка: <#ffb7c5>" + SORT_NAMES.get(h.sort), "<gray>Нажми, чтобы сменить"));
        inv.setItem(48, it(Material.BOOKSHELF, "<white>Категория: <#ffb7c5>" + Cats.NAMES.getOrDefault(h.cat, h.cat), "<gray>Нажми, чтобы сменить"));
        inv.setItem(49, it(Material.OAK_SIGN, "<white>Поиск" + (h.query.isEmpty() ? "" : ": <#ffb7c5>" + esc(h.query)), "<gray>ЛКМ — искать", "<gray>ПКМ — сбросить"));
        inv.setItem(50, it(Material.GOLD_INGOT, "<gray>Баланс: <white>" + Economy.fmt(pl.eco().balance(p.getUniqueId())) + " SC"));
        inv.setItem(51, it(Material.WRITABLE_BOOK, "<white>История сделок", "<gray>Покажу в чате"));
        inv.setItem(52, it(Material.PAPER, "<white>Как продать", "<gray>/ah sell <цена>", "<gray>предмет берётся из руки"));
        if (h.page < pages - 1) inv.setItem(53, it(Material.ARROW, "<white>Вперёд →"));
    }

    private void openConfirm(Player p, long id, MenuHolder prev) {
        Lot l = pl.auction().lot(id);
        if (l == null || !l.status().equals("ACTIVE")) { p.sendMessage(pl.plain("Лот уже недоступен.")); renderAh(prev, p); return; }
        MenuHolder h = new MenuHolder(MenuHolder.Type.AH_CONFIRM);
        h.lotId = id; h.prev = prev;
        Inventory inv = Bukkit.createInventory(h, 27, c("<#ffb7c5>Подтверди покупку"));
        h.inv = inv;
        fill(inv);
        inv.setItem(13, l.item().clone());
        inv.setItem(11, it(Material.LIME_STAINED_GLASS_PANE, "<green>Купить за " + Economy.fmt(l.price()) + " SC", "<gray>Лот #" + id));
        inv.setItem(15, it(Material.RED_STAINED_GLASS_PANE, "<red>Отмена"));
        inv.setItem(22, it(Material.PAPER, "<white>Продавец: <#ffb7c5>" + esc(l.sellerName()), "<gray>Успешных продаж: <white>" + pl.auction().sellerSales(l.seller()),
                "<gray>Осталось: <white>" + timeLeft(l.expires() - System.currentTimeMillis())));
        p.openInventory(inv);
    }

    private void openMine(Player p, MenuHolder prev) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.AH_MINE);
        h.prev = prev;
        Inventory inv = Bukkit.createInventory(h, 54, c("<#ffb7c5>Мои лоты"));
        h.inv = inv;
        renderMine(h, p);
        p.openInventory(inv);
    }

    private void renderMine(MenuHolder h, Player p) {
        fill(h.inv);
        Arrays.fill(h.slots, null);
        List<Lot> lots = pl.auction().mine(p.getUniqueId());
        for (int i = 0; i < lots.size() && i < 45; i++) {
            Lot l = lots.get(i);
            ItemStack d = l.item().clone();
            ItemMeta meta = d.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(c(""));
            lore.add(c("<gray>Цена: <#ffb7c5>" + Economy.fmt(l.price()) + " SC"));
            if (l.status().equals("ACTIVE")) {
                lore.add(c("<gray>Осталось: <white>" + timeLeft(l.expires() - System.currentTimeMillis())));
                lore.add(c("<yellow>Нажми, чтобы снять с продажи"));
            } else lore.add(c("<yellow>Срок истёк — нажми, чтобы забрать"));
            meta.lore(lore);
            d.setItemMeta(meta);
            h.inv.setItem(i, d);
            h.slots[i] = l;
        }
        h.inv.setItem(49, it(Material.BARRIER, "<red>К аукциону"));
    }

    // ---------- клики ----------
    public void click(Player p, MenuHolder h, InventoryClickEvent e) {
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= h.inv.getSize()) return;
        ClickType ct = e.getClick();
        switch (h.type) {
            case MODES -> {
                if (raw == 11) {
                    if (!pl.getConfig().getBoolean("modes.anarchy1-enabled", true)) { p.sendMessage(pl.plain("Режим сейчас недоступен.")); return; }
                    p.closeInventory();
                    pl.enterAnarchy(p);
                } else if (raw == 13 || raw == 15) {
                    p.sendMessage(pl.plain("Этот режим ещё не запущен. Статус: В разработке."));
                }
            }
            case SHOP_CATS -> {
                if (h.slots[raw] instanceof String cat) openShopItems(p, cat, 0);
            }
            case SHOP_ITEMS -> {
                if (raw == 45) { h.page--; renderShop(h, p); return; }
                if (raw == 53) { h.page++; renderShop(h, p); return; }
                if (raw == 49) { openShopCats(p); return; }
                if (raw < 45 && h.slots[raw] instanceof ShopManager.Item s) {
                    switch (ct) {
                        case LEFT -> shopBuy(p, s, 1);
                        case SHIFT_LEFT -> shopBuy(p, s, 16);
                        case RIGHT -> shopSell(p, s, false);
                        case SHIFT_RIGHT -> shopSell(p, s, true);
                        default -> { }
                    }
                    renderShop(h, p);
                }
            }
            case AH -> {
                if (raw == 45) { h.page--; renderAh(h, p); return; }
                if (raw == 53) { h.page++; renderAh(h, p); return; }
                if (raw == 46) { openMine(p, h); return; }
                if (raw == 47) {
                    int i = Arrays.asList(SORTS).indexOf(h.sort);
                    h.sort = SORTS[(i + 1) % SORTS.length]; h.page = 0; renderAh(h, p); return;
                }
                if (raw == 48) { h.cat = Cats.next(h.cat); h.page = 0; renderAh(h, p); return; }
                if (raw == 49) {
                    if (ct == ClickType.RIGHT) { h.query = ""; h.page = 0; renderAh(h, p); return; }
                    pl.searchPending().put(p.getUniqueId(), new String[]{h.cat, h.sort});
                    p.closeInventory();
                    p.sendMessage(pl.plain("Введи в чат, что искать (название по-русски, по-английски или ID). «отмена» — выйти."));
                    return;
                }
                if (raw == 51) {
                    List<String> hist = pl.auction().history(p.getUniqueId(), 10);
                    p.sendMessage(pl.plain(hist.isEmpty() ? "История сделок пуста." : "История сделок:"));
                    for (String s : hist) p.sendMessage(pl.plain(" • " + s));
                    return;
                }
                if (raw < 45 && h.slots[raw] instanceof Long id) openConfirm(p, id, h);
            }
            case AH_CONFIRM -> {
                if (raw == 11) {
                    String err = pl.auction().buy(p, h.lotId);
                    p.sendMessage(pl.plain(err == null ? "Покупка успешна!" : err));
                    p.closeInventory();
                    if (h.prev != null) openAuction(p, h.prev.cat, h.prev.sort, h.prev.query, h.prev.page);
                } else if (raw == 15) {
                    openAuction(p, h.prev.cat, h.prev.sort, h.prev.query, h.prev.page);
                }
            }
            case AH_MINE -> {
                if (raw == 49) { openAuction(p, h.prev.cat, h.prev.sort, h.prev.query, h.prev.page); return; }
                if (raw < 45 && h.slots[raw] instanceof Lot l) {
                    if (l.status().equals("ACTIVE")) {
                        String err = pl.auction().cancel(p, l.id());
                        p.sendMessage(pl.plain(err == null ? "Лот снят с продажи." : err));
                    } else {
                        int n = pl.auction().deliver(p);
                        if (n == 0) p.sendMessage(pl.plain("Не удалось выдать предмет: освободи место в инвентаре."));
                    }
                    renderMine(h, p);
                }
            }
        }
    }
}
