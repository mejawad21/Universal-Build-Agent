package com.example.streamshield;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import java.io.ByteArrayInputStream;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.app.PictureInPictureParams;
import android.util.Rational;
import android.app.DownloadManager;
import android.content.Context;
import android.os.Environment;
import android.net.Uri;
import android.content.Intent;
public class MainActivity extends Activity {
 WebView web; View fullscreen; WebChromeClient.CustomViewCallback callback; FrameLayout root; LinearLayout controls; LinearLayout actions; boolean pipWasPlaying=false; boolean miniMode=false; WebView browseWeb; FrameLayout miniHolder; FrameLayout contentHost; LinearLayout pageLayout;
 final Handler handler=new Handler(Looper.getMainLooper());
 final Runnable adCheck=new Runnable(){public void run(){if(web!=null){web.evaluateJavascript(AD_SCRIPT,null);handler.postDelayed(this,1500);}}};
 static final String AD_SCRIPT="(function(){try{"+
 "if(!document.getElementById('ss-style')){var s=document.createElement('style');s.id='ss-style';s.textContent='ytd-display-ad-renderer,ytd-promoted-sparkles-web-renderer,ytd-promoted-video-renderer,ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,ytd-banner-promo-renderer,ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,ytm-promoted-video-renderer,ytm-ad-slot-renderer,ytm-statement-banner-renderer,.ytp-ad-overlay-container,.ytp-ad-message-container,.ytp-ad-player-overlay,.video-ads,.ad-container,.masthead-ad-control,.ytd-ad-slot-renderer{display:none!important}';document.documentElement.appendChild(s);}"+
 "var skip=document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,.ytp-ad-skip-button-slot button,.ytp-ad-skip-button-container button,button[aria-label*=\\\"Skip ad\\\"]');if(skip)skip.click();"+
 "var ad=document.querySelector('.ad-showing,.ytp-ad-player-overlay,.ytp-ad-text-overlay');"+
 "if(ad){var v=document.querySelector('video');if(v){if(!v.dataset.ssMuted){v.dataset.ssMuted='1';v.dataset.ssWasMuted=v.muted?'1':'0';}v.muted=true;if(v.duration&&isFinite(v.duration)&&v.duration>0&&v.currentTime<v.duration-0.4){try{v.currentTime=v.duration-0.2;}catch(e){}}}}"+
 "else{var v=document.querySelector('video[data-ss-muted]');if(v){v.muted=v.dataset.ssWasMuted==='1';delete v.dataset.ssMuted;delete v.dataset.ssWasMuted;}}"+
 "}catch(e){}})();";
 static final String HOME="https://m.youtube.com/";
 static final String[] BLOCKED={"doubleclick.net","googleadservices.com","googlesyndication.com","adservice.google.com","pagead2.googlesyndication.com","adnxs.com","taboola.com","outbrain.com"};
 @Override public void onCreate(Bundle b) { super.onCreate(b);
 getWindow().getDecorView().setSystemUiVisibility(0);
 getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
 root=new FrameLayout(this); LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);
 root.setOnApplyWindowInsetsListener((v,insets)->{int top,bottom,left,right;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.statusBars()|android.view.WindowInsets.Type.navigationBars());top=bars.top;bottom=bars.bottom;left=bars.left;right=bars.right;}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();}v.setPadding(left,top,right,bottom);return insets;});
 controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setBackgroundColor(Color.DKGRAY);
 Button back=new Button(this);back.setText("‹");controls.addView(back,new LinearLayout.LayoutParams(0,-2,1));
 Button home=new Button(this);home.setText("Home");controls.addView(home,new LinearLayout.LayoutParams(0,-2,2));
 EditText search=new EditText(this);search.setSingleLine(true);search.setTextColor(Color.WHITE);search.setHint("Search");
 controls.addView(search,new LinearLayout.LayoutParams(0,-2,4));
 Button go=new Button(this);go.setText("Go");controls.addView(go,new LinearLayout.LayoutParams(0,-2,1));
 layout.addView(controls);
 actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setBackgroundColor(Color.rgb(35,35,35));
 Button pip=new Button(this);pip.setText("Mini Player");actions.addView(pip,new LinearLayout.LayoutParams(0,-2,1));
 Button dl=new Button(this);dl.setText("Download");actions.addView(dl,new LinearLayout.LayoutParams(0,-2,1));
 layout.addView(actions);
 pip.setOnClickListener(v->toggleMiniPlayer());
 dl.setOnClickListener(v->{String url=web.getUrl();if(url!=null && (url.contains("youtube.com/watch")||url.contains("youtu.be/")||url.contains("youtube.com/shorts/"))){Toast.makeText(this,"YouTube video downloads are not supported. Direct file downloads are supported.",Toast.LENGTH_LONG).show();}else{Toast.makeText(this,"Long-press a downloadable file link, or use a direct download URL.",Toast.LENGTH_LONG).show();}});

 web=createWebView();
 pageLayout=layout;contentHost=new FrameLayout(this);contentHost.addView(web,new FrameLayout.LayoutParams(-1,-1));layout.addView(contentHost,new LinearLayout.LayoutParams(-1,0,1));root.addView(layout);setContentView(root);
 back.setOnClickListener(v->{WebView w=miniMode?browseWeb:web;if(w!=null&&w.canGoBack())w.goBack();});home.setOnClickListener(v->{(miniMode?browseWeb:web).loadUrl(HOME);});
 go.setOnClickListener(v->{String q=search.getText().toString().trim();WebView w=miniMode?browseWeb:web;if(q.startsWith("https://"))w.loadUrl(q);else w.loadUrl("https://m.youtube.com/results?search_query="+android.net.Uri.encode(q));});
 web.loadUrl(HOME);
 prepareBrowseWeb();
 handler.postDelayed(adCheck,1500);
 }

 WebView createWebView(){
   WebView w=new WebView(this);
   w.setBackgroundColor(Color.BLACK);
   w.getSettings().setJavaScriptEnabled(true);
   w.getSettings().setDomStorageEnabled(true);
   w.getSettings().setMediaPlaybackRequiresUserGesture(false);
   w.setDownloadListener((url,userAgent,contentDisposition,mimeType,contentLength)->{
     try{
       if(url==null||!(url.startsWith("https://")||url.startsWith("http://")))return;
       DownloadManager.Request req=new DownloadManager.Request(Uri.parse(url));
       req.setMimeType(mimeType);
       req.addRequestHeader("User-Agent",userAgent==null?"StreamShield":userAgent);
       String cookies=CookieManager.getInstance().getCookie(url);
       if(cookies!=null)req.addRequestHeader("Cookie",cookies);
       String filename=URLUtil.guessFileName(url,contentDisposition,mimeType);
       req.setTitle(filename);
       req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
       req.setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,filename);
       ((DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(req);
       Toast.makeText(this,"Download started: "+filename,Toast.LENGTH_SHORT).show();
     }catch(Exception ex){Toast.makeText(this,"Download failed: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
   });
   w.setWebViewClient(new WebViewClient(){
     @Override public void onPageFinished(WebView v,String url){v.evaluateJavascript(AD_SCRIPT,null);}
     @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
       String host=r.getUrl().getHost();
       if(host!=null)for(String blocked:BLOCKED)
         if(host.equals(blocked)||host.endsWith("."+blocked))
           return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
       return null;
     }
   });
   w.setWebChromeClient(new WebChromeClient(){
     @Override public void onShowCustomView(View v,CustomViewCallback c){
       if(fullscreen!=null){c.onCustomViewHidden();return;}
       if(miniMode)closeMiniPlayer();
       fullscreen=v;callback=c;
       controls.setVisibility(View.GONE);actions.setVisibility(View.GONE);
       web.setVisibility(View.GONE);
       root.addView(v,new FrameLayout.LayoutParams(-1,-1));
     }
     @Override public void onHideCustomView(){
       if(fullscreen==null)return;
       root.removeView(fullscreen);fullscreen=null;
       controls.setVisibility(View.VISIBLE);actions.setVisibility(View.VISIBLE);
       if(web!=null)web.setVisibility(View.VISIBLE);
       if(callback!=null)callback.onCustomViewHidden();
       callback=null;
     }
   });
   return w;
 }
 void prepareBrowseWeb(){
   if(browseWeb!=null||contentHost==null)return;
   browseWeb=createWebView();
   contentHost.addView(browseWeb,0,new FrameLayout.LayoutParams(-1,-1));
   browseWeb.loadUrl(HOME); // Preload only when created, never on Mini Player tap.
 }
 int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}

 // The playing WebView never leaves its parent, never reloads, and keeps its full-size layout.
 // Only its display transform and clip change. This avoids destroying the video surface.
 static final String MINI_CSS_ON="(function(){try{"+
   "var v=document.querySelector('video');if(!v)return false;"+
   "if(!document.getElementById('ss-mini-video-style')){var s=document.createElement('style');s.id='ss-mini-video-style';"+
   "s.textContent='html,body{overflow:hidden!important;background:#000!important}body *{visibility:hidden!important}video{visibility:visible!important;position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:56.25vw!important;max-width:none!important;max-height:none!important;object-fit:contain!important;z-index:2147483647!important;transform:none!important;background:#000!important}';"+
   "document.head.appendChild(s);}return true;}catch(e){return false;}})()";
 static final String MINI_CSS_OFF="(function(){var s=document.getElementById('ss-mini-video-style');if(s)s.remove();})()";
 static final String VIDEO_RESUME="(function(){var v=document.querySelector('video');if(v&&v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}})()";
 float baseMiniX;
 void toggleMiniPlayer(){
   if(miniMode){closeMiniPlayer();return;}
   if(web==null)return;
   web.evaluateJavascript("(function(){var v=document.querySelector('video');return !v?'none':(v.paused?'paused':'playing');})()",result->{
     if("\"none\"".equals(result)||"null".equals(result)){
       Toast.makeText(this,"Start a video first",Toast.LENGTH_SHORT).show();return;
     }
     if(browseWeb==null)prepareBrowseWeb();
     if(browseWeb==null){Toast.makeText(this,"Unable to open browser",Toast.LENGTH_SHORT).show();return;}
     int width=contentHost.getWidth(),height=contentHost.getHeight();
     if(width<=0||height<=0){Toast.makeText(this,"Player is not ready",Toast.LENGTH_SHORT).show();return;}
     boolean wasPlaying="\"playing\"".equals(result);
     miniMode=true;
     final int miniW=Math.min(dp(230),width-dp(24));
     final int videoH=Math.min(height,Math.round(width*9f/16f));
     final float scale=miniW/(float)width;
     final int miniH=Math.round(videoH*scale);
     // Keep layout_params MATCH_PARENT: setScale doesn't resize/recreate the WebView.
     web.setPivotX(0f);web.setPivotY(0f);
     web.setClipBounds(new Rect(0,0,width,videoH));
     web.setScaleX(scale);web.setScaleY(scale);
     baseMiniX=width-miniW-dp(10);
     web.setTranslationX(baseMiniX);
     web.setTranslationY(height-miniH-dp(16));
     web.bringToFront();
     web.evaluateJavascript(MINI_CSS_ON,null);
     miniHolder=new FrameLayout(this);
     miniHolder.setClickable(true);
     FrameLayout.LayoutParams overlay=new FrameLayout.LayoutParams(miniW,miniH,
       android.view.Gravity.BOTTOM|android.view.Gravity.RIGHT);
     overlay.setMargins(0,0,dp(10),dp(16));
     contentHost.addView(miniHolder,overlay);
     miniHolder.bringToFront();
     miniHolder.setOnTouchListener(new View.OnTouchListener(){
       float startX;
       @Override public boolean onTouch(View v,MotionEvent e){
         if(e.getAction()==MotionEvent.ACTION_DOWN){startX=e.getRawX();return true;}
         if(e.getAction()==MotionEvent.ACTION_MOVE){
           float delta=Math.min(0,e.getRawX()-startX);
           v.setTranslationX(delta);
           if(web!=null)web.setTranslationX(baseMiniX+delta);
           return true;
         }
         if(e.getAction()==MotionEvent.ACTION_UP){
           float delta=e.getRawX()-startX;
           if(delta<-dp(65)){v.post(()->dismissMiniPlayer());}
           else{v.animate().translationX(0f).setDuration(160).start();
             if(web!=null)web.animate().translationX(baseMiniX).setDuration(160).start();}
           return true;
         }
         if(e.getAction()==MotionEvent.ACTION_CANCEL){
           v.setTranslationX(0f);if(web!=null)web.setTranslationX(baseMiniX);return true;
         }
         return true;
       }
     });
     Button expand=new Button(this);expand.setText("↗");
     FrameLayout.LayoutParams x=new FrameLayout.LayoutParams(dp(52),dp(46),
       android.view.Gravity.RIGHT|android.view.Gravity.TOP);
     miniHolder.addView(expand,x);expand.setOnClickListener(v->closeMiniPlayer());
     Button stop=new Button(this);stop.setText("×");
     FrameLayout.LayoutParams sx=new FrameLayout.LayoutParams(dp(52),dp(46),
       android.view.Gravity.LEFT|android.view.Gravity.TOP);
     miniHolder.addView(stop,sx);stop.setOnClickListener(v->dismissMiniPlayer());
     if(wasPlaying){
       handler.postDelayed(()->resumeMiniVideo(),350);
       handler.postDelayed(()->resumeMiniVideo(),1100);
     }
   });
 }
 void resumeMiniVideo(){
   if(miniMode&&web!=null)web.evaluateJavascript(VIDEO_RESUME,null);
 }
 void resetVideoView(){
   if(web==null)return;
   web.evaluateJavascript(MINI_CSS_OFF,null);
   web.setClipBounds(null);
   web.setScaleX(1f);web.setScaleY(1f);
   web.setTranslationX(0f);web.setTranslationY(0f);
 }
 // Expand restores the same playing page; browsing WebView remains cached.
 void closeMiniPlayer(){
   if(!miniMode)return;
   miniMode=false;
   if(miniHolder!=null){contentHost.removeView(miniHolder);miniHolder=null;}
   resetVideoView();
   if(web!=null)web.bringToFront();
 }
 // Swipe-left or X closes playback and keeps the browsed page as the active page.
 void dismissMiniPlayer(){
   if(!miniMode)return;
   miniMode=false;
   if(miniHolder!=null){contentHost.removeView(miniHolder);miniHolder=null;}
   WebView oldVideo=web;
   if(browseWeb!=null){
     web=browseWeb;browseWeb=null;
     web.setClipBounds(null);web.setScaleX(1f);web.setScaleY(1f);
     web.setTranslationX(0f);web.setTranslationY(0f);
     web.bringToFront();
   }
   if(oldVideo!=null&&oldVideo!=web){
     oldVideo.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.pause();})()",null);
     contentHost.removeView(oldVideo);
     oldVideo.destroy();
   }
   handler.postDelayed(()->{if(!miniMode)prepareBrowseWeb();},500);
   Toast.makeText(this,"Mini player closed",Toast.LENGTH_SHORT).show();
 }
 boolean backgroundEnabled=false;
 void toggleBackground(){backgroundEnabled=!backgroundEnabled;try{Intent i=new Intent(this,BackgroundService.class);if(backgroundEnabled){if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);Toast.makeText(this,"Background mode on. Playback depends on YouTube and Android.",Toast.LENGTH_LONG).show();}else{stopService(i);Toast.makeText(this,"Background mode off",Toast.LENGTH_SHORT).show();}}catch(Exception e){backgroundEnabled=false;Toast.makeText(this,"Background mode unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
 void startPip(){
 if(Build.VERSION.SDK_INT<26){Toast.makeText(this,"PiP requires Android 8+",Toast.LENGTH_SHORT).show();return;}
 if(!getPackageManager().hasSystemFeature("android.software.picture_in_picture")){Toast.makeText(this,"PiP unsupported on this phone",Toast.LENGTH_LONG).show();return;}
 web.evaluateJavascript("(function(){var v=document.querySelector('video');return v?(v.paused?'paused':'playing'):'none';})()",result->{
 if("\"none\"".equals(result)||"null".equals(result)){Toast.makeText(this,"Start a video before entering PiP",Toast.LENGTH_LONG).show();return;}
 pipWasPlaying="\"playing\"".equals(result);
 try{
 PictureInPictureParams.Builder builder=new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9));
 boolean ok=enterPictureInPictureMode(builder.build());
 if(!ok)Toast.makeText(this,"Android refused PiP. Check app PiP permission.",Toast.LENGTH_LONG).show();
 }catch(Exception e){Toast.makeText(this,"PiP failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
 });
 }
 @Override public void onPictureInPictureModeChanged(boolean isInPip,android.content.res.Configuration config){super.onPictureInPictureModeChanged(isInPip,config);controls.setVisibility(isInPip?View.GONE:View.VISIBLE);actions.setVisibility(isInPip?View.GONE:View.VISIBLE);
 if(isInPip && pipWasPlaying){handler.postDelayed(()->resumePipVideo(),300);handler.postDelayed(()->resumePipVideo(),1200);}
 }
 void resumePipVideo(){if(Build.VERSION.SDK_INT>=26 && isInPictureInPictureMode() && web!=null){web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v&&v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}})()",null);}}
 @Override public void onBackPressed(){if(miniMode){closeMiniPlayer();return;}if(fullscreen!=null){web.getWebChromeClient().onHideCustomView();return;}if(web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(browseWeb!=null)browseWeb.destroy();if(web!=null)web.destroy();super.onDestroy();}
}