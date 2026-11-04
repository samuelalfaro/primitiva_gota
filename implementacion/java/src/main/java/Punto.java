/**
 * Un punto del plano visto desde una {@link Gota}: da, solo cuando se piden, su muestra (t, rho, axial, lateral) y sus
 * coordenadas de textura (u, v). Un {@link Relleno} recibe el punto y consulta lo que necesita: un color sólido no
 * calcula nada, un degradado pide la muestra y una textura pide las coordenadas.
 */
public interface Punto {
    Coordenadas.Muestra muestra();

    /** Coordenadas de la tira; lanza UnsupportedOperationException si el punto no las tiene. */
    Coordenadas.Uv uv();

    /** Punto ya calculado, con muestra y coordenadas de textura. */
    static Punto de(Coordenadas.Muestra m, Coordenadas.Uv uv) {
        return new Punto() {
            public Coordenadas.Muestra muestra() { return m; }
            public Coordenadas.Uv uv() { return uv; }
        };
    }

    /** Punto ya calculado que solo tiene muestra: pedir uv() lanza UnsupportedOperationException. */
    static Punto de(Coordenadas.Muestra m) {
        return new Punto() {
            public Coordenadas.Muestra muestra() { return m; }
            public Coordenadas.Uv uv() { throw new UnsupportedOperationException("este punto no tiene coordenadas Uv"); }
        };
    }
}
