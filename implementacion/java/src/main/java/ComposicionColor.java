import java.util.Objects;

/**
 * Composición de rellenos al estilo de glTexEnv: combina un relleno de color (Cf) con otro de textura (Ct) o con cualquier otro relleno. No
 * confundir con {@link Composicion}, que es la composición topológica de gotas.
 */
public final class ComposicionColor {

    private ComposicionColor() {}

    // ---------- Color x textura (al estilo de glTexEnv) ----------

    /**
     * Cómo se combinan el color de base Cf (el relleno de color) y el texel Ct (el relleno de textura),
     * con las fórmulas de glTexEnv sobre canales no premultiplicados y una textura RGBA:
     *   REPLACE : C = Ct              A = At
     *   MODULATE: C = Cf * Ct         A = Af * At
     *   DECAL   : C = Cf (1 - At) + Ct At    A = Af
     *   BLEND   : C = Cf (1 - Ct) + Ce Ct    A = Af * At     (Ce = color de entorno)
     *   ADD     : C = min(1, Cf + Ct)        A = Af * At
     */
    public enum Modo { REPLACE, MODULATE, DECAL, BLEND, ADD }

    /** Combina un relleno de color con uno de textura (o con cualquier otro relleno). BLEND usa Ce = negro. */
    public static Gota.Relleno combinar(Gota.Relleno color, Gota.Relleno textura, Modo modo) {
        return combinar(color, textura, modo, 0xFF000000);
    }

    /** Igual, con el color de entorno Ce de BLEND (su alfa se ignora, como en OpenGL el de GL_BLEND). */
    public static Gota.Relleno combinar(Gota.Relleno color, Gota.Relleno textura, Modo modo, int entorno) {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(textura, "textura");
        Objects.requireNonNull(modo, "modo");
        return new Combinado(color, textura, modo, entorno);
    }

    /** Atajo: relleno de color multiplicado, reemplazado, etc. por una textura en tira. */
    public static Gota.Relleno conTextura(Gota.Relleno color, Texturas.Textura tex, Modo modo) {
        return combinar(color, Texturas.textura(tex), modo);
    }

    private static final class Combinado implements Gota.Relleno {
        private final Gota.Relleno color, tex;
        private final Modo modo;
        private final int entorno;

        Combinado(Gota.Relleno color, Gota.Relleno tex, Modo modo, int entorno) {
            this.color = color; this.tex = tex; this.modo = modo; this.entorno = entorno;
        }

        @Override public Gota.Relleno para(Gota g) { return new Combinado(color.para(g), tex.para(g), modo, entorno); }

        @Override public boolean usaUv() { return true; }

        @Override public int argb(double t, double rho) {
            throw new UnsupportedOperationException("el relleno combinado necesita la muestra y las coordenadas Uv");
        }

        @Override public int argb(Coordenadas.Muestra m, Coordenadas.Uv uv) {
            return aplica(modo, color.argb(m, uv), tex.argb(m, uv), entorno);
        }
    }

    /** Fórmulas de glTexEnv; público en el paquete para poder contrastarlas en las pruebas. */
    static int aplica(Modo modo, int cf, int ct, int ce) {
        double af = (cf >>> 24) / 255.0, at = (ct >>> 24) / 255.0, a;
        double[] r = new double[3];
        for (int i = 0; i < 3; i++) {
            int s = 16 - 8 * i;
            double f = ((cf >> s) & 0xFF) / 255.0, t = ((ct >> s) & 0xFF) / 255.0, e = ((ce >> s) & 0xFF) / 255.0;
            r[i] = switch (modo) {
                case REPLACE -> t;
                case MODULATE -> f * t;
                case DECAL -> f * (1 - at) + t * at;
                case BLEND -> f * (1 - t) + e * t;
                case ADD -> Math.min(1, f + t);
            };
        }
        a = switch (modo) {
            case REPLACE -> at;
            case DECAL -> af;
            default -> af * at;
        };
        return q(a) << 24 | q(r[0]) << 16 | q(r[1]) << 8 | q(r[2]);
    }

    private static int q(double v) { return (int) Math.round((v < 0 ? 0 : (v > 1 ? 1 : v)) * 255); }
}
