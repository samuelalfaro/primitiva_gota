package color;

/**
 * Interpolación entre dos colores ARGB: u = 0 devuelve c0, u = 1 devuelve c1. Las implementaciones listas para usar
 * son RGB (por defecto), RGB_LINEAL, HSL y LCH (Oklab), o cualquier lambda propia. Si solo se da una Mezcla, se usa
 * para todas las interpolaciones.
 *
 * <p>HSL y LCH no interpolan el tono como un ángulo (para eso habría que elegir un sentido, y la elección salta cuando
 * dos tonos quedan a ~180°, lo que dibujaba cortes en el color): interpolan el vector (croma·cos tono, croma·sen tono),
 * que es continuo en todo el espacio. Entre tonos opuestos el color pasa por un gris, como en RGB; un color
 * acromático (croma 0) no aporta tono, y el vector se limita a atenuarse hacia él.
 */
@FunctionalInterface
public interface Mezcla {
    int mezclar(int c0, int c1, double u);

    /** sRGB directo, alfa premultiplicado. Es el valor por defecto. */
    public static final Mezcla RGB = Mezcla::mezclaRgb;
    /** Interpola en luz lineal (sin la curva sRGB), alfa premultiplicado. */
    public static final Mezcla RGB_LINEAL = Mezcla::mezclaRgbLineal;
    /** Tono, saturación, luminosidad; el vector (saturación·cos tono, saturación·sen tono) se interpola linealmente. */
    public static final Mezcla HSL = Mezcla::mezclaHsl;
    /** Oklab (variante perceptual de LCH): luminosidad y vector (a, b) lineales, sin saltos de tono. */
    public static final Mezcla LCH = Mezcla::mezclaOklab;

    // ---------- RGB ----------

    private static int mezclaRgb(int c0, int c1, double u) {
        if (u <= 0) return c0;
        if (u >= 1) return c1;
        double a0 = (c0 >>> 24) / 255.0, a1 = (c1 >>> 24) / 255.0;
        double a = a0 + (a1 - a0) * u;
        if (a <= 0) return 0;
        int r = canal(c0 >> 16, c1 >> 16, a0, a1, a, u);
        int g = canal(c0 >> 8, c1 >> 8, a0, a1, a, u);
        int b = canal(c0, c1, a0, a1, a, u);
        return (int) Math.round(a * 255) << 24 | r << 16 | g << 8 | b;
    }

    private static int canal(int s0, int s1, double a0, double a1, double a, double u) {
        double p0 = (s0 & 0xFF) * a0, p1 = (s1 & 0xFF) * a1;
        return (int) Math.round((p0 + (p1 - p0) * u) / a);
    }

    // ---------- RGB lineal ----------


    private static int codifica(double lin) {
        double v = lin <= 0.0031308 ? 12.92 * lin : 1.055 * Math.pow(lin, 1 / 2.4) - 0.055;
        return (int) Math.round(clamp01(v) * 255);
    }

    private static int mezclaRgbLineal(int c0, int c1, double u) {
        if (u <= 0) return c0;
        if (u >= 1) return c1;
        double a0 = (c0 >>> 24) / 255.0, a1 = (c1 >>> 24) / 255.0;
        double a = a0 + (a1 - a0) * u;
        if (a <= 0) return 0;
        int r = canalLin(c0 >> 16, c1 >> 16, a0, a1, a, u);
        int g = canalLin(c0 >> 8, c1 >> 8, a0, a1, a, u);
        int b = canalLin(c0, c1, a0, a1, a, u);
        return (int) Math.round(a * 255) << 24 | r << 16 | g << 8 | b;
    }

    private static int canalLin(int s0, int s1, double a0, double a1, double a, double u) {
        double p0 = TablaLineal.LIN[s0 & 0xFF] * a0, p1 = TablaLineal.LIN[s1 & 0xFF] * a1;
        return codifica((p0 + (p1 - p0) * u) / a);
    }

    // ---------- HSL ----------

    private static int mezclaHsl(int c0, int c1, double u) {
        if (u <= 0) return c0;
        if (u >= 1) return c1;
        double[] x = rgbAHsl(c0), y = rgbAHsl(c1);
        double h0 = Math.toRadians(x[0]), h1 = Math.toRadians(y[0]);
        double vx = x[1] * Math.cos(h0) + (y[1] * Math.cos(h1) - x[1] * Math.cos(h0)) * u;
        double vy = x[1] * Math.sin(h0) + (y[1] * Math.sin(h1) - x[1] * Math.sin(h0)) * u;
        double s = Math.min(1, Math.hypot(vx, vy)), h = s < 1e-9 ? 0 : Math.toDegrees(Math.atan2(vy, vx));
        if (h < 0) h += 360;
        double l = x[2] + (y[2] - x[2]) * u;
        double a = (c0 >>> 24) + ((c1 >>> 24) - (c0 >>> 24)) * u;
        return hslARgb(h, s, l, a);
    }

    private static double[] rgbAHsl(int c) {
        double r = ((c >> 16) & 0xFF) / 255.0, g = ((c >> 8) & 0xFF) / 255.0, b = (c & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2, d = max - min, h = 0, s = 0;
        if (d > 0) {
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60;
        }
        return new double[] { h, s, l };
    }

    private static int hslARgb(double h, double s, double l, double alfa) {
        double c = (1 - Math.abs(2 * l - 1)) * s, hp = h / 60.0;
        double x = c * (1 - Math.abs(hp % 2 - 1)), m = l - c / 2;
        double r, g, b;
        if (hp < 1) { r = c; g = x; b = 0; }
        else if (hp < 2) { r = x; g = c; b = 0; }
        else if (hp < 3) { r = 0; g = c; b = x; }
        else if (hp < 4) { r = 0; g = x; b = c; }
        else if (hp < 5) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return empaqueta(alfa, (r + m) * 255, (g + m) * 255, (b + m) * 255);
    }

    // ---------- Oklab ----------

    private static int mezclaOklab(int c0, int c1, double u) {
        if (u <= 0) return c0;
        if (u >= 1) return c1;
        double[] x = rgbAOklab(c0), y = rgbAOklab(c1);
        double a = (c0 >>> 24) + ((c1 >>> 24) - (c0 >>> 24)) * u;
        return oklabARgb(x[0] + (y[0] - x[0]) * u, x[1] + (y[1] - x[1]) * u, x[2] + (y[2] - x[2]) * u, a);
    }

    private static double[] rgbAOklab(int c) {
        double r = TablaLineal.LIN[(c >> 16) & 0xFF], g = TablaLineal.LIN[(c >> 8) & 0xFF], b = TablaLineal.LIN[c & 0xFF];
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[] { 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                              1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                              0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s };
    }

    private static int oklabARgb(double bigL, double oa, double ob, double alfa) {
        double l = bigL + 0.3963377774 * oa + 0.2158037573 * ob;
        double m = bigL - 0.1055613458 * oa - 0.0638541728 * ob;
        double s = bigL - 0.0894841775 * oa - 1.2914855480 * ob;
        l = l * l * l; m = m * m * m; s = s * s * s;
        double r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        double g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        double b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
        // fuera de gamut sRGB: se recorta cada canal
        return empaqueta(alfa, codifica(r), codifica(g), codifica(b));
    }

    // ---------- Utilidades ----------

    private static int empaqueta(double a, double r, double g, double b) {
        return clamp255(a) << 24 | clamp255(r) << 16 | clamp255(g) << 8 | clamp255(b);
    }

    private static int clamp255(double v) { return (int) Math.round(v < 0 ? 0 : (v > 255 ? 255 : v)); }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}

/** Tabla sRGB -> luz lineal de Mezcla (no es parte de la API: una interfaz no puede tener estáticos privados). */
final class TablaLineal {
    private TablaLineal() {}

    static final double[] LIN = new double[256];
    static {
        for (int i = 0; i < 256; i++) {
            double v = i / 255.0;
            TablaLineal.LIN[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
    }
}
