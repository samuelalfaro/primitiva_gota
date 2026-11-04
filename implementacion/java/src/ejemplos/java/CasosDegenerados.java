import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

/**
 * Experimento: batería de casos degenerados de la gota (tapa tangente o contenida en la otra, centros coincidentes,
 * círculos idénticos, perfil que engorda con centros iguales, elipses con centros iguales, casi coincidentes) y barrido
 * de la distancia entre centros hacia 0. No comprueba nada ni corrige nada: pinta lo que sale y lo mide.
 * Solo usa la API pública. Genera:
 * <ul>
 *   <li>degeneradas_texturas.png y degeneradas_degradados.png: un caso por fila y un modo de costura por columna;</li>
 *   <li>degeneradas_barrido.png: la distancia entre centros tiende a 0, con radios iguales y con radios distintos;</li>
 *   <li>degeneradas_informe.txt: por caso, solape y costura, píxeles dentro, con uv NaN, fuera de [0, 1], reparto por
 *       zonas de la tira y a qué tapa pertenecen los puntos detrás de C1 y delante de C2.</li>
 * </ul>
 * La textura de referencia tiene un fondo distinto por zona (tapa trasera, dos mitades del cuerpo, tapa delantera), así
 * que el color dice a qué zona asigna cada píxel el mapeo.
 */
public class CasosDegenerados {
    static Point2D.Double p(double x, double y) { return new Point2D.Double(x, y); }

    static final int FONDO = 0xFFF4F1EA, TEXTO = 0xFF3A3A3A;
    static final Costura[] MODOS = Costura.values();

    record Caso(String titulo, String descripcion, Supplier<Gota> gota) {}

    // ---------- Los casos (celda de 240 x 240; centro en (120, 120); 36 px por unidad) ----------

    static List<Caso> casos() {
        double u = 36;
        Perfil recto = Perfil.recto();
        List<Caso> c = new ArrayList<>();
        c.add(new Caso("Referencia", "Radios iguales r = 40, d = 108.\nUna gota normal.",
                () -> Gota.lineal(p(66, 120), 40, p(174, 120), 40, recto)));
        c.add(new Caso("Tangente interior (documento)", "C1 = (3,0) r1 = 2; C2 = (4,0) r2 = 1.\nTangentes por dentro en x = 5.",
                () -> Gota.lineal(p(120, 120), 2 * u, p(120 + u, 120), 1 * u, recto)));
        c.add(new Caso("Tapa contenida", "r1 = 2, r2 = 0,5, d = 0,5:\nla segunda queda dentro de la primera.",
                () -> Gota.lineal(p(120, 120), 2 * u, p(120 + 0.5 * u, 120), 0.5 * u, recto)));
        c.add(new Caso("Centros iguales, radios 2 y 1", "El mismo centro y el camino de longitud 0:\nla tapa mayor debería quedarse el círculo.",
                () -> Gota.lineal(p(120, 120), 2 * u, p(120, 120), 1 * u, recto)));
        c.add(new Caso("Círculos idénticos", "Mismo centro y mismo radio:\nse ve un círculo, pero hay dos tapas.",
                () -> Gota.lineal(p(120, 120), 60, p(120, 120), 60, recto)));
        c.add(new Caso("Centros iguales, perfil que engorda", "r1 = r2 = 48 y rm = 72:\nel círculo intermedio manda en el contorno.",
                () -> Gota.lineal(p(120, 120), 48, p(120, 120), 48, Perfil.radioMedio(72))));
        c.add(new Caso("Ida y vuelta (centros iguales)", "Mismo centro con tiradores:\nla orientación sale de la Bézier.",
                () -> Gota.curva(p(70, 120), 48, new Gota.Vec(110, 0), p(70, 120), 24, new Gota.Vec(110, 0), recto)));
        c.add(new Caso("Casi coincidentes, radios iguales", "r = 60, d = 4 px (0,1 unidades).\nDebería parecerse al idéntico.",
                () -> Gota.lineal(p(118, 120), 60, p(122, 120), 60, recto)));
        c.add(new Caso("Casi coincidentes, radios 2 y 1", "d = 4 px: k enorme.\nDebería parecerse a centros iguales.",
                () -> Gota.lineal(p(118, 120), 2 * u, p(122, 120), 1 * u, recto)));
        c.add(new Caso("Elipse idéntica", "Centros y radios iguales, aspecto 1,6:\nuna elipse con dos tapas.",
                () -> Gota.lineal(p(120, 120), 40, p(120, 120), 40, recto.aspecto(1.6))));
        c.add(new Caso("Elipses, centros iguales, radios 60 y 30", "Aspecto 1,4; el mismo centro.\nEl caso para la adaptación elíptica.",
                () -> Gota.lineal(p(120, 120), 60, p(120, 120), 30, recto.aspecto(1.4))));
        return c;
    }

    // ---------- Las hojas ----------

    static final String[] DESC_MODO = {
        "Líneas de t constante perpendiculares al camino.\nTapas: semidiscos.",
        "Contorno - eje - contorno en recta.\nTapas: sectores polares.",
        "Como la espiga con Hermite en cada mitad."
    };

    static void hoja(String archivo, String titulo, List<Caso> casos, int w, int h, Relleno relleno) throws Exception {
        int ml = 300, mt = 120;
        BufferedImage hoja = new BufferedImage(ml + 3 * w, mt + casos.size() * h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        g.setColor(new Color(FONDO)); g.fillRect(0, 0, hoja.getWidth(), hoja.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(TEXTO));
        g.setFont(new Font("SansSerif", Font.BOLD, 20)); g.drawString(titulo, 14, 30);
        for (int c = 0; c < 3; c++) {
            g.setFont(new Font("SansSerif", Font.BOLD, 16)); g.drawString(MODOS[c].name(), ml + c * w + 10, 62);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12)); texto(g, DESC_MODO[c], ml + c * w + 10, 80);
        }
        for (int f = 0; f < casos.size(); f++) {
            Caso caso = casos.get(f);
            g.setColor(new Color(TEXTO));
            g.setFont(new Font("SansSerif", Font.BOLD, 13)); texto(g, caso.titulo(), 14, mt + f * h + h / 2 - 20);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12)); texto(g, caso.descripcion(), 14, mt + f * h + h / 2 + 2);
            for (int c = 0; c < 3; c++) {
                BufferedImage celda = celda(caso, w, h, new Opciones(MODOS[c], Solape.PLANO, Tira.POR_DEFECTO), relleno);
                g.drawImage(celda, ml + c * w, mt + f * h, null);
            }
        }
        g.dispose();
        ImageIO.write(hoja, "png", new File(archivo));
    }

    /** Una celda: la gota pintada, o una cruz roja con la excepción si el render falla. */
    static BufferedImage celda(Caso caso, int w, int h, Opciones op, Relleno relleno) {
        BufferedImage celda = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gc = celda.createGraphics();
        gc.setColor(new Color(FONDO)); gc.fillRect(0, 0, w, h);
        try {
            caso.gota().get().render(celda, relleno, op);
        } catch (RuntimeException ex) {
            gc.setColor(Color.RED); gc.drawLine(0, 0, w, h); gc.drawLine(0, h, w, 0);
            gc.setFont(new Font("SansSerif", Font.PLAIN, 11)); gc.drawString(String.valueOf(ex), 6, 14);
        }
        gc.dispose();
        return celda;
    }

    static void texto(Graphics2D g, String s, int x, int y) {
        int lh = g.getFontMetrics().getHeight();
        for (String l : s.split("\n")) { g.drawString(l, x, y); y += lh; }
    }

    // ---------- Barrido: la distancia entre centros tiende a 0 ----------

    static void barrido(String archivo, Relleno relleno) throws Exception {
        double[] ds = { 72, 36, 18, 7.2, 1.8, 0 };
        int w = 200, h = 200, ml = 230, mt = 70;
        String[] filas = { "Radios iguales (r = 48)", "Radios 60 y 36" };
        BufferedImage hoja = new BufferedImage(ml + ds.length * w, mt + 2 * MODOS.length * h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        g.setColor(new Color(FONDO)); g.fillRect(0, 0, hoja.getWidth(), hoja.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(TEXTO));
        g.setFont(new Font("SansSerif", Font.BOLD, 20)); g.drawString("Barrido: la distancia entre centros tiende a 0", 14, 30);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        for (int k = 0; k < ds.length; k++) g.drawString(String.format("d = %.1f px", ds[k]), ml + k * w + 10, 58);
        int fila = 0;
        for (int caso = 0; caso < 2; caso++) {
            for (Costura modo : MODOS) {
                g.setColor(new Color(TEXTO));
                g.setFont(new Font("SansSerif", Font.BOLD, 13));
                texto(g, filas[caso] + "\n" + modo, 14, mt + fila * h + h / 2 - 8);
                for (int k = 0; k < ds.length; k++) {
                    double d = ds[k], r1 = caso == 0 ? 48 : 60, r2 = caso == 0 ? 48 : 36;
                    Caso c = new Caso("", "", () -> Gota.lineal(p(100 - d / 2, 100), r1, p(100 + d / 2, 100), r2, Perfil.recto()));
                    g.drawImage(celda(c, w, h, new Opciones(modo, Solape.PLANO, Tira.POR_DEFECTO), relleno), ml + k * w, mt + fila * h, null);
                }
                fila++;
            }
        }
        g.dispose();
        ImageIO.write(hoja, "png", new File(archivo));
    }

    // ---------- Informe numérico ----------

    /** Recuento sobre los píxeles de la gota; no corrige ni interpreta nada. */
    static String analiza(Gota g, Opciones op) {
        Cortes k = op.tira().cortes(g);
        double[] t0 = g.tangente(0), t1 = g.tangente(1);
        double cx0 = g.cx(0), cy0 = g.cy(0), cx1 = g.cx(1), cy1 = g.cy(1);
        var b = g.bounds();
        int x0 = (int) Math.floor(b.getMinX()), x1 = (int) Math.ceil(b.getMaxX());
        int y0 = (int) Math.floor(b.getMinY()), y1 = (int) Math.ceil(b.getMaxY());
        int dentro = 0, nan = 0, fuera = 0, ta = 0, cu = 0, td = 0, atras = 0, atras1 = 0, delante = 0, delante2 = 0;
        for (int j = y0; j < y1; j++)
            for (int i = x0; i < x1; i++) {
                double x = i + 0.5, y = j + 0.5;
                if (!g.contiene(x, y)) continue;
                dentro++;
                Coordenadas.Uv uv = g.uv(x, y, op);
                boolean malo = Double.isNaN(uv.u()) || Double.isNaN(uv.v());
                boolean aT = (x - cx0) * t0[0] + (y - cy0) * t0[1] < 0, aD = (x - cx1) * t1[0] + (y - cy1) * t1[1] > 0;
                if (aT) atras++;
                if (aD) delante++;
                if (malo) { nan++; continue; }
                if (uv.u() < -1e-6 || uv.u() > 1 + 1e-6 || uv.v() < -1e-6 || uv.v() > 1 + 1e-6) fuera++;
                if (uv.v() < k.c1()) { ta++; if (aT) atras1++; }
                else if (uv.v() > k.c3()) { td++; if (aD) delante2++; }
                else cu++;
            }
        int bien = Math.max(1, dentro - nan);
        return String.format("dentro %6d | NaN %6d | fuera[0,1] %5d | tapa1 %5.1f%% cuerpo %5.1f%% tapa2 %5.1f%% | detrás de C1 en tapa1 %5.1f%% | delante de C2 en tapa2 %5.1f%%",
                dentro, nan, fuera, 100.0 * ta / bien, 100.0 * cu / bien, 100.0 * td / bien,
                atras == 0 ? Double.NaN : 100.0 * atras1 / atras, delante == 0 ? Double.NaN : 100.0 * delante2 / delante);
    }

    static void informe(List<Caso> casos, PrintStream out) {
        for (Caso c : casos) {
            out.println("== " + c.titulo());
            for (Solape s : Solape.values())
                for (Costura m : MODOS) {
                    String linea;
                    try {
                        linea = analiza(c.gota().get(), new Opciones(m, s, Tira.POR_DEFECTO));
                    } catch (RuntimeException ex) {
                        linea = "EXCEPCIÓN " + ex;
                    }
                    out.printf("   %-5s %-10s %s%n", s, m, linea);
                }
        }
    }

    public static void main(String[] args) throws Exception {
        List<Caso> casos = casos();
        Relleno texturas = Texturas.tira(new CosturasDemo.Guia());
        Relleno degradado = Colores.cadenaLateral(
                new Colores.Lateral(0xFFD7263D, 0xFFFFE9A8, 0xFF2D6CDF),
                new Colores.Lateral(0xFF1FA39A, 0xFFE8F5D0, 0xFFF28E2B), Mezcla.RGB);
        hoja("degeneradas_texturas.png", "Casos degenerados: texturas", casos, 240, 240, texturas);
        hoja("degeneradas_degradados.png", "Casos degenerados: degradados", casos, 240, 240, degradado);
        barrido("degeneradas_barrido.png", texturas);
        try (PrintStream out = new PrintStream(new File("degeneradas_informe.txt"), "UTF-8")) {
            informe(casos, out);
        }
        informe(casos, System.out);
        System.out.println("OK: degeneradas_texturas.png, degeneradas_degradados.png, degeneradas_barrido.png, degeneradas_informe.txt");
    }
}
