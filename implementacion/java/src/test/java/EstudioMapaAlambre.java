/** Caso de estudio (rama estudio_solapes; no está en Todas): mapa del signo de J por hebra y mapa de f, con el esquema de PlotterEsquema superpuesto. Uso: EstudioMapaAlambre [d]. */
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class EstudioMapaAlambre {
    public static void main(String[] a)throws Exception{
        String[] formas={"S","ჲ"};int[] cs={0,2,4,6,8};double d=Double.parseDouble(a.length>0?a[0]:"4");int paso=2;EstudioMapaCorte.CORTE=0.6;
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH;
        BufferedImage img=new BufferedImage(130+cs.length*CW,30+formas.length*2*CH,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2=img.createGraphics();g2.setColor(Color.WHITE);g2.fillRect(0,0,img.getWidth(),img.getHeight());
        for(int f=0;f<formas.length;f++)for(int ci=0;ci<cs.length;ci++){int c=cs[ci];
            double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
            Gota gt=SuiteMatriz_03_Colores.gota(formas[f],d,r1,r2,90,CH*0.62);
            BufferedImage sg=EstudioUV.lienzo(CW,CH),mf=EstudioUV.lienzo(CW,CH);
            double[] fm=gt.silueta()!=null?null:EstudioMapaCorte.mapa(gt,CW,CH,0.03,paso);
            for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){var hs=gt.mascara().hebras(x+.5,y+.5);if(hs.isEmpty())continue;int k=0;boolean neg=false;
                for(var h:hs){if(h.cobertura()<=0)continue;k++;if(EstudioSignoJ.Jh(gt,h.t(),x+.5,y+.5)<=0)neg=true;}
                int cs1=0xEEEEEE,cm=0xEEEEEE;
                if(k>=2){cs1=neg?0xC62828:0x2E7D32;double v=fm==null?Double.NaN:fm[(y-y%paso)*CW+(x-x%paso)];if(!Double.isNaN(v))cm=EstudioMapaCorte.color(v);}
                sg.setRGB(x,y,0xFF000000|cs1);mf.setRGB(x,y,0xFF000000|cm);}
            BufferedImage[] v={sg,mf};
            for(int r=0;r<2;r++){Graphics2D gc=v[r].createGraphics();PlotterEsquema.plot(gc,gt);gc.dispose();int x0=130+ci*CW,y0=30+(f*2+r)*CH;g2.drawImage(v[r],x0,y0,null);g2.setColor(new Color(0xCCCCCC));g2.drawRect(x0,y0,CW-1,CH-1);}
            if(f==0){g2.setColor(Color.DARK_GRAY);g2.drawString(String.format("r1=%.1f r2=%.1f",r1,r2),130+ci*CW+8,18);}}
        g2.setColor(Color.DARK_GRAY);for(int f=0;f<formas.length;f++)for(int r=0;r<2;r++)g2.drawString(formas[f]+" d="+(int)d+" "+new String[]{"signo J","mapa f"}[r],4,30+(f*2+r)*CH+16);
        g2.dispose();ImageIO.write(img,"png",new java.io.File("estudio_mapa_alambre_d"+(int)d+".png"));
    }
}
