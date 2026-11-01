package android.widget;
import android.content.Context;
public final class Toast {
 public static final int LENGTH_SHORT=0;public static int shown;
 public static Toast makeText(Context c,String text,int duration){return new Toast();}
 public void show(){shown++;}
}
