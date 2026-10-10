package dev.santik.core.util;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

/** Категории аукциона. */
public final class Cats {
    public static final Map<String, String> NAMES = new LinkedHashMap<>();
    static {
        NAMES.put("ALL", "Все");
        NAMES.put("BLOCKS", "Блоки");
        NAMES.put("ORES", "Руды и ресурсы");
        NAMES.put("FOOD", "Еда");
        NAMES.put("GEAR", "Снаряжение");
        NAMES.put("OTHER", "Прочее");
    }

    private Cats() { }

    public static String of(Material m) {
        String n = m.name();
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || n.endsWith("_SWORD") || n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE") || n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT")
                || n.equals("MACE") || n.equals("SHIELD") || n.equals("ELYTRA") || n.equals("FISHING_ROD")) return "GEAR";
        if (m.isEdible()) return "FOOD";
        if (n.endsWith("_ORE") || n.endsWith("_INGOT") || n.startsWith("RAW_") || n.equals("DIAMOND") || n.equals("EMERALD")
                || n.equals("COAL") || n.equals("REDSTONE") || n.equals("LAPIS_LAZULI") || n.equals("QUARTZ")
                || n.equals("NETHERITE_SCRAP") || n.equals("AMETHYST_SHARD") || n.equals("ENDER_PEARL") || n.equals("BLAZE_ROD")) return "ORES";
        if (m.isBlock()) return "BLOCKS";
        return "OTHER";
    }

    public static String next(String cur) {
        String[] k = NAMES.keySet().toArray(new String[0]);
        for (int i = 0; i < k.length; i++) if (k[i].equals(cur)) return k[(i + 1) % k.length];
        return "ALL";
    }
}
