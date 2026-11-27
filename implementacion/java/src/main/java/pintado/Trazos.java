package pintado;

import coordenadas.Contorno;
import coordenadas.Costura;
import coordenadas.Tira;
import coordenadas.Zonas;
import forma.Camino;
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

/**
 * Los trazos del modo alambre de una geometría: la descripción vectorial de su estructura, sin dibujar nada. Es la ayuda
 * común de {@link PlotterAlambre} y {@link PlotterSvg}; no es una forma de representación por sí misma.
 *
 * {@link #trazos} devuelve una lista de {@link Trazo} (un {@link Rol} y un {@link Shape}): el mismo resultado sirve para
 * pintar en un {@link Graphics2D} ({@link #render}) o para escribir un SVG ({@link PlotterSvg}). El estilo de cada rol se define
 * una sola vez, en {@link Rol}.
 *
 * Solo conoce {@link Camino}: no depende de Gota, de Grafo ni del render sólido. La envolvente sale de los
 * puntos de contacto de la familia (los que usa {@code Zonas}, visibles en el paquete de forma provisional) y de los
 * arcos de los extremos; no de una malla. De ella, la silueta ({@link Rol#CONTORNO}) son los tramos que
 * {@link forma.Mascara#contiene} confirma como borde real: un punto de la envolvente es borde si un punto apenas más hacia
 * fuera queda fuera de la gota. El resto (la parte de la envolvente que queda dentro, en lazos, cuellos y pliegues) es
 * {@link Rol#OCULTA}, opcional. Donde no hay contacto (casos degenerados) se ve lo que hace hoy el modelo.
 */
public final class Trazos {

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
        /** Solo con diagnóstico: la sección y el punto en t = 1/2. */
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

    /**
     * Una forma vectorial y su papel en el alambre. Las etiquetas son trazos de texto: llevan la cadena y su centro, y su
     * {@code forma()} (el contorno de los glifos) solo se calcula al pedirla, que es lo único que necesita una fuente del
     * sistema. Así un exportador (SVG) escribe el texto sin fuentes, y pintar sobre una imagen sí las exige.
     */
    public record Trazo(Rol rol, Shape forma, String texto, double x, double y) {
        public Trazo(Rol rol, Shape forma) { this(rol, forma, null, 0, 0); }

        /** Un trazo de texto, centrado en (x, y). */
        public static Trazo texto(Rol rol, String texto, double x, double y) { return new Trazo(rol, null, texto, x, y); }

        /** La forma; en un trazo de texto, el contorno de sus glifos (falla si el entorno no tiene fuentes del sistema). */
        @Override public Shape forma() { return forma != null ? forma : contornoDelTexto(texto, x, y); }
    }

    /**
     * Qué se dibuja y con cuánto detalle.
     *
     * @param secciones   número de secciones de la familia, extremos incluidos, repartidas uniformemente en t (mínimo 2:
     *                    solo los extremos)
     * @param muestras    puntos por rama del contorno
     * @param diagnostico añade la sección y el punto en t = 1/2
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

    public static List<Trazo> trazos(Camino geo) { return trazos(geo, Ajustes.POR_DEFECTO); }

    /** Los trazos del alambre, del fondo al frente: secciones, camino, contorno, contactos, puntos. */
    public static List<Trazo> trazos(Camino geo, Ajustes aj) {
        List<Trazo> out = new ArrayList<>();
        boolean recto = rectoOIgnorado(geo);          // sin curva que enseñar: nada de tiradores ni semiejes, sino flechas
        int n = aj.secciones();
        for (int i = 1; i < n - 1; i++) out.add(new Trazo(Rol.COSTILLA, seccion(geo, i / (double) (n - 1))));
        if (aj.extremos()) {
            out.add(new Trazo(Rol.EXTREMO, seccion(geo, 0)));
            out.add(new Trazo(Rol.EXTREMO, seccion(geo, 1)));
        }
        if (aj.diagnostico()) out.add(new Trazo(Rol.DIAGNOSTICO, seccion(geo, 0.5)));

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
        if (aj.diagnostico()) out.add(new Trazo(Rol.DIAGNOSTICO, punto(geo.cx(0.5), geo.cy(0.5), 3)));
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
    static boolean rectoOIgnorado(Camino geo) {
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
                if (etiquetas) out.add(Trazo.texto(Rol.ETIQUETA, nombres[2 * e + l], px + 11 * ux, py + 11 * uy));
            }
        }
    }

    /** Ejes (T, N) del marco: {tx, ty, nx, ny}. Con el camino parado, Camino ya da T = eje x y N = (0, -1) (y hacia arriba). */
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
            if (etiquetas) out.add(Trazo.texto(Rol.NOMBRE, "r" + (e + 1),
                f.cx() + sg * (r + 11) * m[0], f.cy() + sg * (r + 11) * m[1]));
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
    private static double[] tangente(Camino geo, int e) {
        double x = geo.dcx(e), y = geo.dcy(e), n = Math.hypot(x, y);
        return n > 1e-9 ? new double[] { x / n, y / n } : null;
    }

    /** Dirección en la que sale la tapa e: contra la tangente del camino en la inicial, a favor en la final; null si está parado. */
    private static double[] haciaFuera(Camino geo, int e) {
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
    private static double[] arcoTapa(Camino geo, Zonas zonas, int e) {
        Zonas.Fr f = zonas.marco(e), o = zonas.marco(1 - e);
        if (!(f.r() > 1e-9)) return null;
        double[] c = { f.cx(), f.cy() }, otro = { o.cx(), o.cy() };
        double[] pa = { f.cx() + f.ox()[0], f.cy() + f.oy()[0] }, pb = { f.cx() + f.ox()[1], f.cy() + f.oy()[1] };
        return barridoTapa(c, otro, f.r(), pa, pb, haciaFuera(geo, e));
    }

    /** Los arcos de las tapas entre sus dos contactos, saliendo de cada centro hacia fuera según la tangente. */
    private static void tapas(Camino geo, Zonas zonas, List<Trazo> out) {
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
    static double[] mitadTapa(Camino geo, int e) {
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
            out.add(Trazo.texto(Rol.NOMBRE, "C" + (e + 1),
                f.cx() + 11 * (sg * 0.7 * m[0] - 0.7 * m[2]), f.cy() + 11 * (sg * 0.7 * m[1] - 0.7 * m[3])));
        }
    }

    /** El texto como contorno vectorial, centrado en (x, y). Necesita fuentes del sistema (y libfreetype/fontconfig). */
    private static Shape contornoDelTexto(String texto, double x, double y) {
        Shape s;
        try {
            s = FUENTE.createGlyphVector(new FontRenderContext(null, true, true), texto).getOutline();
        } catch (LinkageError | InternalError | RuntimeException e) {
            throw new IllegalStateException("este entorno no tiene fuentes del sistema para escribir «" + texto
                + "»: usa Trazos.Ajustes.conEtiquetas(false) o exporta a SVG (que escribe el texto sin fuentes)", e);
        }
        Rectangle2D b = s.getBounds2D();
        return AffineTransform.getTranslateInstance(x - b.getCenterX(), y - b.getCenterY()).createTransformedShape(s);
    }

    /** Pinta los trazos en el sistema de coordenadas del Graphics2D (las coordenadas de Camino son de imagen). */
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

    public static void render(Graphics2D g, Camino geo) { render(g, trazos(geo)); }

    // ---------- Piezas ----------

    private static Shape punto(double x, double y, double r) {
        return new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
    }

    /** Sección de la familia en t: el círculo de centro c(t) y radio r(t). */
    private static Shape seccion(Camino geo, double t) {
        double r = geo.rad(t);
        return new Ellipse2D.Double(geo.cx(t) - r, geo.cy(t) - r, 2 * r, 2 * r);
    }

    /** La silueta ({@link Rol#CONTORNO}) y, si se pide, la envolvente oculta, a partir de {@link Contorno}. */
    private static void silueta(Camino geo, Zonas zonas, Ajustes aj, List<Trazo> out) {
        Contorno c = Contorno.de(geo, zonas, aj.muestras());
        if (c.oculta() != null && aj.ocultas()) out.add(new Trazo(Rol.OCULTA, c.oculta()));
        if (c.borde() != null) out.add(new Trazo(Rol.CONTORNO, c.borde()));
    }
}
