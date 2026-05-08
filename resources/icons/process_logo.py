"""
معالج اللوغو: يَأخذ logo.ico الأصلي، يَحذف الخلفيّة البيضاء (يَجعلها شفّافة)،
يُكبّر اللوغو ليَملأ الفراغ، ثمّ يَحفظ ملفّات بعدة دقّات لاستعمالات مختلفة:
  - bahthia.ico  (Windows MSI: 16, 32, 48, 64, 128, 256)
  - bahthia.png  (Linux/Mac عرض عام بدقّة 512×512)
  - bahthia.icns (macOS — لاحقاً، يَحتاج أداة منفصلة)

استعمال: python process_logo.py
"""
from PIL import Image
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(os.path.dirname(SCRIPT_DIR))
SRC = os.path.join(PROJECT_DIR, "logo.ico")
OUT_ICO = os.path.join(SCRIPT_DIR, "bahthia.ico")
OUT_PNG = os.path.join(SCRIPT_DIR, "bahthia.png")


def remove_white_background(img: Image.Image, threshold: int = 240) -> Image.Image:
    """يَستبدل البكسلات البيضاء (≥ threshold) بالشفّاف."""
    img = img.convert("RGBA")
    pixels = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if r >= threshold and g >= threshold and b >= threshold:
                pixels[x, y] = (255, 255, 255, 0)  # شفّاف
    return img


def crop_to_content(img: Image.Image) -> Image.Image:
    """يَقصّ الإطار الشفّاف حول المحتوى ليُكبَّر بعد ذلك."""
    bbox = img.getbbox()
    if bbox:
        return img.crop(bbox)
    return img


def upscale(img: Image.Image, target: int) -> Image.Image:
    """يُكبّر الصورة الصغيرة (32×32) إلى الدقّة المطلوبة بجودة Lanczos."""
    return img.resize((target, target), Image.LANCZOS)


def main():
    if not os.path.exists(SRC):
        raise SystemExit(f"❌ لم أَجد الملفّ: {SRC}")

    print(f"📂 قراءة: {SRC}")
    img = Image.open(SRC)
    print(f"   الحجم الأصلي: {img.size}, الوضع: {img.mode}")

    # ١) إزالة الخلفيّة البيضاء
    img = remove_white_background(img)

    # ٢) قصّ الإطار الشفّاف
    img = crop_to_content(img)
    print(f"   بعد القصّ: {img.size}")

    # ٣) تكبير لكلّ الدقّات المطلوبة
    sizes = [16, 32, 48, 64, 128, 256]
    upscaled = {}
    for s in sizes:
        upscaled[s] = upscale(img, s)

    # ٤) حفظ ICO متعدّد الدقّات
    upscaled[256].save(
        OUT_ICO,
        format="ICO",
        sizes=[(s, s) for s in sizes],
    )
    print(f"✅ كُتب ICO: {OUT_ICO}")

    # ٥) حفظ PNG عالي الدقّة (للمعاينة + Linux)
    upscale(img, 512).save(OUT_PNG, format="PNG")
    print(f"✅ كُتب PNG: {OUT_PNG}")

    print("\n🎉 انتهى. اللوغو جاهز للاستعمال في jpackage.")


if __name__ == "__main__":
    main()
