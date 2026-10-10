package dev.santik.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {
    public enum Type { MODES, SHOP_CATS, SHOP_ITEMS, AH, AH_CONFIRM, AH_MINE }

    public final Type type;
    public Inventory inv;
    public String shopCat = "";
    public String cat = "ALL";
    public String sort = "price_asc";
    public String query = "";
    public int page = 0;
    public long lotId;
    public MenuHolder prev;
    public final Object[] slots = new Object[54];

    public MenuHolder(Type type) { this.type = type; }

    @Override public Inventory getInventory() { return inv; }
}
