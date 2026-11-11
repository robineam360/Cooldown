package com.robin.claudeusage.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCRM-113 (Sample Port), wireframe §9 b: the banner's width rule in TextFit's em model — the line (206 dp at 13 sp) and
 * "Exit" (23 dp) fit the cover slot (379 dp), a 360 dp phone (328) and a 320 dp phone (288) at font scale 1.0; where the line
 * does not fit (font scale 1.3 on a 360 dp phone, and larger) " — made-up accounts" drops. "● Sample data" and "Exit" never do.
 */
class SampleBannerFitTest {
    private fun tail(available: Float, scale: Float) =
        SampleBannerFit.showsTail(available, 206f * scale, 23f * scale, SampleBannerFit.CHROME_DP)

    @Test fun `the whole line fits the cover slot, a 360 dp phone and a 320 dp phone at font 1_0`() {
        for (w in listOf(379f, 328f, 288f)) assertTrue("$w dp", tail(w, 1.0f))
    }

    @Test fun `the tail drops at font 1_3 on a 360 dp phone and at larger fonts`() {
        assertFalse(tail(328f, 1.3f))
        assertFalse(tail(328f, 2.0f))
        assertFalse(tail(288f, 1.3f))
    }

    @Test fun `the cover slot still fits the tail at font 1_3`() {
        assertTrue(tail(379f, 1.3f))
    }
}
