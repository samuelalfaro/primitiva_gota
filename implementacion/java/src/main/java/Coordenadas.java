/**
 * Coordenadas intrínsecas de los píxeles de una {@link Gota}, comunes a los colores ({@link Colores}) y a las texturas
 * ({@link Texturas}): ambos se calculan sobre las mismas coordenadas, que la gota deduce de la posición del píxel.
 * <ul>
 *   <li>{@link Muestra}: (t, rho, axial, lateral) respecto al círculo de la familia que lo cubre;</li>
 *   <li>{@link Uv}: la tira (u de lado a lado, v a lo largo, con las tapas como semidiscos);</li>
 *   <li>{@link #discoTapa}: del punto de la tira al disco unidad de una tapa (l, a), de donde salen las coordenadas
 *       polares de los colores y la posición plana de las texturas.</li>
 * </ul>
 */
public final class Coordenadas {

    /**
     * Parámetros de un píxel respecto al círculo de la familia que lo cubre (c = c(t), r = r(t)):
     * t = posición en el camino; rho = |p - c| / r;
     * axial = (p - c)·T / r, con T la tangente unitaria del camino;
     * lateral = (p - c)·N / r, con N = (Ty, -Tx) (con T hacia abajo en pantalla, N apunta a la derecha).
     * Se cumple rho² = axial² + lateral².
     */
    public record Muestra(double t, double rho, double axial, double lateral) {}

    /** Coordenadas de textura de la tira: v a lo largo de la gota, u de lado a lado. */
    public record Uv(double u, double v) {}

    /** Posición t sobre el camino y coordenadas de la tira de un píxel, según el modo de costura de la gota. */
    record Costurado(double t, Uv uv) {}

    private final Geometria g;
    private final Costura modo;
    private volatile Zonas zonas;

    Coordenadas(Geometria g, Costura modo) { this.g = g; this.modo = modo; }

    private Zonas zonas() {
        Zonas c = zonas;
        if (c == null) synchronized (this) { if ((c = zonas) == null) zonas = c = new Zonas(g, modo); }
        return c;
    }

    /** Solución única del píxel (solape PLANO). */
    Costurado costurado(double x, double y) { return zonas().calcula(x, y); }

    /** Todas las soluciones del píxel, con su clave de orden (solape TRAZO). */
    java.util.List<Zonas.Candidato> candidatos(double x, double y) { return zonas().candidatos(x, y); }

    /** El punto (x, y) con las coordenadas que elija la fuente; muestra y uv se calculan al pedirlas, una sola vez. */
    Punto punto(double x, double y, java.util.function.Supplier<Costurado> fuente) {
        return new PuntoPerezoso(x, y, fuente);
    }

    private final class PuntoPerezoso implements Punto {
        private final double x, y;
        private java.util.function.Supplier<Costurado> fuente;
        private Costurado costurado;
        private Muestra muestra;

        PuntoPerezoso(double x, double y, java.util.function.Supplier<Costurado> fuente) {
            this.x = x; this.y = y; this.fuente = fuente;
        }

        private Costurado costurado() {
            if (costurado == null) { costurado = fuente.get(); fuente = null; }
            return costurado;
        }

        @Override public Muestra muestra() {
            if (muestra == null) muestra = Coordenadas.this.muestra(x, y, costurado().t());
            return muestra;
        }

        @Override public Uv uv() { return costurado().uv(); }
    }

    Muestra muestra(double x, double y, double t) {
        double r = Math.max(g.rad(t), 1e-9);
        double dx = x - g.cx(t), dy = y - g.cy(t);
        double[] tg = g.tangente(t);
        if (g.esElipse()) {
            double ax = (dx * tg[0] + dy * tg[1]) / (r * g.aspecto(t)), la = (dx * tg[1] - dy * tg[0]) / r;
            return new Muestra(t, Math.hypot(ax, la), ax, la);
        }
        return new Muestra(t, Math.hypot(dx, dy) / r,
                           (dx * tg[0] + dy * tg[1]) / r,
                           (dx * tg[1] - dy * tg[0]) / r);   // N = (Ty, -Tx)
    }

    // ---------- Tira de textura ----------
    //
    // v recorre la gota en cuatro tramos (por defecto cuartos):
    //   [0, v1]   tapa trasera (semicírculo exterior del primer círculo)
    //   [v1, v2]  primera mitad del cuerpo, t en [0, tm]
    //   [v2, v3]  segunda mitad del cuerpo, t en [tm, 1]
    //   [v3, 1]   tapa delantera
    // u cruza de lado a lado: 1/2 sobre el camino, 0 y 1 en los bordes. Es una coordenada con signo,
    // lineal en la distancia lateral, así que no tiene arista en el eje (a diferencia de rho).
    // Orientación: u crece hacia la izquierda del sentido de avance en pantalla.

    /**
     * Las tapas de la tira son semidiscos. Con (u, w) de la tira (w va de 0 en la costura con el cuerpo a 1 en el arco),
     * l = 2u - 1 y a = w·sqrt(1 - l²) dan el punto (l, a) del semidisco unidad (en la gota recta de radios iguales es la
     * posición real del píxel). Como w es una fracción de la profundidad de la tapa, el cuerpo y la tapa casan para
     * cualquier pendiente del radio y para las elipses. La textura se coloca plana sobre ese semidisco (v' = c1·(1 - a))
     * y el color lateral va en polares (s, th): ver {@link Lateral#enDisco}.
     */
    /** {l, a, fin} del punto de tapa de la tira (fin 0 = primera, 1 = segunda), o null si cae en el cuerpo. */
    static double[] discoTapa(Coordenadas.Uv uv, double c1, double c3) {
        double v = uv.v(), w;
        int fin;
        if (v < c1) { w = 1 - v / c1; fin = 0; }
        else if (v > c3) { w = (v - c3) / (1 - c3); fin = 1; }
        else return null;
        double l = 2 * clamp01(uv.u()) - 1;
        return new double[] { l, w * Math.sqrt(Math.max(0, 1 - l * l)), fin };
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
