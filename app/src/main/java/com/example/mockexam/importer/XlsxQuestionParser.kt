package com.example.mockexam.importer

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class ImportValidationException(val issues: List<String>) : Exception(issues.joinToString("\n"))

data class ParsedQuestionData(
    val questionId: String,
    val question: String,
    val imagePath: String?,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctAnswer: String,
    val explanation: String
)

/** Pure JVM OOXML parser; kept separate from Android URI handling so it can be tested directly. */
object XlsxQuestionParser {
    private const val MAX_WORKBOOK_BYTES = 30L * 1024L * 1024L
    private const val MAX_IMAGE_BYTES = 8L * 1024L * 1024L
    private const val MAX_TOTAL_IMAGE_BYTES = 20L * 1024L * 1024L
    private const val MAX_XML_ENTRY_BYTES = 20L * 1024L * 1024L
    private const val MAX_QUESTIONS = 2_000
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val requiredHeaders = listOf(
        "question_id", "question", "image", "option_a", "option_b",
        "option_c", "option_d", "correct_answer", "explanation"
    )

    private data class SheetInfo(val name: String, val path: String)
    private data class WorksheetData(val rows: Map<Int, Map<Int, String>>, val drawingRelId: String?)

    fun parseWorkbook(file: File, imageDir: File): List<ParsedQuestionData> {
        if (!file.isFile || file.length() <= 0L) throw ImportValidationException(listOf("The selected workbook is empty or unavailable."))
        if (file.length() > MAX_WORKBOOK_BYTES) throw ImportValidationException(listOf("Workbook exceeds the 30 MB import limit."))
        ZipFile(file).use { zip ->
            val entries = Collections.list(zip.entries())
            val names = entries.map { it.name }.toSet()
            if ("[Content_Types].xml" !in names || "xl/workbook.xml" !in names) {
                throw ImportValidationException(listOf("This file is not a valid .xlsx workbook."))
            }
            if (entries.size > 20_000) throw ImportValidationException(listOf("Workbook contains an excessive number of embedded files."))

            val workbookRels = readRelationships(zip, "xl/_rels/workbook.xml.rels")
            val sheetInfo = findQuestionsSheet(zip, workbookRels)
                ?: throw ImportValidationException(listOf("Could not find a worksheet. Add a sheet named Questions or put the questions in the first sheet."))
            val sharedStrings = readSharedStrings(zip, "xl/sharedStrings.xml")
            val worksheet = readWorksheet(zip, sheetInfo.path, sharedStrings)
            val worksheetRels = readRelationships(zip, relationshipPartPath(sheetInfo.path))
            val drawingPath = worksheet.drawingRelId?.let { relId -> worksheetRels[relId]?.let { resolveTarget(sheetInfo.path, it) } }
            val pictureByRow = if (drawingPath != null) {
                val drawingRels = readRelationships(zip, relationshipPartPath(drawingPath))
                readDrawingPictures(zip, drawingPath, drawingRels)
            } else emptyMap()

            val headerCells = worksheet.rows[1]
                ?: throw ImportValidationException(listOf("The Questions worksheet must contain its header row in row 1."))
            val headerMap = mutableMapOf<String, Int>()
            val headerErrors = mutableListOf<String>()
            headerCells.forEach { (col, value) ->
                val key = value.trim().lowercase(Locale.ROOT).replace(" ", "_")
                if (key.isNotEmpty() && key in headerMap) headerErrors += "Row 1: duplicate header '$value'."
                if (key.isNotEmpty()) headerMap[key] = col
            }
            val missing = requiredHeaders.filter { it !in headerMap }
            if (missing.isNotEmpty()) headerErrors += "Row 1: missing required column(s): ${missing.joinToString(", ")}."
            if (headerErrors.isNotEmpty()) throw ImportValidationException(headerErrors)

            if (!imageDir.exists() && !imageDir.mkdirs()) {
                throw ImportValidationException(listOf("Could not create local storage for question images."))
            }
            val errors = mutableListOf<String>()
            val seenIds = mutableSetOf<String>()
            val results = mutableListOf<ParsedQuestionData>()
            var totalImageBytes = 0L
            var questionRowCount = 0

            fun cell(row: Map<Int, String>, header: String): String = row[headerMap.getValue(header)].orEmpty().trim()

            worksheet.rows.toSortedMap().forEach { (rowNumber, row) ->
                if (rowNumber == 1 || row.values.all { it.isBlank() }) return@forEach
                questionRowCount++
                if (questionRowCount > MAX_QUESTIONS) {
                    if (questionRowCount == MAX_QUESTIONS + 1) errors += "Row $rowNumber: workbook exceeds the $MAX_QUESTIONS question limit."
                    return@forEach
                }
                val id = cell(row, "question_id")
                val question = cell(row, "question")
                val optionA = cell(row, "option_a")
                val optionB = cell(row, "option_b")
                val optionC = cell(row, "option_c")
                val optionD = cell(row, "option_d")
                val answer = cell(row, "correct_answer").uppercase(Locale.ROOT)
                val explanation = cell(row, "explanation")
                val rowErrors = mutableListOf<String>()

                if (id.isBlank()) rowErrors += "Question_ID is empty"
                else if (!seenIds.add(id.lowercase(Locale.ROOT))) rowErrors += "duplicate Question_ID '$id'"
                if (question.isBlank()) rowErrors += "Question is empty"
                if (optionA.isBlank()) rowErrors += "Option_A is empty"
                if (optionB.isBlank()) rowErrors += "Option_B is empty"
                if (optionC.isBlank()) rowErrors += "Option_C is empty"
                if (optionD.isBlank()) rowErrors += "Option_D is empty"
                if (answer !in setOf("A", "B", "C", "D")) rowErrors += "Correct_Answer must be A, B, C or D"
                if (explanation.isBlank()) rowErrors += "Explanation is empty"

                val imageCell = cell(row, "image")
                val imageEntry = try {
                    when {
                        imageCell.isNotBlank() -> findImageEntry(imageCell, names)
                        else -> pictureByRow[rowNumber]
                    }
                } catch (e: IllegalArgumentException) {
                    rowErrors += e.message ?: "Invalid image reference"
                    null
                }
                var imagePath: String? = null
                if (imageEntry != null) {
                    try {
                        val extension = imageEntry.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        if (extension !in setOf("png", "jpg", "jpeg", "gif", "webp")) {
                            throw IllegalArgumentException("unsupported image type .$extension (use PNG, JPG, GIF or WebP)")
                        }
                        val bytes = zip.getInputStream(zip.getEntry(imageEntry)).use { stream ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8 * 1024)
                            var count = 0L
                            while (true) {
                                val read = stream.read(buffer)
                                if (read < 0) break
                                count += read
                                if (count > MAX_IMAGE_BYTES) throw IllegalArgumentException("image exceeds 8 MB")
                                out.write(buffer, 0, read)
                            }
                            out.toByteArray()
                        }
                        if (bytes.isEmpty()) throw IllegalArgumentException("image file is empty")
                        if (!hasValidImageSignature(bytes, extension)) throw IllegalArgumentException("file contents do not match a supported image")
                        totalImageBytes += bytes.size
                        if (totalImageBytes > MAX_TOTAL_IMAGE_BYTES) throw IllegalArgumentException("embedded images exceed the 20 MB total limit")
                        val output = File(imageDir, "${UUID.randomUUID()}.$extension")
                        output.writeBytes(bytes)
                        imagePath = output.absolutePath
                    } catch (e: Exception) {
                        rowErrors += "Image: ${e.message ?: "could not extract embedded image"}"
                    }
                } else if (imageCell.isNotBlank()) {
                    rowErrors += "Image reference '$imageCell' was not found inside this workbook. External URLs and local disk paths are not supported."
                }

                if (rowErrors.isNotEmpty()) {
                    errors += "Row $rowNumber (Question_ID: ${id.ifBlank { "missing" }}): ${rowErrors.joinToString("; ")}."
                } else {
                    results += ParsedQuestionData(id, question, imagePath, optionA, optionB, optionC, optionD, answer, explanation)
                }
            }
            if (errors.isNotEmpty()) throw ImportValidationException(errors)
            return results
        }
    }

    private fun findQuestionsSheet(zip: ZipFile, rels: Map<String, String>): SheetInfo? {
        val document = readXml(zip, "xl/workbook.xml") ?: return null
        val sheets = descendants(document.documentElement, "sheet").mapNotNull { sheet ->
            val name = sheet.getAttribute("name")
            val relId = attrNs(sheet, REL_NS, "id")
            val target = rels[relId]
            if (target == null) null else SheetInfo(name, resolveTarget("xl/workbook.xml", target))
        }
        return sheets.firstOrNull { it.name.equals("Questions", true) } ?: sheets.firstOrNull()
    }

    private fun readSharedStrings(zip: ZipFile, path: String): List<String> {
        val document = readXml(zip, path) ?: return emptyList()
        return descendants(document.documentElement, "si").map { si ->
            descendants(si, "t").joinToString(separator = "") { it.textContent.orEmpty() }
        }
    }

    private fun readWorksheet(zip: ZipFile, path: String, shared: List<String>): WorksheetData {
        val document = readXml(zip, path)
            ?: throw ImportValidationException(listOf("Could not read worksheet XML ($path)."))
        val rows = linkedMapOf<Int, MutableMap<Int, String>>()
        var fallbackRow = 0
        descendants(document.documentElement, "row").forEach { row ->
            fallbackRow++
            val rowNumber = row.getAttribute("r").toIntOrNull() ?: fallbackRow
            val cells = linkedMapOf<Int, String>()
            directChildren(row, "c").forEach cellLoop@{ cell ->
                val column = columnIndex(cell.getAttribute("r"))
                if (column < 0) return@cellLoop
                val cellType = cell.getAttribute("t")
                val raw = when (cellType) {
                    "inlineStr" -> descendants(cell, "t").joinToString("") { it.textContent.orEmpty() }
                    else -> directChildren(cell, "v").firstOrNull()?.textContent.orEmpty()
                }
                val value = if (cellType == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                cells[column] = value
            }
            rows[rowNumber] = cells
        }
        val drawing = descendants(document.documentElement, "drawing").firstOrNull()
        val drawingRelId = drawing?.let { attrNs(it, REL_NS, "id") }
        return WorksheetData(rows, drawingRelId)
    }

    private fun readRelationships(zip: ZipFile, path: String): Map<String, String> {
        val document = readXml(zip, path) ?: return emptyMap()
        return descendants(document.documentElement, "Relationship").mapNotNull { rel ->
            val id = rel.getAttribute("Id")
            val target = rel.getAttribute("Target")
            if (id.isBlank() || target.isBlank()) null else id to target
        }.toMap()
    }

    private fun readDrawingPictures(zip: ZipFile, path: String, rels: Map<String, String>): Map<Int, String> {
        val document = readXml(zip, path) ?: return emptyMap()
        val result = mutableMapOf<Int, String>()
        val anchors = descendants(document.documentElement, "twoCellAnchor") + descendants(document.documentElement, "oneCellAnchor")
        anchors.forEach { anchor ->
            val from = directChildren(anchor, "from").firstOrNull() ?: return@forEach
            val row = directChildren(from, "row").firstOrNull()?.textContent?.trim()?.toIntOrNull() ?: return@forEach
            val col = directChildren(from, "col").firstOrNull()?.textContent?.trim()?.toIntOrNull() ?: -1
            if (col != 2) return@forEach // Image is the third column (zero-based index 2).
            val blip = descendants(anchor, "blip").firstOrNull() ?: return@forEach
            val relId = attrNs(blip, REL_NS, "embed")
            val target = rels[relId] ?: return@forEach
            result[row + 1] = resolveTarget(path, target)
        }
        return result
    }


    private fun hasValidImageSignature(bytes: ByteArray, extension: String): Boolean = when (extension) {
        "png" -> bytes.size >= 8 && bytes.sliceArray(0..7).contentEquals(byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        ))
        "jpg", "jpeg" -> bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        "gif" -> bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")
        "webp" -> bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        else -> false
    }

    private fun findImageEntry(reference: String, entryNames: Set<String>): String? {
        val ref = reference.trim().replace('\\', '/')
        if (ref.startsWith("http://", true) || ref.startsWith("https://", true) || ref.startsWith("file:", true)) {
            throw IllegalArgumentException("use an image embedded in the workbook, not an external URL or file path")
        }
        val candidates = listOf(ref, "xl/media/$ref", "xl/$ref")
        return candidates.firstOrNull { it in entryNames }
            ?: entryNames.firstOrNull { it.substringAfterLast('/') == ref.substringAfterLast('/') && it.startsWith("xl/media/") }
    }

    private fun readXml(zip: ZipFile, path: String): Document? {
        val entry = zip.getEntry(path) ?: return null
        if (entry.size > MAX_XML_ENTRY_BYTES) {
            throw ImportValidationException(listOf("Workbook XML part '$path' exceeds the 20 MB safety limit."))
        }
        val xmlBytes = zip.getInputStream(entry).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_XML_ENTRY_BYTES) {
                    throw ImportValidationException(listOf("Workbook XML part '$path' exceeds the 20 MB safety limit."))
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            try { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") } catch (_: Exception) { }
            try { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") } catch (_: Exception) { }
        }
        return java.io.ByteArrayInputStream(xmlBytes).use { factory.newDocumentBuilder().parse(it) }
    }

    private fun descendants(root: org.w3c.dom.Node, localName: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = if (root is Document) root.getElementsByTagNameNS("*", localName) else (root as Element).getElementsByTagNameNS("*", localName)
        for (index in 0 until nodes.length) result += nodes.item(index) as Element
        return result
    }

    private fun directChildren(parent: Element, localName: String): List<Element> {
        val result = mutableListOf<Element>()
        val children = parent.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node is Element && local(node) == localName) result += node
        }
        return result
    }

    private fun local(element: Element): String = element.localName ?: element.tagName.substringAfter(':')

    private fun attrNs(element: Element, namespace: String, name: String): String {
        val value = element.getAttributeNS(namespace, name)
        if (value.isNotBlank()) return value
        return element.getAttribute("r:$name").ifBlank { element.getAttribute("$name") }
    }

    private fun relationshipPartPath(partPath: String): String {
        val slash = partPath.lastIndexOf('/')
        val parent = if (slash >= 0) partPath.substring(0, slash + 1) else ""
        val name = partPath.substring(slash + 1)
        return "${parent}_rels/$name.rels"
    }

    private fun resolveTarget(sourcePart: String, target: String): String {
        val combined = if (target.startsWith("/")) target.removePrefix("/") else {
            val parent = sourcePart.substringBeforeLast('/', "")
            if (parent.isBlank()) target else "$parent/$target"
        }
        val stack = mutableListOf<String>()
        combined.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> stack += segment
            }
        }
        return stack.joinToString("/")
    }

    private fun columnIndex(cellReference: String): Int {
        val letters = cellReference.takeWhile { it.isLetter() }.uppercase(Locale.ROOT)
        if (letters.isBlank()) return -1
        var value = 0
        letters.forEach { value = value * 26 + (it - 'A' + 1) }
        return value - 1
    }
}
