import java.util.Objects;

/**
 * Cómo se pinta una {@link Gota}: la {@link Costura} reparte las coordenadas de la tira, el {@link Solape} resuelve
 * los cruces del camino y la {@link Tira} reparte el eje v entre tapas y cuerpo. Son independientes entre sí y no
 * cambian la forma de la gota, solo qué coordenadas (y por tanto qué color) llega a cada píxel.
 */
public record Opciones(Costura costura, Solape solape, Tira tira) {

    /** ESPIGA, PLANO y la tira 1 : 2 : 1. */
    public static final Opciones POR_DEFECTO = new Opciones(Costura.ESPIGA, Solape.PLANO, Tira.POR_DEFECTO);

    public Opciones {
        Objects.requireNonNull(costura, "costura");
        Objects.requireNonNull(solape, "solape");
        Objects.requireNonNull(tira, "tira");
    }

    public Opciones conCostura(Costura c) { return new Opciones(c, solape, tira); }

    public Opciones conSolape(Solape s) { return new Opciones(costura, s, tira); }

    public Opciones conTira(Tira t) { return new Opciones(costura, solape, t); }
}
