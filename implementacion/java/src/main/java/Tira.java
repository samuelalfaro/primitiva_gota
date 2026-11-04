/**
 * Cómo se reparte el eje v de la tira entre las dos tapas y el cuerpo: es la malla interna de la gota sobre la que
 * se calculan colores y texturas. No cambia la forma de la gota, solo qué fracción de v (y por tanto de la textura)
 * ocupa cada zona. Forma parte de las {@link Opciones} de pintado.
 */
public interface Tira {

    /** Los cortes de v para esta geometría. */
    Cortes cortes(Geometria g);

    /** Tapa trasera, cuerpo y tapa delantera en proporción 1 : 2 : 1 (v1 = 1/4, v3 = 3/4). */
    Tira POR_DEFECTO = new Pesos(1, 2, 1);

    /**
     * Reparto por pesos: la parte de v que ocupan la tapa trasera, el cuerpo y la tapa delantera. Dentro del cuerpo v
     * es lineal en t y el corte central cae donde t = tm: tm manda sobre el corte central.
     */
    static Tira pesos(double wTapa1, double wCuerpo, double wTapa2) { return new Pesos(wTapa1, wCuerpo, wTapa2); }

    /** Reparto según la longitud real: tapas de longitud r1 y r2, cuerpo del largo del camino. La textura no se estira más en un tramo que en otro. */
    static Tira proporcional() { return Proporcional.INSTANCIA; }

    record Pesos(double wTapa1, double wCuerpo, double wTapa2) implements Tira {
        public Pesos {
            if (!(wTapa1 > 0) || !(wCuerpo > 0) || !(wTapa2 > 0) || !Double.isFinite(wTapa1 + wCuerpo + wTapa2))
                throw new IllegalArgumentException("los pesos de la tira deben ser finitos y > 0");
        }

        @Override public Cortes cortes(Geometria g) {
            double s = wTapa1 + wCuerpo + wTapa2, c1 = wTapa1 / s, c3 = (wTapa1 + wCuerpo) / s;
            return new Cortes(c1, c1 + (c3 - c1) * g.tm(), c3);
        }
    }

    record Proporcional() implements Tira {
        private static final Proporcional INSTANCIA = new Proporcional();

        @Override public Cortes cortes(Geometria g) {
            double largo = 0, px = g.cx(0), py = g.cy(0);
            for (int i = 1; i <= 400; i++) {
                double x = g.cx(i / 400.0), y = g.cy(i / 400.0), dx = px - x, dy = py - y;
                largo += Math.sqrt(dx * dx + dy * dy);
                px = x; py = y;
            }
            double r1 = g.rad(0), r2 = g.rad(1);
            double mini = 1e-3 * (r1 + r2 + largo + 1e-9);
            return new Pesos(Math.max(r1, mini), Math.max(largo, mini), Math.max(r2, mini)).cortes(g);
        }
    }
}
