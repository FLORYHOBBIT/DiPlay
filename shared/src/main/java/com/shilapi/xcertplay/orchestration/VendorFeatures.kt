package com.shilapi.xcertplay.orchestration

import android.content.Context
import com.shilapi.xcertplay.shared.R

/** Product boundary: AION uses its own instrument outputs and never initializes BYD integrations. */
object VendorFeatures {
    @JvmStatic fun enabled(context: Context): Boolean = try {
        context.resources.getBoolean(R.bool.config_vendor_vehicle_features)
    } catch (_: android.content.res.Resources.NotFoundException) {
        // Source-only shared tests do not merge Android resources; preserve the generic product.
        true
    }
}
