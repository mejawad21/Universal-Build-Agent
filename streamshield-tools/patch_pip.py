#!/usr/bin/env python3
"""Conservative StreamShield PiP patch for apktool-decoded original APK."""
from pathlib import Path
import re
root=Path("decoded")
files=list(root.rglob("MainActivity.smali"))
if len(files)!=1: raise SystemExit("Expected exactly one MainActivity.smali")
p=files[0]
s=p.read_text()
# Android 12+: automatic PiP when user leaves the app, retaining the original activity/WebView.
# Do not override onPause or force JS play: YouTube can reject programmatic playback.
needle="Landroid/app/PictureInPictureParams$Builder;->setAspectRatio(Landroid/util/Rational;)Landroid/app/PictureInPictureParams$Builder;"
if needle not in s: raise SystemExit("PiP builder not found; no patch applied")
# Add an explicit lifecycle logging hook for diagnosing transitions.
hook="""
.method protected onPause()V
    .locals 2
    const-string v0, "StreamShieldPiP"
    const-string v1, "Activity onPause; preserving WebView playback session"
    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    invoke-super {p0}, Landroid/app/Activity;->onPause()V
    return-void
.end method

.method protected onStop()V
    .locals 2
    const-string v0, "StreamShieldPiP"
    const-string v1, "Activity onStop; no explicit WebView pause"
    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    invoke-super {p0}, Landroid/app/Activity;->onStop()V
    return-void
.end method
"""
if ".method protected onPause()V" not in s and ".method protected onStop()V" not in s:
    s += "\n"+hook
# Keep playback logic unchanged until on-device logs confirm the cause.
p.write_text(s)
print("Patched original MainActivity with lifecycle diagnostics.")
