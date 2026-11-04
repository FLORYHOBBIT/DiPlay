package com.shilapi.xcertplay

import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.setup.SetupGuide
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], qualifiers = "zh-rCN-w1024dp-h600dp")
class AionStartupUiTest {
    @Test fun freshAionInstallSkipsTheBydWizardAndOffersConnectionSetup() {
        // The common test APK is the generic edition. Opt into the real edition branch while
        // leaving firmware detection intact, so no vendor service is contacted by this UI test.
        Mockito.mockStatic(AionIntegration::class.java, Answer { invocation ->
            if (invocation.method.name == "enabled") true else invocation.callRealMethod()
        }).use {
            val context = RuntimeEnvironment.getApplication()
            // Avoid requiring a private accessory identity just to test the setup screen.
            AirPlayPersistence.saveMfiTarget(context, MfiTarget.USB_CH341)
            val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
            val activity = controller.get()
            try {
                assertTrue(SetupGuide.seen(activity))
                assertEquals("home", ReflectionHelpers.getField<String>(activity, "page"))
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "openSetupGuide", ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
                assertEquals("connection", ReflectionHelpers.getField<String>(activity, "page"))
                ReflectionHelpers.setField(activity, "page", "settings")
                ReflectionHelpers.setField(activity, "settingsCategory", SettingsCategory.LANGUAGE)
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")
                assertEquals(SettingsCategory.OVERVIEW, ReflectionHelpers.getField<SettingsCategory>(activity, "settingsCategory"))
            } finally { controller.pause().stop().destroy() }
        }
    }
}
