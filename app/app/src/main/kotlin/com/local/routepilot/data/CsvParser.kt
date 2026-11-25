package com.local.routepilot.data

/** Minimal CSV reader: auto-detects comma or semicolon and supports quoted fields. */
object CsvParser {
  data class Result(val headers: List<String>, val rows: List<Map<String, String>>, val errors: List<String>)

  fun parse(text: String): Result {
    val lines = text.lineSequence()
      .map { it.trimEnd('\r') }
      .filter { it.isNotBlank() }
      .toList()
    if (lines.isEmpty()) return Result(emptyList(), emptyList(), listOf("File is empty"))

    val delimiter = if (lines.first().count { it == ';' } > lines.first().count { it == ',' }) ';' else ','
    val parsedLines = lines.map { splitLine(it, delimiter) }
    val headers = parsedLines.first().map { it.trim().lowercase() }
    val rows = mutableListOf<Map<String, String>>()
    val errors = mutableListOf<String>()

    parsedLines.drop(1).forEachIndexed { index, cells ->
      if (cells.size != headers.size) {
        errors.add("Row ${index + 2}: expected ${headers.size} columns, found ${cells.size}")
        return@forEachIndexed
      }
      rows.add(headers.zip(cells.map { it.trim() }).toMap())
    }
    return Result(headers, rows, errors)
  }

  private fun splitLine(line: String, delimiter: Char): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
      val c = line[i]
      when {
        c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
          current.append('"')
          i++
        }
        c == '"' -> quoted = !quoted
        c == delimiter && !quoted -> {
          result.add(current.toString())
          current.clear()
        }
        else -> current.append(c)
      }
      i++
    }
    result.add(current.toString())
    return result
  }

  fun field(row: Map<String, String>, vararg names: String): String {
    names.forEach { name ->
      row[name.lowercase()]?.let { if (it.isNotBlank()) return it }
    }
    return ""
  }
}
