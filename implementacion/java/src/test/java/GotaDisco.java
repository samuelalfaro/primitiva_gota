import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;
import java.util.Random;
import javax.imageio.ImageIO;

/** Mapeo en disco: semejanza por círculo, extremos, interpolación del ángulo (Tono), orientaciones y galería. */
public class GotaDisco {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-84s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    static Point2D.Double p(double x, double y) { return new Point2D.Double(x, y); }
    static int[] datos(BufferedImage i) { return ((DataBufferInt) i.getRaster().getDataBuffer()).getData(); }

    /** Textura que registra las coordenadas pedidas. */
    static final double[] ultima = new double[2];
    static final Texturas.Textura REGISTRA = (u, v) -> { ultima[0] = u; ultima[1] = v; return 0xFFFFFFFF; };

    static double[] coords(Gota.Relleno rel, Coordenadas.Muestra m) { rel.argb(m); return new double[] { ultima[0], ultima[1] }; }

    static boolean igual(double[] a, double x, double y) { return Math.abs(a[0] - x) < 1e-12 && Math.abs(a[1] - y) < 1e-12; }

    public static void main(String[] args) throws Exception {
        extremos();
        giros();
        degenerados();
        orientaciones();
        galeria();
        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }

    static void extremos() {
        Texturas.Disco d1 = new Texturas.Disco(0.3, 0.4, 0.5, 0.4), d2 = new Texturas.Disco(0.7, 0.6, 0.7, 0.9);   // r = 0.2 hacia +x; r = 0.3 hacia +y
        Gota.Relleno rel = Texturas.disco(REGISTRA, d1, d2);
        comprueba("centro del círculo 1 -> centro del disco 1", igual(coords(rel, new Coordenadas.Muestra(0, 0, 0, 0)), 0.3, 0.4), "");
        comprueba("centro del círculo 2 -> centro del disco 2", igual(coords(rel, new Coordenadas.Muestra(1, 0, 0, 0)), 0.7, 0.6), "");
        comprueba("proa del círculo 1 (axial 1) -> punto del borde e1", igual(coords(rel, new Coordenadas.Muestra(0, 1, 1, 0)), 0.5, 0.4), "");
        comprueba("proa del círculo 2 (axial 1) -> punto del borde e2", igual(coords(rel, new Coordenadas.Muestra(1, 1, 1, 0)), 0.7, 0.9), "");
        double[] m = coords(rel, new Coordenadas.Muestra(0.5, 0, 0, 0));
        comprueba("centro a mitad de camino: centros interpolados", Math.abs(m[0] - 0.5) < 1e-12 && Math.abs(m[1] - 0.5) < 1e-12, "");

        // lateral = +1 (a la derecha del avance en pantalla) con tangente +x y tira hacia +y
        Gota.Relleno uno = Texturas.disco(REGISTRA, Texturas.Disco.inscrito(), Texturas.Disco.inscrito());
        double[] l = coords(uno, new Coordenadas.Muestra(0.3, 1, 0, 1));
        comprueba("lateral +1 (izquierda del avance en pantalla) -> arriba en la textura: (0.5, 0)",
                  Math.abs(l[0] - 0.5) < 1e-12 && Math.abs(l[1] - 0.0) < 1e-12, String.format("(%.3f, %.3f)", l[0], l[1]));

        // interpolación de la escala
        double[] s = coords(rel, new Coordenadas.Muestra(0.5, 1, 1, 0));
        double esc = (0.2 + 0.3) / 2, th = Math.toRadians(Texturas.interpolaAngulo(0, 90, 0.5, Mezcla.Tono.CORTO));
        comprueba("escala y ángulo interpolados en t = 1/2", Math.abs(s[0] - (0.5 + esc * Math.cos(th))) < 1e-12 && Math.abs(s[1] - (0.5 + esc * Math.sin(th))) < 1e-12, "");

        boolean lanza = false;
        try { new Texturas.Disco(Double.NaN, 0, 1, 1); } catch (IllegalArgumentException e) { lanza = true; }
        try { Texturas.disco(null); lanza &= false; } catch (NullPointerException e) { }
        comprueba("disco no finito y textura nula rechazados", lanza, "");
        boolean lanza2 = false;
        try { Texturas.disco(REGISTRA).argb(0.5, 0.5); } catch (UnsupportedOperationException e) { lanza2 = true; }
        comprueba("argb(t, rho) exige la muestra", lanza2, "");
    }

    static void degenerados() {
        // borde sobre el centro: radio 0, color plano del texel (cu, cv), sin NaN
        Texturas.Disco cero = new Texturas.Disco(0.5, 0.5, 0.5, 0.5);
        double[] c = coords(Texturas.disco(REGISTRA, cero, cero, Texturas.Orientacion.TANGENTE, Mezcla.Tono.CORTO),
                new Coordenadas.Muestra(0.3, 1, 0.7, 0.2));
        comprueba("disco de radio 0 en los dos extremos: muestrea siempre su centro", igual(c, 0.5, 0.5), "");
        // un solo extremo degenerado: la escala interpolada es la mitad, finita
        Texturas.Disco uno = new Texturas.Disco(0.5, 0.5, 1, 0.5);
        double[] m = coords(Texturas.disco(REGISTRA, cero, uno, Texturas.Orientacion.TANGENTE, Mezcla.Tono.CORTO),
                new Coordenadas.Muestra(0.5, 1, 1, 0));
        comprueba("un extremo de radio 0: la escala se interpola y el resultado es finito",
                Double.isFinite(m[0]) && Double.isFinite(m[1]) && Math.abs(Math.hypot(m[0] - 0.5, m[1] - 0.5) - 0.25) < 1e-12,
                Arrays.toString(m));
        // interpolaAngulo se devuelve normalizada a [0, 360): 350° -> 10° pasa por 360 sin salirse del intervalo
        boolean rango = true, extremosOk = true;
        Random rnd = new Random(11);
        for (int i = 0; i < 2000; i++) {
            double a0 = rnd.nextDouble() * 1440 - 720, a1 = rnd.nextDouble() * 1440 - 720, u = rnd.nextDouble();
            for (Mezcla.Tono t : Mezcla.Tono.values()) {
                double v = Texturas.interpolaAngulo(a0, a1, u, t);
                if (!(v >= 0 && v < 360)) rango = false;
                double v0 = Texturas.interpolaAngulo(a0, a1, 0, t), v1 = Texturas.interpolaAngulo(a0, a1, 1, t);
                double e0 = Math.abs(((v0 - a0 + 180) % 360 + 360) % 360 - 180), e1 = Math.abs(((v1 - a1 + 180) % 360 + 360) % 360 - 180);
                if (e0 > 1e-9 || e1 > 1e-9) extremosOk = false;
            }
        }
        comprueba("interpolaAngulo devuelve siempre un ángulo en [0, 360)", rango, "");
        comprueba("interpolaAngulo sigue valiendo a0 y a1 (módulo 360) en u = 0 y u = 1", extremosOk, "");
        comprueba("350° -> 10° por el camino corto, u = 1: 10° y no 370°",
                Math.abs(Texturas.interpolaAngulo(350, 10, 1, Mezcla.Tono.CORTO) - 10) < 1e-9, "");
    }

    static void giros() {
        // ángulos de los extremos: 10° y 350° (|e - c| = 1)
        Texturas.Disco a = new Texturas.Disco(0, 0, Math.cos(Math.toRadians(10)), Math.sin(Math.toRadians(10)));
        Texturas.Disco b = new Texturas.Disco(0, 0, Math.cos(Math.toRadians(350)), Math.sin(Math.toRadians(350)));
        double[] esperado = { 0, 180, 180, 0 };   // ángulo a mitad de camino: CORTO por 0°, LARGO por 180°, HORARIO (creciente) por 180°, ANTIHORARIO por 0°
        Mezcla.Tono[] tonos = Mezcla.Tono.values();
        for (int i = 0; i < tonos.length; i++) {
            double[] c = coords(Texturas.disco(REGISTRA, a, b, Texturas.Orientacion.TANGENTE, tonos[i]), new Coordenadas.Muestra(0.5, 1, 1, 0));
            double ang = Math.toDegrees(Math.atan2(c[1], c[0]));
            double dif = Math.abs(((ang - esperado[i] + 180) % 360 + 360) % 360 - 180);
            comprueba("giro " + tonos[i] + ": ángulo a mitad de camino = " + esperado[i] + "°", dif < 1e-9, String.format("%.4f°", ang));
        }
        // extremos exactos en los cuatro sentidos
        boolean ok = true;
        for (Mezcla.Tono t : tonos) {
            Gota.Relleno r = Texturas.disco(REGISTRA, a, b, Texturas.Orientacion.TANGENTE, t);
            double[] c0 = coords(r, new Coordenadas.Muestra(0, 1, 1, 0)), c1 = coords(r, new Coordenadas.Muestra(1, 1, 1, 0));
            ok &= Math.abs(c0[0] - a.eu()) < 1e-12 && Math.abs(c0[1] - a.ev()) < 1e-12 && Math.abs(c1[0] - b.eu()) < 1e-12 && Math.abs(c1[1] - b.ev()) < 1e-12;
        }
        comprueba("los cuatro giros dan exactamente e1 y e2 en los extremos", ok, "");
    }

    static void orientaciones() {
        Texturas.Disco d1 = new Texturas.Disco(0.5, 0.5, 0.9, 0.5), d2 = new Texturas.Disco(0.5, 0.5, 0.9, 0.5);   // mismo disco: nada depende de t
        // camino horizontal (T = +x): TANGENTE y FIJA coinciden
        Gota h = Gota.lineal(p(50, 100), 30, p(200, 100), 30, Gota.Perfil.recto());
        Gota v = Gota.lineal(p(100, 50), 30, p(100, 200), 30, Gota.Perfil.recto());
        Random rnd = new Random(3);
        int malosH = 0, malosV = 0, malosT = 0, n = 0;
        for (int i = 0; i < 400; i++) {
            double x = 40 + rnd.nextDouble() * 170, y = 70 + rnd.nextDouble() * 60;
            if (!h.contiene(x, y)) continue;
            Gota.Relleno rt = Texturas.disco(REGISTRA, d1, d2, Texturas.Orientacion.TANGENTE).para(h);
            Gota.Relleno rf = Texturas.disco(REGISTRA, d1, d2, Texturas.Orientacion.FIJA).para(h);
            Coordenadas.Muestra m = h.parametros(x, y);
            double[] a = coords(rt, m), b = coords(rf, m);
            n++;
            if (Math.abs(a[0] - b[0]) > 1e-9 || Math.abs(a[1] - b[1]) > 1e-9) malosH++;
            // FIJA: la coordenada de textura es la del desplazamiento en pantalla normalizado
            double cx = h.centro(m.t()).getX(), cy = h.centro(m.t()).getY(), r = h.radio(m.t());
            double ex = 0.5 + 0.4 * ((x - cx) / r), ey = 0.5 + 0.4 * ((y - cy) / r);
            if (Math.abs(b[0] - ex) > 1e-9 || Math.abs(b[1] - ey) > 1e-9) malosT++;
        }
        for (int i = 0; i < 400; i++) {   // camino vertical: FIJA sigue siendo el desplazamiento en pantalla; TANGENTE no
            double x = 70 + rnd.nextDouble() * 60, y = 40 + rnd.nextDouble() * 170;
            if (!v.contiene(x, y)) continue;
            Gota.Relleno rf = Texturas.disco(REGISTRA, d1, d2, Texturas.Orientacion.FIJA).para(v);
            Coordenadas.Muestra m = v.parametros(x, y);
            double[] b = coords(rf, m);
            double cx = v.centro(m.t()).getX(), cy = v.centro(m.t()).getY(), r = v.radio(m.t());
            double ex = 0.5 + 0.4 * ((x - cx) / r), ey = 0.5 + 0.4 * ((y - cy) / r);
            if (Math.abs(b[0] - ex) > 1e-9 || Math.abs(b[1] - ey) > 1e-9) malosV++;
        }
        comprueba("camino horizontal: TANGENTE == FIJA", malosH == 0 && n > 100, n + " píxeles");
        comprueba("FIJA, camino horizontal: textura = desplazamiento en pantalla normalizado", malosT == 0, "");
        comprueba("FIJA, camino vertical: textura = desplazamiento en pantalla normalizado (no gira)", malosV == 0, "");

        // TANGENTE con camino vertical: la textura gira 90° respecto a FIJA
        Gota.Relleno rt = Texturas.disco(REGISTRA, d1, d2, Texturas.Orientacion.TANGENTE).para(v);
        Coordenadas.Muestra m = v.parametros(100, 110 + 8);
        double[] a = coords(rt, m);
        double cx = v.centro(m.t()).getX(), cy = v.centro(m.t()).getY(), r = v.radio(m.t());
        double x = (100 - cx) / r, y = (118 - cy) / r;   // desplazamiento en pantalla
        // con T = +y: eje x' = +y, eje y' = -x (N' = T girado +90° = (-Ty, Tx) = (-1, 0))
        double xl = y, yl = -x;
        comprueba("TANGENTE, camino vertical: el sistema local gira con el camino", Math.abs(a[0] - (0.5 + 0.4 * xl)) < 1e-9 && Math.abs(a[1] - (0.5 + 0.4 * yl)) < 1e-9, "");

        // elipses: las coordenadas locales son las normalizadas por cada eje, también en FIJA
        Gota e = Gota.lineal(p(50, 100), 20, p(200, 100), 20, Gota.Perfil.recto().aspecto(2.5));
        Gota.Relleno re = Texturas.disco(REGISTRA, d1, d2, Texturas.Orientacion.TANGENTE).para(e);
        int malosE = 0, nE = 0;
        for (int i = 0; i < 300; i++) {
            double px = 20 + rnd.nextDouble() * 230, py = 70 + rnd.nextDouble() * 60;
            if (!e.contiene(px, py)) continue;
            Coordenadas.Muestra me = e.parametros(px, py);
            double[] c = coords(re, me);
            double cxx = e.centro(me.t()).getX(), cyy = e.centro(me.t()).getY(), b = e.radio(me.t()), aa = e.radioTangente(me.t());
            double ex = 0.5 + 0.4 * ((px - cxx) / aa), ey = 0.5 + 0.4 * ((py - cyy) / b);   // camino horizontal: eje x' = x / a, eje y' = y / b
            nE++;
            if (Math.abs(c[0] - ex) > 1e-9 || Math.abs(c[1] - ey) > 1e-9) malosE++;
        }
        comprueba("elipses: x' = d·T / a y y' = d·N' / b (el disco es el círculo unidad normalizado)", malosE == 0 && nE > 100, nE + " píxeles");

        // render con combinar y orden de los parámetros: no revienta con FIJA en un camino curvo en PLANO y TRAZO
        Gota arco = Gota.curva(p(60, 240), 25, new Gota.Vec(0, -200), p(240, 240), 15, new Gota.Vec(0, -200), Gota.Perfil.recto());
        boolean ok = true;
        for (Gota.Solape s : Gota.Solape.values()) {
            BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
            try {
                arco.conSolape(s).render(img, ComposicionColor.combinar(Colores.cadena(0xFF1E1B4B, 0xFFFFE27A, 0xFF2563EB, 0xFF7C2D12),
                        Texturas.disco(REGISTRA, d1, d2, Texturas.Orientacion.FIJA), ComposicionColor.Modo.MODULATE));
            } catch (RuntimeException ex) { ok = false; }
        }
        comprueba("combinar(cadena, disco FIJA) se renderiza en PLANO y TRAZO sobre un arco", ok, "");
    }

    static void galeria() throws Exception {
        BufferedImage base = ImageIO.read(GotaDisco.class.getResource("/gota_textura_base.png"));
        Texturas.Textura tex = Texturas.deImagen(base, Texturas.Borde.SUJETAR);
        BufferedImage img = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(datos(img), 0xFFFFFFFF);
        Texturas.Disco ins = Texturas.Disco.inscrito();
        Texturas.Disco peq = new Texturas.Disco(0.5, 0.5, 0.75, 0.5), giro = new Texturas.Disco(0.5, 0.5, 0.5, 0.75);
        for (int i = 0; i < 6; i++) {
            double ox = (i % 3) * 300, oy = (i / 3) * 300;
            Gota g = i < 3 ? Gota.lineal(p(ox + 70, oy + 150), 55, p(ox + 230, oy + 150), 35, Gota.Perfil.recto())
                           : Gota.curva(p(ox + 70, oy + 235), 35, new Gota.Vec(0, -200), p(ox + 230, oy + 235), 25, new Gota.Vec(0, -200), Gota.Perfil.recto());
            Gota.Relleno rel = switch (i % 3) {
                case 0 -> Texturas.disco(tex);
                case 1 -> Texturas.disco(tex, ins, giro, Texturas.Orientacion.TANGENTE, Mezcla.Tono.CORTO);
                default -> Texturas.disco(tex, peq, peq, Texturas.Orientacion.FIJA);
            };
            g.render(img, rel);
        }
        ImageIO.write(img, "png", new File("gota_disco.png"));
    }
}
