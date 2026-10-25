package moodygod;

/** Интонация печати: знаки препинания меняют высоту голоса, громкость и паузы. */
public final class Intonation {
    public static final int BASE = 40;
    public record Plan(int[] t, float[] pitch, float[] vol) {}

    public static Plan plan(String s) {
        int n = s.length();
        int[] t = new int[n + 1];
        float[] p = new float[n];
        float[] v = new float[n];
        int time = 0, i = 0;
        while (i < n) {
            int j = i;
            while (j < n && ".!?…".indexOf(s.charAt(j)) < 0) j++;
            char end = j < n ? s.charAt(j) : '.';
            boolean ell = end == '…' || (end == '.' && j + 1 < n && s.charAt(j + 1) == '.');
            int last = Math.min(j, n - 1);
            int len = Math.max(1, last - i + 1);
            for (int k = i; k <= last; k++) {
                float f = (float) (k - i) / len;
                float pp, vv = 1f;
                if (ell) { pp = 1f - 0.3f * f; vv = 0.8f; }
                else if (end == '?') pp = 0.95f + 0.55f * f;
                else if (end == '!') { pp = 1.05f + 0.1f * (float) Math.sin(f * Math.PI); vv = 1.3f; }
                else pp = 1f - 0.1f * f;
                p[k] = pp; v[k] = vv;
                t[k] = time;
                char c = s.charAt(k);
                int d = c == ' ' ? BASE * 6 / 10 : c == '\n' ? 0 : BASE;
                boolean boundary = k + 1 >= n || s.charAt(k + 1) == ' ' || s.charAt(k + 1) == '\n';
                if (c == ',' || c == ':' || c == ';') d += 140;
                else if (c == '—' || (c == '-' && boundary)) d += 100;
                else if (boundary) {
                    if (c == '.') d += 260;
                    else if (c == '!') d += 180;
                    else if (c == '?') d += 220;
                    else if (c == '…') d += 340;
                }
                time += d;
            }
            i = last + 1;
        }
        t[n] = time;
        return new Plan(t, p, v);
    }
}
