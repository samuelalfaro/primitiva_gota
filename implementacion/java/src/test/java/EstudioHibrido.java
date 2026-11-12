import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
/**
 * Caso de estudio (rama estudio_solapes, ver ESTUDIO_SOLAPES.md; no está en {@link Todas}). Regla híbrida de solape hecha a nivel
 * de imagen: donde el píxel está plegado por curvatura (ver {@link EstudioEtiquetaCurvatura}) toma el color de PLANO y donde es
 * legítimo el de TRAZO. Dos variantes: corte duro y transición suave (smoothstep de 0.15 sobre |kappa|·distancia_normal - 1).
 * Cuenta los saltos de color > 100 entre vecinos de las matrices (d = 5 y 4) y deja hibrido_d4.png (S y ჲ con d = 4).
 */
public class EstudioHibrido {
    static final double DELTA=0.15;
    static double smooth(double a,double b,double x){double t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t);}
    static BufferedImage mix(BufferedImage tz,BufferedImage pl,double[][] w,int CW,int CH,int mode){ // mode 0 duro, 1 suave
        BufferedImage r=new BufferedImage(CW,CH,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){int p=tz.getRGB(x,y)&0xFFFFFF,q=pl.getRGB(x,y)&0xFFFFFF;
            double s=w[y][x]; double ww=s==-9?0:mode==0?(s>0?1:0):smooth(-DELTA,DELTA,s);
            int o=0;for(int sh=0;sh<24;sh+=8)o|=((int)Math.round(((p>>sh)&255)*(1-ww)+((q>>sh)&255)*ww))<<sh;r.setRGB(x,y,o);}
        return r;}
    static int saltos(BufferedImage im,int umbral){int n=0;
        for(int y=1;y<im.getHeight()-1;y++)for(int x=1;x<im.getWidth()-1;x++){int p=im.getRGB(x,y)&0xFFFFFF;if(p==0xFFFFFF)continue;
            for(int q:new int[]{im.getRGB(x+1,y)&0xFFFFFF,im.getRGB(x,y+1)&0xFFFFFF}){if(q==0xFFFFFF)continue;int m=0;for(int s=0;s<24;s+=8)m=Math.max(m,Math.abs(((p>>s)&255)-((q>>s)&255)));if(m>umbral){n++;break;}}}
        return n;}
    public static void main(String[] a)throws Exception{
        String[] formas={"suave","S","ჲ"};double[] ds={5,4};
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH,cols=SuiteMatriz_03_Colores.RADIOS.length;
        String[] nom={"PLANO","TRAZO","híbrido duro","híbrido suave"};
        System.out.printf("Píxeles con salto de color > 100 entre vecinos%n%-6s %-3s %-14s","forma","d","regla");
        for(double[] r:SuiteMatriz_03_Colores.RADIOS)System.out.printf(" %3.1f/%3.1f",r[0],r[1]);System.out.println();
        BufferedImage[] big=new BufferedImage[2];int[] cs={0,2,4,6,8};
        // imagen: S y ჲ con d=4: 5 filas (PLANO,TRAZO,duro,suave,etiqueta) x 5 columnas
        int filasImg=2*5;BufferedImage img=new BufferedImage(130+cs.length*CW,30+filasImg*CH,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2=img.createGraphics();g2.setColor(Color.WHITE);g2.fillRect(0,0,img.getWidth(),img.getHeight());
        for(String forma:formas)for(double d:ds){
            int[][] sj=new int[4][cols];
            for(int c=0;c<cols;c++){
                double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
                Gota gp=SuiteMatriz_03_Colores.gota(forma,d,r1,r2,90,CH*0.62),gt=SuiteMatriz_03_Colores.gota(forma,d,r1,r2,90,CH*0.62);
                BufferedImage PL=SuiteMatriz_03_Colores.sobreBlanco(gp,Solape.PLANO),TZ=SuiteMatriz_03_Colores.sobreBlanco(gt,Solape.TRAZO);
                double[][] w=new double[CH][CW];
                boolean rig=gt.silueta()!=null;
                for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){w[y][x]=-9;if(rig)continue;
                    int p=PL.getRGB(x,y)&0xFFFFFF,q=TZ.getRGB(x,y)&0xFFFFFF;if(p==0xFFFFFF&&q==0xFFFFFF)continue;
                    var hs=gt.mascara().hebras(x+.5,y+.5);if(hs.size()<2)continue;
                    double best=-9;
                    for(var hb:hs){double t=hb.t(),k=EstudioEtiquetaCurvatura.kappa(gt,t);if(Math.abs(k)<1e-9){best=Math.max(best,-1);continue;}double[] T=EstudioEtiquetaCurvatura.tan(gt,t);
                        double px=x+.5-gt.cx(t),py=y+.5-gt.cy(t);double vc=(-px*T[1]+py*T[0])*Math.signum(k);best=Math.max(best,vc*Math.abs(k)-1);}
                    w[y][x]=best;}
                BufferedImage[] im={PL,TZ,mix(TZ,PL,w,CW,CH,0),mix(TZ,PL,w,CW,CH,1)};
                for(int m=0;m<4;m++)sj[m][c]=saltos(im[m],100);
                if(d==4&&(forma.equals("S")||forma.equals("ჲ"))){int ci=-1;for(int i=0;i<cs.length;i++)if(cs[i]==c)ci=i;
                    if(ci>=0){int base=(forma.equals("S")?0:5);
                        for(int m=0;m<4;m++){g2.drawImage(im[m],130+ci*CW,30+(base+m)*CH,null);g2.setColor(new Color(0xCCCCCC));g2.drawRect(130+ci*CW,30+(base+m)*CH,CW-1,CH-1);}
                        BufferedImage lab=new BufferedImage(CW,CH,BufferedImage.TYPE_INT_RGB);
                        for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){int p=PL.getRGB(x,y)&0xFFFFFF;lab.setRGB(x,y,p==0xFFFFFF&&(TZ.getRGB(x,y)&0xFFFFFF)==0xFFFFFF?0xFFFFFF:w[y][x]==-9?0xE6E6E6:w[y][x]>0?0xD32F2F:0x90CAF9);}
                        g2.drawImage(lab,130+ci*CW,30+(base+4)*CH,null);g2.setColor(new Color(0xCCCCCC));g2.drawRect(130+ci*CW,30+(base+4)*CH,CW-1,CH-1);
                        g2.setColor(Color.DARK_GRAY);g2.drawString(String.format("r1=%.1f r2=%.1f",r1,r2),130+ci*CW+8,18);}}
            }
            for(int m=0;m<4;m++){System.out.printf("%-6s %-3.0f %-14s",forma,d,nom[m]);for(int c=0;c<cols;c++)System.out.printf(" %7d",sj[m][c]);System.out.println();}
        }
        g2.setColor(Color.DARK_GRAY);String[] ln={"PLANO","TRAZO","híbrido duro","híbrido suave","etiqueta (rojo=plegado)"};
        for(int f=0;f<2;f++)for(int m=0;m<5;m++)g2.drawString((f==0?"S d=4 ":"ჲ d=4 ")+ln[m],4,30+(f*5+m)*CH+16);
        g2.dispose();ImageIO.write(img,"png",new java.io.File("hibrido_d4.png"));
        BufferedImage s=new BufferedImage(img.getWidth()/2,img.getHeight()/2,BufferedImage.TYPE_INT_RGB);Graphics2D h=s.createGraphics();h.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);h.drawImage(img,0,0,s.getWidth(),s.getHeight(),null);h.dispose();ImageIO.write(s,"jpg",new java.io.File("hibrido_d4_s.jpg"));
    }
}
