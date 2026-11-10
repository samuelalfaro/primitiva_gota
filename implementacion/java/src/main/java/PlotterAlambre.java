import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

/**
 * Dibuja una gota en modo alambre. API propia, separada de {@link Plotter}: no usa {@link Relleno} ni
 * {@link Costura}; las opciones son las de {@link Trazos.Ajustes}.
 *
 * <p>Uso típico:
 * <pre>{@code
 * PlotterAlambre pa = new PlotterAlambre(g2d);
 * pa.plot(gota);
 * pa.plot(gota, Trazos.Ajustes.POR_DEFECTO.conContactos(true).conOcultas(true));
 * }</pre>
 *
 * Comparte con {@link Plotter} solo el prefijo del nombre y el verbo {@code plot}: no la implementa, a propósito, hasta
 * que se vean las necesidades de todos los plotters (deuda técnica asumida). Los trazos los calcula {@link Trazos}.
 */
public final class PlotterAlambre {

    /** Dónde se dibuja: un Graphics2D ajeno, o una imagen cuyo Graphics2D se crea y se libera en cada plot. */
    private final Graphics2D g;
    private final BufferedImage dst;
    private final Trazos.Ajustes ajustes;

    private PlotterAlambre(Graphics2D g, BufferedImage dst, Trazos.Ajustes ajustes) {
        this.g = g;
        this.dst = dst;
        this.ajustes = Objects.requireNonNull(ajustes, "ajustes");
    }

    public PlotterAlambre(Graphics2D g) {
        this(g, Trazos.Ajustes.POR_DEFECTO);
    }

    public PlotterAlambre(Graphics2D g, Trazos.Ajustes ajustes) {
        this(Objects.requireNonNull(g, "g"), null, ajustes);
    }

    /**
     * Plotter sobre una imagen TYPE_INT_ARGB. El {@link Graphics2D} se crea y se dispone en cada
     * {@link #plot}; no hace falta gestionarlo desde fuera.
     */
    public static PlotterAlambre sobre(BufferedImage dst) {
        return sobre(dst, Trazos.Ajustes.POR_DEFECTO);
    }

    public static PlotterAlambre sobre(BufferedImage dst, Trazos.Ajustes ajustes) {
        Objects.requireNonNull(dst, "dst");
        if (dst.getType() != BufferedImage.TYPE_INT_ARGB)
            throw new IllegalArgumentException("Se requiere BufferedImage.TYPE_INT_ARGB");
        return new PlotterAlambre(null, dst, ajustes);
    }

    /** Dibuja la gota con los ajustes de este plotter. */
    public void plot(Gota gota) {
        plot(gota, ajustes);
    }

    /** Dibuja la gota con los ajustes indicados (no cambian los del plotter). */
    public void plot(Gota gota, Trazos.Ajustes aj) {
        Objects.requireNonNull(gota, "gota");
        Objects.requireNonNull(aj, "ajustes");
        List<Trazos.Trazo> trazos = Trazos.trazos(gota, aj);
        if (dst == null) {
            Trazos.render(g, trazos);
            return;
        }
        Graphics2D gi = dst.createGraphics();
        try {
            Trazos.render(gi, trazos);
        } finally {
            gi.dispose();
        }
    }

    /** Atajo estático: pinta en el Graphics2D dado. */
    public static void plot(Graphics2D g, Gota gota, Trazos.Ajustes aj) {
        new PlotterAlambre(g, aj).plot(gota);
    }

    public static void plot(Graphics2D g, Gota gota) {
        plot(g, gota, Trazos.Ajustes.POR_DEFECTO);
    }
}
