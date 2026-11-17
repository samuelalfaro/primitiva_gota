import java.awt.geom.Point2D;
/**
 * Estudio (rama estudio_pliegues_polar, issue #29; no está en Todas): salto de las coordenadas (u, v) a través de las dos normales (ORTOGONAL) o
 * costillas (ESPIGA) que acotan la zona polar del pliegue, dentro (Solape.PLIEGUE) frente al candidato de banda más cercano por fuera.
 * Uso: EstudioPliegueBordes [costura: ortogonal|espiga]; con -Dgota.primeraSalida=true, la semianchura de la tira ORTOGONAL es la primera salida de la normal.
 */
public class EstudioPliegueBordes {
    public static void main(String[] a) {
        String cos = a.length > 0 ? a[0].toUpperCase() : "ORTOGONAL";
        double[][] p = {{3.5, 1.6, 1.6}, {3.5, 1.6, 1.0}, {3.5, 1.0, 1.6}, {2.5, 1.6, 1.6}, {2.5, 1.6, 1.0}};
        Opciones op = Opciones.POR_DEFECTO.conCostura(Costura.valueOf(cos)).conSolape(Solape.PLIEGUE);
        double d = 0.6;   // px a cada lado de la normal
        for (double[] q : p) {
            Gota g = EstudioCasosBasicos.codo(q[0], q[1], q[2], 150, 130);
            mide(g, Coordenadas.de(g, op), String.format("codo a=%.1f r1=%.1f r2=%.1f", q[0], q[1], q[2]), d);
        }
        // cúbicas con dos pliegues (rama de varios pliegues)
        double[][][] dos = { {{0.89}, {2.26, 2.39, 2.94, -1.0, -0.19, 2.96, 1.2, -2.66}}, {{0.65}, {-0.73, -1.34, -1.57, 2.79, 1.01, -0.21, 0.44, 2.07}},
                             {{0.88}, {0, 0, 3.19, -1.72, -0.57, -0.49, 4.74, -2.08}} };
        String[] et = {"c147", "c219", "dos"};
        for (int k = 0; k < dos.length; k++) {
            EstudioPliegueDos.ajusta(dos[k][0][0], dos[k][1], et[k]);
            Gota g = EstudioPliegueDos.gota(dos[k][0][0], dos[k][0][0]);
            mide(g, Coordenadas.de(g, op), et[k] + " r=" + dos[k][0][0], d);
        }
    }

    /** Salto de (u, v) a través de los dos bordes de cada zona de pliegue de la gota. */
    static void mide(Gota g, Coordenadas c, String etiqueta, double d) {
        for (Pliegue z : c.zonasPliegue()) {
            System.out.printf("%s  ta=%.3f tb=%.3f lado=%d%n", etiqueta, z.ta, z.tb, z.lado);
            for (int e = 0; e < 2; e++) {
                double t = e == 0 ? z.ta : z.tb;
                double cx = g.cx(t), cy = g.cy(t), dx = z.xx - cx, dy = z.xy - cy, L = Math.hypot(dx, dy);
                double ux = dx / L, uy = dy / L, nx = -uy, ny = ux;
                double r = g.rad(t);
                double maxU = 0, maxV = 0, sumU = 0, sumV = 0, natV = 0, maxNat = 0; int n = 0;
                for (double s = 0.1 * r; s < 1.9 * r; s += 0.05 * r) {            // desde X hacia c y más allá (exterior)
                    double px = z.xx - ux * s, py = z.xy - uy * s;
                    Coordenadas.Costurado dentro = null, fuera = null; double sgIn = 0;
                    for (double sg : new double[] { -1, 1 }) {
                        double qx = px + sg * d * nx, qy = py + sg * d * ny;
                        Coordenadas.Costurado k = c.pliegue(qx, qy);
                        if (k != null) { dentro = k; sgIn = sg; } else fuera = vecino(c, qx, qy, t, e == 0);
                    }
                    if (dentro == null || fuera == null) continue;
                    if (!g.mascara().contiene(px, py)) continue;
                    // control: variación natural de v dentro de la zona entre dos puntos a la misma distancia (2d) que los dos lados del borde
                    Coordenadas.Costurado i1 = c.pliegue(px + sgIn * d * nx, py + sgIn * d * ny), i2 = c.pliegue(px + sgIn * 3 * d * nx, py + sgIn * 3 * d * ny);
                    if (i1 == null || i2 == null) continue;
                    double nat = Math.abs(i1.uv().v() - i2.uv().v()); natV += nat; maxNat = Math.max(maxNat, nat);
                    double du = Math.abs(dentro.uv().u() - fuera.uv().u()), dv = Math.abs(dentro.uv().v() - fuera.uv().v());
                    maxU = Math.max(maxU, du); maxV = Math.max(maxV, dv); sumU += du; sumV += dv; n++;
                }
                System.out.printf("   borde %s: %3d puntos | |Δu| medio %.3f máx %.3f | |Δv| medio %.4f máx %.4f | control dentro: medio %.4f máx %.4f%n", e == 0 ? "ta" : "tb", n, n == 0 ? 0 : sumU / n, maxU, n == 0 ? 0 : sumV / n, maxV, n == 0 ? 0 : natV / n, maxNat);
            }
        }
    }
    /** El candidato de la banda más cercano al borde por fuera de la zona: el de mayor clave <= ta, o el de menor clave >= tb. */
    static Coordenadas.Costurado vecino(Coordenadas c, double x, double y, double tb, boolean abajo) {
        Zonas.Candidato m = null;
        for (Zonas.Candidato k : c.candidatos(x, y)) {
            if (k.clave() < 0 || k.clave() > 1) continue;
            if (abajo ? k.clave() <= tb + 1e-9 : k.clave() >= tb - 1e-9)
                if (m == null || (abajo ? k.clave() > m.clave() : k.clave() < m.clave())) m = k;
        }
        return m == null ? null : m.c();
    }
}
