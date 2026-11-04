package android.content; public class Context {
 public static final String BLUETOOTH_SERVICE="bluetooth";
 public int permission; public android.bluetooth.BluetoothManager manager;
 public Context getApplicationContext(){return this;}
 public int checkSelfPermission(String name){return permission;}
 public Object getSystemService(String name){return manager;}
}
