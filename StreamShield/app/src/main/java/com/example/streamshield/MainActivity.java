package com.example.streamshield;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
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
 WebView web; View fullscreen; WebChromeClient.CustomViewCallback callback; FrameLayout root; LinearLayout controls; LinearLayout actions;
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
 controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setBackgroundColor(Color.DKGRAY);
 Button back=new Button(this);back.setText("‹");controls.addView(back,new LinearLayout.LayoutParams(0,-2,1));
 Button home=new Button(this);home.setText("Home");controls.addView(home,new LinearLayout.LayoutParams(0,-2,2));
 EditText search=new EditText(this);search.setSingleLine(true);search.setTextColor(Color.WHITE);search.setHint("Search");
 controls.addView(search,new LinearLayout.LayoutParams(0,-2,4));
 Button go=new Button(this);go.setText("Go");controls.addView(go,new LinearLayout.LayoutParams(0,-2,1));
 layout.addView(controls);
 actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setBackgroundColor(Color.rgb(35,35,35));
 Button pip=new Button(this);pip.setText("Picture-in-Picture");actions.addView(pip,new LinearLayout.LayoutParams(0,-2,1));
 Button dl=new Button(this);dl.setText("Download");actions.addView(dl,new LinearLayout.LayoutParams(0,-2,1));
 layout.addView(actions);
 pip.setOnClickListener(v->startPip());
 dl.setOnClickListener(v->{String url=web.getUrl();if(url!=null && (url.contains("youtube.com/watch")||url.contains("youtu.be/")||url.contains("youtube.com/shorts/"))){Toast.makeText(this,"YouTube video downloads are not supported. Direct file downloads are supported.",Toast.LENGTH_LONG).show();}else{Toast.makeText(this,"Long-press a downloadable file link, or use a direct download URL.",Toast.LENGTH_LONG).show();}});

 web=new WebView(this);web.setBackgroundColor(Color.BLACK);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setMediaPlaybackRequiresUserGesture(false);
 web.setDownloadListener((url,userAgent,contentDisposition,mimeType,contentLength)->{try{
 if(url==null || !(url.startsWith("https://")||url.startsWith("http://")))return;
 DownloadManager.Request req=new DownloadManager.Request(Uri.parse(url));
 req.setMimeType(mimeType);req.addRequestHeader("User-Agent",userAgent==null?"StreamShield":userAgent);
 String cookies=CookieManager.getInstance().getCookie(url);if(cookies!=null)req.addRequestHeader("Cookie",cookies);
 String filename=URLUtil.guessFileName(url,contentDisposition,mimeType);
 req.setTitle(filename);req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
 req.setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,filename);
 ((DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(req);
 Toast.makeText(this,"Download started: "+filename,Toast.LENGTH_SHORT).show();
 }catch(Exception ex){Toast.makeText(this,"Download failed: "+ex.getMessage(),Toast.LENGTH_LONG).show();}});
 web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){v.evaluateJavascript(AD_SCRIPT,null);}@Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){String host=r.getUrl().getHost();if(host!=null)for(String blocked:BLOCKED)if(host.equals(blocked)||host.endsWith("."+blocked))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));return null;}});
 web.setWebChromeClient(new WebChromeClient(){@Override public void onShowCustomView(View v,CustomViewCallback c){if(fullscreen!=null){c.onCustomViewHidden();return;}fullscreen=v;callback=c;controls.setVisibility(View.GONE);actions.setVisibility(View.GONE);web.setVisibility(View.GONE);root.addView(v,new FrameLayout.LayoutParams(-1,-1));}@Override public void onHideCustomView(){if(fullscreen==null)return;root.removeView(fullscreen);fullscreen=null;controls.setVisibility(View.VISIBLE);actions.setVisibility(View.VISIBLE);web.setVisibility(View.VISIBLE);if(callback!=null)callback.onCustomViewHidden();callback=null;}});
 layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));root.addView(layout);setContentView(root);
 back.setOnClickListener(v->{if(web.canGoBack())web.goBack();});home.setOnClickListener(v->web.loadUrl(HOME));
 go.setOnClickListener(v->{String q=search.getText().toString().trim();if(q.startsWith("https://"))web.loadUrl(q);else web.loadUrl("https://m.youtube.com/results?search_query="+android.net.Uri.encode(q));});
 web.loadUrl(HOME);handler.postDelayed(adCheck,1500);
 }
 boolean backgroundEnabled=false;
 void toggleBackground(){backgroundEnabled=!backgroundEnabled;try{Intent i=new Intent(this,BackgroundService.class);if(backgroundEnabled){if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);Toast.makeText(this,"Background mode on. Playback depends on YouTube and Android.",Toast.LENGTH_LONG).show();}else{stopService(i);Toast.makeText(this,"Background mode off",Toast.LENGTH_SHORT).show();}}catch(Exception e){backgroundEnabled=false;Toast.makeText(this,"Background mode unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
 void startPip(){
 if(Build.VERSION.SDK_INT<26){Toast.makeText(this,"PiP requires Android 8+",Toast.LENGTH_SHORT).show();return;}
 if(!getPackageManager().hasSystemFeature("android.software.picture_in_picture")){Toast.makeText(this,"PiP unsupported on this phone",Toast.LENGTH_LONG).show();return;}
 web.evaluateJavascript("(function(){var v=document.querySelector('video');return !!v;})()",result->{
 if(!"true".equals(result)){Toast.makeText(this,"Start a video before entering PiP",Toast.LENGTH_LONG).show();return;}
 try{
 PictureInPictureParams.Builder builder=new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9));
 boolean ok=enterPictureInPictureMode(builder.build());
 if(!ok)Toast.makeText(this,"Android refused PiP. Check app PiP permission.",Toast.LENGTH_LONG).show();
 }catch(Exception e){Toast.makeText(this,"PiP failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
 });
 }
 @Override public void onPictureInPictureModeChanged(boolean isInPip,android.content.res.Configuration config){super.onPictureInPictureModeChanged(isInPip,config);controls.setVisibility(isInPip?View.GONE:View.VISIBLE);actions.setVisibility(isInPip?View.GONE:View.VISIBLE);
 if(isInPip && fullscreen!=null){try{web.getWebChromeClient().onHideCustomView();}catch(Exception ignored){}}
 }
 @Override public void onBackPressed(){if(fullscreen!=null){web.getWebChromeClient().onHideCustomView();return;}if(web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override protected void onDestroy(){handler.removeCallbacks(adCheck);web.destroy();super.onDestroy();}
}