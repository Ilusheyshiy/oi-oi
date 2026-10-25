package moodygod;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import java.util.stream.Collectors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class GodMod implements ModInitializer {
    public static final String ID = "moody_god";
    static final String SATAN = "\u0001";
    static final Random R = new Random();
    static final Map<UUID, Rules.PState> STATE = new HashMap<>();
    record Task(long at, Runnable run) {}
    static final List<Task> TASKS = new ArrayList<>();
    static final Set<String> FIRSTS = new HashSet<>();
    static final Set<UUID> BURNED = new HashSet<>();
    static final Map<UUID, Long> HOLD = new HashMap<>(), NOJUMP = new HashMap<>(), BLEAT = new HashMap<>();
    static final Map<UUID, Boolean> APP_F = new HashMap<>(), APP_J = new HashMap<>();
    static final Set<String> WATCH = new HashSet<>();
    static final String[] HOSTILE = {"zombie", "skeleton", "creeper", "spider", "cave_spider", "enderman", "witch", "husk",
        "stray", "drowned", "pillager", "vindicator", "slime", "zombie_villager"};
    /** всё, что НЕ является «мобом»: эти сущности не замораживаем */
    static final String NOT_MOB = "type=!minecraft:player,type=!minecraft:item,type=!minecraft:item_display,type=!minecraft:text_display,"
        + "type=!minecraft:block_display,type=!minecraft:marker,type=!minecraft:armor_stand,type=!minecraft:area_effect_cloud,"
        + "type=!minecraft:lightning_bolt,type=!minecraft:experience_orb,type=!minecraft:falling_block,type=!minecraft:interaction,"
        + "type=!minecraft:arrow,type=!minecraft:boat,type=!minecraft:minecart";
    static final Map<String, String> SIN_PUN = Map.of("near", "wool_block", "wool", "wool_block", "shear", "sheep_buddy",
        "feed", "sheep_buddy", "lead", "wool_rain", "sleep", "bed_removal", "stand", "wool_rain", "inv", "bed_removal");

    static MinecraftServer SRV;
    static long tick = 0, nextVisit = 3600;      // первый визит — через 3 минуты
    static long speakingUntil = 0, chatCool = 0;
    static boolean busy = false, forceWrath = false, DEBUG = false, handUp = false;
    static int badStreak = 0, visitNo = 0, pending = 0, handFrame = 0;
    static int clarkStage = 0, clarkCountdown = 0;   // 0 — нет, 1 — сцена 1 на следующем визите, 2 — ждём сцену 2, 3 — всё

    static Rules.PState st(ServerPlayer p) { return STATE.computeIfAbsent(p.getUUID(), k -> new Rules.PState()); }
    static void schedule(int delay, Runnable r) { TASKS.add(new Task(tick + Math.max(0, delay), r)); }
    static int interval() { return 6000 + R.nextInt(4801); } // 5..9 минут
    static String pathB(Block b) { return BuiltInRegistries.BLOCK.getKey(b).getPath(); }
    static String pathI(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(); }
    static boolean online(MinecraftServer s, ServerPlayer p) { return s.getPlayerList().getPlayer(p.getUUID()) != null; }
    static boolean prayed(ServerPlayer sp) { Rules.PState s = st(sp); return s.prayTick > 0 && tick - s.prayTick <= 1200; }
    static void firstEvent(ServerPlayer sp, String key) {
        if (!FIRSTS.add(key)) return;
        st(sp).inc((prayed(sp) ? "p_" : "np_") + key);
    }
    static String blockKey(BlockState state, String id) {
        if (id.endsWith("iron_ore")) return "iron";
        if (id.endsWith("coal_ore")) return "coal";
        if (id.endsWith("diamond_ore")) return "diamond";
        if (id.endsWith("redstone_ore")) return "redstone";
        if (id.equals("stone") || id.equals("cobblestone")) return "stone";
        if (state.is(BlockTags.LOGS)) return "logs";
        if (state.is(BlockTags.LEAVES)) return "leaves";
        if (state.is(BlockTags.FLOWERS)) return "flower";
        if (state.is(BlockTags.CROPS)) return "crops";
        if (id.equals("dirt") || id.equals("grass_block")) return "dirt";
        if (state.is(BlockTags.WOOL)) return "wool";
        if (state.is(BlockTags.BEDS)) return "bed";
        return null;
    }

    // ================= регистрация =================
    @Override
    public void onInitialize() {
        for (String[] a : Rules.FIRST_ITEMS) WATCH.add(a[0]);
        PayloadTypeRegistry.clientboundPlay().register(GodPayload.TYPE, GodPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TimerPayload.TYPE, TimerPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FxPayload.TYPE, FxPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(this::onTick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.getPlayer();
            HOLD.remove(p.getUUID()); NOJUMP.remove(p.getUUID()); APP_F.remove(p.getUUID()); APP_J.remove(p.getUUID());
            cmd(server, p, "run attribute @s minecraft:movement_speed base set 0.1");
            cmd(server, p, "run attribute @s minecraft:jump_strength base set 0.42");
            cmd(server, p, "run attribute @s minecraft:max_health base set 20");
            ServerPlayNetworking.send(p, new TimerPayload(busy ? -1 : (int) Math.max(0, nextVisit - tick), 0));
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((msg, sender, params) -> {
            Long u = BLEAT.get(sender.getUUID());
            if (u != null && tick < u && SRV != null) {   // речевой центр удалён: слова превращаются в блеяние
                String[] b = {"беее", "бееее!", "бе-е-е", "мееее?", "беееее..."};
                Component c = Component.literal("<" + sender.getName().getString() + "> " + b[R.nextInt(b.length)]);
                for (ServerPlayer q : SRV.getPlayerList().getPlayers()) q.sendSystemMessage(c);
                return false;
            }
            return true;
        });

        ServerMessageEvents.CHAT_MESSAGE.register((msg, sender, params) -> {
            String raw = msg.signedContent();
            String t = raw.toLowerCase(Locale.ROOT);
            Rules.PState s = st(sender);
            s.chat.append(' ').append(t).append(' ');
            if (raw.length() >= 4 && raw.equals(raw.toUpperCase(Locale.ROOT)) && !raw.equals(t)) s.inc("caps");
            if (Scene.active || SRV == null) return;
            if (busy && tick < speakingUntil) { interrupt(sender); return; }   // перебил Бога — на месте буквы три точки, и смерть
            if (tick >= chatCool && !busy) {
                List<ServerPlayer> all = SRV.getPlayerList().getPlayers();
                if (t.contains("овц") && R.nextInt(100) < 35) { chatCool = tick + 600; say(all, Lines.pages("ТЫ СКАЗАЛ «ОВЦА»?! Я СЛЫШАЛ! Я ВСЕГДА СЛЫШУ!"), 4); }
                else if (t.contains("бог") && R.nextInt(100) < 25) { chatCool = tick + 600; say(all, Lines.pages("Меня звали? Занят. Приду позже. Или не приду."), 0); }
            }
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            Rules.PState s = st(sp);
            boolean sn = sp.isShiftKeyDown();
            String k = blockKey(state, pathB(state.getBlock()));
            if (k == null) return;
            if (k.equals("iron") || k.equals("coal")) s.inc(k + (sn ? "S" : "N")); else s.inc(k);
            String h = pathI(sp.getMainHandItem());
            if (k.equals("dirt") && !h.endsWith("shovel")) s.inc("dirtHand");
            if (k.equals("logs") && !h.endsWith("_axe")) s.inc("logsNoAxe");
            if (k.equals("stone") && !h.endsWith("pickaxe")) s.inc("stoneHand");
            firstEvent(sp, "b_" + k);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer sp) st(sp).inc("deaths");
            if (source.getEntity() instanceof ServerPlayer killer)
                st(killer).inc("k_" + net.minecraft.world.entity.EntityType.getKey(entity.getType()).getPath());
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player instanceof ServerPlayer sp && Rules.is(entity, "sheep")) {
                ItemStack it = sp.getItemInHand(hand);
                Rules.PState s = st(sp);
                if (it.is(Items.SHEARS)) s.inc("shear");
                else if (it.is(Items.LEAD)) s.inc("lead");
                else if (it.is(Items.WHEAT)) s.inc("feed");
            }
            return InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            Rules.PState s = st(sp);
            String bid = pathB(level.getBlockState(hit.getBlockPos()).getBlock());
            ItemStack held = sp.getItemInHand(hand);
            String hid = pathI(held);
            boolean sn = sp.isShiftKeyDown();
            if (bid.endsWith("chest") || bid.equals("barrel")) { if (!sn) s.inc("chestN"); }
            else if (bid.endsWith("_door") && !bid.startsWith("iron")) { if (hand == InteractionHand.MAIN_HAND) s.inc("doorMain"); }
            else if (bid.equals("lever") || bid.endsWith("_button")) s.inc("lever");
            else if (bid.equals("note_block")) s.inc("noteblock");
            else if (bid.endsWith("anvil")) s.inc("anvil");
            if (held.getItem() instanceof BlockItem) {
                if (hid.endsWith("torch") && !sn) s.inc("tn");
                else if (hid.equals("tnt")) s.inc("placed_tnt");
                if (hid.equals("sea_pickle") && held.getHoverName().getString().contains("Пикл Рик")) pickleBoom(sp);
            }
            return InteractionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((d, reg, env) -> {
            d.register(Commands.literal("god")
                .executes(c -> { startVisit(c.getSource().getServer(), false); return 1; })
                .then(Commands.literal("wrath").executes(c -> { startVisit(c.getSource().getServer(), true); return 1; }))
                .then(Commands.literal("debug").executes(c -> { DEBUG = !DEBUG; c.getSource().sendSystemMessage(Component.literal("debug: " + DEBUG)); return 1; }))
                .then(Commands.literal("pun").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Puns.Pun pn = Puns.get(StringArgumentType.getString(c, "id"));
                    applyPun(c.getSource().getServer(), p, pn, pn.secs() == 0 ? 20 : 30);
                    return 1;
                }))));
            // запускает эвент Кларка: следующий приход Бога — сценка 1, через 4 прихода — сценка 2
            d.register(Commands.literal("vkusno-i-tochka").executes(c -> {
                triggerClark(c.getSource().getServer(), true);
                return 1;
            }));
        });
    }

    static void triggerClark(MinecraftServer server, boolean byCommand) {
        if (clarkStage == 3) { clarkStage = 0; Scene.restaurant = false; }   // можно запустить сагу заново
        if (clarkStage == 0) {
            clarkStage = 1;
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                note(p, "§e[Бог] §fГде-то запахло фритюром... Следующий мой приход будет особенным.");
        } else if (byCommand) {
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                note(p, "§7[Бог] Эвент Кларка уже запущен (стадия " + clarkStage + ").");
        }
    }

    // ================= тик =================
    void onTick(MinecraftServer server) {
        SRV = server;
        tick++;
        for (Task t : new ArrayList<>(TASKS)) {
            if (t.at() <= tick) {
                TASKS.remove(t);
                try { t.run().run(); } catch (Exception ex) { if (DEBUG) System.out.println("[moody_god] task: " + ex); }
            }
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) { nextVisit++; return; }   // таймер стоит, пока никого нет
        if (tick % 100 == 0) { APP_F.clear(); APP_J.clear(); }
        for (ServerPlayer p : players) {
            Rules.PState s = st(p);
            double x = p.getX(), y = p.getY(), z = p.getZ();
            s.wasGround = p.onGround();
            s.lastX = x; s.lastY = y; s.lastZ = z;
            // молитва: присесть и покрутить головой вверх-вниз
            if (p.isShiftKeyDown()) {
                float pit = p.getXRot();
                if (pit < -40) { if (s.look == 2) { s.prayTick = tick; s.look = 0; s.inc("prayed"); } else s.look = 1; }
                else if (pit > 40) { if (s.look == 1) { s.prayTick = tick; s.look = 0; s.inc("prayed"); } else s.look = 2; }
            } else s.look = 0;
            // замок движения/прыжка через атрибуты (никаких эффектов прыгучести)
            if (tick % 5 == 0) {
                UUID id = p.getUUID();
                boolean frozen = tick < HOLD.getOrDefault(id, 0L);
                boolean nj = frozen || tick < NOJUMP.getOrDefault(id, 0L);
                if (frozen != APP_F.getOrDefault(id, false)) {
                    APP_F.put(id, frozen);
                    cmd(server, p, "run attribute @s minecraft:movement_speed base set " + (frozen ? "0" : "0.1"));
                }
                if (nj != APP_J.getOrDefault(id, false)) {
                    APP_J.put(id, nj);
                    cmd(server, p, "run attribute @s minecraft:jump_strength base set " + (nj ? "0" : "0.42"));
                }
            }
            if (tick % 20 == 0) {
                ServerLevel lv = (ServerLevel) p.level();
                if (lv.dimension() == Level.NETHER) s.inc("netherSec");
                if (p.isSleeping()) s.inc("sleepSec");
                var inv = p.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack it = inv.getItem(i);
                    if (it.isEmpty()) continue;
                    String ip = pathI(it);
                    String key = ip.endsWith("_bed") ? "bed" : ip;
                    if (WATCH.contains(key)) firstEvent(p, "i_" + key);
                }
            }
            if (tick % 10 == 0) {   // сожжённые дары Бога
                ServerLevel lv = (ServerLevel) p.level();
                for (Entity e : lv.getEntities((Entity) null, p.getBoundingBox().inflate(24), x2 -> x2 instanceof ItemEntity)) {
                    ItemEntity ie = (ItemEntity) e;
                    if (ie.isOnFire() && ie.getItem().getHoverName().getString().startsWith("Дар") && BURNED.add(ie.getUUID())) giftBurned(server, p);
                }
            }
        }
        if (busy && !Scene.active && tick % 20 == 0) freezeMobs(server, players);   // новые мобы тоже замирают
        if (handUp && tick % 10 == 0) {
            handFrame = (handFrame + 1) % 3;
            serverCmd(server, "execute as @e[tag=godhand] run data modify entity @s item.components.\"minecraft:item_model\" set value \"moody_god:hand" + (handFrame + 1) + "\"");
        }
        long left = nextVisit - tick;   // за 10 секунд до визита мобы нападают на овец
        if (!busy && !Scene.active && left <= 200 && left > 0 && tick % 20 == 0) sheepWar(server, players);
        if (!busy && tick >= nextVisit) startVisit(server, false);
    }

    // ================= утилиты =================
    static void serverCmd(MinecraftServer s, String c) {
        try { s.getCommands().getDispatcher().execute(c, s.createCommandSourceStack().withSuppressedOutput()); }
        catch (Exception e) {
            System.out.println("[moody_god] команда не сработала: " + c + " -> " + e.getMessage());
            if (DEBUG) for (ServerPlayer p : s.getPlayerList().getPlayers()) note(p, "§8[debug] " + c + " -> " + e.getMessage());
        }
    }
    static void cmd(MinecraftServer s, ServerPlayer p, String suffix) { serverCmd(s, "execute as " + p.getStringUUID() + " at @s " + suffix); }
    static void cmdAll(MinecraftServer s, Collection<ServerPlayer> ps, String suffix) { for (ServerPlayer p : ps) cmd(s, p, suffix); }
    static void note(ServerPlayer p, String text) { p.sendSystemMessage(Component.literal(text)); }
    static void sendFx(Collection<ServerPlayer> ps, String op, String a, int n) {
        FxPayload f = new FxPayload(op, a, n);
        for (ServerPlayer p : ps) ServerPlayNetworking.send(p, f);
    }
    static void sendTimer(MinecraftServer server, int ticks) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(p, new TimerPayload(ticks, 0));
    }
    static void hold(Collection<ServerPlayer> ps, long until) {
        for (ServerPlayer p : ps) { if (until == 0) HOLD.remove(p.getUUID()); else HOLD.put(p.getUUID(), until); }
    }

    static int say(Collection<ServerPlayer> ps, List<String> pages, int mood) {
        GodPayload pl = new GodPayload(String.join("\f", pages), mood);
        for (ServerPlayer p : ps) ServerPlayNetworking.send(p, pl);
        int ms = 0;
        for (String pg : pages) ms += GodPayload.pageMs(GodPayload.strip(pg));
        int ticks = ms / 50 + 15;
        if (busy) speakingUntil = Math.max(speakingUntil, tick + ticks);
        return ticks;
    }

    static void interrupt(ServerPlayer sinner) {
        sendFx(SRV.getPlayerList().getPlayers(), "interrupt", "", 0);
        note(sinner, "§c[Бог] §fТы перебил Бога.");
        schedule(25, () -> { cmd(SRV, sinner, "run summon minecraft:lightning_bolt ~ ~ ~"); cmd(SRV, sinner, "run kill @s"); });
    }
    static void pickleBoom(ServerPlayer sp) {
        note(sp, "§a[Рик] §fТы поставил Пикл Рика. Рик не прощает.");
        cmd(SRV, sp, "run particle minecraft:explosion_emitter ~ ~1 ~");
        cmd(SRV, sp, "run playsound minecraft:entity.generic.explode master @a ~ ~ ~ 2");
        cmd(SRV, sp, "run damage @s 12 minecraft:explosion");
        cmd(SRV, sp, "run clear @s minecraft:sea_pickle 1");
    }
    static void giftBurned(MinecraftServer server, ServerPlayer p) {
        List<String> pg = Lines.pages("ТЫ СЖЁГ МОЙ ДАР?! Я ДАЛ ТЕБЕ ПОДАРОК, А ТЫ ЕГО СЖЁГ?! ЗНАЧИТ, ЖОПА. ПОЛНАЯ.");
        int t = say(List.of(p), pg, 4);
        schedule(t - 40, () -> { applyPun(server, p, Puns.get("lightning"), 0); applyPun(server, p, Puns.get("arms"), 90); });
    }

    // ================= ИИ мобов: все в дальности прорисовки замирают и смотрят в небо =================
    static void freezeMobs(MinecraftServer server, Collection<ServerPlayer> ps) {
        for (ServerPlayer p : ps) {
            String sel = "@e[" + NOT_MOB + ",distance=..160]";
            cmd(server, p, "as " + sel + " run data merge entity @s {NoAI:1b}");
            cmd(server, p, "as " + sel + " run tag @s add godfrozen");
            cmd(server, p, "as @e[tag=godfrozen,distance=..160] at @s run tp @s ~ ~ ~ ~ -90");
        }
    }
    static void unfreezeMobs(MinecraftServer server) {
        serverCmd(server, "execute as @e[tag=godfrozen] run data merge entity @s {NoAI:0b}");
        serverCmd(server, "tag @e[tag=godfrozen] remove godfrozen");
    }
    static void sheepWar(MinecraftServer server, List<ServerPlayer> ps) {
        for (ServerPlayer p : ps)
            for (String t : HOSTILE) {
                String near = "if entity @e[type=minecraft:" + t + ",distance=..16] ";
                cmd(server, p, "as @e[type=minecraft:sheep,distance=..30] at @s " + near + "as @n[type=minecraft:" + t + ",distance=..16] run tp @s ~ ~ ~");
                cmd(server, p, "as @e[type=minecraft:sheep,distance=..30] at @s " + near + "run damage @s 3 minecraft:mob_attack");
            }
    }

    // ================= рука из облаков (объект на сервере) =================
    static void spawnHands(MinecraftServer server, List<ServerPlayer> ps) {
        for (ServerPlayer p : ps) {
            ServerLevel lv = (ServerLevel) p.level();
            if (lv.dimension() != Level.OVERWORLD || !lv.canSeeSky(p.blockPosition())) continue;
            cmd(server, p, "rotated ~ 0 positioned ^ ^ ^40 run summon minecraft:item_display ~ ~50 ~ {Tags:[\"godhand\"],billboard:\"center\","
                + "brightness:{sky:15,block:15},view_range:6.0f,item:{id:\"minecraft:stick\",count:1,components:{\"minecraft:item_model\":\"moody_god:hand1\"}},"
                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[90f,90f,90f]}}");
            handUp = true;
        }
    }
    static void killHands(MinecraftServer server) { serverCmd(server, "kill @e[tag=godhand]"); handUp = false; }

    static void weather(MinecraftServer server, int mood) {
        if (mood == 4) serverCmd(server, "weather thunder");
        else if (mood == 2) serverCmd(server, "weather rain");
        else if (mood == 1) serverCmd(server, "weather clear");
    }
    static int pickMood() {
        if (forceWrath) { forceWrath = false; return 4; }
        int r = R.nextInt(100);
        return r < 20 ? 0 : r < 35 ? 1 : r < 47 ? 2 : r < 65 ? 3 : r < 75 ? 4 : r < 88 ? 5 : 6;
    }

    // ================= визит =================
    static void addSatan(List<String> pages, String text) { for (String pg : Lines.pages(text)) pages.add(SATAN + pg); }
    static void addGod(List<String> pages, String text) { for (String pg : Lines.pages(text)) pages.add(GodPayload.tag(4) + pg); }

    static void startVisit(MinecraftServer server, boolean wrath) {
        List<ServerPlayer> ps = new ArrayList<>(server.getPlayerList().getPlayers());
        if (ps.isEmpty() || busy) return;
        busy = true;
        visitNo++;
        sendTimer(server, -1);
        boolean allOver = ps.stream().allMatch(p -> p.level().dimension() == Level.OVERWORLD);
        if (!wrath && allOver) {
            if (clarkStage == 1) { clarkStage = 2; clarkCountdown = 4; Scene.start1(server, ps); return; }
            if (clarkStage == 2 && --clarkCountdown <= 0) { clarkStage = 3; Scene.start2(server, ps); return; }
        }
        boolean satan = visitNo % 5 == 0;                                  // Сатана — каждый пятый приход, без шанса
        boolean disco = !satan && !wrath && R.nextInt(100) < 8;
        boolean tease = clarkStage == 0 && !wrath && R.nextInt(100) < 4;   // 4%: запускается эвент Кларка
        int m0 = wrath ? 4 : pickMood();
        weather(server, m0);
        hold(ps, Long.MAX_VALUE);
        freezeMobs(server, ps);
        spawnHands(server, ps);
        String names = ps.stream().map(p -> p.getName().getString()).collect(Collectors.joining(" и "));
        Rules.Rule aud = Rules.AUDIENCE.get(R.nextInt(Rules.AUDIENCE.size()));

        List<String> pages = new ArrayList<>(Lines.pages(Lines.pick(Lines2.intro(m0)).replace("{n}", names)));
        int introCount = pages.size();

        if (disco) {
            pages.addAll(Lines.pages(Lines.pick(Texts.DISCO_INTRO)));
            int t1 = say(ps, pages, m0);
            schedule(t1, () -> {
                List<String> ly = new ArrayList<>();
                for (String s : Texts.LYRICS) ly.addAll(Lines.pages(s));
                int t2 = say(ps, ly, m0);
                disco(server, ps, t2);
                schedule(t2, () -> verdict(server, m0, ps, aud));
            });
            return;
        }

        if (R.nextInt(100) < 45 || m0 == 4) pages.addAll(Lines.pages(Lines.pick(Lines.SHEEP_RANT)));
        if (R.nextInt(100) < 60) pages.addAll(Lines.pages(R.nextInt(100) < 40 ? Lines.pick(Lines.NETFLIX) : Lines.pick(Lines.STORY)));
        if (R.nextInt(100) < 15) pages.addAll(Lines.pages(Lines.pick(Texts.NOTIFY)));
        if (tease) { pages.addAll(Lines.pages(Lines.pick(Texts.CLARK_TEASE))); triggerClark(server, false); }
        if (satan) {
            for (int i = 0; i < 3; i++) addSatan(pages, Lines.pick(Texts.SATAN_ROAST));
            for (String f : Texts.FIGHT) { if (f.startsWith("S:")) addSatan(pages, f.substring(2)); else addGod(pages, f.substring(2)); }
        }
        pages.addAll(Lines.pages(aud.demand()));
        pages.addAll(Lines.pages("Я жду."));
        if (!wrath && R.nextInt(100) < 25) {   // характер меняется посреди речи
            int cur = (m0 + 1 + R.nextInt(6)) % 7;
            for (int i = introCount; i < pages.size(); i++)
                if (!GodPayload.isSatan(pages.get(i)) && !pages.get(i).startsWith("\u0002")) pages.set(i, GodPayload.tag(cur) + pages.get(i));
        }
        int ticks = say(ps, pages, m0);
        schedule(ticks + 40, () -> verdict(server, m0, ps, aud));
    }

    static void disco(MinecraftServer server, List<ServerPlayer> ps, int ticks) {
        String[] types = {"cow", "pig", "chicken", "sheep", "cat", "wolf", "rabbit", "frog", "llama", "fox", "villager", "bee"};
        for (ServerPlayer p : ps)
            for (int i = 0; i < 20; i++) {
                int dx = R.nextInt(13) - 6, dz = R.nextInt(13) - 6;
                if (dx == 0 && dz == 0) dx = 3;
                cmd(server, p, "run summon minecraft:" + types[R.nextInt(types.length)] + " ~" + dx + " ~1 ~" + dz
                    + " {NoAI:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"disco\"]}");
            }
        sendFx(ps, "disco", "", ticks);
        double[] bass = {0.5, 0.5, 0.75, 0.5, 0.67, 0.5, 0.9, 0.5};
        for (int i = 0; i * 5 < ticks; i++) {
            final int k = i;
            schedule(i * 5, () -> {
                serverCmd(server, "execute as @e[tag=disco] at @s run tp @s ~ ~ ~ ~9 " + (k % 2 == 0 ? "25" : "-15"));
                for (ServerPlayer p : ps) {
                    if (k % 2 == 0) cmd(server, p, String.format(Locale.ROOT, "run playsound minecraft:block.note_block.bass master @s ~ ~ ~ 1.5 %.2f", bass[(k / 2) % bass.length]));
                    else cmd(server, p, "run playsound minecraft:block.note_block.snare master @s ~ ~ ~ 1 1");
                    cmd(server, p, "run playsound minecraft:block.note_block.hat master @s ~ ~ ~ 0.6 2");
                }
            });
        }
        schedule(ticks, () -> {
            sendFx(ps, "disco_end", "", 0);
            serverCmd(server, "kill @e[tag=disco]");
        });
    }

    // ================= вердикт: каждое нарушение → реплики → наказание СРАЗУ =================
    record Seg(List<String> pages, Runnable after) {}

    static Seg punSeg(MinecraftServer server, ServerPlayer p, String line, Puns.Pun pun) {
        List<String> pages = new ArrayList<>();
        if (line != null) pages.addAll(Lines.pages(line));
        int secs = pun.secs() == 0 ? 0 : (int) (pun.secs() * (0.8 + R.nextDouble() * 0.5));
        pages.addAll(Lines.pages(pun.say()[R.nextInt(2)].replace("{T}", String.valueOf(secs))));
        return new Seg(pages, () -> applyPun(server, p, pun, secs));
    }

    static List<Seg> buildSegs(MinecraftServer server, ServerPlayer p, int mood, List<ServerPlayer> ps, Rules.Rule aud) {
        Rules.PState s = st(p);
        ServerLevel lv = (ServerLevel) p.level();
        Rules.Ctx c = new Rules.Ctx(p, lv, s, ps);
        List<Rules.Rule> viol = new ArrayList<>(), ok = new ArrayList<>();
        for (Rules.Rule r : Rules.EVENTS) {
            int v;
            try { v = r.eval().applyAsInt(c); } catch (Exception e) { v = 0; }
            if (v < 0) viol.add(r); else if (v > 0) ok.add(r);
        }
        int a = 0;
        try { a = aud.eval().applyAsInt(c); } catch (Exception e) { a = 0; }
        // --- овцы: всегда включены, Бог тупой и думает, что все их ненавидят ---
        List<String> sins = new ArrayList<>(), praise = new ArrayList<>();
        var below = lv.getBlockState(p.blockPosition().below());
        if (!lv.getEntities((Entity) null, p.getBoundingBox().inflate(10), e -> Rules.is(e, "sheep")).isEmpty()) sins.add("near");
        if (p.getMainHandItem().is(ItemTags.WOOL)) sins.add("wool");
        if (s.n("shear") > 0) sins.add("shear");
        if (s.n("feed") > 0) sins.add("feed");
        if (s.n("lead") > 0) sins.add("lead");
        if (s.n("sleepSec") > 0) sins.add("sleep");
        if (below.is(BlockTags.WOOL)) sins.add("stand");
        if (Rules.countIf(p, it -> it.is(ItemTags.WOOL) || it.is(ItemTags.BEDS) || it.is(ItemTags.WOOL_CARPETS)) > 0) sins.add("inv");
        if (s.n("k_sheep") > 0) praise.add("killed");
        if (s.n("wool") + s.n("bed") > 0) praise.add("break");
        if (Rules.count(p, Items.MUTTON) + Rules.count(p, Items.COOKED_MUTTON) > 0) praise.add("mutton");
        s.reset();

        Collections.shuffle(viol, R);
        Collections.shuffle(sins, R);
        boolean anyBad = !viol.isEmpty() || a < 0 || !sins.isEmpty();
        List<Seg> segs = new ArrayList<>();
        // вступление вердикта
        if (anyBad) segs.add(new Seg(Lines.pages(Lines.pick(Lines2.bad(mood))), null));
        else if (a > 0 || !ok.isEmpty() || !praise.isEmpty()) segs.add(new Seg(Lines.pages(Lines.pick(Lines2.good(mood))), null));
        else segs.add(new Seg(Lines.pages(Lines.pick(Lines.NEUTRAL)), null));
        // овечьи грехи
        for (int i = 0; i < Math.min(2, sins.size()); i++) {
            String key = sins.get(i);
            segs.add(punSeg(server, p, Lines.pick(Texts.SHEEP_SIN.get(key)), Puns.get(SIN_PUN.get(key))));
        }
        // аудиенция
        if (a < 0) segs.add(punSeg(server, p, Lines.pick(aud.bad()), Puns.get(aud.pun())));
        // нарушения правил
        for (int i = 0; i < Math.min(3, viol.size()); i++) {
            Rules.Rule r = viol.get(i);
            segs.add(punSeg(server, p, r.bad()[R.nextInt(2)], Puns.get(r.pun())));
        }
        if (viol.size() > 3) segs.add(new Seg(Lines.pages("И ещё " + (viol.size() - 3) + " нарушений, но у меня закончился голос. Считайте, вы просто плохие."), null));
        // награды
        int good = ok.size() + praise.size() + (a > 0 ? 1 : 0);
        if (!praise.isEmpty())
            segs.add(new Seg(Lines.pages(Lines.pick(Texts.SHEEP_SIN.get(praise.get(R.nextInt(praise.size()))))), () -> reward(server, p, 1)));
        else if (good > 0 && (!anyBad || R.nextInt(100) < 50))
            segs.add(new Seg(Lines.pages(a > 0 ? "Ты выполнил, что я просил. Молодец. Держи." : "Ты делал всё правильно. Удивительно. Держи."), () -> reward(server, p, 1)));
        else if (!anyBad && R.nextInt(100) < 25)
            segs.add(new Seg(Lines.pages("Ладно, в этот раз без пиздеца. Держи просто так."), () -> reward(server, p, 1)));
        // гнев: показательная сцена
        if (mood == 4 && viol.size() >= 2) {
            int sc = R.nextInt(3);
            segs.add(new Seg(Lines.pages(Lines.pick(sc == 0 ? Lines.SCENE_CAT : sc == 1 ? Lines.SCENE_KID : Lines.SCENE_SHEEP)), () -> wrathScene(server, p, sc)));
        }
        // фирменное у новых характеров
        if (mood == 6) segs.add(new Seg(Lines.pages("Ваш чек. Пакет не нужен? Хорошо. Приходите ещё."),
            () -> cmd(server, p, "run give @s minecraft:paper[custom_name=\"Чек: ваши грехи (оплачено болью)\"] 1")));
        if (mood == 5) segs.add(new Seg(Lines.pages("Я нарисовал вам рисунок! Берите! Он про вас!"),
            () -> cmd(server, p, "run give @s minecraft:paper[custom_name=\"Рисунок Бога: Ты и овца\"] 1")));
        if (anyBad) badTemp = true;
        return segs;
    }
    static boolean badTemp = false;

    static void verdict(MinecraftServer server, int mood, List<ServerPlayer> ps, Rules.Rule aud) {
        pending = 0;
        badTemp = false;
        for (ServerPlayer p : ps) {
            if (!online(server, p)) continue;
            pending++;
            runSegs(server, p, buildSegs(server, p, mood, ps, aud), 0, mood);
        }
        if (badTemp) { if (++badStreak >= 2) { forceWrath = true; badStreak = 0; } } else badStreak = 0;
        if (pending == 0) finishVisit(server);
    }

    static void runSegs(MinecraftServer server, ServerPlayer p, List<Seg> segs, int i, int mood) {
        if (i >= segs.size() || !online(server, p)) {
            if (--pending <= 0) schedule(40, () -> finishVisit(server));
            return;
        }
        Seg sg = segs.get(i);
        int total = say(List.of(p), sg.pages(), mood);
        String last = GodPayload.strip(sg.pages().get(sg.pages().size() - 1));
        int tail = (GodPayload.HOLD_MS + last.length() * GodPayload.FALL_STAGGER_MS + GodPayload.FALL_MS + 350) / 50;
        int fire = Math.max(5, total - tail);                 // наказание — сразу после того, как реплика напечатана
        schedule(fire, () -> { if (sg.after() != null) sg.after().run(); });
        schedule(total + 12, () -> runSegs(server, p, segs, i + 1, mood));
    }

    static void finishVisit(MinecraftServer server) {
        busy = false;
        hold(server.getPlayerList().getPlayers(), 0);
        unfreezeMobs(server);
        killHands(server);
        nextVisit = tick + interval();
        sendTimer(server, (int) (nextVisit - tick));
    }

    // ================= наказания и награды =================
    static void applyPun(MinecraftServer server, ServerPlayer p, Puns.Pun pun, int secs) {
        if (!online(server, p)) return;
        String T = String.valueOf(secs);
        for (String c : pun.cmds()) cmd(server, p, c.replace("{T}", T));
        if (pun.limb() != null) {   // визуальная часть: оверлей на клиенте + кровь + отрубленная часть на земле
            ServerPlayNetworking.send(p, new FxPayload("limb", pun.limb(), secs * 20));
            severed(server, p, pun, secs);
        }
        if (pun.special() != null) {
            long end = tick + secs * 20L;
            for (String sp : pun.special().split(",")) {
                switch (sp) {
                    case "nojump" -> NOJUMP.merge(p.getUUID(), end, Math::max);
                    case "bleat" -> BLEAT.put(p.getUUID(), end);
                    case "sway" -> sway(server, p, end);
                    case "faces" -> faces(server, p, end);
                    default -> { }
                }
            }
        }
        for (String u : pun.undo()) schedule(secs * 20, () -> { if (online(server, p)) cmd(server, p, u); });
        note(p, "§c[Наказание] §f" + pun.id() + (secs > 0 ? " (" + secs + " с)" : ""));
    }

    static void severed(MinecraftServer server, ServerPlayer p, Puns.Pun pun, int secs) {
        cmd(server, p, "run particle block{block_state:\"minecraft:redstone_block\"} ~ ~1 ~ 0.3 0.5 0.3 0.15 70 force");
        cmd(server, p, "run playsound minecraft:entity.player.hurt master @a ~ ~ ~ 2 0.7");
        if (pun.drop() != null)
            cmd(server, p, "run summon minecraft:item ~ ~1.5 ~ {Item:{id:\"minecraft:stick\",count:1,components:{\"minecraft:item_model\":\"moody_god:" + pun.drop()[0]
                + "\",\"minecraft:custom_name\":\"" + pun.drop()[1] + "\"}},Motion:[0.25d,0.35d,0.1d],PickupDelay:60s}");
        drip(server, p, tick + secs * 20L);
    }
    static void drip(MinecraftServer server, ServerPlayer p, long end) {   // кровь капает, пока часть отсутствует
        if (tick >= end || !online(server, p)) return;
        cmd(server, p, "run particle block{block_state:\"minecraft:redstone_block\"} ~ ~0.8 ~ 0.2 0.3 0.2 0.02 4");
        schedule(10, () -> drip(server, p, end));
    }
    static void sway(MinecraftServer server, ServerPlayer p, long end) {   // мозжечок: камеру водит
        if (tick >= end || !online(server, p)) return;
        cmd(server, p, "run tp @s ~ ~ ~ ~" + (R.nextInt(9) - 4) + " ~" + (R.nextInt(5) - 2));
        schedule(3, () -> sway(server, p, end));
    }
    /** «Отдел распознавания лиц отключён»: у всех мобов лицо тиммейта (если его нет — твоё). */
    static void faces(MinecraftServer server, ServerPlayer p, long end) {
        if (tick >= end || !online(server, p)) {
            serverCmd(server, "kill @e[tag=facehead]");
            serverCmd(server, "tag @e[tag=faced] remove faced");
            return;
        }
        String name = p.getName().getString();
        for (ServerPlayer o : server.getPlayerList().getPlayers()) if (o != p) { name = o.getName().getString(); break; }
        String sel = "@e[" + NOT_MOB + ",tag=!faced,distance=..48]";
        cmd(server, p, "as " + sel + " at @s run summon minecraft:item_display ~ ~ ~ {Tags:[\"facehead\",\"fresh\"],billboard:\"vertical\",view_range:2.0f,"
            + "item:{id:\"minecraft:player_head\",count:1,components:{\"minecraft:profile\":\"" + name + "\"}},"
            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0.35f,0f],scale:[1.1f,1.1f,1.1f]}}");
        cmd(server, p, "as " + sel + " at @s run ride @n[type=minecraft:item_display,tag=fresh,distance=..0.6] mount @s");
        cmd(server, p, "as @e[tag=facehead,tag=fresh] on vehicle run tag @s add faced");
        cmd(server, p, "run tag @e[tag=fresh] remove fresh");
        schedule(40, () -> faces(server, p, end));
    }

    static void reward(MinecraftServer s, ServerPlayer p, int n) {
        for (int i = 0; i < n; i++) {
            String[] r = Puns.GIFTS[R.nextInt(Puns.GIFTS.length)];
            for (int k = 1; k < r.length; k++) cmd(s, p, r[k]);
            note(p, "§e[Бог] §fНаграда: " + r[0]);
        }
    }

    static void wrathScene(MinecraftServer s, ServerPlayer p, int scene) {
        cmd(s, p, "run weather thunder");
        if (scene == 0) {
            cmd(s, p, "unless entity @e[type=minecraft:cat,distance=..30] run summon minecraft:cat ~2 ~ ~ {CustomName:\"Мурзик\"}");
            schedule(70, () -> {
                String sel = "@e[type=minecraft:cat,limit=1,sort=nearest,distance=..30]";
                cmd(s, p, "as " + sel + " at @s run particle minecraft:explosion_emitter ~ ~1 ~");
                cmd(s, p, "as " + sel + " at @s run playsound minecraft:entity.generic.explode hostile @a ~ ~ ~ 2");
                cmd(s, p, "run kill " + sel);
            });
        } else if (scene == 1) {
            cmd(s, p, "run summon minecraft:villager ~2 ~ ~ {Age:-24000,CustomName:\"Малыш Тимми\"}");
            schedule(80, () -> {
                String sel = "@e[type=minecraft:villager,name=\"Малыш Тимми\",limit=1,sort=nearest,distance=..30]";
                cmd(s, p, "as " + sel + " at @s run particle minecraft:explosion_emitter ~ ~1 ~");
                cmd(s, p, "as " + sel + " at @s run playsound minecraft:entity.generic.explode hostile @a ~ ~ ~ 2");
                cmd(s, p, "run kill " + sel);
            });
        } else {
            schedule(40, () -> {
                cmd(s, p, "as @e[type=minecraft:sheep,distance=..40] at @s run particle minecraft:explosion_emitter ~ ~1 ~");
                cmd(s, p, "as @e[type=minecraft:sheep,distance=..40] at @s run playsound minecraft:entity.generic.explode hostile @a ~ ~ ~ 1");
                cmd(s, p, "run kill @e[type=minecraft:sheep,distance=..40]");
            });
        }
        for (int i = 0; i < 3; i++) schedule(30 + i * 25, () -> cmd(s, p, "run summon minecraft:lightning_bolt ~ ~ ~"));
    }
}
