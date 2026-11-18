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
 * Los trazos del modo alambre de una geometría: la descripción vectorial de su estructura, sin dibujar nada. Es la ayuda
 * común de {@link PlotterAlambre} y {@link PlotterSvg}; no es una forma de representación por sí misma.
 *
 * {@link #trazos} devuelve una lista de {@link Trazo} (un {@link Rol} y un {@link Shape}): el mismo resultado sirve para
 * pintar en un {@link Graphics2D} ({@link #render}) o para escribir un SVG ({@link PlotterSvg}). El estilo de cada rol se define
 * una sola vez, en {@link Rol}.
 *
 * Solo conoce {@link Geometria}: no depende de Gota, de Composicion ni del render sólido. La envolvente sale de los
 * puntos de contacto de la familia (los que usa {@code Zonas}, visibles en el paquete de forma provisional) y de los
 * arcos de los extremos; no de una malla. De ella, la silueta ({@link Rol#CONTORNO}) son los tramos que
 * {@link Geometria#contiene} confirma como borde real: un punto de la envolvente es borde si un punto apenas más hacia
 * fuera queda fuera de la gota. El resto (la parte de la envolvente que queda dentro, en lazos, cuellos y pliegues) es
 * {@link Rol#OCULTA}, opcional. Donde no hay contacto (casos degenerados) se ve lo que hace hoy el modelo.
 */
final class Trazos {

    private Trazos() {}

    /** Paleta única: tinta (estructura), sutil (radios), suave (auxiliares) y ámbar discreto (los puntos P). */
    private static final Color TINTA = new Color(0x12, 0x23, 0x4A), SUTIL = new Color(0x5A, 0x6B, 0x8C),
        SUAVE = new Color(0x8A, 0x97, 0xB0), AMBAR = new Color(0xDC, 0xA0, 0x28);

    private static Color blanco(int alfa) { return new Color(255, 255, 255, alfa); }

    /** Qué representa un trazo; cada rol lleva su estilo. Los roles HALO_* son el contorno claro que se pinta debajo de otro. */
    public enum Rol {
        /** Silueta: los tramos de la envolvente (ramas y arcos de los extremos) que son borde de la gota. El elemento principal. */
        CONTORNO("contorno", TINTA, 2.2f, null, false),
        /** Parte de la envolvente que queda dentro de la gota (no es silueta). Solo con {@code ocultas}. */
        OCULTA("oculta", SUAVE, 0.8f, new float[] { 3f, 3f }, false),
        /** Secciones interiores de la familia. Discretas. */
        COSTILLA("costilla", new Color(0x9A, 0xAB, 0xC4), 0.8f, null, false),
        /** Primera y última sección: algo más de presencia que las interiores. */
        EXTREMO("extremo", SUTIL, 1.2f, null, false),
        /** Camino: la Bézier cúbica de los centros, a trazos. */
        CAMINO("camino", TINTA, 1.4f, new float[] { 5f, 4f }, false),
        /** Tiradores: segmentos del extremo al punto de control. Solo con un camino curvo. */
        TIRADOR("tirador", SUAVE, 0.8f, new float[] { 5f, 4f }, false),
        /** Puntos de control del camino. */
        PUNTO("punto", SUAVE, 1.0f, null, true),
        /** Los dos centros, extremos del camino. */
        CENTRO("centro", TINTA, 1.0f, null, true),
        /** Arco de cada tapa entre sus dos puntos de contacto, saliendo del centro hacia fuera en la dirección de la tangente. */
        TAPA("tapa", TINTA, 2.2f, null, false),
        /** Segmentos del centro de cada extremo a sus dos puntos de contacto (las costuras de ESPIGA en los extremos). */
        RADIO("radio", SUTIL, 0.9f, null, false),
        /** Puntos de contacto P1..P4 con la envolvente. */
        CONTACTO("contacto", AMBAR, 1.0f, null, true),
        /** Nombres de P1..P4, como contornos de texto (siguen siendo Shape). */
        ETIQUETA("etiqueta", new Color(0x8A, 0x5E, 0x10), 0f, null, true),
        /** Radios de los extremos, a lo largo del camino. Solo con un camino curvo; con uno recto o ignorado va la flecha. */
        SEMIEJE("semieje", new Color(30, 140, 120), 1.6f, null, false),
        /** Flecha de tamaño fijo que sale de cada centro en el sentido de la tangente; sustituye a los semiejes y los tiradores si no hay curva. */
        FLECHA("flecha", SUAVE, 1.3f, null, false),
        /** Nombres de los centros y de los radios (C1, C2, r1, r2). */
        NOMBRE("nombre", TINTA, 0f, null, true),
        /** Solo con diagnóstico: la sección y el punto de tm. */
        DIAGNOSTICO("diagnostico", new Color(220, 150, 50), 1.3f, null, false),
        /** Halos: lo que se pinta debajo de otro rol para que se lea sobre cualquier fondo. */
        HALO_CAMINO("halo-camino", blanco(200), 3.2f, null, false),
        HALO_TAPA("halo-tapa", blanco(210), 4.2f, null, false),
        HALO_RADIO("halo-radio", blanco(170), 2.4f, null, false),
        HALO_FLECHA("halo-flecha", blanco(170), 3.0f, null, false),
        HALO_CONTACTO("halo-contacto", Color.WHITE, 1.0f, null, true),
        HALO_CENTRO("halo-centro", Color.WHITE, 1.0f, null, true);

        /** El rol del halo que va debajo de este, o null si no lo lleva. */
        Rol halo() {
            return switch (this) {
                case CAMINO -> HALO_CAMINO;
                case TAPA -> HALO_TAPA;
                case RADIO -> HALO_RADIO;
                case FLECHA -> HALO_FLECHA;
                default -> null;
            };
        }

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
     * @param contorno    dibuja la silueta
     * @param extremos    dibuja las secciones de los dos extremos (círculos completos)
     * @param tiradores   dibuja los tiradores y los puntos de control, salvo que el camino sea recto o se ignore (forma rígida)
     * @param tapas       dibuja el arco de cada tapa entre sus dos puntos de contacto
     */
    public record Ajustes(int secciones, int muestras, boolean diagnostico, boolean contactos, boolean radios,
                          boolean etiquetas, boolean ocultas, boolean contorno, boolean extremos, boolean tiradores,
                          boolean tapas) {
        /** Silueta y extremos, con los nombres de los centros y sin diagnóstico, contactos, radios, tapas ni envolvente oculta. */
        public static final Ajustes POR_DEFECTO = new Ajustes(2, 160, false, false, false, true, false, true, true, true, false);
        /**
         * El esquema estructural, pensado para superponerlo a otro dibujo: los dos centros, el camino a trazos, los arcos de
         * las tapas, y los cuatro puntos P con sus radios. Sin contorno, ni secciones de los extremos, ni tiradores, ni nombres.
         */
        public static final Ajustes ESQUEMA = new Ajustes(2, 160, false, true, false, false, false, false, false, false, true);
        public Ajustes {
            if (secciones < 2 || muestras < 2) throw new IllegalArgumentException("secciones >= 2 y muestras >= 2");
        }
        public Ajustes conSecciones(int n) { return new Ajustes(n, muestras, diagnostico, contactos, radios, etiquetas, ocultas, contorno, extremos, tiradores, tapas); }
        public Ajustes conMuestras(int n) { return new Ajustes(secciones, n, diagnostico, contactos, radios, etiquetas, ocultas, contorno, extremos, tiradores, tapas); }
        public Ajustes conDiagnostico(boolean d) { return new Ajustes(secciones, muestras, d, contactos, radios, etiquetas, ocultas, contorno, extremos, tiradores, tapas); }
        public Ajustes conContactos(boolean c) { return new Ajustes(secciones, muestras, diagnostico, c, radios, etiquetas, ocultas, contorno, extremos, tiradores, tapas); }
        public Ajustes conRadios(boolean r) { return new Ajustes(secciones, muestras, diagnostico, contactos, r, etiquetas, ocultas, contorno, extremos, tiradores, tapas); }
        public Ajustes conEtiquetas(boolean e) { return new Ajustes(secciones, muestras, diagnostico, contactos, radios, e, ocultas, contorno, extremos, tiradores, tapas); }
        public Ajustes conOcultas(boolean o) { return new Ajustes(secciones, muestras, diagnostico, contactos, radios, etiquetas, o, contorno, extremos, tiradores, tapas); }
        public Ajustes conContorno(boolean c) { return new Ajustes(secciones, muestras, diagnostico, contactos, radios, etiquetas, ocultas, c, extremos, tiradores, tapas); }
        public Ajustes conExtremos(boolean x) { return new Ajustes(secciones, muestras, diagnostico, contactos, radios, etiquetas, ocultas, contorno, x, tiradores, tapas); }
        public Ajustes conTiradores(boolean t) { return new Ajustes(secciones, muestras, diagnostico, contactos, radios, etiquetas, ocultas, contorno, extremos, t, tapas); }
        public Ajustes conTapas(boolean t) { return new Ajustes(secciones, muestras, diagnostico, contactos, radios, etiquetas, ocultas, contorno, extremos, tiradores, t); }
    }

    private static final int SEGMENTOS_ARCO = 64;

    public static List<Trazo> trazos(Geometria geo) { return trazos(geo, Ajustes.POR_DEFECTO); }

    /** Los trazos del alambre, del fondo al frente: secciones, camino, contorno, contactos, puntos. */
    public static List<Trazo> trazos(Geometria geo, Ajustes aj) {
        List<Trazo> out = new ArrayList<>();
        boolean recto = rectoOIgnorado(geo);          // sin curva que enseñar: nada de tiradores ni semiejes, sino flechas
        int n = aj.secciones();
        for (int i = 1; i < n - 1; i++) out.add(new Trazo(Rol.COSTILLA, seccion(geo, i / (double) (n - 1))));
        if (aj.extremos()) {
            out.add(new Trazo(Rol.EXTREMO, seccion(geo, 0)));
            out.add(new Trazo(Rol.EXTREMO, seccion(geo, 1)));
        }
        if (aj.diagnostico()) out.add(new Trazo(Rol.DIAGNOSTICO, seccion(geo, geo.tm())));

        double x0 = geo.cx(0), y0 = geo.cy(0), x3 = geo.cx(1), y3 = geo.cy(1);
        double x1 = x0 + geo.dcx(0) / 3, y1 = y0 + geo.dcy(0) / 3;
        double x2 = x3 - geo.dcx(1) / 3, y2 = y3 - geo.dcy(1) / 3;
        boolean tiradores = aj.tiradores() && !recto;
        if (tiradores) {
            out.add(new Trazo(Rol.TIRADOR, new Line2D.Double(x0, y0, x1, y1)));
            out.add(new Trazo(Rol.TIRADOR, new Line2D.Double(x3, y3, x2, y2)));
        }
        Path2D camino = new Path2D.Double();
        camino.moveTo(x0, y0);
        camino.curveTo(x1, y1, x2, y2, x3, y3);
        conHalo(out, Rol.CAMINO, camino);

        var zonas = new Zonas(geo, Costura.ORTOGONAL, Tira.POR_DEFECTO.cortes(geo));
        if (aj.contorno()) silueta(geo, zonas, aj, out);
        if (aj.tapas()) tapas(geo, zonas, out);
        if (aj.radios()) {
            if (recto) flechas(zonas, out); else radios(zonas, aj.etiquetas(), out);
        }
        if (aj.contactos()) contactos(zonas, aj.etiquetas(), out);
        if (aj.etiquetas()) nombresCentros(zonas, out);

        if (tiradores) {
            out.add(new Trazo(Rol.PUNTO, punto(x1, y1, 3)));
            out.add(new Trazo(Rol.PUNTO, punto(x2, y2, 3)));
        }
        centro(out, x0, y0);
        centro(out, x3, y3);
        if (aj.diagnostico()) out.add(new Trazo(Rol.DIAGNOSTICO, punto(geo.cx(geo.tm()), geo.cy(geo.tm()), 3)));
        return out;
    }

    /** El trazo, con su halo debajo si el rol lo lleva. */
    private static void conHalo(List<Trazo> out, Rol rol, Shape forma) {
        if (rol.halo() != null) out.add(new Trazo(rol.halo(), forma));
        out.add(new Trazo(rol, forma));
    }

    /** Un centro: disco sobre un halo blanco. */
    private static void centro(List<Trazo> out, double x, double y) {
        out.add(new Trazo(Rol.HALO_CENTRO, punto(x, y, 5.5)));
        out.add(new Trazo(Rol.CENTRO, punto(x, y, 4.0)));
    }

    /**
     * true si el camino no tiene curva que enseñar: la forma es rígida (el camino se descarta y la espina es la recta de los
     * centros) o los cuatro puntos de control están alineados (camino recto).
     */
    static boolean rectoOIgnorado(Geometria geo) {
        if (geo.arcos() != null) return true;
        double x0 = geo.cx(0), y0 = geo.cy(0), ux = geo.cx(1) - x0, uy = geo.cy(1) - y0, L = Math.hypot(ux, uy);
        double[] px = { geo.dcx(0) / 3, geo.cx(1) - geo.dcx(1) / 3 - x0 }, py = { geo.dcy(0) / 3, geo.cy(1) - geo.dcy(1) / 3 - y0 };
        if (L < 1e-9) return Math.hypot(px[0], py[0]) + Math.hypot(px[1], py[1]) < 1e-9;
        for (int i = 0; i < 2; i++) if (Math.abs(px[i] * uy - py[i] * ux) / L > 1e-6 * L) return false;
        return true;
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
                conHalo(out, Rol.RADIO, new Line2D.Double(f.cx(), f.cy(), px, py));
                out.add(new Trazo(Rol.HALO_CONTACTO, punto(px, py, 3.6)));
                out.add(new Trazo(Rol.CONTACTO, punto(px, py, 2.6)));
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

    private static final int MUESTRAS_TAPA = 90;
    /** Flecha de tamaño fijo (píxeles de imagen): separación del centro, largo y cabeza. */
    private static final double FLECHA_DESDE = 8, FLECHA_LARGO = 16, FLECHA_CABEZA = 5;

    /**
     * Flecha de tamaño fijo que sale de cada centro en el sentido de la tangente (contra ella en el extremo inicial, a favor en
     * el final): sustituye a los semiejes y los tiradores cuando el camino es recto o se ignora.
     */
    private static void flechas(Zonas zonas, List<Trazo> out) {
        for (int e = 0; e < 2; e++) {
            Zonas.Fr f = zonas.marco(e);
            double[] m = ejes(f);
            double sg = e == 0 ? -1 : 1, ux = sg * m[0], uy = sg * m[1];
            double ax = f.cx() + FLECHA_DESDE * ux, ay = f.cy() + FLECHA_DESDE * uy;
            double bx = f.cx() + (FLECHA_DESDE + FLECHA_LARGO) * ux, by = f.cy() + (FLECHA_DESDE + FLECHA_LARGO) * uy;
            double c = Math.cos(Math.toRadians(28)), sn = Math.sin(Math.toRadians(28));
            Path2D flecha = new Path2D.Double();
            flecha.moveTo(ax, ay);
            flecha.lineTo(bx, by);
            flecha.moveTo(bx - FLECHA_CABEZA * (ux * c - uy * sn), by - FLECHA_CABEZA * (uy * c + ux * sn));
            flecha.lineTo(bx, by);
            flecha.lineTo(bx - FLECHA_CABEZA * (ux * c + uy * sn), by - FLECHA_CABEZA * (uy * c - ux * sn));
            conHalo(out, Rol.FLECHA, flecha);
        }
    }

    /** Tangente del camino en el extremo e (0: inicio, 1: final), o null si el camino está parado. */
    private static double[] tangente(Geometria geo, int e) {
        double x = geo.dcx(e), y = geo.dcy(e), n = Math.hypot(x, y);
        return n > 1e-9 ? new double[] { x / n, y / n } : null;
    }

    /** Dirección en la que sale la tapa e: contra la tangente del camino en la inicial, a favor en la final; null si está parado. */
    private static double[] haciaFuera(Geometria geo, int e) {
        double[] t = tangente(geo, e);
        return t == null ? null : new double[] { (e == 0 ? -1 : 1) * t[0], (e == 0 ? -1 : 1) * t[1] };
    }

    /**
     * {ángulo inicial, barrido} del arco de la tapa de centro {@code c} entre sus contactos {@code pa} y {@code pb}: el que
     * sale del centro hacia fuera, en la dirección de la tangente del camino ({@code dir}). Sin tangente (camino parado) se
     * toma el lado alejado del otro centro.
     */
    private static double[] barridoTapa(double[] c, double[] otro, double r, double[] pa, double[] pb, double[] dir) {
        double a = Math.atan2(pa[1] - c[1], pa[0] - c[0]), b = Math.atan2(pb[1] - c[1], pb[0] - c[0]);
        double positivo = ((b - a) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
        double mx = Math.cos(a + positivo / 2), my = Math.sin(a + positivo / 2);   // dirección del punto medio por el barrido positivo
        boolean porPositivo = dir != null
            ? mx * dir[0] + my * dir[1] >= -mx * dir[0] - my * dir[1]
            : Math.hypot(r * mx + c[0] - otro[0], r * my + c[1] - otro[1])
                >= Math.hypot(c[0] - r * mx - otro[0], c[1] - r * my - otro[1]);
        return new double[] { a, porPositivo ? positivo : -(2 * Math.PI - positivo) };
    }

    /** {ángulo inicial, barrido} del arco de la tapa e, o null si su radio es 0. */
    private static double[] arcoTapa(Geometria geo, Zonas zonas, int e) {
        Zonas.Fr f = zonas.marco(e), o = zonas.marco(1 - e);
        if (!(f.r() > 1e-9)) return null;
        double[] c = { f.cx(), f.cy() }, otro = { o.cx(), o.cy() };
        double[] pa = { f.cx() + f.ox()[0], f.cy() + f.oy()[0] }, pb = { f.cx() + f.ox()[1], f.cy() + f.oy()[1] };
        return barridoTapa(c, otro, f.r(), pa, pb, haciaFuera(geo, e));
    }

    /** Los arcos de las tapas entre sus dos contactos, saliendo de cada centro hacia fuera según la tangente. */
    private static void tapas(Geometria geo, Zonas zonas, List<Trazo> out) {
        for (int e = 0; e < 2; e++) {
            double[] bs = arcoTapa(geo, zonas, e);
            if (bs == null) continue;
            Zonas.Fr f = zonas.marco(e);
            Path2D arco = new Path2D.Double();
            for (int i = 0; i <= MUESTRAS_TAPA; i++) {
                double th = bs[0] + bs[1] * i / MUESTRAS_TAPA;
                double x = f.cx() + f.r() * Math.cos(th), y = f.cy() + f.r() * Math.sin(th);
                if (i == 0) arco.moveTo(x, y); else arco.lineTo(x, y);
            }
            conHalo(out, Rol.TAPA, arco);
        }
    }

    /** El punto medio del arco de la tapa e (0 o 1); null si el radio es 0. Para las pruebas. */
    static double[] mitadTapa(Geometria geo, int e) {
        Zonas zonas = new Zonas(geo, Costura.ORTOGONAL, Tira.POR_DEFECTO.cortes(geo));
        double[] bs = arcoTapa(geo, zonas, e);
        if (bs == null) return null;
        Zonas.Fr f = zonas.marco(e);
        return new double[] { f.cx() + f.r() * Math.cos(bs[0] + bs[1] / 2), f.cy() + f.r() * Math.sin(bs[0] + bs[1] / 2) };
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
     * localizan por bisección sobre el parámetro del tramo. Si la geometría tiene silueta rígida ({@link Geometria#arcos}), el
     * contorno son sus cuatro arcos y no hay envolvente oculta que mostrar.
     */
    private static void silueta(Geometria geo, Zonas zonas, Ajustes aj, List<Trazo> out) {
        List<Geometria.Arco> arcos = geo.arcos();
        if (arcos != null) {                      // forma rígida: el contorno es la silueta que la Geometria da, no la envolvente
            Path2D contorno = new Path2D.Double();
            for (Geometria.Arco a : arcos) contorno.append(a.forma(), contorno.getCurrentPoint() != null);
            contorno.closePath();
            out.add(new Trazo(Rol.CONTORNO, contorno));
            return;
        }
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
