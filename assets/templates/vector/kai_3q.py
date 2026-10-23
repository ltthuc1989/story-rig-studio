"""Kai, three-quarter view, vector drawn for the rig (assets/templates/vector/README.md). Writes shapes.csv, hoses.csv.
Coordinates: ref px of the 3q skeleton (bones.csv). Style: flat colours, one darker shade shape per form (far side)."""
import math, sys
OUT = sys.argv[1]

# Palette (the approved design's colours) and shades (about 15% darker).
SKIN, SKIN_SH, SKIN_FAR = '#F7B384', '#E39A6E', '#EBA577'
EAR_IN = '#E08C68'
RED, RED_SH, RED_DK = '#D73433', '#B82A2B', '#A41525'
HAIR, HAIR_BACK = '#241826', '#1A111C'
JEAN, JEAN_SH, JEAN_LINE, CUFF, CUFF_SH = '#42719A', '#36618A', '#2F5578', '#6A97BA', '#5A86A8'
BAG, BAG_SH, STRAP, STRAP_SH = '#D18A2A', '#B0711F', '#E09A35', '#C98424'
SHOE, SHOE_SH, SHOE_STRAP, SOLE = '#E8742A', '#C95F1F', '#F59A55', '#FFFFFF'
SOCK, SOCK_SH, STRIPE = '#FFF6E8', '#EDE3D3', '#D43837'
WHITE, IRIS, PUPIL, LID, BROW = '#FFFFFF', '#7A4222', '#2A1408', '#2E1C14', '#2E1C14'
CHEEK, NOSE, LIP, MOUTH_IN, TONGUE = '#F28C80', '#D98A62', '#B0583A', '#7A2420', '#E66A6A'

rows = []
def add(part, bone, col, d, stroke=0, alpha=1, drawn=1):
    rows.append(f'{part},{bone},{drawn},{col},{alpha},{stroke},{d}')
def marker(part, bone):
    rows.append(f'{part},{bone},1,#000000,0,0,')
def ell(x, y, rx, ry):
    return f'M {x-rx} {y} A {rx} {ry} 0 1 0 {x+rx} {y} A {rx} {ry} 0 1 0 {x-rx} {y} Z'
def half_ell(x, y, rx, ry):  # lower half of an ellipse
    return f'M {x-rx} {y} A {rx} {ry} 0 0 0 {x+rx} {y} Z'
def star(cx, cy, R, r):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5; rr = R if i % 2 == 0 else r
        pts.append(f'{cx + rr * math.cos(a):.1f} {cy + rr * math.sin(a):.1f}')
    return 'M ' + ' L '.join(pts) + ' Z'


def path(cmds, T):
    """SVG path of [('M', x, y), ('L', x, y), ('Q', cx, cy, x, y), ('C', ...), ('Z',)] in local units, through T(x, y)."""
    out = []
    for c in cmds:
        out.append(c[0])
        for i in range(1, len(c), 2):
            x, y = T(c[i], c[i + 1]); out.append(f'{x:.1f} {y:.1f}')
    return ' '.join(out)
def frame(ox, oy, deg, mirror_y=1):
    """Local -> ref: rotate by deg, flip y by mirror_y, then move to (ox, oy)."""
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    return lambda x, y: (ox + x * c - y * mirror_y * s, oy + x * s + y * mirror_y * c)

# ---------------------------------------------------------------- back layers
add('backpack', 'torso', BAG, 'M 366 398 Q 376 372 432 370 L 444 372 L 434 612 Q 398 620 370 602 Q 356 500 366 398 Z')
add('backpack', 'torso', BAG_SH, 'M 366 420 Q 360 500 372 600 Q 382 607 396 608 L 398 420 Q 382 414 366 420 Z')
add('backpack', 'torso', STRAP_SH, 'M 372 470 L 432 466 L 431 482 L 371 486 Z')

# far arm (tubes) and hand
marker('arm_upper_far', 'shoulder_far')
def hand(part, bone, wrist, elbow, thumb_side, col, shade):
    """A cartoon hand drawn along its forearm (x from the wrist towards the fingertips), relaxed: round palm grown out
    of the wrist, four plump round-tipped fingers lying close together, the thumb apart on [thumb_side] (+1/-1 of
    local y). Same drawing for every hand of every character; only size and colour change."""
    deg = math.degrees(math.atan2(wrist[1] - elbow[1], wrist[0] - elbow[0]))
    T = frame(wrist[0], wrist[1], deg, thumb_side)
    # palm and wrist: one rounded shape (the forearm tube ends under it)
    add(part, bone, col, path([('M', -6, -17), ('C', 10, -22, 30, -24, 42, -18), ('C', 50, -12, 50, 12, 42, 18),
                               ('C', 30, 24, 10, 22, -6, 17), ('Z',)], T))
    # fingers: index next to the thumb (+y) to the little finger, close together, gently fanned
    for y, length, ang in ((12, 40, 4), (4, 45, 1), (-4, 42, -2), (-12, 34, -6)):
        a = math.radians(ang)
        add(part, bone, col, path([('M', 36, y), ('L', 36 + length * math.cos(a), y + length * math.sin(a))], T), 12.5)
    # thumb: apart, pointing forward along the index finger
    add(part, bone, col, path([('M', 14, 14), ('Q', 26, 30, 44, 28)], T), 14)
    # a line between the fingers and a palm crease, in the shade colour
    add(part, bone, shade, path([('M', 40, 0), ('L', 70, 1)], T), 2.5)
    add(part, bone, shade, path([('M', 14, 6), ('Q', 24, 2, 30, -8)], T), 3)
hand('hand_far', 'wrist_far', (819.4, 478.1), (721.3, 454.7), -1, SKIN_FAR, SKIN_SH)

# legs (tubes) and shoes
marker('thigh_far', 'hip_far')
def shoe(part, bone, ax, ay, tilt=16):
    """A high-top sneaker seen from the side, toe towards the way the character faces (screen right in the 3q
    view), tilted [tilt] degrees toe down as in the design: orange upper hugging the ankle with a padded collar, a
    tongue bump, two light velcro straps across the vamp, a thick white sole and a round white toe cap. Local x from
    the ankle towards the toe, y down; same drawing for both feet."""
    T = frame(ax, ay, tilt)
    add(part, bone, SHOE, path([('M', -34, -4), ('C', -30, -16, -12, -16, -2, -4), ('C', 8, 6, 20, -14, 36, -6),
                                ('C', 60, 6, 86, 24, 100, 42), ('L', 100, 54), ('L', -38, 54),
                                ('C', -42, 30, -40, 10, -34, -4), ('Z',)], T))
    add(part, bone, SHOE_SH, path([('M', -34, -4), ('C', -40, 10, -42, 30, -38, 54), ('L', -22, 54),
                                   ('C', -26, 34, -24, 12, -18, -8), ('Z',)], T))
    add(part, bone, SHOE_STRAP, path([('M', 6, 16), ('L', 52, 22)], T), 11)
    add(part, bone, SHOE_STRAP, path([('M', 10, 34), ('L', 62, 40)], T), 11)
    add(part, bone, SOLE, path([('M', 64, 22), ('C', 90, 26, 108, 42, 106, 58), ('L', 66, 58),
                                ('C', 58, 46, 58, 32, 64, 22), ('Z',)], T))
    add(part, bone, SOLE, path([('M', -42, 48), ('L', 104, 50), ('C', 112, 54, 112, 66, 102, 68),
                                ('L', -36, 66), ('C', -46, 64, -46, 52, -42, 48), ('Z',)], T))
shoe('shoe_far', 'ankle_far', 596, 892)
marker('thigh_near', 'hip_near')
shoe('shoe_near', 'ankle_near', 410, 888)

# shorts top
add('pelvis', 'root', JEAN, 'M 408 616 L 616 616 L 618 694 Q 570 704 540 702 L 512 690 L 484 702 Q 448 704 406 694 Z')
add('pelvis', 'root', JEAN_SH, 'M 590 616 L 616 616 L 618 694 Q 604 698 588 700 Z')
add('pelvis', 'root', JEAN_LINE, 'M 514 628 L 514 670 Q 512 682 500 688', 4)
add('pelvis', 'root', JEAN_LINE, 'M 424 630 Q 446 652 474 628', 4)
add('pelvis', 'root', JEAN_LINE, 'M 602 630 Q 584 652 556 628', 4)

# neck, shirt
add('neck', 'torso', SKIN, 'M 490 330 L 538 330 L 540 398 L 488 398 Z')
add('neck', 'torso', SKIN_SH, 'M 490 352 Q 514 372 540 352 L 540 372 Q 514 388 488 372 Z')
add('torso', 'torso', RED, 'M 402 404 Q 450 386 486 384 Q 514 402 544 384 Q 592 386 628 404 L 618 452 L 616 626 L 408 626 L 406 452 Z')
add('torso', 'torso', RED_SH, 'M 592 396 Q 614 398 628 404 L 618 452 L 616 626 L 594 626 Q 600 520 592 396 Z')
add('torso', 'torso', RED_DK, 'M 484 386 Q 514 412 546 386', 11)
add('torso', 'torso', WHITE, star(533, 480, 32, 13))
# Far shoulder: a circle in the shirt's shade over the far sleeve's start, as Ben's (round whichever way the arm turns).
add('torso', 'torso', RED_SH, ell(605, 430, 32, 32))
add('torso', 'torso', STRAP, 'M 448 392 Q 434 470 420 560', 20)
add('torso', 'torso', STRAP_SH, 'M 586 390 Q 604 450 612 525', 16)

# head: hair behind, ear, face with the shadow under the fringe
add('hair_back', 'hair', HAIR_BACK, 'M 596 84 C 646 104 672 160 664 222 L 640 236 L 616 120 Z')
add('head', 'head', SKIN, ell(398, 264, 21, 29))
add('head', 'head', EAR_IN, ell(398, 266, 10, 16))
add('head', 'head', SKIN, 'M 406 250 C 406 160 468 118 540 118 C 616 118 662 172 658 250 C 654 322 604 362 532 362 C 458 362 406 322 406 250 Z')
add('head', 'head', SKIN_SH, 'M 430 196 Q 470 190 500 214 Q 540 186 580 206 Q 612 190 650 214 L 650 196 Q 610 172 580 188 Q 540 168 500 196 Q 470 172 430 180 Z')
add('head', 'head', CHEEK, ell(478, 284, 25, 16), 0, .8)
add('head', 'head', CHEEK, ell(624, 284, 19, 15), 0, .8)
add('head', 'head', NOSE, 'M 554 262 Q 568 268 560 280', 4)

# eyes: open, half, happy, wide (drawn=0 except open)
def eye(prefix, cx, cy, rx, ry, ir):
    p = f'{prefix}_open'
    add(p, 'head', WHITE, ell(cx, cy, rx, ry))
    add(p, 'head', IRIS, ell(cx + 5, cy + 1, ir, ir))
    add(p, 'head', PUPIL, ell(cx + 6, cy + 1, ir * .5, ir * .5))
    add(p, 'head', WHITE, ell(cx - 1, cy - 7, 5, 5))
    add(p, 'head', LID, f'M {cx - rx - 2} {cy - 4} Q {cx} {cy - ry - 10} {cx + rx + 2} {cy - 6}', 5)
    p = f'{prefix}_half'
    add(p, 'head', WHITE, half_ell(cx, cy - 2, rx, ry), drawn=0)
    add(p, 'head', IRIS, half_ell(cx + 5, cy - 1, ir, ir), drawn=0)
    add(p, 'head', PUPIL, half_ell(cx + 6, cy - 1, ir * .5, ir * .5), drawn=0)
    add(p, 'head', LID, f'M {cx - rx - 2} {cy - 2} L {cx + rx + 2} {cy - 3}', 5, drawn=0)
    p = f'{prefix}_happy'
    add(p, 'head', LID, f'M {cx - rx + 2} {cy + 8} Q {cx} {cy - 16} {cx + rx - 2} {cy + 8}', 6, drawn=0)
    p = f'{prefix}_wide'
    add(p, 'head', WHITE, ell(cx, cy - 2, rx + 2, ry + 4), drawn=0)
    add(p, 'head', IRIS, ell(cx + 4, cy, ir * .8, ir * .8), drawn=0)
    add(p, 'head', PUPIL, ell(cx + 5, cy, ir * .38, ir * .38), drawn=0)
    add(p, 'head', WHITE, ell(cx - 1, cy - 6, 4, 4), drawn=0)
    add(p, 'head', LID, f'M {cx - rx - 2} {cy - 8} Q {cx} {cy - ry - 16} {cx + rx + 2} {cy - 10}', 4, drawn=0)
eye('eye_near', 502, 238, 27, 26, 20)
eye('eye_far', 603, 238, 24, 26, 19)
add('brow_near', 'head', BROW, 'M 474 204 Q 496 190 518 196', 7)
add('brow_far', 'head', BROW, 'M 588 194 Q 610 188 632 202', 7)

# mouths: smile (shown), e, a, o, s, laugh
add('mouth_smile', 'head', LIP, 'M 520 297 Q 548 314 578 298', 5)
add('mouth_e', 'head', MOUTH_IN, 'M 522 296 Q 548 302 576 296 Q 568 318 548 320 Q 530 318 522 296 Z', drawn=0)
add('mouth_e', 'head', WHITE, 'M 526 298 Q 548 303 572 298 L 570 305 Q 548 309 528 305 Z', drawn=0)
add('mouth_a', 'head', MOUTH_IN, ell(549, 305, 15, 15), drawn=0)
add('mouth_a', 'head', TONGUE, ell(549, 313, 9, 5), drawn=0)
add('mouth_o', 'head', MOUTH_IN, ell(549, 305, 10, 12), drawn=0)
add('mouth_s', 'head', MOUTH_IN, 'M 524 298 Q 548 304 574 298 Q 566 312 548 313 Q 532 312 524 298 Z', drawn=0)
add('mouth_s', 'head', WHITE, 'M 528 300 Q 548 305 570 300 L 568 307 Q 548 311 530 307 Z', drawn=0)
add('mouth_laugh', 'head', MOUTH_IN, 'M 516 292 Q 548 302 580 292 Q 574 328 548 330 Q 522 328 516 292 Z', drawn=0)
add('mouth_laugh', 'head', WHITE, 'M 521 295 Q 548 304 575 295 L 573 302 Q 548 310 523 302 Z', drawn=0)
add('mouth_laugh', 'head', TONGUE, ell(548, 320, 13, 6), drawn=0)

# hair: main mass with the fringe swept to the right, sideburn by the ear, two cowlicks
add('hair', 'hair', HAIR,
    'M 410 230 C 380 236 358 222 356 196 C 352 160 360 120 388 98 L 368 86 L 404 86 '
    'C 418 70 432 56 448 48 L 444 72 C 480 52 540 48 588 62 C 626 74 652 98 664 132 '
    'L 682 140 L 666 158 C 672 178 668 196 656 208 L 640 178 L 612 196 L 600 168 L 566 196 L 548 160 '
    'C 520 168 490 172 466 180 C 452 186 444 200 440 222 L 436 252 C 428 240 420 232 410 230 Z')

# near arm (tubes) and hand, in front
marker('arm_upper_near', 'shoulder_near')
# Near shoulder over the near sleeve's start, with the strap over it.
add('shoulder_near', 'torso', RED, ell(372, 432, 34, 34))
add('shoulder_near', 'torso', STRAP, 'M 448 392 Q 440 420 436 440', 20)
hand('hand_near', 'wrist_near', (203.9, 477.8), (278.3, 460.7), 1, SKIN, SKIN_SH)

open(OUT + '/shapes.csv', 'w').write(
    '# part,bone,drawn,colour,alpha,stroke (0 = fill),d (SVG path, ref px of the 3q skeleton): Kai drawn for the rig, '
    'assets/templates/vector/README.md; a part with no d is where a limb\'s tubes go\n' + '\n'.join(rows) + '\n')

# Limb tubes: t 0..1 upper bone, 1..2 lower; rows draw in order (skin first, garments over it).
H = []
def hose(limb, u, l, col, t0, t1, w, cap='butt'):
    H.append(f'{limb},{u},{l},{col},{t0},{t1},{w},{cap}')
for side, near in (('far', False), ('near', True)):
    sk = SKIN if near else SKIN_FAR
    red, cuff = (RED, RED_DK) if near else (RED_SH, '#8E1220')
    a, b = f'shoulder_{side}', f'elbow_{side}'
    hose(f'arm_{side}', a, b, sk, 0, 2, 40, 'round')
    # The sleeve starts at the shoulder (butt; the shoulder circle covers it, as Ben's); a cuff band at its end.
    sleeve_end = .42 if near else .55
    hose(f'arm_{side}', a, b, red, 0, sleeve_end, 66)
    hose(f'arm_{side}', a, b, cuff, sleeve_end - .1, sleeve_end + .03, 70)
    jean, cf, sock = (JEAN, CUFF, SOCK) if near else (JEAN_SH, CUFF_SH, SOCK_SH)
    a, b = f'hip_{side}', f'knee_{side}'
    hose(f'leg_{side}', a, b, sk, 0, 2, 52, 'round')
    hose(f'leg_{side}', a, b, SKIN_SH, 1.0, 1.12, 53)
    hose(f'leg_{side}', a, b, jean, -.3, .52, 90)
    hose(f'leg_{side}', a, b, cf, .5, 1.02, 100)
    hose(f'leg_{side}', a, b, sock, 1.54, 2, 50)
    hose(f'leg_{side}', a, b, STRIPE, 1.62, 1.68, 51)
open(OUT + '/hoses.csv', 'w').write(
    '# limb,upper bone,lower bone,colour,t0,t1 (0..1 upper bone, 1..2 lower),width (ref px),round|butt: Kai\'s tubes, '
    'assets/templates/vector/README.md\n' + '\n'.join(H) + '\n')
print(len(rows), 'shapes', len(H), 'tubes')
