import java.awt.image.BufferedImage;
import java.util.*;
/**
 * Caso de estudio (rama estudio_solapes, ver ESTUDIO_SOLAPES.md; no está en {@link Todas}). Para los píxeles cubiertos por dos o
 * más hebras (formas no rígidas, d = 5 y 4) reparte la fracción de discrepancia PLANO/TRAZO (> 48 de 255) según: el cociente
 * rho_min/rho_max, el coseno entre las tangentes de la pasada de menor y mayor t, el tipo (banda si |u/r| < 0.5, tapa si no), el
 * signo de u (componente de p - c(t) a lo largo de la tangente) y el tramo de t. Es una medida de dónde discrepan las dos reglas, no de cuál acierta.
 */
public class EstudioProductoEscalar {
    static double[] tan(Gota g,double t){double e=1e-4,t0=Math.max(0,t-e),t1=Math.min(1,t+e);
        double x=g.cx(t1)-g.cx(t0),y=g.cy(t1)-g.cy(t0),n=Math.hypot(x,y);return n<1e-12?new double[]{1,0}:new double[]{x/n,y/n};}
    // acumuladores por clave: [n, discrepantes, suma m]
    static Map<String,double[]> acc(Map<String,Map<String,double[]>> M,String tabla){return M.computeIfAbsent(tabla,k->new TreeMap<>());}
    static void add(Map<String,Map<String,double[]>> M,String tabla,String k,double m){
        double[] a=acc(M,tabla).computeIfAbsent(k,z->new double[3]);a[0]++;if(m>48)a[1]++;a[2]+=m;}
    public static void main(String[] args)throws Exception{
        String[] formas={"suave","S","ჲ"}; double[] ds={5,4};
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH,cols=SuiteMatriz_03_Colores.RADIOS.length;
        for(String forma:formas){
            Map<String,Map<String,double[]>> M=new LinkedHashMap<>();
            double sx=0,sy=0,sxx=0,syy=0,sxy=0,sx2=0,sxy2=0,sxx2=0,n=0; // correlaciones m con rr y con |cos|
            double[] cr=new double[6]; // sums for pearson: m,rr,m2,rr2,m*rr ; cos
            double sm=0,sr=0,sc=0,smm=0,srr=0,scc=0,smr=0,smc=0;
            for(double d:ds)for(int c=0;c<cols;c++){
                double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
                Gota gp=SuiteMatriz_03_Colores.gota(forma,d,r1,r2,90,CH*0.62),gt=SuiteMatriz_03_Colores.gota(forma,d,r1,r2,90,CH*0.62);
                if(gt.silueta()!=null)continue; // formas rígidas: no hay solape
                BufferedImage A=SuiteMatriz_03_Colores.sobreBlanco(gp,Solape.PLANO),B=SuiteMatriz_03_Colores.sobreBlanco(gt,Solape.TRAZO);
                for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){
                    int p=A.getRGB(x,y)&0xFFFFFF,q=B.getRGB(x,y)&0xFFFFFF; if(p==0xFFFFFF&&q==0xFFFFFF)continue;
                    var hs=gt.mascara().hebras(x+.5,y+.5); if(hs.size()<2)continue;
                    int m=0;for(int s=0;s<24;s+=8)m=Math.max(m,Math.abs(((p>>s)&255)-((q>>s)&255)));
                    double[] u=new double[2],rho=new double[2];
                    double[][] T=new double[2][]; int[] idx={0,hs.size()-1};
                    for(int k=0;k<2;k++){double t=hs.get(idx[k]).t();T[k]=tan(gt,t);
                        double px=x+.5-gt.cx(t),py=y+.5-gt.cy(t),r=Math.max(gt.rad(t),1e-9);
                        u[k]=(px*T[k][0]+py*T[k][1])/r; double v=(-px*T[k][1]+py*T[k][0])/r; rho[k]=Math.hypot(u[k],v);}
                    double rr=Math.min(rho[0],rho[1])/Math.max(Math.max(rho[0],rho[1]),1e-9);
                    double ct=T[0][0]*T[1][0]+T[0][1]*T[1][1];
                    String ta=Math.abs(u[0])<0.5?"B":"T",tb=Math.abs(u[1])<0.5?"B":"T";
                    String tipo=ta.equals(tb)?ta+tb:"BT";
                    add(M,"1 rho_min/rho_max", rr>=.9?"d >=.9 empate":rr>=.7?"c .7-.9":rr>=.5?"b .5-.7":"a <.5",m);
                    add(M,"2 cos(T_a,T_b)", ct>.5?"d >+.5 mismo sentido":ct>0?"c 0..+.5":ct>-.5?"b -.5..0 cruce":"a <-.5 sentido contrario",m);
                    add(M,"3 tipo (banda |u/r|<.5 / tapa)",tipo.equals("BB")?"BB":tipo.equals("TT")?"TT":"BT",m);
                    double ta_=hs.get(0).t(), tb_=hs.get(hs.size()-1).t();
                    String sg=(u[0]>=0?"+":"-")+(u[1]>=0?"+":"-");
                    add(M,"5 t_a (pasada baja) x signo",(ta_<.2?"a t_a<.2":ta_<.5?"b t_a .2-.5":"c t_a>=.5")+"  "+sg,m);
                    add(M,"6 t_b (pasada alta) x signo",(tb_>.8?"a t_b>.8":tb_>.5?"b t_b .5-.8":"c t_b<=.5")+"  "+sg,m);
                    add(M,"4 signo de u (a,b)",(u[0]>=0?"+":"-")+(u[1]>=0?"+":"-"),m);
                    sm+=m;sr+=rr;sc+=Math.abs(ct);smm+=m*m;srr+=rr*rr;scc+=ct*ct*0+Math.abs(ct)*Math.abs(ct);smr+=m*rr;smc+=m*Math.abs(ct);n++;
                }
            }
            System.out.printf("%n=== %s (d=5 y 4, formas no rígidas, píxeles con 2+ hebras: %.0f) ===%n",forma,n);
            if(n==0)continue;
            for(var e:M.entrySet()){System.out.println(e.getKey());
                for(var f:e.getValue().entrySet()){double[] a=f.getValue();System.out.printf("   %-28s n=%7.0f  discrepantes %5.1f %%   m medio %5.1f%n",f.getKey(),a[0],100*a[1]/a[0],a[2]/a[0]);}}
            double cmr=(smr/n-(sm/n)*(sr/n))/Math.sqrt((smm/n-(sm/n)*(sm/n))*(srr/n-(sr/n)*(sr/n)));
            double cmc=(smc/n-(sm/n)*(sc/n))/Math.sqrt((smm/n-(sm/n)*(sm/n))*(scc/n-(sc/n)*(sc/n)));
            System.out.printf("Pearson m~rho_min/rho_max: %.2f    m~|cos T_a,T_b|: %.2f%n",cmr,cmc);
        }
    }
}
