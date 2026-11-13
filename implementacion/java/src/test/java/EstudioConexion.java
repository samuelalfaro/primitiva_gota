/** Caso de estudio (rama estudio_solapes; no está en Todas): clasifica píxeles solapados por conexión en t dentro de una ventana ±f·r (legítimo si hebra baja y alta no conectan; pliegue si sí), y lo cruza con las clases de J. */
import java.util.*;
public class EstudioConexion {
    /** por píxel (muestreado cada PASO): 0 sin solape, 1 legítimo, 2 pliegue: la hebra baja y la alta se conectan DENTRO de la ventana local (±frac·r px alrededor del píxel) por pasos de |Δt|<eps */
    static byte[] clasifica(Gota g,int CW,int CH,double eps,double frac,int paso){
        double[][] ts=new double[CW*CH][];double[] rp=new double[CW*CH];
        for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){var hs=g.mascara().hebras(x+.5,y+.5);int k=0;for(var h:hs)if(h.cobertura()>0)k++;if(k==0)continue;
            double[] t=new double[k];int i=0;for(var h:hs)if(h.cobertura()>0)t[i++]=h.t();ts[y*CW+x]=t;rp[y*CW+x]=g.rad(t[0]);}
        byte[] out=new byte[CW*CH];int[] mark=new int[CW*CH*4];int stamp=0;
        int[] qn=new int[CW*CH*4];
        for(int y=0;y<CH;y+=paso)for(int x=0;x<CW;x+=paso){int p=y*CW+x;if(ts[p]==null||ts[p].length<2)continue;
            int R=(int)Math.ceil(frac*rp[p]);int n=ts[p].length;stamp++;int head=0,tail=0;
            // nodo = pixel*4+h (h<4)
            qn[tail++]=p*4;mark[p*4]=stamp;boolean ok=false;int goal=p*4+Math.min(n-1,3);
            while(head<tail&&!ok){int nd=qn[head++];int q=nd>>2,h=nd&3,qx=q%CW,qy=q/CW;double tq=ts[q][h];
                for(int dy=-1;dy<=1&&!ok;dy++)for(int dx=-1;dx<=1;dx++){if(dx==0&&dy==0)continue;int xx=qx+dx,yy=qy+dy;
                    if(xx<0||yy<0||xx>=CW||yy>=CH||Math.abs(xx-x)>R||Math.abs(yy-y)>R)continue;int r=yy*CW+xx;if(ts[r]==null)continue;
                    for(int b=0;b<Math.min(4,ts[r].length);b++){int nn=r*4+b;if(mark[nn]==stamp||Math.abs(ts[r][b]-tq)>=eps)continue;mark[nn]=stamp;qn[tail++]=nn;if(nn==goal){ok=true;break;}}}}
            out[p]=(byte)(ok?2:1);}
        return out;}
    public static void main(String[] a){
        double eps=Double.parseDouble(a.length>0?a[0]:"0.03");double[] fr={0.06,0.12,0.25,0.5,1.0};int paso=3;
        String[] formas={"suave","S","ჲ"};double[] ds={5,4};
        int CW=SuiteMatriz_03_Colores.CW,CH=SuiteMatriz_03_Colores.CH,cols=SuiteMatriz_03_Colores.RADIOS.length;
        System.out.printf("eps=%.3f, ventana local ±frac·r px, muestreo cada %d px%n",eps,paso);
        System.out.println("forma d | frac | legítimo  pliegue   [pliegue según J: legítimo/compresión/pliegue]  [legítimo según J: leg/comp/pl]");
        for(int f=0;f<formas.length;f++)for(double d:ds)for(double fc:fr){long[][] ct=new long[3][3];
            for(int c=0;c<cols;c++){
                double r1=SuiteMatriz_03_Colores.RADIOS[c][0],r2=SuiteMatriz_03_Colores.RADIOS[c][1];
                Gota gt=SuiteMatriz_03_Colores.gota(formas[f],d,r1,r2,90,CH*0.62);if(gt.silueta()!=null)continue;
                byte[] cl=clasifica(gt,CW,CH,eps,fc,paso);
                for(int y=0;y<CH;y++)for(int x=0;x<CW;x++){int k=cl[y*CW+x];if(k==0)continue;
                    var hs=gt.mascara().hebras(x+.5,y+.5);double j=EstudioTresClases.J(gt,hs,x+.5,y+.5);ct[k][j<=0?2:j<0.3?1:0]++;}}
            long n1=ct[1][0]+ct[1][1]+ct[1][2],n2=ct[2][0]+ct[2][1]+ct[2][2];
            System.out.printf("%-5s d=%.0f | %.2f | %7d %7d   [%d / %d / %d]  [%d / %d / %d]%n",formas[f],d,fc,n1,n2,ct[2][0],ct[2][1],ct[2][2],ct[1][0],ct[1][1],ct[1][2]);}
    }
}
