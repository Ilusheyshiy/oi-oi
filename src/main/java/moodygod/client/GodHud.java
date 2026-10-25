package moodygod.client;

import java.util.*;
import moodygod.GodPayload;
import moodygod.Intonation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

/** Текст Бога (печать с интонацией → падение → мини-взрыв), небо, диско, визуал отрубленных частей, блокировка камеры. */
public final class GodHud {
    record Pending(String page, int mood, boolean satan) {}
    static final class Letter { char c; float x, y; int idx; }
    static final class Particle { float x, y, vx, vy; long born; int life, color; }

    static final ArrayDeque<Pending> queue = new ArrayDeque<>();
    static final List<Letter> letters = new ArrayList<>();
    static final List<Particle> parts = new ArrayList<>();
    static final Random R = new Random();
    static final int[] SCHIZO = {0xFFFF2A2A, 0xFF33FF66, 0xFFFFFFFF, 0xFFB050FF, 0xFFFFE94A, 0xFF00E5FF};
    static final int[] DISCO = {0xFFFF2A2A, 0xFF33FF66, 0xFF3399FF, 0xFFFFE94A, 0xFFFF33CC, 0xFF00E5FF, 0xFFFF9900};
    static final int[] MOOD_COL = {0xFFFF8FC8, 0xFFFFE94A, 0xFF8FA3BF, 0, 0xFFFF2A2A, 0xFF9AFFA0, 0xFFFFB347};
    static final float[] MOOD_PITCH = {1.5f, 1.9f, 0.7f, 1f, 0.6f, 1.9f, 1.1f};

    static SoundEvent snd(String id) { return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(id)); }
    static final SoundEvent SND_TYPE = snd("block.note_block.bit");
    static final SoundEvent SND_SATAN = snd("block.note_block.basedrum");
    static final SoundEvent SND_BASS = snd("block.note_block.bass");
    static final SoundEvent SND_BOOM = snd("entity.generic.explode");
    static final SoundEvent SND_ARRIVE = snd("block.beacon.activate");

    static Pending cur;
    static Intonation.Plan plan;
    static int phase, typed, landedCount, curMood;
    static boolean[] landed = new boolean[0];
    static long phaseStart, lastNow, timerDeadline = -1;
    static boolean visitNow;
    static float dim, scale = 2f, sky, glow;
    static long discoUntil, deafUntil;
    static final Map<String, Long> LIMBS = new LinkedHashMap<>();

    // блокировка камеры (сцены): мышь отключает миксин, а здесь камера плавно доезжает до цели
    public static volatile boolean camLocked;
    static float camYaw, camPitch, camSpeed = 150f;

    // ================= приём пакетов =================
    public static void setTimer(int ticks) {
        if (ticks < 0) { visitNow = true; timerDeadline = -1; }
        else { visitNow = false; timerDeadline = System.currentTimeMillis() + ticks * 50L; }
    }

    public static void enqueue(String text, int mood) {
        for (String pg : text.split("\f"))
            queue.add(new Pending(GodPayload.strip(pg), GodPayload.moodOf(pg, mood), GodPayload.isSatan(pg)));
    }

    public static void fx(String op, String a, int n) {
        long now = System.currentTimeMillis();
        switch (op) {
            case "disco" -> discoUntil = now + n * 50L;
            case "disco_end" -> discoUntil = 0;
            case "limb" -> {
                LIMBS.put(a, now + n * 50L);
                if (a.equals("ear")) deafUntil = now + n * 50L;
            }
            case "interrupt" -> interrupt();
            case "hudclear" -> { cur = null; queue.clear(); letters.clear(); phase = 0; }
            case "dualtype" -> { play(SND_BASS, 0.5f, 0.8f); play(SND_TYPE, 1.9f, 0.6f); }
            case "camlock" -> {
                try {
                    String[] f = a.split(",");
                    camYaw = Float.parseFloat(f[0]); camPitch = Float.parseFloat(f[1]); camSpeed = Float.parseFloat(f[2]);
                    camLocked = true;
                } catch (Exception ignored) { }
            }
            case "camunlock" -> camLocked = false;
            default -> { }
        }
    }

    static void play(SoundEvent s, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, Math.max(0.5f, Math.min(2f, pitch)), vol));
    }
    static int moodColor(int m, long now) {
        if (m == 3) return SCHIZO[(int) ((now / 70) % SCHIZO.length)];
        return MOOD_COL[Math.max(0, Math.min(6, m))];
    }
    static float basePitch(int m) {
        float p = MOOD_PITCH[Math.max(0, Math.min(6, m))];
        return m == 3 ? 0.6f + R.nextFloat() * 1.2f : p;
    }
    static void drawLetter(GuiGraphicsExtractor g, Font f, char c, float x, float y, float s, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        g.text(f, String.valueOf(c), 0, 0, color);
        g.pose().popMatrix();
    }

    // ================= таймер =================
    static void drawTimer(GuiGraphicsExtractor g, Minecraft mc, long now, int W) {
        String s; int col = 0xFFFFFFFF;
        if (cur != null || visitNow || !queue.isEmpty()) { s = "БОГ ЗДЕСЬ"; col = 0xFFFFE94A; }
        else if (timerDeadline < 0) return;
        else {
            long ms = Math.max(0, timerDeadline - now);
            long sec = ms / 1000;
            s = "Визит Бога через " + (sec / 60) + ":" + String.format("%02d", sec % 60);
            if (ms < 10000 && (now / 400) % 2 == 0) col = 0xFFFF4040;
        }
        int w = mc.font.width(s);
        int x = (W - w) / 2;
        g.fill(x - 4, 2, x + w + 4, 14, 0x77000000);
        g.text(mc.font, s, x, 4, col);
    }

    // ================= небо желтеет, у Сатаны — красное свечение снизу =================
    static void drawSky(GuiGraphicsExtractor g, int W, int H, long now, float dts) {
        boolean god = visitNow || cur != null || !queue.isEmpty();
        boolean satanNow = cur != null && cur.satan();
        sky += ((!god ? 0f : satanNow ? 0.45f : 1f) - sky) * Math.min(1f, dts * 1.5f);
        glow += ((satanNow ? 1f : 0f) - glow) * Math.min(1f, dts * 3f);
        int bands = 28;
        if (sky > 0.01f) {
            int rgb = curMood == 4 ? 0xFF5A00 : 0xFFD400;
            float pulse = 0.9f + 0.1f * (float) Math.sin(now / 400.0);
            int hh = (int) (H * 0.7f);
            for (int i = 0; i < bands; i++) {
                int a = (int) (150 * sky * pulse * (1f - (float) i / bands));
                g.fill(0, hh * i / bands, W, hh * (i + 1) / bands, (a << 24) | rgb);
            }
            g.fill(0, 0, W, H, ((int) (30 * sky) << 24) | rgb);
        }
        if (glow > 0.01f) {
            int hh = (int) (H * 0.45f);
            for (int i = 0; i < bands; i++) {
                int a = (int) (170 * glow * (1f - (float) i / bands));
                g.fill(0, H - hh * (i + 1) / bands, W, H - hh * i / bands, (a << 24) | 0x8B0000);
            }
        }
    }

    static void drawDisco(GuiGraphicsExtractor g, int W, int H, long now) {
        if (now >= discoUntil) return;
        int c = DISCO[(int) ((now / 130) % DISCO.length)];
        g.fill(0, 0, W, H, (0x40 << 24) | (c & 0xFFFFFF));
        for (int k = 0; k < 5; k++) {
            int x = (int) ((now / 5 + k * W / 5) % W);
            g.fill(x, 0, x + 8, H, (0x30 << 24) | (DISCO[(k + (int) (now / 200)) % DISCO.length] & 0xFFFFFF));
        }
    }

    // ================= визуал отрубленных частей =================
    static String limbName(String id) {
        return switch (id) {
            case "arm_r" -> "Правая рука отрублена"; case "arm_l" -> "Левая рука отрублена"; case "arms" -> "Обе руки отрублены";
            case "leg_r" -> "Правая нога отрублена"; case "legs" -> "Обе ноги отрублены";
            case "eye_l" -> "Левый глаз выколот"; case "eye_r" -> "Правый глаз выколот"; case "eyes" -> "Оба глаза выколоты";
            case "ear" -> "Слух отключён"; case "tongue" -> "Язык вырезан (теперь ты блеешь)"; case "heart" -> "Сердце уменьшено";
            case "faces" -> "Отдел распознавания лиц отключён"; case "balance" -> "Мозжечок удалён"; case "vision" -> "Зрительная кора выключена";
            default -> id;
        };
    }

    static void stump(GuiGraphicsExtractor g, int x, int top, int w, int bottom, long now) {
        int skin = 0xFFE2B496, band = 0xFFF2F2F2, blood = 0xFFB0121C, bloodD = 0xFF700A10;
        g.fill(x, top, x + w, bottom, skin);
        for (int y = top + 16; y < bottom; y += 14) g.fill(x, y, x + w, y + 6, band);
        g.fill(x, top, x + w, top + 12, blood);
        g.fill(x + 2, top + 2, x + w - 2, top + 8, bloodD);
        int d1 = (int) ((now / 70) % 50), d2 = (int) (((now / 70) + 21) % 50);
        g.fill(x + w / 4, top + 12 + d1, x + w / 4 + 3, top + 17 + d1, blood);
        g.fill(x + 3 * w / 4, top + 12 + d2, x + 3 * w / 4 + 3, top + 17 + d2, blood);
    }

    static void drawLimbs(GuiGraphicsExtractor g, Minecraft mc, int W, int H, long now) {
        LIMBS.values().removeIf(u -> u < now);
        int line = 0;
        for (Map.Entry<String, Long> e : LIMBS.entrySet()) {
            String id = e.getKey();
            g.text(mc.font, limbName(id) + " (" + (e.getValue() - now) / 1000 + " с)", 6, 18 + line * 10, 0xFFFF5555);
            line++;
            int sw = Math.max(26, W / 8);
            switch (id) {
                case "arm_r" -> stump(g, W - W / 4, (int) (H * 0.62), sw, H, now);
                case "arm_l" -> stump(g, W / 12, (int) (H * 0.70), sw, H, now);
                case "arms" -> { stump(g, W - W / 4, (int) (H * 0.62), sw, H, now); stump(g, W / 12, (int) (H * 0.62), sw, H, now); }
                case "leg_r", "legs" -> {
                    int pulse = (int) (24 * Math.sin(now / 300.0)) + 40;
                    g.fill(0, H - 8, W, H, 0xCC9A0A12);
                    g.fill(W / 4, H - 14 - pulse / 6, 3 * W / 4, H - 8, 0x88700A10);
                }
                case "eye_l" -> { g.fill(0, 0, W / 2, H, 0xFF000000); g.fill(W / 4, H / 2, W / 4 + 2, H / 2 + 36, 0xFFB0121C); }
                case "eye_r" -> { g.fill(W / 2, 0, W, H, 0xFF000000); g.fill(3 * W / 4, H / 2, 3 * W / 4 + 2, H / 2 + 36, 0xFFB0121C); }
                case "eyes" -> g.fill(0, 0, W, H, 0xFF000000);
                case "ear" -> { g.fill(0, 0, 5, H, 0x889A0A12); g.fill(W - 5, 0, W, H, 0x889A0A12); }
                case "heart" -> {
                    int a = (int) (50 + 50 * Math.max(0, Math.sin(now / 260.0)));
                    int c = (a << 24) | 0xB0121C;
                    g.fill(0, 0, W, 14, c); g.fill(0, H - 14, W, H, c); g.fill(0, 0, 14, H, c); g.fill(W - 14, 0, W, H, c);
                }
                case "vision" -> g.fill(0, 0, W, H, 0x88808080);
                case "balance" -> g.fill(0, 0, W, H, 0x2244AA44);
                default -> { }
            }
        }
    }

    // ================= текст Бога =================
    static void start(Minecraft mc, Pending p, long now, int W, int H) {
        boolean first = cur == null && dim < 0.05f;
        cur = p;
        curMood = p.mood();
        letters.clear();
        scale = p.satan() ? 1.5f : 2f;
        String page = p.page();
        plan = Intonation.plan(page);
        String[] lines = page.split("\n", -1);
        float lineH = 11 * scale;
        float top = p.satan() ? H * 0.70f : H * 0.28f;
        int pos = 0;
        for (int li = 0; li < lines.length; li++) {
            float sx = (W - mc.font.width(lines[li]) * scale) / 2f;
            for (int ci = 0; ci < lines[li].length(); ci++) {
                Letter l = new Letter();
                l.c = lines[li].charAt(ci);
                l.idx = pos + ci;
                l.x = sx + mc.font.width(lines[li].substring(0, ci)) * scale;
                l.y = top + li * lineH;
                letters.add(l);
            }
            pos += lines[li].length() + 1;
        }
        landed = new boolean[letters.size()];
        landedCount = 0; typed = 0; phase = 1; phaseStart = now;
        if (first && !p.satan()) play(SND_ARRIVE, 0.5f, 0.8f);
        if (p.satan()) play(SND_SATAN, 0.4f, 0.5f);
    }

    /** Перебивание в чате: на месте последней напечатанной буквы остаётся «...». */
    static void interrupt() {
        if (cur == null || phase != 1) return;
        Minecraft mc = Minecraft.getInstance();
        int n = Math.min(typed, letters.size());
        StringBuilder sb = new StringBuilder();
        int cnt = 0;
        for (char ch : cur.page().toCharArray()) {
            if (ch == '\n') { sb.append(ch); continue; }
            if (cnt >= n) break;
            sb.append(ch); cnt++;
        }
        sb.append("...");
        long now = System.currentTimeMillis();
        start(mc, new Pending(sb.toString(), cur.mood(), cur.satan()), now, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        typed = letters.size();
        phase = 2; phaseStart = now;
        play(SND_BOOM, 0.6f, 0.4f);
    }

    static void spawnBoom(float x, float y, int mood, long now) {
        for (int i = 0; i < 9; i++) {
            Particle q = new Particle();
            q.x = x; q.y = y;
            q.vx = (R.nextFloat() - 0.5f) * 280f;
            q.vy = -60f - R.nextFloat() * 220f;
            q.born = now; q.life = 350 + R.nextInt(300);
            int[] pal = {0xFFFFAA00, 0xFFFF5500, 0xFFFFFFFF, moodColor(mood, now)};
            q.color = pal[R.nextInt(pal.length)];
            parts.add(q);
        }
    }

    // ================= основной рендер =================
    public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        int W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();
        float dts = Math.min(0.1f, (now - lastNow) / 1000f);
        lastNow = now;

        if (camLocked) {   // игрок не может повернуть камеру: цель держится каждый кадр, к ней плавно доезжаем
            float step = camSpeed * dts;
            float y = mc.player.getYRot();
            float dy = Mth.wrapDegrees(camYaw - y);
            y = Math.abs(dy) <= step ? camYaw : y + Math.signum(dy) * step;
            mc.player.setYRot(y);
            float pt = mc.player.getXRot();
            float dp = camPitch - pt;
            pt = Math.abs(dp) <= step ? camPitch : pt + Math.signum(dp) * step;
            mc.player.setXRot(pt);
        }

        drawSky(g, W, H, now, dts);
        drawDisco(g, W, H, now);
        drawTimer(g, mc, now, W);
        if (cur == null && !queue.isEmpty()) start(mc, queue.poll(), now, W, H);

        boolean busy = cur != null || !queue.isEmpty();
        float target = !busy ? 0f : (cur != null && cur.satan() ? 0.3f : 0.5f);
        dim += (target - dim) * Math.min(1f, dts * 5f);
        if (dim > 0.01f) {
            g.fill(0, 0, W, H, ((int) (dim * 255)) << 24);
            if (cur != null && cur.mood() == 4 && !cur.satan()) {
                int a = (int) (20 + 20 * Math.sin(now / 90.0));
                g.fill(0, 0, W, H, (a << 24) | 0xFF0000);
            }
        }
        drawLimbs(g, mc, W, H, now);
        if (cur != null) tickPage(g, mc, now, H);

        Iterator<Particle> it = parts.iterator();
        while (it.hasNext()) {
            Particle q = it.next();
            long age = now - q.born;
            if (age > q.life) { it.remove(); continue; }
            q.vy += 700f * dts; q.x += q.vx * dts; q.y += q.vy * dts;
            int a = (int) (255 * (1f - (float) age / q.life));
            int sz = 2 + (int) (2 * (1f - (float) age / q.life));
            g.fill((int) q.x, (int) q.y, (int) q.x + sz, (int) q.y + sz, (a << 24) | (q.color & 0xFFFFFF));
        }
        if (now < deafUntil) {
            mc.getSoundManager().stop();
            g.text(mc.font, "[ВЫ ОГЛОХЛИ]", 6, H - 14, 0xFFAAAAAA);
        }
    }

    static void tickPage(GuiGraphicsExtractor g, Minecraft mc, long now, int H) {
        int n = letters.size();
        long t = now - phaseStart;
        boolean satan = cur.satan();
        int mood = cur.mood();
        float amp = satan ? 1.5f : mood == 4 ? 2.5f : mood == 3 ? 1f : 0f;
        float ox = (R.nextFloat() - 0.5f) * amp, oy = (R.nextFloat() - 0.5f) * amp;
        int base = satan ? 0x7A0A0A : (moodColor(mood, now) & 0xFFFFFF);
        int col = 0xFF000000 | base;

        if (phase == 1) {   // печать: символы появляются по таймингам интонации
            while (typed < n && plan.t()[letters.get(typed).idx] <= t) {
                Letter l = letters.get(typed);
                if (l.c != ' ') {
                    float pi = plan.pitch()[l.idx], vo = plan.vol()[l.idx];
                    if (satan) play(SND_SATAN, (0.5f + R.nextFloat() * 0.2f) * pi, 0.22f * vo);
                    else play(SND_TYPE, basePitch(mood) * pi, 0.5f * vo);
                }
                typed++;
            }
            if (typed >= n) { phase = 2; phaseStart = now; }
        } else if (phase == 2 && t >= GodPayload.HOLD_MS) {
            phase = 3; phaseStart = now; t = 0;
        }

        if (phase == 1 || phase == 2) {
            int lim = phase == 1 ? typed : n;
            for (int i = 0; i < lim; i++) {
                Letter l = letters.get(i);
                if (l.c != ' ') drawLetter(g, mc.font, l.c, l.x + ox, l.y + oy, scale, satan ? 0xCC000000 | base : col);
            }
            return;
        }

        float floorY = H - 30;
        boolean all = true;
        for (int i = 0; i < n; i++) {
            Letter l = letters.get(i);
            if (l.c == ' ' || landed[i]) continue;
            long ft = t - (long) i * GodPayload.FALL_STAGGER_MS;
            if (ft < 0) { drawLetter(g, mc.font, l.c, l.x + ox, l.y + oy, scale, satan ? 0xCC000000 | base : col); all = false; continue; }
            if (satan) {   // голос из-под земли: буквы тонут
                if (ft > 1400) { landed[i] = true; continue; }
                int a = (int) (200 * (1f - ft / 1400f));
                drawLetter(g, mc.font, l.c, l.x + ox, l.y + ft * 0.03f, scale, (a << 24) | base);
                all = false;
            } else {
                float y = l.y + 1500f * (ft / 1000f) * (ft / 1000f);
                if (y >= floorY) {
                    landed[i] = true;
                    spawnBoom(l.x + 3 * scale, floorY, mood, now);
                    if ((landedCount++ % 3) == 0) play(SND_BOOM, 1.7f + R.nextFloat() * 0.4f, 0.12f);
                    continue;
                }
                drawLetter(g, mc.font, l.c, l.x + ox, y, scale, col);
                all = false;
            }
        }
        if (all) { phase = 0; cur = null; }
    }
}
