package com.bahthia.app.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.bahthia.app.state.SearchViewModel
import com.bahthia.app.ui.components.BahthiaToggleButton
import com.bahthia.app.ui.components.BahthiaToggleGroup
import com.bahthia.app.ui.components.BahthiaTooltip
import com.bahthia.app.ui.components.TogglePosition
import com.bahthia.app.ui.components.togglePositionOf
import com.bahthia.app.ui.theme.LocalBahthiaColors
import com.bahthia.domain.SearchMode
import com.bahthia.i18n.tr

/**
 * شريط البحث — مطابق ١٠٠٪ لـ Python `SearchPanel.create_search_bar`.
 *
 * البنية:
 *  - GroupBox بإطار ترابي #a0826d وخلفية #f9f9f9
 *  - الصف الأوّل: حقل البحث + زرّ "ابحث" + زرّ "امسح"
 *  - الصف الثاني: 4 RadioButtons (كلمة، جذر، وزن، تعبير نمطي) + 2 CheckBox
 *                  + (اختياريّاً) أيقونة الإعدادات في أقصى نهاية السطر — مباشرة تحت زرّ "امسح"
 *
 * @param settingsMenu  composable slot يُعرَض كآخر عنصر في الصف الثاني (يُحاذي زرّ "امسح" أعلاه).
 *                      مرّر `null` لإخفائه.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    viewModel: SearchViewModel,
    settingsMenu: (@Composable () -> Unit)? = null,
) {
    val colors = LocalBahthiaColors.current
    val focusRequester = remember { FocusRequester() }

    // Ctrl+F → ينقل التركيز لشريط البحث
    LaunchedEffect(viewModel.focusSearchSignal) {
        if (viewModel.focusSearchSignal > 0) {
            try { focusRequester.requestFocus() } catch (_: Throwable) { /* ignore */ }
        }
    }

    Surface(
        shape = RoundedCornerShape(5.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
            // ─── الصف الأول: حقل البحث + ابحث + امسح ───
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.bahthia.app.ui.components.BahthiaSearchField(
                    value = viewModel.query,
                    onValueChange = { viewModel.query = it },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    onSearchAction = { viewModel.runSearch() },
                )

                Spacer(Modifier.width(5.dp))

                BahthiaTooltip(text = "البحث عن المطلوب") {
                    BrownButton(text = tr("search.button.search"), onClick = { viewModel.runSearch() })
                }

                Spacer(Modifier.width(5.dp))

                BahthiaTooltip(text = "مسح المبحوث عنه") {
                    BrownButton(text = tr("search.button.clear"), onClick = { viewModel.reset() })
                }
            }

            Spacer(Modifier.height(5.dp))

            // ─── الصف الثاني: أنواع البحث + الخيارات + (أيقونة الإعدادات) ───
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // SegmentedButton — مجموعة موحَّدة بحدود متّصلة (Material 3)
                data class ModeSpec(val mode: SearchMode, val label: String, val tip: String)
                val modes = listOf(
                    ModeSpec(SearchMode.WORD,        tr("search.mode.word"),
                        "البحث عن كلمة مفردة؛ أو جملة مطابقة (مع تجاهل علامات الترقيم)؛ ادخِل | بين كلمتَين للتقارب، أو + بين كلمتَين للبحث في نفس الصفحة"),
                    ModeSpec(SearchMode.DERIVATIVES, tr("search.mode.derivatives"),
                        "البحث عن كلّ مشتقّات الجذر"),
                    ModeSpec(SearchMode.PATTERN,     tr("search.mode.pattern"),
                        "البحث بالوزن (مفعول، فاعل، استفعال...)"),
                    ModeSpec(SearchMode.REGEX,       tr("search.mode.regex"),
                        "بحث متقدّم بـ regex للمستخدم الخبير"),
                )
                // مجموعة أنواع البحث — كلّ زرّ بحجم نصّه الطبيعي
                // هامش داخليّ أكبر قليلاً ليَتَّسع للخطّ 14sp بأمان
                val togglePadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 14.dp, vertical = 10.dp,
                )

                BahthiaToggleGroup {
                    modes.forEachIndexed { i, spec ->
                        BahthiaToggleButton(
                            selected = viewModel.mode == spec.mode,
                            onClick  = { viewModel.mode = spec.mode },
                            position = togglePositionOf(i, modes.size),
                            contentPadding = togglePadding,
                        ) {
                            BahthiaTooltip(text = spec.tip) {
                                Text(
                                    text = spec.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    softWrap = false,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // مجموعة الخيارات المنطقيّة — بنفس إطار أنواع البحث
                BahthiaToggleGroup {
                    BahthiaToggleButton(
                        selected = viewModel.respectDiacritics,
                        onClick  = { viewModel.respectDiacritics = !viewModel.respectDiacritics },
                        position = TogglePosition.FIRST,
                        contentPadding = togglePadding,
                    ) {
                        BahthiaTooltip(text = "يُطابق فقط النصّ الذي يَحمل التشكيل المكتوب") {
                            Text(
                                text = tr("search.option.diacritics"),
                                style = MaterialTheme.typography.bodyMedium,
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                    BahthiaToggleButton(
                        selected = viewModel.matchWholeLetters,
                        onClick  = { viewModel.matchWholeLetters = !viewModel.matchWholeLetters },
                        position = TogglePosition.LAST,
                        contentPadding = togglePadding,
                    ) {
                        BahthiaTooltip(text = "يفيد المطابقة الصارمة للحروف المكتوبة") {
                            Text(
                                text = tr("search.option.whole_letters"),
                                style = MaterialTheme.typography.bodyMedium,
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // أيقونة الإعدادات في نهاية السطر
                if (settingsMenu != null) {
                    Spacer(Modifier.weight(1f))
                    settingsMenu()
                }
            }
        }
    }
}

/** زرّ ترابي بنفس تنسيق Python QPushButton. */
@Composable
private fun BrownButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = androidx.compose.ui.graphics.Color.White,
        ),
        shape = RoundedCornerShape(3.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

