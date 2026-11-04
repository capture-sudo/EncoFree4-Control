package android.bluetooth;
import java.util.UUID; import java.util.concurrent.atomic.AtomicInteger; import java.util.function.Supplier;
public class BluetoothDevice {
 public final AtomicInteger creates=new AtomicInteger();
 public Supplier<BluetoothSocket> factory;
 public String getName(){return "OPPO Enco Free4";}
 private BluetoothSocket create(){creates.incrementAndGet();return factory.get();}
 public BluetoothSocket createRfcommSocketToServiceRecord(UUID id){return create();}
 public BluetoothSocket createInsecureRfcommSocketToServiceRecord(UUID id){return create();}
 public BluetoothSocket createRfcommSocket(int channel){return create();}
}
