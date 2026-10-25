package moodygod;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Правила выполнимы и избегаемы: один раз получил пизды — запомнил и больше не делаешь. */
public final class Rules {
    public static final class PState {
        public final Map<String, Integer> m = new HashMap<>();
        public final StringBuilder chat = new StringBuilder();
        public boolean wasGround = true;
        public double lastX, lastY, lastZ;
        public int look;
        public long prayTick;
        public void inc(String k) { m.merge(k, 1, Integer::sum); }
        public int n(String k) { return m.getOrDefault(k, 0); }
        public void reset() { m.clear(); chat.setLength(0); }
    }

    public record Ctx(ServerPlayer p, ServerLevel lv, PState s, List<ServerPlayer> all) {}
    /** kind 0 — событие за интервал; kind 1 — «аудиенция»: требование озвучивается в начале визита. */
    public record Rule(String id, int kind, String demand, String[] bad, String pun, ToIntFunction<Ctx> eval) {}

    // ---------- хелперы ----------
    static int n(Ctx c, String k) { return c.s().n(k); }
    static int pm(boolean good, boolean bad) { return good ? 1 : bad ? -1 : 0; }
    static boolean said(Ctx c, String w) { return c.s().chat.indexOf(w) >= 0; }
    static float yd(Ctx c, float target) { return Math.abs(Mth.wrapDegrees(c.p().getYRot() - target)); }

    public static boolean is(Entity e, String id) { return EntityType.getKey(e.getType()).getPath().equals(id); }
    public static int countIf(ServerPlayer p, Predicate<ItemStack> f) {
        int c = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && f.test(st)) c += st.getCount();
        }
        return c;
    }
    public static int count(ServerPlayer p, Item it) { return countIf(p, s -> s.is(it)); }

    static Rule ev(String id, String pun, String b1, String b2, ToIntFunction<Ctx> f) {
        return new Rule(id, 0, null, new String[]{b1, b2}, pun, f);
    }
    static Rule au(String id, String pun, String demand, String b1, String b2, ToIntFunction<Ctx> f) {
        return new Rule(id, 1, demand, new String[]{b1, b2}, pun, f);
    }

    /** «Первый ... в мире» берётся только помолившись (присесть и покрутить головой вверх-вниз). */
    public static final String[][] FIRST_ITEMS = {
        {"crafting_table", "верстак"}, {"furnace", "печка"}, {"bucket", "ведро"}, {"bed", "кровать"}, {"diamond", "алмаз"}
    };
    public static final String[][] FIRST_BLOCKS = {
        {"stone", "камень"}, {"logs", "бревно"}, {"iron", "железная руда"}, {"coal", "угольная руда"}, {"diamond", "алмазная руда"}
    };

    public static final List<Rule> EVENTS = build();
    public static final List<Rule> AUDIENCE = List.of(
        au("a_east", "balance", "Прямо сейчас: смотри на пчелу, которая летит на востоке! На ВОСТОК, слышишь?!",
            "Ты смотрел не туда! Пчела была на востоке, а ты уставился в какую-то херню!",
            "Пчела обиделась и улетела. Из-за тебя. Она была последней в округе.",
            c -> yd(c, -90) < 35 ? 1 : -1),
        au("a_stick", "arm_r", "Прямо сейчас: держи в руке палку! Одну палку! Не меч, не кирку — ПАЛКУ!",
            "Где палка?! Я сказал палку, а у тебя в руке какая-то хренотень!",
            "Без палки со мной разговаривать нельзя. Палка — это уважение. А уважения у тебя нет.",
            c -> c.p().getMainHandItem().is(Items.STICK) ? 1 : -1),
        au("a_bow", "legs", "Прямо сейчас: присядь и поклонись! Бог говорит — смертные приседают!",
            "Ты стоял, как столб! Кланяться надо! Ты что, король?!",
            "Я бог, а ты стоял прямо. Я, конечно, тоже не горжусь, но кланяйся.",
            c -> c.p().isShiftKeyDown() ? 1 : -1)
    );

    static List<Rule> build() {
        List<Rule> l = new ArrayList<>();
        l.add(ev("ironsneak", "arms", "Ты добывал железо СТОЯ! Железо любит поклоны, а ты ему прямую спину!",
            "Я видел: ты долбил железную руду, не присев. Это просто неуважение к металлу.",
            c -> pm(n(c, "ironS") > 0 && n(c, "ironN") == 0, n(c, "ironN") > 0)));
        l.add(ev("coalsneak", "eye_l", "Уголь пачкается и стесняется. А ты долбил его стоя, как нахал!",
            "Ты ломал уголь не на шифте. Теперь уголь расскажет всем, что ты невежа.",
            c -> pm(n(c, "coalS") > 0 && n(c, "coalN") == 0, n(c, "coalN") > 0)));
        l.add(ev("chicken", "chickens", "Ты убил курицу! А это была тётя Люся! У неё остались дети! Куриные!",
            "Курицу бить нельзя! Они мне как родня, а ты — на яйцо и в суп!", c -> n(c, "k_chicken") > 0 ? -1 : 0));
        l.add(ev("cat", "legs", "Ты убил кошку. Убивать кошек могу только Я. Это моя профессия.",
            "Кошки видят всё, а ты их в расход! Теперь они видят и меня, и мне стыдно.", c -> n(c, "k_cat") > 0 ? -1 : 0));
        l.add(ev("wolf", "ear", "Ты убил собаку! Она хотела просто дружить! Ты хуже овцы!",
            "Собака — друг человека, а ты — враг собаки. Значит, я теперь враг тебя.", c -> n(c, "k_wolf") > 0 ? -1 : 0));
        l.add(ev("villager", "arms", "Ты убил жителя! Это мой прихожанин! Прихожан у меня и так три калеки!",
            "Житель — не мишень. Он платит налоги в изумрудах. А ты сломал мне экономику.", c -> n(c, "k_villager") > 0 ? -1 : 0));
        l.add(ev("cow", "tongue", "Ты убил корову. Корова молчит и не жалуется. Это идеальный прихожанин!",
            "Корова мне ничего плохого не сделала, а ты ей стейк. Стыдно!", c -> n(c, "k_cow") > 0 ? -1 : 0));
        l.add(ev("skeleton", "balance", "Ты убил скелета. Его брат Санс теперь пишет на тебя жалобы. Мы все читаем.",
            "Скелеты — друзья. Они хоть не блеют. А ты их костями в мусор.", c -> n(c, "k_skeleton") > 0 ? -1 : 0));
        l.add(ev("flower", "heart", "Ты сломал цветок! Цветок — это Нацуки. Теперь Нацуки плачет, а я утешаю!",
            "Цветы рвать нельзя. Один цветочек — одна обиженная девочка с кексами.", c -> n(c, "flower") > 0 ? -1 : 0));
        l.add(ev("crops", "stone_bread", "Ты ломал урожай. Хлеб растёт сам, ему никто не нужен. Тем более ты.",
            "Урожай — это святое. Ты влез в святое грабельками.", c -> n(c, "crops") > 0 ? -1 : 0));
        l.add(ev("leaves", "vision", "Ты сломал слишком много листьев. Деревья без листвы — как овцы без шерсти: жуть!",
            "Листья — это волосы деревьев. А ты парикмахер-маньяк.", c -> n(c, "leaves") > 15 ? -1 : 0));
        l.add(ev("diamond", "diamond_theft", "Ты добыл мою алмазную руду! Я её туда положил специально для себя!",
            "Алмазы — мои. Ты своровал мои алмазы. Теперь ты вор, а я обиженный бог.", c -> n(c, "diamond") > 0 ? -1 : 0));
        l.add(ev("redstone", "lightning", "Красный камень — это мой бывший. Он ядовитый, а ты его копал. Странный вкус.",
            "Ты ломал красный камень. Я его бросил, так что пусть валяется. А ты нет!", c -> n(c, "redstone") > 0 ? -1 : 0));
        l.add(ev("torch", "eye_r", "Ты ставил факел стоя! Огонь любит скромность, а ты ему прямой взгляд.",
            "Факел поставлен без шифта. Огонёк теперь смущён и горит криво.", c -> n(c, "tn") > 0 ? -1 : 0));
        l.add(ev("chest", "arm_l", "Ты открыл сундук, не присев! Он стеснительный! Теперь у него психотравма!",
            "Сундук открывать надо с почтением, а ты ворвался, как налоговая.", c -> n(c, "chestN") > 0 ? -1 : 0));
        l.add(ev("door", "arm_r", "Ты открыл дверь правой рукой! Правая рука молится, а левая открывает двери! Это закон!",
            "Дверь правой рукой — нарушение. Я не знаю чего, но нарушение.", c -> n(c, "doorMain") > 0 ? -1 : 0));
        l.add(ev("noteblock", "ear", "Ты тыкал нотный блок! Музыка — моя территория! У меня на неё исключительные права!",
            "Нотный блок не твой. Ты сфальшивил, а мне теперь слушать.", c -> n(c, "noteblock") > 0 ? -1 : 0));
        l.add(ev("lever", "sheep_buddy", "Ты дёргал рычаги и кнопки! Это мои пульты управления вселенной!",
            "Кнопки мои, рычаги мои. Ты дёрнул, а у соседа выключился свет.", c -> n(c, "lever") > 0 ? -1 : 0));
        l.add(ev("lag", "tongue", "Ты сказал «лаг»! Слово запрещено! Оно звучит как «благ», а я не благ!",
            "«Лаг» — слово-паразит. Я лаг не люблю, пинг у меня нулевой!", c -> said(c, " лаг") || said(c, " lag ") ? -1 : 0));
        l.add(ev("lol", "tongue", "Ты написал «лол»! Это не смех, это диагноз!",
            "«Лол» запрещено. Смеяться надо без слов, как положено нормальному богу.", c -> said(c, "лол") ? -1 : 0));
        l.add(ev("caps", "ear", "Ты орал капсом! Орать здесь имею право только я!",
            "КАПС — МОЯ ЛИЧНАЯ СОБСТВЕННОСТЬ! ТЫ ПОСЯГНУЛ НА МОЮ ГРОМКОСТЬ!", c -> n(c, "caps") > 0 ? -1 : 0));
        l.add(ev("vkusno", "faces", "Ты произнёс «Вкусно и точка»! Меня тошнит! Меня сейчас вырвет облаками!",
            "Зачем ты это сказал? Теперь у меня во рту вкус холодной картошки.", c -> said(c, "вкусно и точка") || said(c, "вкусно — и точка") ? -1 : 0));
        l.add(ev("deaths", "tax", "Ты умер! Без моего разрешения! Умирать — моя привилегия! Я ещё не умирал, а ты уже!",
            "Умирать без согласования с богом — наглость. Ты помер, а мне бумагу заполнять. YOU DIED, мда.", c -> n(c, "deaths") > 0 ? -1 : 0));
        l.add(ev("nether", "burn", "Ты ходил в Незер. Это территория Сатаны. Ты ему там тапочки приносил?",
            "В Незере жарко и пахнет Сатаной. Ты туда пошёл добровольно? Предатель!", c -> n(c, "netherSec") > 0 ? -1 : 0));
        l.add(ev("dirthand", "arm_l", "Ты копал землю руками! Землю копают лопатой, как нормальные звери!",
            "Земля плачет, когда её роют руками. Она же живая. Я так думаю.", c -> n(c, "dirtHand") > 0 ? -1 : 0));
        l.add(ev("logsaxe", "arm_r", "Ты рубил дерево без топора! Бил кулаком! Это варварство!",
            "Дереву больно, а ты ладошкой. Топор для чего, по-твоему, придуман?", c -> n(c, "logsNoAxe") > 0 ? -1 : 0));
        l.add(ev("stonehand", "arm_l", "Ты ломал камень голой рукой! Камень твёрдый, ты дурак, значит, вы равны!",
            "Камень надо киркой, а не лбом. Хотя лоб у тебя тоже подходящий.", c -> n(c, "stoneHand") > 0 ? -1 : 0));
        l.add(ev("tnt", "burn", "Ты поставил динамит! Это мой любимый фейерверк, а ты без спроса!",
            "Динамит — не игрушка! Динамит — это моя игрушка!", c -> n(c, "placed_tnt") > 0 ? -1 : 0));
        l.add(ev("anvil", "wool_block", "Ты трогал наковальню! Она тяжёлая и несчастная, а ты давишь на её чувства!",
            "Наковальня — это зло. Я её боюсь с детства: она падала мне на ногу.", c -> n(c, "anvil") > 0 ? -1 : 0));

        for (String[] a : FIRST_ITEMS) {
            final String k = "i_" + a[0];
            l.add(first(k, a[1], "Это был первый предмет «" + a[1] + "» в мире! Ты взял его без молитвы! Безбожник!"));
        }
        for (String[] a : FIRST_BLOCKS) {
            final String k = "b_" + a[0];
            l.add(first(k, a[1], "Первый блок «" + a[1] + "» в мире ломают только помолившись! Присесть и покрутить головой вверх-вниз! А ты чё?!"));
        }
        return l;
    }

    static Rule first(String k, String label, String b1) {
        return ev("first_" + k, "wool_block", b1,
            "Первый «" + label + "» берут с молитвой! Это закон! Теперь ты знаешь. Следующий первый — не провали!",
            c -> pm(n(c, "p_" + k) > 0, n(c, "np_" + k) > 0));
    }
}
