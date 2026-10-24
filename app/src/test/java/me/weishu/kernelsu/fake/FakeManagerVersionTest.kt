package me.weishu.kernelsu.fake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The lab lets the user disguise the manager as a release-shaped `x.y.z`
 * version. That string has to yield a versionCode on its own — otherwise the
 * home page would claim `3.1.2` while the update check still compared against
 * the fork's build number.
 */
class FakeManagerVersionTest {

    @Test
    fun aReleaseNameMapsToKernelSuVersionCodeScheme() {
        assertEquals(30000L, FakeManagerVersion.codeFromName("3.0.0"))
        assertEquals(30102L, FakeManagerVersion.codeFromName("3.1.2"))
    }

    @Test
    fun aTwoPartVersionIsAccepted() {
        assertEquals(30100L, FakeManagerVersion.codeFromName("3.1"))
    }

    @Test
    fun prefixesAndSuffixesAreIgnored() {
        assertEquals(30100L, FakeManagerVersion.codeFromName("v3.1.0"))
        assertEquals(30100L, FakeManagerVersion.codeFromName("3.1.0-rc1"))
        assertEquals(30100L, FakeManagerVersion.codeFromName(" 3.1.0 "))
    }

    @Test
    fun textThatIsNotAVersionDerivesNothing() {
        assertNull(FakeManagerVersion.codeFromName(""))
        assertNull(FakeManagerVersion.codeFromName("13000"))
        assertNull(FakeManagerVersion.codeFromName("3.1.0.0"))
        assertNull(FakeManagerVersion.codeFromName("3."))
        assertNull(FakeManagerVersion.codeFromName("3333.1"))
        assertNull(FakeManagerVersion.codeFromName("3.x.0"))
        assertNull(FakeManagerVersion.codeFromName("0.0.0"))
    }
}
