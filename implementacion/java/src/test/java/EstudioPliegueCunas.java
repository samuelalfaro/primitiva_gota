import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.imageio.ImageIO;
/**
 * Estudio (rama estudio_pliegues_polar, issue #29; no está en Todas): diagnóstico de cuñas ORTOGONAL anidadas. Dibuja cada zona de pliegue por separado,
 * todas superpuestas (el color mezcla las que se pisan) y el mapa de la zona ganadora con la regla actual.
 * Uso: EstudioPliegueCunas r x0 y0 x1 y1 x2 y2 x3 y3 etiqueta [espiga]
 */
public class EstudioPliegueCunas {
    public static void main(String[] args) throws Exception {
        double r = Double.parseDouble(args[0]);
        double[] pts = new double[8]; for (int i = 0; i < 8; i++) pts[i] = Double.parseDouble(args[1 + i]);
        EstudioPliegueDos.ajusta(r, pts, args[9]);
        Costura cos = args.length > 10 ? Costura.ESPIGA : Costura.ORTOGONAL;
        Gota g = EstudioPliegueDos.gota(r, r);
        Opciones op = Opciones.POR_DEFECTO.conCostura(cos).conSolape(Solape.PLIEGUE);
        Coordenadas c = Coordenadas.de(g, op);
        List<Pliegue> zs = c.zonasPliegue();
        int n = zs.size(), W = EstudioPliegueDos.CW, H = EstudioPliegueDos.CH;
        Color[] pal = {new Color(0x2060FF), new Color(0xFF3030), new Color(0x20B020), new Color(0xE0A000)};
        boolean[][][] cub = new boolean[n][H][W]; int[] cnt = new int[n]; int[][] par = new int[n][n]; int multi = 0, algo = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (!g.mascara().contiene(x + 0.5, y + 0.5)) continue;
            int k = 0;
            for (int i = 0; i < n; i++) if (Pliegue.localiza(zs, zs.get(i), x + 0.5, y + 0.5) != null) { cub[i][y][x] = true; cnt[i]++; k++; for (int j = 0; j < i; j++) if (cub[j][y][x]) par[j][i]++; }
            if (k > 0) algo++; if (k > 1) multi++;
        }
        System.out.printf("%s | zonas: %d | píxeles con alguna zona %d, con varias %d%n", cos, n, algo, multi);
        for (int i = 0; i < n; i++) {
            Pliegue z = zs.get(i);
            System.out.printf("  zona %d: ta=%.3f tb=%.3f lado=%d X=(%.0f,%.0f) píxeles %d%n", i, z.ta, z.tb, z.lado, z.xx, z.xy, cnt[i]);
        }
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) System.out.printf("  se pisan %d y %d: %d px%n", i, j, par[i][j]);
        int paneles = n + 2, S = 2;
        BufferedImage img = new BufferedImage(paneles * W * S, H * S, BufferedImage.TYPE_INT_RGB);
        Graphics2D gg = img.createGraphics(); gg.setColor(Color.WHITE); gg.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int p = 0; p < paneles; p++) for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int col = 0xFFFFFF;
            if (g.mascara().contiene(x + 0.5, y + 0.5)) {
                col = 0xDDDDDD;
                if (p < n) { if (cub[p][y][x]) col = pal[p % 4].getRGB(); }
                else if (p == n) {
                    int r0 = 0, g0 = 0, b0 = 0, k = 0;
                    for (int i = 0; i < n; i++) if (cub[i][y][x]) { r0 += pal[i % 4].getRed(); g0 += pal[i % 4].getGreen(); b0 += pal[i % 4].getBlue(); k++; }
                    if (k > 0) col = new Color(r0 / k, g0 / k, b0 / k).getRGB();
                    if (k > 1) col = 0x000000;
                } else {
                    Coordenadas.Costurado cs = c.pliegue(x + 0.5, y + 0.5);
                    if (cs != null) {
                        int best = -1; double bq = -9;
                        for (int i = 0; i < n; i++) { double[] q = cub[i][y][x] ? Pliegue.localiza(zs, zs.get(i), x + 0.5, y + 0.5) : null; if (q != null) { double s = zs.get(i).interior(q) ? 1 + Math.abs(q[1]) : Math.abs(q[1]); if (s > bq) { bq = s; best = i; } } }
                        col = best < 0 ? 0xDDDDDD : pal[best % 4].getRGB();
                    }
                }
            }
            for (int dy = 0; dy < S; dy++) for (int dx = 0; dx < S; dx++) img.setRGB(p * W * S + x * S + dx, y * S + dy, col);
        }
        gg.setColor(Color.BLACK);
        for (int p = 0; p < paneles; p++) {
            String t = p < n ? "zona " + p : p == n ? "todas (negro: se pisan)" : "ganadora";
            gg.drawString(t, p * W * S + 6, 14);
            for (Pliegue z : zs) gg.fillOval(p * W * S + (int) (z.xx * S) - 3, (int) (z.xy * S) - 3, 6, 6);
        }
        gg.dispose();
        ImageIO.write(img, "png", new java.io.File("estudio_pliegue_cunas_" + args[9] + "_" + cos.name().toLowerCase() + ".png"));
    }
}
