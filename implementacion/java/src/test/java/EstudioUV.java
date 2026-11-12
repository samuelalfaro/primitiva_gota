import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import javax.imageio.ImageIO;
public class EstudioUV {
    /** textura guía de CosturasDemo: lados distintos, líneas transversales en el cuerpo y radios en las tapas */
    static final class Guia implements Texturas.Textura {
        final Texturas.Textura base = Texturas.referenciaLados();
        static final double MEDIO = 0.012, SUAVE = 0.004; static final int ROJO = 0xFFC62828;
        @Override public double aspecto() { return 0.5; }
        static double acota(double x) { return x < 0 ? 0 : x > 1 ? 1 : x; }
        @Override public int muestrea(double u, double v) {
            int c = base.muestrea(u, v); if ((c >>> 24) == 0) return c;
            double y = 2 * v, l = 0;
            if (y < 0.5 || y > 1.5) { double dx = u - 0.5, dy = y < 0.5 ? 0.5 - y : y - 1.5, d = Math.hypot(dx, dy);
                if (d > 0.03 && d <= 0.5) { double th = Math.atan2(dy, dx); for (int k = 1; k <= 5; k++) l = Math.max(l, acota(0.5 + (MEDIO - d * Math.abs(Math.sin(th - k * Math.PI / 6))) / SUAVE)); }
            } else { for (int m = 1; m <= 7; m++) l = Math.max(l, acota(0.5 + (MEDIO - Math.abs(y - (0.5 + m / 8.0))) / SUAVE)); }
            int rgb = 0; for (int s = 16; s >= 0; s -= 8) rgb |= (int) Math.round(((c >> s) & 255) * (1 - l) + ((ROJO >> s) & 255) * l) << s;
            return (c & 0xFF000000) | rgb; }
    }
    static BufferedImage lienzo(int w,int h){BufferedImage c=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);java.util.Arrays.fill(((DataBufferInt)c.getRaster().getDataBuffer()).getData(),0xFFFFFFFF);return c;}
    public static void main(String[] a)throws Exception{
        String[] formas={"S","ჲ"};double d=Double.parseDouble(a.length>0?a[0]:"4");int[] cs={0,2,4,6,8};
        Solape[] so={Solape.PLANO,Solape.TRAZO,Solape.PONDERADO,Solape.PONDERADO_UV};
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH;
        Relleno[] rel={Texturas.tira(new Guia()),SuiteMatriz_03_Colores.RELLENO};String[] rn={"textura guía","degradado"};
        for(int tipo=0;tipo<2;tipo++){
            BufferedImage img=lienzo(130+cs.length*CW,30+formas.length*so.length*CH);Graphics2D g2=img.createGraphics();
            for(int f=0;f<formas.length;f++)for(int m=0;m<so.length;m++)for(int ci=0;ci<cs.length;ci++){int c=cs[ci];
                double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
                BufferedImage cell=lienzo(CW,CH);Gota gt=SuiteMatriz_03_Colores.gota(formas[f],d,r1,r2,90,CH*0.62);
                Plotter.raster(cell).plot(gt,rel[tipo],SuiteMatriz_03_Colores.OPCIONES.conSolape(so[m]));
                int x0=130+ci*CW,y0=30+(f*so.length+m)*CH;g2.drawImage(cell,x0,y0,null);g2.setColor(new Color(0xCCCCCC));g2.drawRect(x0,y0,CW-1,CH-1);
                if(f==0&&m==0){g2.setColor(Color.DARK_GRAY);g2.drawString(String.format("r1=%.1f r2=%.1f",r1,r2),x0+8,18);}}
            g2.setColor(Color.DARK_GRAY);for(int f=0;f<formas.length;f++)for(int m=0;m<so.length;m++)g2.drawString(formas[f]+" "+so[m],4,30+(f*so.length+m)*CH+16);
            g2.dispose();ImageIO.write(img,"png",new java.io.File("uv_"+(tipo==0?"textura":"degradado")+"_d"+(int)d+".png"));
            BufferedImage s=new BufferedImage(img.getWidth()/2,img.getHeight()/2,BufferedImage.TYPE_INT_RGB);Graphics2D h=s.createGraphics();h.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);h.drawImage(img,0,0,s.getWidth(),s.getHeight(),null);h.dispose();ImageIO.write(s,"jpg",new java.io.File("uv_"+(tipo==0?"textura":"degradado")+"_d"+(int)d+"_s.jpg"));
        }
    }
}
