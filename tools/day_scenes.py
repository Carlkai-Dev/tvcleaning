"""Draws four daytime scenes for wallpaper_moods' afternoon group, in the style of tools/scenes_wallpapers.py and
with its building blocks: a clear sky above, the lower third dark for the dock and the text.

    python3 tools/day_scenes.py /tmp/day      # writes 15-noon-coast.svg ... 18-clear-lake.svg

Rendered like the scenes: a headless Chrome screenshot of each SVG at 1280x720, saved as JPEG (themes/vag/wallpapers).
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scenes_wallpapers import grad, radial, ridge, hills, water, ripples, svg  # noqa: E402

SKY = [(0, "#2f6fb5"), (.45, "#5d9fd8"), (.7, "#a9d2ee"), (.8, "#d8ecf6")]


def clouds(seed, n, ymax, op=.75):
    """Soft fair-weather clouds: blurred clusters of white ellipses above ymax."""
    rnd = random.Random(seed)
    out = []
    for _ in range(n):
        cx, cy, w = rnd.uniform(-50, 1330), rnd.uniform(40, ymax), rnd.uniform(90, 220)
        puffs = "".join(f'<ellipse cx="{cx + rnd.uniform(-w / 2, w / 2):.0f}" cy="{cy + rnd.uniform(-12, 8):.0f}" '
                        f'rx="{rnd.uniform(w / 4, w / 2):.0f}" ry="{rnd.uniform(14, 30):.0f}"/>' for _ in range(5))
        out.append(f'<g fill="#ffffff" opacity="{op}" filter="url(#b8)">{puffs}</g>')
    return "".join(out)


def noon_coast():
    """A bright sea under the midday sun, a dark promenade curve in front: Marine Drive at noon."""
    d = grad("sky", SKY) + radial("sun", "#fffbe6", 1, .4)
    gd, gb = water(430, "#3f86c4", "#0d2a45")
    lamps = "".join(f'<circle cx="{x}" cy="{612 - x * 0.05:.0f}" r="3" fill="#f5f0dc" opacity=".7"/>'
                    for x in range(40, 1280, 70))
    b = ('<rect width="1280" height="720" fill="url(#sky)"/><circle cx="1030" cy="110" r="190" fill="url(#sun)"/>'
         + clouds(151, 7, 300) + f'<path d="{hills(153, 428, 6, 2.5)}" fill="#6f8fae" opacity=".6"/>' + gb +
         ripples(155, 440, "#ffffff", 3, (0.08, 0.3)) +
         '<path d="M-20,720 L-20,640 C300,600 900,570 1300,610 L1300,720 Z" fill="#14202c"/>' + lamps +
         f'<path d="{hills(157, 700, 14, 2.0)}" fill="#0a1118"/>')
    return svg(d + gd, b)


def green_valley():
    """Green hills under a blue sky with drifting clouds."""
    d = grad("sky", SKY) + radial("sun", "#fffbe6", .9, .35)
    b = ('<rect width="1280" height="720" fill="url(#sky)"/><circle cx="240" cy="120" r="170" fill="url(#sun)"/>'
         + clouds(161, 8, 320) +
         f'<path d="{hills(163, 430, 40, 1.2)}" fill="#7fb07a"/>'
         f'<path d="{hills(165, 500, 45, 1.0)}" fill="#4f8a55"/>'
         f'<path d="{hills(167, 580, 45, 1.3)}" fill="#2e5f39"/>'
         f'<path d="{hills(169, 660, 35, 1.6)}" fill="#163522"/>'
         f'<path d="{hills(171, 710, 18, 2.2)}" fill="#0b1d13"/>')
    return svg(d, b)


def palm_beach():
    """A sunny beach, palms leaning over the sand in silhouette."""
    d = grad("sky", SKY) + radial("sun", "#fffbe6", 1, .4) + grad("sand", [(0, "#c9a877"), (1, "#3b2c1c")])
    gd, gb = water(440, "#4b9ad0", "#1d5c86")
    rnd = random.Random(181)

    def palm(x, h, lean):
        top_x, top_y = x + lean, 720 - h
        trunk = (f'<path d="M{x - 7},720 Q{x + lean * .4},{720 - h * .6} {top_x},{top_y} '
                 f'L{top_x + 6},{top_y + 4} Q{x + lean * .4 + 8},{720 - h * .6} {x + 9},720 Z" fill="#101a12"/>')
        fronds = "".join(f'<path d="M{top_x},{top_y} q{dx * .5:.0f},{-28 + rnd.uniform(-8, 8):.0f} {dx:.0f},{dy:.0f} '
                         f'q{-dx * .4:.0f},-14 {-dx:.0f},{-dy:.0f} Z" fill="#0f1d13"/>'
                         for dx, dy in ((120, 40), (-120, 45), (90, 80), (-95, 85), (40, 95), (-30, 100), (150, 10),
                                        (-150, 12)))
        return trunk + fronds

    b = ('<rect width="1280" height="720" fill="url(#sky)"/><circle cx="880" cy="100" r="190" fill="url(#sun)"/>'
         + clouds(183, 6, 280) + gb + ripples(185, 450, "#ffffff", 3, (0.08, 0.28)) +
         '<path d="M-20,560 C400,540 900,550 1300,575 L1300,720 L-20,720 Z" fill="url(#sand)"/>'
         + palm(130, 430, 120) + palm(1150, 400, -110) + palm(1040, 300, -60) +
         f'<path d="{hills(187, 705, 12, 2.4)}" fill="#1a130b"/>')
    return svg(d + gd, b)


def clear_lake():
    """Snow-capped mountains over a still lake on a clear afternoon."""
    d = grad("sky", SKY) + radial("sun", "#fffbe6", .9, .35) + grad("rock", [(0, "#eef3f8"), (.25, "#8aa0b8"),
                                                                              (1, "#3d5068")])
    gd, gb = water(500, "#4f86b5", "#10263d")
    b = ('<rect width="1280" height="720" fill="url(#sky)"/><circle cx="1100" cy="130" r="170" fill="url(#sun)"/>'
         + clouds(191, 5, 220) +
         f'<path d="{ridge(193, 380, 110, 1.1)}" fill="url(#rock)"/>'
         f'<path d="{ridge(195, 450, 60, 1.0)}" fill="#3d6a55"/>'
         f'<path d="{hills(197, 500, 12, 2.0)}" fill="#24493a"/>' + gb + ripples(199, 510, "#ffffff", 2, (0.05, 0.2)) +
         f'<path d="{hills(201, 660, 28, 1.4)}" fill="#0d1d17"/>'
         f'<path d="{hills(203, 708, 14, 2.4)}" fill="#060e0b"/>')
    return svg(d + gd, b)


SCENES = [("15-noon-coast", noon_coast), ("16-green-valley", green_valley), ("17-palm-beach", palm_beach),
          ("18-clear-lake", clear_lake)]

if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    os.makedirs(sys.argv[1], exist_ok=True)
    for name, scene in SCENES:
        with open(os.path.join(sys.argv[1], f"{name}.svg"), "w") as f:
            f.write(scene())
    print(len(SCENES), "scenes written to", sys.argv[1])
