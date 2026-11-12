import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
/**
 * Caso de estudio (rama estudio_solapes). Mapa del signo de u (componente de p - c(t) a lo largo de la tangente) en las dos
 * pasadas de cada píxel solapado: verde = (+,+), rojo = (-,-), azul = signos distintos; color saturado donde PLANO y TRAZO
 * discrepan (> 48 de 255) y pálido donde no. d = 4, columnas (0,4), (2,2) y (4,0) de las matrices; deja mapa_signo_d4.png.
 */
public class MapaSigno {
    static double[] tan(Gota g,double t){double e=1e-4,t0=Math.max(0,t-e),t1=Math.min(1,t+e);double x=g.cx(t1)-g.cx(t0),y=g.cy(t1)-g.cy(t0),n=Math.hypot(x,y);return n<1e-12?new double[]{1,0}:new double[]{x/n,y/n};}
    public static void main(String[] a)throws Exception{
        String[] formas={"suave","S","ჲ"}; double d=4; int[] cs={0,2,4};
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH;
        BufferedImage img=new BufferedImage(110+cs.length*CW,30+formas.length*CH,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2=img.createGraphics();g2.setColor(Color.WHITE);g2.fillRect(0,0,img.getWidth(),img.getHeight());
        for(int f=0;f<formas.length;f++)for(int ci=0;ci<cs.length;ci++){
            int c=cs[ci];double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
            Gota gt=SuiteMatriz_03_Colores.gota(formas[f],d,r1,r2,90,CH*0.62),gp=SuiteMatriz_03_Colores.gota(formas[f],d,r1,r2,90,CH*0.62);
            BufferedImage A=SuiteMatriz_03_Colores.sobreBlanco(gp,Solape.PLANO),B=SuiteMatriz_03_Colores.sobreBlanco(gt,Solape.TRAZO);
            BufferedImage cell=new BufferedImage(CW,CH,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){
                int p=A.getRGB(x,y)&0xFFFFFF,q=B.getRGB(x,y)&0xFFFFFF;
                if(p==0xFFFFFF&&q==0xFFFFFF){cell.setRGB(x,y,0xFFFFFFFF);continue;}
                var hs=gt.mascara().hebras(x+.5,y+.5);
                if(hs.size()<2){cell.setRGB(x,y,0xFFE6E6E6);continue;}
                int m=0;for(int s=0;s<24;s+=8)m=Math.max(m,Math.abs(((p>>s)&255)-((q>>s)&255)));
                double[] u=new double[2];int[] idx={0,hs.size()-1};
                for(int k=0;k<2;k++){double t=hs.get(idx[k]).t();double[] T=tan(gt,t);u[k]=(x+.5-gt.cx(t))*T[0]+(y+.5-gt.cy(t))*T[1];}
                int base=(u[0]>=0&&u[1]>=0)?0x2E9E4F:(u[0]<0&&u[1]<0)?0xD32F2F:0x1565C0;
                // más claro si el píxel NO es discrepante
                int col=m>48?base:blend(base);
                cell.setRGB(x,y,0xFF000000|col);
            }
            g2.drawImage(cell,110+ci*CW,30+f*CH,null);g2.setColor(new Color(0xCCCCCC));g2.drawRect(110+ci*CW,30+f*CH,CW-1,CH-1);
            g2.setColor(Color.DARK_GRAY);g2.drawString(String.format("r1=%.1f r2=%.1f",r1,r2),110+ci*CW+8,18);
        }
        g2.setColor(Color.DARK_GRAY);for(int f=0;f<formas.length;f++)g2.drawString(formas[f]+" d=4",4,30+f*CH+16);
        g2.dispose();ImageIO.write(img,"png",new java.io.File("mapa_signo_d4.png"));
    }
    static int blend(int c){int r=(c>>16)&255,g=(c>>8)&255,b=c&255;r=255-(255-r)/4;g=255-(255-g)/4;b=255-(255-b)/4;return (r<<16)|(g<<8)|b;}
}
