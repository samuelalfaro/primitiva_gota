"""Mezcla de dos renders (PLANO | TRAZO) del trébol con smoothstep según la distancia a M. Centro y radios estimados a ojo."""
from PIL import Image
import numpy as np
im = np.asarray(Image.open('trebol.png').convert('RGB')).astype(float)
W = im.shape[1] // 2
P, T = im[:, :W], im[:, W:]
cx, cy = 350, 430
yy, xx = np.mgrid[0:P.shape[0], 0:P.shape[1]]
d = np.hypot(xx - cx, yy - cy)
for r0, r1 in [(15, 45), (20, 70), (30, 100)]:
    u = np.clip((d - r0) / (r1 - r0), 0, 1); w = (u * u * (3 - 2 * u))[..., None]  # 0 = PLANO, 1 = TRAZO
    Image.fromarray((P * (1 - w) + T * w).astype('uint8')).save(f'mezcla_{r0}_{r1}.png')
