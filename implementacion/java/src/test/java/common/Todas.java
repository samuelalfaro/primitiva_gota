package common;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ejecuta las suites de pruebas, cada una en su propia JVM (terminan con System.exit si falla alguna), con el
 * directorio de trabajo en target/imagenes, donde dejan sus imágenes. Las suites van por niveles: <em>unitarias</em>
 * (lógica y matemática, sin renders de referencia), <em>regresion</em> (pintados concretos contra referencias) y
 * <em>estudio</em> (las matrices y el esquema; el coste real está en el render de sus imágenes, no en las aserciones).
 * Uso: Todas &lt;classpath&gt; [nivel,...]; sin niveles, todas.
 */
public class Todas {

    /** Niveles y sus suites, en orden de ejecución. */
    static final String[][] NIVELES = {
         // Lógica y matemática, sin referencias que comparar: segundos.
        {"unitarias",
          "common.ArquitecturaPruebas"
         ,"geometria.composicion.ComposicionPruebas"
         ,"coordenadas.ContornoPruebas"
         ,"geometria.composicion.PintadaPruebas"
         ,"geometria.composicion.GrafoGeometriaPruebas"
         ,"geometria.GrupoPruebas"
         ,"pintado.AlambreSvgGeometriaPruebas"},
         //FIXME "SiluetaPruebas" y "SiluetaCurvaPruebas" irían aquí: la primera pasa (136/0), la segunda tiene un
         //FIXME fallo preexistente y documentado (#54, relajo LOCAL del camino en S).
         // Pintados de casos concretos contra referencias: menos de 1 minuto.
        {"regresion",
          "forma.GotaRegresion"
         ,"color.GotaCombina"
         ,"color.TexturaReferencia"
         ,"color.TapasDisco"
         ,"color.GotaDisco"
         ,"color.GotaCadena"},
         // Matrices y esquema: casi 9 minutos de render en hardware modesto; en máquinas potentes, menos de 1.
        {"estudio",
          "pintado.EsquemaPruebas"
         ,"forma.SuiteMatriz_01_Alambre"
         ,"forma.SuiteMatriz_02_Siluetas"
         ,"forma.SuiteMatriz_03_Colores"
         ,"forma.SuiteMatriz_04_Limites"
         ,"forma.SuiteMatriz_05_Acercamiento"}
    };

    /** Líneas de la salida de una suite que se muestran si falla. */
    static final int COLA = 40;

    public static void main(String[] args) throws Exception {
        String cp0 = args.length > 0 ? args[0] : System.getProperty("java.class.path");
        StringBuilder sbN = new StringBuilder();
        for (int i = 1; i < args.length; i++) sbN.append(args[i]).append(',');
        Set<String> pedir = new HashSet<>();
        for (String n : sbN.toString().split(",")) if (!n.isBlank()) pedir.add(n.trim());

        List<String[]> elegir = new ArrayList<>();   // {nivel, suite}
        if (pedir.isEmpty()) {
            for (String[] nivel : NIVELES)
                for (int i = 1; i < nivel.length; i++) elegir.add(new String[] { nivel[0], nivel[i] });
        } else {
            StringBuilder nombres = new StringBuilder();
            for (String[] nivel : NIVELES) {
                if (pedir.contains(nivel[0]))
                    for (int i = 1; i < nivel.length; i++) elegir.add(new String[] { nivel[0], nivel[i] });
                nombres.append(nivel[0]).append(' ');
            }
            for (String[] nivel : NIVELES) pedir.remove(nivel[0]);
            if (!pedir.isEmpty()) {
                System.out.println("niveles desconocidos: " + pedir + "; disponibles: " + nombres.toString().trim());
                System.exit(2);
            }
        }
        ejecutalas(cp0, elegir);
    }

    private static void ejecutalas(String cp0, List<String[]> elegir) throws Exception {
        StringBuilder sb = new StringBuilder();   // rutas absolutas: cada suite se ejecuta en otro directorio
        for (String e : cp0.split(File.pathSeparator)) {
            if (sb.length() > 0) sb.append(File.pathSeparator);
            sb.append(new File(e).getAbsolutePath());
        }
        String cp = sb.toString();
        File dir = new File("target/imagenes");
        dir.mkdirs();
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        List<String> fallidas = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        for (String[] par : elegir) {
            String s = par[1];
            long u0 = System.currentTimeMillis();
            Process p = new ProcessBuilder(java, "-Djava.awt.headless=true", "-Dstdout.encoding=UTF-8", "-cp", cp, s)
                    .directory(dir).redirectErrorStream(true).start();
            String ultima = "";
            java.util.ArrayDeque<String> cola = new java.util.ArrayDeque<>();   // últimas líneas, para diagnosticar un fallo
            try (var in = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), "UTF-8"))) {
                for (String l; (l = in.readLine()) != null; ) {
                    if (l.contains("pruebas,")) ultima = l.trim();
                    if (cola.size() == COLA) cola.removeFirst();
                    cola.addLast(l);
                }
            }
            int code = p.waitFor();
            System.out.printf("%-22s %-10s %6.1f s  %s%n", s, par[0], (System.currentTimeMillis() - u0) / 1000.0,
                ultima.isEmpty() ? "(sin resumen, salida " + code + ")" : ultima);
            if (code != 0) {
                fallidas.add(s);
                System.out.println("  --- últimas líneas de " + s + " (salida " + code + ") ---");
                for (String l : cola) System.out.println("  | " + l);
            }
        }
        System.out.printf("%n%d suites, %d con fallos, %.1f s%n", elegir.size(), fallidas.size(),
            (System.currentTimeMillis() - t0) / 1000.0);
        if (!fallidas.isEmpty()) throw new IllegalStateException("suites con fallos: " + fallidas);
    }
}
