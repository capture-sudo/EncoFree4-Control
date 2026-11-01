package io.codex.encofree4;

/** Time-based motion skips late frames instead of accumulating a callback backlog. */
final class WidgetMotion {
    static final long DURATION=240, FRAME_MS=16;
    private long started;
    private float from,to;
    private boolean active;
    float position(long now) {
        if(!active)return to;
        float t=WidgetGeometry.clamp((now-started)/(float)DURATION,0,1);
        float eased=t*t*(3-2*t);
        return from+(to-from)*eased;
    }
    void start(long now,float oldPosition,float target) {
        from=active?position(now):oldPosition;to=target;started=now;active=true;
    }
    boolean running(long now){return active && now-started<DURATION;}
    void snap(float position){active=false;from=to=position;}
}
