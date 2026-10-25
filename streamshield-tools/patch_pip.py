#!/usr/bin/env python3
"""Patch original StreamShield APK smali; fail fast when upstream layout differs."""
from pathlib import Path
import re
p=Path("decoded/smali_classes2/com/example/streamshield/MainActivity.smali")
s=p.read_text()
def replace_once(old,new):
 global s
 assert s.count(old)==1, ("Expected exactly one occurrence",old[:100],s.count(old))
 s=s.replace(old,new,1)
# Remove old two toolbar rows; the YouTube page occupies the freed space.
anchor='    invoke-virtual {p0, v1}, Lcom/example/streamshield/MainActivity;->setContentView(Landroid/view/View;)V'
replace_once(anchor,anchor+"""
    iget-object v1, p0, Lcom/example/streamshield/MainActivity;->controls:Landroid/widget/LinearLayout;
    const/16 v2, 0x8
    invoke-virtual {v1, v2}, Landroid/widget/LinearLayout;->setVisibility(I)V
    iget-object v1, p0, Lcom/example/streamshield/MainActivity;->actions:Landroid/widget/LinearLayout;
    invoke-virtual {v1, v2}, Landroid/widget/LinearLayout;->setVisibility(I)V
    iget-object v1, p0, Lcom/example/streamshield/MainActivity;->web:Landroid/webkit/WebView;
    new-instance v2, Lcom/example/streamshield/SwipeDown;
    invoke-direct {v2, p0}, Lcom/example/streamshield/SwipeDown;-><init>(Lcom/example/streamshield/MainActivity;)V
    invoke-virtual {v1, v2}, Landroid/webkit/WebView;->setOnTouchListener(Landroid/view/View$OnTouchListener;)V
""")
# Mini-player play/pause button: reuse original WebView, do not reload video.
marker='    invoke-virtual {v13, v12}, Landroid/widget/Button;->setOnClickListener(Landroid/view/View$OnClickListener;)V'
replace_once(marker,marker+"""
    invoke-static {v0}, Lcom/example/streamshield/MiniControls;->attach(Lcom/example/streamshield/MainActivity;)V
""")
# Enter PiP on Android Home gesture. The original startPip checks for an active HTML video.
s+="""
.method protected onUserLeaveHint()V
    .locals 0
    invoke-super {p0}, Landroid/app/Activity;->onUserLeaveHint()V
    iget-boolean p0, p0, Lcom/example/streamshield/MainActivity;->miniMode:Z
    return-void
.end method
"""
# Correct onUserLeaveHint: do not clobber p0 object register.
s=s[:s.rfind(".method protected onUserLeaveHint()V")]+"""
.method protected onUserLeaveHint()V
    .locals 4
    invoke-super {p0}, Landroid/app/Activity;->onUserLeaveHint()V
    iget-boolean v0, p0, Lcom/example/streamshield/MainActivity;->miniMode:Z
    if-nez v0, :done
    new-instance v0, Landroid/app/PictureInPictureParams$Builder;
    invoke-direct {v0}, Landroid/app/PictureInPictureParams$Builder;-><init>()V
    new-instance v1, Landroid/util/Rational;
    const/16 v2, 0x10
    const/16 v3, 0x9
    invoke-direct {v1, v2, v3}, Landroid/util/Rational;-><init>(II)V
    invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams$Builder;->setAspectRatio(Landroid/util/Rational;)Landroid/app/PictureInPictureParams$Builder;
    invoke-virtual {v0}, Landroid/app/PictureInPictureParams$Builder;->build()Landroid/app/PictureInPictureParams;
    move-result-object v0
    invoke-virtual {p0, v0}, Lcom/example/streamshield/MainActivity;->enterPictureInPictureMode(Landroid/app/PictureInPictureParams;)Z
    move-result v0
    if-eqz v0, :done
    iget-object v0, p0, Lcom/example/streamshield/MainActivity;->web:Landroid/webkit/WebView;
    if-eqz v0, :done
    const-string v1, "(function(){var v=document.querySelector('video');if(v&&v.paused){v.play().catch(function(e){console.log('PiP resume:',e)})}})()"
    const/4 v2, 0x0
    invoke-virtual {v0, v1, v2}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V
    :done
    return-void
.end method
"""
# Keep the original playback view visible and make video occupy PiP bounds.
# The PiP transition otherwise shrinks the full mobile webpage rather than its video.
pip_callback='.method public onPictureInPictureModeChanged(ZLandroid/content/res/Configuration;)V'
assert s.count(pip_callback)==1
pip_start=s.index(pip_callback)
pip_end=s.index('.end method',pip_start)
section=s[pip_start:pip_end]
callback_anchor='    invoke-super {p0, p1, p2}, Landroid/app/Activity;->onPictureInPictureModeChanged(ZLandroid/content/res/Configuration;)V'
assert section.count(callback_anchor)==1
section=section.replace(callback_anchor,callback_anchor+"""
    # Preserve fullscreen custom view if Chromium created one; otherwise
    # crop the HTML video to fill the PiP activity surface.
    iget-object v0, p0, Lcom/example/streamshield/MainActivity;->web:Landroid/webkit/WebView;
    if-eqz v0, :ss_pip_layout_done
    if-eqz p1, :ss_pip_layout_restore
    const-string v1, "(function(){if(document.getElementById('ss-pip-style'))return;var v=document.querySelector('video');if(!v)return;var s=document.createElement('style');s.id='ss-pip-style';s.textContent='html,body{margin:0!important;overflow:hidden!important;background:#000!important}video{position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;object-fit:contain!important;z-index:2147483647!important;visibility:visible!important}';document.head.appendChild(s)})()"
    goto :ss_pip_js
    :ss_pip_layout_restore
    const-string v1, "(function(){var s=document.getElementById('ss-pip-style');if(s)s.remove()})()"
    :ss_pip_js
    const/4 v2, 0x0
    invoke-virtual {v0, v1, v2}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V
    :ss_pip_layout_done
""",1)
s=s[:pip_start]+section+s[pip_end:]
# Ensure original PiP callback resumes video only when previously playing.
pip_enter='    invoke-virtual {p0, v0}, Lcom/example/streamshield/MainActivity;->enterPictureInPictureMode(Landroid/app/PictureInPictureParams;)Z'
assert s.count(pip_enter)==1
s=s.replace(pip_enter,"    const/4 v1, 0x1\n    iput-boolean v1, p0, Lcom/example/streamshield/MainActivity;->pipWasPlaying:Z\n"+pip_enter,1)
print("Updated PiP lifecycle and HTML video layout")
# Re-arm the swipe gesture on the browsing WebView created by mini mode.
# This WebView remains visible after the mini-player is closed.
browse_anchor='    iput-object v0, p0, Lcom/example/streamshield/MainActivity;->browseWeb:Landroid/webkit/WebView;'
replace_once(browse_anchor,browse_anchor+"""
    new-instance v1, Lcom/example/streamshield/SwipeDown;
    invoke-direct {v1, p0}, Lcom/example/streamshield/SwipeDown;-><init>(Lcom/example/streamshield/MainActivity;)V
    invoke-virtual {v0, v1}, Landroid/webkit/WebView;->setOnTouchListener(Landroid/view/View$OnTouchListener;)V
""")
# Keep the gesture armed when closing the mini-player and reset the native state.
# Do not remove or reload the browsing WebView.
p.write_text(s)
d=p.parent
(d/"MiniControls.smali").write_text(r'''.class public Lcom/example/streamshield/MiniControls;
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
    const/16 v3, 0x11
    const/16 v4, 0x90
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
    const-string v2, "(function(){var v=document.querySelector('video');if(!v)return;if(v.paused){v.play().catch(function(e){console.log(e)})}else{v.pause()}})()"
    const/4 v0, 0x0
    invoke-virtual {v1, v2, v0}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V
    :done
    return-void
.end method
''')
(d/"SwipeDown.smali").write_text(r'''.class public Lcom/example/streamshield/SwipeDown;
.super Ljava/lang/Object;
.implements Landroid/view/View$OnTouchListener;
.field private activity:Lcom/example/streamshield/MainActivity;
.field private startY:F
.field private startX:F
.method public constructor <init>(Lcom/example/streamshield/MainActivity;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/example/streamshield/SwipeDown;->activity:Lcom/example/streamshield/MainActivity;
    return-void
.end method
.method public onTouch(Landroid/view/View;Landroid/view/MotionEvent;)Z
    .locals 5
    invoke-virtual {p2}, Landroid/view/MotionEvent;->getActionMasked()I
    move-result v0
    if-nez v0, :move
    invoke-virtual {p2}, Landroid/view/MotionEvent;->getY()F
    move-result v1
    iput v1, p0, Lcom/example/streamshield/SwipeDown;->startY:F
    invoke-virtual {p2}, Landroid/view/MotionEvent;->getX()F
    move-result v1
    iput v1, p0, Lcom/example/streamshield/SwipeDown;->startX:F
    goto :done
    :move
    const/4 v1, 0x1
    if-ne v0, v1, :done
    iget v1, p0, Lcom/example/streamshield/SwipeDown;->startY:F
    invoke-virtual {p1}, Landroid/view/View;->getHeight()I
    move-result v2
    int-to-float v2, v2
    const v3, 0x3ed9999a
    mul-float/2addr v2, v3
    cmpl-float v2, v1, v2
    if-lez v2, :check
    goto :done
    :check
    invoke-virtual {p2}, Landroid/view/MotionEvent;->getY()F
    move-result v2
    sub-float/2addr v2, v1
    const/high16 v3, 0x43480000
    cmpl-float v2, v2, v3
    if-lez v2, :done
    invoke-virtual {p2}, Landroid/view/MotionEvent;->getX()F
    move-result v2
    iget v3, p0, Lcom/example/streamshield/SwipeDown;->startX:F
    sub-float/2addr v2, v3
    invoke-static {v2}, Ljava/lang/Math;->abs(F)F
    move-result v2
    const/high16 v3, 0x42c80000
    cmpl-float v2, v2, v3
    if-lez v2, :activate
    goto :done
    :activate
    iget-object v1, p0, Lcom/example/streamshield/SwipeDown;->activity:Lcom/example/streamshield/MainActivity;
    iget-boolean v2, v1, Lcom/example/streamshield/MainActivity;->miniMode:Z
    if-nez v2, :done
    invoke-virtual {v1}, Lcom/example/streamshield/MainActivity;->toggleMiniPlayer()V
    :done
    const/4 v0, 0x0
    return v0
.end method
''')
print("Patched toolbar, swipe-down, mini controls, and Home PiP trigger")
