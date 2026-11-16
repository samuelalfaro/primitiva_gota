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
            Coordenadas c = Coordenadas.de(g, op); Pliegue z = c.zonaPliegue();
            if (z == null) { System.out.println("sin zona"); continue; }
            System.out.printf("codo a=%.1f r1=%.1f r2=%.1f  ta=%.3f tb=%.3f%n", q[0], q[1], q[2], z.ta, z.tb);
            for (int e = 0; e < 2; e++) {
                double t = e == 0 ? z.ta : z.tb;
                double cx = g.cx(t), cy = g.cy(t), dx = z.xx - cx, dy = z.xy - cy, L = Math.hypot(dx, dy);
                double ux = dx / L, uy = dy / L, nx = -uy, ny = ux, sgn = e == 0 ? 1 : -1;  // lado de fuera del abanico: hacia t menor (ta) o mayor (tb)
                double r = g.rad(t);
                double maxU = 0, maxV = 0, sumU = 0, sumV = 0; int n = 0, nIn = 0;
                double hmax = 0;
                for (double s = 0.1 * r; s < 1.9 * r; s += 0.05 * r) {            // desde X hacia c y más allá (exterior)
                    double px = z.xx - ux * s, py = z.xy - uy * s;
                    for (int lado = 0; lado < 1; lado++) {
                        Coordenadas.Costurado dentro = null, fuera = null;
                        for (double sg : new double[] { -1, 1 }) {
                            double qx = px + sg * d * nx, qy = py + sg * d * ny;
                            Coordenadas.Costurado k = c.pliegue(qx, qy);
                            if (k != null) dentro = k; else fuera = vecino(c, qx, qy, e == 0 ? z.ta : z.tb, e == 0);
                        }
                        if (dentro == null || fuera == null) continue;
                        if (!g.mascara().contiene(px, py)) continue;
                        double du = Math.abs(dentro.uv().u() - fuera.uv().u()), dv = Math.abs(dentro.uv().v() - fuera.uv().v());
                        maxU = Math.max(maxU, du); maxV = Math.max(maxV, dv); sumU += du; sumV += dv; n++;
                    }
                }
                System.out.printf("   borde %s: %3d puntos | |Δu| medio %.3f máx %.3f | |Δv| medio %.4f máx %.4f%n", e == 0 ? "ta" : "tb", n, n == 0 ? 0 : sumU / n, maxU, n == 0 ? 0 : sumV / n, maxV);
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
