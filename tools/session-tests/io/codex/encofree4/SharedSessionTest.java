package io.codex.encofree4;
import android.bluetooth.*;
import android.content.Context;
import android.os.Handler;
import android.widget.Toast;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class SharedSessionTest {
 static class Events implements EncoController.Listener {
  boolean connected;int batteries,mode=-1;
  public void onConnectionChanged(boolean value,String message){connected=value;}
  public void onBattery(EncoProtocol.Battery b){batteries++;}
  public void onEarStatus(boolean both){}
  public void onNoiseMode(int value){mode=value;}
 }
 static class Fixture implements AutoCloseable {
  Context context=new Context();BluetoothDevice device=new BluetoothDevice();BluetoothSocket socket=new BluetoothSocket(false);
  EncoSession session;EncoSession.Client panel,service;
  Events p=new Events(),s=new Events();
  Fixture(boolean widgets)throws Exception {
   Handler.reset();Toast.shown=0;EncoWidgets.present=widgets;
   Field singleton=EncoSession.class.getDeclaredField("instance");singleton.setAccessible(true);singleton.set(null,null);
   BluetoothAdapter adapter=new BluetoothAdapter();adapter.bonded=Set.of(device);
   BluetoothManager manager=new BluetoothManager();manager.adapter=adapter;context.manager=manager;device.factory=()->socket;
   session=EncoSession.get(context);
   if(widgets){session.keepForWidgets(true);service=session.attach(s,false);}
   panel=session.attach(p,true);
  }
  void connect()throws Exception{panel.connect();await(()->p.connected,"connection");}
  public void close(){session.keepForWidgets(false);panel.close();if(service!=null)service.close();Handler.drain();}
 }
 static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
 static void await(BooleanSupplier condition,String text)throws Exception{
  long until=System.nanoTime()+3_000_000_000L;
  while(!condition.getAsBoolean()){Handler.drain();if(System.nanoTime()>until)throw new AssertionError("timeout: "+text);Thread.sleep(4);}Handler.drain();
 }
 static void oneSocketAndKeepAlive()throws Exception {
  try(Fixture f=new Fixture(true)){
   f.connect();f.service.connect();for(int i=0;i<20;i++)f.panel.connect();
   check(f.device.creates.get()==1,"panel/widget opened competing sockets");
   f.panel.close();check(!f.socket.closed&&f.session.isConnected(),"closing panel killed widget session");
   f.session.keepForWidgets(false);f.service.close();check(f.socket.closed,"last widget removal leaked socket");
   check(!f.session.snapshot().connected,"last removal left connected state");
  }
 }
 static void panelOnlyCloses()throws Exception {
  try(Fixture f=new Fixture(false)){f.connect();f.panel.close();check(f.socket.closed,"panel-only close leaked connection");}
 }
 static void backgroundPauseDoesNotDisableWidgets()throws Exception {
  try(Fixture f=new Fixture(false)){
   f.connect();f.panel.pausePanel();
   check(!f.session.manuallyPaused(),"backgrounding panel permanently paused future widgets");
  }
  try(Fixture f=new Fixture(true)){
   f.connect();f.panel.pausePanel();
   check(f.session.isConnected()&&!f.socket.closed,"panel pause disconnected active widgets");
   f.panel.disconnect();check(f.session.manuallyPaused(),"explicit disconnect did not persist pause");
  }
 }
 static void actualConfirmationAndNoDuplicates()throws Exception {
  try(Fixture f=new Fixture(true)){
   f.connect();f.session.onNoiseMode(EncoProtocol.MODE_ANC);
   check(!f.session.requestMode(EncoProtocol.MODE_ANC,true),"selected mode resent");
   check(f.session.requestMode(EncoProtocol.MODE_TRANSPARENCY,true),"new mode rejected");
   check(f.session.snapshot().mode==EncoProtocol.MODE_ANC,"tap prematurely changed selected mode");
   check(!f.session.requestMode(EncoProtocol.MODE_OFF,false),"panel bypassed pending widget command");
   f.session.onNoiseMode(EncoProtocol.MODE_ANC);
   check(f.session.snapshot().pendingMode==EncoProtocol.MODE_TRANSPARENCY,"old report completed new command");
   f.session.onNoiseMode(EncoProtocol.MODE_TRANSPARENCY);
   check(f.session.snapshot().pendingMode==-1&&f.p.mode==EncoProtocol.MODE_TRANSPARENCY&&f.s.mode==f.p.mode,"confirmed mode not shared");
   Handler.advance(4001);check(Toast.shown==0,"confirmed command later timed out");
  }
 }
 static void failureKeepsConfirmedSelection()throws Exception {
  try(Fixture f=new Fixture(true)){
   f.connect();f.session.onNoiseMode(EncoProtocol.MODE_ANC);f.session.requestMode(EncoProtocol.MODE_TRANSPARENCY,true);
   Handler.advance(4001);
   check(f.session.snapshot().pendingMode==-1&&f.session.snapshot().mode==EncoProtocol.MODE_ANC,"timeout changed actual selection");
   check(Toast.shown==1,"missing/duplicate failure feedback");
  }
 }
 static void partialBatteryAndDisconnectClears()throws Exception {
  try(Fixture f=new Fixture(true)){
   f.connect();EncoProtocol.Battery l=new EncoProtocol.Battery();l.left=85;l.leftCharging=true;f.session.onBattery(l);
   EncoProtocol.Battery r=new EncoProtocol.Battery();r.right=80;f.session.onBattery(r);
   WidgetSnapshot s=f.session.snapshot();check(s.left==85&&s.right==80&&s.leftCharging,"partial battery merge lost data");
   f.session.onNoiseMode(EncoProtocol.MODE_ANC);f.session.disconnectInternal(true);
   s=f.session.snapshot();check(!s.connected&&s.left==-1&&s.right==-1&&s.mode==-1,"disconnect displayed stale state");
   check(f.session.manuallyPaused(),"manual disconnect did not suppress automatic reconnect");
   f.session.onBattery(l);f.session.onNoiseMode(EncoProtocol.MODE_ANC);
   check(f.session.snapshot().left==-1&&f.session.snapshot().mode==-1,"late report restored disconnected state");
  }
 }
 static void refreshAndReplay()throws Exception {
  try(Fixture f=new Fixture(true)){
   f.connect();f.session.onNoiseMode(EncoProtocol.MODE_OFF);EncoProtocol.Battery l=new EncoProtocol.Battery();l.left=75;f.session.onBattery(l);
   Events e=new Events();EncoSession.Client reopened=f.session.attach(e,true);Handler.drain();
   check(e.connected&&e.mode==EncoProtocol.MODE_OFF&&e.batteries==1,"reopened panel failed to synchronize");
   f.session.refresh();check(f.session.snapshot().refreshing,"missing refresh feedback");
   int writes=f.socket.writes.get();await(()->f.socket.writes.get()>writes,"refresh queries");
   Handler.advance(1801);check(!f.session.snapshot().refreshing,"refresh indicator stuck");reopened.close();
  }
 }
 public static void main(String[] args)throws Exception {
  oneSocketAndKeepAlive();panelOnlyCloses();backgroundPauseDoesNotDisableWidgets();actualConfirmationAndNoDuplicates();failureKeepsConfirmedSelection();partialBatteryAndDisconnectClears();refreshAndReplay();
  System.out.println("PASS: 7 shared-session integration scenarios (real controller, simulated Android/Bluetooth)");
 }
}
