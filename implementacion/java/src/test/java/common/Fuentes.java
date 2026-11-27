package common;

import java.awt.Font;
import java.awt.font.FontRenderContext;

/**
 * ¿Puede escribir texto este entorno? Lo comprueban las suites que etiquetan: sin fuentes del sistema (ni fontconfig, ni
 * libfreetype: en ese caso el JDK lanza {@code UnsatisfiedLinkError}), se dibuja sin {@code etiquetas}
 * ({@code Trazos.Ajustes#conEtiquetas(false)}) y sin los rótulos propios. El SVG no las necesita: escribe el texto como
 * {@code <text>}.
 */
public final class Fuentes {

    private Fuentes() {}

    private static Boolean hay;   // memoizado: las fuentes del entorno no cambian durante la ejecución

    /** true si {@code Font.createGlyphVector} sale bien; false si el entorno no tiene fuentes. */
    public static boolean hay() {
        if (hay == null) {
            try {
                new Font(Font.SANS_SERIF, Font.BOLD, 11)
                    .createGlyphVector(new FontRenderContext(null, true, true), "C1 r2").getOutline();
                hay = true;
            } catch (LinkageError | InternalError | RuntimeException e) {
                hay = false;
            }
        }
        return hay;
    }
}
