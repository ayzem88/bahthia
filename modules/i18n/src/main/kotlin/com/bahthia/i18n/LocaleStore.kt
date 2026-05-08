package com.bahthia.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * مخزن اللغة الحاليّة كحالة Compose قابلة للمراقبة.
 *
 * عند استدعاء `tr(key)` من داخل دالّة Composable، تَتسجّل قراءة الـ State
 * في snapshot Compose، فحين يَستدعي [set] لاحقاً تُعاد التركيب تلقائياً.
 */
object LocaleStore {

    private var _current by mutableStateOf(Locale.DEFAULT)

    /** اللغة الحاليّة كقيمة (للقراءة من Composable أو غيرها). */
    val current: Locale get() = _current

    /** نُبقي هذه الدالّة لاحقاً للتوافق مع الكود القديم. */
    fun current(): Locale = _current

    /** يُحدِّث اللغة الحاليّة ويُرجع القديمة. الواجهة تُعاد تركيبها تلقائياً. */
    fun set(locale: Locale): Locale {
        val old = _current
        _current = locale
        return old
    }

    /** يُعيد إلى الافتراض (للاختبار). */
    fun reset() {
        _current = Locale.DEFAULT
    }
}

/**
 * اختصار للحصول على نصّ مترجَم باللغة الحاليّة.
 *
 * الاستعمال:
 * ```kotlin
 * Text(tr("search.button.search"))
 * ```
 */
fun tr(key: String): String = Strings.get(LocaleStore.current(), key)

/** نسخة تأخذ لغة صريحة — مفيدة في الاختبار. */
fun tr(locale: Locale, key: String): String = Strings.get(locale, key)
