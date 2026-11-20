#!/usr/bin/env python3
"""
Silueta de la primitiva "gota" para dos circulos solapados (d < r1+r2).

Modelo (ver gota_formulacion_biarcos.md):
  * dos tapas circulares (c1,r1) y (c2,r2) con semiangulos psi1, psi2 medidos desde la direccion hacia la que mira
    cada tapa (tapa 1 hacia -T, tapa 2 hacia +T); los radios libres que llegan a los extremos son perpendiculares
    a la silueta (G1);
  * un conector por lado entre los extremos P1 (tapa 1) y P2 (tapa 2), con las tangentes impuestas;
  * incognitas (psi1, psi2) = argmin  F = rm*E + lam*(psi1^2 + psi2^2)
        E   = integral de curvatura^2 por longitud de arco del conector
        rm  = (r1+r2)/2,   lam = (2/pi)*max(0, 1 - d/(r1+r2))   (afinidad a la tangente del centro; da la X en identicos)
  * con d >= r1+r2: envolvente convexa (dos arcos + tangentes), psi en forma cerrada.

Conectores:
  'elipse' : arco de elipse (conica racional cuadratica con peso w<1) de MINIMA EXCENTRICIDAD entre las tangentes.
             Es circulo exacto si hay circulo tangente en ambos extremos (r1 = r2). Si con unos psi no existe
             elipse (las tangentes no se cortan hacia delante) esos psi son inadmisibles: los puntos de union se
             mueven antes de recurrir a otro conector.
  'biarco' : biarco simetrico G1 (dos arcos de circunferencia).

Uso:   python3 gota_silueta.py          # autocomprobaciones + figura gota_silueta.png
       from gota_silueta import angulos, silueta
"""
import sys
import numpy as np
from scipy.optimize import minimize, minimize_scalar

LAMBDA0 = 2 / np.pi
PSI_MIN = 0.02
INF = 1e9
_GX, _GW = np.polynomial.legendre.leggauss(32)
_GU, _GWU = 0.5 * (_GX + 1), 0.5 * _GW


# ---------------------------------------------------------------- extremos y tangentes
def extremos(psi, r1, r2, d):
    """P1, t1 (tapa 1) y P2, t2 (tapa 2) del lado +N, en el marco c1 = origen, T = (1,0). Tangentes de recorrido."""
    p1, p2 = psi
    P1 = np.array([-r1 * np.cos(p1), r1 * np.sin(p1)])
    P2 = np.array([d + r2 * np.cos(p2), r2 * np.sin(p2)])
    t1 = np.array([np.sin(p1), np.cos(p1)])
    t2 = np.array([np.sin(p2), -np.cos(p2)])
    return P1, t1, P2, t2


# ---------------------------------------------------------------- conector 1: elipse de minima excentricidad
def _cruce(P1, t1, P2, t2):
    """Interseccion X de las tangentes si es 'hacia delante' (X = P1 + s1 t1 = P2 - s2 t2, s1,s2 > 0)."""
    M = np.column_stack([t1, t2])
    if abs(np.linalg.det(M)) < 1e-12:
        return None
    s1, s2 = np.linalg.solve(M, P2 - P1)
    if s1 <= 0 or s2 <= 0:
        return None
    return P1 + s1 * t1


def elipse(P1, t1, P2, t2):
    """(w, e, X) de la elipse de minima excentricidad entre las tangentes, o None si no existe.

    La conica es  beta^2 = 4 w^2 alfa gamma  en coordenadas baricentricas de (P1, X, P2). Su parte cuadratica es
    S(s) = A0 + s*A1 con s = w^2 (lineal en s), y la excentricidad solo depende de la razon entre la parte
    'desviadora' y la traza de S: g(s) = dev^2 / (tr/2)^2 = (a s^2 + b s + c)/(m0 + m1 s)^2.
    g'(s) = 0 es LINEAL en s: hay forma cerrada. Se acota a w en (0, 1) (en 1 la elipse es una parabola)."""
    X = _cruce(P1, t1, P2, t2)
    if X is None:
        return None
    M = np.array([[P1[0], X[0], P2[0]], [P1[1], X[1], P2[1]], [1, 1, 1]])
    Mi = np.linalg.inv(M)
    A0 = (Mi.T @ np.diag([0.0, 1.0, 0.0]) @ Mi)[:2, :2]
    A1 = (Mi.T @ np.array([[0, 0, -2.0], [0, 0, 0], [-2.0, 0, 0]]) @ Mi)[:2, :2]
    p0, p1 = (A0[0, 0] - A0[1, 1]) / 2, (A1[0, 0] - A1[1, 1]) / 2
    q0, q1 = A0[0, 1], A1[0, 1]
    m0, m1 = (A0[0, 0] + A0[1, 1]) / 2, (A1[0, 0] + A1[1, 1]) / 2
    al, be, ga = p1 * p1 + q1 * q1, 2 * (p0 * p1 + q0 * q1), p0 * p0 + q0 * q0
    den = 2 * al * m0 - be * m1
    if abs(den) < 1e-300:
        return None
    s = (2 * m1 * ga - be * m0) / den
    s = min(max(s, 1e-4), 0.9998)
    g = (al * s * s + be * s + ga) / (m0 + m1 * s) ** 2
    rg = np.sqrt(max(g, 0.0))
    e = np.sqrt(2 * rg / (1 + rg))
    return float(np.sqrt(s)), float(e), X


def energia_conica(P1, X, P2, w):
    """Integral de kappa^2 ds de la conica racional (Gauss-Legendre, derivadas analiticas)."""
    u = _GU[:, None]
    a, b, c = (1 - u) ** 2, 2 * w * u * (1 - u), u ** 2
    D = a + b + c
    N = a * P1 + b * X + c * P2
    da, db, dc = -2 * (1 - u), 2 * w * (1 - 2 * u), 2 * u
    dD = da + db + dc
    dN = da * P1 + db * X + dc * P2
    dda, ddb, ddc = 2 + 0 * u, -4 * w + 0 * u, 2 + 0 * u
    ddD = dda + ddb + ddc
    ddN = dda * P1 + ddb * X + ddc * P2
    C = N / D
    C1 = (dN - dD * C) / D
    C2 = (ddN - 2 * dD * C1 - ddD * C) / D
    cruz = C1[:, 0] * C2[:, 1] - C1[:, 1] * C2[:, 0]
    v = np.hypot(C1[:, 0], C1[:, 1])
    return float(np.sum(_GWU * cruz ** 2 / v ** 5))


def energia_elipse(psi, r1, r2, d):
    P1, t1, P2, t2 = extremos(psi, r1, r2, d)
    if P2[0] - P1[0] <= 1e-9 * (r1 + r2):
        return INF
    el = elipse(P1, t1, P2, t2)
    if el is None:
        return INF
    return energia_conica(P1, el[2], P2, el[0])


# ---------------------------------------------------------------- conector 2: biarco simetrico
def _arco(P, t, Q):
    c = Q - P
    L0 = np.hypot(*c)
    if L0 < 1e-15:
        return 0.0, 0.0
    beta = np.arctan2(t[0] * c[1] - t[1] * c[0], t @ c)
    if abs(beta) < 1e-9:
        return 0.0, L0
    if abs(beta) > np.pi - 1e-6:
        return None
    L = L0 * abs(beta) / np.sin(abs(beta))
    return 2 * np.sin(beta) / L0, L


def biarco(P1, t1, P2, t2):
    v = P2 - P1
    vv = v @ v
    if vv < 1e-18:
        return None
    t = t1 + t2
    vt = v @ t
    a = 4 - t @ t
    if abs(a) < 1e-12:
        if abs(vt) < 1e-9 * np.sqrt(vv):
            return None
        s = vv / (2 * vt)
    else:
        disc = vt * vt + a * vv
        if disc < 0:
            return None
        s = (-vt + np.sqrt(disc)) / a
    if not s > 0 or not np.isfinite(s):
        return None
    J = ((P1 + s * t1) + (P2 - s * t2)) / 2
    a1, a2 = _arco(P1, t1, J), _arco(P2, -t2, J)
    if a1 is None or a2 is None:
        return None
    return J, a1, a2


def energia_biarco(psi, r1, r2, d):
    P1, t1, P2, t2 = extremos(psi, r1, r2, d)
    if P2[0] - P1[0] <= 1e-9 * (r1 + r2):
        return INF
    b = biarco(P1, t1, P2, t2)
    if b is None:
        return INF
    _, (k1, l1), (k2, l2) = b
    return k1 * k1 * l1 + k2 * k2 * l2


# ---------------------------------------------------------------- los angulos
def angulos(r1, r2, d, conector="elipse", n=6):
    """(psi1, psi2) en radianes. conector: 'elipse' (con respaldo de biarco si no hay ninguna pareja admisible) o 'biarco'."""
    if d >= r1 + r2:
        th = np.arccos((r1 - r2) / d)
        return np.pi - th, th
    lam = LAMBDA0 * (1 - d / (r1 + r2))
    rm = (r1 + r2) / 2
    for energia in ([energia_elipse, energia_biarco] if conector == "elipse" else [energia_biarco]):
        def F(psi):
            if min(psi) < PSI_MIN or max(psi) > np.pi - PSI_MIN:
                return INF + 1
            e = energia(psi, r1, r2, d)
            return INF if e >= INF else rm * e + lam * (psi[0] ** 2 + psi[1] ** 2)
        sem = [(a, b) for a in np.linspace(0.15, np.pi - 0.15, n) for b in np.linspace(0.15, np.pi - 0.15, n)]
        sem.append((np.pi / 4, np.pi / 4))
        k = (r1 - r2) / d if d > 0 else 2
        if abs(k) < 1:
            th = np.arccos(k)
            sem.append((np.pi - th, th))
        cand = sorted((F(s), s) for s in sem)
        mejor = None
        for f0, s in cand[:4]:
            if f0 >= INF:
                break
            r = minimize(F, s, method="Nelder-Mead", options=dict(xatol=1e-9, fatol=1e-13, maxiter=500))
            if mejor is None or r.fun < mejor.fun:
                mejor = r
        if mejor is not None:
            return float(mejor.x[0]), float(mejor.x[1])
    raise RuntimeError("sin solucion admisible")


# ---------------------------------------------------------------- la silueta
def silueta(r1, r2, d, conector="elipse", n=200):
    """Contorno cerrado (N x 2) en el marco c1 = origen, T = (1,0). Devuelve (puntos, (psi1, psi2), conector_usado)."""
    psi = angulos(r1, r2, d, conector)
    p1, p2 = psi
    P1, t1, P2, t2 = extremos(psi, r1, r2, d)
    usado = "recta"
    if d >= r1 + r2:
        sup = np.array([P1, P2])
    else:
        el = elipse(P1, t1, P2, t2) if conector == "elipse" else None
        if el is not None:
            w, _, X = el
            u = np.linspace(0, 1, n)[:, None]
            a, b, c = (1 - u) ** 2, 2 * w * u * (1 - u), u ** 2
            sup = (a * P1 + b * X + c * P2) / (a + b + c)
            usado = "elipse"
        else:
            J, (k1, l1), (k2, l2) = biarco(P1, t1, P2, t2)
            def traza(P, t, k, l, m):
                s = np.linspace(0, l, m)
                if abs(k) < 1e-12:
                    return P + np.outer(s, t)
                nn = np.array([-t[1], t[0]])
                return P + np.outer(np.sin(k * s) / k, t) + np.outer((1 - np.cos(k * s)) / k, nn)
            sup = np.vstack([traza(P1, t1, k1, l1, n // 2), traza(P2, -t2, k2, l2, n // 2)[::-1]])
            usado = "biarco"
    cap2 = np.c_[d + r2 * np.cos(np.linspace(p2, -p2, n)), r2 * np.sin(np.linspace(p2, -p2, n))]
    cap1 = np.c_[r1 * np.cos(np.linspace(np.pi - p1, np.pi + p1, n)), r1 * np.sin(np.linspace(np.pi - p1, np.pi + p1, n))][::-1]
    inf = (sup * [1, -1])[::-1]
    return np.vstack([sup, cap2, inf, cap1]), psi, usado


# ---------------------------------------------------------------- autocomprobaciones y figura
def _ok(nombre, cond, det=""):
    print(("  ok   " if cond else "  FALLA ") + nombre + (f"  [{det}]" if det else ""))
    return bool(cond)


def pruebas(conector):
    print(f"== comprobaciones, conector = {conector} ==")
    deg = np.degrees
    ok = True
    p = angulos(60, 60, 0.001, conector)
    ok &= _ok("identicos d=0: psi = 45 grados (la X)", abs(deg(p[0]) - 45) < 0.05 and abs(deg(p[1]) - 45) < 0.05, f"{deg(p[0]):.3f}, {deg(p[1]):.3f}")
    s, _, _ = silueta(60, 60, 0.001, conector)
    rad = np.hypot(s[:, 0] - 0.0005, s[:, 1])
    ok &= _ok("identicos d=0: la silueta es el circulo", abs(rad.max() - 60) < 0.05 and abs(rad.min() - 60) < 0.05,
              f"radio entre {rad.min():.3f} y {rad.max():.3f}")
    a, b = angulos(72, 36, 30, conector), angulos(36, 72, 30, conector)
    ok &= _ok("simetria: intercambiar circulos intercambia psi", abs(a[0] - b[1]) < 1e-5 and abs(a[1] - b[0]) < 1e-5, f"{deg(a[0]):.4f},{deg(a[1]):.4f} / {deg(b[0]):.4f},{deg(b[1]):.4f}")
    a, b = angulos(72, 36, 30, conector), angulos(7.2, 3.6, 3.0, conector)
    ok &= _ok("escala: x0,1 da los mismos psi", abs(a[0] - b[0]) < 1e-5 and abs(a[1] - b[1]) < 1e-5)
    th = np.arccos((72 - 36) / 150.0)
    a = angulos(72, 36, 150, conector)
    ok &= _ok("d >= r1+r2: angulos de la tangente comun", abs(a[1] - th) < 1e-12)
    # continuidad
    for nombre, f, xs in [("72/36, d de 0,001 a 130", lambda x: angulos(72, 36, x, conector), np.linspace(0.001, 130, 261)),
                          ("r2/r1 de 0,1 a 1, d=0,001", lambda x: angulos(1, x, 0.001, conector), np.linspace(0.1, 1, 91)),
                          ("60/60, d de 0,001 a 130", lambda x: angulos(60, 60, x, conector), np.linspace(0.001, 130, 261))]:
        v = np.array([f(x) for x in xs])
        salto = np.degrees(np.abs(np.diff(v, axis=0)).max())
        ok &= _ok("continuidad: " + nombre, salto < 1.5, f"salto maximo entre vecinos {salto:.2f} grados")
    # empalme con la envolvente
    a, b = angulos(72, 36, 107.9, conector), angulos(72, 36, 108.1, conector)
    ok &= _ok("continuidad en d = r1+r2", np.degrees(max(abs(a[0] - b[0]), abs(a[1] - b[1]))) < 1.0, f"{deg(a[0]):.2f},{deg(a[1]):.2f} / {deg(b[0]):.2f},{deg(b[1]):.2f}")
    return ok


def figura(conector, fichero):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    casos = [(60, 60, 0.001), (60, 60, 50), (60, 60, 100), (72, 36, 0.001), (72, 36, 20), (72, 36, 36),
             (72, 36, 60), (72, 36, 80), (72, 36, 100), (4, 1.5, 2), (36, 72, 30), (1, 0.9, 0.001)]
    fig, ax = plt.subplots(3, 4, figsize=(18, 12))
    for a, (r1, r2, d) in zip(ax.ravel(), casos):
        s, psi, usado = silueta(r1, r2, d, conector)
        a.set_aspect("equal"); a.set_xticks([]); a.set_yticks([])
        th = np.linspace(0, 2 * np.pi, 200)
        for c, r in ((0, r1), (d, r2)):
            a.plot(c + r * np.cos(th), r * np.sin(th), color="#ccc", lw=.8)
        a.plot(s[:, 0], s[:, 1], "k", lw=2)
        a.set_title(f"{r1}/{r2} d={d:g}  psi=({np.degrees(psi[0]):.1f}, {np.degrees(psi[1]):.1f})  [{usado}]", fontsize=9)
    fig.tight_layout(); fig.savefig(fichero, dpi=60)


if __name__ == "__main__":
    modo = sys.argv[1] if len(sys.argv) > 1 else "elipse"
    bien = pruebas(modo)
    figura(modo, f"gota_silueta_{modo}.png")
    print("TODO OK" if bien else "HAY FALLOS")
