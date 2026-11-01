package com.saathi.ai.create

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.saathi.ai.core.ErrorCenter
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * AI Document Studio (Phase 18): real files, not text pretending to be files.
 *
 *  - **PDF** through Android's own `PdfDocument` (A4, wrapped Bangla text, page
 *    numbers) — no third-party library, works offline.
 *  - **DOCX / PPTX** by writing the OOXML package directly (a zip with the exact
 *    parts Word and PowerPoint expect). These open in Word/PowerPoint/Google Docs.
 *  - **Markdown / HTML / CSV / TXT** as plain files.
 *
 * Everything lands in `external-files/docs` and the report always carries the real
 * path and size, so "the document is ready" is a checkable claim.
 */
class DocumentStudio(private val context: Context) {

    private val dir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "docs").apply { mkdirs() }

    /** Generic entry point used by the code agent: kind + content → file path. */
    fun create(kind: String, content: String, title: String = ""): String {
        val t = title.ifBlank { firstLine(content) }
        return when (kind.trim().lowercase()) {
            "pdf" -> pdf(t, content)
            "docx", "doc", "word" -> docx(t, content.lines())
            "pptx", "ppt", "slides", "presentation" -> pptx(t, slidesFrom(content))
            "md", "markdown" -> plain(t, content, "md", "text/markdown")
            "html", "htm" -> html(t, content)
            "csv" -> csv(t, parseCsv(content))
            "txt", "text" -> plain(t, content, "txt", "text/plain")
            else -> plain(t, content, "txt", "text/plain")
        }
    }

    private fun firstLine(text: String): String =
        text.lines().firstOrNull { it.isNotBlank() }?.take(60) ?: "jimi-document"

    // -------------------------------------------------------------------- PDF

    /** A4 PDF with wrapped text, a title band and page numbers. */
    fun pdf(title: String, body: String): String {
        return try {
            val doc = PdfDocument()
            val pageW = 595
            val pageH = 842
            val margin = 44f
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 22f
                color = 0xFF141A33.toInt()
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13.5f
                color = 0xFF1B2033.toInt()
            }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9.5f
                color = 0xFF7A8299.toInt()
            }

            val lines = mutableListOf<String>()
            body.lines().forEach { raw ->
                val line = raw.trimEnd()
                if (line.isBlank()) {
                    lines.add("")
                } else {
                    lines.addAll(wrap(line, bodyPaint, pageW - margin * 2))
                }
            }

            var index = 0
            var pageNo = 1
            val usable = pageH - margin * 2 - 40
            val lineHeight = 20f
            val perPage = (usable / lineHeight).toInt()

            while (index < lines.size) {
                val info = PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create()
                val page = doc.startPage(info)
                val canvas = page.canvas
                var y = margin

                if (pageNo == 1) {
                    canvas.drawText(title.take(80), margin, y + 18, titlePaint)
                    y += 42f
                    canvas.drawText(
                        "Jimi Document Studio · ${java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.US)
                            .format(java.util.Date())}",
                        margin, y, metaPaint
                    )
                    y += 24f
                    canvas.drawLine(margin, y, pageW - margin, y, metaPaint)
                    y += 26f
                }

                var drawn = 0
                while (index < lines.size && drawn < perPage) {
                    val l = lines[index]
                    if (l.isNotEmpty()) canvas.drawText(l, margin, y + 13, bodyPaint)
                    y += lineHeight
                    index++
                    drawn++
                }
                canvas.drawText("পৃষ্ঠা $pageNo", pageW - margin - 60, pageH - 26, metaPaint)
                doc.finishPage(page)
                pageNo++
                if (index >= lines.size) break
                if (pageNo > 200) {
                    ErrorCenter.warn("file", "PDF পৃষ্ঠা সীমা", "২০০ পৃষ্ঠায় থামানো হয়েছে")
                    break
                }
            }
            if (pageNo == 1) {
                // Empty body still produces a one-page file, so the claim is true.
                val info = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
                val page = doc.startPage(info)
                page.canvas.drawText(title.ifBlank { "খালি ডকুমেন্ট" }, margin, margin + 20, titlePaint)
                doc.finishPage(page)
            }

            val name = safeName(title).ifBlank { "jimi-doc" }
            val out = File(dir, "$name-${System.currentTimeMillis() / 1000}.pdf")
            FileOutputStream(out).use { doc.writeTo(it) }
            doc.close()
            "সফল: PDF তৈরি হয়েছে (${pageNo.coerceAtLeast(1)} পৃষ্ঠা, ${humanSize(out.length())})\nফাইল: ${out.absolutePath}"
        } catch (t: Throwable) {
            ErrorCenter.logThrow("file", "PDF তৈরি ব্যর্থ", t)
            "ব্যর্থ: PDF বানানো যায়নি (${t.message})"
        }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        val words = text.split(" ")
        var line = StringBuilder()
        words.forEach { w ->
            val test = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(test) <= maxWidth) {
                line = StringBuilder(test)
            } else {
                if (line.isNotEmpty()) out.add(line.toString())
                line = StringBuilder(w)
                // A single word wider than the page: hard-break it instead of losing it.
                while (paint.measureText(line.toString()) > maxWidth && line.length > 1) {
                    var cut = line.length - 1
                    while (cut > 1 && paint.measureText(line.substring(0, cut)) > maxWidth) cut--
                    out.add(line.substring(0, cut))
                    line = StringBuilder(line.substring(cut))
                }
            }
        }
        if (line.isNotEmpty()) out.add(line.toString())
        return if (out.isEmpty()) listOf("") else out
    }

    // ------------------------------------------------------------------- DOCX

    /** Minimal but valid WordprocessingML package. */
    fun docx(title: String, paragraphs: List<String>): String {
        return try {
            val body = StringBuilder()
            body.append(
                "<w:p><w:pPr><w:spacing w:after=\"240\"/><w:rPr><w:b/><w:sz w:val=\"40\"/></w:rPr></w:pPr>" +
                    "<w:r><w:rPr><w:b/><w:sz w:val=\"40\"/></w:rPr><w:t xml:space=\"preserve\">${esc(title)}</w:t></w:r></w:p>"
            )
            paragraphs.forEach { p ->
                if (p.isBlank()) {
                    body.append("<w:p/>")
                    return@forEach
                }
                val isHeading = p.startsWith("#") || p.startsWith("##")
                val text = p.removePrefix("### ").removePrefix("## ").removePrefix("# ")
                val bullet = text.startsWith("• ") || text.startsWith("- ")
                val content = if (bullet) text.drop(2) else text
                val rpr = if (isHeading) "<w:rPr><w:b/><w:sz w:val=\"30\"/></w:rPr>" else ""
                val ppr = if (isHeading) "<w:pPr><w:spacing w:before=\"200\" w:after=\"120\"/>$rpr</w:pPr>"
                else "<w:pPr><w:spacing w:after=\"120\"/>${if (bullet) "<w:ind w:left=\"360\"/>" else ""}</w:pPr>"
                body.append("<w:p>$ppr<w:r>$rpr<w:t xml:space=\"preserve\">${esc(if (bullet) "• $content" else content)}</w:t></w:r></w:p>")
            }
            val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/></w:sectPr></w:body></w:document>"""

            val out = File(dir, "${safeName(title).ifBlank { "jimi-doc" }}-${System.currentTimeMillis() / 1000}.docx")
            ZipOutputStream(FileOutputStream(out)).use { zip ->
                zip.putNextEntry(ZipEntry("[Content_Types].xml"))
                zip.write(CT_DOCX.toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("_rels/.rels"))
                zip.write(RELS_ROOT_DOCX.toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(document.toByteArray())
                zip.closeEntry()
            }
            "সফল: Word ডকুমেন্ট (.docx) তৈরি হয়েছে — ${paragraphs.size}টি অনুচ্ছেদ, ${humanSize(out.length())}\nফাইল: ${out.absolutePath}"
        } catch (t: Throwable) {
            ErrorCenter.logThrow("file", "DOCX তৈরি ব্যর্থ", t)
            "ব্যর্থ: DOCX বানানো যায়নি (${t.message})"
        }
    }

    // ------------------------------------------------------------------- PPTX

    /**
     * Presentation as a real .pptx package (the Jarvis-parity gap). Structure:
     * presentation → slideMaster → slideLayout → theme, plus one slide per section.
     */
    fun pptx(title: String, slides: List<Pair<String, String>>): String {
        return try {
            if (slides.isEmpty()) return "ব্যর্থ: স্লাইডের কোনো বিষয়বস্তু পাওয়া যায়নি"
            val out = File(dir, "${safeName(title).ifBlank { "jimi-slides" }}-${System.currentTimeMillis() / 1000}.pptx")
            ZipOutputStream(FileOutputStream(out)).use { zip ->
                fun add(path: String, content: String) {
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
                add("[Content_Types].xml", ctPptx(slides.size))
                add("_rels/.rels", RELS_ROOT_PPTX)
                add("ppt/presentation.xml", presentationXml(slides.size))
                add("ppt/_rels/presentation.xml.rels", presentationRels(slides.size))
                add("ppt/theme/theme1.xml", THEME_XML)
                add("ppt/slideMasters/slideMaster1.xml", slideMasterXml())
                add("ppt/slideMasters/_rels/slideMaster1.xml.rels", masterRels())
                add("ppt/slideLayouts/slideLayout1.xml", slideLayoutXml())
                add("ppt/slideLayouts/_rels/slideLayout1.xml.rels", layoutRels())
                slides.forEachIndexed { i, (head, text) ->
                    add("ppt/slides/slide${i + 1}.xml", slideXml(head, text))
                    add("ppt/slides/_rels/slide${i + 1}.xml.rels", slideRels())
                }
            }
            "সফল: প্রেজেন্টেশন (.pptx) তৈরি হয়েছে — ${slides.size}টি স্লাইড, ${humanSize(out.length())}\nফাইল: ${out.absolutePath}\n" +
                "স্লাইড: ${slides.joinToString(" · ") { it.first.take(28) }}"
        } catch (t: Throwable) {
            ErrorCenter.logThrow("file", "PPTX তৈরি ব্যর্থ", t)
            "ব্যর্থ: PPTX বানানো যায়নি (${t.message})"
        }
    }

    /** "Title\nbody" blocks separated by blank lines become slides. */
    fun slidesFrom(content: String): List<Pair<String, String>> {
        val blocks = content.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotBlank() }
        return blocks.map { b ->
            val lines = b.lines().map { it.trim() }.filter { it.isNotBlank() }
            (lines.first().removePrefix("#").removePrefix("##").trim()) to lines.drop(1).joinToString("\n")
        }
    }

    // ------------------------------------------------------- text-ish formats

    fun html(title: String, body: String): String {
        val html = buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"bn\"><head><meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            appendLine("<title>${esc(title)}</title>")
            appendLine(
                "<style>body{margin:0;padding:28px 22px;background:#0B1024;color:#F3F1FF;" +
                    "font-family:'Noto Sans Bengali',system-ui,sans-serif;line-height:1.7}" +
                    "h1{font-size:22px;margin:0 0 6px}h2{font-size:17px;color:#35D6C8;margin:22px 0 8px}" +
                    ".meta{color:#9AA3C0;font-size:12px}ul{margin:8px 0 8px 20px}li{margin:5px 0}" +
                    "pre{background:rgba(0,0,0,.4);border:1px solid rgba(255,255,255,.1);border-radius:12px;" +
                    "padding:12px;overflow:auto;font-size:12.5px}</style>"
            )
            appendLine("</head><body>")
            appendLine("<h1>${esc(title)}</h1>")
            appendLine(
                "<div class=\"meta\">Jimi Document Studio · ${
                    java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.US).format(java.util.Date())
                }</div>"
            )
            body.lines().forEach { l ->
                when {
                    l.isBlank() -> Unit
                    l.startsWith("### ") -> appendLine("<h3>${esc(l.drop(4))}</h3>")
                    l.startsWith("## ") -> appendLine("<h2>${esc(l.drop(3))}</h2>")
                    l.startsWith("# ") -> appendLine("<h2>${esc(l.drop(2))}</h2>")
                    l.startsWith("• ") || l.startsWith("- ") -> appendLine("<div>• ${esc(l.drop(2))}</div>")
                    else -> appendLine("<p>${esc(l)}</p>")
                }
            }
            appendLine("</body></html>")
        }
        val out = File(dir, "${safeName(title).ifBlank { "jimi-page" }}-${System.currentTimeMillis() / 1000}.html")
        return try {
            out.writeText(html)
            "সফল: HTML পেজ তৈরি হয়েছে (${humanSize(out.length())})\nফাইল: ${out.absolutePath}"
        } catch (t: Throwable) {
            "ব্যর্থ: HTML লেখা যায়নি (${t.message})"
        }
    }

    fun csv(title: String, rows: List<List<String>>): String {
        return try {
            val out = File(dir, "${safeName(title).ifBlank { "jimi-table" }}-${System.currentTimeMillis() / 1000}.csv")
            out.bufferedWriter().use { w ->
                rows.forEach { row ->
                    w.write(row.joinToString(",") { cell ->
                        if (cell.contains(",") || cell.contains("\"")) "\"" + cell.replace("\"", "\"\"") + "\"" else cell
                    })
                    w.newLine()
                }
            }
            "সফল: CSV টেবিল তৈরি হয়েছে (${rows.size} সারি, ${humanSize(out.length())})\nফাইল: ${out.absolutePath}"
        } catch (t: Throwable) {
            "ব্যর্থ: CSV লেখা যায়নি (${t.message})"
        }
    }

    /** Splits CSV/TSV-ish text into rows for [csv]. */
    fun parseCsv(text: String): List<List<String>> = text.lines()
        .filter { it.isNotBlank() }
        .map { line ->
            if (line.contains('\t')) line.split('\t').map { it.trim() }
            else line.split(Regex("\\s*[|,]\\s*")).map { it.trim() }
        }

    fun plain(title: String, content: String, ext: String, mime: String): String {
        return try {
            val out = File(dir, "${safeName(title).ifBlank { "jimi-note" }}-${System.currentTimeMillis() / 1000}.$ext")
            out.writeText(content)
            "সফল: ফাইল তৈরি হয়েছে ($mime, ${humanSize(out.length())})\nফাইল: ${out.absolutePath}"
        } catch (t: Throwable) {
            "ব্যর্থ: ফাইল লেখা যায়নি (${t.message})"
        }
    }

    // ------------------------------------------------------------- document list

    fun library(): String {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) return "ডকুমেন্ট ফোল্ডার খালি — এখনো কিছু বানানো হয়নি"
        return buildString {
            appendLine("ডকুমেন্ট স্টুডিও: ${files.size}টি ফাইল")
            files.take(30).forEach {
                appendLine("• ${it.name} — ${humanSize(it.length())}")
            }
            append("ফোল্ডার: ${dir.absolutePath}")
        }
    }

    /** Formats this studio can produce — for the settings screen and the model. */
    fun capabilities(): String =
        "ডকুমেন্ট স্টুডিও: PDF (A4, বাংলা র‍্যাপ), DOCX (Word), PPTX (প্রেজেন্টেশন), HTML, Markdown, CSV, TXT — সব অফলাইনে তৈরি হয়"

    // ------------------------------------------------------------------ helpers

    private fun safeName(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9\\u0980-\\u09FF]+"), "-").trim('-').take(40)

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")

    private fun humanSize(b: Long): String = when {
        b > 1_048_576 -> String.format(java.util.Locale.US, "%.1f MB", b / 1_048_576.0)
        b > 1024 -> String.format(java.util.Locale.US, "%.0f KB", b / 1024.0)
        else -> "$b B"
    }

    // ------------------------------------------------------------------- XML

    private val CT_DOCX = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""

    private val RELS_ROOT_DOCX = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

    private val RELS_ROOT_PPTX = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>"""

    private fun ctPptx(slides: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        sb.append("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>")
        sb.append("<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>")
        sb.append("<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>")
        sb.append("<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
        for (i in 1..slides) {
            sb.append("<Override PartName=\"/ppt/slides/slide$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>")
        }
        sb.append("</Types>")
        return sb.toString()
    }

    private fun presentationXml(slides: Int): String {
        val ids = (1..slides).joinToString("") { i ->
            "<p:sldId id=\"${255 + i}\" r:id=\"rId${i + 1}\"/>"
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst><p:sldIdLst>$ids</p:sldIdLst><p:sldSz cx="12192000" cy="6858000"/><p:notesSz cx="6858000" cy="9144000"/></p:presentation>"""
    }

    private fun presentationRels(slides: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        sb.append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")
        for (i in 1..slides) {
            sb.append("<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide$i.xml\"/>")
        }
        sb.append("<Relationship Id=\"rId${slides + 2}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"theme/theme1.xml\"/>")
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun masterRels(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/></Relationships>"""

    private fun layoutRels(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>"""

    private fun slideRels(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/></Relationships>"""

    private fun slideMasterXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val="0B1024"/></a:solidFill><a:effectLst/></p:bgPr></p:bg><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/><p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst></p:sldMaster>"""

    private fun slideLayoutXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank"><p:cSld name="Jimi"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"""

    /** One slide: a title box and a body box, sized for 16:9. */
    private fun slideXml(head: String, text: String): String {
        val paras = text.lines().filter { it.isNotBlank() }.ifEmpty { listOf("") }
            .joinToString("") { line ->
                val clean = line.removePrefix("• ").removePrefix("- ")
                "<a:p><a:r><a:rPr lang=\"bn-BD\" sz=\"2000\" dirty=\"0\"><a:solidFill><a:srgbClr val=\"E6E9FF\"/></a:solidFill></a:rPr>" +
                    "<a:t>${esc(clean)}</a:t></a:r></a:p>"
            }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/><p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x="457200" y="365125"/><a:ext cx="11277600" cy="1143000"/></a:xfrm></p:spPr><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang="bn-BD" sz="3600" b="1" dirty="0"><a:solidFill><a:srgbClr val="35D6C8"/></a:solidFill></a:rPr><a:t>${esc(head)}</a:t></a:r></a:p></p:txBody></p:sp><p:sp><p:nvSpPr><p:cNvPr id="3" name="Body"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x="457200" y="1600200"/><a:ext cx="11277600" cy="4800600"/></a:xfrm></p:spPr><p:txBody><a:bodyPr/><a:lstStyle/>$paras</p:txBody></p:sp></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"""
    }

    private val THEME_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Jimi"><a:themeElements><a:clrScheme name="Jimi"><a:dk1><a:srgbClr val="0B1024"/></a:dk1><a:lt1><a:srgbClr val="F3F1FF"/></a:lt1><a:dk2><a:srgbClr val="141A33"/></a:dk2><a:lt2><a:srgbClr val="E6E9FF"/></a:lt2><a:accent1><a:srgbClr val="7C5CFF"/></a:accent1><a:accent2><a:srgbClr val="35D6C8"/></a:accent2><a:accent3><a:srgbClr val="FF7AC6"/></a:accent3><a:accent4><a:srgbClr val="4AA8FF"/></a:accent4><a:accent5><a:srgbClr val="34D399"/></a:accent5><a:accent6><a:srgbClr val="FBBF24"/></a:accent6><a:hlink><a:srgbClr val="35D6C8"/></a:hlink><a:folHlink><a:srgbClr val="7C5CFF"/></a:folHlink></a:clrScheme><a:fontScheme name="Jimi"><a:majorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont><a:minorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont></a:fontScheme><a:fmtScheme name="Jimi"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>"""
}
