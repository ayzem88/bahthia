# -*- coding: utf-8 -*-
"""
محوّل PDF→TXT عبر Mistral OCR — قابل للاستدعاء من سطر الأوامر.

الاستعمال:
    python run_ocr.py --api-key <KEY> --input <PDF_OR_DIR>

ينشر التقدّم على stdout بصيغة سطور JSON واحد لكلّ حدث:
    {"event": "start", "file": "..."}
    {"event": "progress", "file": "...", "message": "...", "fraction": 0.5}
    {"event": "done", "file": "...", "txt": "...", "pages": 100}
    {"event": "error", "file": "...", "error": "..."}
    {"event": "summary", "ok": 5, "fail": 0, "elapsed": 67}

تستهلكه Kotlin عبر `PdfOcrRunner.kt` كـ subprocess.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
import time
from pathlib import Path
from typing import List, Optional


def emit(event: dict):
    """طباعة حدث JSON على stdout مع flush فوري."""
    sys.stdout.write(json.dumps(event, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def find_pdfs(folder: Path) -> List[Path]:
    return sorted(p for p in folder.rglob("*.pdf"))


def get_page_count(pdf_path: Path) -> int:
    try:
        from pypdf import PdfReader
        return len(PdfReader(str(pdf_path)).pages)
    except Exception:
        return 0


def split_pdf(pdf_path: Path, start: int, end: int) -> str:
    from pypdf import PdfReader, PdfWriter
    reader = PdfReader(str(pdf_path))
    writer = PdfWriter()
    for i in range(start, min(end, len(reader.pages))):
        writer.add_page(reader.pages[i])
    tmp = tempfile.NamedTemporaryFile(suffix=".pdf", delete=False)
    writer.write(tmp); tmp.close()
    return tmp.name


def ocr_with_retry(client, file_path: str, label: str, max_retries: int = 3) -> Optional[List[str]]:
    backoff = 2
    for attempt in range(1, max_retries + 1):
        try:
            with open(file_path, "rb") as f:
                uploaded = client.files.upload(
                    file={"file_name": Path(file_path).name, "content": f},
                    purpose="ocr",
                )
            signed = client.files.get_signed_url(file_id=uploaded.id)
            response = client.ocr.process(
                model="mistral-ocr-latest",
                document={"type": "document_url", "document_url": signed.url},
                include_image_base64=False,
            )
            try: client.files.delete(file_id=uploaded.id)
            except Exception: pass
            return [page.markdown for page in response.pages]
        except Exception as e:
            emit({"event": "warning", "message": f"{label} محاولة {attempt}: {e}"})
            if attempt < max_retries:
                time.sleep(backoff); backoff *= 2
    return None


def convert_one(client, pdf_path: Path, max_pages_per_chunk: int = 950):
    name = pdf_path.name
    emit({"event": "start", "file": str(pdf_path)})
    t0 = time.time()
    output_path = pdf_path.with_name(pdf_path.stem + "_ocr.txt")

    total_pages = get_page_count(pdf_path)
    if total_pages == 0:
        emit({"event": "error", "file": str(pdf_path), "error": "تعذّر قراءة عدد الصفحات"})
        return False

    file_size_mb = pdf_path.stat().st_size / (1024 * 1024)
    emit({
        "event": "progress", "file": str(pdf_path),
        "message": f"{file_size_mb:.1f}MB | {total_pages} صفحة",
        "fraction": 0.05,
    })

    try:
        if total_pages <= max_pages_per_chunk:
            pages = ocr_with_retry(client, str(pdf_path), name)
            if pages is None:
                emit({"event": "error", "file": str(pdf_path), "error": "فشل OCR"})
                return False
            with open(output_path, "w", encoding="utf-8") as f:
                for text in pages: f.write(text + "\n\n")
        else:
            num_chunks = (total_pages + max_pages_per_chunk - 1) // max_pages_per_chunk
            emit({
                "event": "progress", "file": str(pdf_path),
                "message": f"كتاب كبير → {num_chunks} أجزاء",
                "fraction": 0.1,
            })
            for i in range(num_chunks):
                start = i * max_pages_per_chunk
                end = min(start + max_pages_per_chunk, total_pages)
                emit({
                    "event": "progress", "file": str(pdf_path),
                    "message": f"جزء {i+1}/{num_chunks}: ص{start+1}-{end}",
                    "fraction": 0.1 + (0.7 * i / num_chunks),
                })
                tmp = split_pdf(pdf_path, start, end)
                try:
                    pages = ocr_with_retry(client, tmp, f"جزء {i+1}/{num_chunks}")
                    if pages:
                        part = f"{output_path}.part{i}"
                        with open(part, "w", encoding="utf-8") as f:
                            for text in pages: f.write(text + "\n\n")
                finally:
                    try: os.unlink(tmp)
                    except Exception: pass
            with open(output_path, "w", encoding="utf-8") as out:
                for i in range(num_chunks):
                    part = f"{output_path}.part{i}"
                    if os.path.exists(part):
                        with open(part, "r", encoding="utf-8") as pf:
                            out.write(pf.read())
                        os.unlink(part)

        if not output_path.exists() or output_path.stat().st_size == 0:
            emit({"event": "error", "file": str(pdf_path), "error": "الملف الناتج فارغ"})
            return False

        emit({
            "event": "progress", "file": str(pdf_path),
            "message": "جارٍ وسم أرقام الصفحات...",
            "fraction": 0.92,
        })
        # تطبيق ترقيم الصفحات إن توفّر
        try:
            here = Path(__file__).parent
            sys.path.insert(0, str(here))
            from page_numbers_marker import process_file as mark_pages
            mark_pages(output_path)
        except Exception as e:
            emit({"event": "warning", "message": f"الترقيم تخطّى: {e}"})

        elapsed = time.time() - t0
        emit({
            "event": "done", "file": str(pdf_path),
            "txt": str(output_path), "pages": total_pages,
            "elapsed": round(elapsed, 1),
        })
        return True
    except Exception as e:
        emit({"event": "error", "file": str(pdf_path), "error": str(e)})
        return False


def main():
    parser = argparse.ArgumentParser(description="Mistral OCR runner")
    parser.add_argument("--api-key", required=True, help="مفتاح Mistral API")
    parser.add_argument("--input", required=True, help="مسار ملف PDF أو مجلد")
    parser.add_argument("--max-pages-per-chunk", type=int, default=950)
    args = parser.parse_args()

    try:
        from mistralai import Mistral
    except ImportError:
        emit({"event": "fatal", "error": "حزمة mistralai غير مثبَّتة. ثبّتها بـ: pip install mistralai pypdf"})
        sys.exit(1)

    if not args.api_key.strip():
        emit({"event": "fatal", "error": "مفتاح API فارغ — اضبطه من إعدادات التطبيق"})
        sys.exit(1)

    client = Mistral(api_key=args.api_key.strip())
    inp = Path(args.input)

    if inp.is_file():
        files = [inp]
    elif inp.is_dir():
        files = find_pdfs(inp)
    else:
        emit({"event": "fatal", "error": f"المسار غير موجود: {inp}"})
        sys.exit(1)

    if not files:
        emit({"event": "fatal", "error": "لا توجد ملفات PDF"})
        sys.exit(0)

    emit({"event": "batch_start", "total": len(files)})
    t_start = time.time()
    ok = fail = 0

    for f in files:
        if convert_one(client, f, args.max_pages_per_chunk):
            ok += 1
        else:
            fail += 1

    emit({
        "event": "summary", "ok": ok, "fail": fail,
        "total": len(files), "elapsed": round(time.time() - t_start, 1),
    })


if __name__ == "__main__":
    main()
