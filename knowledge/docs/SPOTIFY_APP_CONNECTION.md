# Spotify connection from Room on Android

In Spotify Developer Dashboard, open your application (or create one), enter its Client ID into the Room app under Settings → Connections, add this exact Redirect URI to your Spotify app settings, and save:

```text
in.roomcontroller.app://spotify/callback
```

The Android app receives this callback on whichever Android phone runs the flow.
The optional computer helper continues to use `http://127.0.0.1:8765/callback`;
that address is not the Android callback. Both can be registered in Spotify.

Pair Room with the ESP32, connect to the room's Wi-Fi with internet access, then
open Music and choose **Connect Spotify**. Sign in and approve access in the
system browser. Room exchanges the code using PKCE and provisions the refresh
token over the existing certificate-pinned HTTPS connection to the paired ESP32.
No client secret, computer helper, or manual refresh-token entry is needed.
The temporary verifier, expiring state, and target pairing are encrypted with
the existing Android Keystore vault. The verifier is consumed before token
exchange. Refresh tokens are not displayed or logged by this flow.

The room stores one Spotify account. Other phones paired to that room control
that account; connecting another account replaces it. This does not grant remote
access from outside the local network. Sign-in is implemented for the Android app;
there is no iOS or desktop Room app in this project.

Open Spotify on the desired phone, computer, or supported Spotify Connect speaker
and start a track, then use Music → Devices and select the output. Playback depends
on Spotify account eligibility, API access, and device support. A Bluetooth speaker
must be paired to the phone/computer playing Spotify. The ESP32 controls playback;
it does not decode or stream Spotify audio.

The companion Python backend retains its existing browser authorization and exact
HTTPS server callback at `/spotify/callback`. Its configuration is separate from
the Android-to-ESP32 flow above.

If sign-in is denied, check the Spotify app's user access list and Premium/API
eligibility. If the final device request fails, reconnect the phone to the room's
Wi-Fi and start Connect Spotify again. If a different app captures the custom
scheme, remove that handler and reconnect. A verified HTTPS App Link would require
a separately managed domain and association file; none is deployed here.

## Verification boundary

Unit checks cover the RFC 7636 challenge vector, callback destination, state,
expiry, denial, duplicate query fields, and authorization parameters. Real Spotify
consent, ESP32 credential persistence, and playback on multiple outputs require
the registered callback and a connected device; a build or unit pass does not
prove those steps.

References: [Spotify PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow),
[custom redirect scheme support](https://developer.spotify.com/blog/2025-02-12-increasing-the-security-requirements-for-integrating-with-spotify).
