import java.util.Objects;

/**
 * Cómo se pinta una {@link Gota}: la {@link Costura} reparte las coordenadas de la tira y el {@link Solape} resuelve
 * los cruces del camino. Son independientes entre sí y no cambian la forma de la gota, solo qué coordenadas (y por
 * tanto qué color) llega a cada píxel.
 */
public record Opciones(Costura costura, Solape solape) {

    /** ESPIGA y PLANO. */
    public static final Opciones POR_DEFECTO = new Opciones(Costura.ESPIGA, Solape.PLANO);

    public Opciones {
        Objects.requireNonNull(costura, "costura");
        Objects.requireNonNull(solape, "solape");
    }

    public Opciones conCostura(Costura c) { return new Opciones(c, solape); }

    public Opciones conSolape(Solape s) { return new Opciones(costura, s); }
}
