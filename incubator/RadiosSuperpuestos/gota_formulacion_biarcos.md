# Primitiva Gota: silueta por tapas y biarcos con afinidad a la tangente

Estado: propuesta de formulación (2026-11-06). Sustituye, para círculos, a «unión de círculos» (A) cuando los círculos se solapan o se absorben. Las elipses quedan descartadas de la primitiva.

## 1. Datos y marco

- Dos círculos (c1, r1) y (c2, r2); d = |c2 − c1|.
- Tangente T = (c2 − c1)/d si d > 0. Con d = 0 se usa la T explícita; sin ella, T = (1, 0).
- Normal N = T girada +90° (eje y matemático; en coordenadas de imagen es (Ty, −Tx)).
- Todo se escribe en el marco (T, N) con c1 como origen. La silueta es simétrica respecto al eje T.
- Cada tapa mira hacia fuera: la tapa 1 hacia −T y la tapa 2 hacia +T.

## 2. Incógnitas y puntos de empalme

Las incógnitas son ψ1, ψ2 ∈ (0, π): el semiángulo de cada tapa, medido desde la dirección hacia la que mira.

- Extremo de la tapa 1 (lado +N): P1 = c1 + r1·(−cos ψ1·T + sin ψ1·N).
- Extremo de la tapa 2 (lado +N): P2 = c2 + r2·(cos ψ2·T + sin ψ2·N).
- Los extremos del lado −N son sus espejos (N → −N).
- Tangentes de recorrido (de la tapa 1 a la tapa 2 por el lado +N):
  - t1 = sin ψ1·T + cos ψ1·N
  - t2 = sin ψ2·T − cos ψ2·N
  (son perpendiculares a los radios libres c1→P1 y c2→P2: la marca de ángulo recto del esquema).

Silueta = tapa 1 (arco de r1 con semiángulo ψ1 centrado en −T) + conector superior P1→P2 + tapa 2 (arco de r2, semiángulo ψ2, centrado en +T) + conector inferior (espejo del superior). El conjunto es G1 en los cuatro empalmes.

Admisible: (P2 − P1)·T > 0 y el biarco de la sección 3 existe con |β| < π en ambos arcos.

## 3. Conector: biarco simétrico

Con v = P2 − P1, t = t1 + t2, a = 4 − |t|²:

- Si a ≠ 0: s = (−v·t + √((v·t)² + a·|v|²)) / a.
- Si a = 0: s = |v|² / (2·v·t).
- Exigir s > 0. Sean A = P1 + s·t1, B = P2 − s·t2 y J = (A + B)/2.

Arco 1: de P1 con tangente t1 hasta J. Arco 2: de P2 (recorrido al revés, tangente −t2) hasta J. Los dos comparten tangente en J (G1).

Un arco desde P con tangente t hasta Q: sea c = Q − P y β el ángulo con signo de t a c. Entonces κ = 2·sin β / |c| y L = |c|·β / sin β (con β → 0: κ = 0, L = |c|).

## 4. Energía de flexión del conector

E(ψ1, ψ2) = κa²·La + κb²·Lb (conector superior; el inferior es su espejo).

- E ≥ 0, y E = 0 solo si los dos arcos son rectos, es decir t1 = t2 y paralelas a la cuerda.
- Eso ocurre exactamente con cos ψ2 = (r1 − r2)/d y ψ1 = π − ψ2: los puntos de contacto de la tangente común (existe si d ≥ |r1 − r2|). Ahí E = 0 y el conector es la recta de A.
- E escala como 1/longitud.

## 5. Afinidad a la tangente

Los radios libres tienen afinidad hacia la tangente del centro (el eje de su tapa): un término que penaliza ψ. Con r̄ = (r1 + r2)/2 y δ = d/(r1 + r2):

F(ψ1, ψ2) = r̄·E(ψ1, ψ2) + λ(δ)·(ψ1² + ψ2²),  λ(δ) = (2/π)·max(0, 1 − δ).

- λ0 = 2/π sale de exigir ψ = π/4 (la X) para dos círculos idénticos: con conectores que son arcos del propio círculo, E = (π − 2ψ)/r por conector y F(ψ) = (π − 2ψ) + 2λψ², con mínimo en ψ = 1/(2λ).
- Con d ≥ r1 + r2 (δ ≥ 1), λ = 0: se mantiene A exacta.
- r̄ vuelve F adimensional, y la solución no depende de la escala.

## 6. Problema y solución

(ψ1*, ψ2*) = argmin F sobre las parejas admisibles.

- Con δ ≥ 1: ψ2* = acos((r1 − r2)/d), ψ1* = π − ψ2* (sin minimizar).
- Con δ < 1: minimización 2D, suave. Valores de arranque: los ángulos de A si hay tangente común; la solución del caso vecino si se recorre d o r2/r1; si no, una rejilla 5×5.
- La solución depende solo de (r1, r2, d) y de la orientación; es cacheable por primitiva.

## 7. Propiedades

- Con tangente común y δ ≥ 1: silueta actual (A).
- Círculos idénticos (r1 = r2, d = 0): círculo exacto; los cuatro radios libres a 45° forman la X que reparte zonas.
- Intercambio (c1, r1) ↔ (c2, r2) con T → −T: ψ1 ↔ ψ2 (numéricamente exacto).
- Con d = 0: ψ1 = ψ2 y solo depende de r2/r1 (con 72/36, 26,5°; con r2/r1 → 1, 45°).
- Los empalmes son G1; la curvatura salta en las uniones (tapa–arco y J).

## 8. Evidencia numérica (λ0 = 2/π)

| r1 / r2 | d | ψ1* | ψ2* |
|---|---|---|---|
| 72 / 36 | 150 | 103,89° | 76,11° (A) |
| 72 / 36 | 108 (= r1 + r2) | 109,47° | 70,53° (A) |
| 72 / 36 | 100 | 106,78° | 65,26° |
| 72 / 36 | 80 | 99,09° | 51,69° |
| 72 / 36 | 60 | 78,07° | 37,46° |
| 72 / 36 | 45 | 58,03° | 31,40° |
| 72 / 36 | 36 | 49,56° | 29,18° |
| 72 / 36 | 20 | 38,15° | 26,70° |
| 72 / 36 | 4 | 28,76° | 26,17° |
| 72 / 36 | 0 | 26,48° | 26,48° |
| 4 / 1,5 | 2 | 45,69° | 24,38° |
| 1 / 0,9 | 0 | 42,66° | 42,66° |
| 1 / 0,99 | 0 | 44,97° | 44,97° |
| 60 / 60 | 0 | 45,00° | 45,00° |
| 60 / 60 | 50 | 59,59° | 59,59° |
| 60 / 60 | 100 | 80,64° | 80,64° |
| 60 / 60 | 120 (= 2r) | 90,00° | 90,00° (A) |

Continuidad (con 49 arranques en rejilla más el vecino en cada muestra):

- 72/36, d de 0 a 130 (261 puntos): salto máximo entre vecinos 0,78°, medio 0,26°.
- r2/r1 de 0,1 a 1 con d = 0,001 (181 puntos): máximo 0,22°, medio 0,17°.
- r2/r1 de 0,1 a 1 con d = 0,3 (181 puntos): máximo 0,27°.
- 60/60, d de 0 a 130 (261 puntos): máximo 0,24°.
- Intercambio de círculos: (38,148°, 26,701°) frente a (26,701°, 38,148°).
- Escala ×0,1: idéntica hasta unos 1e-5°.
- En ninguna de esas barridas apareció un segundo mínimo.

## 9. Decisiones y límites

1. La unicidad del mínimo no está demostrada; hay indicios numéricos.
2. λ0 = 2/π (la X) y el desvanecimiento lineal en d hasta d = r1 + r2 son elecciones de diseño; el umbral r1 + r2 es del autor.
3. Con r1 − r2 < d < r1 + r2 (solapados con tangente común), la recta de A se sustituye por un conector curvo; la tangente interior deja de ser el círculo grande.
4. Sin afinidad (λ = 0), los biarcos colapsan la tapa 2 en los anidados (ψ2 → 0): la afinidad es necesaria en todo el rango d < r1 + r2, no solo casi idéntico.
5. Descartadas: energía de flexión de toda la silueta (rompe A: 72/36 con d = 150 daba ψ = 37° / 27°), conector Hermite cúbico (la X con r1 = r2 sobresale ~6 % del círculo), elipses.
6. Fuera de alcance: ida y vuelta (centros y radios idénticos con tangentes opuestas), tangente y normal explícitas en la API.
7. Con ψ cerca de 0 o π el conector puede degenerar: el solver debe avisar si llega a un tope.
