package com.adkill.app;
import android.app.*;import android.content.*;import android.content.pm.*;import android.graphics.Color;import android.net.VpnService;import android.os.*;import android.view.*;import android.widget.*;import java.util.*;
public class MainActivity extends Activity {
 final TreeSet<String> selected=new TreeSet<>();TextView status;
 public void onCreate(Bundle b){super.onCreate(b);selected.addAll(getPreferences(0).getStringSet("apps",Collections.emptySet()));ScrollView sc=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(30,30,30,30);root.setBackgroundColor(Color.rgb(15,17,23));sc.addView(root);
 TextView title=new TextView(this);title.setText("☠ ADKILL");title.setTextColor(Color.RED);title.setTextSize(32);root.addView(title);
 status=new TextView(this);status.setTextColor(Color.WHITE);root.addView(status);
 Button start=new Button(this);start.setText("KORUMAYI BAŞLAT");root.addView(start);Button stop=new Button(this);stop.setText("DURDUR");root.addView(stop);
 start.setOnClickListener(v->{if(selected.isEmpty()){Toast.makeText(this,"Uygulama seç",0).show();return;}Intent i=VpnService.prepare(this);if(i!=null)startActivityForResult(i,44);else startVpn();});
 stop.setOnClickListener(v->{startService(new Intent(this,AdVpnService.class).setAction("STOP"));new Handler().postDelayed(this::refresh,500);});
 PackageManager pm=getPackageManager();List<PackageInfo> apps=pm.getInstalledPackages(0);apps.sort(Comparator.comparing(a->a.packageName));
 for(PackageInfo p:apps){if(p.applicationInfo==null||p.packageName.equals(getPackageName())||pm.getLaunchIntentForPackage(p.packageName)==null)continue;CheckBox c=new CheckBox(this);c.setText(p.applicationInfo.loadLabel(pm));c.setTextColor(Color.WHITE);c.setChecked(selected.contains(p.packageName));root.addView(c);c.setOnCheckedChangeListener((v,checked)->{if(checked)selected.add(p.packageName);else selected.remove(p.packageName);getPreferences(0).edit().putStringSet("apps",new HashSet<>(selected)).apply();refresh();});}
 setContentView(sc);refresh();}
 void refresh(){status.setText("Seçili: "+selected.size()+" | VPN: "+(AdVpnService.running?"AKTİF":"KAPALI"));}
 void startVpn(){Intent i=new Intent(this,AdVpnService.class);i.putStringArrayListExtra("packages",new ArrayList<>(selected));startForegroundService(i);new Handler().postDelayed(this::refresh,800);}
 protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==44&&c==RESULT_OK)startVpn();}
}