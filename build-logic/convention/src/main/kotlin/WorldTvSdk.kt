/**
 * The SDK levels, in one place.
 *
 * Plain constants rather than catalog entries: the catalog holds strings, and a
 * `compileSdk` read from it is a string-to-int round trip on every module for no gain.
 */
object WorldTvSdk {
    const val COMPILE = 37
    const val TARGET = 37

    /** Android 6.0. Bare AOSP boxes and the oldest Fire TV sticks still in use. */
    const val MIN = 23

    const val TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
}
