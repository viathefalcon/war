# W.A.R. for Android (BETA)

Waveson Audio Remote for Android (BETA) accepts audio-related commands over a custom Bluetooth Low Energy service and executes them on the local device, in part by simulating key presses of media buttons (e.g. play/pause).

_BETA_ because there are a couple of issues which, if overlooked, do not prevent the app from being mostly useful, but probably do prevent it from being universally useful (even amongst the select group of people who might be in the market for such a thing):
* Hooking up to the Bluetooth LE service can be a bit hit and miss, sometimes requiring multiple retries. This seems especially acute when the service is hosted on, say, a 2012 MacBook Pro in an open-plan office (a.k.a. the target environment)..
* The app blindly issues local commands, and key presses, without any regard to or awareness of the state of audio playback on the device. So, if a media session/player is active, and responds to the media keys the app simulates, then the whole thing works pretty seamlessly. Otherwise, not so much: not all media/streaming apps necessarily respond to the media keys (and I have not tried many), and those that do do not respond all the time, nor consistently so; YouTube Music, in particular, seems to just give up after a few minutes after playback is paused.

To mitigate the first issue, I added an option to automatically re-connect when the BLE stack reports the connection to the peripheral as having dropped - essentially, automating what I'd been doing manually. And, this seems to work: if the connection doesn't drop immediately after being established, it tends to stay up for hours at a time, no problem. But, the experience isn't great: to the un-initiated, I imagine it could even seem as if the app was spiraling out of control.

As for the second issue, a more holistic solution is probably required..

In the meantime, inasmuch as I can bounce through shuffled music on my phone without having take my hands off the keyboard and/or pick up/touch the phone, set up playlists, etc., once I get over/around the first issue, the overall experience is good enough for me.