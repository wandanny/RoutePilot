package com.local.routepilot.data

/** Extracts the delivery address from on-device OCR text of a parcel label. Pure Kotlin. */
object LabelParser {
  data class Result(
    val name: String = "",
    val address: String = "",
    val postcode: String = "",
    val phone: String = "",
    val rawText: String = "",
    val warnings: List<String> = emptyList(),
  )

  private val postcodeRegex = Regex("\\b\\d{5}\\b")
  private val phoneRegex = Regex("(?:\\+?60)?01[0-9]{8,9}")
  private val sectionRegex = Regex("(?i)details|penerima|pengirim")

  fun parse(rawText: String): Result {
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
    val warnings = mutableListOf<String>()

    val recipientIndex = lines.indexOfFirst { it.contains("Recipient", true) || it.contains("Penerima", true) }
    val parsed = if (recipientIndex >= 0) {
      extractSection(lines, recipientIndex + 1)
    } else {
      val genericIndex = lines.indexOfFirst { it.startsWith("Address:", true) }
      if (genericIndex >= 0) extractSection(lines, genericIndex) else Section()
    }

    var postcode = parsed.postcode
    if (postcode.isBlank()) {
      postcode = lines.firstNotNullOfOrNull { postcodeRegex.find(it)?.value }.orEmpty()
      if (postcode.isNotBlank()) warnings.add("Postcode inferred from label text")
    }
    val phone = lines.firstNotNullOfOrNull { phoneRegex.find(it)?.value }.orEmpty()

    if (parsed.address.isBlank()) warnings.add("No address found")
    if (parsed.name.isBlank()) warnings.add("No recipient name found")
    if (postcode.isBlank()) warnings.add("No 5-digit postcode found")

    return Result(
      name = parsed.name,
      address = parsed.address,
      postcode = postcode,
      phone = phone,
      rawText = rawText,
      warnings = warnings,
    )
  }

  private data class Section(val name: String = "", val address: String = "", val postcode: String = "")

  private fun extractSection(lines: List<String>, start: Int): Section {
    var name = ""
    var address = ""
    var postcode = ""

    for (i in start until lines.size) {
      val line = lines[i]
      when {
        name.isBlank() && line.startsWith("Name:", true) -> {
          name = line.substringAfter(':').trim()
        }

        address.isBlank() && line.startsWith("Address:", true) -> {
          val parts = mutableListOf(line.substringAfter(':').trim())
          var j = i + 1
          while (j < lines.size && !lines[j].startsWith("Postcode:", true) &&
            !lines[j].startsWith("Name:", true) && !sectionRegex.containsMatchIn(lines[j])
          ) {
            parts.add(lines[j])
            j++
          }
          address = parts.filter { it.isNotBlank() }.joinToString(", ")
          if (j < lines.size && lines[j].startsWith("Postcode:", true)) {
            postcode = postcodeRegex.find(lines[j])?.value.orEmpty()
          }
        }
      }
      if (name.isNotBlank() && address.isNotBlank() && postcode.isNotBlank()) break
    }
    return Section(name, address, postcode)
  }
}
