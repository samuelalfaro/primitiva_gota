/** Caso de estudio (rama estudio_solapes, ver ESTUDIO_SOLAPES.md; no está en Todas). */
public class EstudioSaltosUv {
    public static void main(String[] args){
        String[] formas={"suave","S","ჲ"};double[] ds={5,4};
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH,cols=SuiteMatriz_03_Colores.RADIOS.length;
        Opciones op=SuiteMatriz_03_Colores.OPCIONES.conSolape(Solape.TRAZO);
        System.out.println("forma | clase       píxeles  con salto de uv (%)   (salto: |Δt|>0.03 o |Δu|,|Δv|>0.2 a ±1–2 px)");
        for(String forma:formas){long[] n=new long[3],sj=new long[3];
            for(double d:ds)for(int c=0;c<cols;c++){
                double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
                Gota gt=SuiteMatriz_03_Colores.gota(forma,d,r1,r2,90,CH*0.62);if(gt.silueta()!=null)continue;Coordenadas cc=Coordenadas.de(gt,op);
                for(int y=3;y<CH-3;y++)for(int x=3;x<CW-3;x++){var hs=gt.mascara().hebras(x+.5,y+.5);if(hs.size()<2)continue;
                    double j=EstudioTresClases.J(gt,hs,x+.5,y+.5);int k=j<=0?2:j<0.3?1:0;n[k]++;
                    boolean s=Double.isNaN(EstudioDensidadTexels.dens(gt,op,x+.5,y+.5));double t0=Solape.TRAZO.ganador(cc,x+.5,y+.5).t();
                    for(int[] e:new int[][]{{2,0},{-2,0},{0,2},{0,-2}})if(Math.abs(Solape.TRAZO.ganador(cc,x+.5+e[0],y+.5+e[1]).t()-t0)>0.03)s=true;
                    if(s)sj[k]++;}}
            String[] nm={"legítimo","compresión","pliegue"};
            for(int k=0;k<3;k++)System.out.printf("%-5s | %-11s %7d  %6.1f %%%n",forma,nm[k],n[k],n[k]==0?0:100.0*sj[k]/n[k]);}
    }
}
