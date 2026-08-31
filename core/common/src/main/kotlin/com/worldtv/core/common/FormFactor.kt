package com.worldtv.core.common

/** Which interaction model the app should present. */
enum class FormFactor { TV, MOBILE }

/**
 * Decides the form factor from platform facts.
 *
 * Split out of [DeviceCapabilities] so the rule is testable on the JVM: the alternative
 * is an emulator, and getting this wrong is not a subtle bug — a TV that lands in the
 * mobile tree shows a touch UI to a device with no touchscreen, and a tablet that lands
 * in the TV tree shows `androidx.tv.material3` components, whose click path is D-pad
 * key events only and which therefore do not respond to taps at all.
 *
 * The ordering is the substance. `FEATURE_LEANBACK` is deliberately not the primary
 * test: it is a static package-manager fact that some tablets also report, whereas the
 * UI mode reflects what the device is actually running as right now.
 */
object FormFactorDetector {

    /**
     * @param isTelevisionUiMode `UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION`
     * @param isNormalUiMode `… == UI_MODE_TYPE_NORMAL` — a phone, tablet or anything
     *   that has not declared itself a special mode
     * @param hasTouchscreen `PackageManager.FEATURE_TOUCHSCREEN`
     * @param hasLeanback `PackageManager.FEATURE_LEANBACK`
     * @param hasTelevisionFeature `"android.hardware.type.television"` — the older
     *   marker, still the only one some bare AOSP boxes set at API 23
     */
    fun detect(
        isTelevisionUiMode: Boolean,
        isNormalUiMode: Boolean,
        hasTouchscreen: Boolean,
        hasLeanback: Boolean,
        hasTelevisionFeature: Boolean,
    ): FormFactor = when {
        // 1. The runtime mode is authoritative when it says television.
        isTelevisionUiMode -> FormFactor.TV

        // 2. A device running in normal mode *with a touchscreen* is a phone or tablet,
        //    even if it also advertises leanback — which is exactly the case that a
        //    plain `hasLeanback` check gets wrong.
        isNormalUiMode && hasTouchscreen -> FormFactor.MOBILE

        // 3. Otherwise the static markers decide. Reached when the UI mode is undefined
        //    or unreadable, or when there is no touchscreen at all — neither of which
        //    describes a phone.
        hasLeanback || hasTelevisionFeature -> FormFactor.TV

        else -> FormFactor.MOBILE
    }
}
