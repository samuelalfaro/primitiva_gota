/** Caso de estudio (rama estudio_solapes; no está en Todas): signo del jacobiano J = 1 - k·y por hebra frente a f, en los píxeles con 2+ hebras. */
public class EstudioSignoJ {
    static double Jh(Gota g,double t,double x,double y){double k=EstudioTresClases.kappa(g,t);double[] T=EstudioTresClases.tan(g,t);double yn=-(x-g.cx(t))*T[1]+(y-g.cy(t))*T[0];return 1-k*yn;}
    public static void main(String[] a){
        String[] formas={"suave","S","ჲ"};double[] ds={5,4};int paso=2;
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH,cols=SuiteMatriz_03_Colores.RADIOS.length;
        String[] fn={"f ≤0.3","0.3–0.6","0.6–1.0","no conectan"};
        System.out.println("Patrón de signos de J por hebra (+,+ = misma orientación; opuestos = una hebra con J<0) según f");
        for(String forma:formas){long[][] n=new long[4][2];long[] mal=new long[4];
            for(double d:ds)for(int c=0;c<cols;c++){
                double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
                Gota gt=SuiteMatriz_03_Colores.gota(forma,d,r1,r2,90,CH*0.62);if(gt.silueta()!=null)continue;
                double[] fm=EstudioMapaCorte.mapa(gt,CW,CH,0.03,paso);
                for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){var hs=gt.mascara().hebras(x+.5,y+.5);int k=0;boolean pos=false,neg=false;
                    for(var h:hs){if(h.cobertura()<=0)continue;k++;double j=Jh(gt,h.t(),x+.5,y+.5);if(j>0)pos=true;else neg=true;}
                    if(k<2)continue;double f=fm[(y-y%paso)*CW+(x-x%paso)];if(Double.isNaN(f))continue;
                    int b=Double.isInfinite(f)||f>1?3:f<=0.3?0:f<=0.6?1:f<=1.0?2:3;
                    int s=pos&&neg?1:0;n[b][s]++;if(!pos)mal[b]++;}}
            System.out.printf("%n=== %s%n  tramo de f   | píxeles | mismo signo (J>0 en todas) | signos opuestos | todas J<0%n",forma);
            for(int b=0;b<4;b++){long t=n[b][0]+n[b][1];System.out.printf("  %-12s | %7d | %9.1f %%              | %8.1f %%      | %5.1f %%%n",fn[b],t,t==0?0:100.0*(n[b][0]-mal[b])/t,t==0?0:100.0*n[b][1]/t,t==0?0:100.0*mal[b]/t);}
        }}}
