import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Ejecuta todas las suites de pruebas, cada una en su propia JVM (terminan con System.exit si falla alguna), con
 * el directorio de trabajo en target/imagenes, donde dejan sus imágenes. Uso: Todas <classpath>.
 */
public class Todas {
    static final String[] SUITES = {
            "GotaRegresion", "GotaCadena", "GotaCombina", "GotaElipses", "GotaDisco",
            "TexturaReferencia", "TapasDisco", "ComposicionPruebas"
    };

    public static void main(String[] args) throws Exception {
        String cp0 = args.length > 0 ? args[0] : System.getProperty("java.class.path");
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
        int total = 0;
        for (String s : SUITES) {
            Process p = new ProcessBuilder(java, "-Djava.awt.headless=true", "-Dstdout.encoding=UTF-8", "-cp", cp, s)
                    .directory(dir).redirectErrorStream(true).start();
            String ultima = "";
            try (var in = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), "UTF-8"))) {
                for (String l; (l = in.readLine()) != null; ) if (l.contains("pruebas,")) ultima = l.trim();
            }
            int code = p.waitFor();
            System.out.printf("%-20s %s%n", s, ultima.isEmpty() ? "(sin resumen)" : ultima);
            if (code != 0) fallidas.add(s);
            total++;
        }
        System.out.printf("%n%d suites, %d con fallos%n", total, fallidas.size());
        if (!fallidas.isEmpty()) throw new IllegalStateException("suites con fallos: " + fallidas);
    }
}
