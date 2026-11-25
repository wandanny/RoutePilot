package com.local.routepilot

import com.local.routepilot.data.LabelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelParserTest {
  private val spxOcr = """
    SPX EXPRESS
    Order Details
    Ship By Date: 03-06-2025
    Sender Details (Pengirim)
    Address: 40 Jalan Seroja 39 Taman Johor
    Jaya, Johor Bahru, Johor
    Postcode: 81000
    Recipient Details (Penerima)
    Name: LEE MEI SEE
    Address: No.23, Jalan PUJ 2/45, Taman Puncak
    Jalil, Bandar Putra Permai, 43300 Seri
    Kembangan, Sel.D.E. Petaling,
    Petaling Jaya, Selangor
    Postcode: 43300
    SPXYM058541602035
  """.trimIndent()

  @Test
  fun spxLabelUsesRecipientNotSender() {
    val result = LabelParser.parse(spxOcr)
    assertEquals("LEE MEI SEE", result.name)
    assertTrue(result.address.startsWith("No.23, Jalan PUJ 2/45"))
    assertTrue(result.address.contains("Petaling Jaya, Selangor"))
    assertEquals("43300", result.postcode)
    assertTrue(result.warnings.isEmpty())
  }

  @Test
  fun genericLabelStillFindsAddress() {
    val result = LabelParser.parse("Delivery address\nAddress: 12 Jalan Kota, Taiping\nName: Ali Bin Abu\n54300")
    assertEquals("12 Jalan Kota, Taiping", result.address)
    assertEquals("54300", result.postcode)
  }

  @Test
  fun unreadableLabelReportsWarnings() {
    val result = LabelParser.parse("blurry text only")
    assertEquals("", result.address)
    assertTrue(result.warnings.any { it.contains("No address") })
  }

  @Test
  fun malaysianPhoneIsExtractedWhenPresent() {
    val result = LabelParser.parse("Name: Lim\nAddress: 1 Jalan Ampang\n0123456789\nPostcode: 34000")
    assertEquals("0123456789", result.phone)
  }
}
