package coordenadas;

import forma.*;

/**
 * Estudio (rama estudio_pliegues_polar, issue #29; no está en Todas): salto de (u, v) en los bordes de las zonas de los casos de tres lóbulos (t3a, t3b),
 * con {@link EstudioPliegueBordes#mide}. Uso: EstudioPliegueBordesT3 [ortogonal|espiga]; el modo de zonas anidadas se elige con -Dgota.anidadas=interior|espina|no|cortar.
 */
public class EstudioPliegueBordesT3 {
    public static void main(String[] a) {
        String cos = a.length > 0 ? a[0].toUpperCase() : "ORTOGONAL";
        Opciones op = Opciones.POR_DEFECTO.conCostura(Costura.valueOf(cos)).conSolape(Solape.PLIEGUE);
        double[][][] casos = { {{1.0}, {-2.74, 2.77, 2.75, 1.49, 0.79, 1.64, 1.62, 1.69}}, {{1.39}, {-0.28, -2.39, -2.98, 1.88, -1.45, -1.6, -2.26, 1.54}} };
        String[] et = {"t3a", "t3b"};
        System.out.println("== " + cos + " anidadas=" + Pliegue.ANIDADAS);
        for (int k = 0; k < casos.length; k++) {
            EstudioPliegueDos.ajusta(casos[k][0][0], casos[k][1], et[k]);
            Gota g = EstudioPliegueDos.gota(casos[k][0][0], casos[k][0][0]);
            Coordenadas c = Coordenadas.de(g, op);
            EstudioPliegueBordes.mide(g, c, et[k] + " r=" + casos[k][0][0], 0.6);
            espina(g, c, et[k], 0.6);
        }
    }

    /**
     * Salto de (u, v) a través de la espina dentro del tramo de cada zona: del lado interior (zona de pliegue) al exterior (banda, donde ninguna zona cubre el píxel).
     * Es la frontera nueva del modo {@code espina}; con {@code interior} solo existe en las zonas anidadas.
     */
    static void espina(Gota g, Coordenadas c, String et, double d) {
        int i = 0;
        for (Pliegue z : c.zonasPliegue()) {
            double maxU = 0, maxV = 0, sumU = 0, sumV = 0, nat = 0; int n = 0;
            for (int k = 1; k < 200; k++) {
                double t = z.ta + (z.tb - z.ta) * k / 200, cx = g.cx(t), cy = g.cy(t);
                double[] tg = g.tangente(t); double nx = tg[1], ny = -tg[0];       // N = (Ty, -Tx)
                for (double sg : new double[] { 1, -1 }) {
                    Coordenadas.Costurado in = c.pliegue(cx + sg * d * nx, cy + sg * d * ny), in2 = c.pliegue(cx + sg * 3 * d * nx, cy + sg * 3 * d * ny);
                    if (in == null || in2 == null || !g.mascara().contiene(cx - sg * d * nx, cy - sg * d * ny)) continue;
                    if (c.pliegue(cx - sg * d * nx, cy - sg * d * ny) != null) continue;                      // el otro lado también es zona
                    Coordenadas.Costurado out = c.costurado(cx - sg * d * nx, cy - sg * d * ny);
                    double du = Math.abs(in.uv().u() - out.uv().u()), dv = Math.abs(in.uv().v() - out.uv().v());
                    maxU = Math.max(maxU, du); maxV = Math.max(maxV, dv); sumU += du; sumV += dv; nat += Math.abs(in.uv().v() - in2.uv().v()); n++;
                }
            }
            System.out.printf("%s zona %d (ta=%.3f tb=%.3f) espina: %3d puntos | |Δu| medio %.3f máx %.3f | |Δv| medio %.4f máx %.4f | control dentro: medio %.4f%n",
                    et, i++, z.ta, z.tb, n, n == 0 ? 0 : sumU / n, maxU, n == 0 ? 0 : sumV / n, maxV, n == 0 ? 0 : nat / n);
        }
    }
}
