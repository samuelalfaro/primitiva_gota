/** Caso de estudio (rama estudio_solapes, ver ESTUDIO_SOLAPES.md; no está en Todas). Mapa de calor de la fracción mínima f (ventana euclídea ±f·r) a la que las hebras baja y alta de cada píxel solapado conectan en t (pasos |Δt|<0.03); lo que supera el corte f (-Destudio.fcorte, por defecto 0.6, o segundo argumento) o no conecta se pinta como solape legítimo (verde oscuro). Uso: EstudioMapaCorte [d] [corte]. */
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.imageio.ImageIO;
public class EstudioMapaCorte {
    static final double FMAX=1.0;static double CORTE=Double.parseDouble(System.getProperty("estudio.fcorte","0.6"));
    /** por píxel (cada paso px): fracción mínima f (radio de ventana / r) a la que las hebras baja y alta conectan por pasos |Δt|<eps; NaN: sin solape; +inf: no conectan dentro de FMAX */
    static double[] mapa(Gota g,int CW,int CH,double eps,int paso){
        double[][] ts=new double[CW*CH][];double[] rp=new double[CW*CH];
        for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){var hs=g.mascara().hebras(x+.5,y+.5);int k=0;for(var h:hs)if(h.cobertura()>0)k++;if(k==0)continue;
            double[] t=new double[k];int i=0;for(var h:hs)if(h.cobertura()>0)t[i++]=h.t();ts[y*CW+x]=t;rp[y*CW+x]=g.rad(t[0]);}
        double[] out=new double[CW*CH];Arrays.fill(out,Double.NaN);
        int[] mark=new int[CW*CH*4];int stamp=0;
        for(int y=0;y<CH;y+=paso)for(int x=0;x<CW;x+=paso){int p=y*CW+x;if(ts[p]==null||ts[p].length<2)continue;
            int Rm=(int)Math.ceil(FMAX*rp[p]);int n=ts[p].length;stamp++;
            ArrayList<Integer>[] bk=new ArrayList[Rm+1];for(int i=0;i<=Rm;i++)bk[i]=new ArrayList<>();
            bk[0].add(p*4);int goal=p*4+Math.min(n-1,3);double res=Double.POSITIVE_INFINITY;
            outer:for(int c=0;c<=Rm;c++){for(int ii=0;ii<bk[c].size();ii++){int nd=bk[c].get(ii);if(mark[nd]==stamp)continue;mark[nd]=stamp;
                if(nd==goal){res=c;break outer;}
                int q=nd>>2,h=nd&3,qx=q%CW,qy=q/CW;double tq=ts[q][h];
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){if(dx==0&&dy==0)continue;int xx=qx+dx,yy=qy+dy;if(xx<0||yy<0||xx>=CW||yy>=CH)continue;int cc=Math.max(c,(int)Math.ceil(Math.hypot(xx-x,yy-y)));if(cc>Rm)continue;int r=yy*CW+xx;if(ts[r]==null)continue;
                    for(int b=0;b<Math.min(4,ts[r].length);b++){int nn=r*4+b;if(mark[nn]==stamp||Math.abs(ts[r][b]-tq)>=eps)continue;bk[cc].add(nn);}}}}
            out[p]=res/Math.max(rp[p],1e-9);}
        return out;}
    static int color(double f){ // 0 (rojo oscuro: conectan enseguida) -> FMAX (azul claro); inf: gris verdoso claro (no conectan)
        if(Double.isInfinite(f)||f>CORTE)return 0x1B5E20;double u=Math.min(f/FMAX,1);
        double[][] st={{0x7F,0x00,0x00},{0xD7,0x30,0x27},{0xFD,0xAE,0x61},{0xFF,0xFF,0xBF},{0xAB,0xD9,0xE9},{0x2C,0x7B,0xB6}};
        double s=u*(st.length-1);int i=Math.min((int)s,st.length-2);double w=s-i;int c=0;for(int k=0;k<3;k++)c=(c<<8)|(int)Math.round(st[i][k]*(1-w)+st[i+1][k]*w);return c;}
    public static void main(String[] a)throws Exception{
        String[] formas={"suave","S","ჲ"};int[] cs={0,2,4,6,8};double d=Double.parseDouble(a.length>0?a[0]:"4");if(a.length>1)CORTE=Double.parseDouble(a[1]);int paso=2;
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH;
        BufferedImage img=new BufferedImage(110+cs.length*CW,60+formas.length*CH,BufferedImage.TYPE_INT_RGB);
        Graphics2D g2=img.createGraphics();g2.setColor(Color.WHITE);g2.fillRect(0,0,img.getWidth(),img.getHeight());
        for(int f=0;f<formas.length;f++)for(int ci=0;ci<cs.length;ci++){int c=cs[ci];
            double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
            Gota gt=SuiteMatriz_03_Colores.gota(formas[f],d,r1,r2,90,CH*0.62);int x0=110+ci*CW,y0=30+f*CH;
            g2.setColor(new Color(0xCCCCCC));g2.drawRect(x0,y0,CW-1,CH-1);
            if(f==0){g2.setColor(Color.DARK_GRAY);g2.drawString(String.format("r1=%.1f r2=%.1f",r1,r2),x0+8,18);}
            if(gt.silueta()!=null){g2.setColor(Color.GRAY);g2.drawString("rígida",x0+8,y0+16);continue;}
            double[] m=mapa(gt,CW,CH,0.03,paso);
            for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){var hs=gt.mascara().hebras(x+.5,y+.5);if(hs.isEmpty())continue;
                int col=0xEEEEEE;if(hs.size()>=2){double v=m[(y-y%paso)*CW+(x-x%paso)];if(!Double.isNaN(v))col=color(v);}img.setRGB(x0+x,y0+y,col);}}
        g2.setColor(Color.DARK_GRAY);for(int f=0;f<formas.length;f++)g2.drawString(formas[f]+" d="+(int)d,4,30+f*CH+16);
        int by=40+formas.length*CH;g2.drawString("f mínima a la que las hebras baja y alta conectan (ventana ±f·r, pasos |Δt|<0,03):",110,by-6);
        for(int i=0;i<400;i++){g2.setColor(new Color(color(FMAX*i/399.0)));g2.fillRect(110+i,by,1,12);}
        g2.setColor(Color.DARK_GRAY);for(int k=0;k<=4;k++)g2.drawString(String.format("%.2f",FMAX*k/4),110+k*100-(k==4?24:0),by+26);
        g2.setColor(new Color(0x1B5E20));g2.fillRect(560,by,14,12);g2.setColor(Color.DARK_GRAY);g2.drawString("solape legítimo (f>"+CORTE+" o no conectan)",580,by+11);
        g2.setColor(new Color(0xEEEEEE));g2.fillRect(560,by+16,14,12);g2.setColor(Color.DARK_GRAY);g2.drawString("una sola hebra",580,by+27);
        g2.dispose();ImageIO.write(img,"png",new java.io.File("estudio_mapa_corte_d"+(int)d+".png"));
        BufferedImage s=new BufferedImage(img.getWidth()*3/4,img.getHeight()*3/4,BufferedImage.TYPE_INT_RGB);Graphics2D h=s.createGraphics();h.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);h.drawImage(img,0,0,s.getWidth(),s.getHeight(),null);h.dispose();ImageIO.write(s,"jpg",new java.io.File("estudio_mapa_corte_d"+(int)d+"_s.jpg"));
    }
}
