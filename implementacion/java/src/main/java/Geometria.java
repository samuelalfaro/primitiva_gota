/**
 * Consulta de solo lectura de la geometría de una gota: el camino (una Bézier cúbica de centros), los radios
 * a lo largo del parámetro t en [0, 1] y la pertenencia de un punto.
 * Es lo único que necesitan {@link Coordenadas} y las zonas de la tira para repartir el interior de la gota, y también lo que
 * necesitaría cualquier otro dibujo de la primitiva (por ejemplo, un modo de alambre).
 * Todas las coordenadas están en el espacio de la imagen.
 */
public interface Geometria {
    /** Centro del círculo de la familia en t. */
    double cx(double t);
    double cy(double t);

    /** Derivada del centro respecto a t (velocidad sobre el camino). */
    double dcx(double t);
    double dcy(double t);

    /** Radio en t y su derivada. */
    double rad(double t);
    double drad(double t);

    /** Tangente unitaria del camino en t. */
    double[] tangente(double t);

    /** Posición efectiva de rm sobre el camino: es también donde cae el corte central de la tira. */
    double tm();

    /** Pertenencia exacta de un punto a la gota. */
    boolean contiene(double x, double y);

    /** Una pasada del camino sobre un punto: su parámetro t y la distancia con signo al borde de esa pasada. */
    record Hebra(double t, double distancia) {
        /** Fracción del píxel que cubre la pasada (antialiasing de medio píxel a cada lado del borde). */
        public double cobertura() { return Math.max(0, Math.min(1, 0.5 - distancia)); }
    }

    /** Fracción del píxel centrado en (x, y) que cubre la unión de la gota, en [0, 1]. */
    double cobertura(double x, double y);

    /** Las pasadas del camino que cubren (aunque sea en parte) el píxel (x, y), en orden de t creciente. */
    java.util.List<Hebra> hebras(double x, double y);
}
