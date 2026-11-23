import pintado.Plotter;
import geometria.composicion.Grafo;
import coordenadas.Solape;
import color.Mezcla;
import coordenadas.Opciones;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Cámaras para el {@link ArbolParametrico3D}: yaw (giro alrededor de la vertical), pitch (inclinación) y, opcionalmente,
 * perspectiva. La profundidad de cámara (mayor = más cerca del espectador) pasa a ser la altura de cada arista, de modo
 * que las ramas se superponen con el orden correcto; después se pueden aplicar relajar() y alisar().
 * Escribe arbol_vistas.png: por filas, vista ortográfica y con perspectiva; por columnas, sin altura | con altura |
 * con altura + relajar() | con altura + relajar() + alisar().
 */
public final class ArbolVistas {
    static final int W = 800, H = 700, FONDO = 0xFFF5F0E6;

    /**
     * Cámara ajustada al modelo. Convención de ArbolParametrico3D tal como se genera: x a la derecha, y hacia abajo en
     * pantalla (las ramas crecen hacia y negativa), z hacia el fondo (sin giro). Yaw y pitch son rotaciones propias
     * (derecha, y -> abajo, z -> fondo); la profundidad devuelta es hacia el espectador.
     */
    static ArbolParametrico3D.Proyeccion camara(ArbolParametrico3D.Modelo m, double yawGrados, double pitchGrados, double distancia) {
        final double cy = Math.cos(Math.toRadians(yawGrados)), sy = Math.sin(Math.toRadians(yawGrados));
        final double cp = Math.cos(Math.toRadians(pitchGrados)), sp = Math.sin(Math.toRadians(pitchGrados));
        // centro del modelo para girar alrededor de él
        double mx = 0, my = 0, mz = 0;
        for (var p : m.puntos()) { mx += p.x(); my += p.y(); mz += p.z(); }
        final double ox = mx / m.puntos().size(), oy = my / m.puntos().size(), oz = mz / m.puntos().size();
        final boolean persp = distancia > 0;
        double[][] crudo = new double[m.puntos().size()][];
        double minX = 1e18, maxX = -1e18, minY = 1e18, maxY = -1e18;
        for (var p : m.puntos()) {
            double[] c = gira(p.x() - ox, p.y() - oy, p.z() - oz, cy, sy, cp, sp, distancia);
            crudo[p.id()] = c;
            minX = Math.min(minX, c[0] * c[3]); maxX = Math.max(maxX, c[0] * c[3]);
            minY = Math.min(minY, c[1] * c[3]); maxY = Math.max(maxY, c[1] * c[3]);
        }
        final double s = Math.min((W - 120) / (maxX - minX), (H - 120) / (maxY - minY));
        final double offX = W / 2.0 - s * (minX + maxX) / 2, offY = H / 2.0 - s * (minY + maxY) / 2;
        return p -> {
            double[] c = crudo[p.id()];
            return new ArbolParametrico3D.Proyectado(offX + s * c[0] * c[3], offY + s * c[1] * c[3], -c[2], s * c[3]);
        };
    }

    /** {x', y', z (hacia el fondo), factor de perspectiva}. */
    static double[] gira(double x, double y, double z, double cy, double sy, double cp, double sp, double distancia) {
        double x1 = x * cy + z * sy, z1 = -x * sy + z * cy;           // yaw
        double y2 = y * cp - z1 * sp, z2 = y * sp + z1 * cp;          // pitch
        double k = distancia > 0 ? distancia / (distancia + z2) : 1;
        return new double[] { x1, y2, z2, k };
    }

    /**
     * La geometría proyectada con mis propios nodos y aristas (igual que {@link ArbolParametrico3D.Modelo#proyectar}
     * cuando conAltura y no porNodo), para poder quitar la altura (solo manda el orden del recorrido, TRAZO por s) o
     * dar a cada nodo un color distinto y ver qué rama queda encima de cuál (diagnóstico).
     */
    static Grafo construye(ArbolParametrico3D.Modelo m, ArbolParametrico3D.Proyeccion cam, boolean conAltura, boolean porNodo) {
        return construye(m, cam, conAltura, porNodo, 0);
    }

    /**
     * Como {@link #construye(ArbolParametrico3D.Modelo, ArbolParametrico3D.Proyeccion, boolean, boolean)} con niebla por
     * profundidad: el color de cada nodo se mezcla con el fondo según lo lejos que está de la cámara (0 = sin niebla;
     * 1 = el punto más lejano se funde del todo con el fondo). Es el clásico "depth cue" del 3D primitivo.
     */
    static Grafo construye(ArbolParametrico3D.Modelo m, ArbolParametrico3D.Proyeccion cam, boolean conAltura, boolean porNodo, double niebla) {
        double zMin = Double.POSITIVE_INFINITY, zMax = Double.NEGATIVE_INFINITY;
        for (var p : m.puntos()) {
            double z = cam.aplicar(p).profundidad();
            zMin = Math.min(zMin, z);
            zMax = Math.max(zMax, z);
        }
        Grafo c = new Grafo().mezcla(Mezcla.RGB);
        var nodos = new Grafo.Nodo[m.puntos().size()];
        int[] color = new int[nodos.length];
        for (var p : m.puntos()) {
            var q = cam.aplicar(p);
            color[p.id()] = porNodo ? java.awt.Color.HSBtoRGB((float) ((p.id() * 0.618034) % 1.0), 0.85f, 0.9f) | 0xFF000000 : p.color();
            if (niebla > 0 && zMax > zMin)      // la profundidad crece hacia la cámara: lejos = zMin
                color[p.id()] = Mezcla.RGB.mezclar(color[p.id()], FONDO, niebla * (zMax - q.profundidad()) / (zMax - zMin));
            nodos[p.id()] = c.nodo(q.x(), q.y(), p.radio() * q.escala()).centro(color[p.id()]);
        }
        for (var r : m.ramas()) {
            final int a = color[r.origen().id()], b = color[r.extremo().id()];
            final double h0 = cam.aplicar(r.origen()).profundidad(), h1 = cam.aplicar(r.extremo()).profundidad();
            var e = c.arista(nodos[r.origen().id()], nodos[r.extremo().id()]).espina(t -> Mezcla.RGB.mezclar(a, b, Math.max(0, Math.min(1, t))));
            if (conAltura) e.altura(t -> h0 + (h1 - h0) * t);
        }
        return c;
    }

    static BufferedImage pinta(Grafo c) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        Plotter.raster(im).plot(c, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        return im;
    }

    /** Un color distinto por nodo: filas = vista de frente y desde el lado opuesto (yaw + 180); columnas = sin altura | con altura. */
    static void diagnostico(ArbolParametrico3D.Modelo m, double yaw, double pitch) throws Exception {
        BufferedImage hoja = new BufferedImage(2 * W, 2 * H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        for (int fila = 0; fila < 2; fila++) {
            var cam = camara(m, yaw + 180 * fila, pitch, 0);
            for (int col = 0; col < 2; col++) g.drawImage(pinta(construye(m, cam, col == 1, true)), col * W, fila * H, null);
        }
        g.dispose();
        ImageIO.write(hoja, "png", new File("arbol_vistas_diagnostico.png"));
        System.out.println("arbol_vistas_diagnostico.png (filas: yaw y yaw + 180; columnas: sin altura | con altura)");
    }

    /** Niebla por profundidad: columnas = sin niebla | 0.6 | 0.9, con altura + relajar + alisar, en perspectiva. */
    static void niebla(ArbolParametrico3D.Modelo m, double yaw, double pitch) throws Exception {
        BufferedImage hoja = new BufferedImage(3 * W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        var cam = camara(m, yaw, pitch, 900);
        double[] k = { 0, 0.6, 0.9 };
        for (int col = 0; col < 3; col++) {
            Grafo c = construye(m, cam, true, false, k[col]);
            c.relajar();
            c.alisar();
            g.drawImage(pinta(c), col * W, 0, null);
        }
        g.dispose();
        ImageIO.write(hoja, "png", new File("arbol_niebla.png"));
        System.out.println("arbol_niebla.png (niebla 0 | 0.6 | 0.9)");
    }

    public static void main(String[] args) throws Exception {
        ArbolParametrico3D.Parametros p = new ArbolParametrico3D.Parametros();
        // densidad: -Dramas=N (ramas hijas por nodo) y -Dniveles=N; con 3-4 ramas hay muchos más cruces (test de estrés)
        p.ramas = Integer.getInteger("ramas", p.ramas);
        p.niveles = Integer.getInteger("niveles", p.niveles);
        double yaw = args.length > 0 ? Double.parseDouble(args[0]) : 35, pitch = args.length > 1 ? Double.parseDouble(args[1]) : 20;
        ArbolParametrico3D.Modelo m = ArbolParametrico3D.crear(p);
        if (args.length > 2 && args[2].equals("niebla")) { niebla(m, yaw, pitch); return; }
        if (args.length > 2 && args[2].equals("diagnostico")) { diagnostico(m, yaw, pitch); return; }
        BufferedImage hoja = new BufferedImage(4 * W, 2 * H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        String[] cols = { "sin altura", "altura", "altura + relajar", "altura + relajar + alisar" };
        double[] dist = { 0, 900 };
        for (int fila = 0; fila < 2; fila++) {
            var cam = camara(m, yaw, pitch, dist[fila]);
            for (int col = 0; col < 4; col++) {
                Grafo c = col == 0 ? construye(m, cam, false, false) : m.proyectar(cam);
                if (col >= 2) c.relajar();
                if (col >= 3) c.alisar();
                g.drawImage(pinta(c), col * W, fila * H, null);
                System.out.printf("%-10s %-26s %d aristas, %d rígidas, %d plegadas%n", fila == 0 ? "ortográfica" : "perspectiva", cols[col], c.gotas().size(), c.rigidas().size(), c.plegadas().size());
            }
        }
        g.dispose();
        ImageIO.write(hoja, "png", new File("arbol_vistas.png"));
    }
}
