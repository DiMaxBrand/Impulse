package eu.siacs.conversations.update

import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionOrderingTest {
    private fun compare(a: String, b: String): Int = UpdateChecker.compareSemver(
        requireNotNull(UpdateChecker.parseVersion(a)), requireNotNull(UpdateChecker.parseVersion(b))
    )

    @Test fun unifiedSequenceOrdersAllChannelsByNumber() {
        assertTrue(compare("1.16.0-alpha.3", "1.16.0-rc.2") > 0)
        assertTrue(compare("1.16.0-rc.4", "1.16.0-alpha.3") > 0)
        assertTrue(compare("1.16.0", "1.16.0-rc.99") > 0)
        assertTrue(compare("1.16.0-alpha.1", "1.15.1") > 0)
        assertTrue(compare("1.15.1-rc.1", "1.15.1-beta.99") > 0)
    }
}
