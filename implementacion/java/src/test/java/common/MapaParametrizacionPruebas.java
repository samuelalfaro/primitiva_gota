package common;

import forma.Gota;
import java.awt.geom.Point2D;

/** Pruebas de {@link MapaParametrizacion}: cero en una recta, desvío en una curva con tiradores desiguales, signo y colores de la escala. */
public class MapaParametrizacionPruebas {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-78s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    public static void main(String[] args) {
        Object[][] gs = MapaParametrizacion.demo();
        var recta = MapaParametrizacion.mapa((Gota) gs[0][1], 240, 8, 0.25);
        comprueba("recta: la t avanza a ritmo constante (desvío < 1e-3)", recta.pixeles() > 1000 && recta.maximo() < 1e-3, String.format("%.5f", recta.maximo()));
        var desigual = MapaParametrizacion.mapa((Gota) gs[3][1], 240, 8, 0.25);
        comprueba("curva con tiradores largos: desvío máximo > 0.05", desigual.maximo() > 0.05, String.format("%.3f", desigual.maximo()));
        var suave = MapaParametrizacion.mapa((Gota) gs[1][1], 240, 8, 0.25);
        comprueba("más desigual la parametrización, más desvío (suave < tiradores largos)", suave.medio() < desigual.medio(), String.format("%.3f < %.3f", suave.medio(), desigual.medio()));
        comprueba("el fondo es blanco y la recta es gris neutro", recta.imagen().getRGB(0, 0) == 0xFFFFFFFF
                  && recta.imagen().getRGB(recta.imagen().getWidth() / 2, recta.imagen().getHeight() / 2) == MapaParametrizacion.color(0), "");
        comprueba("escala: 0 gris, + rojo, − azul, saturada en ±1", MapaParametrizacion.color(0) == 0xFFB0B0B0 && MapaParametrizacion.color(1) == 0xFFD02020
                  && MapaParametrizacion.color(-1) == 0xFF2040D0 && MapaParametrizacion.color(5) == MapaParametrizacion.color(1), "");
        System.out.println(pruebas + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }
}
