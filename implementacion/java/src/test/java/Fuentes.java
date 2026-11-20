import java.awt.Font;
import java.awt.font.FontRenderContext;

/**
 * ¿Puede escribir texto este entorno? Lo comprueban las suites que etiquetan: sin fuentes del sistema (o libfontconfig),
 * se dibuja sin {@code etiquetas} ({@code Trazos.Ajustes#conEtiquetas(false)}) y sin los rótulos propios.
 */
final class Fuentes {

    private Fuentes() {}

    private static Boolean hay;   // memoizado: las fuentes del entorno no cambian durante la ejecución

    /** true si {@code Font.createGlyphVector} sale bien; false si el entorno no tiene fuentes. */
    static boolean hay() {
        if (hay == null) {
            try {
                new Font(Font.SANS_SERIF, Font.BOLD, 11)
                    .createGlyphVector(new FontRenderContext(null, true, true), "C1 r2").getOutline();
                hay = true;
            } catch (InternalError | RuntimeException e) {
                hay = false;
            }
        }
        return hay;
    }
}
