package android.content;
import java.util.HashMap;
public class Context {
 public static final String BLUETOOTH_SERVICE="bluetooth";public static final int MODE_PRIVATE=0;
 public int permission;public android.bluetooth.BluetoothManager manager;
 private final HashMap<String,SharedPreferences> prefs=new HashMap<>();
 public Context getApplicationContext(){return this;}
 public int checkSelfPermission(String name){return permission;}
 public Object getSystemService(String name){return manager;}
 public SharedPreferences getSharedPreferences(String name,int mode){return prefs.computeIfAbsent(name,k->new SharedPreferences());}
}
