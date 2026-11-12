/** Caso de estudio (rama estudio_solapes, ver ESTUDIO_SOLAPES.md; no está en Todas): clasifica los píxeles con 2+ hebras en legítimo / compresión / pliegue según J = 1 - |k|·y (lado cóncavo, mínimo entre pasadas); umbral de compresión provisional 0.3. */
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class EstudioTresClases {
    static double[] tan(Gota g,double t){double e=1e-4,t0=Math.max(0,t-e),t1=Math.min(1,t+e);double x=g.cx(t1)-g.cx(t0),y=g.cy(t1)-g.cy(t0),n=Math.hypot(x,y);return n<1e-12?new double[]{1,0}:new double[]{x/n,y/n};}
    static double kappa(Gota g,double t){double h=2e-3;double a=Math.max(0,t-h),b=Math.min(1,t+h),m=(a+b)/2;
        double x0=g.cx(a),y0=g.cy(a),x1=g.cx(m),y1=g.cy(m),x2=g.cx(b),y2=g.cy(b);
        double dx=(x2-x0)/(b-a),dy=(y2-y0)/(b-a),ddx=(x2-2*x1+x0)/((b-a)*(b-a)/4),ddy=(y2-2*y1+y0)/((b-a)*(b-a)/4);
        double n=Math.hypot(dx,dy);return n<1e-9?0:(dx*ddy-dy*ddx)/(n*n*n);}
    /** J = 1 - |k|*vc del lado cóncavo (J<=0: inversión), mínimo entre las pasadas */
    static double J(Gota g,java.util.List<Geometria.Hebra> hs,double x,double y){double j=1e9;
        for(var hb:hs){double t=hb.t(),k=kappa(g,t);if(Math.abs(k)<1e-9)continue;double[] T=tan(g,t);
            double vc=(-(x-g.cx(t))*T[1]+(y-g.cy(t))*T[0])*Math.signum(k);j=Math.min(j,1-Math.abs(k)*vc);}
        return j;}
    public static void main(String[] a)throws Exception{
        double UMB=Double.parseDouble(a.length>0?a[0]:"0.3");
        String[] formas={"suave","S","ჲ"};double[] ds={5,4};
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH,cols=SuiteMatriz_03_Colores.RADIOS.length;
        BufferedImage img=new BufferedImage(110+cols*CW,30+formas.length*ds.length*CH,BufferedImage.TYPE_INT_RGB);
        Graphics2D g2=img.createGraphics();g2.setColor(Color.WHITE);g2.fillRect(0,0,img.getWidth(),img.getHeight());
        System.out.printf("umbral compresión J<%.2f%n",UMB);
        System.out.println("forma d | clase       píxeles  discrepan PLANO≠TRAZO");
        for(int f=0;f<formas.length;f++)for(int di=0;di<ds.length;di++){double d=ds[di];
            long[] n=new long[3],dis=new long[3];
            for(int c=0;c<cols;c++){
                double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
                Gota gp=SuiteMatriz_03_Colores.gota(formas[f],d,r1,r2,90,CH*0.62),gt=SuiteMatriz_03_Colores.gota(formas[f],d,r1,r2,90,CH*0.62);
                int x0=110+c*CW,y0=30+(f*ds.length+di)*CH;g2.setColor(new Color(0xCCCCCC));g2.drawRect(x0,y0,CW-1,CH-1);
                if(gt.silueta()!=null){g2.setColor(Color.GRAY);g2.drawString("rígida",x0+8,y0+16);continue;}
                BufferedImage A=SuiteMatriz_03_Colores.sobreBlanco(gp,Solape.PLANO),B=SuiteMatriz_03_Colores.sobreBlanco(gt,Solape.TRAZO);
                for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){
                    var hs=gt.mascara().hebras(x+.5,y+.5);if(hs.isEmpty())continue;
                    int col=0xE6E6E6;
                    if(hs.size()>=2){
                        int p=A.getRGB(x,y)&0xFFFFFF,q=B.getRGB(x,y)&0xFFFFFF,m=0;for(int s=0;s<24;s+=8)m=Math.max(m,Math.abs(((p>>s)&255)-((q>>s)&255)));
                        double j=J(gt,hs,x+.5,y+.5);int cl=j<=0?2:j<UMB?1:0;n[cl]++;if(m>48)dis[cl]++;
                        col=cl==2?0xC62828:cl==1?0xF9A825:0x2E7D32;if(m>48)col=cl==2?0x6A0000:cl==1?0x8D5A00:0x0B3D0E;
                    }
                    img.setRGB(x0+x,y0+y,col);}
            }
            String[] nm={"legítimo","compresión","pliegue"};
            for(int k=0;k<3;k++)System.out.printf("%-5s %.0f | %-11s %8d  %5.1f %%%n",formas[f],d,nm[k],n[k],n[k]==0?0:100.0*dis[k]/n[k]);
            g2.setColor(Color.DARK_GRAY);g2.drawString(formas[f]+" d="+(int)d,4,30+(f*ds.length+di)*CH+16);
        }
        for(int c=0;c<cols;c++)g2.drawString(String.format("r1=%.1f r2=%.1f",SuiteMatriz_03_Colores.RADIOS[c][0],SuiteMatriz_03_Colores.RADIOS[c][1]),110+c*CW+8,18);
        g2.dispose();ImageIO.write(img,"png",new java.io.File("tres.png"));
        BufferedImage s=new BufferedImage(img.getWidth()*3/4,img.getHeight()*3/4,BufferedImage.TYPE_INT_RGB);Graphics2D h=s.createGraphics();h.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);h.drawImage(img,0,0,s.getWidth(),s.getHeight(),null);h.dispose();ImageIO.write(s,"jpg",new java.io.File("tres_s.jpg"));
    }
}
