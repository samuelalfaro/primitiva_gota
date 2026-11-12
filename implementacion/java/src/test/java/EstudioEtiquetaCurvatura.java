import java.awt.image.BufferedImage;
import java.util.*;
/**
 * Caso de estudio (rama estudio_solapes, ver ESTUDIO_SOLAPES.md; no está en {@link Todas}). Etiqueta cada píxel solapado (dos o
 * más hebras, formas no rígidas, d = 5 y 4) como PLEGADO si en alguna de sus pasadas el píxel está en el lado cóncavo más allá del
 * centro de curvatura de la espina (distancia normal > 1/|kappa|: cara interior de un codo cerrado) y como LEGÍTIMO si no, y mide
 * con el AUC si cada variable (cociente de rho, coseno entre tangentes, Δt, u/r, ...) separa las dos clases. La curvatura sale de
 * diferencias finitas y se ignora r'(t): es una etiqueta aproximada.
 */
public class EstudioEtiquetaCurvatura {
    static double[] tan(Gota g,double t){double e=1e-4,t0=Math.max(0,t-e),t1=Math.min(1,t+e);double x=g.cx(t1)-g.cx(t0),y=g.cy(t1)-g.cy(t0),n=Math.hypot(x,y);return n<1e-12?new double[]{1,0}:new double[]{x/n,y/n};}
    /** curvatura con signo (cruce(c',c'')/|c'|^3), por diferencias centrales */
    static double kappa(Gota g,double t){double h=2e-3;double a=Math.max(0,t-h),b=Math.min(1,t+h),m=(a+b)/2;
        double x0=g.cx(a),y0=g.cy(a),x1=g.cx(m),y1=g.cy(m),x2=g.cx(b),y2=g.cy(b);
        double dx=(x2-x0)/(b-a),dy=(y2-y0)/(b-a),ddx=(x2-2*x1+x0)/((b-a)*(b-a)/4),ddy=(y2-2*y1+y0)/((b-a)*(b-a)/4);
        double n=Math.hypot(dx,dy);return n<1e-9?0:(dx*ddy-dy*ddx)/(n*n*n);}
    static class P{double v;boolean pleg;P(double v,boolean p){this.v=v;pleg=p;}}
    static double auc(List<double[]> s){ // s: {valor, etiqueta(1=plegado)}; prob de que plegado > legítimo
        s.sort(Comparator.comparingDouble(a->a[0])); double r=0,n1=0,n0=0; int i=0,N=s.size(); double sumRank1=0;
        while(i<N){int j=i;while(j<N&&s.get(j)[0]==s.get(i)[0])j++;double rk=(i+j+1)/2.0;for(int k=i;k<j;k++)if(s.get(k)[1]==1){sumRank1+=rk;n1++;}else n0++;i=j;}
        return n1==0||n0==0?Double.NaN:(sumRank1-n1*(n1+1)/2)/(n1*n0);}
    public static void main(String[] args)throws Exception{
        String[] formas={"suave","S","ჲ"};double[] ds={5,4};
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH,cols=SuiteMatriz_03_Colores.RADIOS.length;
        String[] vn={"rho_min/rho_max","|cos T_a,T_b|","cos T_a,T_b","Δt","u/r pasada baja","u/r pasada alta","(−,−) indicador","discrepa PLANO≠TRAZO"};
        for(String forma:formas){
            List<List<double[]>> S=new ArrayList<>();for(int i=0;i<vn.length;i++)S.add(new ArrayList<>());
            int nP=0,nL=0;double dP=0,dL=0;
            for(double d:ds)for(int c=0;c<cols;c++){
                double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
                Gota gp=SuiteMatriz_03_Colores.gota(forma,d,r1,r2,90,CH*0.62),gt=SuiteMatriz_03_Colores.gota(forma,d,r1,r2,90,CH*0.62);
                if(gt.silueta()!=null)continue;
                BufferedImage A=SuiteMatriz_03_Colores.sobreBlanco(gp,Solape.PLANO),B=SuiteMatriz_03_Colores.sobreBlanco(gt,Solape.TRAZO);
                for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){
                    int p=A.getRGB(x,y)&0xFFFFFF,q=B.getRGB(x,y)&0xFFFFFF;if(p==0xFFFFFF&&q==0xFFFFFF)continue;
                    var hs=gt.mascara().hebras(x+.5,y+.5);if(hs.size()<2)continue;
                    int m=0;for(int s=0;s<24;s+=8)m=Math.max(m,Math.abs(((p>>s)&255)-((q>>s)&255)));
                    boolean pleg=false;
                    for(var hb:hs){double t=hb.t(),k=kappa(gt,t);if(Math.abs(k)<1e-9)continue;double[] T=tan(gt,t);
                        double px=x+.5-gt.cx(t),py=y+.5-gt.cy(t);double vl=-px*T[1]+py*T[0];double vc=vl*Math.signum(k);
                        if(vc>1.0/Math.abs(k)){pleg=true;break;}}
                    double[] u=new double[2],rho=new double[2];double[][] T=new double[2][];int[] idx={0,hs.size()-1};
                    for(int k=0;k<2;k++){double t=hs.get(idx[k]).t();T[k]=tan(gt,t);double px=x+.5-gt.cx(t),py=y+.5-gt.cy(t),r=Math.max(gt.rad(t),1e-9);
                        u[k]=(px*T[k][0]+py*T[k][1])/r;double v=(-px*T[k][1]+py*T[k][0])/r;rho[k]=Math.hypot(u[k],v);}
                    double rr=Math.min(rho[0],rho[1])/Math.max(Math.max(rho[0],rho[1]),1e-9),ct=T[0][0]*T[1][0]+T[0][1]*T[1][1];
                    double dt=hs.get(hs.size()-1).t()-hs.get(0).t();double lab=pleg?1:0;
                    double[] vals={rr,Math.abs(ct),ct,dt,u[0],u[1],(u[0]<0&&u[1]<0)?1:0,m>48?1:0};
                    for(int i=0;i<vals.length;i++)S.get(i).add(new double[]{vals[i],lab});
                    if(pleg){nP++;if(m>48)dP++;}else{nL++;if(m>48)dL++;}
                }
            }
            System.out.printf("%n=== %s (d=5 y 4): píxeles con 2+ hebras  plegado=%d  legítimo=%d%n",forma,nP,nL);
            System.out.printf("   discrepantes PLANO≠TRAZO: plegado %.1f %%   legítimo %.1f %%%n",nP==0?0:100*dP/nP,nL==0?0:100*dL/nL);
            System.out.printf("   AUC (prob. de que un píxel plegado tenga valor mayor que uno legítimo; 0.5 = no separa):%n");
            for(int i=0;i<vn.length;i++)System.out.printf("     %-24s %.2f%n",vn[i],auc(S.get(i)));
        }
    }
}
