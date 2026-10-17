#!/usr/bin/env python3
"""Reproduce the vector logo as local, antialiased legacy launcher PNGs.

Development-only dependency: Pillow. Android uses no additional libraries.
"""
from pathlib import Path
from PIL import Image, ImageDraw

RES = Path(__file__).resolve().parents[1] / "app/src/main/res"
SCALE = 4


def icon(size, circular=False):
    side = size * SCALE
    image = Image.new("RGBA", (side, side))
    pixels = image.load()
    for y in range(side):
        for x in range(side):
            t = (x + y) / (2 * (side - 1))
            stops = ((59, 130, 246), (37, 99, 235), (29, 78, 216))
            a, b = (stops[0], stops[1]) if t < .5 else (stops[1], stops[2])
            u = t * 2 if t < .5 else (t - .5) * 2
            pixels[x, y] = tuple(round(p + (q - p) * u) for p, q in zip(a, b)) + (255,)
    mask = Image.new("L", image.size)
    draw = ImageDraw.Draw(mask)
    if circular:
        draw.ellipse((0, 0, side - 1, side - 1), fill=255)
    else:
        draw.rounded_rectangle((0, 0, side - 1, side - 1), radius=side * .23, fill=255)
    image.putalpha(mask)
    art = Image.new("RGBA", image.size)
    pen = ImageDraw.Draw(art)
    # Match the adaptive foreground, enlarged to the legacy icon's visible bounds.
    def rect(box, radius):
        coords = [((v - 54) * 1.42 + 54) * side / 108 for v in box]
        pen.rounded_rectangle(coords, radius=radius * 1.42 * side / 108, fill="white")
    rect((42, 50, 66, 58), 0)
    rect((34, 36, 44, 72), 3)
    rect((27, 43, 34, 65), 2)
    rect((64, 36, 74, 72), 3)
    rect((74, 43, 81, 65), 2)
    art = art.rotate(35, resample=Image.Resampling.BICUBIC)
    image.alpha_composite(art)
    return image.resize((size, size), Image.Resampling.LANCZOS)


if __name__ == "__main__":
    for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        destination = RES / f"mipmap-{density}"
        destination.mkdir(exist_ok=True)
        icon(size).save(destination / "ic_launcher.png", optimize=True)
        icon(size, circular=True).save(destination / "ic_launcher_round.png", optimize=True)
