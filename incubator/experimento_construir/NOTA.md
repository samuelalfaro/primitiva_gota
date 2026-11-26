# Experimento: ¿`Grafo.construir()` puede devolver una `Geometria` más simple?

Idea (especulativa, de Samuel): que construir el grafo devuelva `Geometria` en lugar de `Grafo`. Con una sola arista, la gota
(con su relleno); sin aristas, un disco (gota de centros coincidentes y radios iguales). Esta carpeta guarda las pruebas, aparte
de la librería. Se ejecuta con `UnaArista` (necesita las clases de `src/main/java`).

## Primera medida: un `Grafo` de una arista frente a `Pintada.de(gota, relleno equivalente)`

| Caso | Silueta distinta | Píxeles distintos | Diferencia máx. por canal |
|---|---|---|---|
| negro, radios iguales, recta | 0 | 0 | 0 |
| dos colores, radios iguales, recta | 0 | 0 | 0 |
| dos colores, radios 50 y 15, recta | 0 | 0 | 0 |
| dos colores, curva con tiradores | 0 | 17182 | 28 |
| curva con `ajustarPendientes(false)` | 0 | 17182 | 28 |

- La **silueta** coincide siempre: la gota que da el `Grafo` es la misma.
- En **recta** el color coincide al píxel con `Colores.dosColores`.
- En **curva** hay una diferencia de color de hasta 28/255 (no es de forma ni de las pendientes del radio: no cambia con
  `ajustarPendientes(false)`).

### Causa (medida con `Grafo.rellenoDe(i)`, visibilidad de paquete)
- No es el relleno: pintar la gota con el relleno lateral exacto que construye el `Grafo` da la misma diferencia (17182 px, 28).
- Es el **reparto**: con `Reparto.POR_GOTA` el `Grafo` de una arista coincide con `Pintada.de(gota, dosColores)` en los cinco
  casos, 0 píxeles distintos. La diferencia aparece solo con el valor por defecto, `Reparto.GLOBAL`, y solo en curva.
- Con una única arista, `GLOBAL` debería dar los mismos cortes que `POR_GOTA` (v0 = c1, v1 = c3); si no es así, la diferencia
  está en cómo `cortesGlobales` / `Tira.fija` reparte v frente a la tira propia de la gota (longitud de arco de `sTotal` frente
  a la parametrización de la gota). Sin aclarar cuál de las dos es la «correcta»; no lo toco.
- Para `construir()`: devolver la gota con un `Grafo` de una arista es seguro en silueta y, en color, con `POR_GOTA`.

## Siguiente
- Decidir si en una arista `GLOBAL` debe coincidir con `POR_GOTA` (ver arriba).
- Después, `label`, `altura` y `textura` (propiedades del `Grafo` que habría que trasladar).
- ~~Y el disco (nodo sin aristas)~~: hecho, ver abajo.

## Segunda medida: el disco (`Gota` de centros coincidentes y radios iguales)

`Disco.java`: `Gota.lineal(p, 100, p, 100, Perfil.recto())` con `Colores.cuatroColores`, frente a la misma gota con los centros
separados 0,5 (imagen `disco_coincidente_vs_separado.png`, coincidente a la izquierda).

- Es un **círculo**: 31 700 píxeles no blancos frente a los 31 416 del círculo ideal (el exceso es el borde suavizado).
- **Orientación por defecto: el eje x**, como decían las reglas de la gota de cuatro arcos: el extremo 1 queda a la izquierda y
  el 2 a la derecha. Dibujada es indistinguible de la de centros separados 0,5; difieren 136 píxeles de borde en la silueta y
  hay diferencias numéricas pequeñas de color, sin efecto visible.
- Conclusión (Samuel): no hace falta parámetro de orientación en el disco. Si la orientación se da por arista en el `Grafo`, el
  caso de una arista no es distinto; si es intrínseca a los centros o a las curvas, el valor por defecto (−x para un centro y +x
  para el otro, en cada centro solapado) también es correcto. La orientación que se quiera se dará después con la transformada
  de `Grupo` (`Transformada`, pendiente), que arrastra con ella las coordenadas de color y textura.
