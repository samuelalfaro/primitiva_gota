import color.Mezcla;
import geometria.Geometria;
import geometria.Grupo;
import geometria.composicion.Grafo;
import pintado.Plotter;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Un alfabeto experimental sin fuentes del sistema. Cada carácter es una pequeña red de gotas
 * negras: las aristas son los trazos y los radios de sus nodos dan peso y gesto al dibujo.
 *
 * <p>Genera {@code abecedario_by_codex.png} en el directorio desde el que se ejecute. Las
 * coordenadas de los glifos están normalizadas en una caja 0..100; ningún carácter usa más de
 * doce gotas.</p>
 */
public class Abecedario_by_codex {
    static final int FONDO = 0xFFFFFFFF, TINTA = 0xFF000000;
    static final int CELDA_W = 150, CELDA_H = 185, MARGEN = 18;

    /** Una polilínea de un glifo; cada pareja consecutiva de puntos produce una gota. */
    record Trazo(double[] xy) {
        Trazo {
            if (xy.length < 4 || (xy.length & 1) != 0) throw new IllegalArgumentException("trazo inválido");
        }
    }

    static Trazo t(double... xy) { return new Trazo(xy); }

    /*
     * El pequeño lenguaje de puntos es deliberadamente manual: es la "fuente" de gotas.
     * Los contornos cerrados llevan suficientes nodos para que Grafo relaje sus tangentes.
     */
    static List<Trazo> dibujo(char c) {
        return switch (c) {
            case 'A' -> List.of(t(14,100, 38,5, 62,5, 86,100), t(25,60, 75,60));
            case 'B' -> List.of(t(16,100, 16,4, 58,4, 82,23, 62,50, 16,50), t(16,50, 64,50, 85,75, 62,100, 16,100));
            case 'C' -> List.of(t(85,18, 66,4, 32,7, 13,35, 13,70, 31,96, 67,97, 86,82));
            case 'D' -> List.of(t(16,100, 16,4, 52,5, 82,28, 83,73, 53,99, 16,100));
            case 'E' -> List.of(t(84,5, 16,5, 16,100, 85,100), t(16,51, 66,51));
            case 'F' -> List.of(t(16,100, 16,5, 85,5), t(16,51, 65,51));
            case 'G' -> List.of(t(86,20, 65,5, 30,8, 12,37, 15,75, 36,97, 72,94, 84,73, 84,57, 54,57));
            case 'H' -> List.of(t(16,5, 16,100), t(84,5, 84,100), t(16,51, 84,51));
            case 'I' -> List.of(t(18,5, 82,5), t(50,5, 50,100), t(18,100, 82,100));
            case 'J' -> List.of(t(18,5, 82,5, 82,75, 68,97, 37,97, 18,79));
            case 'K' -> List.of(t(16,5, 16,100), t(84,5, 16,52, 86,100));
            case 'L' -> List.of(t(16,5, 16,100, 85,100));
            case 'M' -> List.of(t(13,100, 13,5, 50,53, 87,5, 87,100));
            case 'N' -> List.of(t(15,100, 15,5, 85,100, 85,5));
            // O ancho y redondo; el cero, más abajo, es estrecho y lleva una barra de contable.
            case 'O' -> List.of(t(50,5, 82,10, 94,39, 91,69, 75,94, 50,99, 25,94, 9,69, 6,39, 18,10, 50,5));
            case 'P' -> List.of(t(16,100, 16,5, 59,5, 84,25, 64,51, 16,51));
            case 'Q' -> List.of(t(50,4, 78,12, 89,42, 84,75, 65,98, 35,97, 13,72, 11,37, 25,11, 50,4), t(60,70, 89,100));
            case 'R' -> List.of(t(16,100, 16,5, 59,5, 84,25, 63,51, 16,51), t(55,51, 87,100));
            case 'S' -> List.of(t(85,19, 65,4, 31,7, 14,28, 29,48, 67,53, 86,72, 71,96, 34,98, 14,82));
            case 'T' -> List.of(t(12,5, 88,5), t(50,5, 50,100));
            case 'U' -> List.of(t(15,5, 15,72, 31,96, 65,97, 85,74, 85,5));
            case 'V' -> List.of(t(12,5, 50,100, 88,5));
            case 'W' -> List.of(t(10,5, 27,100, 50,57, 73,100, 90,5));
            case 'X' -> List.of(t(13,5, 87,100), t(87,5, 13,100));
            case 'Y' -> List.of(t(12,5, 50,52, 88,5), t(50,52, 50,100));
            case 'Z' -> List.of(t(13,5, 87,5, 13,100, 87,100));
            case '0' -> List.of(t(50,4, 72,13, 81,43, 77,75, 61,98, 39,96, 19,71, 19,34, 29,10, 50,4), t(29,83, 71,17));
            case '1' -> List.of(t(31,23, 51,5, 51,100), t(25,100, 78,100));
            case '2' -> List.of(t(17,25, 34,5, 66,6, 85,28, 74,51, 16,100, 87,100));
            case '3' -> List.of(t(18,16, 38,4, 68,8, 82,29, 60,51, 80,68, 70,94, 37,98, 16,83));
            case '4' -> List.of(t(72,100, 72,5), t(72,5, 13,67, 88,67));
            case '5' -> List.of(t(82,5, 22,5, 18,47, 58,47, 82,67, 70,94, 35,98, 15,82));
            case '6' -> List.of(t(79,17, 62,5, 32,12, 15,43, 18,79, 37,98, 68,94, 82,70, 65,49, 18,53));
            case '7' -> List.of(t(13,5, 87,5, 48,100));
            case '8' -> List.of(t(50,4, 75,13, 77,35, 50,51, 23,36, 25,13, 50,4), t(50,51, 78,67, 71,93, 50,99, 27,93, 22,67, 50,51));
            case '9' -> List.of(t(82,51, 63,49, 18,31, 31,7, 63,5, 84,26, 80,65, 62,96, 32,98, 17,83));
            case 'a' -> List.of(t(47,48, 24,55, 17,77, 30,98, 57,98, 76,78, 70,55, 47,48), t(76,52, 76,100));
            case 'b' -> List.of(t(18,5, 18,100), t(18,53, 43,48, 70,58, 77,78, 62,98, 35,98, 18,84));
            case 'c' -> List.of(t(76,61, 60,49, 34,52, 18,72, 23,94, 48,100, 73,88));
            case 'd' -> List.of(t(78,5, 78,100), t(77,54, 54,49, 28,57, 20,78, 35,98, 62,98, 78,83));
            case 'e' -> List.of(t(18,76, 76,76, 67,54, 39,49, 18,67, 25,94, 51,100, 75,88));
            case 'f' -> List.of(t(67,15, 54,5, 38,13, 35,100), t(18,49, 65,49));
            case 'g' -> List.of(t(76,54, 54,49, 27,57, 19,78, 35,98, 62,98, 78,82), t(78,78, 74,111, 53,118, 28,108));
            case 'h' -> List.of(t(18,5, 18,100), t(18,58, 40,49, 68,58, 72,100));
            case 'i' -> List.of(t(49,50, 49,100), t(49,22, 49,27));
            case 'j' -> List.of(t(55,50, 55,103, 43,117, 22,110), t(55,22, 55,27));
            case 'k' -> List.of(t(18,5, 18,100), t(72,50, 18,76, 75,100));
            case 'l' -> List.of(t(48,5, 48,100));
            case 'm' -> List.of(t(14,100, 14,54, 32,49, 48,61, 50,100), t(50,61, 65,49, 83,60, 85,100));
            case 'n' -> List.of(t(18,100, 18,53, 42,49, 70,61, 72,100));
            case 'o' -> List.of(t(48,49, 70,56, 78,77, 66,97, 39,99, 19,81, 23,58, 48,49));
            case 'p' -> List.of(t(18,118, 18,51, 43,49, 72,60, 75,80, 60,98, 34,97, 18,83));
            case 'q' -> List.of(t(76,118, 76,53), t(76,54, 54,49, 28,57, 20,78, 35,98, 62,98, 76,83));
            case 'r' -> List.of(t(18,100, 18,53, 41,50, 65,60));
            case 's' -> List.of(t(74,60, 55,49, 29,53, 22,67, 40,75, 67,78, 73,91, 55,100, 27,94));
            case 't' -> List.of(t(48,18, 48,86, 58,99, 73,96), t(27,49, 67,49));
            case 'u' -> List.of(t(18,51, 18,83, 31,99, 55,98, 73,83, 73,51, 73,100));
            case 'v' -> List.of(t(16,51, 47,100, 78,51));
            case 'w' -> List.of(t(11,51, 27,100, 47,70, 67,100, 84,51));
            case 'x' -> List.of(t(18,51, 77,100), t(77,51, 18,100));
            case 'y' -> List.of(t(17,51, 45,98, 73,51), t(45,98, 36,114, 20,117));
            case 'z' -> List.of(t(18,51, 77,51, 20,100, 79,100));
            default -> throw new IllegalArgumentException("carácter no soportado: " + c);
        };
    }

    static double radio(char c, int indice, int total) {
        // Un pulso muy suave mantiene la lectura tipográfica y evita un trazo mecánico uniforme.
        double fase = (c * 0.37 + indice * 1.71) % (2 * Math.PI);
        return 5.0 + 1.35 * Math.sin(fase) + 0.55 * Math.cos(fase * 2.0 + total);
    }

    /** Cada trazo es una polilínea de nodos propios, sin unir a los demás: un Grafo conexo por trazo y el glifo es su Grupo. */
    static Geometria glifo(char c, int ox, int oy) {
        List<Trazo> trazos = dibujo(c);
        int cuenta = trazos.stream().mapToInt(q -> q.xy.length / 2).sum();
        int i = 0;
        List<Geometria> partes = new java.util.ArrayList<>();
        for (Trazo trazo : trazos) {
            Grafo forma = new Grafo().mezcla(Mezcla.RGB).suavizado(1.35);
            int n = trazo.xy.length / 2;
            Grafo.Nodo[] nodos = new Grafo.Nodo[n];
            for (int k = 0; k < n; k++, i++) {
                double x = ox + MARGEN + trazo.xy[2 * k] * (CELDA_W - 2.0 * MARGEN) / 100.0;
                double y = oy + MARGEN + trazo.xy[2 * k + 1] * (CELDA_H - 2.0 * MARGEN) / 100.0;
                nodos[k] = forma.nodo(x, y, radio(c, i, cuenta)).color(TINTA);
            }
            for (int k = 0; k + 1 < n; k++) forma.arista(nodos[k], nodos[k + 1]);
            partes.add(forma.relajar().alisar());
        }
        return partes.size() == 1 ? partes.get(0) : new Grupo(partes.toArray(new Geometria[0]));
    }

    public static void main(String[] args) throws Exception {
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        int columnas = 6, filas = (caracteres.length() + columnas - 1) / columnas;
        BufferedImage imagen = new BufferedImage(columnas * CELDA_W, filas * CELDA_H, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) imagen.getRaster().getDataBuffer()).getData(), FONDO);
        for (int i = 0; i < caracteres.length(); i++) {
            int col = i % columnas, fila = i / columnas;
            Plotter.raster(imagen).plot(glifo(caracteres.charAt(i), col * CELDA_W, fila * CELDA_H));
        }
        File salida = new File("abecedario_by_codex.png");
        ImageIO.write(imagen, "png", salida);
        System.out.printf("%s: %d glifos, 6 columnas, radios variables y suavizado de uniones.%n",
                salida.getName(), caracteres.length());
    }
}
