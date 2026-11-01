package io.codex.encofree4;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class EncoController {
    private static final String TAG = "EncoFree4";
    private static final UUID SERVICE_UUID = UUID.fromString(
            "0000079A-D102-11E1-9B23-00025B00A5A5"
    );
    private static final int RFCOMM_CHANNEL = 15;
    private static final int MAX_AUTOMATIC_RETRIES = 2;

    interface Listener {
        void onConnectionChanged(boolean connected, String message);
        void onBattery(EncoProtocol.Battery battery);
        void onEarStatus(boolean bothInCase);
        void onNoiseMode(int mode);
    }

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object stateLock = new Object();
    // Replaced when a generation is retired: a stuck vendor call must not queue future sessions.
    private ScheduledExecutorService ioExecutor = newExecutor("EncoFree4-IO");
    // Connection establishment blocks the IO worker, so its deadline needs a separate worker.
    private final ScheduledExecutorService timeoutExecutor = newExecutor("EncoFree4-Deadline");
    private final long connectTimeoutMs;
    private final long retryDelayMs;
    private final List<ScheduledFuture<?>> sessionTasks = new ArrayList<>();

    // All mutable connection state is guarded by stateLock.
    private long generation;
    private boolean closed;
    private boolean userDisconnected;
    private boolean connecting;
    private int retriesRemaining;
    private BluetoothSocket pendingSocket;
    private Session session;
    private ScheduledFuture<?> connectDeadline;
    private ScheduledFuture<?> reconnectTask;
    private volatile long pollIntervalMs = 5000;
    private long lastPollNanos;

    private static final class Session {
        final long generation;
        final BluetoothSocket socket;
        final InputStream input;
        final OutputStream output;
        int sequence;

        Session(long generation, BluetoothSocket socket) throws IOException {
            this.generation = generation;
            this.socket = socket;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
        }

        int nextSequence() {
            // Writes and sequence allocation happen only on the single IO worker.
            return sequence++ & 0xFF;
        }
    }

    EncoController(Context context, Listener listener) {
        this(context, listener, 12000, 1500);
    }

    // Timing is injectable for deterministic lifecycle tests without Bluetooth hardware.
    EncoController(Context context, Listener listener, long connectTimeoutMs, long retryDelayMs) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.connectTimeoutMs = connectTimeoutMs;
        this.retryDelayMs = retryDelayMs;
    }

    private static ScheduledExecutorService newExecutor(String name) {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    void connect() {
        synchronized (stateLock) {
            if (closed || connecting || session != null || reconnectTask != null) {
                return;
            }
            userDisconnected = false;
            retriesRemaining = MAX_AUTOMATIC_RETRIES;
            long token = ++generation;
            postConnection(token, false, "正在连接…");
            beginConnectionLocked(token);
        }
    }

    // Arm before queuing work: lookup, socket creation and a blocked worker are part of the deadline.
    private void beginConnectionLocked(long token) {
        connecting = true;
        connectDeadline = timeoutExecutor.schedule(() -> onConnectTimeout(token),
                connectTimeoutMs, TimeUnit.MILLISECONDS);
        ioExecutor.execute(() -> connectInternal(token));
    }

    void setNoiseMode(int mode) {
        synchronized (stateLock) {
            if (closed || userDisconnected) {
                return;
            }
            Session target = session;
            if (target == null) {
                // Preserve a pending reconnect; a stale UI click must not change its status.
                if (!connecting && reconnectTask == null) {
                    postConnection(generation, false, "耳机尚未连接，请重试");
                }
                return;
            }
            ioExecutor.execute(() -> runSessionTask(target, () -> {
                send(target, EncoProtocol.setNoiseModeFrame(mode, target.nextSequence()));
                scheduleSessionTask(target, () -> queryNoiseMode(target), 450);
            }));
        }
    }

    void refreshNoiseMode() {
        synchronized (stateLock) {
            if (closed || userDisconnected || session == null) {
                return;
            }
            Session target = session;
            ioExecutor.execute(() -> runSessionTask(target, () -> queryNoiseMode(target)));
        }
    }

    void refreshState() {
        synchronized (stateLock) {
            if (closed || userDisconnected || session == null) return;
            Session target = session;
            ioExecutor.execute(() -> runSessionTask(target, () -> {
                send(target, EncoProtocol.multiBatteryRequestFrame(target.nextSequence()));
                queryNoiseMode(target);
            }));
        }
    }

    void setPollIntervalMs(long interval) {
        pollIntervalMs = Math.max(5000, interval);
    }

    void close() {
        BluetoothSocket[] sockets;
        synchronized (stateLock) {
            if (closed) {
                return;
            }
            closed = true;
            userDisconnected = true;
            ++generation;
            sockets = detachConnectionLocked();
            timeoutExecutor.shutdownNow();
        }
        closeSockets(sockets);
        mainHandler.removeCallbacksAndMessages(null);
    }

    void disconnect() {
        BluetoothSocket[] sockets;
        synchronized (stateLock) {
            if (closed) {
                return;
            }
            userDisconnected = true;
            long token = ++generation;
            sockets = detachConnectionLocked();
            postConnection(token, false, "已断开");
        }
        // Close immediately, even when connect() is blocking the IO worker.
        closeSockets(sockets);
    }

    @SuppressLint("MissingPermission")
    private void connectInternal(long token) {
        if (!isCurrent(token)) {
            return;
        }
        try {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                fail(token, "需要“附近的设备”权限", false);
                return;
            }
            BluetoothManager manager =
                    (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                fail(token, "蓝牙未开启", false);
                return;
            }
            BluetoothDevice device = findDevice(adapter);
            if (device == null) {
                fail(token, "请先完成 OPPO Enco Free4 蓝牙配对", false);
                return;
            }
            BluetoothSocket newSocket = openControlSocket(device, token);
            Session established = new Session(token, newSocket);
            boolean accepted;
            synchronized (stateLock) {
                accepted = isCurrentLocked(token);
                if (accepted) {
                    cancel(connectDeadline);
                    connectDeadline = null;
                    pendingSocket = null;
                    session = established;
                    connecting = false;
                    postConnection(token, true, "已连接");
                }
            }
            if (!accepted) {
                closeQuietly(newSocket);
                return;
            }
            startReadLoop(established);
            runSessionTask(established, () -> {
                send(established, EncoProtocol.protocolHelloFrame(established.nextSequence()));
                send(established, EncoProtocol.queryNotificationCapabilityFrame(established.nextSequence()));
                scheduleSessionTask(established, () -> {
                    send(established, EncoProtocol.registerNotificationsFrame(established.nextSequence()));
                    queryNoiseMode(established);
                    send(established, EncoProtocol.multiBatteryRequestFrame(established.nextSequence()));
                }, 350);
                scheduleSessionTask(established, () -> send(established,
                        EncoProtocol.multiBatteryRequestFrame(established.nextSequence())), 1400);
                synchronized (stateLock) {
                    if (session == established && isCurrentLocked(token)) {
                        sessionTasks.add(ioExecutor.scheduleWithFixedDelay(
                                () -> runSessionTask(established, () -> pollState(established)),
                                6, 5, TimeUnit.SECONDS));
                    }
                }
            });
        } catch (SecurityException error) {
            fail(token, "蓝牙权限异常，请允许“附近的设备”权限后重试", false);
        } catch (Exception error) {
            if (isCurrent(token)) {
                // Do not log socket exceptions: vendors may include a Bluetooth address.
                Log.w(TAG, "Unable to open the control channel: " + error.getClass().getSimpleName());
                fail(token, "控制通道连接失败，请确认耳机已连接，并关闭其他耳机管理应用后重试", true);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private BluetoothSocket openControlSocket(BluetoothDevice device, long token) throws Exception {
        Exception lastError = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (!isCurrent(token)) {
                throw new IOException("Connection cancelled");
            }
            BluetoothSocket candidate = null;
            try {
                if (attempt < 2) {
                    candidate = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
                } else if (attempt == 2) {
                    candidate = device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID);
                } else {
                    Method createChannel = BluetoothDevice.class.getMethod("createRfcommSocket", int.class);
                    candidate = (BluetoothSocket) createChannel.invoke(device, RFCOMM_CHANNEL);
                }
                if (candidate == null) {
                    throw new IOException("No control socket");
                }
                synchronized (stateLock) {
                    if (!isCurrentLocked(token)) {
                        throw new IOException("Connection cancelled");
                    }
                    // Publish before the blocking call so close/deadline can abort it.
                    pendingSocket = candidate;
                }
                connectCandidate(candidate, token);
                if (!isCurrent(token)) {
                    throw new IOException("Connection cancelled");
                }
                return candidate;
            } catch (Exception error) {
                closeQuietly(candidate);
                synchronized (stateLock) {
                    if (pendingSocket == candidate) {
                        pendingSocket = null;
                    }
                }
                if (error instanceof SecurityException) {
                    throw error;
                }
                lastError = error;
                if (attempt == 0 && isCurrent(token)) {
                    try {
                        Thread.sleep(350);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Connection cancelled", interrupted);
                    }
                }
            }
        }
        throw lastError == null ? new IOException("Unable to create RFCOMM socket") : lastError;
    }

    @SuppressLint("MissingPermission")
    private void connectCandidate(BluetoothSocket candidate, long token) throws IOException {
        AtomicBoolean waiting = new AtomicBoolean(true);
        ScheduledFuture<?> attemptDeadline;
        synchronized (stateLock) {
            if (!isCurrentLocked(token)) {
                throw new IOException("Connection cancelled");
            }
            // Reserve time for all existing transport fallbacks within the overall deadline.
            attemptDeadline = timeoutExecutor.schedule(() -> {
                boolean shouldClose;
                synchronized (stateLock) {
                    shouldClose = isCurrentLocked(token) && pendingSocket == candidate
                            && waiting.compareAndSet(true, false);
                }
                if (shouldClose) {
                    closeQuietly(candidate);
                }
            }, Math.min(2500, Math.max(1, connectTimeoutMs / 5)), TimeUnit.MILLISECONDS);
            sessionTasks.add(attemptDeadline);
        }
        try {
            candidate.connect();
            synchronized (stateLock) {
                if (!waiting.getAndSet(false) || !isCurrentLocked(token)) {
                    throw new IOException("Connection attempt timed out or was cancelled");
                }
            }
        } finally {
            waiting.set(false);
            cancel(attemptDeadline);
            synchronized (stateLock) {
                sessionTasks.remove(attemptDeadline);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private BluetoothDevice findDevice(BluetoothAdapter adapter) {
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        BluetoothDevice fallback = null;
        for (BluetoothDevice device : bonded) {
            String name = device.getName();
            if (name == null) {
                continue;
            }
            String normalized = name.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains("enco free4")) {
                return device;
            }
            if (fallback == null && normalized.contains("free4")
                    && (normalized.contains("enco") || normalized.contains("oppo"))) {
                fallback = device;
            }
        }
        return fallback;
    }

    private interface IoTask {
        void run() throws IOException;
    }

    private void runSessionTask(Session target, IoTask task) {
        if (!isActive(target)) {
            return;
        }
        try {
            task.run();
        } catch (IOException | RuntimeException error) {
            fail(target.generation, "控制通道通信失败，请重试", true);
        }
    }

    private void scheduleSessionTask(Session target, IoTask task, long delayMs) {
        synchronized (stateLock) {
            if (session != target || !isCurrentLocked(target.generation)) {
                return;
            }
            sessionTasks.removeIf(ScheduledFuture::isDone);
            sessionTasks.add(ioExecutor.schedule(() -> runSessionTask(target, task),
                    delayMs, TimeUnit.MILLISECONDS));
        }
    }

    private void pollState(Session target) throws IOException {
        long now = System.nanoTime();
        if (now - lastPollNanos < TimeUnit.MILLISECONDS.toNanos(pollIntervalMs)) return;
        lastPollNanos = now;
        send(target, EncoProtocol.multiBatteryRequestFrame(target.nextSequence()));
        queryNoiseMode(target);
    }

    private void queryNoiseMode(Session target) throws IOException {
        // Current OPPO implementations use 01 01; the earlier Free4 build used
        // 02 01. Both are read-only queries, so keep both until device testing
        // establishes which form this firmware answers.
        send(target, EncoProtocol.queryNoiseModeFrame(target.nextSequence()));
        send(target, EncoProtocol.legacyQueryNoiseModeFrame(target.nextSequence()));
    }

    private void send(Session target, byte[] frame) throws IOException {
        if (!isActive(target)) {
            throw new IOException("Session expired");
        }
        // This stream belongs only to target; it can never become a later session's stream.
        target.output.write(frame);
        target.output.flush();
    }

    private void startReadLoop(Session target) {
        Thread reader = new Thread(() -> {
            try {
                while (isActive(target)) {
                    byte[] frame = readFrame(target.input);
                    if (frame == null) {
                        break;
                    }
                    EncoProtocol.Battery battery = EncoProtocol.parseBattery(frame);
                    if (battery != null) {
                        postSessionCallback(target, () -> listener.onBattery(battery));
                    }
                    EncoProtocol.EarStatus earStatus = EncoProtocol.parseEarStatus(frame);
                    if (earStatus != null) {
                        postSessionCallback(target, () -> listener.onEarStatus(earStatus.bothInCase()));
                    }
                    int mode = EncoProtocol.parseNoiseMode(frame);
                    if (mode >= 0) {
                        postSessionCallback(target, () -> listener.onNoiseMode(mode));
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // The generation check below distinguishes a failure from intentional closure.
            } finally {
                fail(target.generation, "控制通道已断开，请重试", true);
            }
        }, "EncoFree4-Reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void onConnectTimeout(long token) {
        fail(token, "控制通道连接超时，请确认耳机已连接后重试", true, true);
    }

    private void fail(long token, String message, boolean allowRetry) {
        fail(token, message, allowRetry, false);
    }

    private void fail(long token, String message, boolean allowRetry, boolean connectingOnly) {
        BluetoothSocket[] sockets;
        synchronized (stateLock) {
            // A cancelled deadline may already be running; atomically protect healthy sessions.
            if (!isCurrentLocked(token) || (connectingOnly && (!connecting || session != null))) {
                return;
            }
            long nextToken = ++generation;
            sockets = detachConnectionLocked();
            if (allowRetry && retriesRemaining > 0) {
                int attempt = MAX_AUTOMATIC_RETRIES - retriesRemaining + 1;
                retriesRemaining--;
                postConnection(nextToken, false,
                        "连接暂时中断，正在重连（" + attempt + "/" + MAX_AUTOMATIC_RETRIES + "）…");
                reconnectTask = timeoutExecutor.schedule(() -> {
                    synchronized (stateLock) {
                        if (!isCurrentLocked(nextToken)) {
                            return;
                        }
                        reconnectTask = null;
                        beginConnectionLocked(nextToken);
                    }
                }, retryDelayMs * attempt, TimeUnit.MILLISECONDS);
            } else {
                postConnection(nextToken, false, message);
            }
        }
        closeSockets(sockets);
    }

    // Caller owns stateLock. Invalidate generation before calling this method.
    private BluetoothSocket[] detachConnectionLocked() {
        cancel(connectDeadline);
        connectDeadline = null;
        cancel(reconnectTask);
        reconnectTask = null;
        for (ScheduledFuture<?> task : sessionTasks) {
            cancel(task);
        }
        sessionTasks.clear();
        BluetoothSocket activeSocket = session == null ? null : session.socket;
        BluetoothSocket connectingSocket = pendingSocket;
        session = null;
        pendingSocket = null;
        connecting = false;
        ioExecutor.shutdownNow();
        if (!closed) {
            ioExecutor = newExecutor("EncoFree4-IO");
        }
        return new BluetoothSocket[]{activeSocket, connectingSocket};
    }

    private boolean isCurrentLocked(long token) {
        return !closed && !userDisconnected && generation == token;
    }

    private boolean isCurrent(long token) {
        synchronized (stateLock) {
            return isCurrentLocked(token);
        }
    }

    private boolean isActive(Session target) {
        synchronized (stateLock) {
            return session == target && isCurrentLocked(target.generation);
        }
    }

    private void postConnection(long token, boolean connected, String message) {
        mainHandler.post(() -> {
            synchronized (stateLock) {
                if (closed || generation != token) {
                    return;
                }
            }
            listener.onConnectionChanged(connected, message);
        });
    }

    private void postSessionCallback(Session target, Runnable callback) {
        mainHandler.post(() -> {
            if (isActive(target)) {
                callback.run();
            }
        });
    }

    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            // The socket handles interruption of blocking IO; do not interrupt unrelated queued work.
            future.cancel(false);
        }
    }

    private static void closeSockets(BluetoothSocket[] sockets) {
        closeQuietly(sockets[0]);
        if (sockets[1] != sockets[0]) {
            closeQuietly(sockets[1]);
        }
    }

    private static void closeQuietly(BluetoothSocket target) {
        if (target != null) {
            try {
                target.close();
            } catch (IOException ignored) {
                // Best effort cleanup.
            }
        }
    }

    private byte[] readFrame(InputStream stream) throws IOException {
        int first;
        do {
            first = stream.read();
            if (first == -1) {
                return null;
            }
        } while (first != 0xAA);
        ByteArrayOutputStream frame = new ByteArrayOutputStream(64);
        frame.write(0xAA);
        int value = 0;
        int shift = 0;
        int lengthBytes = 0;
        int current;
        do {
            current = stream.read();
            if (current == -1) {
                return null;
            }
            frame.write(current);
            value |= (current & 0x7F) << shift;
            shift += 7;
            lengthBytes++;
            if (lengthBytes > 3) {
                throw new IOException("Invalid frame length");
            }
        } while ((current & 0x80) != 0);
        // The varint covers the two link-header bytes plus the complete TLV.
        if (value < 2 || value > 4096) {
            throw new IOException("Invalid frame payload length: " + value);
        }
        for (int i = 0; i < value; i++) {
            current = stream.read();
            if (current == -1) {
                return null;
            }
            frame.write(current);
        }
        return frame.toByteArray();
    }
}
