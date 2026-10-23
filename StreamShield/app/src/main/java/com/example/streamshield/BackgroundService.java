package com.example.streamshield;
import android.app.*;import android.content.*;import android.os.*;
public class BackgroundService extends Service {
 public static final String CHANNEL="streamshield_playback";
 @Override public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Background playback",NotificationManager.IMPORTANCE_LOW);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
 if(intent!=null&&"STOP".equals(intent.getAction())){stopForeground(true);stopSelf();return START_NOT_STICKY;}
 Intent open=new Intent(this,MainActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
 PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 PendingIntent stopPi=PendingIntent.getService(this,1,new Intent(this,BackgroundService.class).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 Notification n=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("StreamShield background mode").setContentText("Tap to return to video").setContentIntent(pi).addAction(android.R.drawable.ic_media_pause,"Stop mode",stopPi).setOngoing(true).build();
 startForeground(101,n);return START_STICKY;
 }
 @Override public IBinder onBind(Intent i){return null;}
}