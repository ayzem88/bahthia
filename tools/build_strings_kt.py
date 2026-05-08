"""يَدمج translations_output.txt مع قالب Strings.kt ويَكتب الملفّ النهائي."""
import os

INPUT = r"C:\Users\Aymen\Desktop\Bahthia_Library\tools\translations_output.txt"
OUT = r"C:\Users\Aymen\Desktop\Bahthia_Library\modules\i18n\src\main\kotlin\com\bahthia\i18n\Strings.kt"


def main():
    with open(INPUT, encoding="utf-8") as f:
        content = f.read()

    # نَأخذ كلّ كتل private val LANG = mapOf(...)
    # كلّ كتلة تَبدأ بـ "    private val" وتَنتهي بـ "    )"
    # نَستخرجها مع المحافظة على المسافة البادئة

    body_lines = []
    for line in content.split("\n"):
        if line.startswith("    private val") or line.strip().startswith('"') or line.strip() == ")":
            body_lines.append(line)

    body = "\n".join(body_lines)

    template = f"""package com.bahthia.i18n

/**
 * كلّ نصوص الواجهة — مُنظَّمة بمساحات اسم نقطيّة (dotted namespaces).
 *
 * البنية:
 * ```
 * search.button.search    → "ابحث" / "Search"
 * search.button.clear     → "امسح" / "Clear"
 * settings.title          → "الإعدادات"
 * ```
 *
 * تحديث الترجمات: ببساطة أضف المفتاح إلى الخريطة المناسبة.
 * إن كان المفتاح غير مُترجَم في اللغة الحاليّة، يَرجع إلى العربيّة.
 *
 * **هذا الملفّ مُولَّد آلياً** عبر `tools/extract_translations.py` و `tools/build_strings_kt.py`.
 * لا تُعدّله يدويّاً — عَدِّل القاموس الأمّ في الـ Python ثمّ شغّل السكربت.
 */
object Strings {{

{body}

    private val tables: Map<Locale, Map<String, String>> = mapOf(
        Locale.AR to AR,
        Locale.EN to EN,
        Locale.FR to FR,
        Locale.DE to DE,
        Locale.ES to ES,
        Locale.TR to TR,
        Locale.FA to FA,
        Locale.UR to UR,
        Locale.MS to MS,
        Locale.IT to IT,
        Locale.ZH to ZH,
        Locale.JA to JA,
        Locale.KO to KO,
    )

    /**
     * يُرجع النصّ المترجم للمفتاح [key] باللغة [locale].
     * إن لم يكن المفتاح مُترجَماً، يرجع إلى العربيّة.
     * إن لم يكن في العربيّة أيضاً، يُرجع المفتاح نفسه (مفيد للتنقيح).
     */
    fun get(locale: Locale, key: String): String {{
        tables[locale]?.get(key)?.let {{ return it }}
        AR[key]?.let {{ return it }}
        return key
    }}

    /** كلّ المفاتيح المعروفة. */
    fun allKeys(): Set<String> = AR.keys

    /** نسبة التغطية للغة معيّنة (٠.٠ – ١.٠). */
    fun coverage(locale: Locale): Double {{
        val total = AR.size.toDouble()
        if (total == 0.0) return 1.0
        val translated = tables[locale]?.size ?: 0
        return (translated / total).coerceIn(0.0, 1.0)
    }}
}}
"""

    with open(OUT, "w", encoding="utf-8") as f:
        f.write(template)
    print(f"OK: {OUT}")
    print(f"   Size: {os.path.getsize(OUT)} bytes")


if __name__ == "__main__":
    main()
