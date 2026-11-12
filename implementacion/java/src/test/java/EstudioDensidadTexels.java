/** Caso de estudio (rama estudio_solapes, ver ESTUDIO_SOLAPES.md; no está en Todas). */
import java.util.*;
public class EstudioDensidadTexels {
    /** densidad de texels (|det d(u,v)/d(x,y)|) por píxel² con diferencias centrales; NaN si hay salto de costura */
    static double dens(Gota g,Opciones op,double x,double y){
        Coordenadas.Uv a=Coordenadas.uv(g,x+1,y,op),b=Coordenadas.uv(g,x-1,y,op),c=Coordenadas.uv(g,x,y+1,op),d=Coordenadas.uv(g,x,y-1,op);
        double ux=(a.u()-b.u())/2,vx=(a.v()-b.v())/2,uy=(c.u()-d.u())/2,vy=(c.v()-d.v())/2;
        if(Math.abs(ux)>0.2||Math.abs(vx)>0.2||Math.abs(uy)>0.2||Math.abs(vy)>0.2)return Double.NaN;
        return ux*vy-uy*vx;}
    public static void main(String[] args)throws Exception{
        String[] formas={"suave","S","ჲ"};double[] ds={5,4};int NB=40,NR=10;
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH,cols=SuiteMatriz_03_Colores.RADIOS.length;
        Opciones op=SuiteMatriz_03_Colores.OPCIONES.conSolape(Solape.TRAZO);
        System.out.println("Q = densidad de texels / mediana de los píxeles de 1 hebra con el mismo t y rho (Q>2: texels comprimidos >2x)");
        System.out.println("forma    | clase       píxeles  Q mediana  Q>2 (%)  Q<0 invertido (%)");
        for(String forma:formas){
            List<double[]>[] sample=new List[3];for(int i=0;i<3;i++)sample[i]=new ArrayList<>();
            List<double[]> uno=new ArrayList<>(); List<Object[]> multi=new ArrayList<>();
            for(double d:ds)for(int c=0;c<cols;c++){
                double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
                Gota gt=SuiteMatriz_03_Colores.gota(forma,d,r1,r2,90,CH*0.62);if(gt.silueta()!=null)continue;
                for(int y=2;y<CH-2;y+=1)for(int x=2;x<CW-2;x+=1){
                    var hs=gt.mascara().hebras(x+.5,y+.5);if(hs.isEmpty())continue;
                    double D=dens(gt,op,x+.5,y+.5);if(Double.isNaN(D))continue;boolean salto=false;{Coordenadas cc=Coordenadas.de(gt,op);double t0=Solape.TRAZO.ganador(cc,x+.5,y+.5).t();for(int[] e:new int[][]{{2,0},{-2,0},{0,2},{0,-2}})if(Math.abs(Solape.TRAZO.ganador(cc,x+.5+e[0],y+.5+e[1]).t()-t0)>0.03)salto=true;}if(salto)continue;
                    double t=Solape.TRAZO.ganador(Coordenadas.de(gt,op),x+.5,y+.5).t();double rho=Math.hypot(x+.5-gt.cx(t),y+.5-gt.cy(t))/Math.max(gt.rad(t),1e-9);
                    if(hs.size()==1)uno.add(new double[]{t,D,rho});
                    else{double j=EstudioTresClases.J(gt,hs,x+.5,y+.5);multi.add(new Object[]{t,D,j<=0?2:j<0.3?1:0,rho});}
                }}
            double[][] med=new double[NB][];List<Double>[] bins=new List[NB*NR];for(int i=0;i<NB*NR;i++)bins[i]=new ArrayList<>();
            for(double[] p:uno)bins[Math.min(NB-1,(int)(p[0]*NB))*NR+Math.min(NR-1,(int)(p[2]*NR/1.5))].add(p[1]);
            double[] m=new double[NB*NR];for(int i=0;i<NB*NR;i++){Collections.sort(bins[i]);m[i]=bins[i].size()<5?Double.NaN:bins[i].get(bins[i].size()/2);}
            List<Double>[] qs=new List[3];for(int i=0;i<3;i++)qs[i]=new ArrayList<>();
            for(Object[] o:multi){int b=Math.min(NB-1,(int)((double)o[0]*NB))*NR+Math.min(NR-1,(int)((double)o[3]*NR/1.5));if(Double.isNaN(m[b])||m[b]<=0)continue;qs[(int)o[2]].add((double)o[1]/m[b]);}
            String[] nm={"legítimo","compresión","pliegue"};
            for(int k=0;k<3;k++){List<Double> q=qs[k];Collections.sort(q);long g2=q.stream().filter(v->v>2).count(),g4=q.stream().filter(v->v<0).count();
                System.out.printf("%-8s | %-11s %7d  %9.2f  %7.1f  %7.1f%n",forma,nm[k],q.size(),q.isEmpty()?Double.NaN:q.get(q.size()/2),q.isEmpty()?0:100.0*g2/q.size(),q.isEmpty()?0:100.0*g4/q.size());}
        }
    }
}
