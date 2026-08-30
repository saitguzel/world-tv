package com.worldtv.feature.youtube

/**
 * The page loaded into the WebView.
 *
 * Built as a string rather than an asset so the video id and the origin are
 * interpolated once, at construction, instead of being poked in later through
 * `evaluateJavascript` — one less moving part in a component that already has too
 * many.
 *
 * This uses the official IFrame Player API. Extracting an HLS manifest from YouTube
 * and handing it to ExoPlayer would be simpler and is what most IPTV apps do, but it
 * violates YouTube's terms of service and is the kind of thing that gets an app pulled.
 */
object IFramePlayerHtml {

    /**
     * @param videoId the broadcast to play
     * @param originUrl sent as the IFrame API `origin` parameter; YouTube requires a
     *   plausible origin and rejects playback without one
     */
    fun build(videoId: String, originUrl: String = DEFAULT_ORIGIN): String = """
        <!DOCTYPE html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
            <style>
              html, body { margin: 0; padding: 0; background: #0F0F0F; overflow: hidden; }
              /* The iframe fills the surface; all chrome is drawn by Compose on top. */
              #player { position: fixed; top: 0; left: 0; width: 100%; height: 100%; border: 0; }
            </style>
          </head>
          <body>
            <div id="player"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
              var player;
              var ready = false;

              function onYouTubeIframeAPIReady() {
                player = new YT.Player('player', {
                  videoId: '${videoId.jsEscaped()}',
                  playerVars: {
                    autoplay: 1,
                    controls: 0,       // Compose draws the controls
                    modestbranding: 1,
                    rel: 0,
                    fs: 0,
                    playsinline: 1,
                    iv_load_policy: 3, // no annotation overlays
                    origin: '${originUrl.jsEscaped()}'
                  },
                  events: {
                    onReady: function () {
                      ready = true;
                      player.playVideo();
                      ${BRIDGE}.onReady();
                    },
                    onStateChange: function (e) { ${BRIDGE}.onStateChange(e.data); },
                    onError: function (e) { ${BRIDGE}.onError(e.data); }
                  }
                });
              }

              // Called from Kotlin when a remote key needs to reach the player.
              function wtvCommand(name, value) {
                if (!ready || !player) return;
                switch (name) {
                  case 'play':   player.playVideo(); break;
                  case 'pause':  player.pauseVideo(); break;
                  case 'toggle':
                    if (player.getPlayerState() === YT.PlayerState.PLAYING) {
                      player.pauseVideo();
                    } else {
                      player.playVideo();
                    }
                    break;
                  case 'mute':   player.isMuted() ? player.unMute() : player.mute(); break;
                  case 'volume': player.setVolume(value); break;
                  case 'load':   player.loadVideoById(value); break;
                }
              }
            </script>
          </body>
        </html>
    """.trimIndent()

    /** Name the Kotlin bridge object is exposed under. */
    const val BRIDGE = "WorldTvBridge"

    /**
     * A plausible https origin. YouTube refuses embedded playback from `file://` and
     * from an empty origin, so the WebView loads this page with a base URL to match.
     */
    const val DEFAULT_ORIGIN = "https://worldtv.local"
}

/**
 * Escapes a value for inclusion in a single-quoted JS string literal.
 *
 * The video id comes from the YouTube API rather than from a user, but it still ends
 * up inside a script this app generates — quoting it properly costs nothing and keeps
 * a malformed or hostile id from breaking out of the literal.
 */
internal fun String.jsEscaped(): String = buildString(length + 8) {
    for (ch in this@jsEscaped) {
        when (ch) {
            '\\' -> append("\\\\")
            '\'' -> append("\\'")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '<' -> append("\\u003C")
            '>' -> append("\\u003E")
            '&' -> append("\\u0026")
            else -> append(ch)
        }
    }
}
