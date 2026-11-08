# Primitiva Gota: silueta por tapas y conector elíptico (biarcos como respaldo) con afinidad a la tangente

Estado (2026-11-07): propuesta de formulación, implementada en Java (`Silueta`, `SiluetaCurva`) y en Python (`gota_silueta.py`). **El conector por defecto es el arco de elipse de mínima excentricidad (sección 10)**; el biarco simétrico (secciones 3 a 9, formulación original) queda como respaldo y como variante pedida expresamente (`Silueta.Conector.BIARCO`). Sustituye, para círculos, a «unión de círculos» (A) cuando los círculos se solapan o se absorben (d < r1 + r2); con d ≥ r1 + r2 se mantiene la envolvente convexa (conector recto). Las elipses de la envolvente de la gota (polinomio de grado 12) quedan descartadas de la primitiva; no tienen que ver con los arcos elípticos del conector, que resuelven las superposiciones conservando la estructura de cuatro tapas y cinco ejes.

Guía de lectura: 1 y 2 (marco e incógnitas) valen para cualquier conector; 3 a 9 detallan el biarco y la energía/afinidad (la afinidad y el problema de la sección 6 son comunes); 10 es el conector por defecto; 11 a 12.3 tratan el camino curvo entre centros (doblado, relajo, regla del trazo).

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

Admisible: (P2 − P1)·T > 0 y el conector existe: con el biarco de la sección 3, |β| < π en ambos arcos; con la elipse de la sección 10 (por defecto), las tangentes de recorrido se cortan hacia delante.

## 3. Conector de respaldo: biarco simétrico

(Formulación original. Por defecto se usa el conector elíptico de la sección 10; el biarco se usa si no hay ninguna pareja admisible con elipse o si se pide con `Conector.BIARCO`.)

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

(Valores con el conector biarco; con el elíptico, por defecto, véase la referencia de la sección 10.)

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
5. Descartadas: energía de flexión de toda la silueta (rompe A: 72/36 con d = 150 daba ψ = 37° / 27°), conector Hermite cúbico (la X con r1 = r2 sobresale ~6 % del círculo), elipses de la envolvente de la gota (las del conector, en cambio, son la opción por defecto: sección 10).
6. Fuera de alcance: ida y vuelta (centros y radios idénticos con tangentes opuestas), tangente y normal explícitas en la API.
7. Con ψ cerca de 0 o π el conector puede degenerar: el solver debe avisar si llega a un tope.

## 10. Conector elíptico (por defecto desde el 2026-11-06)

Implementado en `Silueta` (`Conector {ELIPSE, BIARCO, RECTA}`; `ELIPSE` es el valor por defecto, `RECTA` se usa con δ ≥ 1 y `BIARCO` es el respaldo) y en `gota_silueta.py` (`python3 gota_silueta.py elipse|biarco`). Mismo problema (sección 6) con otro conector: arco de elipse de **mínima excentricidad** tangente a los dos radios libres.

- Conector: cónica racional cuadrática (1, w, 1) sobre (P1, X, P2), con X el cruce de las tangentes de recorrido (debe ser «hacia delante»: X = P1 + s1·t1 = P2 − s2·t2 con s1, s2 > 0) y w < 1 (elipse; w = 1 parábola). Con las dos tangentes impuestas queda un solo grado de libertad, w.
- Elección de w: mínima excentricidad. La parte cuadrática de la cónica es A0 + s·A1 con s = w²; la excentricidad depende solo de g(s) = (a s² + b s + c)/(m0 + m1 s)² y g'(s) = 0 es lineal en s: hay forma cerrada, sin búsqueda. Se acota a w < 1.
- Es círculo exacto cuando existe un círculo tangente en los dos extremos (extremos y tangentes simétricos, p. ej. r1 = r2): con círculos idénticos sale w = cos 45°, e = 0 y la X a 45°, sin regla añadida.
- Energía: E = ∫ κ² ds de la cónica (Gauss-Legendre de 32 nodos con derivadas analíticas); F y λ como en la sección 5.
- Los puntos de unión se mueven antes de recurrir a otro conector: sin elipse (tangentes que no se cortan hacia delante) la pareja (ψ1, ψ2) es inadmisible y el solver busca otra. El biarco es solo respaldo si no hay ninguna pareja admisible (no ocurrió en los barridos) o si se pide.
- Dibujo: la cónica es la imagen afín de un arco de circunferencia unidad de semiángulo acos(w); se dibuja con cúbicas de ≤45° y se transforma.
- Con d ≥ r1 + r2: recta (envolvente convexa), como antes.

Referencia (r1/r2, d → ψ1, ψ2 en grados; entre paréntesis, biarco): 72/36 d=100 → 106,70 / 65,33 (106,78 / 65,26); d=80 → 98,46 / 52,04 (99,09 / 51,69); d=60 → 77,17 / 38,41 (78,07 / 37,46); d=36 → 49,99 / 30,25 (49,56 / 29,18); d=20 → 38,79 / 27,63 (38,15 / 26,70); d=0 → 27,19 / 27,19 (26,48); 4/1,5 d=2 → 46,26 / 25,26 (45,69 / 24,38); 60/60: 45,00 (d=0), 59,59 (d=50), 80,64 (d=100). Las siluetas elíptica y de biarco difieren menos del 3 % de r medio.

Evidencia numérica (Python y Java): continuidad con saltos máximos de 0,74° (72/36, d de 0 a 130), 0,43° (r2/r1 de 0,1 a 1 con d ≈ 0) y 0,24° (60/60); simetría y escala exactas; continuo en d = r1 + r2. Unicidad solo numérica, como antes. Sin comprobar: la curvatura en los empalmes (G2 no se impone) y las siluetas con tangentes en S.

## 11. Camino curvo entre centros (estudio en Python, 2026-11-06; implementado en Java en la sección 12)

Montaje: cada lado se resuelve por separado con las tangentes locales T1 = c'(0) y T2 = c'(1) y sus normales; ψ se mide desde −T1 y +T2; dominio δ = L/(r1+r2) con L la longitud de arco; el camino es una cúbica con giro total θ (hacia +N). Conector elíptico (sección 10). Scripts: estudio_bezier.py, salto_envolvente.py, figura_bezier.py.

- Equilibrio: con radios iguales, ψ del lado interior baja y el del exterior sube suavemente con θ (60/60, δ = 0,9: 84° → 7° interior con θ = 165°). Con radios distintos la tapa pequeña interior colapsa antes: ψ → 0 en θ ≈ 60° (δ = 0,3), 90° (δ = 0,6), 120° (δ = 0,9) con 72/36.
- ψ → 0 es un límite legítimo (la tapa queda en una punta), pero el solver se queda en el tope y más allá aparecen saltos: soluciones no monótonas, ψ ≈ 179° y contornos inválidos desde θ ≈ 135-150°; con θ = 180° no hay solución admisible.
- El conector elíptico aguanta algo más que el biarco: con δ ≤ 0,6 el biarco colapsa los dos lados a θ ≈ 135°, la elipse conserva el lado exterior.
- Problema principal: la silueta solo ve los dos círculos de los extremos y las tangentes locales; el camino interior no cuenta. Con δ → 1⁻ tiende a la envolvente de los dos círculos extremos, no a la unión de círculos del camino. Diferencia de área con la unión de círculos (r lineal): con δ = 0,99 y θ = 60°, 8 % (72/36) y 10 % (60/60); con θ = 90°, 11,5 % y 14,5 %; sin giro, 0,3 % y 0,8 %. Con un giro apreciable el salto en δ = 1 no es continuo.
- Idea sin probar: usar el grado de libertad w de la cónica (en lugar de la mínima excentricidad) para que el conector sea tangente al círculo medio (c(tm), r(tm)), con lo que los puntos medios de los ejes intervienen en la silueta.

### 11.1 Dos intentos para el salto en δ = 1 (2026-11-06; scripts medio.py y doblado.py)

Métrica: fracción del área en que la silueta difiere de la unión de círculos del camino (radio lineal), con giros de 0°, 30°, 60° y 90°.

1. **Elipse por el punto medio.** Se fija w para que el conector pase por M = c(tm) + rm·n, el contacto de la envolvente con el círculo medio; con coordenadas baricéntricas (a, b, g) de M en el triángulo (P1, X, P2) sale w = b/(2·√(a·g)). Con círculos idénticos da el círculo. Pero en los solapados rectos M queda fuera de la silueta y no hay solución (72/36 con d = 0, 20 y 4/1,5 con d = 2), o w se pega a 1 y los ψ se deforman (72/36 con d = 60: 65°/51° en lugar de 77°/38°). Mezclando w con δ^4 y penalizando que M no esté en el triángulo, los casos rectos se conservan (menos de 0,7° salvo d = 100), pero con δ = 0,99 la diferencia baja solo un 30-40 %: 72/36 pasa de 4,1/8,0/11,5 % a 2,6/5,1/7,8 % y 60/60 de 5,3/10,0/14,5 % a 3,3/6,4/9,7 %. No cierra el salto.
2. **Silueta recta doblada.** Se resuelve la silueta recta con d := L (longitud de arco) y se aplica (x, y) → c(x) + y·N(x) (fuera de [0, L] se usa la tangente del extremo). Diferencia con la unión de círculos, con giros de 0°/30°/60°/90°/120°:
   - 72/36, δ = 0,99: 0,4 / 1,0 / 1,9 / 2,8 / 3,2 %.
   - 72/36, δ = 0,9: 1,5 / 1,8 / 3,0 / 4,5 / 5,3 %.
   - 60/60, δ = 0,99: 0,9 / 0,7 / 0,7 / 0,8 / 2,2 %.
   - 60/60, δ = 0,9: 2,3 / 2,2 / 2,2 / 2,3 / 4,0 %.
   Con δ = 0,5 la diferencia crece con el giro (72/36: 1,6 % a 13,5 %), pero ahí la silueta nueva difiere de la unión por diseño.
   Límite: en el lado interior el contorno se pliega cuando la semianchura supera el radio de curvatura del camino (marcado en el barrido: 60/60 con δ = 0,5 desde 60°, con δ = 0,9 desde 90°; 72/36 con δ = 0,9 a 120°).

## 12. Implementación en Java del camino curvo (2026-11-07)

Clase `SiluetaCurva` (junto a `Silueta`, sin tocar `Gota`): resuelve la silueta recta con d := L (longitud de arco de la cúbica) y la dobla con (x, y) → c(x) + y·N(x); fuera de [0, L] usa la tangente del extremo. Cada tramo de la silueta recta se parte en trozos y se lleva como cúbica de Hermite con el jacobiano T·(1 + k·y)·dx + N·dy, que da G1 con el doblado exacto en los extremos de cada trozo. `Silueta` expone ahora `formaLocal()` y `extremosLocales()`.

- API: `SiluetaCurva.de(p0, h1, h2, p3, r1, r2[, conector])`, `forma()`, `extremos()` (P1..P4 doblados), `longitud()`, `recta()`, `pliegue()` (mínimo de 1 + k·y sobre el contorno, con x en (0, L)) y `plegada()` (pliegue ≤ 0).
- Pruebas (`SiluetaCurvaPruebas`; 26 en esta fase, 53 con las de 12.1 y 12.2): el camino recto coincide con `Silueta`, sin esquinas y simple con giros moderados, diferencia con la unión de círculos en δ = 0,99 (72/36: 0,15 / 0,8 / 1,7 / 2,5 % con giros de 0° / 30° / 60° / 90°; 60/60: 0,45 / 0,17 / 0,20 / 0,19 %), aviso de pliegue, escala y orientación, camino de longitud cero. `SiluetaPruebas` (132) y `SiluetaCurvaPruebas` (53) pasan.
- Los extremos P1 y P2 se desplazan hasta un 2,5 % del radio respecto al círculo de la tapa (ψ > 90° los lleva dentro de [0, L]); las tapas dejan de ser arcos exactos en esa zona.
- Se pliega el lado interior cuando 1 + k·y ≤ 0: 72/36 con δ = 0,6 a partir de 60°, con δ = 0,9 a 90°; 60/60 con δ = 0,9 a 90°. Con pliegue pequeño pero positivo (≈ 0,2) el contorno ya muestra una muesca en el lado interior.
- Decisión pendiente: criterio de cambio a la envolvente (¿pliegue ≤ un umbral positivo?), pertenencia y coordenadas de los cinco ejes sobre el contorno doblado, rm/tm no circulares.

### 12.1 Experimento: relajar los arcos elípticos con una spline (2026-11-07)

`SiluetaCurva.Relajo { NINGUNO, EXTERIOR, INTERIOR, AMBOS }` (exterior e interior respecto al giro total del camino, con signo). Un conector relajado deja de ser el arco elíptico doblado y es una spline en el plano, en dos cúbicas de Hermite: por P (extremo de la tapa 1), el punto medio M y el otro extremo; en los extremos la tangente de la silueta doblada y en M la de la envolvente. M es el contacto de la envolvente con el círculo medio, c(L/2) + rm·(−r'·T ± √(1−r'²)·N), mezclado con el punto de la silueta doblada con una función suave del giro total (|giro| / 20°), de modo que con el camino recto se conserva el arco elíptico (área a menos del 0,01 %). Como la spline no se dobla, no se pliega.

Resultados (demo `SiluetaRelajoDemo`, imagen silueta_relajo.png; pruebas (6) de `SiluetaCurvaPruebas`):
- Relajar el exterior (2) hace que el lado exterior siga la unión de círculos; con el interior sin relajar se conserva la torsión (la cúspide del pliegue en 72/36 con δ = 0,6 y 60°, el cruce en 60/60 con 90°).
- Relajar el interior (3) elimina el pliegue: queda una muesca redondeada que sigue la envolvente.
- Relajar ambos (4): diferencia de área con la unión de círculos de 2,8 % → 1,0 % (72/36, δ = 0,9, 60°) y de 1,7 % → 0,3 % (60/60, δ = 0,9, 90°).
- Con el exterior relajado y el interior sin relajar el contorno tiene esquinas donde se pliega (22° y 40° de giro máximo entre aristas), como cabe esperar de la cúspide.

### 12.2 Relajo LOCAL y caminos complejos (2026-11-07)

Propuesta de Samuel: en lugar de pasar a la envolvente al cruzar d = r1 + r2, partir siempre de la silueta de cuatro tapas como si fuera recta (con d ≥ r1 + r2 es la envolvente convexa), aplicarle la torsión de la spline y relajar la parte tensa exterior a la curva o respetar los pliegues internos.

`SiluetaCurva.Metodo.LOCAL` decide exterior/interior punto a punto, no con el giro total: se muestrea el conector recto, se dobla (B) y se mezcla con el contacto exacto de la envolvente del círculo de la misma x, E = c(x) + r(x)·(−r'·T + lado·√(1−r'²)·N), con Q = B + w·(E − B) y w = suave(lado·k·r̄/κ0) para el exterior y suave(−lado·k·r̄/κ0) para el interior (κ0 = 0,25; w se anula en los extremos del conector y fuera de [0, L]). Con el camino recto w = 0 y se conserva el arco elíptico (área a menos del 0,03 %).

Caminos que fuerzan el pliegue (demo `SiluetaComplejaDemo`, silueta_compleja.png): S, horquilla (170°), lazo (298°), caracol (188°), con δ de 0,9 a 1,3. Diferencia de área con la unión de círculos con el relleno de regla no cero (NINGUNO / EXTERIOR / INTERIOR / AMBOS): S 1,1 / 0,9 / 0,9 / 0,7 %; horquilla 2,7 / 0,2 / 2,7 / 0,1 %; lazo 0,6 % en las cuatro; caracol 2,0 / 3,2 / 1,3 / 2,5 %.
- Con la regla de relleno no cero, las zonas plegadas o solapadas quedan dentro de la unión; lo que se ve son los trazos interiores del contorno (rizos y cruces), que son el efecto de la torsión.
- El exterior relajado sigue la unión de círculos (horquilla: 2,7 → 0,2 %). En el caracol, con δ = 1,2 y radios 70/25, el exterior queda algo por dentro de la unión (3,2 %).
- Las tapas, que fuera de [0, L] se mueven con la tangente del extremo, se cruzan entre sí cuando los extremos del camino se aproximan (horquilla, lazo): los lóbulos de las tapas se solapan, y el relajo no los toca.
- Con el lazo (giro total mayor de 180° y camino que se cruza a sí mismo) el contorno es caótico, aunque el relleno sigue siendo cercano a la unión.

### 12.3 Regla del trazo aplicada a la silueta (2026-11-07)

`SiluetaCurva.trazo()` devuelve `Trazo(visible, oculto)`: el contorno como si se dibujara a lo largo del camino, de modo que los círculos posteriores tapan los trazos anteriores (regla del trazo: gana el parámetro mayor; las tapas son la prolongación del camino).

- Cada cúbica del contorno lleva la longitud de arco s del camino a la que pertenece: x acotada a [0, L] en los conectores (los relajados, por la x de cada muestra), s = L en la tapa 2 (la última) y s = 0 en la tapa 1 (la primera).
- Un punto con etiqueta s queda oculto si está dentro (más de ε = 0,2 % de r1 + r2) de algún círculo (c(t'), r(t')) con t' > s. Se evalúa en 12 puntos por cúbica y cada tramo toma el estado más oculto de sus dos extremos. Salida: caminos abiertos (negro = visible, gris punteado = tapado en el demo `SiluetaTrazoDemo`, silueta_trazo.png).
- Resultado en los caminos de 12.2 (S, horquilla, lazo, caracol; cuatro relajos): el negro deja de cruzar el interior de la unión; los tramos plegados de los conectores interiores quedan tapados y en el exterior casi todo el contorno es visible. Con relajo interior o de ambos desaparece el pico interior del lazo y el caracol.
- Límites: ε está elegido a ojo (casi tangentes pueden cambiar de estado); se oculta un punto dentro de cualquier círculo posterior aunque venga de otro tramo del mismo contorno; sin pruebas automáticas todavía, solo el demo; la relación con el relleno (par-impar o no cero) está sin decidir.

## 13. Pertenencia: la silueta es la máscara (2026-11-08)

La silueta decide si un punto es de la gota (como dibujar la gota de un color sólido); a qué zona de las coordenadas intrínsecas pertenece un punto es una etapa posterior, también parte de la definición.

### 13.1 Camino recto: cónicas

En el marco local (c1 en el origen, T = (1, 0)) el contorno es simétrico respecto al eje y, en el semiplano superior, una función de x: h(x) es el círculo de la tapa 1 hasta P1, el conector (la recta P1-P3 o el arco de elipse) hasta P3 y el círculo de la tapa 2. El arco de elipse es la cónica racional (1, w, 1) sobre P1, X, P3 y es monótono en x (X queda entre P1 y P3 en x), así que x(u) = x es una ecuación de segundo grado en u y h(x) sale sin buscar raíces: coste constante. Dentro si |y| <= h(x). `Silueta.contiene` / `contieneLocal`; con el respaldo BIARCO se usa el contorno.

Medido (408 configuraciones: 8 a mano y 400 con radios de 0,4 a 3 y d hasta 1,3·(r1 + r2); 303 con elipse, 105 con recta; ninguna con biarco): los puntos que discrepan del relleno de `forma()` están como mucho a 5,8·10⁻⁵ radios del borde. `Silueta.distancia` (signo = pertenencia) es exacta en las tapas y de primer orden en el conector; a menos de un píxel del borde el peor error fue 0,7 píxeles, en un caso extremo (r1 ≈ 0,4, r2 ≈ 4,7, d = 0,2); no se ha medido el percentil, de modo que la cobertura necesita una iteración de proyección antes de fiarse.

### 13.2 Camino curvo: unión de pies de perpendicular

El doblado es Φ(x, y) = c(x) + y·N(x) (con las prolongaciones rectas de las tapas fuera de [0, L]). Un punto p es imagen de (x, y) si x es un pie de perpendicular, (p - c(x))·T(x) = 0, e y = (p - c(x))·N(x). `SiluetaCurva.contiene`: dentro si algún pie cae en la silueta recta (más las dos prolongaciones). Sin pliegue Φ es inyectiva en la región y coincide con el relleno del contorno doblado (peor 2,7·10⁻⁵ radios en 35 casos, frente al contorno doblado punto a punto).

Con pliegue (1 + k·y <= 0 en algún punto del contorno) hay pies de orientación invertida. Dos reglas posibles:

- **Unión de pies** (la elegida): dentro si hay algún pie. Es la imagen del doblado: lo que la forma cubre.
- **Winding**: el winding de p es la suma, sobre sus pies dentro de la región, del signo de 1 + k·y; dentro si no es cero (lo que hace `Path2D.contains`). Una capa invertida y una normal se cancelan, y se abren huecos en la forma: 25 de 37 casos plegados de las pruebas.

Elegida la unión: contiene todo lo que cubre el contorno doblado y puede cubrir más. `SiluetaCurva.pies(x, y, signos)` da el número de pies y el winding con la misma pasada. Consecuencia pendiente: en la zona plegada el contorno doblado (Path2D) tiene un lazo que queda dentro de la unión, así que el borde visible de la máscara no es ese contorno.

Límites: el barrido de pies es lineal en la tabla del camino (2049 muestras); `pliegue()` solo se ha corregido para el conector recto (PR #12); no se ha probado con relajo ni con caminos que se cruzan globalmente.
