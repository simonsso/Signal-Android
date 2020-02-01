# Signal with private patches

## Background
In signal for Android 4.51 changes to how UI works initiates calls were made. Since I am not a fan of unmotivated confirm requesters I
decided to use the source and remove this feature. When I had opened the box of Pandora and begun maintaining my own fork other improvements could also be done.

Check version history to judge if I'm still maintaining this fork or not.

## List of changes
1. Click on "Call" or "Video" icon will immediately start a call [373ee3ef763b23b1e439c03f8c3641e049fb6901](https://github.com/simonsso/Signal-Android/commit/373ee3ef763b23b1e439c03f8c3641e049fb6901 )
2. Local display - viewfinder for video calls is smaller not to interfere with call [982762a8cc2f066543163bec6f8e5deb4ca9a186](https://github.com/simonsso/Signal-Android/commit/982762a8cc2f066543163bec6f8e5deb4ca9a186)
3. Toolbar is semitransparent when making video calls. [80cb4f04ec9430e31b3510a0aeefbe06a8d6f317](https://github.com/simonsso/Signal-Android/commit/80cb4f04ec9430e31b3510a0aeefbe06a8d6f317)
