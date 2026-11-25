package com.local.routepilot

import com.local.routepilot.data.LucifClient
import org.junit.Assert.assertEquals
import org.junit.Test

class LucifClientTest {
  @Test
  fun parsesPlainJson() {
    val result = LucifClient.parseFields("""{"name":"LEE MEI SEE","address":"No.23 Jalan PUJ","postcode":"43300"}""")
    assertEquals("LEE MEI SEE", result.name)
    assertEquals("43300", result.postcode)
  }

  @Test
  fun parsesFencedJson() {
    val result = LucifClient.parseFields(
      "```json\n{\"name\":\"Ali\",\"address\":\"1 Jalan Kota\",\"postcode\":\"34000\"}\n```",
    )
    assertEquals("Ali", result.name)
    assertEquals("1 Jalan Kota", result.address)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsResponseWithoutJson() {
    LucifClient.parseFields("I cannot read this label")
  }
}
