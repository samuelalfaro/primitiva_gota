import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.imageio.ImageIO;
/**
 * Estudio (rama estudio_pliegues_polar, issue #29; no está en Todas): píxeles cubiertos por dos zonas de pliegue a la vez (cuñas ORTOGONAL que se pisan)
 * en la cúbica `dos`. Para cada regla de prioridad mide el salto de (t, q) entre vecinos de 4 donde cambia la zona ganadora, frente a la variación natural
 * entre vecinos de la misma zona. Escribe estudio_pliegue_solape.png (azul: solo A, rojo: solo B, verde: las dos).
 */
public class EstudioPliegueSolape {
    interface Regla { int gana(double[] a, double[] b, Pliegue za, Pliegue zb); }
    public static void main(String[] args) throws Exception {
        double r = 0.88;
        EstudioPliegueDos.ajusta(r, new double[]{0, 0, 3.19, -1.72, -0.57, -0.49, 4.74, -2.08}, "dos");
        Gota g = EstudioPliegueDos.gota(r, r);
        Opciones op = Opciones.POR_DEFECTO.conCostura(Costura.ORTOGONAL).conSolape(Solape.PLIEGUE);
        List<Pliegue> zs = Coordenadas.de(g, op).zonasPliegue();
        System.out.println("zonas: " + zs.size());
        Pliegue A = zs.get(0), B = zs.get(1);
        System.out.printf("A ta=%.3f tb=%.3f lado=%d | B ta=%.3f tb=%.3f lado=%d%n", A.ta, A.tb, A.lado, B.ta, B.tb, B.lado);
        final double sA = signoInterior(g, A), sB = signoInterior(g, B);
        System.out.printf("signo de q en la mitad interior: A %.0f, B %.0f%n", sA, sB);
        int W = EstudioPliegueDos.CW, H = EstudioPliegueDos.CH;
        double[][][] ca = new double[H][W][], cb = new double[H][W][];
        int soloA = 0, soloB = 0, ambas = 0;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            double px = x + 0.5, py = y + 0.5; int col = 0xFFFFFF;
            if (g.mascara().contiene(px, py)) {
                ca[y][x] = A.localiza(px, py); cb[y][x] = B.localiza(px, py);
                if (ca[y][x] != null && cb[y][x] != null) { ambas++; col = 0x40C040; }
                else if (ca[y][x] != null) { soloA++; col = 0x6080FF; }
                else if (cb[y][x] != null) { soloB++; col = 0xFF6060; }
                else col = 0xDDDDDD;
            }
            img.setRGB(x, y, col);
        }
        ImageIO.write(img, "png", new java.io.File("estudio_pliegue_solape.png"));
        System.out.printf("píxeles: solo A %d | solo B %d | ambas %d%n", soloA, soloB, ambas);
        // diferencia entre las dos soluciones donde se pisan
        double sdt = 0, mdt = 0, sdq = 0, mdq = 0, tAmayor = 0; int n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (ca[y][x] != null && cb[y][x] != null) {
            double dt = Math.abs(ca[y][x][0] - cb[y][x][0]), dq = Math.abs(ca[y][x][1] - cb[y][x][1]) / 2;
            sdt += dt; mdt = Math.max(mdt, dt); sdq += dq; mdq = Math.max(mdq, dq); n++; if (ca[y][x][0] > cb[y][x][0]) tAmayor++;
        }
        if (n > 0) System.out.printf("donde se pisan: |Δt| medio %.3f máx %.3f | |Δu| medio %.3f máx %.3f | tA > tB en %.0f %%%n", sdt / n, mdt, sdq / n, mdq, 100 * tAmayor / n);
        int ii = 0, io = 0, oo = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (ca[y][x] != null && cb[y][x] != null) {
            boolean ia = ca[y][x][1] * sA > 0, ib = cb[y][x][1] * sB > 0;
            if (ia && ib) ii++; else if (ia || ib) io++; else oo++;
        }
        System.out.printf("pixeles que se pisan: interior en las dos %d | interior en una y exterior en otra %d | exterior en las dos %d%n", ii, io, oo);
        String[] nom = {"primera (A)", "última (B)", "t mayor", "t menor", "|q| menor (más cerca de su X)", "|q| mayor", "mitad interior manda (q·signo mayor)"};
        Regla[] reglas = {
            (a, b, za, zb) -> 0, (a, b, za, zb) -> 1,
            (a, b, za, zb) -> a[0] >= b[0] ? 0 : 1, (a, b, za, zb) -> a[0] <= b[0] ? 0 : 1,
            (a, b, za, zb) -> Math.abs(a[1]) <= Math.abs(b[1]) ? 0 : 1, (a, b, za, zb) -> Math.abs(a[1]) >= Math.abs(b[1]) ? 0 : 1,
            (a, b, za, zb) -> a[1] * sA >= b[1] * sB ? 0 : 1 };
        for (int k = 0; k < reglas.length; k++) {
            double sw = 0, swMax = 0, nat = 0, natMax = 0; int ns = 0, nn = 0;
            double[][][] w = new double[H][W][];
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                double[] a = ca[y][x], b = cb[y][x];
                if (a == null && b == null) continue;
                w[y][x] = a == null ? withId(b, 1) : b == null ? withId(a, 0) : withId(reglas[k].gana(a, b, A, B) == 0 ? a : b, reglas[k].gana(a, b, A, B));
            }
            for (int y = 0; y < H - 1; y++) for (int x = 0; x < W - 1; x++) for (int d = 0; d < 2; d++) {
                double[] p = w[y][x], q = d == 0 ? w[y][x + 1] : w[y + 1][x];
                if (p == null || q == null) continue;
                double dist = Math.abs(p[0] - q[0]) + Math.abs(p[1] - q[1]) / 2;
                if (p[2] != q[2]) { sw += dist; swMax = Math.max(swMax, dist); ns++; } else { nat += dist; natMax = Math.max(natMax, dist); nn++; }
            }
            System.out.printf("%-34s cambio de zona: %4d pares, salto medio %.4f máx %.4f | misma zona: medio %.4f máx %.4f%n", nom[k], ns, ns == 0 ? 0 : sw / ns, swMax, nn == 0 ? 0 : nat / nn, natMax);
        }
    }
    /** Signo de q en el píxel de la zona más cercano a X (que está en la mitad interior). */
    static double signoInterior(Gota g, Pliegue z) {
        double best = 1e9, sg = 1;
        for (int y = 0; y < EstudioPliegueDos.CH; y++) for (int x = 0; x < EstudioPliegueDos.CW; x++) {
            double[] q = z.localiza(x + 0.5, y + 0.5);
            if (q == null) continue;
            double d = Math.hypot(x + 0.5 - z.xx, y + 0.5 - z.xy);
            if (d < best) { best = d; sg = Math.signum(q[1]); }
        }
        return sg;
    }
    static double[] withId(double[] a, int id) { return new double[]{a[0], a[1], id}; }
}
