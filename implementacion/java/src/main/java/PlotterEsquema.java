import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Esquema estructural de una gota, pensado para <b>superponerlo</b> sobre otro dibujo (rasterizado, mapas de
 * diagnóstico, siluetas) y poder leer la estructura de un vistazo, con el aspecto {@code ( ~ )}:
 * <ul>
 *   <li>los dos centros, destacados;</li>
 *   <li>los cuatro puntos de contacto P1..P4, en ámbar discreto, y los radios del centro a ellos, en un tono sutil;</li>
 *   <li>el camino de los centros, a trazos y sin tiradores de la Bézier;</li>
 *   <li>los dos arcos de las tapas, entre los dos P de cada centro, por el lado alejado del otro centro.</li>
 * </ul>
 * Cada trazo lleva un halo claro semitransparente para que se lea sobre cualquier fondo. No usa {@link Relleno} ni
 * {@link Costura}: solo {@link Geometria}. Los P y los radios son los de las zonas {@link Costura#ORTOGONAL} (los
 * contactos con la envolvente), igual que en {@link PlotterAlambre}.
 *
 * <p>Uso típico:
 * <pre>{@code
 * Plotter.raster(img).plot(gota, relleno);
 * PlotterEsquema.sobre(img).plot(gota);
 * }</pre>
 * Mantiene el convenio de nombres de los plotters (prefijo {@code Plotter}, verbo {@code plot}).
 */
public final class PlotterEsquema {

    private static final Color TINTA = new Color(0x12, 0x23, 0x4A);
    private static final Color SUTIL = new Color(0x5A, 0x6B, 0x8C);
    /** Los puntos P1..P4: ámbar discreto, color yema. */
    private static final Color AMBAR = new Color(0xDC, 0xA0, 0x28);
    private static final int MUESTRAS_CAMINO = 120, MUESTRAS_ARCO = 90;

    private final Graphics2D g;
    private final BufferedImage dst;

    private PlotterEsquema(Graphics2D g, BufferedImage dst) {
        this.g = g;
        this.dst = dst;
    }

    /** Plotter sobre un Graphics2D ajeno (en el sistema de coordenadas de {@link Geometria}). */
    public PlotterEsquema(Graphics2D g) {
        this(Objects.requireNonNull(g, "g"), null);
    }

    /** Plotter sobre una imagen TYPE_INT_ARGB; el Graphics2D se crea y se libera en cada {@link #plot}. */
    public static PlotterEsquema sobre(BufferedImage dst) {
        Objects.requireNonNull(dst, "dst");
        if (dst.getType() != BufferedImage.TYPE_INT_ARGB)
            throw new IllegalArgumentException("Se requiere BufferedImage.TYPE_INT_ARGB");
        return new PlotterEsquema(null, dst);
    }

    /** Dibuja el esquema de la gota. */
    public void plot(Geometria geo) {
        Objects.requireNonNull(geo, "geo");
        if (dst == null) {
            esquema(g, geo);
            return;
        }
        Graphics2D gi = dst.createGraphics();
        try {
            esquema(gi, geo);
        } finally {
            gi.dispose();
        }
    }

    /** Atajo estático: pinta en el Graphics2D dado. */
    public static void plot(Graphics2D g, Geometria geo) {
        new PlotterEsquema(g).plot(geo);
    }

    private static void esquema(Graphics2D g, Geometria geo) {
        Zonas zonas = new Zonas(geo, Costura.ORTOGONAL, Tira.POR_DEFECTO.cortes(geo));
        double[][] c = new double[2][], p = new double[4][];
        double[] r = new double[2];
        for (int e = 0; e < 2; e++) {
            Zonas.Fr f = zonas.marco(e);
            c[e] = new double[] { f.cx(), f.cy() };
            r[e] = f.r();
            for (int l = 0; l < 2; l++) p[2 * e + l] = new double[] { f.cx() + f.ox()[l], f.cy() + f.oy()[l] };
        }
        Stroke trazo = g.getStroke();
        Color color = g.getColor();
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object control = g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        try {
            camino(g, geo);
            for (int e = 0; e < 2; e++) arcoTapa(g, c[e], c[1 - e], r[e], p[2 * e], p[2 * e + 1]);
            for (int e = 0; e < 2; e++)
                for (int l = 0; l < 2; l++) {
                    Line2D radio = new Line2D.Double(c[e][0], c[e][1], p[2 * e + l][0], p[2 * e + l][1]);
                    linea(g, radio, SUTIL, 0.9f, 2.4f);
                }
            for (double[] q : p) punto(g, q, 3.6, 2.6, AMBAR);
            for (double[] q : c) punto(g, q, 5.5, 4.0, TINTA);
        } finally {
            g.setColor(color);
            g.setStroke(trazo);
            if (aa != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
            if (control != null) g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, control);
        }
    }

    /** El camino de los centros, a trazos (sin tiradores). */
    private static void camino(Graphics2D g, Geometria geo) {
        Path2D camino = new Path2D.Double();
        for (int i = 0; i <= MUESTRAS_CAMINO; i++) {
            double t = i / (double) MUESTRAS_CAMINO;
            if (i == 0) camino.moveTo(geo.cx(t), geo.cy(t)); else camino.lineTo(geo.cx(t), geo.cy(t));
        }
        g.setColor(new Color(255, 255, 255, 200));
        g.setStroke(new BasicStroke(3.2f));
        g.draw(camino);
        g.setColor(TINTA);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] { 5f, 4f }, 0f));
        g.draw(camino);
    }

    /** El arco de la tapa de centro {@code c} entre sus dos contactos, por el lado alejado del otro centro {@code otro}. */
    private static void arcoTapa(Graphics2D g, double[] c, double[] otro, double r, double[] pa, double[] pb) {
        if (!(r > 1e-9)) return;
        double a = Math.atan2(pa[1] - c[1], pa[0] - c[0]), b = Math.atan2(pb[1] - c[1], pb[0] - c[0]);
        double positivo = ((b - a) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
        double mx = c[0] + r * Math.cos(a + positivo / 2), my = c[1] + r * Math.sin(a + positivo / 2);
        boolean porPositivo = Math.hypot(mx - otro[0], my - otro[1])
            >= Math.hypot(2 * c[0] - mx - otro[0], 2 * c[1] - my - otro[1]);
        double barrido = porPositivo ? positivo : -(2 * Math.PI - positivo);
        Path2D arco = new Path2D.Double();
        for (int i = 0; i <= MUESTRAS_ARCO; i++) {
            double th = a + barrido * i / MUESTRAS_ARCO;
            double x = c[0] + r * Math.cos(th), y = c[1] + r * Math.sin(th);
            if (i == 0) arco.moveTo(x, y); else arco.lineTo(x, y);
        }
        g.setColor(new Color(255, 255, 255, 210));
        g.setStroke(new BasicStroke(4.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(arco);
        g.setColor(TINTA);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(arco);
    }

    private static void linea(Graphics2D g, Line2D l, Color color, float ancho, float anchoHalo) {
        g.setColor(new Color(255, 255, 255, 170));
        g.setStroke(new BasicStroke(anchoHalo));
        g.draw(l);
        g.setColor(color);
        g.setStroke(new BasicStroke(ancho));
        g.draw(l);
    }

    private static void punto(Graphics2D g, double[] q, double rHalo, double r, Color color) {
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(q[0] - rHalo, q[1] - rHalo, 2 * rHalo, 2 * rHalo));
        g.setColor(color);
        g.fill(new Ellipse2D.Double(q[0] - r, q[1] - r, 2 * r, 2 * r));
    }
}
