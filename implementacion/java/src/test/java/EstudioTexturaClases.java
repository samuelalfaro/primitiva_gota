/** Caso de estudio (rama estudio_solapes, ver ESTUDIO_SOLAPES.md; no está en Todas). */
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class EstudioTexturaClases {
    public static void main(String[] a)throws Exception{
        String[] formas={"S","S","ჲ"};double[] ds={4,5,4};int[] cs={0,1,2,6,7,8};
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH;
        Relleno rel=Texturas.tira(new EstudioUV.Guia());
        BufferedImage img=new BufferedImage(110+cs.length*CW,30+formas.length*2*CH,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2=img.createGraphics();g2.setColor(Color.WHITE);g2.fillRect(0,0,img.getWidth(),img.getHeight());
        for(int f=0;f<formas.length;f++)for(int ci=0;ci<cs.length;ci++){int c=cs[ci];
            double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
            Gota gt=SuiteMatriz_03_Colores.gota(formas[f],ds[f],r1,r2,90,CH*0.62);
            BufferedImage cell=EstudioUV.lienzo(CW,CH);Plotter.raster(cell).plot(gt,rel,SuiteMatriz_03_Colores.OPCIONES.conSolape(Solape.TRAZO));
            int x0=110+ci*CW,y0=30+(2*f)*CH;g2.drawImage(cell,x0,y0,null);
            BufferedImage ov=EstudioUV.lienzo(CW,CH);ov.createGraphics().drawImage(cell,0,0,null);
            if(gt.silueta()==null)for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){var hs=gt.mascara().hebras(x+.5,y+.5);if(hs.size()<2)continue;
                double j=EstudioTresClases.J(gt,hs,x+.5,y+.5);if(j>=0.3)continue;int tint=j<=0?0xC62828:0xF9A825;int p=ov.getRGB(x,y);
                int r=0;for(int s=16;s>=0;s-=8)r|=(int)Math.round(((p>>s)&255)*0.45+((tint>>s)&255)*0.55)<<s;ov.setRGB(x,y,0xFF000000|r);}
            g2.drawImage(ov,x0,y0+CH,null);g2.setColor(new Color(0xCCCCCC));g2.drawRect(x0,y0,CW-1,CH-1);g2.drawRect(x0,y0+CH,CW-1,CH-1);
            if(f==0){g2.setColor(Color.DARK_GRAY);g2.drawString(String.format("r1=%.1f r2=%.1f",r1,r2),x0+8,18);}}
        g2.setColor(Color.DARK_GRAY);for(int f=0;f<formas.length;f++){g2.drawString(formas[f]+" d="+(int)ds[f]+" TRAZO",4,30+2*f*CH+16);g2.drawString("+ compr./pliegue",4,30+(2*f+1)*CH+16);}
        g2.dispose();ImageIO.write(img,"png",new java.io.File("texclases.png"));
        BufferedImage s=new BufferedImage(img.getWidth()*3/4,img.getHeight()*3/4,BufferedImage.TYPE_INT_RGB);Graphics2D h=s.createGraphics();h.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);h.drawImage(img,0,0,s.getWidth(),s.getHeight(),null);h.dispose();ImageIO.write(s,"jpg",new java.io.File("texclases_s.jpg"));
    }
}
