package com.worldtv.core.common

import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The country the device claims to be in.
 *
 * No permissions are involved: reading `Locale` and `TimeZone` tells us what region the
 * user set the device up for, which is the only honest answer a media app can get
 * without a `READ_PHONE_STATE` prompt nobody wants on first launch. The mapping is kept
 * pure and exhaustive enough to test on the JVM.
 */
@Singleton
class DeviceCountry @Inject constructor() {

    /**
     * ISO 3166-1 alpha-2 for the device region, or null when it cannot be determined.
     *
     * The locale is usually set to a country-qualified entry («tr-TR», «en-GB»); when
     * it is not (some builds ship «en» with no region) the timezone falls back to a
     * small mapping of the common zones.
     */
    fun currentCountryCode(): String? = DeviceCountryDetector.detect(
        localeCountryIso = Locale.getDefault().country,
        timeZoneId = TimeZone.getDefault().id,
    )
}

/**
 * The rule, split from [DeviceCountry] so it can be tested without a device.
 *
 * Same shape as [FormFactorDetector]: the Android bits are a thin envelope around a
 * pure function.
 */
object DeviceCountryDetector {

    /**
     * @param localeCountryIso `Locale.getDefault().country`, or whatever the call site
     *   hands over — an empty string or a two-letter ISO code.
     * @param timeZoneId `TimeZone.getDefault().id`, e.g. "Europe/Istanbul".
     */
    fun detect(localeCountryIso: String?, timeZoneId: String): String? {
        val fromLocale = localeCountryIso
            ?.takeIf { it.length == 2 && it.all(Char::isLetter) }
            ?.uppercase(Locale.ROOT)
        if (fromLocale != null) return fromLocale

        return TIMEZONE_COUNTRY[timeZoneId]
    }

    /**
     * `ZoneId` to ISO alpha-2 for the zones that matter in practice.
     *
     * Deliberately incomplete: a phone with an unmapped zone is told to fall back to
     * the app's own default, which is better than guessing wrong. Region-prefix
     * matching was rejected because it guesses — «America/Indiana/Indianapolis» is the
     * US, but a table has to say so explicitly to stay honest.
     */
    private val TIMEZONE_COUNTRY = mapOf(
        "Europe/Istanbul" to "TR",
        "Europe/London" to "GB",
        "Europe/Berlin" to "DE",
        "Europe/Paris" to "FR",
        "Europe/Madrid" to "ES",
        "Europe/Rome" to "IT",
        "Europe/Amsterdam" to "NL",
        "Europe/Brussels" to "BE",
        "Europe/Vienna" to "AT",
        "Europe/Warsaw" to "PL",
        "Europe/Prague" to "CZ",
        "Europe/Athens" to "GR",
        "Europe/Helsinki" to "FI",
        "Europe/Stockholm" to "SE",
        "Europe/Oslo" to "NO",
        "Europe/Copenhagen" to "DK",
        "Europe/Lisbon" to "PT",
        "Europe/Bucharest" to "RO",
        "Europe/Budapest" to "HU",
        "Europe/Kyiv" to "UA",
        "Europe/Zurich" to "CH",
        "Europe/Dublin" to "IE",
        "Europe/Moscow" to "RU",
        "America/New_York" to "US",
        "America/Chicago" to "US",
        "America/Denver" to "US",
        "America/Los_Angeles" to "US",
        "America/Toronto" to "CA",
        "America/Vancouver" to "CA",
        "America/Mexico_City" to "MX",
        "America/Sao_Paulo" to "BR",
        "America/Argentina/Buenos_Aires" to "AR",
        "Asia/Tokyo" to "JP",
        "Asia/Seoul" to "KR",
        "Asia/Shanghai" to "CN",
        "Asia/Kolkata" to "IN",
        "Asia/Dubai" to "AE",
        "Asia/Riyadh" to "SA",
        "Asia/Jerusalem" to "IL",
        "Asia/Bangkok" to "TH",
        "Asia/Singapore" to "SG",
        "Asia/Jakarta" to "ID",
        "Asia/Manila" to "PH",
        "Asia/Karachi" to "PK",
        "Asia/Dhaka" to "BD",
        "Asia/Ho_Chi_Minh" to "VN",
        "Asia/Taipei" to "TW",
        "Asia/Hong_Kong" to "HK",
        "Australia/Sydney" to "AU",
        "Australia/Melbourne" to "AU",
        "Australia/Brisbane" to "AU",
        "Australia/Perth" to "AU",
        "Pacific/Auckland" to "NZ",
        "Africa/Cairo" to "EG",
        "Africa/Johannesburg" to "ZA",
        "Africa/Lagos" to "NG",
        "Africa/Nairobi" to "KE",
        "Africa/Casablanca" to "MA",
    )
}