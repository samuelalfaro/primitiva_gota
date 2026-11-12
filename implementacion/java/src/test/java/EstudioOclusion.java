import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
/**
 * Caso de estudio (rama estudio_solapes, ver ESTUDIO_SOLAPES.md; no está en {@link Todas}). Pista de oclusión sobre la imagen de
 * TRAZO: sombra suave sobre la pasada inferior (píxeles con una sola hebra) junto al borde de la superior (píxeles vecinos con
 * dos o más hebras y t mayor que la de la inferior). Intensidad {@code -Da=0.45} y ancho {@code -Dw=9} px; deja oclusion_d<d>.png
 * (S y ჲ, TRAZO frente a TRAZO + sombra). Es un efecto de imagen, no una regla de {@link Solape}.
 */
public class EstudioOclusion {
    static final double A=Double.parseDouble(System.getProperty("a","0.45")); static final int W=Integer.parseInt(System.getProperty("w","9"));
    /** sombra sobre la pasada inferior junto al borde de la superior */
    static BufferedImage sombra(Gota gt,BufferedImage TZ,int CW,int CH,int[] cuenta){
        int[] n=new int[CW*CH];double[] top=new double[CW*CH];double[] self=new double[CW*CH];
        for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){if((TZ.getRGB(x,y)&0xFFFFFF)==0xFFFFFF)continue;
            var hs=gt.mascara().hebras(x+.5,y+.5);n[y*CW+x]=hs.size();if(hs.isEmpty())continue;top[y*CW+x]=hs.get(hs.size()-1).t();self[y*CW+x]=hs.get(0).t();}
        BufferedImage r=new BufferedImage(CW,CH,BufferedImage.TYPE_INT_RGB);int sh=0;
        for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){int c=TZ.getRGB(x,y)&0xFFFFFF;
            if(c!=0xFFFFFF&&n[y*CW+x]==1){ // pasada única: ¿la superior está cerca y la tapa a ella?
                double best=1e9;double ts=self[y*CW+x];
                for(int j=-W;j<=W;j++)for(int i=-W;i<=W;i++){int xx=x+i,yy=y+j;if(xx<0||yy<0||xx>=CW||yy>=CH)continue;
                    if(n[yy*CW+xx]>=2&&top[yy*CW+xx]>ts+0.02){double d=Math.hypot(i,j);if(d<best)best=d;}}
                if(best<=W){double f=1-A*Math.pow(1-best/W,2);int o=0;for(int s=0;s<24;s+=8)o|=((int)Math.round(((c>>s)&255)*f))<<s;c=o;sh++;}
            }
            r.setRGB(x,y,c);}
        cuenta[0]=sh;return r;}
    public static void main(String[] a)throws Exception{
        String[] formas={"S","ჲ"};double d=Double.parseDouble(a.length>0?a[0]:"4");int[] cs={0,2,4,6,8};
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH;
        BufferedImage img=new BufferedImage(130+cs.length*CW,30+4*CH,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2=img.createGraphics();g2.setColor(Color.WHITE);g2.fillRect(0,0,img.getWidth(),img.getHeight());
        System.out.printf("Oclusión a=%.2f w=%d px, d=%.0f: píxeles oscurecidos%n",A,W,d);
        for(int f=0;f<2;f++)for(int ci=0;ci<cs.length;ci++){int c=cs[ci];
            double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
            Gota gt=SuiteMatriz_03_Colores.gota(formas[f],d,r1,r2,90,CH*0.62);BufferedImage TZ=SuiteMatriz_03_Colores.sobreBlanco(gt,Solape.TRAZO);
            int[] cnt=new int[1];BufferedImage S=sombra(gt,TZ,CW,CH,cnt);
            System.out.printf("%-3s %.1f/%.1f  %6d%n",formas[f],r1,r2,cnt[0]);
            g2.drawImage(TZ,130+ci*CW,30+(2*f)*CH,null);g2.drawImage(S,130+ci*CW,30+(2*f+1)*CH,null);
            g2.setColor(new Color(0xCCCCCC));g2.drawRect(130+ci*CW,30+(2*f)*CH,CW-1,CH-1);g2.drawRect(130+ci*CW,30+(2*f+1)*CH,CW-1,CH-1);
            g2.setColor(Color.DARK_GRAY);g2.drawString(String.format("r1=%.1f r2=%.1f",r1,r2),130+ci*CW+8,18);}
        g2.setColor(Color.DARK_GRAY);for(int f=0;f<2;f++){g2.drawString(formas[f]+" TRAZO",4,30+2*f*CH+16);g2.drawString(formas[f]+" TRAZO + sombra",4,30+(2*f+1)*CH+16);}
        g2.dispose();ImageIO.write(img,"png",new java.io.File("oclusion_d"+(int)d+".png"));
        BufferedImage s=new BufferedImage(img.getWidth()/2,img.getHeight()/2,BufferedImage.TYPE_INT_RGB);Graphics2D h=s.createGraphics();h.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);h.drawImage(img,0,0,s.getWidth(),s.getHeight(),null);h.dispose();ImageIO.write(s,"jpg",new java.io.File("oclusion_d"+(int)d+"_s.jpg"));
    }
}
