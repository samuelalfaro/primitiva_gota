import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.font.FontRenderContext;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
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
public class PlotterAlambre {

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

/**
 * Modo alambre de una geometría: la descripción vectorial de su estructura, sin dibujar nada.
 *
 * {@link #trazos} devuelve una lista de {@link Trazo} (un {@link Rol} y un {@link Shape}): el mismo resultado sirve para
 * pintar en un {@link Graphics2D} ({@link #render}) o para escribir un SVG ({@link Svg}). El estilo de cada rol se define
 * una sola vez, en {@link Rol}.
 *
 * Solo conoce {@link Geometria}: no depende de Gota, de Composicion ni del render sólido. La envolvente sale de los
 * puntos de contacto de la familia (los que usa {@code Zonas}, visibles en el paquete de forma provisional) y de los
 * arcos de los extremos; no de una malla. De ella, la silueta ({@link Rol#CONTORNO}) son los tramos que
 * {@link Geometria#contiene} confirma como borde real: un punto de la envolvente es borde si un punto apenas más hacia
 * fuera queda fuera de la gota. El resto (la parte de la envolvente que queda dentro, en lazos, cuellos y pliegues) es
 * {@link Rol#OCULTA}, opcional. Donde no hay contacto (casos degenerados) se ve lo que hace hoy el modelo.
 */
final class Alambre {

    private Alambre() {}

    /** Qué representa un trazo; cada rol lleva su estilo. */
    public enum Rol {
        /** Silueta: los tramos de la envolvente (ramas y arcos de los extremos) que son borde de la gota. El elemento principal. */
        CONTORNO("contorno", new Color(40, 40, 48), 2.0f, null, false),
        /** Parte de la envolvente que queda dentro de la gota (no es silueta). Solo con {@code ocultas}. */
        OCULTA("oculta", new Color(150, 150, 165), 0.8f, new float[] { 3f, 3f }, false),
        /** Secciones interiores de la familia. Discretas. */
        COSTILLA("costilla", new Color(120, 165, 190), 0.8f, null, false),
        /** Primera y última sección: algo más de presencia que las interiores. */
        EXTREMO("extremo", new Color(70, 130, 170), 1.3f, null, false),
        /** Camino: la Bézier cúbica de los centros. */
        CAMINO("camino", new Color(70, 110, 190), 1.5f, null, false),
        /** Tiradores: segmentos del extremo al punto de control. */
        TIRADOR("tirador", new Color(140, 140, 140), 0.8f, new float[] { 5f, 4f }, false),
        /** Extremos del camino y puntos de control. */
        PUNTO("punto", new Color(210, 90, 65), 1.0f, null, true),
        /** Segmentos del centro de cada extremo a sus dos puntos de contacto (las costuras de ESPIGA en los extremos). */
        RADIO("radio", new Color(200, 50, 50), 1.2f, null, false),
        /** Puntos de contacto P1..P4 con la envolvente. */
        CONTACTO("contacto", new Color(200, 50, 50), 1.0f, null, true),
        /** Nombres de P1..P4, como contornos de texto (siguen siendo Shape). */
        ETIQUETA("etiqueta", new Color(150, 40, 40), 0f, null, true),
        /** Radios de los extremos, a lo largo del camino. */
        SEMIEJE("semieje", new Color(30, 140, 120), 1.6f, null, false),
        /** Nombres de los centros y de los radios (C1, C2, r1, r2). */
        NOMBRE("nombre", new Color(30, 90, 85), 0f, null, true),
        /** Solo con diagnóstico: la sección y el punto de tm. */
        DIAGNOSTICO("diagnostico", new Color(220, 150, 50), 1.3f, null, false);

        /** Nombre de la clase CSS en el SVG. */
        public final String clase;
        public final Color color;
        public final float ancho;
        /** Patrón de guiones, o null si es continuo. */
        public final float[] guiones;
        /** true: se rellena del color del rol (los puntos). */
        public final boolean relleno;

        Rol(String clase, Color color, float ancho, float[] guiones, boolean relleno) {
            this.clase = clase; this.color = color; this.ancho = ancho; this.guiones = guiones; this.relleno = relleno;
        }

        BasicStroke trazo() {
            return guiones == null
                ? new BasicStroke(ancho, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                : new BasicStroke(ancho, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, guiones, 0f);
        }
    }

    /** Una forma vectorial y su papel en el alambre. */
    public record Trazo(Rol rol, Shape forma) {}

    /**
     * Qué se dibuja y con cuánto detalle.
     *
     * @param secciones   número de secciones de la familia, extremos incluidos, repartidas uniformemente en t (mínimo 2:
     *                    solo los extremos)
     * @param muestras    puntos por rama del contorno
     * @param diagnostico añade la sección y el punto de tm
     * @param contactos   añade los puntos de contacto P1..P4 y los segmentos desde los centros
     * @param radios      añade los radios de los extremos: r1 sale del centro hacia atrás (contra la tangente) y r2 hacia delante
     * @param etiquetas   escribe los nombres (C1, C2, P1..P4, radios) de lo que se dibuje
     * @param ocultas     dibuja también, discontinua, la parte de la envolvente que queda dentro de la gota
     */
    public record Ajustes(int secciones, int muestras, boolean diagnostico, boolean contactos, boolean radios,
                          boolean etiquetas, boolean ocultas) {
        /** Silueta y extremos, con los nombres de los centros y sin diagnóstico, contactos, radios ni envolvente oculta. */
        public static final Ajustes POR_DEFECTO = new Ajustes(2, 160, false, false, false, true, false);
        public Ajustes {
            if (secciones < 2 || muestras < 2) throw new IllegalArgumentException("secciones >= 2 y muestras >= 2");
        }
        public Ajustes conSecciones(int n) { return new Ajustes(n, muestras, diagnostico, contactos, radios, etiquetas, ocultas); }
        public Ajustes conMuestras(int n) { return new Ajustes(secciones, n, diagnostico, contactos, radios, etiquetas, ocultas); }
        public Ajustes conDiagnostico(boolean d) { return new Ajustes(secciones, muestras, d, contactos, radios, etiquetas, ocultas); }
        public Ajustes conContactos(boolean c) { return new Ajustes(secciones, muestras, diagnostico, c, radios, etiquetas, ocultas); }
        public Ajustes conRadios(boolean r) { return new Ajustes(secciones, muestras, diagnostico, contactos, r, etiquetas, ocultas); }
        public Ajustes conEtiquetas(boolean e) { return new Ajustes(secciones, muestras, diagnostico, contactos, radios, e, ocultas); }
        public Ajustes conOcultas(boolean o) { return new Ajustes(secciones, muestras, diagnostico, contactos, radios, etiquetas, o); }
    }

    private static final int SEGMENTOS_ARCO = 64;

    public static List<Trazo> trazos(Geometria geo) { return trazos(geo, Ajustes.POR_DEFECTO); }

    /** Los trazos del alambre, del fondo al frente: secciones, camino, contorno, contactos, puntos. */
    public static List<Trazo> trazos(Geometria geo, Ajustes aj) {
        List<Trazo> out = new ArrayList<>();
        int n = aj.secciones();
        for (int i = 1; i < n - 1; i++) out.add(new Trazo(Rol.COSTILLA, seccion(geo, i / (double) (n - 1))));
        out.add(new Trazo(Rol.EXTREMO, seccion(geo, 0)));
        out.add(new Trazo(Rol.EXTREMO, seccion(geo, 1)));
        if (aj.diagnostico()) out.add(new Trazo(Rol.DIAGNOSTICO, seccion(geo, geo.tm())));

        double x0 = geo.cx(0), y0 = geo.cy(0), x3 = geo.cx(1), y3 = geo.cy(1);
        double x1 = x0 + geo.dcx(0) / 3, y1 = y0 + geo.dcy(0) / 3;
        double x2 = x3 - geo.dcx(1) / 3, y2 = y3 - geo.dcy(1) / 3;
        out.add(new Trazo(Rol.TIRADOR, new Line2D.Double(x0, y0, x1, y1)));
        out.add(new Trazo(Rol.TIRADOR, new Line2D.Double(x3, y3, x2, y2)));
        Path2D camino = new Path2D.Double();
        camino.moveTo(x0, y0);
        camino.curveTo(x1, y1, x2, y2, x3, y3);
        out.add(new Trazo(Rol.CAMINO, camino));

        var zonas = new Zonas(geo, Costura.ORTOGONAL, Tira.POR_DEFECTO.cortes(geo));
        silueta(geo, zonas, aj, out);
        if (aj.radios()) radios(zonas, aj.etiquetas(), out);
        if (aj.contactos()) contactos(zonas, aj.etiquetas(), out);
        if (aj.etiquetas()) nombresCentros(zonas, out);

        out.add(new Trazo(Rol.PUNTO, punto(x0, y0, 4)));
        out.add(new Trazo(Rol.PUNTO, punto(x3, y3, 4)));
        out.add(new Trazo(Rol.PUNTO, punto(x1, y1, 3)));
        out.add(new Trazo(Rol.PUNTO, punto(x2, y2, 3)));
        if (aj.diagnostico()) out.add(new Trazo(Rol.DIAGNOSTICO, punto(geo.cx(geo.tm()), geo.cy(geo.tm()), 3)));
        return out;
    }

    private static final Font FUENTE = new Font(Font.SANS_SERIF, Font.BOLD, 11);

    /**
     * P1 y P2 son los contactos del extremo inicial (lados -N y +N) y P3 y P4 los del final, de modo que las ramas del
     * contorno son P1 -> P3 (lado -N) y P2 -> P4 (lado +N). Cada uno va unido a su centro por un segmento.
     */
    private static void contactos(Zonas zonas, boolean etiquetas, List<Trazo> out) {
        String[] nombres = { "P1", "P2", "P3", "P4" };
        for (int e = 0; e < 2; e++) {
            Zonas.Fr f = zonas.marco(e);
            for (int l = 0; l < 2; l++) {
                double px = f.cx() + f.ox()[l], py = f.cy() + f.oy()[l];
                out.add(new Trazo(Rol.RADIO, new Line2D.Double(f.cx(), f.cy(), px, py)));
                out.add(new Trazo(Rol.CONTACTO, punto(px, py, 3.5)));
                double dx = px - f.cx(), dy = py - f.cy(), d = Math.hypot(dx, dy);
                double ux = d > 1e-9 ? dx / d : 0, uy = d > 1e-9 ? dy / d : -1;
                if (etiquetas) out.add(new Trazo(Rol.ETIQUETA, etiqueta(nombres[2 * e + l], px + 11 * ux, py + 11 * uy)));
            }
        }
    }

    /** Ejes (T, N) del marco: {tx, ty, nx, ny}. Con el camino parado, Geometria ya da T = eje x y N = (0, -1) (y hacia arriba). */
    private static double[] ejes(Zonas.Fr f) { return new double[] { f.tx(), f.ty(), f.nx(), f.ny() }; }

    /**
     * Radios de los extremos. Salen del centro contra la tangente en el extremo inicial (r1) y a favor en el final (r2),
     * de modo que se lee (r1, C1, C2, r2).
     */
    private static void radios(Zonas zonas, boolean etiquetas, List<Trazo> out) {
        for (int e = 0; e < 2; e++) {
            Zonas.Fr f = zonas.marco(e);
            double[] m = ejes(f);
            double sg = e == 0 ? -1 : 1, r = f.r();
            out.add(new Trazo(Rol.SEMIEJE, new Line2D.Double(f.cx(), f.cy(), f.cx() + sg * r * m[0], f.cy() + sg * r * m[1])));
            if (etiquetas) out.add(new Trazo(Rol.NOMBRE, etiqueta("r" + (e + 1),
                f.cx() + sg * (r + 11) * m[0], f.cy() + sg * (r + 11) * m[1])));
        }
    }

    /** C1 y C2, en la diagonal entre el semieje tangente y el lado -N, hacia fuera de la gota. */
    private static void nombresCentros(Zonas zonas, List<Trazo> out) {
        for (int e = 0; e < 2; e++) {
            Zonas.Fr f = zonas.marco(e);
            double[] m = ejes(f);
            double sg = e == 0 ? -1 : 1;
            out.add(new Trazo(Rol.NOMBRE, etiqueta("C" + (e + 1),
                f.cx() + 11 * (sg * 0.7 * m[0] - 0.7 * m[2]), f.cy() + 11 * (sg * 0.7 * m[1] - 0.7 * m[3]))));
        }
    }

    /** El texto como contorno vectorial, centrado en (x, y). */
    private static Shape etiqueta(String texto, double x, double y) {
        Shape s = FUENTE.createGlyphVector(new FontRenderContext(null, true, true), texto).getOutline();
        Rectangle2D b = s.getBounds2D();
        return AffineTransform.getTranslateInstance(x - b.getCenterX(), y - b.getCenterY()).createTransformedShape(s);
    }

    /** Pinta los trazos en el sistema de coordenadas del Graphics2D (las coordenadas de Geometria son de imagen). */
    public static void render(Graphics2D g, List<Trazo> trazos) {
        var color = g.getColor();
        var trazo = g.getStroke();
        var aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        var control = g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);   // sin ajustar vértices al píxel
        try {
            for (Trazo t : trazos) {
                g.setColor(t.rol().color);
                g.setStroke(t.rol().trazo());
                if (t.rol().relleno) g.fill(t.forma()); else g.draw(t.forma());
            }
        } finally {
            g.setColor(color);
            g.setStroke(trazo);
            if (aa != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
            if (control != null) g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, control);
        }
    }

    public static void render(Graphics2D g, Geometria geo) { render(g, trazos(geo)); }

    // ---------- Piezas ----------

    private static Shape punto(double x, double y, double r) {
        return new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
    }

    /** Sección de la familia en t: el círculo de centro c(t) y radio r(t). */
    private static Shape seccion(Geometria geo, double t) {
        double r = geo.rad(t);
        return new Ellipse2D.Double(geo.cx(t) - r, geo.cy(t) - r, 2 * r, 2 * r);
    }

    /** Un punto de la envolvente: posición, normal hacia fuera (sin normalizar) y distancia del test de pertenencia. */
    private record Punto(double x, double y, double nx, double ny, double eps) {}

    /** Un tramo de la envolvente, de s = 0 a s = 1. */
    private interface Tramo {
        Punto en(double s);
        int muestras();
    }

    private static final double EPS_REL = 1e-3;

    /** Punto del círculo del marco f con ángulo th (desde T hacia N) y normal hacia fuera. */
    private static Punto enCirculo(Zonas.Fr f, double th) {
        double r = Math.max(f.r(), 1e-12);
        double A = r * Math.cos(th), S = r * Math.sin(th);
        double nA = Math.cos(th) / r, nS = Math.sin(th) / r;
        return new Punto(f.cx() + A * f.tx() + S * f.nx(), f.cy() + A * f.ty() + S * f.ny(),
            nA * f.tx() + nS * f.nx(), nA * f.ty() + nS * f.ny(), Math.max(EPS_REL * f.r(), 1e-4));
    }

    /** Punto de contacto del lado l (0 = -N, 1 = +N) en el marco f. */
    private static Punto enContacto(Zonas.Fr f, int l) {
        return enCirculo(f, angulo(f, l));
    }

    /**
     * Los tramos de la envolvente, en orden y cerrados: rama del lado -N (t de 0 a 1), arco del extremo final por delante
     * (pasando por +T), rama del lado +N (t de 1 a 0) y arco del extremo inicial por detrás (pasando por -T). Cada rama
     * se parte en los saltos de la envolvente (ver {@link #saltos}) y ahí lleva el arco de la figura.
     */
    private static List<Tramo> tramos(Zonas zonas, int muestras) {
        Zonas.Fr f0 = zonas.marco(0), f1 = zonas.marco(1);
        double[] fin = barrido(angulo(f1, 0), angulo(f1, 1)), ini = barrido(angulo(f0, 1), angulo(f0, 0));
        List<double[]> saltos = saltos(zonas, 4 * muestras);
        List<Tramo> out = new ArrayList<>(ramas(zonas, saltos, 0, muestras));
        out.add(tramo(SEGMENTOS_ARCO, s -> enCirculo(f1, fin[0] + fin[1] * s)));
        out.addAll(ramas(zonas, saltos, 1, muestras));
        out.add(tramo(SEGMENTOS_ARCO, s -> enCirculo(f0, ini[0] + ini[1] * s)));
        return out;
    }

    /**
     * Saltos de la envolvente: parámetros {lo, hi} muy próximos entre los que el contacto de un lado cambia de sitio de
     * golpe mientras el centro apenas se mueve. Ocurre con velocidad nula: en una cúspide (la tangente se invierte) o
     * cuando el radio cambia de signo de variación con el camino parado (el contacto pasa de una punta a la otra). La
     * envolvente no recorre nada entre los dos contactos, pero la silueta real sí: pasa por el arco de la figura en ese
     * instante ({@link #arcoSalto}).
     */
    private static List<double[]> saltos(Zonas zonas, int n) {
        List<double[]> out = new ArrayList<>();
        Zonas.Fr prev = zonas.marco(0);
        double tp = 0;
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n;
            Zonas.Fr f = zonas.marco(t);
            double r = Math.max(f.r(), 1e-9);
            boolean quieto = Math.hypot(f.cx() - prev.cx(), f.cy() - prev.cy()) < 0.05 * r;
            for (int l = 0; l < 2 && quieto; l++) {
                if (Math.hypot(contactoX(f, l) - contactoX(prev, l), contactoY(f, l) - contactoY(prev, l)) > 0.5 * r) {
                    double lo = tp, hi = t, px = contactoX(prev, l), py = contactoY(prev, l), qx = contactoX(f, l), qy = contactoY(f, l);
                    while (hi - lo > 1e-6) {
                        double m = 0.5 * (lo + hi);
                        Zonas.Fr fm = zonas.marco(m);
                        double mx = contactoX(fm, l), my = contactoY(fm, l);
                        if (Math.hypot(mx - px, my - py) < Math.hypot(mx - qx, my - qy)) lo = m; else hi = m;
                    }
                    out.add(new double[] { lo, hi });
                    break;                                   // un salto por intervalo, aunque lo vean los dos lados
                }
            }
            prev = f;
            tp = t;
        }
        return out;
    }

    private static double contactoX(Zonas.Fr f, int l) { return f.cx() + f.ox()[l]; }
    private static double contactoY(Zonas.Fr f, int l) { return f.cy() + f.oy()[l]; }

    /** Rama del lado l: en sentido de t creciente con l = 0 y decreciente con l = 1, con un arco en cada salto. */
    private static List<Tramo> ramas(Zonas zonas, List<double[]> cuspides, int l, int muestras) {
        List<Tramo> out = new ArrayList<>();
        int k = cuspides.size();
        if (l == 0) {
            double ta = 0;
            for (int c = 0; c < k; c++) {
                out.add(rama(zonas, l, ta, cuspides.get(c)[0], muestras));
                out.add(arcoSalto(zonas, cuspides.get(c), l, false));
                ta = cuspides.get(c)[1];
            }
            out.add(rama(zonas, l, ta, 1, muestras));
        } else {
            double ta = 1;
            for (int c = k - 1; c >= 0; c--) {
                out.add(rama(zonas, l, ta, cuspides.get(c)[1], muestras));
                out.add(arcoSalto(zonas, cuspides.get(c), l, true));
                ta = cuspides.get(c)[0];
            }
            out.add(rama(zonas, l, ta, 0, muestras));
        }
        return out;
    }

    private static Tramo rama(Zonas zonas, int l, double ta, double tb, int muestras) {
        int m = Math.max(2, (int) Math.round(muestras * Math.abs(tb - ta)));
        return tramo(m, s -> enContacto(zonas.marco(ta + (tb - ta) * s), l));
    }

    /**
     * Arco del círculo del salto entre el contacto del lado l justo antes (en su marco) y justo después. Se toma el arco
     * más corto; si son iguales (una cúspide: media vuelta) el que pasa por la punta de delante del marco de antes
     * (th = 0). Con reverso se recorre de después a antes.
     */
    private static Tramo arcoSalto(Zonas zonas, double[] salto, int l, boolean reverso) {
        Zonas.Fr fb = zonas.marco(salto[0]), fa = zonas.marco(salto[1]);
        double thB = angulo(fb, l);
        double dx = contactoX(fa, l) - fb.cx(), dy = contactoY(fa, l) - fb.cy();
        double A = dx * fb.tx() + dy * fb.ty(), S = dx * fb.nx() + dy * fb.ny();
        double thA = Math.atan2(S / fb.r(), A / fb.r());
        double d = (thA - thB) % (2 * Math.PI);
        if (d > Math.PI) d -= 2 * Math.PI;
        if (d <= -Math.PI) d += 2 * Math.PI;
        double signo, barrido;
        if (Math.abs(Math.abs(d) - Math.PI) < 1e-3) {      // media vuelta: por delante (l = 0 crece desde -N; l = 1 decrece desde +N)
            signo = l == 0 ? 1 : -1;
            barrido = Math.PI;
        } else {
            signo = Math.signum(d);
            barrido = Math.abs(d);
        }
        return tramo(SEGMENTOS_ARCO, s -> enCirculo(fb, thB + signo * barrido * (reverso ? 1 - s : s)));
    }

    private static Tramo tramo(int n, java.util.function.DoubleFunction<Punto> f) {
        return new Tramo() {
            public Punto en(double s) { return f.apply(s); }
            public int muestras() { return n; }
        };
    }

    /** {inicio, barrido}: de th0 a th1 en sentido creciente, con barrido en (0, 2 pi]. */
    private static double[] barrido(double th0, double th1) {
        double b = (th1 - th0) % (2 * Math.PI);
        if (b < 0) b += 2 * Math.PI;
        return new double[] { th0, b };
    }

    /** Ángulo (en el círculo, desde T hacia N) del punto de contacto del lado l. */
    private static double angulo(Zonas.Fr f, int l) {
        double A = f.ox()[l] * f.tx() + f.oy()[l] * f.ty();
        double S = f.ox()[l] * f.nx() + f.oy()[l] * f.ny();
        return Math.atan2(S / f.r(), A / f.r());
    }

    /** true si el punto es borde de la gota: un punto apenas más hacia fuera queda fuera. */
    private static boolean borde(Geometria geo, Punto p) {
        double n = Math.hypot(p.nx(), p.ny());
        if (n < 1e-300) return false;
        return !geo.mascara().contiene(p.x() + p.eps() * p.nx() / n, p.y() + p.eps() * p.ny() / n);
    }

    /**
     * Reparte la envolvente en tramos de borde ({@link Rol#CONTORNO}) y ocultos ({@link Rol#OCULTA}). Los cambios se
     * localizan por bisección sobre el parámetro del tramo.
     */
    private static void silueta(Geometria geo, Zonas zonas, Ajustes aj, List<Trazo> out) {
        List<List<Punto>> corridas = new ArrayList<>();
        List<Boolean> visibles = new ArrayList<>();
        List<Punto> actual = null;
        boolean vis = false;
        for (Tramo t : tramos(zonas, aj.muestras())) {
            double antes = 0;
            for (int i = 0; i <= t.muestras(); i++) {
                double s = i / (double) t.muestras();
                Punto q = t.en(s);
                boolean v = borde(geo, q);
                if (actual == null) {
                    actual = new ArrayList<>(List.of(q)); vis = v;
                } else if (i == 0) {                     // empalme entre tramos: es el mismo punto
                    if (v != vis) {
                        corridas.add(actual); visibles.add(vis);
                        actual = new ArrayList<>(List.of(q)); vis = v;
                    }
                } else if (v == vis) {
                    actual.add(q);
                } else {
                    double lo = antes, hi = s;
                    for (int k = 0; k < 40; k++) {
                        double m = 0.5 * (lo + hi);
                        if (borde(geo, t.en(m)) == vis) lo = m; else hi = m;
                    }
                    Punto c = t.en(0.5 * (lo + hi));
                    actual.add(c);
                    corridas.add(actual); visibles.add(vis);
                    actual = new ArrayList<>(List.of(c, q)); vis = v;
                }
                antes = s;
            }
        }
        corridas.add(actual); visibles.add(vis);
        boolean cerrado = corridas.size() == 1;
        if (corridas.size() > 1 && visibles.get(0) == visibles.get(visibles.size() - 1)) {   // la silueta es cíclica
            List<Punto> ultima = corridas.remove(corridas.size() - 1);
            visibles.remove(visibles.size() - 1);
            ultima.addAll(corridas.get(0).subList(1, corridas.get(0).size()));
            corridas.set(0, ultima);
        }
        Path2D borde = new Path2D.Double(), oculta = new Path2D.Double();
        boolean hayBorde = false, hayOculta = false;
        for (int r = 0; r < corridas.size(); r++) {
            Path2D p = visibles.get(r) ? borde : oculta;
            List<Punto> c = corridas.get(r);
            for (int i = 0; i < c.size(); i++) {
                if (i == 0) p.moveTo(c.get(i).x(), c.get(i).y()); else p.lineTo(c.get(i).x(), c.get(i).y());
            }
            if (cerrado) p.closePath();
            if (visibles.get(r)) hayBorde = true; else hayOculta = true;
        }
        if (hayOculta && aj.ocultas()) out.add(new Trazo(Rol.OCULTA, oculta));
        if (hayBorde) out.add(new Trazo(Rol.CONTORNO, borde));
    }
}
