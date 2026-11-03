#!/usr/bin/env python3
"""Draws the Liftr app icon (orange gradient, white dumbbell with an upward stroke) at 1024 px."""
import os, sys
from PIL import Image, ImageDraw, ImageFilter

S = 4096  # supersample, scaled down to 1024


def gradient(size, top, bottom):
    img = Image.new("RGB", (size, size))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / (size - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (size, y)], fill=c)
    return img


def icon():
    img = gradient(S, (255, 128, 32), (214, 70, 0))
    # soft light in the top-left corner
    glow = Image.new("L", (S, S), 0)
    ImageDraw.Draw(glow).ellipse([-S * 0.4, -S * 0.5, S * 0.8, S * 0.6], fill=70)
    glow = glow.filter(ImageFilter.GaussianBlur(S * 0.08))
    img = Image.composite(Image.new("RGB", (S, S), (255, 190, 120)), img, glow)

    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    white = (255, 255, 255, 255)
    cx, cy = S / 2, S * 0.52
    u = S / 1024
    # bar
    d.rounded_rectangle([cx - 300 * u, cy - 34 * u, cx + 300 * u, cy + 34 * u], radius=34 * u, fill=white)
    # plates: big inner, small outer, both sides
    for side in (-1, 1):
        big_x = cx + side * 215 * u
        d.rounded_rectangle([big_x - 52 * u, cy - 210 * u, big_x + 52 * u, cy + 210 * u], radius=40 * u, fill=white)
        small_x = cx + side * 318 * u
        d.rounded_rectangle([small_x - 38 * u, cy - 140 * u, small_x + 38 * u, cy + 140 * u], radius=30 * u, fill=white)
    # rotate slightly upwards for energy
    layer = layer.rotate(18, resample=Image.BICUBIC, center=(cx, cy))
    # shadow
    shadow = layer.split()[3].filter(ImageFilter.GaussianBlur(S * 0.018))
    shadow_img = Image.new("RGBA", (S, S), (120, 30, 0, 0))
    shadow_img.putalpha(shadow.point(lambda a: int(a * 0.45)))
    base = img.convert("RGBA")
    base.alpha_composite(shadow_img, (0, int(S * 0.012)))
    base.alpha_composite(layer)
    return base.convert("RGB").resize((1024, 1024), Image.LANCZOS)


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = icon()
    for path in sys.argv[1:] or []:
        out.save(os.path.join(root, path))
    if not sys.argv[1:]:
        out.save(os.path.join(root, "icon.png"))
