#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
==========================================================
أداة وسم أرقام الصفحات في ملفات OCR النصية
==========================================================

الفكرة:
    تمر على ملفات .txt في مجلد محدد (مع المجلدات الفرعية)،
    تكتشف الأسطر التي تحتوي على رقم منفرد فقط،
    تتعرّف على "سلسلة الترقيم" (أرقام تتزايد مع السماح بفجوات صغيرة)،
    ثم تستبدل الأسطر المنتمية للسلسلة بالشكل:
        ========== 13 ==========

الضوابط:
    1) نوعا المرشّحين:
       - صارم: السطر = رقم فقط → يُستبدل السطر كلّياً بسطر العلامة.
       - متساهل: الرقم على حافة السطر فقط (بداية أو نهاية) ومعه نص على
         الجانب الآخر → يُدرَج سطر علامة فوق السطر الأصلي مع إبقائه كما هو.
       - الأرقام في وسط السطر (نص قبلها ونص بعدها) → تُرفض دائماً (متن).
    2) الفرق بين كل رقمين متتاليين في السلسلة بين 1 و MAX_GAP (افتراضياً 5).
    3) لا تُعتبر سلسلة صحيحة إلا إذا كان طولها ≥ MIN_SEQUENCE_LENGTH (افتراضياً 3).
    4) الأرقام العربية الهندية (٠-٩) تُحوَّل إلى لاتينية (0-9) في علامة الترقيم.
    5) أي مرشّح لا يلائم أي سلسلة → يُتجاهل (يُترك السطر كما هو).
    6) ترتيب الأزواج المتلاصقة: عند كشف رقمين صارمين متجاورين بترتيب عكسي
       (مثل صفحات OCR للوجه/الوجه)، يُعاد ترتيبهما تصاعدياً تلقائياً
       (يُمكن تعطيل ذلك بـ REORDER_ADJACENT = False).

الخوارزمية:
    برمجة ديناميكية للعثور على أطول سلسلة صالحة، ثم تكرار العملية
    لاكتشاف سلاسل متعددة (مثل ترقيم مقدمة منفصل عن ترقيم متن).

المخرجات:
    - يُعدَّل كل ملف في مكانه مع حفظ نسخة احتياطية بامتداد .bak
    - يُكتب تقرير شامل بجانب السكربت باسم: تقرير_الترقيم.txt
"""

import os
import re
import sys
import shutil
from pathlib import Path
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor, as_completed

# ضمان طباعة بترميز UTF-8 على Windows (cmd) لتفادي أخطاء عند طباعة عربي
try:
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')
except Exception:
    pass


# ==================== الإعدادات ====================
FOLDER_PATH = r"I:\04 الكتب المصورة\02 المعجم والمعجمية - نصوص"  # المجلد المراد معالجته
MIN_SEQUENCE_LENGTH = 3                          # أقل عدد أرقام لاعتبار سلسلة
MAX_GAP = 4                                      # أكبر فرق مسموح بين رقمين متتاليين
EQUALS_COUNT = 25                                # عدد علامات = على كل جانب
MAKE_BACKUP = False                               # إنشاء نسخة احتياطية .bak
PARALLEL_WORKERS = 8                             # عدد الملفات تُعالَج بالتوازي
REORDER_ADJACENT = True                          # ترتيب الأرقام المتلاصقة تصاعدياً (لمعالجة OCR للصفحات المتقابلة)
REJECT_LIST_ITEMS = True                         # رفض الأسطر التي تبدأ ببنود قوائم: "٢ - نص"، "(1) نص"، "1. نص"
ONLY_DISJOINT_SECONDARY_CHAINS = True            # السلاسل الثانوية تُقبل فقط إن كان نطاق أسطرها خارج نطاق السلسلة الرئيسية
REPORT_NAME = "تقرير_الترقيم.txt"                 # اسم ملف التقرير
# ===================================================


# جدول تحويل الأرقام العربية الهندية إلى لاتينية
AR_DIGITS = "٠١٢٣٤٥٦٧٨٩"
AR_TO_EN = str.maketrans(AR_DIGITS, "0123456789")

# أنماط الكشف عن المرشّحين:
# (أ) صارم: السطر يحوي الرقم فقط (لا شيء غيره) — ثقة عالية.
STRICT_NUMBER_LINE_RE = re.compile(r'^\s*([0-9٠-٩]+)\s*$')
# (ب) متساهل: الرقم في بداية السطر يليه نص (مثل: ٤٠ شرح ديباجة القاموس).
LOOSE_START_RE = re.compile(r'^\s*([0-9٠-٩]+)(?=\s+\S)')
# (ج) متساهل: الرقم في نهاية السطر يسبقه نص (مثل: شرح ديباجة القاموس ٤٠).
LOOSE_END_RE = re.compile(r'(?<=\S)\s+([0-9٠-٩]+)\s*$')
# (د) صارم مُزخرف: الرقم محاطاً بزخرفة فقط (لا نص ولا أحرف).
# أمثلة: "- ٢٢٩ -"، "[229]"، "(229)"، "* 229 *"، "—229—"، ". 229 .".
DECORATED_NUMBER_LINE_RE = re.compile(
    r'^\s*'
    r'[-–—*\.\[\(]+\s*'         # زخرفة قبل الرقم
    r'([0-9٠-٩]+)'              # الرقم
    r'\s*[-–—*\.\]\)]+\s*$'     # زخرفة بعد الرقم حتى نهاية السطر
)
# الأرقام في وسط السطر (نص قبلها وبعدها) لا تتطابق مع أيٍ من الأنماط أعلاه → تُرفض.

# نمط بنود القوائم/التعدادات: رقم في بداية السطر متبوع بفاصل قائمة + نص.
# يُرفض هذا النمط لأنه ترقيم نقاط/أفكار وليس ترقيم صفحات.
# أمثلة المطابقة: "٢ - نشأته"، "1. text"، "1) text"، "(1) text".
# لا يطابق: "٤٠ شرح ديباجة القاموس" (لا فاصل، فقط مسافة).
LIST_ITEM_RE = re.compile(
    r'^\s*'
    r'(?:'
    r'[\(\[]\s*[0-9٠-٩]+\s*[\)\]]'   # ‏(1) أو [1]
    r'|[0-9٠-٩]+\s*[-–—]'            # ‏1- أو ‏1 -
    r'|[0-9٠-٩]+\s*\.'               # ‏1.
    r'|[0-9٠-٩]+\s*[:،]'             # ‏1: أو ‏1،
    r')\s+\S'
)

# نمط الكشف عن سطر مَوسوم سابقاً (لتجنب المعالجة المكررة)
ALREADY_MARKED_RE = re.compile(r'^=+\s*[0-9٠-٩]+\s*=+\s*$')


# ---------- استكشاف الملفات وقراءتها ----------

def find_txt_files(folder: Path):
    """أعد قائمة بجميع ملفات .txt في المجلد المعطى وفروعه (مرتبة)."""
    return sorted(folder.rglob("*.txt"))


def read_file_text(path: Path):
    """
    قراءة الملف بترميزات شائعة حتى ينجح أحدها.
    تُعاد البيانات المقروءة والترميز المستخدم.
    """
    raw = path.read_bytes()
    encodings_to_try = [
        'utf-8-sig',   # UTF-8 مع BOM
        'utf-8',
        'cp1256',      # Windows Arabic
        'utf-16',
        'utf-16-le',
        'utf-16-be',
        'iso-8859-6',  # Arabic
    ]
    for enc in encodings_to_try:
        try:
            return raw.decode(enc), enc
        except UnicodeDecodeError:
            continue
    return None, None


def is_already_processed(content: str) -> bool:
    """تحقق إن كان الملف قد عُولج سابقاً (يحوي أسطر بعلامة ========)."""
    for line in content.splitlines():
        if ALREADY_MARKED_RE.match(line):
            return True
    return False


# ---------- اكتشاف المرشّحين ----------

def find_candidates(content: str):
    """
    استخراج الأسطر المرشّحة. كل مرشّح إمّا صارم أو متساهل:
    - صارم: السطر = رقم فقط، يُعالج بالاستبدال الكامل للسطر بالعلامة.
    - متساهل: الرقم على حافة السطر فقط (بداية أو نهاية)، يُعالج بإدراج
      سطر علامة قبله مع إبقاء السطر الأصلي.
    أرقام في وسط السطر (نص قبلها ونص بعدها) → مرفوضة دائماً.
    تُعاد قائمة المرشّحين + قائمة الأسطر بنهاياتها الأصلية.
    """
    parts = content.splitlines(keepends=True)
    candidates = []
    for i, part in enumerate(parts):
        line_text = part.rstrip('\r\n')
        ending = part[len(line_text):]   # \n أو \r\n أو ''

        # 0) رفض بنود القوائم (ترقيم نقاط/أفكار للمؤلف، ليس صفحات)
        if REJECT_LIST_ITEMS and LIST_ITEM_RE.match(line_text):
            continue

        # 1) صارم
        m = STRICT_NUMBER_LINE_RE.match(line_text)
        if m:
            num_str = m.group(1)
            try:
                num_value = int(num_str.translate(AR_TO_EN))
                candidates.append({
                    "line_idx": i,
                    "num_str": num_str,
                    "num": num_value,
                    "ending": ending,
                    "type": "strict",
                })
            except ValueError:
                pass
            continue

        # 1.5) صارم مُزخرف (مثل: - ٢٢٩ -)
        m = DECORATED_NUMBER_LINE_RE.match(line_text)
        if m:
            num_str = m.group(1)
            try:
                num_value = int(num_str.translate(AR_TO_EN))
                candidates.append({
                    "line_idx": i,
                    "num_str": num_str,
                    "num": num_value,
                    "ending": ending,
                    "type": "strict",
                })
            except ValueError:
                pass
            continue

        # 2) متساهل في البداية
        m = LOOSE_START_RE.match(line_text)
        if m:
            num_str = m.group(1)
            try:
                num_value = int(num_str.translate(AR_TO_EN))
                candidates.append({
                    "line_idx": i,
                    "num_str": num_str,
                    "num": num_value,
                    "ending": ending,
                    "type": "loose",
                    "position": "start",
                })
                continue
            except ValueError:
                pass

        # 3) متساهل في النهاية
        m = LOOSE_END_RE.search(line_text)
        if m:
            num_str = m.group(1)
            try:
                num_value = int(num_str.translate(AR_TO_EN))
                candidates.append({
                    "line_idx": i,
                    "num_str": num_str,
                    "num": num_value,
                    "ending": ending,
                    "type": "loose",
                    "position": "end",
                })
            except ValueError:
                pass

    return candidates, parts


# ---------- كشف السلسلة بالبرمجة الديناميكية ----------

def find_all_chains(candidates,
                    min_len: int = MIN_SEQUENCE_LENGTH,
                    max_gap: int = MAX_GAP):
    """
    تجد جميع السلاسل الصالحة من المرشّحين عبر برمجة ديناميكية متعدّدة الجولات.
    تُعاد قائمة بكل سلسلة (كل سلسلة قائمة بفهارس داخل candidates).
    """
    chains = []
    if not candidates:
        return chains

    available = list(range(len(candidates)))

    while available:
        n = len(available)
        dp = [1] * n
        parent = [-1] * n
        best_for_value = {}  # قيمة → (أفضل dp, موقع في available)

        for i in range(n):
            num_i = candidates[available[i]]["num"]
            best_dp_pred = 0
            best_pred = -1
            for delta in range(1, max_gap + 1):
                entry = best_for_value.get(num_i - delta)
                if entry and entry[0] > best_dp_pred:
                    best_dp_pred = entry[0]
                    best_pred = entry[1]

            if best_pred != -1:
                dp[i] = best_dp_pred + 1
                parent[i] = best_pred

            cur = best_for_value.get(num_i)
            if cur is None or dp[i] > cur[0]:
                best_for_value[num_i] = (dp[i], i)

        best_i = max(range(n), key=lambda i: dp[i])
        if dp[best_i] < min_len:
            break

        chain = []
        p = best_i
        while p != -1:
            chain.append(available[p])
            p = parent[p]
        chain.reverse()
        chains.append(chain)

        used = set(chain)
        available = [a for a in available if a not in used]

    return chains


def filter_chains_by_line_range(chains, candidates):
    """
    احتفظ بالسلسلة الرئيسية (الأطول) + كل سلسلة ثانوية يكون نطاق أسطرها
    خارج نطاق أسطر السلسلة الرئيسية كلياً (قبلها أو بعدها).
    السلاسل الثانوية المتداخلة في وسط السلسلة الرئيسية تُرفض كضوضاء.
    """
    if not chains:
        return set()

    chains_sorted = sorted(chains, key=len, reverse=True)
    primary = chains_sorted[0]
    primary_lines = [candidates[ci]["line_idx"] for ci in primary]
    p_min, p_max = min(primary_lines), max(primary_lines)

    accepted = set(primary)

    for chain in chains_sorted[1:]:
        chain_lines = [candidates[ci]["line_idx"] for ci in chain]
        c_min, c_max = min(chain_lines), max(chain_lines)
        # تُقبل فقط إن كانت أسطرها كلها قبل أو كلها بعد السلسلة الرئيسية
        if c_max < p_min or c_min > p_max:
            accepted.update(chain)
        # غير ذلك: متداخلة → تُرفض

    return accepted


def find_sequence_indices(candidates,
                          min_len: int = MIN_SEQUENCE_LENGTH,
                          max_gap: int = MAX_GAP):
    """
    تجد كل السلاسل الصالحة، ثم تُرشّحها بفلتر نطاق الأسطر.
    ترجع: مجموعة بفهارس المرشّحين المقبولين نهائياً.
    """
    chains = find_all_chains(candidates, min_len=min_len, max_gap=max_gap)
    if ONLY_DISJOINT_SECONDARY_CHAINS:
        return filter_chains_by_line_range(chains, candidates)
    # سلوك قديم: قبول كل السلاسل
    accepted = set()
    for chain in chains:
        accepted.update(chain)
    return accepted


# ---------- بناء السطر الموسوم ----------

def mark_number(num_value: int) -> str:
    """صياغة سطر الترقيم: ========== 13 =========="""
    eq = "=" * EQUALS_COUNT
    return f"{eq} {num_value} {eq}"


def detect_newline(content: str) -> str:
    """يكتشف نمط نهاية السطر السائد في الملف."""
    if "\r\n" in content:
        return "\r\n"
    if "\r" in content and "\n" not in content:
        return "\r"
    return "\n"


def reorder_strict_adjacent(actions: dict, parts: list) -> None:
    """
    داخل كل مجموعة من المرشّحين الصارمين المتلاصقين (مفصولين بأسطر فارغة فقط)،
    رتّب الأرقام تصاعدياً وأعِد توزيعها على نفس مواقع الأسطر الأصلية.
    يُعدّل actions في مكانه. يُطبَّق فقط على الصارم (لأن المتساهل يحتفظ بالنص).
    """
    sorted_indices = sorted(idx for idx, a in actions.items() if a["type"] == "strict")
    i = 0
    while i < len(sorted_indices):
        group = [sorted_indices[i]]
        j = i + 1
        while j < len(sorted_indices):
            prev_idx = group[-1]
            cur_idx = sorted_indices[j]
            # كل الأسطر بين prev_idx و cur_idx يجب أن تكون فارغة
            all_blank = all(parts[k].strip() == "" for k in range(prev_idx + 1, cur_idx))
            if all_blank:
                group.append(cur_idx)
                j += 1
            else:
                break

        if len(group) > 1:
            sorted_nums = sorted(actions[idx]["num"] for idx in group)
            for k, idx in enumerate(group):
                actions[idx]["num"] = sorted_nums[k]

        i = j if j > i + 1 else i + 1


def build_marked_content(parts: list, candidates: list,
                         accepted_set: set, content: str) -> str:
    """
    يبني المحتوى بعد الوسم:
    - مرشّح صارم: يَستبدل السطر بالعلامة.
    - مرشّح متساهل: يُدرج سطر علامة قبل السطر الأصلي ويُبقي السطر كما هو.
    """
    nl = detect_newline(content)

    # خريطة: فهرس السطر الأصلي → معلومات الإجراء
    actions = {}
    for ci in accepted_set:
        c = candidates[ci]
        actions[c["line_idx"]] = {
            "type": c["type"],
            "num": c["num"],
            "ending": c["ending"],
        }

    if REORDER_ADJACENT:
        reorder_strict_adjacent(actions, parts)

    new_lines = []
    for i, part in enumerate(parts):
        if i in actions:
            act = actions[i]
            if act["type"] == "strict":
                new_lines.append(mark_number(act["num"]) + act["ending"])
            else:
                # المتساهل: علامة فوق السطر، ثم السطر الأصلي كما هو
                marker_ending = act["ending"] if act["ending"] else nl
                new_lines.append(mark_number(act["num"]) + marker_ending)
                new_lines.append(part)
        else:
            new_lines.append(part)

    return "".join(new_lines)


# ---------- معالجة ملف واحد ----------

def process_file(path: Path) -> dict:
    """
    معالجة ملف واحد. يُعاد قاموس بمعلومات الملف للتقرير.
    """
    info = {
        "file": str(path),
        "ok": True,
        "marked_count": 0,
        "candidates_count": 0,
    }

    content, encoding = read_file_text(path)
    if content is None:
        info["ok"] = False
        info["error"] = "فشل قراءة الملف بأي ترميز معروف"
        return info

    info["encoding"] = encoding

    if is_already_processed(content):
        info["status"] = "already_processed"
        return info

    candidates, parts = find_candidates(content)
    info["candidates_count"] = len(candidates)

    if not candidates:
        return info

    accepted = find_sequence_indices(candidates)
    info["marked_count"] = len(accepted)

    if not accepted:
        return info

    accepted_nums = sorted(candidates[i]["num"] for i in accepted)
    info["first_number"] = accepted_nums[0]
    info["last_number"] = accepted_nums[-1]

    # عدّ المرشّحين بحسب النوع
    info["strict_marked"] = sum(1 for ci in accepted if candidates[ci]["type"] == "strict")
    info["loose_marked"] = sum(1 for ci in accepted if candidates[ci]["type"] == "loose")

    # بناء المحتوى الجديد:
    #  - الصارم: استبدال السطر بالعلامة (الرقم في المخرج لاتيني دائماً).
    #  - المتساهل: إدراج سطر علامة قبل السطر الأصلي مع إبقائه.
    new_content = build_marked_content(parts, candidates, accepted, content)

    # نسخة احتياطية
    if MAKE_BACKUP:
        bak_path = Path(str(path) + ".bak")
        if not bak_path.exists():
            try:
                shutil.copy2(path, bak_path)
                info["backup"] = str(bak_path)
            except Exception as e:
                info["backup_error"] = f"تعذّر إنشاء النسخة الاحتياطية: {e}"

    # حفظ الملف بالترميز نفسه قدر الإمكان
    save_encoding = encoding or 'utf-8'
    try:
        if save_encoding == 'utf-8-sig':
            path.write_bytes(b'\xef\xbb\xbf' + new_content.encode('utf-8'))
        else:
            path.write_text(new_content, encoding=save_encoding)
        info["saved_encoding"] = save_encoding
    except (UnicodeEncodeError, LookupError) as e:
        # في حال فشل الترميز الأصلي، نحفظ بـ UTF-8
        path.write_text(new_content, encoding='utf-8')
        info["saved_encoding"] = "utf-8 (fallback)"
        info["save_warning"] = str(e)

    return info


# ---------- التقرير ----------

def write_report(report_path: Path, folder: Path, results: list):
    """كتابة تقرير مفصّل بجانب السكربت."""
    no_pages = [r for r in results
                if r.get("ok", True)
                and r.get("status") != "already_processed"
                and r.get("marked_count", 0) == 0]
    has_pages = [r for r in results if r.get("marked_count", 0) > 0]
    already = [r for r in results if r.get("status") == "already_processed"]
    errors = [r for r in results if not r.get("ok", True)]

    sep = "=" * 60
    sub = "-" * 60

    lines = []
    lines.append(sep)
    lines.append("تقرير اكتشاف ووسم أرقام الصفحات")
    lines.append(sep)
    lines.append(f"التاريخ: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"المجلد المعالج: {folder}")
    lines.append(f"إجمالي الملفات: {len(results)}")
    lines.append(f"  - تم وسم ترقيم فيها: {len(has_pages)}")
    lines.append(f"  - بدون ترقيم مكتشف: {len(no_pages)}")
    lines.append(f"  - معالجة مسبقاً (متخطّاة): {len(already)}")
    lines.append(f"  - بأخطاء: {len(errors)}")
    lines.append("")

    lines.append(sub)
    lines.append("الكتب التي لم يُكتشف فيها أي ترقيم:")
    lines.append(sub)
    if not no_pages:
        lines.append("(لا يوجد)")
    else:
        for r in no_pages:
            lines.append(f"- {r['file']}")
            lines.append(f"    عدد المرشّحين (أسطر أرقام منفردة): {r.get('candidates_count', 0)}")

    lines.append("")
    lines.append(sub)
    lines.append("الكتب التي تم وسم ترقيمها:")
    lines.append(sub)
    if not has_pages:
        lines.append("(لا يوجد)")
    else:
        for r in has_pages:
            lines.append(f"- {r['file']}")
            lines.append(
                f"    عدد الأرقام الموسومة: {r['marked_count']}"
                f" (صارم: {r.get('strict_marked', 0)} | متساهل: {r.get('loose_marked', 0)})"
                f" | أول رقم: {r.get('first_number')}"
                f" | آخر رقم: {r.get('last_number')}"
                f" | الترميز: {r.get('encoding')}"
            )

    if already:
        lines.append("")
        lines.append(sub)
        lines.append("الكتب التي عُولجت سابقاً (تم تخطّيها):")
        lines.append(sub)
        for r in already:
            lines.append(f"- {r['file']}")

    if errors:
        lines.append("")
        lines.append(sub)
        lines.append("ملفات بأخطاء:")
        lines.append(sub)
        for r in errors:
            lines.append(f"- {r['file']}: {r.get('error', 'خطأ غير معروف')}")

    lines.append("")
    lines.append(sep)
    lines.append("انتهى التقرير.")
    lines.append(sep)

    report_path.write_text("\n".join(lines), encoding='utf-8')


# ---------- الدخول الرئيسي ----------

def main():
    folder_str = sys.argv[1] if len(sys.argv) >= 2 else FOLDER_PATH
    folder = Path(folder_str)

    if not folder.exists():
        print(f"[خطأ] المجلد غير موجود: {folder}")
        sys.exit(1)
    if not folder.is_dir():
        print(f"[خطأ] المسار ليس مجلداً: {folder}")
        sys.exit(1)

    files = find_txt_files(folder)
    if not files:
        print("لا توجد ملفات .txt في المجلد المحدد.")
        return

    print(f"[المجلد]  {folder}")
    print(f"[الملفات] {len(files)} | التوازي: {PARALLEL_WORKERS}\n")

    results = []

    def _process_and_format(f):
        info = process_file(f)
        if not info.get("ok", True):
            line = f"[تحذير] {f.name}: {info.get('error')}"
        elif info.get("status") == "already_processed":
            line = f"[تخطّي] {f.name}: معالج سابقاً"
        elif info.get("marked_count", 0) > 0:
            line = (f"[تم]    {f.name}: وُسم {info['marked_count']} رقم"
                    f" (صارم: {info.get('strict_marked', 0)} / متساهل: {info.get('loose_marked', 0)})"
                    f" (من {info.get('first_number')} إلى {info.get('last_number')})")
        else:
            line = (f"[فارغ]  {f.name}: لم يُكتشف ترقيم"
                    f" (مرشّحون: {info.get('candidates_count', 0)})")
        return info, line

    with ThreadPoolExecutor(max_workers=PARALLEL_WORKERS) as pool:
        futures = {pool.submit(_process_and_format, f): f for f in files}
        for future in as_completed(futures):
            try:
                info, line = future.result()
                results.append(info)
                print(line)
            except Exception as ex:
                f = futures[future]
                print(f"[خطأ]   {f.name}: {ex}")
                results.append({"file": str(f), "ok": False, "error": str(ex)})

    # حفظ التقرير بجانب السكربت
    try:
        script_dir = Path(__file__).resolve().parent
    except NameError:
        script_dir = Path.cwd()

    report_path = script_dir / REPORT_NAME
    write_report(report_path, folder, results)
    print(f"\n[التقرير] {report_path}")


if __name__ == "__main__":
    main()
