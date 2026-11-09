/**
 * La máscara de una gota: dice si un punto pertenece a ella, como si se dibujara la gota de un color sólido. Es lo primero
 * que se decide; a qué zona de las coordenadas intrínsecas pertenece el punto es posterior ({@link Coordenadas}).
 */
public interface Mascara {
    /** Pertenencia exacta de un punto. */
    boolean contiene(double x, double y);

    /** Fracción del píxel centrado en (x, y) que cubre la gota, en [0, 1]. */
    double cobertura(double x, double y);

    /** Las pasadas del camino que cubren (aunque sea en parte) el píxel (x, y), en orden de t creciente. */
    java.util.List<Geometria.Hebra> hebras(double x, double y);
}
