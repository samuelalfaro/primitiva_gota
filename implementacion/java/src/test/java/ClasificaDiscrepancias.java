import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
/**
 * Caso de estudio (rama estudio_solapes, ver ESTUDIO_SOLAPES.md; no está en {@link Todas}). Clasifica los píxeles donde el
 * color de PLANO y el de TRAZO difieren en más de 48 (de 255) según cuántas hebras del camino los cubren y según el cociente
 * rho_min / rho_max entre ellas (rho = distancia a la espina dividida por el radio de su t). Uso: {@code ClasificaDiscrepancias [d]}.
 * Deja {@code clasifica_rho_d<d>.png}: azul = empate (cociente >= 0.9), ámbar = medio (0.7 a 0.9), rojo = solape profundo (< 0.7),
 * verde = una sola hebra.
 */
public class ClasificaDiscrepancias {
    public static void main(String[] a) throws Exception {
        String[] formas={"suave","S","ჲ"}; double d=Double.parseDouble(a[0]);
        double[] cortes={0.05,0.1,0.2,0.4,0.6,1.01};
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH,cols=SuiteMatriz_03_Colores.RADIOS.length;
        BufferedImage img=new BufferedImage(130+cols*CW,30+formas.length*CH,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2=img.createGraphics(); g2.setColor(Color.WHITE); g2.fillRect(0,0,img.getWidth(),img.getHeight());
        System.out.printf("d=%.0f. Píxeles discrepantes (PLANO/TRAZO > 48) por clase: 0h=sin hebra, 1h=una hebra, Δt = t máx - t mín de las hebras del píxel%n",d);
        System.out.printf("%-6s %-9s %6s %5s %5s | %s | mediana%n","forma","r1/r2","total","0h","1h","2+h por rho_min/rho_max: empate(>=.9) medio(.7-.9) profundo(<.7)");
        for(int f=0;f<formas.length;f++) for(int c=0;c<cols;c++){
            double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
            Gota gp=SuiteMatriz_03_Colores.gota(formas[f],d,r1,r2,90,CH*0.62), gt=SuiteMatriz_03_Colores.gota(formas[f],d,r1,r2,90,CH*0.62);
            BufferedImage A=SuiteMatriz_03_Colores.sobreBlanco(gp,Solape.PLANO), B=SuiteMatriz_03_Colores.sobreBlanco(gt,Solape.TRAZO);
            BufferedImage cell=new BufferedImage(CW,CH,BufferedImage.TYPE_INT_ARGB);
            int tot=0,h0=0,h1=0; int[] bins=new int[cortes.length]; List<Double> ratios=new ArrayList<>();
            for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){
                int p=A.getRGB(x,y)&0xFFFFFF,q=B.getRGB(x,y)&0xFFFFFF;
                if(p==0xFFFFFF&&q==0xFFFFFF){cell.setRGB(x,y,0xFFFFFFFF);continue;}
                int m=0;for(int s=0;s<24;s+=8)m=Math.max(m,Math.abs(((p>>s)&255)-((q>>s)&255)));
                if(m<=48){cell.setRGB(x,y,0xFFE6E6E6);continue;}
                tot++;
                var hs=gt.mascara().hebras(x+.5,y+.5);
                int col;
                if(hs.isEmpty()){h0++;col=0xFF9E9E9E;}
                else if(hs.size()==1){h1++;col=0xFF2E9E4F;}
                else{
                    double tmin=1,tmax=0,rmin=1e9,rmax=0;
                    for(var hb:hs){tmin=Math.min(tmin,hb.t());tmax=Math.max(tmax,hb.t());
                        double rho=Math.hypot(x+.5-gt.cx(hb.t()),y+.5-gt.cy(hb.t()))/Math.max(gt.rad(hb.t()),1e-9);
                        rmin=Math.min(rmin,rho);rmax=Math.max(rmax,rho);}
                    double rr=rmax>0?rmin/rmax:1; ratios.add(rr);
                    int k= rr>=0.9?0: rr>=0.7?1: 2; bins[k]++;
                    col= k==0?0xFF1565C0: k==1?0xFFF9A825: 0xFFD32F2F;   // azul: empate (>=0.9); ámbar: medio; rojo: solape profundo (<0.7)
                }
                cell.setRGB(x,y,col);
            }
            Collections.sort(ratios);
            System.out.printf("%-6s %.1f/%.1f %6d %5d %5d | %7d %7d %7d | %s%n",formas[f],r1,r2,tot,h0,h1,bins[0],bins[1],bins[2],ratios.isEmpty()?"-":String.format("%.2f",ratios.get(ratios.size()/2)));
            g2.drawImage(cell,130+c*CW,30+f*CH,null); g2.setColor(new Color(0xCCCCCC)); g2.drawRect(130+c*CW,30+f*CH,CW-1,CH-1);
        }
        g2.setColor(Color.DARK_GRAY);
        for(int f=0;f<formas.length;f++)g2.drawString(formas[f],4,30+f*CH+16);
        for(int c=0;c<cols;c++)g2.drawString(String.format("r1=%.1f r2=%.1f",SuiteMatriz_03_Colores.RADIOS[c][0],SuiteMatriz_03_Colores.RADIOS[c][1]),130+c*CW+8,18);
        g2.dispose(); ImageIO.write(img,"png",new java.io.File("clasifica_rho_d"+(int)d+".png"));
    }
}
