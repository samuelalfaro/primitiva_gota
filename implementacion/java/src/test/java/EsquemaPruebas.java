import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Pruebas del esquema estructural ({@link Trazos.Ajustes#ESQUEMA} con {@link PlotterAlambre}): se dibuja sobre una imagen (deja huella, pinta los dos centros y
 * respeta el contrato de los plotters) en la batería de formas y radios de las suites de matriz, incluidos los
 * radios 0 y los lazos. Deja la imagen {@code esquema.png} como catálogo.
 */
public class EsquemaPruebas {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        if (!ok) System.out.printf("%-60s FALLA  %s%n", n, d);
    }

    static final String[] FORMAS = { "recta", "suave", "S", "ჲ" };
    static final double[][] RADIOS = { { 0, 4 }, { 1, 3 }, { 2, 2 }, { 3, 1 }, { 4, 0 } };
    static final int CW = SuiteMatriz_01_Alambre.CW, CH = SuiteMatriz_01_Alambre.CH;
    static final int TINTA = 0x12234A;

    static BufferedImage lienzo(int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return im;
    }

    static boolean distintoDeBlanco(BufferedImage im) {
        for (int y = 0; y < im.getHeight(); y++)
            for (int x = 0; x < im.getWidth(); x++)
                if ((im.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) return true;
        return false;
    }

    /** El arco de cada tapa sale del centro hacia fuera según la tangente: contra ella en la inicial, a favor en la final. */
    static void arcosHaciaFuera(String nombre, Geometria g) {
        for (int e = 0; e < 2; e++) {
            double[] m = Trazos.mitadTapa(g, e);
            double tx = g.dcx(e), ty = g.dcy(e), n = Math.hypot(tx, ty);
            if (m == null || n < 1e-9) continue;
            double s = e == 0 ? -1 : 1;
            double dot = ((m[0] - g.cx(e)) * tx + (m[1] - g.cy(e)) * ty) * s / n;
            comprueba(nombre + ": arco de la tapa " + (e + 1) + " hacia fuera según la tangente", dot > 0, "producto " + dot);
        }
    }

    static long cuenta(java.util.List<Trazos.Trazo> ts, Trazos.Rol r) { return ts.stream().filter(t -> t.rol() == r).count(); }

    /** Qué roles salen en cada caso: el esquema, el camino recto o ignorado (flechas) y el camino curvo (tiradores y semiejes). */
    static void estructura() {
        Gota rigida = SuiteMatriz_01_Alambre.gota("suave", 3, 2, 2, 90, 150);      // trayectoria <= r1 + r2: el camino se ignora
        Gota recta = SuiteMatriz_01_Alambre.gota("recta", 5, 1, 1, 90, 150);       // recta con trayectoria > r1 + r2
        Gota curva = SuiteMatriz_01_Alambre.gota("suave", 5, 1, 1, 90, 150);       // curva con trayectoria > r1 + r2
        Trazos.Ajustes aj = Trazos.Ajustes.POR_DEFECTO.conRadios(true);
        comprueba("rigida: se ignora el camino", Trazos.rectoOIgnorado(rigida), "");
        comprueba("recta: sin curva", Trazos.rectoOIgnorado(recta), "");
        comprueba("curva: hay curva", !Trazos.rectoOIgnorado(curva), "");
        for (var c : new Object[][] { { "rígida", rigida }, { "recta", recta } }) {
            var ts = Trazos.trazos((Gota) c[1], aj);
            comprueba(c[0] + ": sin tiradores", cuenta(ts, Trazos.Rol.TIRADOR) == 0 && cuenta(ts, Trazos.Rol.PUNTO) == 0, "");
            comprueba(c[0] + ": sin semiejes", cuenta(ts, Trazos.Rol.SEMIEJE) == 0, "");
            comprueba(c[0] + ": una flecha por centro", cuenta(ts, Trazos.Rol.FLECHA) == 2, cuenta(ts, Trazos.Rol.FLECHA) + " flechas");
        }
        var tc = Trazos.trazos(curva, aj);
        comprueba("curva: tiradores y semiejes, sin flechas",
                  cuenta(tc, Trazos.Rol.TIRADOR) == 2 && cuenta(tc, Trazos.Rol.SEMIEJE) == 2 && cuenta(tc, Trazos.Rol.FLECHA) == 0, "");
        var te = Trazos.trazos(curva, Trazos.Ajustes.ESQUEMA);
        comprueba("esquema: sin contorno, extremos, tiradores ni semiejes",
                  cuenta(te, Trazos.Rol.CONTORNO) + cuenta(te, Trazos.Rol.EXTREMO) + cuenta(te, Trazos.Rol.TIRADOR) + cuenta(te, Trazos.Rol.SEMIEJE) == 0, "");
        comprueba("esquema: dos tapas, cuatro radios, cuatro P y dos centros",
                  cuenta(te, Trazos.Rol.TAPA) == 2 && cuenta(te, Trazos.Rol.RADIO) == 4 && cuenta(te, Trazos.Rol.CONTACTO) == 4 && cuenta(te, Trazos.Rol.CENTRO) == 2, "");
        comprueba("esquema: cada trazo lleva su halo debajo", cuenta(te, Trazos.Rol.HALO_TAPA) == 2 && cuenta(te, Trazos.Rol.HALO_RADIO) == 4
                  && cuenta(te, Trazos.Rol.HALO_CONTACTO) == 4 && cuenta(te, Trazos.Rol.HALO_CENTRO) == 2 && cuenta(te, Trazos.Rol.HALO_CAMINO) == 1, "");
        for (int i = 0; i < te.size(); i++)
            if (te.get(i).rol().halo() != null)
                comprueba("halo justo debajo de " + te.get(i).rol(), i > 0 && te.get(i - 1).rol() == te.get(i).rol().halo(), "");
        java.io.StringWriter w = new java.io.StringWriter();
        try (PlotterSvg svg = new PlotterSvg(w, Trazos.Ajustes.ESQUEMA)) { svg.plot(curva); }
        String sv = w.toString();
        comprueba("SVG del esquema: clases y opacidad de los halos", sv.contains("class=\"tapa\"") && sv.contains("class=\"halo-tapa\"")
                  && sv.contains(".halo-tapa { stroke: #ffffff; stroke-width: 4.2; stroke-opacity: 0.82") && !sv.contains("class=\"contorno\""), "");
        comprueba("flecha: tamaño fijo, no depende del radio",
                  Math.abs(longitud(Trazos.trazos(SuiteMatriz_01_Alambre.gota("recta", 5, 1, 1, 90, 150), aj))
                         - longitud(Trazos.trazos(SuiteMatriz_01_Alambre.gota("recta", 5, 3, 0.5, 90, 150), aj))) < 1e-9, "");
    }

    /** Distancia entre el primer y el último punto del primer trazo FLECHA (el largo del eje). */
    static double longitud(java.util.List<Trazos.Trazo> ts) {
        for (var t : ts)
            if (t.rol() == Trazos.Rol.FLECHA) {
                double[] c = new double[6];
                var it = t.forma().getPathIterator(null);
                it.currentSegment(c); double ax = c[0], ay = c[1]; it.next(); it.currentSegment(c);
                return Math.hypot(c[0] - ax, c[1] - ay);
            }
        return -1;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage cat = lienzo(70 + RADIOS.length * CW, 30 + FORMAS.length * CH);
        for (int f = 0; f < FORMAS.length; f++) {
            for (int c = 0; c < RADIOS.length; c++) {
                double r1 = RADIOS[c][0], r2 = RADIOS[c][1];
                String nombre = String.format("%s r1=%.1f r2=%.1f", FORMAS[f], r1, r2);
                BufferedImage celda = lienzo(CW, CH);
                try {
                    Gota g = SuiteMatriz_01_Alambre.gota(FORMAS[f], 4, r1, r2, 90, CH * 0.62);
                    Plotter.raster(celda).plot(g, Relleno.solido(0xFFE3E0D6));
                    BufferedImage base = new BufferedImage(CW, CH, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D gb = base.createGraphics();
                    gb.drawImage(celda, 0, 0, null);
                    gb.dispose();
                    PlotterAlambre.sobre(celda, Trazos.Ajustes.ESQUEMA).plot(g);
                    boolean cambia = false;
                    for (int y = 0; y < CH && !cambia; y++)
                        for (int x = 0; x < CW; x++)
                            if (celda.getRGB(x, y) != base.getRGB(x, y)) { cambia = true; break; }
                    comprueba(nombre + ": deja huella sobre el dibujo", cambia, "sin cambios");
                    for (int e = 0; e < 2; e++) {
                        int x = (int) Math.round(g.cx(e)), y = (int) Math.round(g.cy(e));
                        comprueba(nombre + ": centro " + (e + 1) + " pintado",
                                  (celda.getRGB(x, y) & 0xFFFFFF) == TINTA, Integer.toHexString(celda.getRGB(x, y)));
                    }
                    // por Graphics2D ajeno: mismo resultado que sobre(imagen)
                    BufferedImage otra = lienzo(CW, CH);
                    Plotter.raster(otra).plot(g, Relleno.solido(0xFFE3E0D6));
                    Graphics2D go = otra.createGraphics();
                    PlotterAlambre.plot(go, g, Trazos.Ajustes.ESQUEMA);
                    go.dispose();
                    boolean igual = true;
                    for (int y = 0; y < CH && igual; y++)
                        for (int x = 0; x < CW; x++)
                            if (otra.getRGB(x, y) != celda.getRGB(x, y)) { igual = false; break; }
                    comprueba(nombre + ": Graphics2D y sobre(imagen) coinciden", igual, "difieren");
                    arcosHaciaFuera(nombre, g);
                } catch (RuntimeException e) {
                    comprueba(nombre + ": se dibuja", false, e.toString());
                }
                Graphics2D gg = cat.createGraphics();
                gg.drawImage(celda, 70 + c * CW, 30 + f * CH, null);
                gg.setColor(new Color(0xCCCCCC));
                gg.drawRect(70 + c * CW, 30 + f * CH, CW - 1, CH - 1);
                if (f == 0) {
                    gg.setColor(Color.DARK_GRAY);
                    gg.drawString(String.format("r1 = %.1f   r2 = %.1f", r1, r2), 70 + c * CW + 8, 18);
                }
                if (c == 0) {
                    gg.setColor(Color.DARK_GRAY);
                    gg.drawString(FORMAS[f], 6, 30 + f * CH + 16);
                }
                gg.dispose();
            }
        }
        // caminos con lazo (α) y C casi cerrada (Ω): el otro centro no queda "detrás" de la tapa
        double E = 34, ox = 150, oy = 140;
        for (double k : new double[] { 1.2, 2.0, 2.8 }) {
            Gota g = Gota.curva(new java.awt.geom.Point2D.Double(ox - E, oy), E, new Gota.Vec(k * E, -0.9 * k * E),
                                new java.awt.geom.Point2D.Double(ox + E, oy), E, new Gota.Vec(-k * E, -0.9 * k * E), Perfil.recto());
            arcosHaciaFuera("lazo k=" + k, g);
        }
        for (double fi : new double[] { 50, 25, 5 }) {
            double R = 2.4 * E, p = Math.toRadians(fi), L = 1.9 * R * 0.55;
            Gota g = Gota.curva(new java.awt.geom.Point2D.Double(ox + R * Math.cos(p), oy - R * Math.sin(p)), E,
                                new Gota.Vec(-L * Math.sin(p), -L * Math.cos(p)),
                                new java.awt.geom.Point2D.Double(ox + R * Math.cos(p), oy + R * Math.sin(p)), E,
                                new Gota.Vec(-L * Math.sin(p), L * Math.cos(p)), Perfil.recto());
            arcosHaciaFuera("C cerrada φ=" + fi, g);
        }
        estructura();
        boolean lanza = false;
        try {
            PlotterAlambre.sobre(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), Trazos.Ajustes.ESQUEMA);
        } catch (IllegalArgumentException e) { lanza = true; }
        comprueba("sobre(imagen): exige TYPE_INT_ARGB", lanza, "no lanza");
        ImageIO.write(cat, "png", new File("esquema.png"));
        System.out.printf("%n%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
