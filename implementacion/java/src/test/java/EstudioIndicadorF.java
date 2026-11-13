/** Caso de estudio (rama estudio_solapes; no está en Todas): contrasta f (fracción mínima de conexión de EstudioMapaCorte) con la densidad de texels y con el salto de pasada ganadora, por tramos de f. */
import java.util.*;
public class EstudioIndicadorF {
    static double[] rank(double[] v){Integer[] ix=new Integer[v.length];for(int i=0;i<ix.length;i++)ix[i]=i;Arrays.sort(ix,Comparator.comparingDouble(i->v[i]));double[] r=new double[v.length];int i=0;while(i<ix.length){int j=i;while(j<ix.length&&v[ix[j]]==v[ix[i]])j++;double rk=(i+j-1)/2.0;for(int k=i;k<j;k++)r[ix[k]]=rk;i=j;}return r;}
    static double spearman(List<double[]> s,int a,int b){int n=s.size();if(n<10)return Double.NaN;double[] x=new double[n],y=new double[n];for(int i=0;i<n;i++){x[i]=s.get(i)[a];y[i]=s.get(i)[b];}
        double[] rx=rank(x),ry=rank(y);double mx=0,my=0;for(int i=0;i<n;i++){mx+=rx[i];my+=ry[i];}mx/=n;my/=n;double sxy=0,sxx=0,syy=0;for(int i=0;i<n;i++){sxy+=(rx[i]-mx)*(ry[i]-my);sxx+=(rx[i]-mx)*(rx[i]-mx);syy+=(ry[i]-my)*(ry[i]-my);}return sxy/Math.sqrt(sxx*syy);}
    public static void main(String[] args){
        String[] formas={"suave","S","ჲ"};double[] ds={5,4};int NB=40,NR=10,paso=2;double FC=0.6;
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH,cols=SuiteMatriz_03_Colores.RADIOS.length;
        Opciones op=SuiteMatriz_03_Colores.OPCIONES.conSolape(Solape.TRAZO);
        double[] edges={0,0.1,0.2,0.3,0.4,0.5,0.6,1.0001};
        System.out.println("columnas: f en ventana | píxeles | Q mediana (densidad texels / referencia 1 hebra) | % con salto de t (±2px, la pasada ganadora cambia)");
        for(String forma:formas){
            // referencia de densidad
            List<double[]> uno=new ArrayList<>();List<double[]> pix=new ArrayList<>(); // pix: {f,Q,salto,form}
            List<Object[]> multi=new ArrayList<>();
            for(double d:ds)for(int c=0;c<cols;c++){
                double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
                Gota gt=SuiteMatriz_03_Colores.gota(forma,d,r1,r2,90,CH*0.62);if(gt.silueta()!=null)continue;Coordenadas cc=Coordenadas.de(gt,op);
                double[] fm=EstudioMapaCorte.mapa(gt,CW,CH,0.03,paso);
                for(int y=3;y<CH-3;y++)for(int x=3;x<CW-3;x++){var hs=gt.mascara().hebras(x+.5,y+.5);if(hs.isEmpty())continue;
                    var g0=Solape.TRAZO.ganador(cc,x+.5,y+.5);double t=g0.t();double rho=Math.hypot(x+.5-gt.cx(t),y+.5-gt.cy(t))/Math.max(gt.rad(t),1e-9);
                    boolean salto=false;for(int[] e:new int[][]{{2,0},{-2,0},{0,2},{0,-2}})if(Math.abs(Solape.TRAZO.ganador(cc,x+.5+e[0],y+.5+e[1]).t()-t)>0.03)salto=true;
                    double D=EstudioDensidadTexels.dens(gt,op,x+.5,y+.5);
                    if(hs.size()==1){if(!salto&&!Double.isNaN(D))uno.add(new double[]{t,D,rho});continue;}
                    double f=fm[(y-y%paso)*CW+(x-x%paso)];if(Double.isNaN(f))continue;
                    multi.add(new Object[]{f,t,rho,D,salto});}}
            double[] m=new double[NB*NR];List<Double>[] bins=new List[NB*NR];for(int i=0;i<NB*NR;i++)bins[i]=new ArrayList<>();
            for(double[] p:uno)bins[Math.min(NB-1,(int)(p[0]*NB))*NR+Math.min(NR-1,(int)(p[2]*NR/1.5))].add(p[1]);
            for(int i=0;i<NB*NR;i++){Collections.sort(bins[i]);m[i]=bins[i].size()<5?Double.NaN:bins[i].get(bins[i].size()/2);}
            List<double[]> S=new ArrayList<>(); // {f,Q,saltoIndicador,absQlog}
            List<double[]>[] bq=new List[edges.length];List<Integer> nb=new ArrayList<>();long[] cnt=new long[edges.length],sj=new long[edges.length];List<Double>[] qq=new List[edges.length];for(int i=0;i<edges.length;i++)qq[i]=new ArrayList<>();
            long noconn=0,sjn=0;List<Double> qn=new ArrayList<>();
            for(Object[] o:multi){double f=(double)o[0],t=(double)o[1],rho=(double)o[2],D=(double)o[3];boolean salto=(boolean)o[4];
                if(Double.isInfinite(f)){noconn++;if(salto)sjn++;int b=Math.min(NB-1,(int)(t*NB))*NR+Math.min(NR-1,(int)(rho*NR/1.5));if(!Double.isNaN(D)&&!salto&&!Double.isNaN(m[b])&&m[b]>0)qn.add(D/m[b]);continue;}
                int e=0;while(e<edges.length-2&&f>=edges[e+1])e++;cnt[e]++;if(salto)sj[e]++;
                int b=Math.min(NB-1,(int)(t*NB))*NR+Math.min(NR-1,(int)(rho*NR/1.5));
                if(!Double.isNaN(D)&&!salto&&!Double.isNaN(m[b])&&m[b]>0){double q=D/m[b];qq[e].add(q);if(f<=FC)S.add(new double[]{f,q,salto?1:0});}
                if(f<=FC)S.add(new double[]{f,Double.NaN,salto?1:0});}
            System.out.printf("%n=== %s (d=5 y 4)%n",forma);
            for(int e=0;e<edges.length-1;e++){Collections.sort(qq[e]);System.out.printf("  f %.1f–%.1f | %6d | Q med %s (n=%d) | salto t %5.1f %%%n",edges[e],Math.min(edges[e+1],1.0),cnt[e],qq[e].isEmpty()?"  - ":String.format("%.2f",qq[e].get(qq[e].size()/2)),qq[e].size(),cnt[e]==0?0:100.0*sj[e]/cnt[e]);}
            Collections.sort(qn);System.out.printf("  no conectan | %6d | Q med %s (n=%d) | salto t %5.1f %%%n",noconn,qn.isEmpty()?"  - ":String.format("%.2f",qn.get(qn.size()/2)),qn.size(),noconn==0?0:100.0*sjn/noconn);
            List<double[]> sq=new ArrayList<>(),sg=new ArrayList<>();for(double[] r:S){if(!Double.isNaN(r[1]))sq.add(new double[]{r[0],r[1]});sg.add(new double[]{r[0],r[2]});}
            System.out.printf("  Spearman (f ≤ %.1f): f–Q %.2f (n=%d) | f–salto de t %.2f (n=%d)   [hipótesis: f↓ ⇒ Q↑ y salto↑, es decir correlaciones negativas]%n",FC,spearman(sq,0,1),sq.size(),spearman(sg,0,1),sg.size());
        }
    }
}
