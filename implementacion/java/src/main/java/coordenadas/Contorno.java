package coordenadas;

import forma.Camino;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * El contorno de una {@link Camino}: los tramos de su envolvente que son borde real de la gota ({@link #borde}) y los que
 * quedan dentro (lazos, cuellos y pliegues: {@link #oculta}). Sale de los puntos de contacto de {@link Zonas} y de los
 * arcos de los extremos, no de una malla; un punto de la envolvente es borde si un punto apenas más hacia fuera queda
 * fuera según {@link Camino#mascara()}. Lo usan la silueta de las geometrías y el modo alambre ({@code Trazos}).
 *
 * @param borde  la silueta (puede ser {@code null} en casos degenerados sin ningún tramo de borde)
 * @param oculta la parte de la envolvente que queda dentro, o {@code null} si no hay
 */
public record Contorno(Shape borde, Shape oculta) {

    /** Muestras por tramo que usa {@link #de(Camino)}. */
    public static final int MUESTRAS = 160;

    private static final int SEGMENTOS_ARCO = 64;

    public static Contorno de(Camino geo) { return de(geo, MUESTRAS); }

    public static Contorno de(Camino geo, int muestras) {
        return de(geo, new Zonas(geo, Costura.ORTOGONAL, Tira.EN_T.cortes(geo)), muestras);
    }

    /** Un punto de la envolvente: posición, normal hacia fuera (sin normalizar) y distancia del test de pertenencia. */
    private record PuntoBorde(double x, double y, double nx, double ny, double eps) {}

    /** Un tramo de la envolvente, de s = 0 a s = 1. */
    private interface Tramo {
        PuntoBorde en(double s);
        int muestras();
    }

    private static final double EPS_REL = 1e-3;

    /** PuntoBorde del círculo del marco f con ángulo th (desde T hacia N) y normal hacia fuera. */
    private static PuntoBorde enCirculo(Zonas.Fr f, double th) {
        double r = Math.max(f.r(), 1e-12);
        double A = r * Math.cos(th), S = r * Math.sin(th);
        double nA = Math.cos(th) / r, nS = Math.sin(th) / r;
        return new PuntoBorde(f.cx() + A * f.tx() + S * f.nx(), f.cy() + A * f.ty() + S * f.ny(),
            nA * f.tx() + nS * f.nx(), nA * f.ty() + nS * f.ny(), Math.max(EPS_REL * f.r(), 1e-4));
    }

    /** PuntoBorde de contacto del lado l (0 = -N, 1 = +N) en el marco f. */
    private static PuntoBorde enContacto(Zonas.Fr f, int l) {
        return enCirculo(f, angulo(f, l));
    }

    /**
     * Los tramos de la envolvente, en orden y cerrados: rama del lado -N (t de 0 a 1), arco del extremo final por delante
     * (pasando por +T), rama del lado +N (t de 1 a 0) y arco del extremo inicial por detrás (pasando por -T). Cada rama
     * se parte en los saltos de la envolvente (ver {@link #saltos}) y ahí lleva el arco de la figura.
     */
    private static List<Tramo> tramos(Zonas zonas, int muestras) {
        Zonas.Fr f0 = zonas.marco(0), f1 = zonas.marco(1);
        double[] fin = barrido(angulo(f1, 0), angulo(f1, 1)), ini = barrido(angulo(f0, 1), angulo(f0, 0));
        List<double[]> saltos = saltos(zonas, 4 * muestras);
        List<Tramo> out = new ArrayList<>(ramas(zonas, saltos, 0, muestras));
        out.add(tramo(SEGMENTOS_ARCO, s -> enCirculo(f1, fin[0] + fin[1] * s)));
        out.addAll(ramas(zonas, saltos, 1, muestras));
        out.add(tramo(SEGMENTOS_ARCO, s -> enCirculo(f0, ini[0] + ini[1] * s)));
        return out;
    }

    /**
     * Saltos de la envolvente: parámetros {lo, hi} muy próximos entre los que el contacto de un lado cambia de sitio de
     * golpe mientras el centro apenas se mueve. Ocurre con velocidad nula: en una cúspide (la tangente se invierte) o
     * cuando el radio cambia de signo de variación con el camino parado (el contacto pasa de una punta a la otra). La
     * envolvente no recorre nada entre los dos contactos, pero la silueta real sí: pasa por el arco de la figura en ese
     * instante ({@link #arcoSalto}).
     */
    private static List<double[]> saltos(Zonas zonas, int n) {
        List<double[]> out = new ArrayList<>();
        Zonas.Fr prev = zonas.marco(0);
        double tp = 0;
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n;
            Zonas.Fr f = zonas.marco(t);
            double r = Math.max(f.r(), 1e-9);
            boolean quieto = Math.hypot(f.cx() - prev.cx(), f.cy() - prev.cy()) < 0.05 * r;
            for (int l = 0; l < 2 && quieto; l++) {
                if (Math.hypot(contactoX(f, l) - contactoX(prev, l), contactoY(f, l) - contactoY(prev, l)) > 0.5 * r) {
                    double lo = tp, hi = t, px = contactoX(prev, l), py = contactoY(prev, l), qx = contactoX(f, l), qy = contactoY(f, l);
                    while (hi - lo > 1e-6) {
                        double m = 0.5 * (lo + hi);
                        Zonas.Fr fm = zonas.marco(m);
                        double mx = contactoX(fm, l), my = contactoY(fm, l);
                        if (Math.hypot(mx - px, my - py) < Math.hypot(mx - qx, my - qy)) lo = m; else hi = m;
                    }
                    out.add(new double[] { lo, hi });
                    break;                                   // un salto por intervalo, aunque lo vean los dos lados
                }
            }
            prev = f;
            tp = t;
        }
        return out;
    }

    private static double contactoX(Zonas.Fr f, int l) { return f.cx() + f.ox()[l]; }
    private static double contactoY(Zonas.Fr f, int l) { return f.cy() + f.oy()[l]; }

    /** Rama del lado l: en sentido de t creciente con l = 0 y decreciente con l = 1, con un arco en cada salto. */
    private static List<Tramo> ramas(Zonas zonas, List<double[]> cuspides, int l, int muestras) {
        List<Tramo> out = new ArrayList<>();
        int k = cuspides.size();
        if (l == 0) {
            double ta = 0;
            for (int c = 0; c < k; c++) {
                out.add(rama(zonas, l, ta, cuspides.get(c)[0], muestras));
                out.add(arcoSalto(zonas, cuspides.get(c), l, false));
                ta = cuspides.get(c)[1];
            }
            out.add(rama(zonas, l, ta, 1, muestras));
        } else {
            double ta = 1;
            for (int c = k - 1; c >= 0; c--) {
                out.add(rama(zonas, l, ta, cuspides.get(c)[1], muestras));
                out.add(arcoSalto(zonas, cuspides.get(c), l, true));
                ta = cuspides.get(c)[0];
            }
            out.add(rama(zonas, l, ta, 0, muestras));
        }
        return out;
    }

    private static Tramo rama(Zonas zonas, int l, double ta, double tb, int muestras) {
        int m = Math.max(2, (int) Math.round(muestras * Math.abs(tb - ta)));
        return tramo(m, s -> enContacto(zonas.marco(ta + (tb - ta) * s), l));
    }

    /**
     * Arco del círculo del salto entre el contacto del lado l justo antes (en su marco) y justo después. Se toma el arco
     * más corto; si son iguales (una cúspide: media vuelta) el que pasa por la punta de delante del marco de antes
     * (th = 0). Con reverso se recorre de después a antes.
     */
    private static Tramo arcoSalto(Zonas zonas, double[] salto, int l, boolean reverso) {
        Zonas.Fr fb = zonas.marco(salto[0]), fa = zonas.marco(salto[1]);
        double thB = angulo(fb, l);
        double dx = contactoX(fa, l) - fb.cx(), dy = contactoY(fa, l) - fb.cy();
        double A = dx * fb.tx() + dy * fb.ty(), S = dx * fb.nx() + dy * fb.ny();
        double thA = Math.atan2(S / fb.r(), A / fb.r());
        double d = (thA - thB) % (2 * Math.PI);
        if (d > Math.PI) d -= 2 * Math.PI;
        if (d <= -Math.PI) d += 2 * Math.PI;
        double signo, barrido;
        if (Math.abs(Math.abs(d) - Math.PI) < 1e-3) {      // media vuelta: por delante (l = 0 crece desde -N; l = 1 decrece desde +N)
            signo = l == 0 ? 1 : -1;
            barrido = Math.PI;
        } else {
            signo = Math.signum(d);
            barrido = Math.abs(d);
        }
        return tramo(SEGMENTOS_ARCO, s -> enCirculo(fb, thB + signo * barrido * (reverso ? 1 - s : s)));
    }

    private static Tramo tramo(int n, java.util.function.DoubleFunction<PuntoBorde> f) {
        return new Tramo() {
            public PuntoBorde en(double s) { return f.apply(s); }
            public int muestras() { return n; }
        };
    }

    /** {inicio, barrido}: de th0 a th1 en sentido creciente, con barrido en (0, 2 pi]. */
    private static double[] barrido(double th0, double th1) {
        double b = (th1 - th0) % (2 * Math.PI);
        if (b < 0) b += 2 * Math.PI;
        return new double[] { th0, b };
    }

    /** Ángulo (en el círculo, desde T hacia N) del punto de contacto del lado l. */
    private static double angulo(Zonas.Fr f, int l) {
        double A = f.ox()[l] * f.tx() + f.oy()[l] * f.ty();
        double S = f.ox()[l] * f.nx() + f.oy()[l] * f.ny();
        return Math.atan2(S / f.r(), A / f.r());
    }

    /** true si el punto es borde de la gota: un punto apenas más hacia fuera queda fuera. */
    private static boolean borde(Camino geo, PuntoBorde p) {
        double n = Math.hypot(p.nx(), p.ny());
        if (n < 1e-300) return false;
        return !geo.mascara().contiene(p.x() + p.eps() * p.nx() / n, p.y() + p.eps() * p.ny() / n);
    }

    /**
     * Reparte la envolvente en tramos de borde (borde) y ocultos (ocultos). Los cambios se
     * localizan por bisección sobre el parámetro del tramo. Si la geometría tiene silueta rígida ({@link Camino#arcos}), el
     * contorno son sus cuatro arcos y no hay envolvente oculta que mostrar.
     */
    public static Contorno de(Camino geo, Zonas zonas, int muestras) {
        List<Camino.Arco> arcos = geo.arcos();
        if (arcos != null) {                      // forma rígida: el contorno es la silueta que el Camino da, no la envolvente
            Path2D contorno = new Path2D.Double();
            for (Camino.Arco a : arcos) contorno.append(a.forma(), contorno.getCurrentPoint() != null);
            contorno.closePath();
            return new Contorno(contorno, null);
        }
        List<List<PuntoBorde>> corridas = new ArrayList<>();
        List<Boolean> visibles = new ArrayList<>();
        List<PuntoBorde> actual = null;
        boolean vis = false;
        for (Tramo t : tramos(zonas, muestras)) {
            double antes = 0;
            for (int i = 0; i <= t.muestras(); i++) {
                double s = i / (double) t.muestras();
                PuntoBorde q = t.en(s);
                boolean v = borde(geo, q);
                if (actual == null) {
                    actual = new ArrayList<>(List.of(q)); vis = v;
                } else if (i == 0) {                     // empalme entre tramos: es el mismo punto
                    if (v != vis) {
                        corridas.add(actual); visibles.add(vis);
                        actual = new ArrayList<>(List.of(q)); vis = v;
                    }
                } else if (v == vis) {
                    actual.add(q);
                } else {
                    double lo = antes, hi = s;
                    for (int k = 0; k < 40; k++) {
                        double m = 0.5 * (lo + hi);
                        if (borde(geo, t.en(m)) == vis) lo = m; else hi = m;
                    }
                    PuntoBorde c = t.en(0.5 * (lo + hi));
                    actual.add(c);
                    corridas.add(actual); visibles.add(vis);
                    actual = new ArrayList<>(List.of(c, q)); vis = v;
                }
                antes = s;
            }
        }
        corridas.add(actual); visibles.add(vis);
        boolean cerrado = corridas.size() == 1;
        if (corridas.size() > 1 && visibles.get(0) == visibles.get(visibles.size() - 1)) {   // la silueta es cíclica
            List<PuntoBorde> ultima = corridas.remove(corridas.size() - 1);
            visibles.remove(visibles.size() - 1);
            ultima.addAll(corridas.get(0).subList(1, corridas.get(0).size()));
            corridas.set(0, ultima);
        }
        Path2D borde = new Path2D.Double(), oculta = new Path2D.Double();
        boolean hayBorde = false, hayOculta = false;
        for (int r = 0; r < corridas.size(); r++) {
            Path2D p = visibles.get(r) ? borde : oculta;
            List<PuntoBorde> c = corridas.get(r);
            for (int i = 0; i < c.size(); i++) {
                if (i == 0) p.moveTo(c.get(i).x(), c.get(i).y()); else p.lineTo(c.get(i).x(), c.get(i).y());
            }
            if (cerrado) p.closePath();
            if (visibles.get(r)) hayBorde = true; else hayOculta = true;
        }
        return new Contorno(hayBorde ? borde : null, hayOculta ? oculta : null);
    }
}
