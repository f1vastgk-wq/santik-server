package dev.santik.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** Компас режимов. Определяется скрытым тегом в PDC, а не названием. */
public final class CompassItem {
    private static NamespacedKey KEY;
    private static NamespacedKey MAP_KEY;

    private CompassItem() { }

    public static void init(Plugin pl) {
        KEY = new NamespacedKey(pl, "menu_compass");
        MAP_KEY = new NamespacedKey(pl, "captcha_map");
    }

    public static NamespacedKey mapKey() { return MAP_KEY; }

    public static ItemStack create() {
        ItemStack it = new ItemStack(Material.COMPASS);
        ItemMeta m = it.getItemMeta();
        MiniMessage mm = MiniMessage.miniMessage();
        m.displayName(mm.deserialize("<!italic><#ffb7c5>Выбор режима <dark_gray>• <gray>ПКМ"));
        m.lore(List.of(mm.deserialize("<!italic><gray>Открывает меню режимов SANTIK")));
        m.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public static boolean is(ItemStack it) {
        if (it == null || it.getType() != Material.COMPASS || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    public static boolean isCaptchaMap(ItemStack it) {
        if (it == null || it.getType() != Material.FILLED_MAP || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(MAP_KEY, PersistentDataType.BYTE);
    }

    public static void strip(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (is(s) || isCaptchaMap(s)) inv.setItem(i, null);
        }
        if (is(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    /** Только для лобби-режима (инвентарь временный): очищает и выдаёт компас в 5-й слот. */
    public static void giveLobby(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setItem(4, create());
        inv.setHeldItemSlot(4);
    }

    public static Component name() { return Component.text("Выбор режима"); }
}
