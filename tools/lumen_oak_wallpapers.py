"""Draws the Lumen & Oak sample wallpapers (themes/lumen-oak-*): dark, warm, 1920x1080, a small wordmark bottom right.

Lumen & Oak is a made-up company. Usage:
  python3 tools/lumen_oak_wallpapers.py launcher/app/src/main/res/font/outfit.ttf themes/lumen-oak-showroom/wallpapers
Needs Pillow (pip install pillow). The meeting-room wallpaper.jpg is 03-brass-pendant.jpg.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1920, 1080
FONT = sys.argv[1]
OUT = Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)
GOLD = (200, 162, 107)


def vignette(img, strength=0.75):
    mask = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(mask)
    for i in range(60):
        t = i / 60
        d.ellipse([-W * 0.3 + t * W * 0.3, -H * 0.4 + t * H * 0.4, W * 1.3 - t * W * 0.3, H * 1.4 - t * H * 0.4],
                  fill=int(255 * t))
    mask = mask.filter(ImageFilter.GaussianBlur(120))
    dark = Image.new("RGB", (W, H), (0, 0, 0))
    return Image.composite(img, Image.blend(img, dark, strength), mask)


def bottom_shade(img):
    """Darker towards the bottom, where the dock and its band sit."""
    shade = Image.new("L", (W, H))
    shade.putdata([int(200 * max(0, (y / H - 0.45) / 0.55) ** 1.4) for y in range(H) for _ in range(W)])
    return Image.composite(Image.new("RGB", (W, H), (0, 0, 0)), img, shade)


def wordmark(img):
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(FONT, 26)
    text = "L U M E N   &   O A K"
    w = d.textlength(text, font=f)
    d.text((W - 90 - w, H - 70), text, font=f, fill=(GOLD[0] // 2 + 20, GOLD[1] // 2 + 15, GOLD[2] // 2 + 10))
    return img


def walnut():
    rnd = random.Random(7)
    img = Image.new("RGB", (W // 2, H // 2))
    px = img.load()
    streak = [0.8 + 0.4 * rnd.random() for _ in range(H // 2)]
    for y in range(H // 2):
        for x in range(W // 2):
            warp = 7 * math.sin(x / 260 + 0.8 * math.sin(y / 70)) + 2 * math.sin(x / 53)
            g = 0.5 + 0.5 * math.sin((y + warp) * streak[y] / 2.1)
            ring = 0.5 + 0.5 * math.sin((y + warp * 3.1) / 23)
            v = 0.35 * g + 0.45 * ring + 0.2 * rnd.random()
            px[x, y] = (int(38 + 30 * v), int(22 + 16 * v), int(12 + 8 * v))
    img = img.resize((W, H), Image.BICUBIC).filter(ImageFilter.GaussianBlur(1.2))
    light = Image.new("RGB", (W, H), (255, 190, 120))
    glow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(glow).ellipse([W * 0.45, -H * 0.5, W * 1.3, H * 0.6], fill=70)
    img = Image.composite(light, img, glow.filter(ImageFilter.GaussianBlur(220)))
    return wordmark(bottom_shade(vignette(img)))


def linen():
    rnd = random.Random(3)
    img = Image.new("RGB", (W, H), (34, 30, 27))
    d = ImageDraw.Draw(img)
    for y in range(0, H, 3):
        c = 30 + rnd.randint(0, 9)
        d.line([(0, y), (W, y)], fill=(c, c - 3, c - 6))
    for x in range(0, W, 3):
        c = rnd.randint(0, 6)
        d.line([(x, 0), (x, H)], fill=(36 + c, 32 + c, 28 + c))
    img = img.filter(ImageFilter.GaussianBlur(0.8))
    light = Image.new("RGB", (W, H), (230, 200, 160))
    glow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(glow).ellipse([-W * 0.3, -H * 0.6, W * 0.6, H * 0.5], fill=60)
    img = Image.composite(light, img, glow.filter(ImageFilter.GaussianBlur(240)))
    return wordmark(bottom_shade(vignette(img)))


def brass():
    img = Image.new("RGB", (W, H), (14, 12, 11))
    glow = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(glow)
    d.ellipse([W * 0.52, H * 0.02, W * 0.86, H * 0.42], fill=120)
    img = Image.composite(Image.new("RGB", (W, H), GOLD), img, glow.filter(ImageFilter.GaussianBlur(160)))
    d = ImageDraw.Draw(img)
    cx, cy = W * 0.69, H * 0.2
    for i, r in enumerate((260, 340, 430)):
        d.arc([cx - r, cy - r, cx + r, cy + r], 200, 340, fill=(90 - i * 18, 72 - i * 14, 46 - i * 9), width=2)
    d.line([(cx, 0), (cx, cy - 40)], fill=(70, 56, 36), width=2)
    pendant = Image.new("L", (W, H), 0)
    ImageDraw.Draw(pendant).pieslice([cx - 70, cy - 50, cx + 70, cy + 50], 180, 360, fill=255)
    img = Image.composite(Image.new("RGB", (W, H), (120, 92, 54)), img, pendant)
    bulb = Image.new("L", (W, H), 0)
    ImageDraw.Draw(bulb).ellipse([cx - 22, cy - 14, cx + 22, cy + 22], fill=255)
    img = Image.composite(Image.new("RGB", (W, H), (255, 225, 170)), img, bulb.filter(ImageFilter.GaussianBlur(6)))
    return wordmark(bottom_shade(vignette(img, 0.6)))


for name, make in (("01-walnut.jpg", walnut), ("02-linen.jpg", linen), ("03-brass-pendant.jpg", brass)):
    make().save(OUT / name, quality=82, optimize=True, progressive=True)
    print(name, (OUT / name).stat().st_size // 1024, "KB")
