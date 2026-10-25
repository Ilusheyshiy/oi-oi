package moodygod;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Сцены с Кларком. Платформа из дерна 41x41 на высоте 220 остаётся в мире.
 * Персонажи — настоящие объекты на сервере (item_display с пиксель-текстурами, всегда лицом к игроку).
 * Реплики — text_display над головами, печатаются сервером с интонацией.
 */
final class Scene {
    static boolean active, built, restaurant;
    static int cx, cz;
    static final int PY = 220;
    static final Map<UUID, double[]> BACK = new HashMap<>();
    static final Map<String, Integer> TOKEN = new HashMap<>();
    static final Map<String, Integer> TYPED = new HashMap<>();
    static final Map<String, String> FULL = new HashMap<>();
    static final Map<Character, double[]> POS = new HashMap<>();
    static int godLines = 0;

    // ---------------- реплики ----------------
    static final String[] MONO = {
        "Так. Присели. Тема лекции: «Вкусно и точка», она же ЕДА ДЛЯ ТЕХ, КТО НЕНАВИДИТ СЕБЯ!",
        "Это не бургер, это ПРЕСС-ФОРМА ОБИДЫ с булкой! Булка там как моя вера в вас: мягкая и ПУСТАЯ!",
        "Картошка фри? Нет, это ХОЛОДНЫЕ СОПЛИ ИЗ ФРИТЮРА!",
        "А кола?! Кола там — это ВОДА, которая притворяется ГАЗОМ!",
        "И вообще, кто придумал эту точку в названии?! Тот, кто хотел поставить точку на ВКУСЕ! И поставил! Потому что внутри нагетсов ЖИВЁТ"
    };

    // S|кто|брови|тайминг|текст.  ~NN — начать, когда предыдущая реплика напечатана на NN% (перебивает), +MS — через MS мс после конца.
    static final String[] SCRIPT1 = {
        "CAM|90",
        "S|M|3|+300|Я не понимаю, что вы от меня хотите...",
        "S|C|1|~85|Я архитектор, бЛяттьб… Я хочу построить Вкусно и точка рядом со своим домом, чтобы он работал 24/7 без перебоев!",
        "S|M|0|~85|Это невозможно.", "S|C|1|~50|Нет, возможно!", "S|M|1|~60|Нет!", "S|C|1|~40|Нет, возможно!",
        "S|M|1|~70|Докажите, что вы архитектор!", "S|C|1|~50|Я архитектор!", "S|M|0|~60|Где ваш бейджик?", "S|C|1|~70|У тебя в жопе!",
        "S|M|2|+500|Оу, мы дошли до БДСМ.", "S|C|1|~85|Нет, я хочу Вкусно и точка!",
        "S|M|1|~70|Это невозможно!", "S|C|1|~60|Нет, возможно!", "S|M|1|~60|Нет!", "S|C|1|~50|Нет, возможно!",
        "S|M|1|~70|СУЙ!!!", "S|C|2|+700|Что совать?", "S|M|0|+900|Ананас в жопу.", "S|C|0|+700|Ладно.",
        "CAM|0", "GOD|Че за хуйня?..", "CAM|90",
        "S|C|0|+500|Я хочу Вкусно и точка, понимаете?", "S|M|0|~85|Понимаю.",
        "S|C|0|~70|Я уже придумал план, как я буду строить это, как это… хэ-хэ, понимаете? Находится с моим домом и всякое прочее.",
        "S|M|0|~85|Вы будете единственным посетителем в этом заведении?",
        "S|C|2|~70|Эу… ну, как бы, да, я так и планировал…",
        "S|C|0|+900|В чём проблема? Здание 125 квадратных метров мне хватит.",
        "S|M|2|~85|Сколько, простите?", "S|C|0|~70|Сто… двадцать пять квадратных метров.",
        "S|M|2|~85|Но у вас квартира восемь на восемь… Это безуми-",
        "S|C|1|~55|Я АРХИТЕКТОР, БЛЯТЬ! Я РАСПЛАНИРОВАЛ ТАК, ЧТОБЫ ВКУСНО И ТОЧКА ВЫХОДИЛ К ДОМУ МОЕГО СОСЕДА, КОТОРОГО ВЫСЕЛЯТ, И ОН БУДЕТ ЖИТЬ НА УЛИЦЕ!",
        "S|M|2|+900|Кто у вас будет работать?", "S|C|0|~85|Мой папа.", "S|M|0|~85|Можно с ним поговорить?",
        "RISE|K",
        "S|K|1|+0|ААА, ОГУЗОК! ДОРМОЕД ТЫ ТУПОЙ, ЧТО ТЫ НАДЕЛАЛ? ПРИДУРОК! ТЕБЕ БЫЛО СКАЗАНО ЗАПИХНУТЬ АНАНАС В ЖОПУ, НАХРЕНА ТЫ В МОЮ-ТО ЕГО ЗАПИХНУЛ?",
        "S|C|3|~45|Я НЕ ХОТЕЛ, ПАПА!",
        "WAIT|2000", "END|"
    };

    static final String[] SCRIPT2 = {
        "S|C|3|+800|У меня получилось..!",
        "CLOSE|", "BACK|",
        "S|C|3|+300|*шмыг* Я в это не верю… Это реально?",
        "RISE|K",
        "S|K|3|+300|Сынок!", "S|C|3|~80|Папа!",
        "S|K|0|+400|Прости, что я в тебя не верил! Ты. Вправду отличный архитектор.",
        "APPEAR|V",
        "S|V|0|+500|А я всегда в него верил. Я верил, что он построит Вкусно и точка.",
        "S|C|2|~85|Лысый Качок… ты тоже здесь? Спасибо!",
        "S|V|0|~85|Ведь он отличный архитектор. И он делал это для семьи.",
        "APPEAR|M",
        "S|M|3|+500|Да простите вы нас, пожалуйста!",
        "S|C|3|~85|Я прощаю вас! Спасибо вам! Спасибо вам большое всем!",
        "APPEAR|O",
        "S|O|0|+500|Я всегда в него верил, что он сможет построить Вкусно и точка.",
        "WAIT|1500", "GODEND|", "END|"
    };

    // ---------------- справочники ----------------
    static String key(char w) {
        return switch (w) { case 'C' -> "clark"; case 'M' -> "mary"; case 'K' -> "chef"; case 'V' -> "kachok"; default -> "oguzok"; };
    }
    static double H(char w) { return switch (w) { case 'M' -> 2.0; case 'K' -> 2.5; case 'V' -> 2.4; case 'O' -> 2.3; default -> 2.3; }; }
    static String color(char w) { return switch (w) { case 'C' -> "yellow"; case 'M' -> "aqua"; case 'K' -> "red"; case 'V' -> "green"; default -> "white"; }; }
    static String model(char w, int b, int m) { return "moody_god:" + key(w) + "_" + b + m; }
    static String f(double d) { return String.format(Locale.ROOT, "%.2f", d); }
    static void in(MinecraftServer s, String c) { GodMod.serverCmd(s, "execute in minecraft:overworld run " + c); }
    static String fill(int x0, int y0, int z0, int x1, int y1, int z1, String b) {
        return "fill " + x0 + " " + y0 + " " + z0 + " " + x1 + " " + y1 + " " + z1 + " " + b;
    }
    static final String ID = "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]";

    // ---------------- объекты на сервере ----------------
    static void spawnSprite(MinecraftServer s, char who, double x, double z, int state) {
        double h = H(who);
        String sc = state == 1 ? "0.0f" : f(h) + "f";
        String ty = state == 2 ? "-" + f(h * 1.1) + "f" : "0.0f";
        POS.put(who, new double[]{x, z});
        in(s, "summon minecraft:item_display " + f(x) + " " + f(PY + h / 2) + " " + f(z)
            + " {Tags:[\"sc\",\"sc_" + who + "\"],billboard:\"vertical\",brightness:{sky:15,block:15},view_range:4.0f,"
            + "item:{id:\"minecraft:stick\",count:1,components:{\"minecraft:item_model\":\"" + model(who, 0, 0) + "\"}},"
            + "transformation:{" + ID + ",translation:[0.0f," + ty + ",0.0f],scale:[" + sc + "," + sc + "," + sc + "]}}");
    }
    static void setFrame(MinecraftServer s, char who, int b, int m) {
        in(s, "execute as @e[tag=sc_" + who + "] run data modify entity @s item.components.\"minecraft:item_model\" set value \"" + model(who, b, m) + "\"");
    }
    static void anim(MinecraftServer s, char who, String tr, String sc, int ticks) {
        in(s, "execute as @e[tag=sc_" + who + "] run data merge entity @s {start_interpolation:0,interpolation_duration:" + ticks
            + ",transformation:{" + ID + ",translation:" + tr + ",scale:" + sc + "}}");
    }
    static void appear(MinecraftServer s, char who) {
        String sc = "[" + f(H(who)) + "f," + f(H(who)) + "f," + f(H(who)) + "f]";
        anim(s, who, "[0.0f,0.0f,0.0f]", sc, 12);
        in(s, "execute as @e[tag=sc_" + who + "] at @s run particle minecraft:poof ~ ~1 ~ 0.4 0.6 0.4 0.05 30");
        in(s, "execute as @e[tag=sc_" + who + "] at @s run playsound minecraft:entity.illusioner.cast_spell master @a ~ ~ ~ 1 1.3");
    }
    static void rise(MinecraftServer s, char who) {
        String sc = "[" + f(H(who)) + "f," + f(H(who)) + "f," + f(H(who)) + "f]";
        anim(s, who, "[0.0f,0.0f,0.0f]", sc, 22);
        in(s, "execute as @e[tag=sc_" + who + "] at @s run particle block{block_state:\"minecraft:dirt\"} ~ ~0.2 ~ 0.5 0.2 0.5 0.1 40");
        in(s, "execute as @e[tag=sc_" + who + "] at @s run playsound minecraft:block.gravel.break master @a ~ ~ ~ 2 0.6");
    }

    // ---------------- речь (текст над головой, интонация по знакам препинания) ----------------
    static boolean angry(String t) {
        int up = 0, lo = 0;
        for (char c : t.toCharArray()) { if (Character.isUpperCase(c)) up++; else if (Character.isLowerCase(c)) lo++; }
        return up > 4 && up > lo;
    }
    static String clean(String t) { return t.replace("\"", "»").replace("\\", ""); }
    static void setBubble(MinecraftServer s, char who, String text) {
        in(s, "data modify entity @e[tag=bb_" + who + ",limit=1] text set value {text:\"" + clean(text) + "\",color:\"" + color(who) + "\"}");
    }
    static void note(MinecraftServer s, char who, double x, double y, double z, float pitch, float vol, boolean ang) {
        float p = Math.max(0.5f, Math.min(2f, pitch));
        String at = f(x) + " " + f(y) + " " + f(z) + " ";
        switch (who) {
            case 'C' -> { // «полу бас, полу высокая нота»
                in(s, "playsound minecraft:block.note_block.bass master @a " + at + f(1.4 * vol) + " " + f(Math.max(0.5, p * (ang ? 0.5 : 0.6))));
                in(s, "playsound minecraft:block.note_block.bit master @a " + at + f(0.9 * vol) + " " + f(p * 1.8));
            }
            case 'M' -> in(s, "playsound minecraft:block.note_block.flute master @a " + at + f(1.3 * vol) + " " + f(p * 1.15));
            case 'K' -> in(s, "playsound minecraft:block.note_block.didgeridoo master @a " + at + f(1.7 * vol) + " " + f(p * 0.75));
            case 'V' -> in(s, "playsound minecraft:block.note_block.bass master @a " + at + f(1.5 * vol) + " " + f(p * 0.6));
            default -> in(s, "playsound minecraft:block.note_block.bit master @a " + at + f(1.2 * vol) + " " + f(p * 1.35));
        }
    }

    static int speak(MinecraftServer s, char who, int brow, String raw) {
        final String text = clean(raw);
        final String bk = "b" + who;
        final int token = TOKEN.merge(bk, 1, Integer::sum);
        FULL.put(bk, text); TYPED.put(bk, 0);
        double[] pos = POS.getOrDefault(who, new double[]{cx, cz});
        double h = H(who);
        in(s, "kill @e[tag=bb_" + who + "]");
        in(s, "summon minecraft:text_display " + f(pos[0]) + " " + f(PY + h + 0.6) + " " + f(pos[1])
            + " {Tags:[\"sc\",\"bb_" + who + "\"],billboard:\"vertical\",alignment:\"center\",line_width:260,background:0,shadow:1b,"
            + "brightness:{sky:15,block:15},view_range:4.0f,text:{text:\" \",color:\"" + color(who) + "\"},"
            + "transformation:{" + ID + ",translation:[0.0f,0.0f,0.0f],scale:[1.6f,1.6f,1.6f]}}");
        setFrame(s, who, brow, 0);
        final Intonation.Plan pl = Intonation.plan(text);
        final boolean ang = angry(text);
        final int n = text.length();
        for (int i = 0; i < n; i++) {
            final int k = i;
            GodMod.schedule(pl.t()[i] / 50, () -> {
                if (!Objects.equals(TOKEN.get(bk), token)) return;
                TYPED.put(bk, k + 1);
                setBubble(s, who, text.substring(0, k + 1));
                char c = text.charAt(k);
                if (c != ' ') {
                    note(s, who, pos[0], PY + h, pos[1], pl.pitch()[k] * (ang ? 0.9f : 1f), pl.vol()[k] * (ang ? 1.5f : 1f), ang);
                    setFrame(s, who, brow, k % 2 == 0 ? 1 : 0);
                }
            });
        }
        final int dur = pl.t()[n] / 50 + 2;
        GodMod.schedule(dur, () -> { if (Objects.equals(TOKEN.get(bk), token)) setFrame(s, who, brow, 0); });
        return dur;
    }

    /** Перебитая реплика: обрывается на напечатанном и ставится тире. */
    static void cut(MinecraftServer s, char who) {
        String bk = "b" + who;
        TOKEN.merge(bk, 1, Integer::sum);
        String full = FULL.getOrDefault(bk, "");
        int k = Math.min(TYPED.getOrDefault(bk, 0), full.length());
        if (k > 0 && k < full.length()) setBubble(s, who, full.substring(0, k) + "—");
        setFrame(s, who, 0, 0);
    }

    // ---------------- текст Бога как объект в мире ----------------
    static void godText(MinecraftServer s, String raw, boolean typed) {
        final String text = clean(raw);
        final String tag = "gt" + (godLines++);
        double x = cx + 0.5, y = PY + 3.0 - 0.9 * (godLines - 1), z = cz + 7.0;
        in(s, "summon minecraft:text_display " + f(x) + " " + f(y) + " " + f(z)
            + " {Tags:[\"sc\",\"" + tag + "\"],billboard:\"vertical\",alignment:\"center\",line_width:300,background:0,shadow:1b,"
            + "brightness:{sky:15,block:15},view_range:4.0f,text:{text:\"" + (typed ? " " : text) + "\",color:\"red\"},"
            + "transformation:{" + ID + ",translation:[0.0f,0.0f,0.0f],scale:[2.6f,2.6f,2.6f]}}");
        if (!typed) return;
        Intonation.Plan pl = Intonation.plan(text);
        for (int i = 0; i < text.length(); i++) {
            final int k = i;
            GodMod.schedule(pl.t()[i] / 50, () -> {
                in(s, "data modify entity @e[tag=" + tag + ",limit=1] text set value {text:\"" + text.substring(0, k + 1) + "\",color:\"red\"}");
                if (text.charAt(k) != ' ')
                    in(s, "playsound minecraft:block.note_block.bit master @a " + f(x) + " " + f(y) + " " + f(z) + " 1.2 " + f(Math.max(0.5, Math.min(2.0, pl.pitch()[k] * 0.9))));
            });
        }
    }

    // ---------------- построение ----------------
    static void ensurePlatform(MinecraftServer s) {
        GodMod.serverCmd(s, "execute in minecraft:overworld run forceload add " + (cx - 21) + " " + (cz - 21) + " " + (cx + 21) + " " + (cz + 21));
        if (built) return;
        built = true;
        in(s, fill(cx - 20, 217, cz - 20, cx + 20, 218, cz + 20, "minecraft:dirt"));
        in(s, fill(cx - 20, 219, cz - 20, cx + 20, 219, cz + 20, "minecraft:grass_block"));
        in(s, fill(cx - 20, 220, cz - 20, cx + 20, 240, cz + 20, "minecraft:air"));
    }

    /** «Вкусно — и точка»: стеклянный фасад, тёмно-зелёный и оранжевый (цвета бренда), логотип — две оранжевые «палочки» и красная точка. */
    static void buildRestaurant(MinecraftServer s) {
        if (restaurant) return;
        restaurant = true;
        int x0 = cx - 8, x1 = cx + 8, z0 = cz + 6, z1 = cz + 16;
        in(s, fill(x0, 220, z0, x1, 220, z1, "minecraft:light_gray_concrete"));
        in(s, fill(x0, 221, z0, x1, 225, z1, "minecraft:white_concrete"));
        in(s, fill(x0 + 1, 221, z0 + 1, x1 - 1, 225, z1 - 1, "minecraft:air"));
        in(s, fill(x0, 226, z0, x1, 226, z1, "minecraft:green_concrete"));
        in(s, fill(x0, 221, z0, x1, 223, z0, "minecraft:glass"));
        in(s, fill(x0, 224, z0, x1, 225, z0, "minecraft:green_concrete"));
        for (int px : new int[]{x0, cx - 4, cx + 4, x1}) in(s, fill(px, 221, z0, px, 223, z0, "minecraft:green_concrete"));
        in(s, fill(cx - 1, 221, z0, cx + 1, 222, z0, "minecraft:air"));
        in(s, fill(cx - 5, 227, z0 + 1, cx + 5, 232, z0 + 1, "minecraft:green_concrete"));
        in(s, fill(cx - 4, 228, z0, cx - 4, 231, z0, "minecraft:orange_concrete"));
        in(s, fill(cx - 2, 228, z0, cx - 2, 231, z0, "minecraft:orange_concrete"));
        in(s, fill(cx + 1, 229, z0, cx + 2, 230, z0, "minecraft:red_concrete"));
        in(s, fill(cx - 5, 227, z0, cx + 5, 227, z0, "minecraft:sea_lantern"));
        in(s, fill(x0 + 1, 225, z0 + 3, x1 - 1, 225, z0 + 3, "minecraft:sea_lantern"));
        in(s, fill(x0 + 1, 225, z0 + 7, x1 - 1, 225, z0 + 7, "minecraft:sea_lantern"));
        in(s, fill(cx - 6, 221, z1 - 3, cx + 6, 221, z1 - 3, "minecraft:quartz_block"));
        in(s, fill(cx - 6, 222, z1 - 3, cx + 6, 222, z1 - 3, "minecraft:quartz_slab"));
        for (int tx : new int[]{cx - 6, cx - 2, cx + 2, cx + 6}) {
            in(s, "setblock " + tx + " 221 " + (z0 + 4) + " minecraft:oak_fence");
            in(s, "setblock " + tx + " 222 " + (z0 + 4) + " minecraft:oak_pressure_plate");
        }
        in(s, "summon minecraft:text_display " + f(cx + 0.5) + " 224.9 " + f(z0 - 0.4)
            + " {Tags:[\"vitpl\"],billboard:\"vertical\",alignment:\"center\",background:0,shadow:1b,brightness:{sky:15,block:15},view_range:6.0f,"
            + "text:{text:\"ВКУСНО — И ТОЧКА\",color:\"gold\",bold:1b},transformation:{" + ID + ",translation:[0.0f,0.0f,0.0f],scale:[4.0f,4.0f,4.0f]}}");
    }

    // ---------------- запуск сцен ----------------
    static void initCenter(List<ServerPlayer> ps) {
        if (built) return;
        cx = ps.get(0).blockPosition().getX();
        cz = ps.get(0).blockPosition().getZ();
    }
    static void saveBack(List<ServerPlayer> ps) {
        BACK.clear();
        for (ServerPlayer p : ps) BACK.put(p.getUUID(), new double[]{p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()});
    }
    static void tpTo(MinecraftServer s, List<ServerPlayer> ps, double z, float yaw, float pitch) {
        int i = 0;
        for (ServerPlayer p : ps) {
            if (GodMod.online(s, p))
                GodMod.cmd(s, p, "run tp @s " + f(cx + 0.5 + (i % 2 == 0 ? -0.8 : 0.8) * ((i + 1) / 2 + 1)) + " " + PY + " " + f(z) + " " + f(yaw) + " " + f(pitch));
            i++;
        }
    }

    static void start1(MinecraftServer s, List<ServerPlayer> ps) {
        active = true;
        initCenter(ps);
        saveBack(ps);
        godLines = 0;
        int t1 = GodMod.say(ps, Lines.pages("Так. Идите-ка сюда. На свежий воздух. Поговорим."), 4);
        GodMod.schedule(t1, () -> {
            ensurePlatform(s);
            GodMod.schedule(40, () -> {
                in(s, "kill @e[tag=sc]");
                spawnSprite(s, 'C', cx + 0.5 - 6.0, cz + 0.5 + 1.4, 0);
                spawnSprite(s, 'M', cx + 0.5 - 6.0, cz + 0.5 - 1.8, 0);
                spawnSprite(s, 'K', cx + 0.5 - 6.0, cz + 0.5 + 4.2, 2);
                GodMod.hold(ps, Long.MAX_VALUE);
                GodMod.cmdAll(s, ps, "run effect give @s minecraft:resistance 900 4 true");
                tpTo(s, ps, cz + 0.5, 0f, 0f);
                GodMod.sendFx(ps, "camlock", "0,0,400", 0);
                GodMod.schedule(15, () -> monologue(s, ps, SCRIPT1, 1));
            });
        });
    }

    static void start2(MinecraftServer s, List<ServerPlayer> ps) {
        active = true;
        initCenter(ps);
        saveBack(ps);
        godLines = 0;
        int t1 = GodMod.say(ps, Lines.pages("Идите сюда. Я чую фритюр. Опять он. Опять эта точка."), 4);
        GodMod.schedule(t1, () -> {
            ensurePlatform(s);
            buildRestaurant(s);
            GodMod.schedule(40, () -> {
                in(s, "kill @e[tag=sc]");
                double z = cz + 2.0;
                spawnSprite(s, 'C', cx + 0.5, z, 0);
                spawnSprite(s, 'K', cx + 4.5, z, 2);
                spawnSprite(s, 'V', cx + 1.9, z, 1);
                spawnSprite(s, 'M', cx - 2.5, z, 1);
                spawnSprite(s, 'O', cx - 5.0, z, 1);
                setFrame(s, 'C', 3, 0);
                GodMod.hold(ps, Long.MAX_VALUE);
                GodMod.cmdAll(s, ps, "run effect give @s minecraft:resistance 900 4 true");
                tpTo(s, ps, cz - 8.0, 0f, 0f);
                GodMod.sendFx(ps, "camlock", "0,0,400", 0);
                GodMod.schedule(25, () -> play(s, ps, SCRIPT2, () -> end(s, ps)));
            });
        });
    }

    static void monologue(MinecraftServer s, List<ServerPlayer> ps, String[] script, int sceneNo) {
        List<String> pages = new ArrayList<>();
        for (String t : MONO) pages.add(Lines.wrap(t, 26));
        int last = pages.size() - 1;
        int ms = 0;
        for (int i = 0; i < last; i++) ms += GodPayload.pageMs(pages.get(i));
        String lp = pages.get(last);
        Intonation.Plan pl = Intonation.plan(lp);
        int target = (int) (pl.t()[lp.length()] * 0.55);
        int k = 0;
        while (k < lp.length() && pl.t()[k] < target) k++;
        String typed = lp.substring(0, k).replace("\n", " ").trim();
        int irq = (ms + pl.t()[k] + 500) / 50;
        GodMod.say(ps, pages, 4);
        GodMod.schedule(irq, () -> {
            GodMod.sendFx(ps, "dualtype", "", 0);
            GodMod.sendFx(ps, "hudclear", "", 0);
            godText(s, typed + "—", false);   // текст уходит с клиента и зависает в мире
            GodMod.schedule(40, () -> play(s, ps, script, () -> end(s, ps)));
        });
    }

    // ---------------- движок сценария ----------------
    static void play(MinecraftServer s, List<ServerPlayer> ps, String[] script, Runnable done) {
        int prevStart = 0, prevEnd = 0;
        char prevWho = 0;
        for (String line : script) {
            final String[] fl = line.split("\\|", 5);
            switch (fl[0]) {
                case "S" -> {
                    final char who = fl[1].charAt(0);
                    final int brow = Integer.parseInt(fl[2]);
                    final String text = fl[4];
                    int dur = Intonation.plan(clean(text)).t()[clean(text).length()] / 50 + 2;
                    int start = fl[3].startsWith("~")
                        ? prevStart + (int) ((prevEnd - prevStart) * Integer.parseInt(fl[3].substring(1)) / 100.0)
                        : prevEnd + Integer.parseInt(fl[3].substring(1)) / 50;
                    final char pw = prevWho;
                    final boolean cutPrev = start < prevEnd && pw != 0;
                    GodMod.schedule(start, () -> { if (cutPrev) cut(s, pw); speak(s, who, brow, text); });
                    prevStart = start; prevEnd = start + dur; prevWho = who;
                }
                case "CAM" -> {
                    final String yaw = fl[1];
                    int at = prevEnd + 8;
                    GodMod.schedule(at, () -> GodMod.sendFx(ps, "camlock", yaw + ",0,150", 0));
                    prevStart = at; prevEnd = at + 40; prevWho = 0;
                }
                case "GOD" -> {
                    final String text = fl[1];
                    int at = prevEnd + 10;
                    GodMod.schedule(at, () -> godText(s, text, true));
                    int dur = Intonation.plan(text).t()[text.length()] / 50 + 70;   // напечатать + подержать, потом камера уйдёт
                    prevStart = at; prevEnd = at + dur; prevWho = 0;
                }
                case "RISE" -> { final char w = fl[1].charAt(0); int at = prevEnd + 5; GodMod.schedule(at, () -> rise(s, w)); prevStart = at; prevEnd = at + 28; }
                case "APPEAR" -> { final char w = fl[1].charAt(0); int at = prevEnd + 5; GodMod.schedule(at, () -> appear(s, w)); prevStart = at; prevEnd = at + 18; }
                case "CLOSE" -> {
                    int at = prevEnd + 10;
                    GodMod.schedule(at, () -> { tpTo(s, ps, cz - 1.0, 0f, -6f); GodMod.sendFx(ps, "camlock", "0,-6,400", 0); });
                    prevStart = at; prevEnd = at + 75; prevWho = 0;
                }
                case "BACK" -> {
                    int at = prevEnd + 5;
                    GodMod.schedule(at, () -> { tpTo(s, ps, cz - 8.0, 0f, 0f); GodMod.sendFx(ps, "camlock", "0,0,400", 0); });
                    prevStart = at; prevEnd = at + 15; prevWho = 0;
                }
                case "WAIT" -> prevEnd += Integer.parseInt(fl[1]) / 50;
                case "GODEND" -> {
                    int at = prevEnd + 10;
                    final List<String> pg = Lines.pages("Это что, ХЭППИ-ЭНД ДЛЯ ФАСТФУДА?! ВЫКЛЮЧИТЕ ЭТО! МЕНЯ ТОШНИТ!");
                    GodMod.schedule(at, () -> {
                        in(s, "execute as @e[tag=sc_C] at @s run particle minecraft:firework ~ ~2 ~ 1.5 1 1.5 0.1 80");
                        GodMod.say(ps, pg, 4);
                    });
                    int d = 0; for (String x : pg) d += GodPayload.pageMs(x) / 50;
                    prevStart = at; prevEnd = at + d + 20;
                }
                case "END" -> GodMod.schedule(prevEnd + 20, done);
                default -> { }
            }
        }
    }

    static void end(MinecraftServer s, List<ServerPlayer> ps) {
        GodMod.sendFx(ps, "camunlock", "", 0);
        GodMod.hold(ps, 0);
        for (ServerPlayer p : ps) {
            double[] b = BACK.get(p.getUUID());
            if (b != null && GodMod.online(s, p)) {
                GodMod.cmd(s, p, String.format(Locale.ROOT, "run tp @s %.2f %.2f %.2f %.1f %.1f", b[0], b[1], b[2], b[3], b[4]));
                GodMod.cmd(s, p, "run effect clear @s minecraft:resistance");
            }
        }
        GodMod.schedule(40, () -> {
            in(s, "kill @e[tag=sc]");
            GodMod.serverCmd(s, "execute in minecraft:overworld run forceload remove " + (cx - 21) + " " + (cz - 21) + " " + (cx + 21) + " " + (cz + 21));
            active = false;
            GodMod.finishVisit(s);
        });
    }
}
