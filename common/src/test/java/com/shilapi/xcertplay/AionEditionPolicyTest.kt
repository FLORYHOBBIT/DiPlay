package com.shilapi.xcertplay

import android.content.Context
import android.content.res.Resources
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class AionEditionPolicyTest {
    private fun edition(): Context {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(context.resources).thenReturn(resources)
        `when`(resources.getBoolean(R.bool.aion_edition)).thenReturn(true)
        return context
    }

    @Test fun aionRemovesVendorAndLanguageControlsButRetainsDriverSettings() {
        val context = edition()
        assertFalse(SettingsCategory.LANGUAGE in AionIntegration.categories(context))
        assertFalse(SettingsCategory.ADVANCED in AionIntegration.categories(context))
        assertTrue(SettingsCategory.NAVIGATION in AionIntegration.categories(context))
        assertFalse(AionIntegration.section(context, SettingsSection.BYD_ADB))
        assertFalse(AionIntegration.section(context, SettingsSection.BYD_NAVIGATION))
        assertFalse(AionIntegration.section(context, SettingsSection.AMBIENT_LIGHTING))
        assertFalse(AionIntegration.section(context, SettingsSection.WHEEL_KEYS))
        assertTrue(AionIntegration.section(context, SettingsSection.AION_MAP))
        assertTrue(AionIntegration.section(context, SettingsSection.AUDIO_ROUTING))
        assertEquals(AppLocale.SIMPLIFIED_CHINESE, AppLocale.preference(context))
    }

    @Test fun aionControlsHaveOneDriverGoalEach() {
        assertTrue(SettingsSection.AION_BLUETOOTH in SettingsInformationArchitecture.sectionsByCategory.getValue(SettingsCategory.CONNECTION))
        assertTrue(SettingsSection.AION_MUSIC in SettingsInformationArchitecture.sectionsByCategory.getValue(SettingsCategory.AUDIO))
        assertTrue(SettingsSection.AION_MAP in SettingsInformationArchitecture.sectionsByCategory.getValue(SettingsCategory.NAVIGATION))
    }
}
