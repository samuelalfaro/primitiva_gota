import pintado.Plotter;
import geometria.Grupo;
import geometria.Geometria;
import geometria.composicion.Grafo;
import coordenadas.Solape;
import coordenadas.Opciones;
import color.Mezcla;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Primer follaje del {@link ArbolParametrico3D}: las ramas del nivel máximo llevan hojas a lo largo (la geometría de
 * dos gotas del limbo de la {@link Hoja}, sin venas: a escala de follaje son sub-pixel), colocadas con la misma
 * proyección que el tronco, y la profundidad de cada base se usa como altura.
 * <p>
 * El panel compara las dos maneras de juntarlo con la arquitectura nueva. En un {@link Grupo} la altura es de cada
 * geometría: una hoja queda por encima o por debajo del tronco entero, aunque la rama que la cruza esté delante. En el
 * mismo {@link Grafo}, la altura por arista (la de siempre del ferry 3D) resuelve el solape por píxel. Con la vista
 * desde el lado opuesto (yaw + 180) se comprueba si el orden over/under es el mismo por detrás.
 * <p>
 * Colores: cada hoja parte de la paleta de la Hoja con un tono al azar (más oscura), y se mezcla hacia el fondo según
 * su profundidad (niebla por hoja, no por nodo).
 * Escribe follaje_arbol.png: por filas, frente y yaw + 180; por columnas, árbol desnudo |
 * follaje en Grupo | follaje en el mismo Grafo.
 */
public final class Follaje {
    static final int W = 800, H = 700, FONDO = 0xFFF5F0E6;

    /** Coordenadas locales de la hoja (las de {@link Hoja}): base (226, 655), punta (318, 70). */
    static final double HX = 226, HY = 655, HEJE = Math.atan2(70 - 655, 318 - 226);
    static final double[] MEDIO = { 262, 410 }, PUNTA = { 318, 70 };
    static final double LARGO = Math.hypot(318 - 226, 70 - 655);
    static final int OSCURO = 0xFF2F4D1F;

    /** Proyección de un punto del mundo cualquiera (no dado de alta como nodo del modelo). */
    interface Puntual { ArbolParametrico3D.Proyectado de(double x, double y, double z); }

    /**
     * Cámara para {@link ArbolParametrico3D}, la de {@link ArbolVistas} (yaw, pitch y perspectiva) pero que también
     * proyecta puntos libres {@link Puntual}: las hojas necesitan puntos que no son nodos del árbol (su base y su
     * punta calculadas en el mundo). Misma convención y mismos números que la de ArbolVistas.
     */
    record Camara(ArbolParametrico3D.Proyeccion nodo, Puntual libre) {}

    static Camara camara(ArbolParametrico3D.Modelo m, double yawGrados, double pitchGrados, double distancia) {
        final double cy = Math.cos(Math.toRadians(yawGrados)), sy = Math.sin(Math.toRadians(yawGrados));
        final double cp = Math.cos(Math.toRadians(pitchGrados)), sp = Math.sin(Math.toRadians(pitchGrados));
        double mx = 0, my = 0, mz = 0;
        for (var p : m.puntos()) { mx += p.x(); my += p.y(); mz += p.z(); }
        final double ox = mx / m.puntos().size(), oy = my / m.puntos().size(), oz = mz / m.puntos().size();
        double minX = 1e18, maxX = -1e18, minY = 1e18, maxY = -1e18;
        for (var p : m.puntos()) {
            double[] c = ArbolVistas.gira(p.x() - ox, p.y() - oy, p.z() - oz, cy, sy, cp, sp, distancia);
            minX = Math.min(minX, c[0] * c[3]); maxX = Math.max(maxX, c[0] * c[3]);
            minY = Math.min(minY, c[1] * c[3]); maxY = Math.max(maxY, c[1] * c[3]);
        }
        final double s = Math.min((W - 120) / (maxX - minX), (H - 120) / (maxY - minY));
        final double offX = W / 2.0 - s * (minX + maxX) / 2, offY = H / 2.0 - s * (minY + maxY) / 2;
        Puntual libre = (x, y, z) -> {
            double[] c = ArbolVistas.gira(x - ox, y - oy, z - oz, cy, sy, cp, sp, distancia);
            return new ArbolParametrico3D.Proyectado(offX + s * c[0] * c[3], offY + s * c[1] * c[3], -c[2], s * c[3]);
        };
        return new Camara(p -> libre.de(p.x(), p.y(), p.z()), libre);
    }

    /**
     * Sitúa una hoja: lleva su base a (x, y), gira su eje hasta el ángulo dado (radianes de pantalla) y escala. Es la
     * semejanza de {@link Hoja.Tr}.
     */
    record Pos(double x, double y, double ang, double esc) {
        double X(double px, double py) {
            double d = ang - HEJE, c = Math.cos(d), s = Math.sin(d);
            return x + esc * ((px - HX) * c - (py - HY) * s);
        }
        double Y(double px, double py) {
            double d = ang - HEJE, c = Math.cos(d), s = Math.sin(d);
            return y + esc * ((px - HX) * s + (py - HY) * c);
        }
    }

    /** Una hoja ya situada: geometría local, paleta (color por color, ya mezclada) y altura (profundidad de su base). */
    record HojaPos(Pos t, int[] pal, double altura) {}

    /** Colores de la hoja de referencia ({@link Hoja}); orden: base centro, base sector, medio centro, medio contra, medio normal, punta centro, punta sector. */
    static int[] paletaBase() {
        return new int[] { 0xFFC5E1A5, 0xFF4E8F34, 0xFFE6EE9C, 0xFF1B5E20, 0xFF9CCC65, 0xFFDCEDC8, 0xFF5E9B38 };
    }

    static int[] mezclaTodos(int[] pal, int hacia, double t) {
        for (int i = 0; i < pal.length; i++) pal[i] = Mezcla.RGB.mezclar(pal[i], hacia, t);
        return pal;
    }

    /** Añade la hoja al grafo: limbo de dos gotas con la marca del nervio por el ajuste lateral (como el limbo de Hoja). */
    static void hoja(Grafo c, HojaPos h) {
        Pos t = h.t();
        int[] pal = h.pal();
        Grafo.Nodo base = c.nodo(t.X(HX, HY), t.Y(HX, HY), 6 * t.esc()).centro(pal[0]).sectores(pal[1]);
        Grafo.Nodo medio = c.nodo(t.X(MEDIO[0], MEDIO[1]), t.Y(MEDIO[0], MEDIO[1]), 112 * t.esc())
                .centro(pal[2]).lados(pal[3], pal[4]).ajusteLateral(0.3);
        Grafo.Nodo punta = c.nodo(t.X(PUNTA[0], PUNTA[1]), t.Y(PUNTA[0], PUNTA[1]), 5 * t.esc())
                .centro(pal[5]).sectores(pal[6]);
        c.arista(base, medio).altura(v -> h.altura());
        c.arista(medio, punta).altura(v -> h.altura());
    }

    /**
     * Coloca hojas a lo largo de las ramas del nivel máximo (no solo en la punta): la base va a una altura t al azar
     * del segmento, y la dirección de la hoja se decide en 3D — sale del eje de la rama con un ángulo al azar (entre
     * 30° y 80°) alrededor de él, y se sesga hacia abajo (la hoja cuelga), para luego proyectarla: así la misma hoja
     * le pega igual vista de frente y desde detrás. La escala sale del segmento proyectado. La profundidad
     * normalizada de la base da el tono (más profunda, más oscura) y la niebla (mezcla hacia el fondo, hasta niebla).
     */
    static List<HojaPos> follaje(ArbolParametrico3D.Modelo m, int niveles, Camara cam,
                                 int porPunta, double niebla, Random azar) {
        double zMin = Double.POSITIVE_INFINITY, zMax = Double.NEGATIVE_INFINITY;
        for (var p : m.puntos()) {
            double z = cam.nodo().aplicar(p).profundidad();
            zMin = Math.min(zMin, z); zMax = Math.max(zMax, z);
        }
        List<HojaPos> hojas = new ArrayList<>();
        for (var r : m.ramas()) {
            if (r.nivel() != niveles) continue;
            var o = r.origen(); var f = r.extremo();
            double[] d = normaliza(f.x() - o.x(), f.y() - o.y(), f.z() - o.z());
            var qo = cam.libre().de(o.x(), o.y(), o.z());
            var qf = cam.libre().de(f.x(), f.y(), f.z());
            double segProy = Math.hypot(qf.x() - qo.x(), qf.y() - qo.y());
            for (int i = 0; i < porPunta; i++) {
                double t = i == 0 ? 1 : 0.4 + azar.nextDouble() * 0.6;   // la primera en la punta, el resto a lo largo
                double[] eje = normaliza(azar.nextDouble() * 2 - 1, azar.nextDouble() * 2 - 1, azar.nextDouble() * 2 - 1);
                double[] lat = normaliza(eje[0] - d[0] * producto(eje, d), eje[1] - d[1] * producto(eje, d), eje[2] - d[2] * producto(eje, d));
                double abre = Math.toRadians(30 + azar.nextDouble() * 50);
                double[] dir = normaliza(d[0] * Math.cos(abre) + lat[0] * Math.sin(abre),
                        d[1] * Math.cos(abre) + lat[1] * Math.sin(abre), d[2] * Math.cos(abre) + lat[2] * Math.sin(abre));
                dir = normaliza(dir[0] + 0.3, dir[1] + 0.3, dir[2]);     // cuelga: sesgo hacia y + (abajo en el mundo)
                double baseX = o.x() + (f.x() - o.x()) * t, baseY = o.y() + (f.y() - o.y()) * t, baseZ = o.z() + (f.z() - o.z()) * t;
                var q0 = cam.libre().de(baseX, baseY, baseZ);
                var q1 = cam.libre().de(baseX + dir[0] * 10, baseY + dir[1] * 10, baseZ + dir[2] * 10);
                double hA = Math.atan2(q1.y() - q0.y(), q1.x() - q0.x());
                double esc = segProy * (0.95 + azar.nextDouble() * 0.35) / LARGO;
                double prof = zMax > zMin ? (zMax - q0.profundidad()) / (zMax - zMin) : 0;
                int[] pal = mezclaTodos(paletaBase(), OSCURO, 0.15 + 0.55 * azar.nextDouble());
                mezclaTodos(pal, FONDO, niebla * prof);
                hojas.add(new HojaPos(new Pos(q0.x(), q0.y(), hA, esc), pal, q0.profundidad()));
            }
        }
        return hojas;
    }

    static double producto(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    static double[] normaliza(double x, double y, double z) {
        double n = Math.sqrt(x * x + y * y + z * z);
        if (!(n > 1e-9)) return new double[] { 0, 1, 0 };
        return new double[] { x / n, y / n, z / n };
    }

    /** El tronco proyectado, con la profundidad como altura por arista, relajado y alisado (como ArbolVistas). */
    static Grafo tronco(ArbolParametrico3D.Modelo m, ArbolParametrico3D.Proyeccion cam) {
        Grafo c = m.proyectar(cam);
        c.relajar();
        c.alisar();
        return c;
    }

    static BufferedImage pinta(Geometria g) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        Plotter.raster(im).plot(g, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        return im;
    }

    public static void main(String[] args) throws Exception {
        ArbolParametrico3D.Parametros p = new ArbolParametrico3D.Parametros();
        p.ramas = Integer.getInteger("ramas", 3);            // 3^4 = 81 puntas; el árbol clásico: -Dramas=2 -Dniveles=5
        p.niveles = Integer.getInteger("niveles", 4);
        int porPunta = Integer.getInteger("hojas", 3);
        double yaw = args.length > 0 ? Double.parseDouble(args[0]) : 35, pitch = args.length > 1 ? Double.parseDouble(args[1]) : 20;
        ArbolParametrico3D.Modelo m = ArbolParametrico3D.crear(p);
        BufferedImage panel = new BufferedImage(3 * W, 2 * H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = panel.createGraphics();
        String[] cols = { "desnudo", "Grupo (altura por hoja)", "mismo Grafo (altura por píxel)" };
        for (int fila = 0; fila < 2; fila++) {
            var cam = camara(m, yaw + 180 * fila, pitch, 900);
            List<HojaPos> hojas = follaje(m, p.niveles, cam, porPunta, 0.35, new Random(p.semilla));
            for (int col = 0; col < 3; col++) {
                Geometria geo;
                if (col == 0) {
                    geo = tronco(m, cam.nodo());
                } else if (col == 1) {
                    Grupo gr = new Grupo();
                    for (HojaPos h : hojas) {
                        Grafo hojaAlone = new Grafo().mezcla(Mezcla.RGB);
                        hoja(hojaAlone, h);
                        gr.anade(hojaAlone.altura(h.altura()));
                    }
                    gr.anade(tronco(m, cam.nodo()));
                    geo = gr;
                } else {
                    Grafo c = tronco(m, cam.nodo());
                    for (HojaPos h : hojas) hoja(c, h);
                    geo = c;
                }
                g.drawImage(pinta(geo), col * W, fila * H, null);
                System.out.printf("fila %d (yaw %3.0f)  %-32s %d hojas%n", fila, yaw + 180 * fila, cols[col], col == 0 ? 0 : hojas.size());
            }
        }
        g.dispose();
        ImageIO.write(panel, "png", new File("follaje_arbol.png"));
        System.out.println("follaje_arbol.png (filas: yaw y yaw + 180; columnas: desnudo | Grupo | mismo Grafo)");
    }
}
