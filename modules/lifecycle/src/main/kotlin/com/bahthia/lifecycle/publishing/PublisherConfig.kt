package com.bahthia.lifecycle.publishing

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * إعدادات النشر للمطوّر — تُقرأ من ملفّ خارج repo Git.
 *
 * **المسار**: `~/.bahthia/publisher.properties`  (Windows: `%USERPROFILE%\.bahthia\publisher.properties`)
 *
 * **مثال للملفّ**:
 * ```properties
 * cpanel.host=server168.web-hosting.com
 * cpanel.port=2083
 * cpanel.user=aymannji
 * cpanel.token=YOUR_API_TOKEN_HERE
 * cpanel.remote.dir=/home/aymannji/public_html
 * site.base.url=https://www.bahthia.com
 * ```
 *
 * **لماذا خارج repo؟**
 *   - رمز API يَمنح وصولاً كاملاً للخادم
 *   - إن دخل repo عام، يقدر أيّ شخص يَرفع/يَحذف ملفّاتك
 *   - الأفضل: يُحفَظ في home المستخدم، صلاحيّاته `600` (المالك فقط)
 *
 * يُعتبر `isAvailable() == false` إن لم يوجد الملفّ — لا يُفسد التطبيق
 * بل يَكشف وضع المطوّر فقط لمن عنده الإعدادات.
 */
class PublisherConfig internal constructor(
    val cpanelHost: String,
    val cpanelPort: Int,
    val cpanelUser: String,
    val cpanelToken: String,
    val remoteDir: String,
    val siteBaseUrl: String,
) {

    /** يتحقّق من ملء كلّ الحقول الإلزاميّة. */
    val isComplete: Boolean
        get() = cpanelHost.isNotBlank() && cpanelUser.isNotBlank() &&
                cpanelToken.isNotBlank() && remoteDir.isNotBlank()

    companion object {
        private val logger = LoggerFactory.getLogger(PublisherConfig::class.java)

        /** المسار الافتراضيّ (`~/.bahthia/publisher.properties`). */
        val defaultPath: Path = Path.of(System.getProperty("user.home"), ".bahthia", "publisher.properties")

        /**
         * يَقرأ الإعدادات من [path]. إن لم يوجد الملفّ، يُرجع `null`.
         * @return الإعدادات أو null إن كان الملفّ ناقصاً/مفقوداً
         */
        fun loadOrNull(path: Path = defaultPath): PublisherConfig? {
            if (!Files.exists(path)) {
                logger.debug("Publisher config not found at {} — developer mode disabled.", path)
                return null
            }
            return try {
                val props = Properties()
                Files.newInputStream(path).use { props.load(it) }
                PublisherConfig(
                    cpanelHost = props.getProperty("cpanel.host", "").trim(),
                    cpanelPort = props.getProperty("cpanel.port", "2083").trim().toIntOrNull() ?: 2083,
                    cpanelUser = props.getProperty("cpanel.user", "").trim(),
                    cpanelToken = props.getProperty("cpanel.token", "").trim(),
                    remoteDir = props.getProperty("cpanel.remote.dir", "").trim(),
                    siteBaseUrl = props.getProperty("site.base.url", "https://www.bahthia.com").trim(),
                ).takeIf { it.isComplete }
            } catch (e: Exception) {
                logger.warn("Failed to read publisher config: {}", e.message)
                null
            }
        }

        /** يَكتب قالب فارغ في [path] لتسهيل الإعداد للمطوّر الجديد. */
        fun writeTemplate(path: Path = defaultPath) {
            Files.createDirectories(path.parent)
            val content = """
                # إعدادات نشر المكتبة البحثيّة — لا تَرفع هذا الملفّ إلى Git!
                # المسار الافتراضيّ: ~/.bahthia/publisher.properties

                # خادم cPanel
                cpanel.host=server168.web-hosting.com
                cpanel.port=2083
                cpanel.user=YOUR_CPANEL_USERNAME
                cpanel.token=YOUR_API_TOKEN_HERE

                # مسار جذر الموقع على الخادم
                cpanel.remote.dir=/home/YOUR_USERNAME/public_html

                # العنوان العامّ للموقع (لروابط التحميل)
                site.base.url=https://www.bahthia.com
            """.trimIndent()
            Files.writeString(path, content)
        }
    }
}
