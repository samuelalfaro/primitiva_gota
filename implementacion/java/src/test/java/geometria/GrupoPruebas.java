package geometria;

import composicion.Grafo;
import composicion.Pintada;
import color.Colores;
import color.Mezcla;
import coordenadas.Opciones;
import coordenadas.Solape;
import forma.Gota;
import forma.Perfil;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import pintado.Plotter;

/**
 * Pruebas de {@link Grupo}: las capas de varias geometrías se componen por altura (no por orden de añadido), con
 * empate por orden de añadido; el alfa deja ver lo de abajo; la altura del grupo desplaza a todos sus hijos; los
 * grupos se anidan; y se puede poner una composición por encima de otra, que es lo que el orden de pintado no permitía.
 */
public class GrupoPruebas {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-78s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    static final int W = 420, H = 420, FONDO = 0xFFF4F1EA;
    static final int ROJO = 0xFFE8283A, AZUL = 0xFF2850E8, VERDE = 0xFF2E9E5B;
    static final Opciones OP = Opciones.POR_DEFECTO;

    static BufferedImage lienzo() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        return im;
    }

    static int distintos(BufferedImage a, BufferedImage b) {
        int[] x = ((DataBufferInt) a.getRaster().getDataBuffer()).getData(), y = ((DataBufferInt) b.getRaster().getDataBuffer()).getData();
        int n = 0;
        for (int i = 0; i < x.length; i++) if (x[i] != y[i]) n++;
        return n;
    }

    /** Un disco (gota circular) de un solo color. */
    static Pintada disco(double x, double y, double r, int color) {
        return Pintada.de(Gota.lineal(new Point2D.Double(x, y), r, new Point2D.Double(x + 0.01, y), r, Perfil.recto()), Colores.dosColores(color, color));
    }

    static List<Geometria> hojas(Geometria g) {
        List<Geometria> r = new ArrayList<>();
        g.acepta(new Visitante() { @Override public void hoja(Geometria h) { r.add(h); } });
        return r;
    }

    static BufferedImage pinta(Geometria g) {
        BufferedImage im = lienzo();
        Plotter.raster(im).plot(g, OP);
        return im;
    }

    static int pixel(Geometria g, int x, int y) { return pinta(g).getRGB(x, y); }

    /** Dos cintas que se cruzan: roja de (150,150) a (360,360) y azul de (360,60) a (60,360). */
    static Grafo cinta(boolean roja) {
        Grafo c = new Grafo().mezcla(Mezcla.RGB);
        int col = roja ? ROJO : AZUL;
        if (roja) c.arista(c.nodo(150, 150, 14).centro(col), c.nodo(360, 360, 14).centro(col));
        else c.arista(c.nodo(360, 60, 14).centro(col), c.nodo(60, 360, 14).centro(col));
        return c;
    }

    public static void main(String[] args) {
        // 1. la altura manda sobre el orden de añadido
        Pintada rojo = disco(180, 200, 60, ROJO), azul = disco(240, 200, 60, AZUL);
        int cruce = 210;     // dentro de los dos discos
        comprueba("misma altura: va encima la añadida después (azul)", pixel(new Grupo(rojo, azul), cruce, 200) == AZUL, "");
        comprueba("misma altura: va encima la añadida después (rojo)", pixel(new Grupo(azul, rojo), cruce, 200) == ROJO, "");
        comprueba("el rojo más alto queda encima aunque se añada antes", pixel(new Grupo(rojo.conAltura(2), azul.conAltura(1)), cruce, 200) == ROJO, "");
        comprueba("el rojo más alto queda encima aunque se añada después", pixel(new Grupo(azul.conAltura(1), rojo.conAltura(2)), cruce, 200) == ROJO, "");
        comprueba("fuera del solape cada disco conserva su color", pixel(new Grupo(rojo, azul), 140, 200) == ROJO && pixel(new Grupo(rojo, azul), 280, 200) == AZUL, "");

        // 2. el grupo es igual que pintar las geometrías una a una, de abajo a arriba
        Pintada a = disco(150, 200, 70, 0xAAE8283A), b = disco(220, 160, 70, 0xAA2850E8), c = disco(220, 250, 70, 0xAA2E9E5B);
        BufferedImage seq = lienzo();
        Plotter.raster(seq).plot(b.conAltura(-1), OP);
        Plotter.raster(seq).plot(c, OP);
        Plotter.raster(seq).plot(a.conAltura(1), OP);
        comprueba("con alfa, el grupo equivale a pintar una a una por altura", distintos(seq, pinta(new Grupo(a.conAltura(1), b.conAltura(-1), c))) == 0, "");
        comprueba("con alfa se ve lo de abajo (mezcla distinta del color de arriba)", pixel(new Grupo(a, b), 185, 180) != 0xAAE8283A && ((pixel(new Grupo(a, b), 185, 180) >> 8) & 255) != ((0xAAE8283A >> 8) & 255), "");

        // 3. la altura del grupo desplaza a todos los hijos
        Grupo bajo = new Grupo(disco(180, 200, 60, ROJO), disco(240, 200, 60, ROJO));
        Pintada otro = disco(210, 200, 30, VERDE).conAltura(3);
        comprueba("un grupo con altura 0 queda debajo de un hijo de altura 3", pixel(new Grupo(bajo, otro), cruce, 200) == VERDE, "");
        comprueba("un grupo con altura 5 queda por encima de un hijo de altura 3", pixel(new Grupo(bajo.altura(5), otro), cruce, 200) == ROJO, "");
        bajo.altura(0);

        // 4. grupos anidados
        Grupo plano = new Grupo(rojo.conAltura(1), azul.conAltura(2), disco(210, 200, 20, VERDE).conAltura(3));
        Grupo anidado = new Grupo(new Grupo(rojo.conAltura(1), azul.conAltura(2)), disco(210, 200, 20, VERDE).conAltura(3));
        comprueba("un grupo anidado da lo mismo que el grupo plano", distintos(pinta(plano), pinta(anidado)) == 0, "");

        // 5. dos composiciones: la altura decide quién va encima, no el orden de pintado (la capa entre Grafos de #48)
        Grafo r = cinta(true), z = cinta(false);
        int xc = 210, yc = 210;       // el cruce de las dos cintas
        comprueba("dos Grafo, mismas alturas: encima el añadido después (azul)", pixel(new Grupo(r, z), xc, yc) == AZUL, "");
        comprueba("dos Grafo, rojo con altura 1: encima el rojo aunque se añada antes", pixel(new Grupo(r.altura(1), z), xc, yc) == ROJO, "");
        comprueba("dos Grafo, rojo con altura 1: encima el rojo aunque se añada después", pixel(new Grupo(z, r), xc, yc) == ROJO, "");
        comprueba("dos Grafo, azul con altura 2: encima el azul", pixel(new Grupo(r, z.altura(2)), xc, yc) == AZUL, "");
        r.altura(0); z.altura(0);
        Grupo mixto = new Grupo(r.altura(1), z, disco(100, 100, 25, VERDE));
        comprueba("un grupo mezcla Grafo y Pintada", pixel(mixto, 100, 100) == VERDE && pixel(mixto, xc, yc) == ROJO, "");

        // 6. límites, silueta, vacío, metadatos y errores
        Grupo g2 = new Grupo(rojo, azul).label("par");
        var bb = g2.bounds();
        comprueba("bounds() es la unión de los límites", bb.contains(rojo.bounds()) && bb.contains(azul.bounds()) && bb.getWidth() < rojo.bounds().getWidth() + azul.bounds().getWidth(), bb.toString());
        comprueba("la silueta contiene los dos discos", g2.silueta().contains(150, 200) && g2.silueta().contains(270, 200) && !g2.silueta().contains(10, 10), "");
        comprueba("label, altura por defecto y hijos en orden", "par".equals(g2.label()) && g2.altura() == 0 && hojas(g2).equals(List.of(rojo, azul)), "");
        comprueba("un grupo vacío no pinta nada", distintos(pinta(new Grupo()), lienzo()) == 0 && new Grupo().bounds().isEmpty(), "");
        boolean ciclo = false, nulo = false;
        Grupo gg = new Grupo(), h = new Grupo(gg);
        try { gg.anade(h); } catch (IllegalArgumentException e) { ciclo = true; }
        try { gg.anade(gg); } catch (IllegalArgumentException e) { ciclo &= true; }
        try { gg.anade(null); } catch (NullPointerException e) { nulo = true; }
        comprueba("no admite ciclos ni nulos", ciclo && nulo, "");

        // 6b. visitante, caminos, recorre, esHoja y nHijos
        List<String> ev = new ArrayList<>();
        Grupo interno = new Grupo(azul).label("in"), externo = new Grupo(rojo, interno);
        externo.acepta(new Visitante() {
            @Override public void hoja(Geometria g) { ev.add(g == rojo ? "rojo" : "azul"); }
            @Override public boolean entra(Grupo g) { ev.add("entra " + g.label()); return true; }
            @Override public void sale(Grupo g) { ev.add("sale " + g.label()); }
        });
        comprueba("visitante: orden entra/hoja/sale en grupos anidados", ev.equals(List.of("entra null", "rojo", "entra in", "azul", "sale in", "sale null")), ev.toString());
        ev.clear();
        externo.acepta(new Visitante() {
            @Override public void hoja(Geometria g) { ev.add("hoja"); }
            @Override public boolean entra(Grupo g) { ev.add("entra " + g.label()); return g.label() == null; }
            @Override public void sale(Grupo g) { ev.add("sale " + g.label()); }
        });
        comprueba("entra() = false no desciende pero sale() se llama", ev.equals(List.of("entra null", "hoja", "entra in", "sale in", "sale null")), ev.toString());
        ev.clear();
        rojo.acepta(new Visitante() { @Override public void hoja(Geometria g) { ev.add("hoja"); } });
        comprueba("una hoja llama a hoja() una vez", ev.size() == 1, ev.toString());
        comprueba("caminos(): uno por pintada y concatenados en el grupo", rojo.caminos().size() == 1 && externo.caminos().size() == 2 && new Grupo().caminos().isEmpty() && externo.caminos().get(0) == rojo.gota(), "");
        int[] nr = {0};
        boolean[] ident = {true};
        externo.recorre((cam, tr) -> { nr[0]++; ident[0] &= tr.isIdentity(); });
        comprueba("recorre(): un camino por gota, con la identidad", nr[0] == 2 && ident[0], "");
        Grafo cr = cinta(true);
        comprueba("Grafo: caminos() = sus gotas", cr.caminos().size() == cr.gotas().size() && !cr.caminos().isEmpty(), "");
        comprueba("esHoja/nHijos: hoja 0, grupo n, grupo vacío no es hoja", rojo.esHoja() && rojo.nHijos() == 0 && !externo.esHoja() && externo.nHijos() == 2 && !new Grupo().esHoja() && new Grupo().nHijos() == 0, "");

        // 7. TRAZO: las capas de una gota con lazo se conservan en su orden dentro del grupo
        Pintada lazo = Pintada.de(Gota.curva(new Point2D.Double(100, 160), 16, new Gota.Vec(330, -420), new Point2D.Double(200, 160), 16, new Gota.Vec(-330, -420), Perfil.recto()),
                Colores.dosColores(0xAAD94A38, 0xAA2F6DB5));
        BufferedImage sola = lienzo(), enGrupo = lienzo();
        Plotter.raster(sola).plot(lazo, OP.conSolape(Solape.TRAZO));
        Plotter.raster(enGrupo).plot(new Grupo(lazo), OP.conSolape(Solape.TRAZO));
        comprueba("un grupo de una geometría pinta igual que ella sola (TRAZO, con lazo)", distintos(sola, enGrupo) == 0, "");

        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
