package moodygod;

import java.util.*;

/** Наказания. У каждого две реплики Бога, срабатывают СРАЗУ после объявления. Часть откровенно тупые. */
final class Puns {
    /** limb — id для клиентского оверлея (null — нет); drop — {модель, название} отрубленной части (null — нет). */
    record Pun(String id, String[] say, int secs, String[] cmds, String limb, String[] drop, String special, String[] undo) {}
    static final String[] NO = {};
    static Pun p(String id, String s1, String s2, int secs, String limb, String[] drop, String special, String[] undo, String... cmds) {
        return new Pun(id, new String[]{s1, s2}, secs, cmds, limb, drop, special, undo);
    }
    static final String FAT = "run effect give @s minecraft:mining_fatigue {T} ";
    static final String WEAK = "run effect give @s minecraft:weakness {T} ";
    static final String SLOW = "run effect give @s minecraft:slowness {T} ";
    static final String[] ARM = {"limb_arm", "Отрубленная рука"}, LEG = {"limb_leg", "Отрубленная нога"},
        EYE = {"limb_eye", "Вырванный глаз"}, EAR = {"limb_ear", "Отрезанное ухо"}, TON = {"limb_tongue", "Вырезанный язык"},
        HEA = {"limb_heart", "Кусок сердца"}, BRA = {"limb_brain", "Кусок мозга"};

    static final Map<String, Pun> ALL = new LinkedHashMap<>();
    static void add(Pun x) { ALL.put(x.id(), x); }
    static {
        // --- тело (с визуальной частью: оверлей, кровь, отрубленная часть на земле) ---
        add(p("arm_r", "Правая рука отрублена на {T} секунд. Ничего, левой тоже можно жить.", "Отрубаю правую руку. Не плачь, отрастёт. Наверное.",
            60, "arm_r", ARM, null, NO, FAT + "2 true", WEAK + "2 true"));
        add(p("arm_l", "Левая рука отрублена на {T} секунд. Держи правой, умник.", "Левую руку изымаю. Так надо. Не спрашивай кому.",
            60, "arm_l", ARM, null, NO, FAT + "1 true", WEAK + "1 true"));
        add(p("arms", "Обе руки отрублены на {T} секунд. Сможешь только смотреть и жалеть.", "Руки — в утиль. На {T} секунд. Потом верну, если вспомню.",
            90, "arms", ARM, null, NO, FAT + "4 true", WEAK + "3 true"));
        add(p("leg_r", "Правая нога отрублена на {T} секунд. Прыгать больше нечем.", "Правую ногу снимаю. Ходи хромой, как моя совесть.",
            60, "leg_r", LEG, "nojump", NO, SLOW + "1 true"));
        add(p("legs", "Обе ноги отрублены на {T} секунд. Ползи к своей ошибке.", "Ног у тебя больше нет. На {T} секунд. Потом посмотрим.",
            90, "legs", LEG, "nojump", NO, SLOW + "3 true"));
        add(p("eye_l", "Левый глаз выколот на {T} секунд. Красота — это когда смотришь одним.", "Левый глаз мне нужнее, чем тебе. Забираю на {T} секунд.",
            60, "eye_l", EYE, null, NO));
        add(p("eye_r", "Правый глаз выколот на {T} секунд. Смотри левым, не жалуйся.", "Правый глаз — в стакан. На {T} секунд. Вкусный, наверное.",
            60, "eye_r", EYE, null, NO));
        add(p("eyes", "Оба глаза — в стакан. На {T} секунд ты слепой пророк.", "Слепота на {T} секунд. Зато овец не увидишь. Подарок от меня.",
            80, "eyes", EYE, null, NO));
        add(p("ear", "Слух отключён на {T} секунд. Теперь ты не услышишь даже мои проповеди. Жаль.", "Уши — прочь. Слушать меня теперь некому.",
            80, "ear", EAR, null, NO));
        add(p("tongue", "Язык отрезан на {T} секунд. Теперь вместо слов у тебя «беее». Не благодари.", "Речь отнимаю. Всё, что напишешь в чат, превратится в блеяние. Овцы оценят.",
            90, "tongue", TON, "bleat", NO));
        add(p("heart", "Сердце уменьшено до трёх на {T} секунд. Береги его, оно маленькое.", "Забираю половину сердца. Вторую не отдам: она мне нравится.",
            90, "heart", HEA, null, new String[]{"run attribute @s minecraft:max_health base set 20"}, "run attribute @s minecraft:max_health base set 6"));
        // --- мозг ---
        add(p("faces", "Отдел распознавания лиц отключён. На {T} секунд все вокруг с лицом твоего друга. Привыкай.", "Теперь у всех лицо твоего друга. Не знаю зачем, но мне весело.",
            120, "faces", BRA, "faces", NO));
        add(p("balance", "Мозжечок удалён на {T} секунд. Походишь как пьяный. Бесплатно.", "Равновесие забираю. Не падай. Хотя падай, мне весело.",
            80, "balance", BRA, "sway", NO, "run effect give @s minecraft:nausea {T} 0 true"));
        add(p("vision", "Зрительная кора выключена на {T} секунд. Мир станет серым. Как моя жизнь.", "Цвета отменяются на {T} секунд. Любишь чёрно-белое? Теперь любишь.",
            90, "vision", BRA, null, NO));
        // --- откровенно тупые ---
        add(p("wool_block", "Держи блок шерсти. Он заставит тебя понять всю жуть твоего поступка.", "Подарок. Блок шерсти. Смотри на него и осознавай, как всё плохо.",
            0, null, null, null, NO, "run give @s minecraft:white_wool[custom_name=\"Блок осознания\"] 1"));
        add(p("sheep_buddy", "Держи овцу. Теперь она твоя. Вы будете вместе до конца. Это самое страшное наказание, поверь.", "Овца для тебя. Наслаждайся её обществом. Я бы не смог.",
            0, null, null, null, NO, "run summon minecraft:sheep ~2 ~ ~ {CustomName:\"Твой личный ужас\"}"));
        add(p("stone_bread", "Хлеб забираю. Взамен камень. Камень хотя бы не плесневеет.", "Хлеба тебе слишком много. Держи камни, они питательные. Наверное.",
            0, null, null, null, NO, "run clear @s minecraft:bread", "run give @s minecraft:stone[custom_name=\"Хлеб (каменный)\"] 3"));
        add(p("icarus", "Лети, Икар. Сначала вверх, потом вниз. Так положено.", "Сейчас ты узнаешь, что такое высота. И что такое земля.",
            0, null, null, null, NO, "run tp @s ~ ~40 ~", "run effect give @s minecraft:slow_falling 25 0 true"));
        add(p("lightning", "Получи молнию. Прямо в макушку. Я старался.", "Электричество тебе не помешает. Особенно на голове.",
            0, null, null, null, NO, "run summon minecraft:lightning_bolt ~ ~ ~", "run damage @s 4 minecraft:magic"));
        add(p("wool_rain", "С неба сейчас пойдёт шерсть. Это самое ужасное, что я придумал.", "Шерстяной дождь! Страшно? Мне тоже, но ради тебя.",
            0, null, null, null, NO,
            "run summon minecraft:falling_block ~1 ~7 ~ {BlockState:{Name:\"minecraft:white_wool\"},Time:1,HurtEntities:1b,FallHurtAmount:2.0f,FallHurtMax:10}",
            "run summon minecraft:falling_block ~-1 ~8 ~ {BlockState:{Name:\"minecraft:white_wool\"},Time:1,HurtEntities:1b,FallHurtAmount:2.0f,FallHurtMax:10}",
            "run summon minecraft:falling_block ~ ~9 ~1 {BlockState:{Name:\"minecraft:white_wool\"},Time:1,HurtEntities:1b,FallHurtAmount:2.0f,FallHurtMax:10}",
            "run summon minecraft:falling_block ~ ~10 ~-1 {BlockState:{Name:\"minecraft:white_wool\"},Time:1,HurtEntities:1b,FallHurtAmount:2.0f,FallHurtMax:10}"));
        add(p("tax", "Налог на глупость: два уровня опыта и гнилая плоть. Не благодари.", "Штраф: опыт и тухлятина. Тухлятину съешь сам.",
            0, null, null, null, NO, "run experience add @s -2 levels", "run give @s minecraft:rotten_flesh 3"));
        add(p("bed_removal", "Кровати — это овечьи трупы. Конфискую все твои кровати.", "Спать на шерсти нельзя. Забираю кровати. Спи на земле, как собака.",
            0, null, null, null, NO, "run clear @s #minecraft:beds", "run give @s minecraft:stick[custom_name=\"Твоя новая кровать\"] 1"));
        add(p("chickens", "ТЁТЯ ЛЮСЯ! Вперёд! Куры, отомстите за неё!", "Куры падают с неба. Ты же сам виноват.",
            0, null, null, null, NO,
            "run summon minecraft:chicken ~ ~6 ~ {CustomName:\"Тётя Люся\"}", "run summon minecraft:chicken ~1 ~7 ~ {CustomName:\"Тётя Люся\"}",
            "run summon minecraft:chicken ~-1 ~8 ~ {CustomName:\"Тётя Люся\"}", "run summon minecraft:chicken ~ ~9 ~1 {CustomName:\"Тётя Люся\"}",
            "run summon minecraft:chicken ~ ~10 ~-1 {CustomName:\"Тётя Люся\"}"));
        add(p("diamond_theft", "Алмазы мои. Забираю все. Все до одного. Ты и так богатый.", "Верни мои алмазы. Что не вернёшь, отниму.",
            0, null, null, null, NO, "run clear @s minecraft:diamond"));
        add(p("burn", "Ты сгоришь. Совсем чуть-чуть, но всё равно обидно.", "Огонёк! Тебе понравится. Нет, не понравится.",
            0, null, null, null, NO, "run damage @s 4 minecraft:on_fire"));
        add(p("forgot", "Какое наказание?.. Я забыл. Ладно, прощаю. Нет, не прощаю: чихни.", "Хотел наказать, но забыл как. Иди пока.",
            0, null, null, null, NO, "run effect give @s minecraft:nausea 3 0 true"));
    }

    static final String[] BODY = {"arm_r", "arm_l", "arms", "leg_r", "legs", "eye_l", "eye_r", "eyes", "ear", "tongue", "heart", "faces", "balance", "vision"};
    static final String[] DUMB = {"wool_block", "sheep_buddy", "stone_bread", "icarus", "lightning", "tax", "forgot"};

    static Pun get(String id) { return ALL.getOrDefault(id, ALL.get("forgot")); }
    static Pun random(Random r) {
        return r.nextBoolean() ? ALL.get(BODY[r.nextInt(BODY.length)]) : ALL.get(DUMB[r.nextInt(DUMB.length)]);
    }

    static final String[][] GIFTS = {
        {"Кекс Нацуки (н-не для тебя!)", "run give @s minecraft:cookie[custom_name=\"Дар: Кекс Нацуки\"] 4"},
        {"Сычуаньский соус", "run give @s minecraft:honey_bottle[custom_name=\"Дар: Сычуаньский соус\"] 1"},
        {"Пикл Рик (НЕ СТАВЬ!)", "run give @s minecraft:sea_pickle[custom_name=\"Дар: Пикл Рик\"] 1"},
        {"Священная палка", "run give @s minecraft:stick[custom_name=\"Дар: Священная палка\"] 1"},
        {"Месть овцам", "run give @s minecraft:cooked_mutton[custom_name=\"Дар: Месть овцам\"] 4"},
        {"Хлеб-спаситель", "run give @s minecraft:bread[custom_name=\"Дар: Хлеб-спаситель\"] 5"},
        {"Золотая морковка", "run give @s minecraft:golden_carrot[custom_name=\"Дар: Золотая морковка\"] 3"},
        {"Изумруды на пиво", "run give @s minecraft:emerald[custom_name=\"Дар: На пиво\"] 2"},
        {"3 уровня опыта", "run experience add @s 3 levels"},
        {"Регенерация", "run effect give @s minecraft:regeneration 20 1"},
        {"Скорость", "run effect give @s minecraft:speed 60 2"},
        {"Ночное зрение", "run effect give @s minecraft:night_vision 120 0"},
        {"Невидимость", "run effect give @s minecraft:invisibility 60 0"},
        {"Щит от Сатаны", "run give @s minecraft:shield[custom_name=\"Дар: Щит от Сатаны\"] 1"},
        {"Золотой билет", "run give @s minecraft:paper[custom_name=\"Дар: Золотой билет\"] 1"},
        {"Ананас (не в жопу!)", "run give @s minecraft:melon_slice[custom_name=\"Дар: Ананас\"] 3"},
        {"Алмаз (редкий)", "run give @s minecraft:diamond[custom_name=\"Дар: Редкий алмаз\"] 1"},
        {"Жемчуг Края", "run give @s minecraft:ender_pearl[custom_name=\"Дар: Жемчуг\"] 2"},
        {"Тотем бессмертия", "run give @s minecraft:totem_of_undying[custom_name=\"Дар: Страховка\"] 1"},
        {"Нагетс", "run give @s minecraft:cooked_chicken[custom_name=\"Дар: Нагетс\"] 3"},
        {"Бекон", "run give @s minecraft:cooked_porkchop[custom_name=\"Дар: Бекон\"] 4"}
    };
}
