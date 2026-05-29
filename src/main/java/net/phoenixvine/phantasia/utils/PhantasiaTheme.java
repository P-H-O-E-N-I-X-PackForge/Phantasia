package net.phoenixvine.phantasia.utils;

import net.minecraft.util.Mth;
import net.phoenixvine.phantasia.configs.PhantasiaConfigs;

public enum PhantasiaTheme {

    // ── Cobalt (default) ──────────────────────────────────────────────────────
    COBALT(
            /* bg */ 0xFF080810,
            /* panel */ 0xEE0C0C1A,
            /* accent */ 0xFF4FC3F7,
            /* btn */ 0xBB151528,
            /* btnHov */ 0xBB1A2840,
            /* btnAct */ 0xBB0D3050,
            /* text */ 0xFFDDDDDD,
            /* dim */ 0xFF667788,
            /* tlBg */ 0xFF0F1820,
            /* prog */ 0xFF4FC3F7,
            /* warn */ 0xFFFFB74D,
            /* hilight */ 0xFFFFEB3B,
            /* rainbow */ false),

    // ── Rainbow ───────────────────────────────────────────────────────────────
    RAINBOW(
            /* bg */ 0xFF090909,
            /* panel */ 0xEE111111,
            /* accent */ 0xFFFF4444,
            /* btn */ 0xBB1A1A1A,
            /* btnHov */ 0xBB2A2A2A,
            /* btnAct */ 0xBB202040,
            /* text */ 0xFFEEEEEE,
            /* dim */ 0xFF666666,
            /* tlBg */ 0xFF101010,
            /* prog */ 0xFFFF4444,
            /* warn */ 0xFFFFB74D,
            /* hilight */ 0xFFFFFF44,
            /* rainbow */ true),

    // ── Amethyst ──────────────────────────────────────────────────────────────
    AMETHYST(
            /* bg */ 0xFF08060F,
            /* panel */ 0xEE100818,
            /* accent */ 0xFFB39DDB,
            /* btn */ 0xBB1A0A2E,
            /* btnHov */ 0xBB2A1048,
            /* btnAct */ 0xBB3D1A6E,
            /* text */ 0xFFE8D5FF,
            /* dim */ 0xFF7755AA,
            /* tlBg */ 0xFF0C0514,
            /* prog */ 0xFFFFD700,
            /* warn */ 0xFFFF9800,
            /* hilight */ 0xFFFFD700,
            /* rainbow */ false),

    // ── Minecraft ─────────────────────────────────────────────────────────────
    MINECRAFT(
            /* bg */ 0xFF373737,
            /* panel */ 0xFF2D2D2D,
            /* accent */ 0xFFFFAA00,
            /* btn */ 0xFF5A5A5A,
            /* btnHov */ 0xFF6E6E6E,
            /* btnAct */ 0xFF4A4A8A,
            /* text */ 0xFFFFFFFF,
            /* dim */ 0xFFAAAAAA,
            /* tlBg */ 0xFF1E1E1E,
            /* prog */ 0xFF55FF55,
            /* warn */ 0xFFFF5555,
            /* hilight */ 0xFFFFFF55,
            /* rainbow */ false),

    // ── Crimson (NEW) ─────────────────────────────────────────────────────────
    // Dark netherite / blood-red aesthetic. Perfect for combat or dark settings.
    CRIMSON(
            /* bg */ 0xFF120505,
            /* panel */ 0xEE1C0A0A,
            /* accent */ 0xFFFF5252,   // Vivid Red
            /* btn */ 0xBB2A1010,
            /* btnHov */ 0xBB421414,
            /* btnAct */ 0xBB5A1818,
            /* text */ 0xFFFFEAEA,
            /* dim */ 0xFF996666,
            /* tlBg */ 0xFF180808,
            /* prog */ 0xFFFF5252,
            /* warn */ 0xFFFFB74D,
            /* hilight */ 0xFFFFEB3B,
            /* rainbow */ false),

    // ── Emerald (NEW) ─────────────────────────────────────────────────────────
    // Matrix / terminal / cozy green vibe.
    EMERALD(
            /* bg */ 0xFF040A06,
            /* panel */ 0xEE08140C,
            /* accent */ 0xFF69F0AE,   // Bright Mint Emerald
            /* btn */ 0xBB0D2415,
            /* btnHov */ 0xBB143A22,
            /* btnAct */ 0xBB1B5230,
            /* text */ 0xFFE0F2F1,
            /* dim */ 0xFF669977,
            /* tlBg */ 0xFF060F09,
            /* prog */ 0xFF69F0AE,
            /* warn */ 0xFFFFB74D,
            /* hilight */ 0xFFFFEB3B,
            /* rainbow */ false),

    // ── Void (NEW) ────────────────────────────────────────────────────────────
    // Extremely dark pitch-black background with highly contrasting cosmic white/blue text.
    VOID(
            /* bg */ 0xFF020204,
            /* panel */ 0xEE050508,
            /* accent */ 0xFFE0E0FF,   // Starlight White
            /* btn */ 0xBB101018,
            /* btnHov */ 0xBB181826,
            /* btnAct */ 0xBB252538,
            /* text */ 0xFFF5F5F5,
            /* dim */ 0xFF555566,
            /* tlBg */ 0xFF08080C,
            /* prog */ 0xFF80DEEA,   // Cyan progress
            /* warn */ 0xFFFF8A80,
            /* hilight */ 0xFFFFFF8D,
            /* rainbow */ false),

    // ── Cyberpunk (NEW) ───────────────────────────────────────────────────────
    // High-contrast synthwave mix: Pitch black panels, hot pink accent, cyan details.
    CYBERPUNK(
            /* bg */ 0xFF050308,
            /* panel */ 0xEE0A0512,
            /* accent */ 0xFFFF007F,   // Neon Pink
            /* btn */ 0xBB140520,
            /* btnHov */ 0xBB280A40,
            /* btnAct */ 0xBB00E5FF,   // Cyan active flash
            /* text */ 0xFF00E5FF,   // Neon Cyan Text
            /* dim */ 0xFF7A20A0,
            /* tlBg */ 0xFF0A0015,
            /* prog */ 0xFF00E5FF,
            /* warn */ 0xFFFFEA00,
            /* hilight */ 0xFFFF007F,
            /* rainbow */ false),

    // ── Quartz (NEW) ──────────────────────────────────────────────────────────
    // A clean, bright Light-Mode option.
    QUARTZ(
            /* bg */ 0xFFEAEAEA,
            /* panel */ 0xEEF5F5F5,
            /* accent */ 0xFF1976D2,   // Deep blue contrast
            /* btn */ 0xFFDCDCDC,
            /* btnHov */ 0xFFCECECE,
            /* btnAct */ 0xFFB4B4B4,
            /* text */ 0xFF212121,   // Dark text
            /* dim */ 0xFF666666,
            /* tlBg */ 0xFFE0E0E0,
            /* prog */ 0xFF4CAF50,   // Fresh green progress
            /* warn */ 0xFFF57C00,
            /* hilight */ 0xFFFBC02D,
            /* rainbow */ false);

    // ── Fields & Logic below remain completely unchanged ──────────────────────
    public final int C_BG;
    public final int C_PANEL;
    public final int C_ACCENT;
    public final int C_BTN;
    public final int C_BTN_HOV;
    public final int C_BTN_ACT;
    public final int C_TEXT;
    public final int C_DIM;
    public final int C_TL_BG;
    public final int C_PROG;
    public final int C_WARN;
    public final int C_HILIGHT;
    private final boolean rainbow;

    PhantasiaTheme(int bg, int panel, int accent, int btn, int btnHov, int btnAct,
                   int text, int dim, int tlBg, int prog, int warn, int hilight,
                   boolean rainbow) {
        this.C_BG = bg;
        this.C_PANEL = panel;
        this.C_ACCENT = accent;
        this.C_BTN = btn;
        this.C_BTN_HOV = btnHov;
        this.C_BTN_ACT = btnAct;
        this.C_TEXT = text;
        this.C_DIM = dim;
        this.C_TL_BG = tlBg;
        this.C_PROG = prog;
        this.C_WARN = warn;
        this.C_HILIGHT = hilight;
        this.rainbow = rainbow;
    }

    private static PhantasiaTheme cachedTheme = COBALT;
    private static String lastKnownConfigString = null;

    public static PhantasiaTheme current() {
        String activeStr = PhantasiaConfigs.INSTANCE.phantasiaUI.theme;
        if (activeStr != lastKnownConfigString) {
            if (!activeStr.equals(lastKnownConfigString)) {
                lastKnownConfigString = activeStr;
                cachedTheme = fromString(activeStr);
            }
        }
        return cachedTheme;
    }

    public boolean isRainbow() {
        return rainbow;
    }

    public int getAccent(long gameTime) {
        return rainbow ? hueToArgb(gameTime, 0f, 1f) : C_ACCENT;
    }

    public int getProg(long gameTime) {
        return rainbow ? hueToArgb(gameTime, 0.1f, 0.9f) : C_PROG;
    }

    public int getHilight(long gameTime) {
        return rainbow ? hueToArgb(gameTime, 0.25f, 0.85f) : C_HILIGHT;
    }

    private static int hueToArgb(long gameTime, float saturation, float brightness) {
        float hue = (gameTime % 200) / 200f;
        float h = hue * 6f;
        int i = (int) h;
        float f = h - i;
        float p = brightness * (1f - saturation);
        float q = brightness * (1f - saturation * f);
        float t = brightness * (1f - saturation * (1f - f));
        float r, g, b;
        switch (i % 6) {
            case 0 -> {
                r = brightness;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = brightness;
                b = p;
            }
            case 2 -> {
                r = p;
                g = brightness;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = brightness;
            }
            case 4 -> {
                r = t;
                g = p;
                b = brightness;
            }
            default -> {
                r = brightness;
                g = p;
                b = q;
            }
        }
        int ri = Mth.clamp((int) (r * 255), 0, 255);
        int gi = Mth.clamp((int) (g * 255), 0, 255);
        int bi = Mth.clamp((int) (b * 255), 0, 255);
        return (0xFF << 24) | (ri << 16) | (gi << 8) | bi;
    }

    public static PhantasiaTheme fromString(String s) {
        if (s == null) return COBALT;
        try {
            return valueOf(s.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return COBALT;
        }
    }
}
