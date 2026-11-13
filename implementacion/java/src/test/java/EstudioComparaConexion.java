/** Caso de estudio (rama estudio_solapes; no está en Todas): textura TRAZO con las clases de J y las de conexión (f=0.5 y 1.0) superpuestas. */
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class EstudioComparaConexion {
    static void tinte(BufferedImage im,int x,int y,int c){int p=im.getRGB(x,y);int r=0;for(int s=16;s>=0;s-=8)r|=(int)Math.round(((p>>s)&255)*0.4+((c>>s)&255)*0.6)<<s;im.setRGB(x,y,0xFF000000|r);}
    public static void main(String[] a)throws Exception{
        String[] formas={"S","ჲ"};int[] cs={0,2,4,6,8};double d=4;int paso=2;
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH;
        Relleno rel=Texturas.tira(new EstudioUV.Guia());Opciones op=SuiteMatriz_03_Colores.OPCIONES.conSolape(Solape.TRAZO);
        String[] fil={"textura TRAZO","J: legítimo / compresión / pliegue","conexión f=0.5","conexión f=1.0"};
        BufferedImage img=new BufferedImage(130+cs.length*CW,30+formas.length*4*CH,BufferedImage.TYPE_INT_RGB);
        Graphics2D g2=img.createGraphics();g2.setColor(Color.WHITE);g2.fillRect(0,0,img.getWidth(),img.getHeight());
        for(int f=0;f<formas.length;f++)for(int ci=0;ci<cs.length;ci++){int c=cs[ci];
            double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
            Gota gt=SuiteMatriz_03_Colores.gota(formas[f],d,r1,r2,90,CH*0.62);
            BufferedImage base=EstudioUV.lienzo(CW,CH);Plotter.raster(base).plot(gt,rel,op);
            BufferedImage[] v=new BufferedImage[4];for(int k=0;k<4;k++){v[k]=EstudioUV.lienzo(CW,CH);v[k].createGraphics().drawImage(base,0,0,null);}
            byte[] c5=EstudioConexion.clasifica(gt,CW,CH,0.03,0.5,paso),c1=EstudioConexion.clasifica(gt,CW,CH,0.03,1.0,paso);
            for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){var hs=gt.mascara().hebras(x+.5,y+.5);if(hs.size()<2)continue;
                double j=EstudioTresClases.J(gt,hs,x+.5,y+.5);tinte(v[1],x,y,j<=0?0xC62828:j<0.3?0xF9A825:0x2E7D32);
                int bx=x-x%paso,by=y-y%paso;int k5=c5[by*CW+bx],k1=c1[by*CW+bx];
                if(k5>0)tinte(v[2],x,y,k5==2?0xC62828:0x2E7D32);if(k1>0)tinte(v[3],x,y,k1==2?0xC62828:0x2E7D32);}
            for(int k=0;k<4;k++){int x0=130+ci*CW,y0=30+(f*4+k)*CH;g2.drawImage(v[k],x0,y0,null);g2.setColor(new Color(0xCCCCCC));g2.drawRect(x0,y0,CW-1,CH-1);}
            if(f==0){g2.setColor(Color.DARK_GRAY);g2.drawString(String.format("r1=%.1f r2=%.1f",r1,r2),130+ci*CW+8,18);}}
        g2.setColor(Color.DARK_GRAY);for(int f=0;f<formas.length;f++)for(int k=0;k<4;k++)g2.drawString(formas[f]+" d=4 "+fil[k],4,30+(f*4+k)*CH+16);
        g2.dispose();ImageIO.write(img,"png",new java.io.File("compim.png"));
        BufferedImage s=new BufferedImage(img.getWidth()*3/5,img.getHeight()*3/5,BufferedImage.TYPE_INT_RGB);Graphics2D h=s.createGraphics();h.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);h.drawImage(img,0,0,s.getWidth(),s.getHeight(),null);h.dispose();ImageIO.write(s,"jpg",new java.io.File("compim_s.jpg"));
    }
}
