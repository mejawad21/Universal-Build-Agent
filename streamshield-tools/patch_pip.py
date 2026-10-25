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

# Add a center play/pause button to the existing mini-player overlay.
marker="    invoke-virtual {v13, v12}, Landroid/widget/Button;->setOnClickListener(Landroid/view/View$OnClickListener;)V"
if s.count(marker)!=1: raise SystemExit("Mini-player insertion point not unique")
addition="""

    # StreamShield mini-player play/pause overlay
    new-instance v12, Landroid/widget/Button;
    invoke-direct {v12, v0}, Landroid/widget/Button;-><init>(Landroid/content/Context;)V
    const-string v14, "⏯"
    invoke-virtual {v12, v14}, Landroid/widget/Button;->setText(Ljava/lang/CharSequence;)V
    new-instance v14, Landroid/widget/FrameLayout$LayoutParams;
    const/16 v15, 0x40
    invoke-virtual {v0, v15}, Lcom/example/streamshield/MainActivity;->dp(I)I
    move-result v15
    const/16 v1, 0x11
    invoke-direct {v14, v15, v15, v1}, Landroid/widget/FrameLayout$LayoutParams;-><init>(III)V
    iget-object v1, v0, Lcom/example/streamshield/MainActivity;->miniHolder:Landroid/widget/FrameLayout;
    invoke-virtual {v1, v12, v14}, Landroid/widget/FrameLayout;->addView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V
    new-instance v1, Lcom/example/streamshield/MiniPlayPauseClick;
    invoke-direct {v1, v0}, Lcom/example/streamshield/MiniPlayPauseClick;-><init>(Lcom/example/streamshield/MainActivity;)V
    invoke-virtual {v12, v1}, Landroid/widget/Button;->setOnClickListener(Landroid/view/View$OnClickListener;)V
"""
# Avoid changing existing live register variables after the insertion: new method on activity instead.
addition=addition.replace('    const/16 v15, 0x40\n    invoke-virtual {v0, v15}, Lcom/example/streamshield/MainActivity;->dp(I)I\n    move-result v15','    const/16 v15, 0xa0').replace('    const/16 v1, 0x11','    const/16 v1, 0x11')
# Insert after stop listener; preserve v1 used later by restoring its layout-param reference from v14 (not safe).
# Use a separate helper invoked by existing MainActivity, requiring only one insertion and one temporary register.
addition="""
    invoke-static {v0}, Lcom/example/streamshield/MiniControls;->attach(Lcom/example/streamshield/MainActivity;)V
"""
s=s.replace(marker,marker+addition)
p.write_text(s)
helper=r"""
.class public Lcom/example/streamshield/MiniControls;
.super Ljava/lang/Object;
.implements Landroid/view/View$OnClickListener;

.field private activity:Lcom/example/streamshield/MainActivity;

.method public constructor <init>(Lcom/example/streamshield/MainActivity;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/example/streamshield/MiniControls;->activity:Lcom/example/streamshield/MainActivity;
    return-void
.end method

.method public static attach(Lcom/example/streamshield/MainActivity;)V
    .locals 5
    iget-object v0, p0, Lcom/example/streamshield/MainActivity;->miniHolder:Landroid/widget/FrameLayout;
    if-eqz v0, :done
    new-instance v1, Landroid/widget/Button;
    invoke-direct {v1, p0}, Landroid/widget/Button;-><init>(Landroid/content/Context;)V
    const-string v2, "⏯"
    invoke-virtual {v1, v2}, Landroid/widget/Button;->setText(Ljava/lang/CharSequence;)V
    new-instance v2, Landroid/widget/FrameLayout$LayoutParams;
    const/16 v3, 0xa0
    const/16 v4, 0xa0
    const/16 v3, 0x11
    const/16 v4, 0xa0
    invoke-direct {v2, v4, v4, v3}, Landroid/widget/FrameLayout$LayoutParams;-><init>(III)V
    invoke-virtual {v0, v1, v2}, Landroid/widget/FrameLayout;->addView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V
    new-instance v2, Lcom/example/streamshield/MiniControls;
    invoke-direct {v2, p0}, Lcom/example/streamshield/MiniControls;-><init>(Lcom/example/streamshield/MainActivity;)V
    invoke-virtual {v1, v2}, Landroid/widget/Button;->setOnClickListener(Landroid/view/View$OnClickListener;)V
    :done
    return-void
.end method

.method public onClick(Landroid/view/View;)V
    .locals 3
    iget-object v0, p0, Lcom/example/streamshield/MiniControls;->activity:Lcom/example/streamshield/MainActivity;
    iget-object v1, v0, Lcom/example/streamshield/MainActivity;->web:Landroid/webkit/WebView;
    if-eqz v1, :done
    const-string v2, "(function(){var v=document.querySelector('video');if(!v)return 'missing';if(v.paused){v.play().catch(function(e){console.log('StreamShield play blocked: '+e)});return 'play'}v.pause();return 'pause'})()"
    const/4 v0, 0x0
    invoke-virtual {v1, v2, v0}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V
    :done
    return-void
.end method
"""
(Path("decoded/smali_classes2/com/example/streamshield/MiniControls.smali")).write_text(helper)
print("Added mini-player play/pause control")

# Hide legacy Home/Go/Download bars, while retaining Back/Search and mini-player access
# through the existing button until swipe gesture is verified.
# Set actions and controls visibility at the end of onCreate to avoid altering their initialization.
anchor="    invoke-virtual {p0, v0}, Lcom/example/streamshield/MainActivity;->setContentView(Landroid/view/View;)V"
if anchor in s:
    s=s.replace(anchor,anchor+"""
    iget-object v0, p0, Lcom/example/streamshield/MainActivity;->controls:Landroid/widget/LinearLayout;
    const/16 v1, 0x8
    invoke-virtual {v0, v1}, Landroid/widget/LinearLayout;->setVisibility(I)V
    iget-object v0, p0, Lcom/example/streamshield/MainActivity;->actions:Landroid/widget/LinearLayout;
    invoke-virtual {v0, v1}, Landroid/widget/LinearLayout;->setVisibility(I)V
""",1)
    p.write_text(s)
    print("Hidden legacy toolbar")
else:
    print("Legacy toolbar anchor not matched; leaving unchanged")
