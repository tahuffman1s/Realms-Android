package com.realmsoffate.game.data.updater

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class InstallLauncherTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val tempApk = File(ctx.cacheDir, "updates").let { dir ->
        dir.mkdirs()
        File(dir, "fake.apk").apply { writeBytes(ByteArray(8)) }
    }

    @Test fun `buildIntent returns installer intent with file uri`() {
        val intent = InstallLauncher.buildIntent(ctx, tempApk)
        assertNotNull(intent)
        assertNotNull(intent.data)
        val expectedAuthority = "${ctx.packageName}.updates"
        assertTrue(
            "expected authority $expectedAuthority, got ${intent.data!!.authority}",
            intent.data!!.authority == expectedAuthority
        )
        assertTrue(intent.action == android.content.Intent.ACTION_VIEW)
    }

    @Test fun `signatureMismatch does not throw on missing data`() {
        // Robolectric's package archive parsing won't find signatures in our 8-byte
        // fake APK; the helper should return false rather than crash.
        val mismatch = InstallLauncher.signatureMismatchAgainstInstalled(ctx, tempApk)
        assertTrue("expected non-throw; mismatch=$mismatch", mismatch == true || mismatch == false)
    }
}
