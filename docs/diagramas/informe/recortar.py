# Recorta el margen sobrante de una imagen (todo lo que es color de fondo) y deja un borde parejo.
import sys
from PIL import Image, ImageChops
for f in sys.argv[1:]:
    im = Image.open(f).convert('RGB')
    fondo = Image.new('RGB', im.size, im.getpixel((2, im.height - 3)))
    caja = ImageChops.difference(im, fondo).point(lambda v: 255 if v > 12 else 0).getbbox()
    if caja:
        m = 36
        caja = (max(0, caja[0] - m), max(0, caja[1] - m), min(im.width, caja[2] + m), min(im.height, caja[3] + m))
        im.crop(caja).save(f)
    print(f, Image.open(f).size)
