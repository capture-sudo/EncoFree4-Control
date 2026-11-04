package android.content;
import java.util.HashMap;
public final class SharedPreferences {
 private final HashMap<String,Boolean> values=new HashMap<>();
 public boolean getBoolean(String key,boolean fallback){return values.getOrDefault(key,fallback);}
 public Editor edit(){return new Editor();}
 public final class Editor {
  public Editor putBoolean(String key,boolean value){values.put(key,value);return this;}
  public void apply(){}
 }
}
