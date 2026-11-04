package android.os; import java.util.concurrent.ConcurrentLinkedQueue; public class Handler {
 private final ConcurrentLinkedQueue<Runnable> queue=new ConcurrentLinkedQueue<>();
 private static final java.util.List<Handler> handlers=new java.util.concurrent.CopyOnWriteArrayList<>();
 public Handler(Looper looper){handlers.add(this);}
 public boolean post(Runnable action){queue.add(action);return true;}
 public void removeCallbacksAndMessages(Object token){queue.clear();}
 public static void drain(){for(Handler h:handlers){Runnable action;while((action=h.queue.poll())!=null)action.run();}}
}
