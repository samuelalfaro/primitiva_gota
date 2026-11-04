import java.util.List;
import java.util.function.Consumer;

/**
 * Cómo se resuelve lo que pasa donde el camino de una {@link Gota} se cruza consigo mismo o los círculos se solapan:
 * dado un píxel, el solape produce las capas que se pintan, de la de abajo a la de arriba. Es independiente de la
 * {@link Costura} (que dice cómo se reparten las coordenadas) y de la forma (la silueta es la misma en los dos).
 * La diferencia se ve con colores translúcidos y con texturas: PLANO pinta la unión una sola vez y TRAZO acumula una
 * capa por cada pasada del camino.
 */
public enum Solape {
    /**
     * Primitiva plana (por defecto): una sola capa por píxel, con la cobertura de la unión y las coordenadas de la
     * solución única que da la costura. Deja costuras duras donde dos tramos compiten.
     */
    PLANO {
        @Override void capas(Geometria g, Coordenadas c, double x, double y, Consumer<Capa> out) {
            double cob = g.cobertura(x, y);
            if (cob > 0) out.accept(new Capa(cob, c.punto(x, y, () -> c.costurado(x, y))));
        }

        @Override Coordenadas.Costurado ganador(Coordenadas c, double x, double y) { return c.costurado(x, y); }
    },
    /**
     * Trazo: cada pasada del camino sobre el píxel es una capa, con su cobertura y antialiasing propios. Las de t
     * mayor tapan a las anteriores. Cada capa toma las coordenadas del candidato de mayor clave (la más tardía del
     * trazo) por debajo del punto medio con la capa siguiente; la última, la mayor de todas, tapas finales incluidas.
     */
    TRAZO {
        @Override void capas(Geometria g, Coordenadas c, double x, double y, Consumer<Capa> out) {
            List<Geometria.Hebra> hs = g.hebras(x, y);
            Memo memo = new Memo(c, x, y);
            for (int h = 0; h < hs.size(); h++) {            // t creciente: la posterior tapa a la anterior
                Geometria.Hebra hb = hs.get(h);
                double cob = hb.cobertura();
                if (cob <= 0) continue;
                final double limite = h == hs.size() - 1 ? Double.POSITIVE_INFINITY : 0.5 * (hb.t() + hs.get(h + 1).t());
                out.accept(new Capa(cob, c.punto(x, y, () -> elige(memo.candidatos(), limite, c, x, y))));
            }
        }

        @Override Coordenadas.Costurado ganador(Coordenadas c, double x, double y) {
            Zonas.Candidato mejor = null;
            for (Zonas.Candidato k : c.candidatos(x, y)) if (mejor == null || k.clave() > mejor.clave()) mejor = k;
            return mejor != null ? mejor.c() : c.costurado(x, y);
        }

        /** El de mayor clave que no pase de limite; si ninguno, el de menor clave; si no hay candidatos, la solución única. */
        private static Coordenadas.Costurado elige(List<Zonas.Candidato> cs, double limite, Coordenadas c, double x, double y) {
            double kb = Double.NEGATIVE_INFINITY, kmin = Double.POSITIVE_INFINITY;
            Coordenadas.Costurado best = null, primero = null;
            for (Zonas.Candidato k : cs) {
                if (k.clave() <= limite && k.clave() > kb) { best = k.c(); kb = k.clave(); }
                if (k.clave() < kmin) { primero = k.c(); kmin = k.clave(); }
            }
            return best != null ? best : primero != null ? primero : c.costurado(x, y);
        }
    };

    /** Una capa del píxel: la fracción que cubre (antialiasing) y el punto con el que se colorea. */
    public record Capa(double cobertura, Punto punto) {}

    /** Entrega, de abajo a arriba, las capas que se pintan en el píxel (x, y). */
    abstract void capas(Geometria g, Coordenadas c, double x, double y, Consumer<Capa> out);

    /** Las coordenadas con las que se ve el píxel como un todo: la solución única en PLANO, la más tardía del trazo en TRAZO. */
    abstract Coordenadas.Costurado ganador(Coordenadas c, double x, double y);

    /** Candidatos del píxel, calculados una sola vez y solo si alguna capa los pide. */
    private static final class Memo {
        private final Coordenadas c;
        private final double x, y;
        private List<Zonas.Candidato> cs;

        Memo(Coordenadas c, double x, double y) { this.c = c; this.x = x; this.y = y; }

        List<Zonas.Candidato> candidatos() {
            if (cs == null) cs = c.candidatos(x, y);
            return cs;
        }
    }
}
