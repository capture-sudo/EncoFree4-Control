package android.bluetooth;
import java.io.*; import java.util.concurrent.*; import java.util.concurrent.atomic.AtomicInteger;
public class BluetoothSocket {
 public final CountDownLatch connecting=new CountDownLatch(1);
 public final CountDownLatch allowConnect;
 public final AtomicInteger writes=new AtomicInteger();
 public volatile boolean closed, failWrites, deferReadClose, blockWrites, ignoreConnectClose;
 public final CountDownLatch writeEntered=new CountDownLatch(1), releaseWrite=new CountDownLatch(1);
 public final FeedInput input=new FeedInput();
 public BluetoothSocket(boolean block){allowConnect=new CountDownLatch(block?1:0);}
 public static void awaitUninterruptibly(CountDownLatch latch){boolean interrupted=false;while(true){try{latch.await();break;}catch(InterruptedException e){interrupted=true;}}if(interrupted)Thread.currentThread().interrupt();}
 public void connect() throws IOException {connecting.countDown();if(ignoreConnectClose){awaitUninterruptibly(allowConnect);}else{try{allowConnect.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}if(closed)throw new IOException("closed");}
 public InputStream getInputStream(){return input;}
 public OutputStream getOutputStream(){return new OutputStream(){public void write(int b)throws IOException{if(blockWrites){writeEntered.countDown();awaitUninterruptibly(releaseWrite);}if(closed||failWrites)throw new IOException("write failed");}public void write(byte[] b)throws IOException{write(0);writes.incrementAndGet();}};}
 public void close()throws IOException{closed=true;if(!ignoreConnectClose)allowConnect.countDown();if(!deferReadClose)input.end();}
 public static final class FeedInput extends InputStream {
  private final BlockingQueue<Integer> bytes=new LinkedBlockingQueue<>();
  public void feed(byte[] data){for(byte b:data)bytes.add(b&255);}
  public void end(){bytes.add(-1);}
  public int read()throws IOException{try{return bytes.take();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}
 }
}
