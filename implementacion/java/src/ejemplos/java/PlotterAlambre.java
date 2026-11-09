import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Dibuja una gota en modo alambre. API propia, separada de {@link Plotter}: no usa {@link Relleno} ni
 * {@link Costura}; las opciones son las de {@link Alambre.Ajustes}.
 *
 * <p>Uso típico:
 * <pre>{@code
 * PlotterAlambre pa = new PlotterAlambre(g2d);
 * pa.plot(gota);
 * pa.plot(gota, Alambre.Ajustes.POR_DEFECTO.conContactos(true).conOcultas(true));
 * }</pre>
 *
 * Más adelante se podrá generalizar hacia la interfaz {@link Plotter} cuando queden claras las necesidades.
 * Cuando Alambre pase a main, esta clase se mueve con él.
 */
public final class PlotterAlambre {

    private final Graphics2D g;
    private final Alambre.Ajustes ajustes;

    public PlotterAlambre(Graphics2D g) {
        this(g, Alambre.Ajustes.POR_DEFECTO);
    }

    public PlotterAlambre(Graphics2D g, Alambre.Ajustes ajustes) {
        this.g = Objects.requireNonNull(g, "g");
        this.ajustes = Objects.requireNonNull(ajustes, "ajustes");
    }

    /**
     * Plotter sobre una imagen TYPE_INT_ARGB. El {@link Graphics2D} se crea y se dispone en cada
     * {@link #plot}; no hace falta gestionarlo desde fuera.
     */
    public static PlotterAlambre sobre(BufferedImage dst) {
        return sobre(dst, Alambre.Ajustes.POR_DEFECTO);
    }

    public static PlotterAlambre sobre(BufferedImage dst, Alambre.Ajustes ajustes) {
        Objects.requireNonNull(dst, "dst");
        if (dst.getType() != BufferedImage.TYPE_INT_ARGB)
            throw new IllegalArgumentException("Se requiere BufferedImage.TYPE_INT_ARGB");
        return new SobreImagen(dst, ajustes);
    }

    /** Dibuja la gota con los ajustes de este plotter. */
    public void plot(Gota gota) {
        plot(gota, ajustes);
    }

    /** Dibuja la gota con los ajustes indicados (no cambian los del plotter). */
    public void plot(Gota gota, Alambre.Ajustes aj) {
        Objects.requireNonNull(gota, "gota");
        Objects.requireNonNull(aj, "ajustes");
        Alambre.render(g, Alambre.trazos(gota, aj));
    }

    /** Atajo estático: pinta en el Graphics2D dado. */
    public static void plot(Graphics2D g, Gota gota, Alambre.Ajustes aj) {
        new PlotterAlambre(g, aj).plot(gota);
    }

    public static void plot(Graphics2D g, Gota gota) {
        plot(g, gota, Alambre.Ajustes.POR_DEFECTO);
    }

    /** Variante que crea y dispone el Graphics2D de la imagen en cada plot. */
    private static final class SobreImagen extends PlotterAlambre {
        private final BufferedImage dst;

        SobreImagen(BufferedImage dst, Alambre.Ajustes aj) {
            super(dummyG(dst), aj);
            this.dst = dst;
        }

        private static Graphics2D dummyG(BufferedImage dst) {
            Graphics2D g = dst.createGraphics();
            g.dispose();
            return g;
        }

        @Override public void plot(Gota gota, Alambre.Ajustes ajustes) {
            Objects.requireNonNull(gota, "gota");
            Objects.requireNonNull(ajustes, "ajustes");
            Graphics2D g = dst.createGraphics();
            try {
                Alambre.render(g, Alambre.trazos(gota, ajustes));
            } finally {
                g.dispose();
            }
        }
    }
}
