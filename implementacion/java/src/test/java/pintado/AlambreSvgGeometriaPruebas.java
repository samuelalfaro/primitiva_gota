package pintado;

import color.Colores;
import color.Mezcla;
import composicion.Grafo;
import composicion.Pintada;
import forma.Gota;
import forma.Perfil;
import geometria.Grupo;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.StringWriter;
import java.util.Arrays;

/**
 * Pruebas del alambre y el SVG sobre {@link geometria.Geometria}: el alambre de un grupo es el de sus gotas una a una; el
 * SVG conserva la estructura (cada {@link Grupo} es un {@code <g>}, con su label como id, y las etiquetas están
 * equilibradas); un {@link Grafo} da los trazos de todas sus gotas.
 */
public class AlambreSvgGeometriaPruebas {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-78s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    static Gota disco(double x, double y, double r) {
        return Gota.lineal(new Point2D.Double(x, y), r, new Point2D.Double(x + 80, y), r, Perfil.recto());
    }

    static BufferedImage lienzo() {
        BufferedImage im = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);
        return im;
    }

    static int[] datos(BufferedImage im) { return ((DataBufferInt) im.getRaster().getDataBuffer()).getData(); }

    static int cuenta(String s, String t) {
        int n = 0;
        for (int i = s.indexOf(t); i >= 0; i = s.indexOf(t, i + t.length())) n++;
        return n;
    }

    static String svg(java.util.function.Consumer<PlotterSvg> f) {
        StringWriter sw = new StringWriter();
        PlotterSvg p = new PlotterSvg(sw);
        f.accept(p);
        p.close();
        return sw.toString();
    }

    public static void main(String[] args) {
        Gota a = disco(60, 80, 25), b = disco(160, 180, 20);
        Pintada pa = Pintada.de(a, Colores.dosColores(0xFFFF0000, 0xFFFF0000)), pb = Pintada.de(b, Colores.dosColores(0xFF0000FF, 0xFF0000FF));
        Grupo g = new Grupo(pa, new Grupo(pb).label("interno")).label("par");

        BufferedImage x = lienzo(), y = lienzo();
        PlotterAlambre.sobre(x).plot(g);
        PlotterAlambre.sobre(y).plot(a);
        PlotterAlambre.sobre(y).plot(b);
        comprueba("alambre de un grupo = alambre de sus gotas una a una", Arrays.equals(datos(x), datos(y)) && !Arrays.equals(datos(x), datos(lienzo())), "");

        BufferedImage h = lienzo(), k = lienzo();
        PlotterAlambre.sobre(h).plot(pa);
        PlotterAlambre.sobre(k).plot(a);
        comprueba("alambre de una Pintada = alambre de su gota", Arrays.equals(datos(h), datos(k)), "");

        String sa = svg(p -> p.plot(a)), sb = svg(p -> p.plot(b));
        String sg = svg(p -> p.plot(g));
        comprueba("SVG del grupo: tantos paths como sus gotas por separado", cuenta(sg, "<path") == cuenta(sa, "<path") + cuenta(sb, "<path"), cuenta(sg, "<path") + "");
        comprueba("SVG del grupo: <g> anidados con su id y etiquetas equilibradas",
            sg.contains("<g id=\"par\">") && sg.contains("<g id=\"interno\">") && cuenta(sg, "<g") == 2 && cuenta(sg, "</g>") == 2
                && sg.indexOf("<g id=\"par\">") < sg.indexOf("<g id=\"interno\">") && sg.lastIndexOf("</g>") < sg.indexOf("</svg>"), "");
        comprueba("SVG de una gota sola no lleva <g>", !sa.contains("<g"), "");

        String sl = svg(p -> p.plot(new Grupo(pa.conLabel("a<&\"b")).label("x")));
        comprueba("el label se escapa como atributo XML", sl.contains("<g id=\"a&lt;&amp;&quot;b\">") && cuenta(sl, "<g") == 2, "");

        Grafo gr = new Grafo().mezcla(Mezcla.RGB);
        gr.arista(gr.nodo(60, 60, 10).centro(0xFFFF0000), gr.nodo(200, 60, 10).centro(0xFF00FF00));
        gr.arista(gr.nodo(200, 60, 10).centro(0xFF00FF00), gr.nodo(200, 200, 10).centro(0xFF0000FF));
        String sgr = svg(p -> p.plot(gr));
        int porGota = 0;
        for (Gota o : gr.gotas()) porGota += cuenta(svg(p -> p.plot(o)), "<path");
        comprueba("SVG de un Grafo: los trazos de todas sus gotas", cuenta(sgr, "<path") == porGota && gr.gotas().size() == 2, cuenta(sgr, "<path") + " vs " + porGota);

        String svc = svg(p -> { p.plot(new Grupo()); p.plot(pa); });
        comprueba("un grupo vacío no ensancha la vista", svc.contains("viewBox") && svc.contains(sa.substring(sa.indexOf("viewBox"), sa.indexOf("viewBox") + 20)), "");

        System.out.println(pruebas + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }
}
