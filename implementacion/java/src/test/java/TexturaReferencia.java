import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Textura de referencia 1 x 2 (cápsula en cuatro cuartos) y los dos mapeos: en disco (idéntico a la textura en una
 * gota recta de radios iguales con centros a 2r) y en tira (también idéntico: las tapas son semidiscos).
 */
public class TexturaReferencia {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-84s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    static Point2D.Double p(double x, double y) { return new Point2D.Double(x, y); }
    static int[] datos(BufferedImage i) { return ((DataBufferInt) i.getRaster().getDataBuffer()).getData(); }

    static final int[] FONDOS = { 0xFFFFE0B2, 0xFFB3E5FC, 0xFFC8E6C9, 0xFFF8BBD0 };
    static final int LINEA = 0xFF37474F;

    static int maxCanal(int a, int b) {
        int m = 0;
        for (int s = 0; s <= 24; s += 8) m = Math.max(m, Math.abs(((a >> s) & 255) - ((b >> s) & 255)));
        return m;
    }

    static void texturaEnSi() {
        Texturas.Textura t = Texturas.referencia();
        comprueba("referencia: aspecto 1 x 2 (ancho/alto = 0.5)", t.aspecto() == 0.5, "");
        // fondos en el centro de cada cuarto, a distancia 0.1 del eje (entre las líneas de 0.05 y 0.15)
        double[][] pts = { { 0.5, 0.2 }, { 0.6, 0.375 }, { 0.6, 0.625 }, { 0.5, 0.8 } };
        boolean ok = true;
        for (int i = 0; i < 4; i++) if (t.muestrea(pts[i][0], pts[i][1]) != FONDOS[i]) ok = false;
        comprueba("referencia: los cuatro cuartos tienen su color de fondo", ok, "");
        // una circunferencia de la tapa continúa sin corte en la recta del rectángulo (radio 0.25: línea 3)
        int a = t.muestrea(0.75, (0.5 - 1e-7) / 2), b = t.muestrea(0.75, (0.5 + 1e-7) / 2);
        comprueba("referencia: la circunferencia de radio 0.25 sigue en la recta del rectángulo contiguo", a == LINEA && b == LINEA,
                String.format("%08X / %08X", a, b));
        int c = t.muestrea(0.75, (1.5 - 1e-7) / 2), d = t.muestrea(0.75, (1.5 + 1e-7) / 2);
        comprueba("referencia: lo mismo en la unión del rectángulo con la tapa de abajo", c == LINEA && d == LINEA, "");
        comprueba("referencia: las esquinas fuera del semicírculo son transparentes",
                (t.muestrea(0.02, 0.05) >>> 24) == 0 && (t.muestrea(0.98, 0.95) >>> 24) == 0, "");
        // simetrías: espejo en u, y tapa de abajo = tapa de arriba reflejada (misma silueta y mismas líneas)
        boolean sim = true;
        for (int i = 1; i < 60; i++) for (int j = 1; j < 120; j++) {
            double u = i / 60.0, v = j / 120.0;
            int x = t.muestrea(u, v), y = t.muestrea(1 - u, v);
            if ((x >>> 24) != (y >>> 24) || (x == LINEA) != (y == LINEA)) sim = false;
            int z = t.muestrea(u, 1 - v);
            if ((x >>> 24) != (z >>> 24) || (x == LINEA) != (z == LINEA)) sim = false;
        }
        comprueba("referencia: simétrica en u, y la tapa de abajo es la de arriba reflejada", sim, "");
        // con fondos a cada lado del eje: izquierda y derecha distintos en cada cuarto, las líneas no cambian
        Texturas.Textura tl = Texturas.referenciaLados();
        boolean lados = true;
        double[][] pl = { { 0.499, 0.2 }, { 0.4, 0.375 }, { 0.4, 0.625 }, { 0.499, 0.8 } };   // a distancia 0.1 del eje (entre líneas)
        int[] izq = { 0xFFFFE0B2, 0xFFB3E5FC, 0xFFC8E6C9, 0xFFF8BBD0 }, der = { 0xFFFF9E80, 0xFF80D8FF, 0xFFA5D6A7, 0xFFCE93D8 };
        for (int i = 0; i < 4; i++) {
            if (tl.muestrea(pl[i][0], pl[i][1]) != izq[i]) lados = false;
            if (tl.muestrea(1 - pl[i][0], pl[i][1]) != der[i]) lados = false;   // simétrico respecto a u = 1/2
        }
        boolean lineas = true;   // las líneas son las mismas que en la normal (alfa y presencia de línea)
        for (int i = 1; i < 60; i++) for (int j = 1; j < 120; j++) {
            double u = i / 60.0, v = j / 120.0;
            int x = tl.muestrea(u, v), y = t.muestrea(u, v);
            if ((x >>> 24) != (y >>> 24) || (x == LINEA) != (y == LINEA)) lineas = false;
        }
        comprueba("referenciaLados: fondo distinto a cada lado del eje en los cuatro cuartos", lados, "");
        comprueba("referenciaLados: la silueta y las líneas son las de la referencia", lineas, "");
        boolean lanza = false;
        try { Texturas.referencia(0, FONDOS, LINEA, 0.02); } catch (IllegalArgumentException e) { lanza = true; }
        comprueba("referencia: parámetros no válidos se rechazan", lanza, "");
    }

    static Gota recta(double cx, double cy, double r) {
        return Gota.lineal(p(cx, cy), r, p(cx, cy + 2 * r), r, Gota.Perfil.recto());
    }

    static void identidad() {
        Texturas.Textura tex = Texturas.referencia();
        double r = 40, cx = 100, cy = 100;
        Gota g = recta(cx, cy, r);
        BufferedImage disco = new BufferedImage(220, 300, BufferedImage.TYPE_INT_ARGB);
        BufferedImage tira = new BufferedImage(220, 300, BufferedImage.TYPE_INT_ARGB);
        g.render(disco, Rellenos.discoCapsula(tex));
        g.render(tira, Rellenos.tira(tex));
        int[] dd = datos(disco), dt = datos(tira);
        int nDentro = 0, mDisco = 0, mTira = 0, maxD = 0, maxT = 0;
        for (int j = 0; j < 300; j++)
            for (int i = 0; i < 220; i++) {
                double x = i + 0.5, y = j + 0.5;
                if (g.sdf(x, y) > -2) continue;
                nDentro++;
                int esperado = tex.muestrea((x - (cx - r)) / (2 * r), (y - (cy - r)) / (4 * r));
                int e1 = maxCanal(dd[j * 220 + i], esperado);
                if (e1 > 2) mDisco++;
                maxD = Math.max(maxD, e1);
                int e2 = maxCanal(dt[j * 220 + i], esperado);
                if (e2 > 2) mTira++;
                maxT = Math.max(maxT, e2);
            }
        comprueba("mapeo en disco: la gota recta reproduce la textura idéntica (error <= 2 por canal)", mDisco == 0,
                "píxeles " + nDentro + ", distintos " + mDisco + ", máx " + maxD);
        comprueba("tira (tapas de semidisco): la gota recta reproduce la textura idéntica, cuerpo y tapas", mTira == 0,
                "píxeles " + nDentro + ", distintos " + mTira + ", máx " + maxT);
        // lo mismo con los fondos a cada lado: la orientación (normal a la derecha) tiene que casar con la de la textura
        Texturas.Textura tl = Texturas.referenciaLados();
        BufferedImage il = new BufferedImage(220, 300, BufferedImage.TYPE_INT_ARGB);
        g.render(il, Rellenos.tira(tl));
        int[] dl = datos(il);
        int mL = 0;
        for (int j = 0; j < 300; j++)
            for (int i = 0; i < 220; i++) {
                double x = i + 0.5, y = j + 0.5;
                if (g.sdf(x, y) > -2) continue;
                if (maxCanal(dl[j * 220 + i], tl.muestrea((x - (cx - r)) / (2 * r), (y - (cy - r)) / (4 * r))) > 2) mL++;
            }
        comprueba("tira con fondos a cada lado: la gota recta reproduce la textura idéntica (sin giros ni espejos)", mL == 0, "distintos " + mL);
    }

    static void galeria() throws Exception {
        Texturas.Textura tex = Texturas.referencia();
        int W = 300, H = 340, FONDO = 0xFFF4F1EA;
        BufferedImage out = new BufferedImage(W * 5, H, BufferedImage.TYPE_INT_ARGB);
        int[] o = datos(out);
        java.util.Arrays.fill(o, FONDO);
        // 0: la textura en sí (150 x 300)
        for (int j = 0; j < 300; j++)
            for (int i = 0; i < 150; i++) {
                int c = tex.muestrea((i + 0.5) / 150, (j + 0.5) / 300);
                double a = (c >>> 24) / 255.0;
                int px = 0xFF000000;
                for (int s = 16; s >= 0; s -= 8)
                    px |= (int) Math.round(((FONDO >> s) & 255) * (1 - a) + ((c >> s) & 255) * a) << s;
                o[(20 + j) * (W * 5) + 75 + i] = px;
            }
        Gota recta = recta(150, 70, 50);                         // 2r = 100: la cápsula exacta
        Gota curva = Gota.curva(p(70, 70), 30, new Gota.Vec(120, 0), p(220, 250), 55, new Gota.Vec(0, -110), Gota.Perfil.recto());
        Gota.Relleno[] rel = { Rellenos.tira(tex), Rellenos.discoCapsula(tex) };
        Gota[] gs = { recta, curva };
        for (int k = 0; k < 4; k++) {
            BufferedImage panel = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            java.util.Arrays.fill(datos(panel), FONDO);
            gs[k / 2].render(panel, rel[k % 2]);
            out.getGraphics().drawImage(panel, (k + 1) * W, 0, null);
        }
        ImageIO.write(out, "png", new File("texturas_referencia.png"));
    }

    public static void main(String[] args) throws Exception {
        texturaEnSi();
        identidad();
        galeria();
        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
