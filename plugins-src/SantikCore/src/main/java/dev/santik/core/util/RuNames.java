package dev.santik.core.util;

import dev.santik.core.SantikCore;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Русские названия для поиска: из shop.yml + автогенерация по словарю. */
public final class RuNames {
    private static final Map<String, String> W = new HashMap<>();
    static {
        String[] d = ("netherite=незеритовый,diamond=алмазный,iron=железный,golden=золотой,gold=золотой,stone=каменный,wooden=деревянный,"
            + "leather=кожаный,chainmail=кольчужный,turtle=черепаший,copper=медный,chestplate=нагрудник,helmet=шлем,leggings=поножи,boots=ботинки,"
            + "sword=меч,pickaxe=кирка,axe=топор,shovel=лопата,hoe=мотыга,bow=лук,crossbow=арбалет,trident=трезубец,shield=щит,elytra=элитры,"
            + "ore=руда,ingot=слиток,block=блок,raw=сырой,log=бревно,planks=доски,cobblestone=булыжник,dirt=земля,sand=песок,gravel=гравий,"
            + "glass=стекло,obsidian=обсидиан,coal=уголь,redstone=редстоун,lapis=лазурит,lazuli=лазурит,emerald=изумруд,quartz=кварц,"
            + "scrap=обломок,apple=яблоко,bread=хлеб,beef=говядина,cooked=жареный,steak=стейк,porkchop=свинина,chicken=курица,potato=картофель,"
            + "carrot=морковь,wheat=пшеница,pearl=жемчуг,ender=эндер,blaze=огненный,rod=стержень,star=звезда,totem=тотем,undying=бессмертия,"
            + "enchanted=зачарованный,book=книга,bottle=бутылочка,experience=опыта,shulker=шалкер,box=ящик,shell=панцирь,bone=кость,string=нить,"
            + "gunpowder=порох,slime=слизь,ball=шар,sugar=сахар,cane=тростник,pumpkin=тыква,melon=арбуз,slice=долька,cocoa=какао,beans=бобы,"
            + "bamboo=бамбук,sapling=саженец,oak=дуб,spruce=ель,birch=береза,jungle=джунгли,acacia=акация,dark=темный,cherry=вишня,mangrove=мангр,"
            + "white=белый,black=черный,concrete=бетон,wool=шерсть,brick=кирпич,bricks=кирпичи,amethyst=аметист,shard=осколок,tear=слеза,ghast=гаст,"
            + "prismarine=призмарин,sponge=губка,chest=сундук,barrel=бочка,furnace=печь,table=стол,crafting=верстак,torch=факел,arrow=стрела,"
            + "bucket=ведро,water=водяной,lava=лавовый,shears=ножницы,anvil=наковальня,enchanting=зачарования,beacon=маяк,tnt=динамит,"
            + "fishing=удочка,compass=компас,clock=часы,map=карта,saddle=седло,lead=поводок,egg=яйцо,spawn=призыва,ender_eye=око,eye=око,"
            + "end=края,crystal=кристалл,portal=портал,frame=рамка,dragon=дракона,head=голова,skull=череп,nether=незера,netherrack=незерак,"
            + "soul=душ,sand=песок,soil=почва,grass=трава,leaves=листва,slab=плита,stairs=ступени,fence=забор,door=дверь,trapdoor=люк,"
            + "button=кнопка,lever=рычаг,piston=поршень,hopper=воронка,dispenser=раздатчик,dropper=выбрасыватель,observer=наблюдатель,"
            + "rail=рельсы,minecart=вагонетка,boat=лодка,bed=кровать,sign=табличка,lantern=фонарь,candle=свеча,banner=флаг,carpet=ковер,"
            + "terracotta=терракота,glazed=глазурованная,pane=панель,stained=окрашенное,red=красный,blue=синий,green=зеленый,yellow=желтый,"
            + "pink=розовый,purple=фиолетовый,orange=оранжевый,gray=серый,light=светлый,cyan=бирюзовый,lime=лаймовый,magenta=пурпурный,brown=коричневый").split(",");
        for (String s : d) { String[] kv = s.split("="); W.put(kv[0], kv[1]); }
    }

    private RuNames() { }

    public static String english(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String w : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(w);
        }
        return sb.toString();
    }

    public static String generated(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String w : m.name().toLowerCase(Locale.ROOT).split("_")) {
            String t = W.get(w);
            if (t == null) return null;
            if (sb.length() > 0) sb.append(' ');
            sb.append(t);
        }
        return sb.toString();
    }

    public static String norm(String s) {
        s = s.toLowerCase(Locale.ROOT).replace('ё', 'е').replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) sb.append(Character.isLetterOrDigit(c) ? c : ' ');
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    /** Строка для поиска: id + английское + русское (shop.yml, иначе авто). */
    public static String haystack(SantikCore pl, Material m) {
        String ru = null;
        var it = pl.shop().get(m);
        if (it != null) ru = it.ru();
        if (ru == null) ru = generated(m);
        return norm(m.name() + " " + english(m) + " " + (ru == null ? "" : ru));
    }

    /** Основы слов запроса (первые 5 букв для кириллицы) для LIKE-поиска. */
    public static java.util.List<String> stems(String query) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String t : norm(query).split(" ")) {
            if (t.isEmpty()) continue;
            boolean cyr = t.chars().anyMatch(c -> c >= 'а' && c <= 'я');
            out.add(cyr && t.length() > 5 ? t.substring(0, 5) : t);
        }
        return out;
    }
}
