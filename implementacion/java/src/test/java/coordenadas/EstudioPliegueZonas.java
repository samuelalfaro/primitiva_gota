package coordenadas;

import forma.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
/**
 * Estudio (rama estudio_pliegues_polar, issue #29; no está en Todas): mapa de zonas del codo con la costura ORTOGONAL. Arriba, TRAZO (en rosa, la banda con t
 * entre ta y tb, que es la plegada); abajo, PLIEGUE: tapa (verde), banda antes de ta (azul), polar (morado), banda tras tb (naranja), tapa (verde oscuro).
 * Escribe estudio_pliegue_zonas_ortogonal.png (cada píxel ampliado 3 veces).
 */
public class EstudioPliegueZonas {
    public static void main(String[] a) throws Exception {
        double[][] p = {{3.5, 1.6, 1.0}, {2.5, 1.6, 1.0}, {3.5, 1.6, 1.6}};
        int CW = 300, CH = 260, K = 3;
        Opciones op = Opciones.POR_DEFECTO.conCostura(Costura.ORTOGONAL).conSolape(Solape.PLIEGUE);
        BufferedImage out = new BufferedImage(p.length * CW * K, 2 * CH * K, BufferedImage.TYPE_INT_RGB);
        for (int ci = 0; ci < p.length; ci++) {
            Gota g = EstudioCasosBasicos.codo(p[ci][0], p[ci][1], p[ci][2], CW * .62, CH * .62);
            Coordenadas c = Coordenadas.de(g, op); Pliegue z = c.zonaPliegue();
            int[] cnt = new int[8];
            for (int y = 0; y < CH; y++) for (int x = 0; x < CW; x++) {
                double px = x + .5, py = y + .5; int col = 0xFFFFFF, col2 = 0xFFFFFF;
                if (g.mascara().contiene(px, py)) {
                    var cands = c.candidatos(px, py);
                    Coordenadas.Costurado w = Solape.TRAZO.ganador(c, px, py);
                    col = cat(w, z, false); 
                    Coordenadas.Costurado w2 = Solape.PLIEGUE.ganador(c, px, py);
                    col2 = (z != null && c.pliegue(px, py) != null) ? 0xAA33CC : cat(w2, z, false);
                    if (z != null && c.pliegue(px, py) != null && cands.size() > 1) { cnt[0]++; }
                }
                for (int dy = 0; dy < K; dy++) for (int dx = 0; dx < K; dx++) { out.setRGB(ci * CW * K + x * K + dx, y * K + dy, col); out.setRGB(ci * CW * K + x * K + dx, (CH + y) * K + dy, col2); }
            }
            System.out.printf("codo a=%.1f r1=%.1f r2=%.1f %s%n", p[ci][0], p[ci][1], p[ci][2], z);
        }
        ImageIO.write(out, "png", new java.io.File("estudio_pliegue_zonas_ortogonal.png"));
    }
    /** colores: tapa1 verde, tapa2 verde oscuro, banda antes de ta azul claro, polar morado, banda tras tb naranja, banda sin zona gris */
    static int cat(Coordenadas.Costurado w, Pliegue z, boolean pl) {
        if (w == null) return 0x000000;
        double t = w.t();
        if (t == 0) return 0x66CC33; if (t == 1) return 0x228822;
        if (z == null) return 0xBBBBBB;
        if (t < z.ta) return 0x99CCFF; if (t > z.tb) return 0xFFAA33;
        return pl ? 0xAA33CC : 0xFF5577;     // dentro de [ta, tb]: en TRAZO es la banda plegada (rojo); en PLIEGUE, la polar (morado)
    }
}
