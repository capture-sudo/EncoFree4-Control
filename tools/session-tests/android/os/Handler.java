package android.os;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
public final class Handler {
 private record Entry(Runnable action,long at){}
 private static long now;
 private static final List<Handler> handlers=new CopyOnWriteArrayList<>();
 private final List<Entry> queue=new ArrayList<>();
 public Handler(Looper looper){handlers.add(this);}
 public synchronized boolean post(Runnable action){queue.add(new Entry(action,now));return true;}
 public synchronized boolean postDelayed(Runnable action,long delay){queue.add(new Entry(action,now+delay));return true;}
 public synchronized void removeCallbacks(Runnable action){queue.removeIf(e->e.action==action);}
 public synchronized void removeCallbacksAndMessages(Object token){queue.clear();}
 public static void reset(){handlers.clear();now=0;}
 public static void advance(long ms){now+=ms;drain();}
 public static void drain(){
  boolean more;
  do {more=false;for(Handler h:handlers){Runnable r=null;synchronized(h){for(int i=0;i<h.queue.size();i++){Entry e=h.queue.get(i);if(e.at<=now){r=e.action;h.queue.remove(i);break;}}}if(r!=null){r.run();more=true;}}}while(more);
 }
}
