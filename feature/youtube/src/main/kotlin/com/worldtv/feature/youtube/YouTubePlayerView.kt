package com.worldtv.feature.youtube

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.worldtv.core.designsystem.theme.WorldTvColors

/** Playback states reported by the IFrame API. */
enum class YouTubePlayerState(val code: Int) {
    UNSTARTED(-1),
    ENDED(0),
    PLAYING(1),
    PAUSED(2),
    BUFFERING(3),
    CUED(5),
    ;

    companion object {
        fun fromCode(code: Int): YouTubePlayerState =
            entries.firstOrNull { it.code == code } ?: UNSTARTED
    }
}

/** Commands sent from Kotlin down into the IFrame player. */
sealed interface YouTubeCommand {
    data object Toggle : YouTubeCommand
    data object Play : YouTubeCommand
    data object Pause : YouTubeCommand
    data object ToggleMute : YouTubeCommand
    data class Load(val videoId: String) : YouTubeCommand
}

/**
 * Handle for driving the WebView from Compose without holding the view itself.
 *
 * Remote key handling lives in the Compose layer — see [YouTubePlayerScreen] — and
 * reaches the player through this. The WebView must never take D-pad focus itself:
 * once focus is inside a WebView, its own key handling swallows the remote and the
 * user cannot get back out.
 */
class YouTubePlayerController {
    internal var send: ((YouTubeCommand) -> Unit)? = null

    fun dispatch(command: YouTubeCommand) {
        send?.invoke(command)
    }
}

@Composable
fun rememberYouTubePlayerController(): YouTubePlayerController =
    remember { YouTubePlayerController() }

/**
 * Hosts the YouTube IFrame player.
 *
 * @param onStateChange playback state, so Compose can show a buffering backdrop
 * @param onError IFrame API error code (2, 5, 100, 101, 150)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerView(
    videoId: String,
    controller: YouTubePlayerController,
    onReady: () -> Unit,
    onStateChange: (YouTubePlayerState) -> Unit,
    onError: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bridge = remember(videoId) {
        JsBridge(
            readyCallback = onReady,
            stateCallback = onStateChange,
            errorCallback = onError,
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(WorldTvColors.Surface.toArgb())

                // The remote belongs to Compose. A focusable WebView captures D-pad
                // events and there is no reliable way to hand them back.
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

                settings.apply {
                    javaScriptEnabled = true
                    // Without this the IFrame API refuses to autoplay, and there is no
                    // touch gesture on a TV to satisfy the requirement with.
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    // Nothing here needs local files, and blocking them removes a
                    // whole class of WebView exposure.
                    allowFileAccess = false
                    allowContentAccess = false
                }

                addJavascriptInterface(bridge, IFramePlayerHtml.BRIDGE)
                webViewClient = WebViewClient()

                controller.send = { command -> post { evaluate(command) } }

                loadDataWithBaseURL(
                    // A real https base URL: the IFrame API rejects an embed whose
                    // origin is file:// or empty.
                    IFramePlayerHtml.DEFAULT_ORIGIN,
                    IFramePlayerHtml.build(videoId),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        update = { webView ->
            controller.send = { command -> webView.post { webView.evaluate(command) } }
        },
        onRelease = { webView ->
            controller.send = null
            // Order matters: stop loading and detach before destroying, or the
            // WebView can leak its window token on some vendor builds.
            webView.loadUrl("about:blank")
            webView.removeJavascriptInterface(IFramePlayerHtml.BRIDGE)
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        },
    )

    DisposableEffect(controller) {
        onDispose { controller.send = null }
    }
}

private fun WebView.evaluate(command: YouTubeCommand) {
    val script = when (command) {
        YouTubeCommand.Toggle -> "wtvCommand('toggle')"
        YouTubeCommand.Play -> "wtvCommand('play')"
        YouTubeCommand.Pause -> "wtvCommand('pause')"
        YouTubeCommand.ToggleMute -> "wtvCommand('mute')"
        is YouTubeCommand.Load -> "wtvCommand('load', '${command.videoId.jsEscaped()}')"
    }
    evaluateJavascript(script, null)
}

/**
 * The JS-to-Kotlin bridge.
 *
 * Every method is annotated and does nothing but forward a value — a bridge is
 * reachable by any script the page loads, so it must expose no capability beyond
 * reporting state.
 */
private class JsBridge(
    private val readyCallback: () -> Unit,
    private val stateCallback: (YouTubePlayerState) -> Unit,
    private val errorCallback: (Int) -> Unit,
) {
    @JavascriptInterface
    fun onReady() = readyCallback()

    @JavascriptInterface
    fun onStateChange(code: Int) = stateCallback(YouTubePlayerState.fromCode(code))

    @JavascriptInterface
    fun onError(code: Int) = errorCallback(code)
}
