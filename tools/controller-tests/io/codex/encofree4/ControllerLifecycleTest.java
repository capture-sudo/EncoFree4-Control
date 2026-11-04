package io.codex.encofree4;
import android.bluetooth.*; import android.content.Context; import android.os.Handler;
import java.util.*; import java.util.concurrent.*; import java.util.function.BooleanSupplier;
public final class ControllerLifecycleTest {
 static final class Events implements EncoController.Listener {
  final List<String> statuses=new CopyOnWriteArrayList<>(); int batteries;
  public void onConnectionChanged(boolean connected,String message){statuses.add((connected?"yes:":"no:")+message);}
  public void onBattery(EncoProtocol.Battery battery){batteries++;}
  public void onEarStatus(boolean bothInCase){}
  public void onNoiseMode(int mode){}
 }
 static final class Fixture {
  final Context context=new Context();final BluetoothDevice device=new BluetoothDevice();final Events events=new Events();
  Fixture(){BluetoothAdapter adapter=new BluetoothAdapter();adapter.bonded=Set.of(device);BluetoothManager manager=new BluetoothManager();manager.adapter=adapter;context.manager=manager;}
  EncoController controller(long timeout,long retry){return new EncoController(context,events,timeout,retry);}
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void await(BooleanSupplier done,String message)throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);while(!done.getAsBoolean()){Handler.drain();if(System.nanoTime()>until)throw new AssertionError("timeout: "+message);Thread.sleep(5);}Handler.drain();}
 static boolean connected(Events e){return e.statuses.stream().anyMatch(s->s.startsWith("yes:"));}
 static void coalescesAndCancels()throws Exception{
  Fixture f=new Fixture();BluetoothSocket socket=new BluetoothSocket(true);f.device.factory=()->socket;
  EncoController c=f.controller(1000,20);c.connect();await(()->socket.connecting.getCount()==0,"connecting");
  for(int i=0;i<30;i++)c.connect();Thread.sleep(30);check(f.device.creates.get()==1,"duplicate connections");
  c.disconnect();await(()->socket.closed,"cancel pending");Thread.sleep(80);Handler.drain();
  check(f.device.creates.get()==1,"intentional disconnect retried");check(!connected(f.events),"cancelled connection reported success");c.close();
 }
 static void oldReaderCannotCloseNewSession()throws Exception{
  Fixture f=new Fixture();BluetoothSocket old=new BluetoothSocket(false);old.deferReadClose=true;BluetoothSocket fresh=new BluetoothSocket(false);
  Queue<BluetoothSocket> sockets=new ConcurrentLinkedQueue<>(List.of(old,fresh));f.device.factory=()->sockets.remove();
  EncoController c=f.controller(1000,20);c.connect();await(()->old.writes.get()>=2,"old hello");Handler.drain();
  old.input.feed(EncoProtocol.singleFrame(0x0204,new byte[]{1,1,1,80},1));Thread.sleep(40);
  c.disconnect();c.connect();await(()->fresh.writes.get()>=2,"fresh hello");old.input.end();Thread.sleep(80);Handler.drain();
  check(!fresh.closed,"old reader closed fresh session");check(f.events.batteries==0,"stale battery callback reached new session");
  check(f.device.creates.get()==2,"old reader scheduled reconnect");c.close();
 }
 static void timeoutRetriesAreBounded()throws Exception{
  Fixture f=new Fixture();List<BluetoothSocket> sockets=new CopyOnWriteArrayList<>();
  f.device.factory=()->{BluetoothSocket s=new BluetoothSocket(true);sockets.add(s);return s;};
  EncoController c=f.controller(50,15);c.connect();
  await(()->f.events.statuses.stream().anyMatch(s->s.contains("连接超时")),"final timeout");
  check(f.device.creates.get()==3,"expected initial connection and two retries");check(sockets.stream().allMatch(s->s.closed),"timeout socket left open");
  Thread.sleep(120);check(f.device.creates.get()==3,"retry budget exceeded");c.close();
 }
 static void closeSuppressesCallbacksAndRetries()throws Exception{
  Fixture f=new Fixture();BluetoothSocket socket=new BluetoothSocket(true);f.device.factory=()->socket;
  EncoController c=f.controller(70,15);c.connect();await(()->socket.connecting.getCount()==0,"connecting");int prior=f.events.statuses.size();
  c.close();c.connect();c.setNoiseMode(EncoProtocol.MODE_ANC);Thread.sleep(120);Handler.drain();
  check(socket.closed,"close failed to abort connection");check(f.device.creates.get()==1,"closed controller retried");
  check(f.events.statuses.size()==prior,"callback after close");
 }
 static void writeFailureReportsAndReconnects()throws Exception{
  Fixture f=new Fixture();BluetoothSocket old=new BluetoothSocket(false);BluetoothSocket fresh=new BluetoothSocket(false);
  Queue<BluetoothSocket> sockets=new ConcurrentLinkedQueue<>(List.of(old,fresh));f.device.factory=()->sockets.remove();
  EncoController c=f.controller(1000,30);c.connect();await(()->old.writes.get()>=2,"connected");
  old.failWrites=true;c.setNoiseMode(EncoProtocol.MODE_ANC);
  await(()->fresh.writes.get()>=2,"reconnected after failed mode write");
  check(old.closed,"failed socket retained");check(f.events.statuses.stream().anyMatch(s->s.contains("正在重连")),"failure not reported");c.close();
 }
 static void blockedSecureAttemptsReachFallback()throws Exception{
  Fixture f=new Fixture();BluetoothSocket first=new BluetoothSocket(true);BluetoothSocket second=new BluetoothSocket(true);BluetoothSocket fallback=new BluetoothSocket(false);
  Queue<BluetoothSocket> sockets=new ConcurrentLinkedQueue<>(List.of(first,second,fallback));f.device.factory=()->sockets.remove();
  EncoController c=f.controller(3000,20);c.connect();await(()->fallback.writes.get()>=2,"insecure fallback after blocked secure attempts");
  check(first.closed&&second.closed,"blocked attempts left open");check(!fallback.closed,"healthy fallback closed by prior deadline");
  check(f.device.creates.get()==3,"fallback transport not reached");c.close();
 }
 static void permissionFailureDoesNotRetry()throws Exception{
  Fixture f=new Fixture();f.context.permission=-1;f.device.factory=()->{throw new AssertionError("must not open without permission");};
  EncoController c=f.controller(100,10);c.connect();await(()->f.events.statuses.stream().anyMatch(s->s.contains("权限")),"permission error");
  Thread.sleep(60);check(f.device.creates.get()==0,"permission failure retried");c.close();
 }
 static void disconnectCancelsDelayedInitialization()throws Exception{
  Fixture f=new Fixture();BluetoothSocket socket=new BluetoothSocket(false);f.device.factory=()->socket;
  EncoController c=f.controller(1000,15);c.connect();await(()->socket.writes.get()>=2,"hello");
  c.disconnect();int writes=socket.writes.get();Thread.sleep(420);
  check(socket.writes.get()==writes,"delayed initialization ran after disconnect");check(f.device.creates.get()==1,"disconnect triggered retry");c.close();
 }
 static void explicitModeRefreshUsesCurrentSession()throws Exception{
  Fixture f=new Fixture();BluetoothSocket socket=new BluetoothSocket(false);f.device.factory=()->socket;
  EncoController c=f.controller(1000,15);c.connect();await(()->socket.writes.get()>=2,"hello");
  int before=socket.writes.get();c.refreshNoiseMode();await(()->socket.writes.get()>before,"explicit mode query");
  c.disconnect();int after=socket.writes.get();c.refreshNoiseMode();Thread.sleep(30);
  check(socket.writes.get()==after,"refresh wrote after disconnect");c.close();
 }
 static void reopenBypassesStuckOldWrite()throws Exception{
  Fixture f=new Fixture();BluetoothSocket old=new BluetoothSocket(false),fresh=new BluetoothSocket(false);
  Queue<BluetoothSocket> sockets=new ConcurrentLinkedQueue<>(List.of(old,fresh));f.device.factory=()->sockets.remove();
  EncoController c=f.controller(1000,20);
  try{
   c.connect();await(()->old.writes.get()>=2,"old hello");old.blockWrites=true;c.setNoiseMode(EncoProtocol.MODE_ANC);
   await(()->old.writeEntered.getCount()==0,"blocked old write");c.disconnect();c.connect();
   await(()->fresh.writes.get()>=2,"reopen must bypass blocked old worker");
   old.releaseWrite.countDown();Thread.sleep(40);Handler.drain();
   check(!fresh.closed,"late old write failure closed replacement");
  }finally{old.releaseWrite.countDown();c.close();}
 }
 static void retryBypassesConnectIgnoringClose()throws Exception{
  Fixture f=new Fixture();BluetoothSocket old=new BluetoothSocket(true),fresh=new BluetoothSocket(false);old.ignoreConnectClose=true;
  Queue<BluetoothSocket> sockets=new ConcurrentLinkedQueue<>(List.of(old,fresh));f.device.factory=()->sockets.remove();
  EncoController c=f.controller(100,20);
  try{
   c.connect();await(()->fresh.writes.get()>=2,"automatic retry must bypass blocked connect");
   check(old.closed,"timed-out socket not closed");old.allowConnect.countDown();Thread.sleep(40);Handler.drain();
   check(!fresh.closed,"late connection completion closed replacement");
  }finally{old.allowConnect.countDown();c.close();}
 }
 static void deadlineCoversSocketCreation()throws Exception{
  Fixture f=new Fixture();BluetoothSocket old=new BluetoothSocket(false),fresh=new BluetoothSocket(false);
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  f.device.factory=()->{if(f.device.creates.get()==1){entered.countDown();BluetoothSocket.awaitUninterruptibly(release);return old;}return fresh;};
  EncoController c=f.controller(100,20);
  try{
   c.connect();await(()->entered.getCount()==0,"socket creation blocked");
   await(()->fresh.writes.get()>=2,"deadline before socket publication must retry");
   release.countDown();await(()->old.closed,"late socket must be closed");
   check(!fresh.closed,"stale factory completion closed replacement");
  }finally{release.countDown();c.close();}
 }
 static void stalledCreationRetriesAreBounded()throws Exception{
  Fixture f=new Fixture();CountDownLatch release=new CountDownLatch(1);
  List<BluetoothSocket> sockets=new CopyOnWriteArrayList<>();
  f.device.factory=()->{BluetoothSocket s=new BluetoothSocket(false);sockets.add(s);BluetoothSocket.awaitUninterruptibly(release);return s;};
  EncoController c=f.controller(80,15);
  try{
   c.connect();await(()->f.events.statuses.stream().anyMatch(s->s.contains("连接超时")),"bounded socket creation timeout");
   check(f.device.creates.get()==3,"stalled creation exceeded retry budget");
   release.countDown();await(()->sockets.stream().allMatch(s->s.closed),"retired factory sockets closed");
   check(!connected(f.events),"stale factory reported connected");
  }finally{release.countDown();c.close();}
 }
 static void lateDeadlineKeepsHealthySession()throws Exception{
  Fixture f=new Fixture();BluetoothSocket socket=new BluetoothSocket(false);f.device.factory=()->socket;
  EncoController c=f.controller(1000,15);
  try{
   c.connect();await(()->socket.writes.get()>=2,"hello");
   var generation=EncoController.class.getDeclaredField("generation");generation.setAccessible(true);
   var deadline=EncoController.class.getDeclaredMethod("onConnectTimeout",long.class);deadline.setAccessible(true);
   deadline.invoke(c,generation.getLong(c));Thread.sleep(40);Handler.drain();
   check(!socket.closed&&f.device.creates.get()==1,"late deadline retired healthy session");
  }finally{c.close();}
 }
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("recovery")){reopenBypassesStuckOldWrite();retryBypassesConnectIgnoringClose();deadlineCoversSocketCreation();System.out.println("Recovery regression tests passed (3 scenarios).");return;}
  coalescesAndCancels();oldReaderCannotCloseNewSession();timeoutRetriesAreBounded();
  closeSuppressesCallbacksAndRetries();writeFailureReportsAndReconnects();blockedSecureAttemptsReachFallback();permissionFailureDoesNotRetry();disconnectCancelsDelayedInitialization();explicitModeRefreshUsesCurrentSession();
  reopenBypassesStuckOldWrite();retryBypassesConnectIgnoringClose();deadlineCoversSocketCreation();
  stalledCreationRetriesAreBounded();lateDeadlineKeepsHealthySession();
  System.out.println("Controller lifecycle tests passed (14 scenarios).");
 }
}
