package com.worldtv.feature.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import com.worldtv.data.health.PlaybackSignal

/**
 * Translates a Media3 error into a health signal.
 *
 * The classification matters more than it looks. Counting every playback error against
 * the stream would eliminate working channels whenever the user's Wi-Fi drops or their
 * box lacks a codec — and those two causes together are a large share of real errors.
 */
// HttpDataSource.InvalidResponseCodeException is @UnstableApi; it is the only way
// to read the HTTP status off a PlaybackException, which this mapper is built on.
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
object PlaybackErrorMapper {

    fun toSignal(error: PlaybackException): PlaybackSignal = when (error.errorCode) {
        // A concrete HTTP status. 403/451 read as a region lock, everything else is
        // a real failure of this URL.
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val status = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            when (status) {
                HTTP_FORBIDDEN, HTTP_UNAVAILABLE_LEGAL -> PlaybackSignal.GeoBlocked(status)
                null -> PlaybackSignal.Failed(error.errorCode)
                else -> PlaybackSignal.Failed(status)
            }
        }

        // The user's own connectivity. Says nothing about the stream.
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        -> PlaybackSignal.NetworkFailure(error.errorCode)

        // A broken manifest or container is genuinely this stream's fault.
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_TIMEOUT,
        -> PlaybackSignal.Failed(error.errorCode)

        // Decoder faults are a property of this box, not of the stream. A device with
        // no HEVC decoder must not delete HEVC channels for everyone using the app.
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        -> PlaybackSignal.DeviceLocalFailure(error.errorCode)

        // DRM is out of scope for a directory of free-to-air streams, but a DRM error
        // is still this stream being unplayable here rather than broken.
        PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
        PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
        PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
        -> PlaybackSignal.DeviceLocalFailure(error.errorCode)

        else -> PlaybackSignal.Failed(error.errorCode)
    }

    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_UNAVAILABLE_LEGAL = 451
}
