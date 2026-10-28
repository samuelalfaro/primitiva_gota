import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;
import java.util.Random;
import javax.imageio.ImageIO;

/** Color x textura: fórmulas de glTexEnv, casos límite, delegación en la gota y galería. */
public class GotaCombina {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-72s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    static Point2D.Double p(double x, double y) { return new Point2D.Double(x, y); }
    static int[] datos(BufferedImage i) { return ((DataBufferInt) i.getRaster().getDataBuffer()).getData(); }
    static int canal(int c, int s) { return (c >> s) & 0xFF; }

    /** Referencia independiente: las fórmulas de la especificación de glTexEnv, escritas a mano. */
    static int ref(Rellenos.Modo m, int cf, int ct, int ce) {
        double af = (cf >>> 24) / 255.0, at = (ct >>> 24) / 255.0;
        double[] c = new double[3];
        for (int k = 0; k < 3; k++) {
            int s = 16 - 8 * k;
            double f = canal(cf, s) / 255.0, t = canal(ct, s) / 255.0, e = canal(ce, s) / 255.0;
            c[k] = switch (m) {
                case REPLACE -> t;
                case MODULATE -> f * t;
                case DECAL -> f * (1 - at) + t * at;
                case BLEND -> f * (1 - t) + e * t;
                case ADD -> f + t > 1 ? 1 : f + t;
            };
        }
        double a = m == Rellenos.Modo.REPLACE ? at : m == Rellenos.Modo.DECAL ? af : af * at;
        int[] o = new int[4];
        o[0] = (int) Math.floor(a * 255 + 0.5);
        for (int k = 0; k < 3; k++) o[k + 1] = (int) Math.floor(c[k] * 255 + 0.5);
        return o[0] << 24 | o[1] << 16 | o[2] << 8 | o[3];
    }

    static boolean cerca(int a, int b, int tol) {
        for (int s = 0; s < 32; s += 8) if (Math.abs(canal(a, s) - canal(b, s)) > tol) return false;
        return true;
    }

    public static void main(String[] args) throws Exception {
        formulas();
        limites();
        enLaGota();
        galeria();
        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }

    static void formulas() {
        Random rnd = new Random(5);
        for (Rellenos.Modo m : Rellenos.Modo.values()) {
            int peor = 0;
            for (int i = 0; i < 20000; i++) {
                int cf = rnd.nextInt(), ct = rnd.nextInt(), ce = rnd.nextInt();
                int a = Rellenos.aplica(m, cf, ct, ce), b = ref(m, cf, ct, ce);
                for (int s = 0; s < 32; s += 8) peor = Math.max(peor, Math.abs(canal(a, s) - canal(b, s)));
            }
            comprueba("modo " + m + ": 20000 combinaciones aleatorias contra la fórmula de glTexEnv", peor <= 1, "dif. máx " + peor);
        }
    }

    static void limites() {
        int rojo = 0xFFCC3322, tex = 0xFF3377DD, blanco = 0xFFFFFFFF, negro = 0xFF000000, ce = 0xFF00FF00;
        comprueba("MODULATE con color blanco opaco devuelve la textura", Rellenos.aplica(Rellenos.Modo.MODULATE, blanco, tex, ce) == tex, "");
        comprueba("MODULATE con textura blanca opaca devuelve el color", Rellenos.aplica(Rellenos.Modo.MODULATE, rojo, blanco, ce) == rojo, "");
        comprueba("REPLACE devuelve la textura, también con su alfa", Rellenos.aplica(Rellenos.Modo.REPLACE, rojo, 0x803377DD, ce) == 0x803377DD, "");
        comprueba("DECAL con alfa de textura 0 deja el color y con 255 la textura",
                  Rellenos.aplica(Rellenos.Modo.DECAL, rojo, 0x003377DD, ce) == rojo
                  && Rellenos.aplica(Rellenos.Modo.DECAL, rojo, tex, ce) == tex, "");
        comprueba("DECAL conserva el alfa del color", (Rellenos.aplica(Rellenos.Modo.DECAL, 0x80CC3322, tex, ce) >>> 24) == 0x80, "");
        comprueba("BLEND: texel negro -> color; texel blanco -> color de entorno",
                  Rellenos.aplica(Rellenos.Modo.BLEND, rojo, negro, ce) == rojo
                  && Rellenos.aplica(Rellenos.Modo.BLEND, rojo, blanco, ce) == ce, "");
        comprueba("ADD satura en 255 y no desborda", Rellenos.aplica(Rellenos.Modo.ADD, 0xFFC0C0C0, 0xFFC0C0C0, ce) == 0xFFFFFFFF, "");
        comprueba("el alfa de MODULATE es el producto de alfas", (Rellenos.aplica(Rellenos.Modo.MODULATE, 0x80FFFFFF, 0x80FFFFFF, ce) >>> 24) == 64, "");

        boolean lanza = true;
        try { Rellenos.combinar(null, Gota.Relleno.solido(0), Rellenos.Modo.ADD); lanza = false; } catch (NullPointerException e) { }
        try { Rellenos.combinar(Gota.Relleno.solido(0), null, Rellenos.Modo.ADD); lanza = false; } catch (NullPointerException e) { }
        try { Rellenos.combinar(Gota.Relleno.solido(0), Gota.Relleno.solido(0), null); lanza = false; } catch (NullPointerException e) { }
        comprueba("argumentos nulos rechazados", lanza, "");
    }

    /** Textura sintética con alfa: escala en u, bandas en v, mitad inferior semitransparente. */
    static Texturas.Textura prueba() {
        BufferedImage im = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                boolean dama = ((x / 8) + (y / 8)) % 2 == 0;
                int a = y < 40 ? 255 : 170;
                im.setRGB(x, y, a << 24 | (dama ? 0xF2F2F2 : 0x4A5A8A));
            }
        return Texturas.deImagen(im);
    }

    static void enLaGota() {
        Texturas.Textura tx = prueba();
        Gota g = Gota.lineal(p(60.5, 150.5), 50, p(240.5, 150.5), 20, Gota.Perfil.radioMedio(30, 0.4, 0.5));
        int cc = 0xFFE05A3C;

        // REPLACE con un color sólido == textura sola, píxel a píxel
        BufferedImage a = lienzo(), b = lienzo();
        g.render(a, Rellenos.textura(tx));
        g.render(b, Rellenos.combinar(Gota.Relleno.solido(cc), Rellenos.textura(tx), Rellenos.Modo.REPLACE));
        comprueba("combinar(sólido, textura, REPLACE) == textura sola (píxel a píxel)", Arrays.equals(datos(a), datos(b)), "");

        // MODULATE: cada píxel interior coincide con la fórmula aplicada al color y al texel de ese píxel
        Rellenos.Modo[] ms = Rellenos.Modo.values();
        for (Rellenos.Modo m : ms) {
            Gota.Relleno color = Rellenos.cadena(0xFF1E1B4B, 0xFFFFE27A, 0xFF2563EB, 0xFF7C2D12);
            Gota.Relleno comb = Rellenos.combinar(color, Rellenos.textura(tx), m, 0xFF20C040).para(g);
            Gota.Relleno c2 = color.para(g), t2 = Rellenos.textura(tx).para(g);
            int malos = 0, n = 0;
            for (int y = 100; y < 200; y += 3)
                for (int x = 10; x < 260; x += 3) {
                    if (!g.contiene(x + 0.5, y + 0.5)) continue;
                    Gota.Muestra mu = g.parametros(x + 0.5, y + 0.5);
                    Gota.Uv uv = g.uv(x + 0.5, y + 0.5);
                    int esperado = ref(m, c2.argb(mu, uv), t2.argb(mu, uv), 0xFF20C040);
                    n++;
                    if (!cerca(comb.argb(mu, uv), esperado, 1)) malos++;
                }
            comprueba("gota: " + m + " por píxel == fórmula(color de la cadena, texel de la tira)", malos == 0 && n > 500, n + " píxeles, " + malos + " malos");
        }

        // la tira de Rellenos y la textura comparten Uv: tira(tex) == textura(tex) con cortes por defecto
        BufferedImage c = lienzo(), d = lienzo();
        g.render(c, Rellenos.textura(tx));
        g.render(d, Rellenos.tira(tx));
        comprueba("tira(tex) == textura(tex) con los cortes por defecto", Arrays.equals(datos(c), datos(d)), "");

        // tira con cortes explícitos: el corte central sigue a tm
        double[] ult = new double[2];
        Gota gm = Gota.lineal(p(60.5, 150.5), 50, p(240.5, 150.5), 20, Gota.Perfil.radioMedio(30, 0.3, 1));
        Gota.Relleno rt = Rellenos.tira((u, v) -> { ult[0] = u; ult[1] = v; return 0xFFFFFFFF; }, 0, 0, 1, 1, 0.1, 0.9).para(gm);
        Point2D cm = gm.centro(gm.tm());
        rt.argb(gm.parametros(cm.getX(), cm.getY()), gm.uv(cm.getX(), cm.getY()));
        comprueba("tira(tex, ..., c1, c3): v en el centro de t = tm es c1 + (c3 - c1)·tm", Math.abs(ult[1] - (0.1 + 0.8 * 0.3)) < 2e-3, String.format("v = %.4f", ult[1]));

        // multitextura (combinaciones anidadas) en PLANO y en TRAZO: cada píxel sigue la fórmula encadenada
        Gota.Relleno multi = Rellenos.combinar(Rellenos.combinar(Gota.Relleno.solido(cc), Rellenos.textura(tx), Rellenos.Modo.MODULATE),
                                               Rellenos.textura(tx), Rellenos.Modo.ADD);
        for (Gota gg : new Gota[] { g, g.conSolape(Gota.Solape.TRAZO) }) {
            Gota.Relleno m2 = multi.para(gg), t2 = Rellenos.textura(tx).para(gg);
            int malos = 0, n = 0;
            for (int y = 100; y < 200; y += 3)
                for (int x = 10; x < 260; x += 3) {
                    if (!gg.contiene(x + 0.5, y + 0.5)) continue;
                    Gota.Muestra mu = gg.parametros(x + 0.5, y + 0.5);
                    Gota.Uv uv = gg.uv(x + 0.5, y + 0.5);
                    int tt = t2.argb(mu, uv);
                    int esperado = ref(Rellenos.Modo.ADD, ref(Rellenos.Modo.MODULATE, cc, tt, 0), tt, 0);
                    n++;
                    if (!cerca(m2.argb(mu, uv), esperado, 1)) malos++;
                }
            comprueba("multitextura en " + gg.solape() + ": MODULATE y luego ADD por píxel", malos == 0 && n > 500, n + " píxeles, " + malos + " malos");
        }

        boolean lanza = false;
        try { Rellenos.combinar(Gota.Relleno.solido(0), Gota.Relleno.solido(0), Rellenos.Modo.ADD).argb(0.5, 0.5); } catch (UnsupportedOperationException x) { lanza = true; }
        comprueba("argb(t, rho) sobre el combinado exige la muestra", lanza, "");
    }

    static BufferedImage lienzo() { return new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB); }

    static void galeria() throws Exception {
        BufferedImage img = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(datos(img), 0xFFFFFFFF);
        Texturas.Textura tx = Texturas.deImagen(javax.imageio.ImageIO.read(GotaCombina.class.getResource("/gota_textura_base.png")), Texturas.Borde.SUJETAR);
        Rellenos.Modo[] ms = Rellenos.Modo.values();
        for (int i = 0; i < 6; i++) {
            double ox = (i % 3) * 300, oy = (i / 3) * 300;
            Gota g = Gota.lineal(p(ox + 60, oy + 150), 50, p(ox + 240, oy + 150), 25, Gota.Perfil.radioMedio(36, 0.5, 1));
            Gota.Relleno color = Rellenos.cadena(0xFF1E1B4B, 0xFFFFC857, 0xFF2563EB, 0xFF7C2D12);
            Rellenos.Modo m = i < 5 ? ms[i] : Rellenos.Modo.MODULATE;
            Gota.Relleno rel = i < 5 ? Rellenos.combinar(color, Rellenos.textura(tx), m, 0xFF20C040)
                                     : Rellenos.combinar(color, Rellenos.tira(tx, 0, 0, 1, 1, 0.1, 0.9), m);
            g.render(img, rel);
        }
        ImageIO.write(img, "png", new File("gota_combina.png"));
    }
}
