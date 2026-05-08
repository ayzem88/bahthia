# المكتبة البَحثيَّة — Bahthia Library

> أَداة سَطح مَكتب لِلبَحث المُتقدِّم في النُصوص العَرَبيّة: كَلمة، جَذر، وَزن صَرفي،
> وتَعبير نَمَطي. مَبنيّة بِـ **Kotlin + Apache Lucene + Compose Desktop**.
>
> **الإِصدار الحالي**: `1.0.0` — قابِل للتَجربة، بَعد قَريباً MSI رَسميّ.

---

## ⚡ التَشغيل السَريع

انقر مَرّتَين على [`تشغيل.bat`](تشغيل.bat).

أَوّل تَشغيل: ٢-٥ دَقائق (تَحميل الاعتماديّات).
التَشغيلات اللاحِقة: ٥-١٠ ثَوانٍ.

> يَحتاج إلى **JDK 21** (Eclipse Temurin) في مَسار ASCII فَقط.
> ```powershell
> winget install EclipseAdoptium.Temurin.21.JDK
> ```

---

## 🔍 المَيّزات

| الميزة | الوَصف |
|---|---|
| **٤ أَنواع بَحث** | كَلمة، جَذر (سـألتمونيها)، وَزن صَرفي، تَعبير نَمطي |
| **خَيارات** | مُراعاة التَشكيل، مُطابقة الحُروف، تَقارب `+`، احتمال `\|` |
| **فَلاتر** | الحَقل المعرفيّ، الحَقل الزَّمنيّ (سَنة الوَفاة / تاريخ الاستِعمال)، الحَقل الجُغرافي، الكُتب |
| **استيراد** | PDF (Mistral OCR) + DOCX + TXT + ترحيل من SQLite |
| **التَّصدير** | TXT + DOCX + CSV |
| **المفضّلة** | حِفظ جَلسات بَحث كامِلة (٥٠ جَلسة) واسترجاعها لاحِقاً |
| **النَسخ الاحتياطي** | تَصدير + استعادة الفَهرس وتَفضيلات المُستخدِم |

---

## 🏗️ البِنية

سَبعة موديولات Gradle:

```
modules/
├── domain/        — نماذج (Book, Page, SearchResult, TimeMode)
├── search/        — Lucene + التَطبيع العَرَبي + الجَذر + الوَزن + Highlighter
├── persistence/   — احتياطي (غير مُستعمَل حالياً)
├── importer/      — استيراد PDF/TXT/DOCX + ترحيل SQLite
├── lifecycle/     — UserPreferences, AutoUpdater, BackupManager,
│                    CrashReporter, TelemetryService
├── i18n/          — ترجمات (١٣ لُغة معدَّة)
└── app-desktop/   — واجِهة Compose Desktop (entrypoint: com.bahthia.app.MainKt)
```

---

## 🛠️ البِناء والاختبار

```powershell
# اختبارات كاملة
.\gradlew.bat test

# تَجميع
.\gradlew.bat build

# تَشغيل التطبيق
.\gradlew.bat :app-desktop:run

# تَوليد MSI (Windows)
.\gradlew.bat :app-desktop:packageMsi
```

> اختبارات ١٦١ نَشِطة الآن (Kotlin + Python). انظر `CHANGELOG.md` للتَفاصيل.

---

## 📂 مَسارات بَيانات وقت التَشغيل

| المَسار | المُحتَوى |
|---|---|
| `%APPDATA%\Bahthia\lucene-index\` | فَهرس Lucene |
| `%APPDATA%\Bahthia\preferences.properties` | التفضيلات |
| `%APPDATA%\Bahthia\favorites\` | جَلسات البَحث المحفوظة (٥٠ كحدّ أَقصى) |
| `%APPDATA%\Bahthia\telemetry.json` | إحصاءات استخدام مَحلّيّة |
| `%APPDATA%\Bahthia\crashes\` | سَجلّات الأَعطال (آخر ٢٠) |
| `%USERPROFILE%\Documents\Bahthia Backups\` | النَسخ الاحتياطية |

---

## 📜 السِّجلّ

سَجلّ التَغييرات الكامل في [`CHANGELOG.md`](CHANGELOG.md).
خُطّة الإطلاق التَفصيليّة في [`خطة_الإطلاق_1.0.md`](خطة_الإطلاق_1.0.md).

---

## ✍️ المُطوّر

**أَيمن الطيّب بن نجي** — `aymen.nji@gmail.com` — [bahthia.com](https://www.bahthia.com)

> هذا العَمل خالِصٌ لِوَجه الله تَعالى. أَسأله أن يَنفع به وأن يَجعله صَدقةً جاريةً
> عن والِديَّ. دُعاؤكم لي ولِوالِديَّ بِظَهر الغَيب أَعظم مُكافأة. 🌿
