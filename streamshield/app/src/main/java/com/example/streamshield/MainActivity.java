package com.example.streamshield;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
public class MainActivity extends Activity {
 private FrameLayout root,playerHost,miniHost; private LinearLayout controls; private WebView web; private boolean mini=false;
 @Override public void onCreate(Bundle state){super.onCreate(state);int h=(int)(240*getResources().getDisplayMetrics().density);root=new FrameLayout(this);LinearLayout main=new LinearLayout(this);main.setOrientation(1);root.addView(main,new FrameLayout.LayoutParams(-1,-1));playerHost=new FrameLayout(this);main.addView(playerHost,new LinearLayout.LayoutParams(-1,h));controls=new LinearLayout(this);main.addView(controls,new LinearLayout.LayoutParams(-1,-2));addButton("Mini player",v->toggleMini());addButton("PiP",v->startPip());addButton("Back",v->{if(web.canGoBack())web.goBack();});miniHost=new FrameLayout(this);FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams((int)(220*getResources().getDisplayMetrics().density),(int)(124*getResources().getDisplayMetrics().density),Gravity.BOTTOM|Gravity.RIGHT);mp.setMargins(0,0,12,18);root.addView(miniHost,mp);miniHost.setVisibility(View.GONE);web=new WebView(this);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);web.setWebViewClient(new WebViewClient());web.setWebChromeClient(new WebChromeClient());playerHost.addView(web,new FrameLayout.LayoutParams(-1,-1));web.loadUrl("https://m.youtube.com/");miniHost.setOnClickListener(v->toggleMini());setContentView(root);}
 private void addButton(String name,View.OnClickListener listener){Button b=new Button(this);b.setText(name);b.setOnClickListener(listener);controls.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
 private void moveTo(FrameLayout host){ViewGroup p=(ViewGroup)web.getParent();if(p!=null)p.removeView(web);host.addView(web,new FrameLayout.LayoutParams(-1,-1));}
 private void toggleMini(){mini=!mini;if(mini){moveTo(miniHost);miniHost.setVisibility(View.VISIBLE);playerHost.setVisibility(View.GONE);}else{moveTo(playerHost);playerHost.setVisibility(View.VISIBLE);miniHost.setVisibility(View.GONE);}}
 private void startPip(){if(Build.VERSION.SDK_INT<26||!getPackageManager().hasSystemFeature("android.software.picture_in_picture")){Toast.makeText(this,"PiP not supported",0).show();return;}try{PictureInPictureParams.Builder p=new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9));if(Build.VERSION.SDK_INT>=31)p.setAutoEnterEnabled(true);enterPictureInPictureMode(p.build());}catch(Exception e){Toast.makeText(this,"PiP unavailable: "+e.getMessage(),1).show();}}
 @Override public void onPictureInPictureModeChanged(boolean active,android.content.res.Configuration config){super.onPictureInPictureModeChanged(active,config);controls.setVisibility(active?View.GONE:View.VISIBLE);if(active&&mini)toggleMini();}
 @Override protected void onDestroy(){if(web!=null){ViewGroup p=(ViewGroup)web.getParent();if(p!=null)p.removeView(web);web.destroy();}super.onDestroy();}
 @Override public void onBackPressed(){if(mini)toggleMini();else if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
