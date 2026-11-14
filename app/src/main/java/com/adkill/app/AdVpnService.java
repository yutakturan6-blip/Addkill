package com.adkill.app;
import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;
public class AdVpnService extends VpnService {
 public static volatile boolean running=false;
 private volatile boolean active=false;
 private ParcelFileDescriptor vpn;
 private Thread worker;
 private static final String[] BLOCK={"doubleclick.net","googlesyndication.com","googleadservices.com","adnxs.com","adsrvr.org","taboola.com","outbrain.com","pubmatic.com","criteo.com"};
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null)return START_NOT_STICKY;
  if("STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  shutdown();
  ArrayList<String> packages=intent.getStringArrayListExtra("packages");
  if(packages==null||packages.isEmpty()){stopSelf();return START_NOT_STICKY;}
  try{
   NotificationManager nm=getSystemService(NotificationManager.class);
   nm.createNotificationChannel(new NotificationChannel("adkill","adkilimoncello",NotificationManager.IMPORTANCE_LOW));
   startForeground(1,new Notification.Builder(this,"adkill").setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("adkilimoncello aktif").build());
   Builder b=new Builder().setSession("adkilimoncello").setMtu(1500).addAddress("10.111.0.2",32).addDnsServer("10.111.0.1").addRoute("10.111.0.1",32).setBlocking(true);
   for(String p:packages)b.addAllowedApplication(p);
   vpn=b.establish();
   if(vpn==null)throw new IOException("VPN permission denied");
   active=true;running=true;
   worker=new Thread(this::serve,"adkill-dns");worker.start();
  }catch(Exception e){android.util.Log.e("ADKILL","VPN startup failed",e);stopSelf();}
  return START_NOT_STICKY;
 }
 private void serve(){
  try(FileInputStream in=new FileInputStream(vpn.getFileDescriptor());FileOutputStream out=new FileOutputStream(vpn.getFileDescriptor())){
   byte[] buffer=new byte[32767];
   while(active){
    int n=in.read(buffer);
    if(n<28)continue;
    byte[] packet=Arrays.copyOf(buffer,n);
    if((packet[0]&0xf0)!=0x40)continue;
    int h=(packet[0]&15)*4;
    if(h<20||n<h+8||(packet[9]&255)!=17||word(packet,h+2)!=53)continue;
    int udpLen=word(packet,h+4);
    if(udpLen<8||h+udpLen>n)continue;
    byte[] query=Arrays.copyOfRange(packet,h+8,h+udpLen);
    byte[] response=resolve(query);
    if(response!=null)out.write(reply(packet,h,response));
   }
  }catch(Exception e){if(active)android.util.Log.e("ADKILL","DNS loop failed",e);}
  finally{if(active)stopSelf();}
 }
 private byte[] resolve(byte[] query){
  if(query.length<12)return null;
  String domain=domain(query);
  if(domain!=null)for(String blocked:BLOCK)if(domain.equals(blocked)||domain.endsWith("."+blocked)){
   byte[] response=Arrays.copyOf(query,query.length);
   response[2]=(byte)0x81;response[3]=(byte)0x83;
   Arrays.fill(response,6,12,(byte)0);
   return response;
  }
  // Try two public resolvers; never silently drop a DNS query.
  String[] resolvers={"1.1.1.1","8.8.8.8"};
  for(String resolver:resolvers){
   try(DatagramSocket socket=new DatagramSocket()){
    if(!protect(socket))continue;
    socket.setSoTimeout(1800);
    socket.send(new DatagramPacket(query,query.length,InetAddress.getByName(resolver),53));
    byte[] buf=new byte[4096];DatagramPacket p=new DatagramPacket(buf,buf.length);
    socket.receive(p);
    if(p.getLength()>=12 && buf[0]==query[0] && buf[1]==query[1])
     return Arrays.copyOf(buf,p.getLength());
   }catch(Exception e){android.util.Log.w("adkilimoncello","DNS resolver failed: "+resolver,e);}
  }
  // Return SERVFAIL so the client can retry rather than hanging indefinitely.
  byte[] failure=Arrays.copyOf(query,query.length);
  failure[2]=(byte)0x81;failure[3]=(byte)0x82;
  Arrays.fill(failure,6,12,(byte)0);
  return failure;
 }
 private static String domain(byte[] q){
  int p=12;StringBuilder b=new StringBuilder();
  while(p<q.length){int len=q[p++]&255;if(len==0)return b.toString().toLowerCase(Locale.ROOT);if(len>63||p+len>q.length)return null;if(b.length()>0)b.append('.');for(int i=0;i<len;i++)b.append((char)(q[p++]&255));}
  return null;
 }
 private static int word(byte[] b,int i){return ((b[i]&255)<<8)|(b[i+1]&255);}
 private static void put(byte[] b,int i,int v){b[i]=(byte)(v>>>8);b[i+1]=(byte)v;}
 private static int checksum(byte[] b,int offset,int len){long s=0;for(int i=0;i<len;i+=2){int v=(b[offset+i]&255)<<8;if(i+1<len)v|=b[offset+i+1]&255;s+=v;}while((s>>>16)!=0)s=(s&65535)+(s>>>16);return (int)(~s)&65535;}
 private static byte[] reply(byte[] request,int h,byte[] dns){
  int u=dns.length+8;byte[] out=new byte[20+u];out[0]=0x45;put(out,2,out.length);out[8]=64;out[9]=17;
  System.arraycopy(request,16,out,12,4);System.arraycopy(request,12,out,16,4);
  put(out,20,word(request,h+2));put(out,22,word(request,h));put(out,24,u);
  System.arraycopy(dns,0,out,28,dns.length);put(out,10,checksum(out,0,20));
  byte[] pseudo=new byte[12+u+(u%2)];
  System.arraycopy(out,12,pseudo,0,8);pseudo[9]=17;put(pseudo,10,u);System.arraycopy(out,20,pseudo,12,u);
  int sum=checksum(pseudo,0,pseudo.length);put(out,26,sum==0?65535:sum);
  return out;
 }
 private void shutdown(){active=false;running=false;if(vpn!=null){try{vpn.close();}catch(Exception ignored){}vpn=null;}}
 @Override public void onDestroy(){shutdown();super.onDestroy();}
 @Override public void onRevoke(){stopSelf();}
}