package common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Orden de capas de los paquetes: cada paquete solo puede depender de los anteriores de {@link #CAPAS}. Lee los
 * {@code import} y los nombres calificados (fuera de comentarios y cadenas) de {@code src/main/java} y falla si algún
 * paquete usa uno posterior, o si hay un paquete que no está en el orden (hay que decidir dónde va).
 * <p>
 * Orden actual, de abajo a arriba: {@code forma}, {@code coordenadas}, {@code color}, {@code geometria}, {@code pintado},
 * {@code composicion}. {@code utils} queda fuera: todos pueden usarlo y él no puede usar a ninguno. Al mover paquetes hay
 * que actualizar {@link #CAPAS} (por ejemplo, al anidar {@code composicion} en {@code geometria}).
 * <p>
 * El directorio de fuentes se toma de {@code -Dgota.fuentes} o, si no, se busca {@code src/main/java} subiendo desde el
 * directorio de trabajo.
 */
public class ArquitecturaPruebas {
    /** Paquetes de abajo a arriba; un paquete solo puede importar los de índice menor. */
    static final List<String> CAPAS = List.of("forma", "coordenadas", "color", "geometria", "pintado", "composicion");
    /** Paquetes que cualquiera puede usar y que no usan a ninguno del proyecto. */
    static final Set<String> TRANSVERSALES = Set.of("utils");

    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-86s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    // ---------- el análisis, separado de la lectura de ficheros para poder probarlo con grafos inventados ----------

    /** Dependencias prohibidas: (origen → destino) con su motivo, para un mapa paquete → paquetes que usa. */
    static List<String> violaciones(Map<String, Set<String>> usa, List<String> capas, Set<String> transversales) {
        List<String> v = new ArrayList<>();
        for (var e : new TreeMap<>(usa).entrySet()) {
            String o = e.getKey();
            boolean oTrans = transversales.contains(o);
            if (!oTrans && !capas.contains(o)) {
                v.add(o + ": paquete que no está en el orden de capas (decide dónde va)");
                continue;
            }
            for (String d : new TreeSet<>(e.getValue())) {
                if (d.equals(o) || transversales.contains(d)) {
                    if (oTrans && !d.equals(o)) v.add(o + " → " + d + ": un paquete transversal no puede usar otro");
                    continue;
                }
                if (oTrans) { v.add(o + " → " + d + ": un paquete transversal no puede usar los del proyecto"); continue; }
                if (!capas.contains(d)) { v.add(o + " → " + d + ": destino fuera del orden de capas"); continue; }
                if (capas.indexOf(d) > capas.indexOf(o)) v.add(o + " → " + d + ": depende de una capa superior");
            }
        }
        return v;
    }

    // ---------- lectura de los fuentes ----------

    static Path fuentes() {
        String p = System.getProperty("gota.fuentes");
        if (p != null) return Path.of(p);
        Path d = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && d != null; i++, d = d.getParent())
            for (String rel : new String[] { "src/main/java", "implementacion/java/src/main/java" })
                if (Files.isDirectory(d.resolve(rel))) return d.resolve(rel);
        throw new IllegalStateException("no encuentro src/main/java (usa -Dgota.fuentes=ruta)");
    }

    static String sinComentariosNiCadenas(String s) {
        s = s.replaceAll("(?s)/\\*.*?\\*/", "");
        s = s.replaceAll("//[^\\n]*", "");
        return s.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
    }

    /** Paquete → paquetes del proyecto que usa (por import o por nombre calificado). */
    static Map<String, Set<String>> dependencias(Path raiz) throws IOException {
        Set<String> propios = new TreeSet<>();
        try (Stream<Path> dirs = Files.list(raiz)) { dirs.filter(Files::isDirectory).forEach(p -> propios.add(p.getFileName().toString())); }
        Pattern calificado = Pattern.compile("\\b(" + String.join("|", propios) + ")\\.(?:\\*|[A-Z]\\w*)");
        Map<String, Set<String>> usa = new TreeMap<>();
        for (String pk : propios) usa.put(pk, new TreeSet<>());
        for (String pk : propios) {
            try (Stream<Path> fs = Files.list(raiz.resolve(pk))) {
                for (Path f : fs.filter(x -> x.toString().endsWith(".java")).toList()) {
                    String s = sinComentariosNiCadenas(Files.readString(f, StandardCharsets.UTF_8));
                    Matcher m = calificado.matcher(s);
                    while (m.find()) if (!m.group(1).equals(pk)) usa.get(pk).add(m.group(1));
                }
            }
        }
        return usa;
    }

    public static void main(String[] args) throws IOException {
        // 1. el detector detecta (grafos inventados)
        var ok = Map.of("forma", Set.<String>of(), "coordenadas", Set.of("forma"), "color", Set.of("forma", "coordenadas", "utils"), "utils", Set.<String>of());
        comprueba("un grafo en orden no da violaciones", violaciones(ok, List.of("forma", "coordenadas", "color"), Set.of("utils")).isEmpty(), "");
        var arriba = Map.of("forma", Set.of("coordenadas"), "coordenadas", Set.<String>of());
        comprueba("detecta una dependencia hacia una capa superior", violaciones(arriba, List.of("forma", "coordenadas"), Set.of()).size() == 1, "");
        var ciclo = Map.of("a", Set.of("b"), "b", Set.of("a"));
        comprueba("detecta un ciclo (una de las dos aristas va hacia arriba)", violaciones(ciclo, List.of("a", "b"), Set.of()).size() == 1, "");
        var nuevo = Map.of("forma", Set.<String>of(), "nuevo", Set.of("forma"));
        comprueba("detecta un paquete que no está en el orden", violaciones(nuevo, List.of("forma"), Set.of()).size() == 1, "");
        var trans = Map.of("forma", Set.<String>of(), "utils", Set.of("forma"));
        comprueba("un paquete transversal no puede usar los del proyecto", violaciones(trans, List.of("forma"), Set.of("utils")).size() == 1, "");

        // 2. el proyecto de verdad
        Path raiz = fuentes();
        Map<String, Set<String>> usa = dependencias(raiz);
        comprueba("se leen los paquetes esperados", usa.keySet().containsAll(CAPAS) && usa.containsKey("utils"), usa.keySet().toString());
        int aristas = usa.values().stream().mapToInt(Set::size).sum();
        comprueba("se ve alguna dependencia entre paquetes (la lectura no está vacía)", aristas >= 10, aristas + " aristas");
        List<String> v = violaciones(usa, CAPAS, TRANSVERSALES);
        comprueba("el proyecto respeta el orden de capas " + CAPAS, v.isEmpty(), v.isEmpty() ? "" : String.join("; ", v));

        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
