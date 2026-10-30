import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Textura en tira. Pinta seis gotas con una textura de prueba y comprueba:
 * rango de (u, v), continuidad entre píxeles vecinos y puntos clave (centros, mitad y puntas).
 */
public class GotaTextura {

    static Point2D.Double p(int i, double x, double y) {
        return new Point2D.Double((i % 3) * 300 + x, (i / 3) * 300 + y);
    }

    static double frac(double v) { return v - Math.floor(v); }

    /** Cuatro bandas de color; puntos = número de banda; triángulo negro en (u=0, arriba); recta blanca en u=0.5. */
    static BufferedImage texturaPrueba() {
        int n = 512;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        int[] base = { 0xF4A58A, 0x7FD1B9, 0x8EB8F0, 0xF2C46D };
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                double u = (px + 0.5) / n, v = (py + 0.5) / n;
                int banda = Math.min(3, (int) (v * 4));
                double bs = banda * 0.25;
                double k = 1 - 0.35 * u;                                   // u crece hacia la derecha: más oscuro
                if (frac(u * 16) < 0.07 || frac(v * 16) < 0.07) k *= 0.65; // rejilla
                int r = (int) (((base[banda] >> 16) & 0xFF) * k);
                int g = (int) (((base[banda] >> 8) & 0xFF) * k);
                int b = (int) ((base[banda] & 0xFF) * k);
                boolean negro = Math.abs(v - 0.25) < 0.005 || Math.abs(v - 0.5) < 0.005
                        || Math.abs(v - 0.75) < 0.005;
                double dv = v - bs;
                if (dv < 0.10 && u < 0.10 - dv) negro = true;
                int pts = banda + 1;
                for (int i = 0; i < pts; i++) {
                    double cu = 0.5 + (i - (pts - 1) / 2.0) * 0.10;
                    if (Math.hypot(u - cu, v - (bs + 0.125)) < 0.03) negro = true;
                }
                if (negro) { r = g = b = 20; }
                else if (Math.abs(u - 0.5) < 0.004) { r = g = b = 250; }
                img.setRGB(px, py, 0xFF000000 | r << 16 | g << 8 | b);
            }
        }
        return img;
    }

    public static void main(String[] args) throws Exception {
        String[] nombres = { "lineal h=1", "hinchada h=1.8", "guitarra h=0.55",
                             "arco", "punta r2=0", "lazo (TRAZO)" };
        Gota[] g = new Gota[6];
        g[0] = Gota.lineal(p(0, 60, 150), 50, p(0, 240, 150), 20, Gota.Perfil.central(1.0));
        g[1] = Gota.lineal(p(1, 60, 150), 50, p(1, 240, 150), 20, Gota.Perfil.central(1.8));
        g[2] = Gota.lineal(p(2, 60, 150), 50, p(2, 240, 150), 20, Gota.Perfil.central(0.55));
        g[3] = Gota.curva(p(3, 60, 240), 35, new Gota.Vec(0, -200),
                          p(3, 240, 240), 15, new Gota.Vec(0, -200), Gota.Perfil.central(1.0));
        g[4] = Gota.lineal(p(4, 90, 150), 60, p(4, 250, 150), 0, Gota.Perfil.central(1.0));
        g[5] = Gota.curva(p(5, 80, 230), 40, new Gota.Vec(180, -230),
                          p(5, 220, 230), 30, new Gota.Vec(-180, -230), Gota.Perfil.central(1.0)).conSolape(Gota.Solape.TRAZO);

        BufferedImage tex = texturaPrueba();
        ImageIO.write(tex, "png", new File("gota_textura_base.png"));
        Gota.Relleno rel = Texturas.textura(Texturas.deImagen(tex));

        BufferedImage img = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) img.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);

        boolean ok = true;
        for (int i = 0; i < g.length; i++) {
            long t0 = System.nanoTime();
            g[i].render(img, rel);
            double ms = (System.nanoTime() - t0) / 1e6;
            boolean estricto = i < 5;
            ok &= revisa(nombres[i], g[i], ms, estricto, i != 5, i != 4, i == 1 ? 0.30 : 0.15, i == 4);
        }
        ImageIO.write(img, "png", new File("gota_textura.png"));

        // Reparto proporcional de la tira: solo cambian los cortes
        // Pesos 50 : largo/2 : largo/2 : 20 sobre la gota recta, así que los cortes son v1 = 50/total y v2 = (50 + largo/2)/total
        Gota prop = g[0].conTiraProporcional();
        double largo = g[0].centro(0).distance(g[0].centro(1)), total = 50 + largo + 20;
        double e1 = 50 / total, e2 = (50 + largo / 2) / total, e3 = (50 + largo) / total;
        Point2D c0 = prop.centro(0), cm = prop.centro(0.5), c1 = prop.centro(1);
        double a1 = prop.uv(c0.getX(), c0.getY()).v(), a2 = prop.uv(cm.getX(), cm.getY()).v(),
               a3 = prop.uv(c1.getX(), c1.getY()).v();
        boolean okProp = Math.abs(a1 - e1) < 0.01 && Math.abs(a2 - e2) < 0.01 && Math.abs(a3 - e3) < 0.01;
        System.out.printf("proporcional: cortes v=(%.3f, %.3f, %.3f), esperados (%.3f, %.3f, %.3f) %s%n",
                a1, a2, a3, e1, e2, e3, okProp ? "ok" : "REVISAR");
        ok &= okProp;

        System.out.println(ok ? "OK" : "HAY PROBLEMAS");
    }

    /** Devuelve true si todo va bien. Los saltos y las puntas solo se exigen en las formas sin cruces. */
    static boolean revisa(String nombre, Gota g, double ms, boolean estricto, boolean puntas, boolean puntaFrente,
                          double umbral, boolean excluirPunta) {
        var b = g.bounds();
        int x0 = (int) Math.floor(b.getMinX()), x1 = (int) Math.ceil(b.getMaxX());
        int y0 = (int) Math.floor(b.getMinY()), y1 = (int) Math.ceil(b.getMaxY());
        int w = x1 - x0, h = y1 - y0;
        double[][] U = new double[h][w], V = new double[h][w];
        int dentro = 0, fuera = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                U[y][x] = V[y][x] = Double.NaN;
                double px = x0 + x + 0.5, py = y0 + y + 0.5;
                if (!g.contiene(px, py)) continue;
                Coordenadas.Uv uv = g.uv(px, py);
                U[y][x] = uv.u(); V[y][x] = uv.v();
                dentro++;
                if (uv.u() < -1e-9 || uv.u() > 1 + 1e-9 || uv.v() < -1e-9 || uv.v() > 1 + 1e-9
                        || Double.isNaN(uv.u()) || Double.isNaN(uv.v())) fuera++;
            }
        }
        int saltos = 0;
        double maxSalto = 0;
        int sx = 0, sy = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (Double.isNaN(U[y][x])) continue;
                for (int d = 0; d < 2; d++) {
                    int xx = x + d, yy = y + 1 - d;
                    if (xx >= w || yy >= h || Double.isNaN(U[yy][xx])) continue;
                    if (excluirPunta) {      // un cono tiene una singularidad en la punta: u recorre [0,1] en un ancho que tiende a 0
                        Point2D e = g.centro(1);   // aquí el ancho es ~0.75 d px, así que el salto por píxel supera 0.15 hasta d ~ 9 px
                        if (Math.hypot(x0 + x + 0.5 - e.getX(), y0 + y + 0.5 - e.getY()) < 10) continue;
                    }
                    double s = Math.max(Math.abs(U[yy][xx] - U[y][x]), Math.abs(V[yy][xx] - V[y][x]));
                    if (s > maxSalto) { maxSalto = s; sx = x0 + x; sy = y0 + y; }
                    if (s > umbral) saltos++;
                }
            }
        }
        boolean bien = fuera == 0;
        String clave = "";
        if (estricto) {
            Point2D c0 = g.centro(0), cm = g.centro(0.5), c1 = g.centro(1);
            bien &= cerca(g.uv(c0.getX(), c0.getY()), 0.5, 0.25);
            bien &= cerca(g.uv(cm.getX(), cm.getY()), 0.5, 0.5);
            if (puntaFrente) bien &= cerca(g.uv(c1.getX(), c1.getY()), 0.5, 0.75);
            if (puntas) {
                Point2D d = g.centro(0.001);
                double tx = d.getX() - c0.getX(), ty = d.getY() - c0.getY(), n = Math.hypot(tx, ty);
                tx /= n; ty /= n;
                double r1 = g.radio(0) - 0.5;
                Coordenadas.Uv t1 = g.uv(c0.getX() - tx * r1, c0.getY() - ty * r1);
                bien &= t1.v() < 0.02 && Math.abs(t1.u() - 0.5) < 0.02;
                clave = String.format(" | punta trasera uv=(%.3f, %.3f)", t1.u(), t1.v());
                if (puntaFrente) {
                    Point2D e = g.centro(0.999);
                    double ux = c1.getX() - e.getX(), uy = c1.getY() - e.getY(), m = Math.hypot(ux, uy);
                    ux /= m; uy /= m;
                    double r2 = g.radio(1) - 0.5;
                    Coordenadas.Uv t2 = g.uv(c1.getX() + ux * r2, c1.getY() + uy * r2);
                    bien &= t2.v() > 0.98 && Math.abs(t2.u() - 0.5) < 0.02;
                    clave += String.format(" | punta delantera uv=(%.3f, %.3f)", t2.u(), t2.v());
                }
            }
            bien &= saltos == 0;
        }
        System.out.printf("%-16s %6.0f ms | dentro=%6d  fuera de [0,1]=%d  saltos>%.2f=%d (max %.3f en %d,%d)%s %s%n",
                nombre, ms, dentro, fuera, umbral, saltos, maxSalto, sx, sy, clave, bien ? "ok" : "REVISAR");
        return bien;
    }

    static boolean cerca(Coordenadas.Uv uv, double u, double v) {
        boolean ok = Math.abs(uv.u() - u) < 0.02 && Math.abs(uv.v() - v) < 0.02;
        if (!ok) System.out.printf("   punto clave: uv=(%.3f, %.3f), esperado (%.2f, %.2f)%n", uv.u(), uv.v(), u, v);
        return ok;
    }
}
