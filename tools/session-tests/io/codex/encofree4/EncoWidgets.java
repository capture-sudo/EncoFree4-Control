package io.codex.encofree4;
import android.content.Context;
final class EncoWidgets {
 static boolean present;static int updates;
 static boolean hasWidgets(Context c){return present;}
 static void scheduleUpdate(Context c){updates++;}
}
