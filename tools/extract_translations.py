"""
يَستخرج الترجمات من قواميس Python بمطابقة بعد إزالة التشكيل،
ومُكمَّلة بترجمات يدويّة للمفاتيح غير الموجودة في Python.
"""
import json
import os
import re

PYTHON_I18N_DIR = r"E:\البرامج والتطبيقات\المكتبة البحثية\i18n"
OUTPUT_FILE = r"C:\Users\Aymen\Desktop\Bahthia_Library\tools\translations_output.txt"

# تشكيل عربي
TASHKEEL = re.compile(r"[ً-ٰٟـ]")  # حركات + ألف خنجريّة + تطويل


def strip_tashkeel(s: str) -> str:
    return TASHKEEL.sub("", s)


# قاموس المفاتيح: Kotlin key → النصّ العربي القانوني
KEYS = {
    # ─── شريط البحث ───
    "search.placeholder": "يمكنك البحث عن جذر أو كلمة أو مركّب أو جملة أو وزن صرفيّ ...",
    "search.button.search": "ابحث",
    "search.button.clear": "امسح",
    "search.button.search.tooltip": "البحث عن المطلوب",
    "search.button.clear.tooltip": "مسح المبحوث عنه",
    "search.mode.word": "الكلمة | الجملة",
    "search.mode.derivatives": "الجذر",
    "search.mode.pattern": "الوزن الصّرفيّ",
    "search.mode.regex": "التّعبير النّمطيّ",
    "search.mode.word.tooltip": "البحث عن كلمة مفردة؛ أو جملة مطابقة (مع تجاهل علامات الترقيم)؛ ادخِل | بين كلمتَين للتقارب، أو + بين كلمتَين للبحث في نفس الصفحة",
    "search.mode.derivatives.tooltip": "البحث عن كلّ مشتقّات الجذر",
    "search.mode.pattern.tooltip": "البحث بالوزن (مفعول، فاعل، استفعال...)",
    "search.mode.regex.tooltip": "بحث متقدّم بـ regex للمستخدم الخبير",
    "search.option.diacritics": "مراعاة التّشكيل",
    "search.option.whole_letters": "مطابقة الحروف",
    "search.option.diacritics.tooltip": "يُطابق فقط النصّ الذي يَحمل التشكيل المكتوب",
    "search.option.whole_letters.tooltip": "يفيد المطابقة الصارمة للحروف المكتوبة",

    # ─── العمود الأوّل (الفئات) ───
    "panel.categories.title": "الحقل المعرفيّ",
    "panel.categories.title.years": "الحقل الزمنيّ",
    "panel.categories.title.regions": "الحقل الجغرافيّ",
    "panel.categories.cycle.tooltip": "التبديل بين الحقل المعرفيّ والحقل الزمنيّ والحقل الجغرافيّ",
    "panel.categories.cycle.toYears": "التّبديل إلى الحقل الزمنيّ",
    "panel.categories.cycle.toRegions": "التّبديل إلى الحقل الجغرافيّ",
    "panel.categories.cycle.toCategories": "التّبديل إلى الحقل المعرفيّ",
    "panel.categories.search.placeholder": "ابحث ...",
    "panel.categories.allBooks": "جميع الكتب",
    "panel.categories.unclassified": "غير مصنّف",

    # ─── العمود الثاني (الكتب) ───
    "panel.books.title": "الكتب",
    "panel.books.search.placeholder": "ابحث في العناوين...",

    # ─── أزرار تحديد/إلغاء ───
    "panel.selectAll": "تحديد الكلّ",
    "panel.clearAll": "إلغاء الكلّ",
    "panel.selectAll.books.tooltip": "تَحديد كلّ الكتب لتقييد البحث بها",
    "panel.clearAll.books.tooltip": "إلغاء تَحديد كلّ الكتب المقيّدة للبحث",
    "panel.selectAll.categories.tooltip": "اختيار كلّ الحقول",
    "panel.clearAll.categories.tooltip": "إلغاء كلّ الحقول المختارة",

    # ─── ترويسة لوحة العرض ───
    "display.header.title": "كُنّاشة النّتائج",
    "display.header.bookCard.tooltip": "عرض معلومات الكتاب التفصيليّة",
    "display.header.saveSession.tooltip": "حفظ هذا (البحث + الخيارات + النّتائج) لاستعادته لاحقاً",
    "display.header.cite.tooltip": "للاقتباس الأكاديميّ",
    "display.header.close.tooltip": "إغلاق العرض الحاليّ ومسح اختيار النتيجة",
    "display.empty": "اختر نتيجة من الجدول لعرض الصفحة كاملةً",
    "display.breadcrumb.result": "نتيجة",
    "display.breadcrumb.browse": "تصفّح",
    "display.breadcrumb.page": "صفحة",

    # ─── جدول النتائج ───
    "table.col.serial": "مسلسل",
    "table.col.context": "السّياق",
    "table.col.book": "الكتاب",
    "table.col.year": "السنة",
    "table.col.page": "الصّفحة",
    "table.empty": "لا توجد نتائج",
    "table.searching": "جارٍ البحث…",
    "table.error.title": "تعذّر البحث",

    # ─── أزرار شريط الأدوات السفلي ───
    "toolbar.prevPage": "الصّفحة السّابقة",
    "toolbar.nextPage": "الصّفحة التّالية",
    "toolbar.nextGroup": "المجموعة التّالية",
    "toolbar.fullBook": "الكتاب كاملًا",
    "toolbar.nextResult": "النّتيجة التّالية",
    "toolbar.prevResult": "النّتيجة السّابقة",
    "toolbar.export": "تصدير",
    "toolbar.prevPage.tooltip": "الانتقال إلى الصفحة السابقة في الكتاب نفسه",
    "toolbar.nextPage.tooltip": "الانتقال إلى الصفحة التالية في الكتاب نفسه",
    "toolbar.nextGroup.tooltip": "تحميل المجموعة التالية من النتائج",
    "toolbar.fullBook.tooltip": "فتح الكتاب الذي تَنتمي إليه النتيجة في قارئ كامل",
    "toolbar.nextResult.tooltip": "الانتقال للنتيجة التالية",
    "toolbar.prevResult.tooltip": "الانتقال للنتيجة السابقة",
    "toolbar.export.tooltip": "تصدير النتائج إلى ملفّ نصّي أو وورد أو CSV",

    # ─── شريط الحالة ───
    "status.searching": "جارٍ البحث…",
    "status.empty": "المكتبة فارغة — استورد كتباً من قائمة الإعدادات لتبدأ",
    "status.loading": "جارٍ تحميل المكتبة…",

    # ─── قائمة الإعدادات ───
    "settings.title": "الإعدادات",
    "settings.tooltip": "قائمة الإعدادات",
    "settings.import": "استيراد الكتب",
    "settings.delete": "حذف الكتب",
    "settings.indexing": "الفهرسة الآلية",
    "settings.limit": "حد النتائج",
    "settings.limit.unlimited": "دون تحديد",
    "settings.language": "اللغة",
    "settings.timeMode": "معيار البحث الزمني",
    "settings.stats": "إحصاءات",
    "settings.favorites": "المفضّلة",
    "settings.sessions": "الجلسات المحفوظة",
    "settings.theme": "السمة",
    "settings.preferences": "التفضيلات",
    "settings.tour": "جولة تعريفيّة",
    "settings.backup": "النسخ الاحتياطي",
    "settings.checkUpdates": "التحقّق من التحديثات",
    "settings.reportBug": "الإبلاغ عن خطأ",
    "settings.suggestFeature": "اقتراح ميزة",
    "settings.suggestBook": "اقتراح كتاب",
    "settings.about": "حول البرنامج",
    "settings.contact": "تواصل معنا",
    "settings.donate": "ادعم المشروع",
    "settings.visitSite": "زيارة الموقع",

    # ─── السمة ───
    "theme.title": "اختر السمة",
    "theme.earthy": "ترابي (افتراضي)",
    "theme.dark": "داكن",
    "theme.gray": "رصاصي",
    "theme.auto": "تلقائي (يتبع النظام)",

    # ─── حدّ النتائج ───
    "limit.title": "حدّ النتائج",

    # ─── المعيار الزمني ───
    "timeMode.title": "معيار البحث الزمنيّ",
    "timeMode.deathYear": "سنة الوفاة",
    "timeMode.usageDate": "تاريخ الاستعمال",

    # ─── النسخ الاحتياطي ───
    "backup.export": "تصدير المكتبة...",
    "backup.restore": "استعادة من نسخة...",

    # ─── التبرّع ───
    "donate.bank": "تحويل بنكيّ",

    # ─── عام ───
    "common.ok": "حسناً",
    "common.cancel": "إلغاء",
    "common.close": "إغلاق",
    "common.yes": "نعم",
    "common.no": "لا",
    "common.error": "خطأ",
    "common.error.unknown": "خطأ غير معروف",
}


# ترجمات يدويّة لمفاتيح غير موجودة في Python أو غير مطابقة
# المُفتاح = Kotlin key، القيمة = dict {lang: translation}
MANUAL = {
    "search.mode.word": {  # "الكلمة | الجملة"
        "en": "Word | Phrase", "fr": "Mot | Phrase", "de": "Wort | Phrase",
        "es": "Palabra | Frase", "tr": "Kelime | Cümle", "fa": "کلمه | جمله",
        "ur": "لفظ | جملہ", "ms": "Kata | Frasa",
        "it": "Parola | Frase", "zh": "词 | 句", "ja": "単語 | 文", "ko": "단어 | 구",
    },
    "search.mode.derivatives": {  # "الجذر"
        "en": "Root", "fr": "Racine", "de": "Wurzel", "es": "Raíz",
        "tr": "Kök", "fa": "ریشه", "ur": "جڑ", "ms": "Akar",
        "it": "Radice", "zh": "词根", "ja": "語根", "ko": "어근",
    },
    "search.mode.pattern": {  # "الوزن الصّرفيّ"
        "en": "Pattern", "fr": "Schéma morph.", "de": "Muster", "es": "Patrón",
        "tr": "Vezin", "fa": "وزن صرفی", "ur": "وزن", "ms": "Wazan",
        "it": "Schema", "zh": "词型", "ja": "形態素", "ko": "형태",
    },
    "search.mode.regex": {  # "التّعبير النّمطيّ"
        "en": "Regex", "fr": "Regex", "de": "Regex", "es": "Regex",
        "tr": "Regex", "fa": "Regex", "ur": "Regex", "ms": "Regex",
        "it": "Regex", "zh": "正则", "ja": "正規表現", "ko": "정규식",
    },
    "panel.categories.title": {  # الحقل المعرفيّ
        "en": "Knowledge Field", "fr": "Domaine du savoir", "de": "Wissensgebiet",
        "es": "Campo del saber", "tr": "Bilgi Alanı", "fa": "حوزهٔ معرفت",
        "ur": "علمی شعبہ", "ms": "Bidang Ilmu",
        "it": "Campo del sapere", "zh": "知识领域", "ja": "知識分野", "ko": "지식 분야",
    },
    "panel.categories.title.years": {  # الحقل الزمنيّ
        "en": "Time Field", "fr": "Période", "de": "Zeitraum", "es": "Periodo",
        "tr": "Zaman Alanı", "fa": "حوزهٔ زمانی", "ur": "زمانی حدّ", "ms": "Tempoh Masa",
        "it": "Periodo", "zh": "时间", "ja": "時代", "ko": "시기",
    },
    "panel.categories.title.regions": {  # الحقل الجغرافيّ
        "en": "Region", "fr": "Région", "de": "Region", "es": "Región",
        "tr": "Bölge", "fa": "منطقه", "ur": "خطّہ", "ms": "Wilayah",
        "it": "Regione", "zh": "地区", "ja": "地域", "ko": "지역",
    },
    "panel.categories.cycle.toYears": {
        "en": "Switch to Time Field", "fr": "Passer à Période", "de": "Zu Zeitraum wechseln",
        "es": "Cambiar a Periodo", "tr": "Zaman Alanına Geç", "fa": "تغییر به حوزهٔ زمانی",
        "ur": "زمانی حدّ پر جائیں", "ms": "Tukar ke Tempoh",
        "it": "Passa al Periodo", "zh": "切换到时间", "ja": "時代に切替", "ko": "시기로 전환",
    },
    "panel.categories.cycle.toRegions": {
        "en": "Switch to Region", "fr": "Passer à Région", "de": "Zu Region wechseln",
        "es": "Cambiar a Región", "tr": "Bölgeye Geç", "fa": "تغییر به منطقه",
        "ur": "خطّے پر جائیں", "ms": "Tukar ke Wilayah",
        "it": "Passa alla Regione", "zh": "切换到地区", "ja": "地域に切替", "ko": "지역으로 전환",
    },
    "panel.categories.cycle.toCategories": {
        "en": "Switch to Knowledge Field", "fr": "Passer au Domaine", "de": "Zu Wissensgebiet wechseln",
        "es": "Cambiar a Campo", "tr": "Bilgi Alanına Geç", "fa": "تغییر به حوزهٔ معرفت",
        "ur": "علمی شعبے پر جائیں", "ms": "Tukar ke Bidang Ilmu",
        "it": "Passa al Campo", "zh": "切换到知识领域", "ja": "知識分野に切替", "ko": "지식 분야로 전환",
    },
    "panel.categories.cycle.tooltip": {
        "en": "Switch among Knowledge / Time / Region", "fr": "Basculer entre Domaine / Période / Région",
        "de": "Zwischen Wissen / Zeit / Region wechseln", "es": "Alternar Campo / Periodo / Región",
        "tr": "Bilgi / Zaman / Bölge geçişi", "fa": "تعویض میان معرفت / زمان / منطقه",
        "ur": "علم / زمانہ / خطّہ کے درمیان تبدیل", "ms": "Tukar antara Ilmu / Masa / Wilayah",
        "it": "Alterna Campo / Periodo / Regione", "zh": "在 知识/时间/地区 间切换",
        "ja": "知識/時代/地域 を切替", "ko": "지식/시기/지역 전환",
    },
    "panel.categories.unclassified": {
        "en": "Unclassified", "fr": "Non classé", "de": "Unklassifiziert", "es": "Sin clasificar",
        "tr": "Sınıflandırılmamış", "fa": "دسته‌بندی نشده", "ur": "غیر مصنّف", "ms": "Tidak Berkategori",
        "it": "Non classificato", "zh": "未分类", "ja": "未分類", "ko": "미분류",
    },
    "panel.books.title": {  # الكتب
        "en": "Books", "fr": "Livres", "de": "Bücher", "es": "Libros",
        "tr": "Kitaplar", "fa": "کتاب‌ها", "ur": "کتابیں", "ms": "Buku-buku",
        "it": "Libri", "zh": "书籍", "ja": "書籍", "ko": "책",
    },
    "panel.books.search.placeholder": {
        "en": "Search titles...", "fr": "Rechercher les titres...", "de": "Titel suchen...",
        "es": "Buscar títulos...", "tr": "Başlıklarda ara...", "fa": "جست‌وجو در عناوین...",
        "ur": "عنوانات میں تلاش...", "ms": "Cari tajuk...",
        "it": "Cerca titoli...", "zh": "搜索标题...", "ja": "タイトル検索...", "ko": "제목 검색...",
    },
    "panel.selectAll": {  # تحديد الكلّ
        "en": "Select All", "fr": "Tout sélectionner", "de": "Alle auswählen",
        "es": "Seleccionar todo", "tr": "Tümünü seç", "fa": "انتخاب همه",
        "ur": "سب منتخب", "ms": "Pilih Semua",
        "it": "Seleziona tutto", "zh": "全选", "ja": "すべて選択", "ko": "모두 선택",
    },
    "panel.clearAll": {  # إلغاء الكلّ
        "en": "Clear All", "fr": "Tout effacer", "de": "Alle entfernen",
        "es": "Borrar todo", "tr": "Tümünü temizle", "fa": "پاک کردن همه",
        "ur": "سب صاف", "ms": "Kosongkan Semua",
        "it": "Cancella tutto", "zh": "全部清除", "ja": "すべて解除", "ko": "모두 지움",
    },
    "panel.selectAll.books.tooltip": {
        "en": "Select all books to limit search", "fr": "Sélectionner tous les livres pour filtrer",
        "de": "Alle Bücher auswählen", "es": "Seleccionar todos los libros",
        "tr": "Aramayı kısıtlamak için tüm kitapları seç", "fa": "همهٔ کتاب‌ها برای محدود کردن جست‌وجو",
        "ur": "تلاش محدود کرنے کے لیے سب کتب", "ms": "Pilih semua untuk hadkan carian",
        "it": "Seleziona tutti per filtrare", "zh": "选择全部以限制搜索",
        "ja": "全選択で検索を限定", "ko": "검색을 모두 제한",
    },
    "panel.clearAll.books.tooltip": {
        "en": "Clear all selected books", "fr": "Désélectionner tous les livres",
        "de": "Auswahl der Bücher aufheben", "es": "Deseleccionar libros",
        "tr": "Seçili kitapları temizle", "fa": "لغو انتخاب همهٔ کتاب‌ها",
        "ur": "منتخب کتب صاف کریں", "ms": "Buang semua pilihan",
        "it": "Deseleziona tutti i libri", "zh": "清除所有选定书籍",
        "ja": "選択をすべて解除", "ko": "선택된 책 모두 해제",
    },
    "panel.selectAll.categories.tooltip": {
        "en": "Select all fields", "fr": "Sélectionner tous les domaines",
        "de": "Alle Felder", "es": "Seleccionar todos los campos",
        "tr": "Tüm alanları seç", "fa": "انتخاب همهٔ حوزه‌ها",
        "ur": "سب شعبے", "ms": "Pilih semua bidang",
        "it": "Seleziona tutti i campi", "zh": "选择所有字段",
        "ja": "すべての分野を選択", "ko": "모든 분야 선택",
    },
    "panel.clearAll.categories.tooltip": {
        "en": "Clear selected fields", "fr": "Désélectionner les domaines",
        "de": "Felder entfernen", "es": "Borrar campos",
        "tr": "Alanları temizle", "fa": "پاک کردن حوزه‌ها",
        "ur": "شعبے صاف", "ms": "Buang pilihan bidang",
        "it": "Cancella campi", "zh": "清除字段",
        "ja": "分野をクリア", "ko": "분야 해제",
    },
    "display.breadcrumb.result": {
        "en": "Result", "fr": "Résultat", "de": "Ergebnis", "es": "Resultado",
        "tr": "Sonuç", "fa": "نتیجه", "ur": "نتیجہ", "ms": "Keputusan",
        "it": "Risultato", "zh": "结果", "ja": "結果", "ko": "결과",
    },
    "display.breadcrumb.browse": {
        "en": "Browse", "fr": "Parcourir", "de": "Durchsuchen", "es": "Explorar",
        "tr": "Gezinti", "fa": "مرور", "ur": "براؤز", "ms": "Layar",
        "it": "Sfoglia", "zh": "浏览", "ja": "閲覧", "ko": "탐색",
    },
    "display.breadcrumb.page": {
        "en": "Page", "fr": "Page", "de": "Seite", "es": "Página",
        "tr": "Sayfa", "fa": "صفحه", "ur": "صفحہ", "ms": "Halaman",
        "it": "Pagina", "zh": "页", "ja": "ページ", "ko": "쪽",
    },
    "display.empty": {
        "en": "Choose a result from the table to see the full page",
        "fr": "Choisissez un résultat pour voir la page complète",
        "de": "Wählen Sie ein Ergebnis, um die ganze Seite zu sehen",
        "es": "Elija un resultado para ver la página completa",
        "tr": "Tam sayfayı görmek için bir sonuç seçin",
        "fa": "نتیجه‌ای از جدول برگزینید تا تمام صفحه نمایان شود",
        "ur": "مکمل صفحہ دیکھنے کے لیے نتیجہ منتخب کریں",
        "ms": "Pilih keputusan untuk lihat halaman penuh",
        "it": "Scegli un risultato per la pagina intera",
        "zh": "从表格选择结果以查看整页",
        "ja": "結果を選び、全ページを表示",
        "ko": "결과를 선택하면 전체 쪽을 봅니다",
    },
    "table.col.serial": {
        "en": "#", "fr": "N°", "de": "Nr.", "es": "N.º",
        "tr": "Sıra", "fa": "ردیف", "ur": "نمبر", "ms": "No.",
        "it": "N.", "zh": "序号", "ja": "番号", "ko": "번호",
    },
    "table.col.context": {
        "en": "Context", "fr": "Contexte", "de": "Kontext", "es": "Contexto",
        "tr": "Bağlam", "fa": "بافت", "ur": "سیاق", "ms": "Konteks",
        "it": "Contesto", "zh": "上下文", "ja": "文脈", "ko": "문맥",
    },
    "toolbar.prevPage": {
        "en": "Prev. Page", "fr": "Page préc.", "de": "Vor. Seite", "es": "Pág. ant.",
        "tr": "Önceki Sayfa", "fa": "صفحهٔ قبل", "ur": "پچھلا صفحہ", "ms": "Halaman Sebelum",
        "it": "Pag. prec.", "zh": "上一页", "ja": "前ページ", "ko": "이전 쪽",
    },
    "toolbar.nextPage": {
        "en": "Next Page", "fr": "Page suiv.", "de": "Näch. Seite", "es": "Pág. sig.",
        "tr": "Sonraki Sayfa", "fa": "صفحهٔ بعد", "ur": "اگلا صفحہ", "ms": "Halaman Berikut",
        "it": "Pag. succ.", "zh": "下一页", "ja": "次ページ", "ko": "다음 쪽",
    },
    "toolbar.nextGroup": {
        "en": "Next Batch", "fr": "Lot suiv.", "de": "Näch. Gruppe", "es": "Sig. lote",
        "tr": "Sonraki Grup", "fa": "گروه بعد", "ur": "اگلا گروپ", "ms": "Kumpulan Berikut",
        "it": "Lotto succ.", "zh": "下一组", "ja": "次の組", "ko": "다음 묶음",
    },
    "toolbar.fullBook": {
        "en": "Full Book", "fr": "Livre entier", "de": "Ganzes Buch", "es": "Libro completo",
        "tr": "Kitap Tümü", "fa": "تمام کتاب", "ur": "پوری کتاب", "ms": "Buku Penuh",
        "it": "Libro intero", "zh": "整本书", "ja": "本全体", "ko": "전체 책",
    },
    "toolbar.nextResult": {
        "en": "Next Result", "fr": "Résultat suiv.", "de": "Näch. Ergebnis", "es": "Sig. resultado",
        "tr": "Sonraki Sonuç", "fa": "نتیجهٔ بعد", "ur": "اگلا نتیجہ", "ms": "Keputusan Berikut",
        "it": "Risult. succ.", "zh": "下一结果", "ja": "次の結果", "ko": "다음 결과",
    },
    "toolbar.prevResult": {
        "en": "Prev. Result", "fr": "Résultat préc.", "de": "Vor. Ergebnis", "es": "Resultado ant.",
        "tr": "Önceki Sonuç", "fa": "نتیجهٔ قبل", "ur": "پچھلا نتیجہ", "ms": "Keputusan Sebelum",
        "it": "Risult. prec.", "zh": "上一结果", "ja": "前の結果", "ko": "이전 결과",
    },
    "toolbar.export": {
        "en": "Export", "fr": "Exporter", "de": "Exportieren", "es": "Exportar",
        "tr": "Dışa aktar", "fa": "خروجی", "ur": "برآمد", "ms": "Eksport",
        "it": "Esporta", "zh": "导出", "ja": "出力", "ko": "내보내기",
    },
    "settings.title": {
        "en": "Settings", "fr": "Paramètres", "de": "Einstellungen", "es": "Ajustes",
        "tr": "Ayarlar", "fa": "تنظیمات", "ur": "ترتیبات", "ms": "Tetapan",
        "it": "Impostazioni", "zh": "设置", "ja": "設定", "ko": "설정",
    },
    "settings.tooltip": {
        "en": "Settings menu", "fr": "Menu paramètres", "de": "Einstellungsmenü",
        "es": "Menú de ajustes", "tr": "Ayarlar menüsü", "fa": "منوی تنظیمات",
        "ur": "ترتیبات کا مینو", "ms": "Menu tetapan",
        "it": "Menu impostazioni", "zh": "设置菜单", "ja": "設定メニュー", "ko": "설정 메뉴",
    },
    "settings.import": {
        "en": "Import Books", "fr": "Importer des livres", "de": "Bücher importieren",
        "es": "Importar libros", "tr": "Kitap içe aktar", "fa": "وارد کردن کتاب",
        "ur": "کتب درآمد", "ms": "Import Buku",
        "it": "Importa libri", "zh": "导入书籍", "ja": "本のインポート", "ko": "책 가져오기",
    },
    "settings.delete": {
        "en": "Delete Books", "fr": "Supprimer des livres", "de": "Bücher löschen",
        "es": "Eliminar libros", "tr": "Kitap sil", "fa": "حذف کتاب",
        "ur": "کتب حذف", "ms": "Padam Buku",
        "it": "Elimina libri", "zh": "删除书籍", "ja": "本を削除", "ko": "책 삭제",
    },
    "settings.indexing": {
        "en": "Auto Indexing", "fr": "Indexation auto", "de": "Auto-Indizierung",
        "es": "Indexación auto", "tr": "Oto. dizinleme", "fa": "نمایه‌سازی خودکار",
        "ur": "خودکار اشاریہ", "ms": "Pengindeksan Auto",
        "it": "Indicizz. auto", "zh": "自动索引", "ja": "自動索引", "ko": "자동 색인",
    },
    "settings.limit": {
        "en": "Result Limit", "fr": "Limite", "de": "Limit", "es": "Límite",
        "tr": "Sonuç Sınırı", "fa": "حد نتایج", "ur": "حدِّ نتائج", "ms": "Had Keputusan",
        "it": "Limite", "zh": "结果上限", "ja": "結果の上限", "ko": "결과 한계",
    },
    "settings.limit.unlimited": {
        "en": "Unlimited", "fr": "Illimité", "de": "Unbegrenzt", "es": "Sin límite",
        "tr": "Sınırsız", "fa": "بدون حد", "ur": "لامحدود", "ms": "Tanpa Had",
        "it": "Illimitato", "zh": "无限制", "ja": "無制限", "ko": "무제한",
    },
    "settings.language": {
        "en": "Language", "fr": "Langue", "de": "Sprache", "es": "Idioma",
        "tr": "Dil", "fa": "زبان", "ur": "زبان", "ms": "Bahasa",
        "it": "Lingua", "zh": "语言", "ja": "言語", "ko": "언어",
    },
    "settings.timeMode": {
        "en": "Time Criterion", "fr": "Critère temporel", "de": "Zeitkriterium",
        "es": "Criterio temporal", "tr": "Zaman ölçütü", "fa": "ملاک زمانی",
        "ur": "زمانی معیار", "ms": "Kriteria Masa",
        "it": "Criterio temporale", "zh": "时间标准", "ja": "時間基準", "ko": "시간 기준",
    },
    "settings.stats": {
        "en": "Statistics", "fr": "Statistiques", "de": "Statistik", "es": "Estadísticas",
        "tr": "İstatistik", "fa": "آمار", "ur": "اعدادوشمار", "ms": "Statistik",
        "it": "Statistiche", "zh": "统计", "ja": "統計", "ko": "통계",
    },
    "settings.favorites": {
        "en": "Favorites", "fr": "Favoris", "de": "Favoriten", "es": "Favoritos",
        "tr": "Favoriler", "fa": "علاقه‌مندی‌ها", "ur": "پسندیدہ", "ms": "Kegemaran",
        "it": "Preferiti", "zh": "收藏", "ja": "お気に入り", "ko": "즐겨찾기",
    },
    "settings.sessions": {
        "en": "Saved Sessions", "fr": "Sessions enregistrées", "de": "Gespeicherte Sitzungen",
        "es": "Sesiones guardadas", "tr": "Kayıtlı oturumlar", "fa": "نشست‌های ذخیره‌شده",
        "ur": "محفوظ نشستیں", "ms": "Sesi Disimpan",
        "it": "Sessioni salvate", "zh": "已保存会话", "ja": "保存セッション", "ko": "저장된 세션",
    },
    "settings.theme": {
        "en": "Theme", "fr": "Thème", "de": "Design", "es": "Tema",
        "tr": "Tema", "fa": "پوسته", "ur": "تھیم", "ms": "Tema",
        "it": "Tema", "zh": "主题", "ja": "テーマ", "ko": "테마",
    },
    "settings.preferences": {
        "en": "Preferences", "fr": "Préférences", "de": "Voreinstellungen",
        "es": "Preferencias", "tr": "Tercihler", "fa": "ترجیحات",
        "ur": "ترجیحات", "ms": "Keutamaan",
        "it": "Preferenze", "zh": "首选项", "ja": "環境設定", "ko": "환경설정",
    },
    "settings.tour": {
        "en": "Welcome Tour", "fr": "Visite guidée", "de": "Einführung",
        "es": "Tour de bienvenida", "tr": "Tanıtım Turu", "fa": "تور آشنایی",
        "ur": "تعارفی دورہ", "ms": "Lawatan Pengenalan",
        "it": "Tour introduttivo", "zh": "入门导览", "ja": "ご案内", "ko": "소개 투어",
    },
    "settings.backup": {
        "en": "Backup", "fr": "Sauvegarde", "de": "Sicherung", "es": "Copia de seguridad",
        "tr": "Yedekleme", "fa": "پشتیبان‌گیری", "ur": "بیک‌اپ", "ms": "Sandaran",
        "it": "Backup", "zh": "备份", "ja": "バックアップ", "ko": "백업",
    },
    "settings.checkUpdates": {
        "en": "Check for Updates", "fr": "Vérifier les mises à jour", "de": "Updates suchen",
        "es": "Buscar actualizaciones", "tr": "Güncelleme denetle", "fa": "بررسی به‌روزرسانی",
        "ur": "اپڈیٹ چیک کریں", "ms": "Semak Kemas Kini",
        "it": "Cerca aggiornamenti", "zh": "检查更新", "ja": "更新確認", "ko": "업데이트 확인",
    },
    "settings.reportBug": {
        "en": "Report Bug", "fr": "Signaler un bug", "de": "Fehler melden",
        "es": "Reportar fallo", "tr": "Hata bildir", "fa": "گزارش خطا",
        "ur": "نقص رپورٹ", "ms": "Lapor Pepijat",
        "it": "Segnala bug", "zh": "报告错误", "ja": "不具合報告", "ko": "오류 보고",
    },
    "settings.suggestFeature": {
        "en": "Suggest Feature", "fr": "Suggérer une fonction", "de": "Funktion vorschlagen",
        "es": "Sugerir función", "tr": "Özellik öner", "fa": "پیشنهاد قابلیت",
        "ur": "خصوصیت تجویز", "ms": "Cadang Ciri",
        "it": "Suggerisci funzione", "zh": "功能建议", "ja": "機能提案", "ko": "기능 제안",
    },
    "settings.suggestBook": {
        "en": "Suggest Book", "fr": "Suggérer un livre", "de": "Buch vorschlagen",
        "es": "Sugerir libro", "tr": "Kitap öner", "fa": "پیشنهاد کتاب",
        "ur": "کتاب تجویز", "ms": "Cadang Buku",
        "it": "Suggerisci libro", "zh": "推荐书籍", "ja": "本を提案", "ko": "책 추천",
    },
    "settings.about": {
        "en": "About", "fr": "À propos", "de": "Über", "es": "Acerca de",
        "tr": "Hakkında", "fa": "درباره", "ur": "تعارف", "ms": "Tentang",
        "it": "Informazioni", "zh": "关于", "ja": "情報", "ko": "정보",
    },
    "settings.contact": {
        "en": "Contact Us", "fr": "Nous contacter", "de": "Kontakt",
        "es": "Contáctanos", "tr": "Bize ulaşın", "fa": "تماس",
        "ur": "رابطہ", "ms": "Hubungi Kami",
        "it": "Contattaci", "zh": "联系我们", "ja": "お問い合わせ", "ko": "문의",
    },
    "settings.donate": {
        "en": "Support the Project", "fr": "Soutenir le projet", "de": "Projekt unterstützen",
        "es": "Apoyar el proyecto", "tr": "Projeyi destekle", "fa": "حمایت از پروژه",
        "ur": "مشروع کی حمایت", "ms": "Sokong Projek",
        "it": "Sostieni il progetto", "zh": "支持项目", "ja": "プロジェクト支援", "ko": "프로젝트 후원",
    },
    "settings.visitSite": {
        "en": "Visit website", "fr": "Visiter le site", "de": "Webseite besuchen",
        "es": "Visitar sitio web", "tr": "Web sitesine git", "fa": "بازدید از سایت",
        "ur": "ویب سائٹ", "ms": "Lawat laman",
        "it": "Vai al sito", "zh": "访问网站", "ja": "サイト訪問", "ko": "사이트 방문",
    },
    "theme.title": {
        "en": "Choose theme", "fr": "Choisir un thème", "de": "Design wählen",
        "es": "Elegir tema", "tr": "Tema seç", "fa": "گزینش پوسته",
        "ur": "تھیم منتخب کریں", "ms": "Pilih tema",
        "it": "Scegli un tema", "zh": "选择主题", "ja": "テーマ選択", "ko": "테마 선택",
    },
    "theme.earthy": {
        "en": "Earthy (default)", "fr": "Terre (défaut)", "de": "Erdton (Standard)",
        "es": "Terroso (predet.)", "tr": "Toprak (varsayılan)", "fa": "خاکی (پیش‌فرض)",
        "ur": "مٹیلا (طے شدہ)", "ms": "Tanah (lalai)",
        "it": "Terra (predef.)", "zh": "土色（默认）", "ja": "アース（既定）", "ko": "어스(기본)",
    },
    "theme.dark": {
        "en": "Dark", "fr": "Sombre", "de": "Dunkel", "es": "Oscuro",
        "tr": "Koyu", "fa": "تیره", "ur": "گہرا", "ms": "Gelap",
        "it": "Scuro", "zh": "暗色", "ja": "ダーク", "ko": "어둡게",
    },
    "theme.gray": {
        "en": "Gray", "fr": "Gris", "de": "Grau", "es": "Gris",
        "tr": "Gri", "fa": "خاکستری", "ur": "خاکستری", "ms": "Kelabu",
        "it": "Grigio", "zh": "灰色", "ja": "グレー", "ko": "회색",
    },
    "theme.auto": {
        "en": "Auto (system)", "fr": "Auto (système)", "de": "Auto (System)",
        "es": "Auto (sistema)", "tr": "Otomatik", "fa": "خودکار (سیستم)",
        "ur": "خودکار (نظام)", "ms": "Auto (sistem)",
        "it": "Auto (sistema)", "zh": "自动（系统）", "ja": "自動（システム）", "ko": "자동(시스템)",
    },
    "limit.title": {
        "en": "Result limit", "fr": "Limite de résultats", "de": "Ergebnis-Limit",
        "es": "Límite de resultados", "tr": "Sonuç sınırı", "fa": "حد نتایج",
        "ur": "حدِّ نتائج", "ms": "Had keputusan",
        "it": "Limite risultati", "zh": "结果上限", "ja": "結果上限", "ko": "결과 한계",
    },
    "timeMode.title": {
        "en": "Time criterion", "fr": "Critère temporel", "de": "Zeitkriterium",
        "es": "Criterio temporal", "tr": "Zaman ölçütü", "fa": "ملاک زمانی",
        "ur": "زمانی معیار", "ms": "Kriteria masa",
        "it": "Criterio temporale", "zh": "时间标准", "ja": "時間基準", "ko": "시간 기준",
    },
    "timeMode.deathYear": {
        "en": "Year of death", "fr": "Année de décès", "de": "Todesjahr",
        "es": "Año de fallecimiento", "tr": "Vefat yılı", "fa": "سال وفات",
        "ur": "سن وفات", "ms": "Tahun wafat",
        "it": "Anno di morte", "zh": "卒年", "ja": "没年", "ko": "사망 연도",
    },
    "timeMode.usageDate": {
        "en": "Date of use", "fr": "Date d'usage", "de": "Verwendungsdatum",
        "es": "Fecha de uso", "tr": "Kullanım tarihi", "fa": "تاریخ استعمال",
        "ur": "تاریخِ استعمال", "ms": "Tarikh penggunaan",
        "it": "Data d'uso", "zh": "使用日期", "ja": "使用日", "ko": "사용일",
    },
    "backup.export": {
        "en": "Export library...", "fr": "Exporter la bibliothèque...", "de": "Bibliothek exportieren...",
        "es": "Exportar biblioteca...", "tr": "Kütüphaneyi dışa aktar...", "fa": "خروجی کتابخانه...",
        "ur": "لائبریری برآمد...", "ms": "Eksport pustaka...",
        "it": "Esporta biblioteca...", "zh": "导出图书馆...", "ja": "ライブラリ書出...", "ko": "라이브러리 내보내기...",
    },
    "backup.restore": {
        "en": "Restore from backup...", "fr": "Restaurer depuis sauvegarde...",
        "de": "Aus Sicherung wiederherstellen...", "es": "Restaurar desde copia...",
        "tr": "Yedekten geri yükle...", "fa": "بازیابی از پشتیبان...",
        "ur": "بیک‌اپ سے بحال...", "ms": "Pulih dari sandaran...",
        "it": "Ripristina da backup...", "zh": "从备份恢复...", "ja": "バックアップから復元...", "ko": "백업에서 복원...",
    },
    "donate.bank": {
        "en": "Bank transfer", "fr": "Virement bancaire", "de": "Banküberweisung",
        "es": "Transferencia bancaria", "tr": "Banka havalesi", "fa": "حوالهٔ بانکی",
        "ur": "بینک منتقلی", "ms": "Pemindahan bank",
        "it": "Bonifico bancario", "zh": "银行转账", "ja": "銀行振込", "ko": "은행 송금",
    },
    "common.ok": {
        "en": "OK", "fr": "OK", "de": "OK", "es": "Aceptar",
        "tr": "Tamam", "fa": "تأیید", "ur": "ٹھیک", "ms": "OK",
        "it": "OK", "zh": "好", "ja": "OK", "ko": "확인",
    },
    "common.cancel": {
        "en": "Cancel", "fr": "Annuler", "de": "Abbrechen", "es": "Cancelar",
        "tr": "İptal", "fa": "لغو", "ur": "منسوخ", "ms": "Batal",
        "it": "Annulla", "zh": "取消", "ja": "キャンセル", "ko": "취소",
    },
    "common.close": {
        "en": "Close", "fr": "Fermer", "de": "Schließen", "es": "Cerrar",
        "tr": "Kapat", "fa": "بستن", "ur": "بند", "ms": "Tutup",
        "it": "Chiudi", "zh": "关闭", "ja": "閉じる", "ko": "닫기",
    },
    "common.yes": {
        "en": "Yes", "fr": "Oui", "de": "Ja", "es": "Sí", "tr": "Evet",
        "fa": "بله", "ur": "ہاں", "ms": "Ya",
        "it": "Sì", "zh": "是", "ja": "はい", "ko": "예",
    },
    "common.no": {
        "en": "No", "fr": "Non", "de": "Nein", "es": "No", "tr": "Hayır",
        "fa": "خیر", "ur": "نہیں", "ms": "Tidak",
        "it": "No", "zh": "否", "ja": "いいえ", "ko": "아니오",
    },
    "common.error": {
        "en": "Error", "fr": "Erreur", "de": "Fehler", "es": "Error",
        "tr": "Hata", "fa": "خطا", "ur": "خرابی", "ms": "Ralat",
        "it": "Errore", "zh": "错误", "ja": "エラー", "ko": "오류",
    },
    "common.error.unknown": {
        "en": "Unknown error", "fr": "Erreur inconnue", "de": "Unbekannter Fehler",
        "es": "Error desconocido", "tr": "Bilinmeyen hata", "fa": "خطای نامعلوم",
        "ur": "نامعلوم خرابی", "ms": "Ralat tidak diketahui",
        "it": "Errore sconosciuto", "zh": "未知错误", "ja": "不明なエラー", "ko": "알 수 없는 오류",
    },
}

LANG_TO_FILE = {
    "en": "translations-english.json",
    "fr": "translations-french.json",
    "de": "translations-german.json",
    "es": "translations-spanish.json",
    "tr": "translations-turkish.json",
    "fa": "translations-persian.json",
    "ur": "translations-urdu.json",
    "ms": "translations-malay.json",
    "it": "translations-italian.json",
    "zh": "translations-chinese.json",
    "ja": "translations-japanese.json",
    "ko": "translations-korean.json",
}


def load_lang_dict(lang_code: str) -> dict:
    fp = os.path.join(PYTHON_I18N_DIR, LANG_TO_FILE[lang_code])
    if not os.path.exists(fp):
        return {}
    with open(fp, encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        return {}
    # نَبني فهرسَين: مفتاح حرفيّ + مفتاح بدون تشكيل
    out = {}
    for k, v in data.items():
        out[k] = v
        stripped = strip_tashkeel(k)
        if stripped not in out:
            out[stripped] = v
    return out


def kotlin_escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")


def translate(key: str, ar_text: str, lang_code: str, py_dict: dict) -> str:
    # 1. ترجمة يدويّة لها أولوية
    if key in MANUAL and lang_code in MANUAL[key]:
        return MANUAL[key][lang_code]
    # 2. مطابقة حرفيّة في Python
    if ar_text in py_dict:
        return py_dict[ar_text]
    # 3. مطابقة بعد إزالة التشكيل
    stripped = strip_tashkeel(ar_text)
    if stripped in py_dict:
        return py_dict[stripped]
    # 4. fallback: العربية
    return ar_text


def render_lang_block(lang_code: str, py_dict: dict) -> tuple:
    lines = [f'    private val {lang_code.upper()} = mapOf(']
    untranslated = []
    for key, ar_text in KEYS.items():
        if lang_code == "ar":
            translated = ar_text
        else:
            translated = translate(key, ar_text, lang_code, py_dict)
            if translated == ar_text:
                untranslated.append(key)
        lines.append(f'        "{key}" to "{kotlin_escape(translated)}",')
    lines.append("    )")
    return "\n".join(lines), untranslated


def main():
    out = []
    out.append(f"// Auto-generated translation maps for Strings.kt")
    out.append(f"// Total keys: {len(KEYS)}")
    out.append("")

    block, _ = render_lang_block("ar", {})
    out.append(block)
    out.append("")

    stats = []
    for code in ["en", "fr", "de", "es", "tr", "fa", "ur", "ms", "it", "zh", "ja", "ko"]:
        d = load_lang_dict(code)
        block, untrans = render_lang_block(code, d)
        out.append(block)
        out.append("")
        translated = len(KEYS) - len(untrans)
        coverage = translated * 100 // len(KEYS)
        stats.append((code, translated, coverage, untrans[:5]))

    out.append("// ─── Coverage report ───")
    for code, translated, coverage, sample in stats:
        out.append(f"// {code}: {translated}/{len(KEYS)} ({coverage}%)")

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(out))

    print(f"OK: {OUTPUT_FILE}")
    print(f"Keys: {len(KEYS)}")
    for code, translated, coverage, sample in stats:
        marker = "OK" if coverage == 100 else "!! " + ", ".join(sample[:3])
        print(f"  {code}: {translated}/{len(KEYS)} ({coverage}%) {marker}")


if __name__ == "__main__":
    main()
