package io.codex.encofree4;

public final class WidgetViewportTest {
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    static void close(float a,float b,String message) { check(Math.abs(a-b)<.02f,message+": "+a+" vs "+b); }
    static void inside(WidgetGeometry.Box box,float w,float h) {
        check(box.x>=-.02f && box.y>=-.02f && box.x+box.w<=w+.02f && box.y+box.h<=h+.02f,
                "target outside "+w+"x"+h+": "+box.x+","+box.y+" "+box.w+"x"+box.h);
    }
    static void targets(WidgetViewport v,boolean compact) {
        WidgetGeometry g=new WidgetGeometry(v.modelWidth,v.modelHeight,compact);
        close(g.width,v.modelWidth,"geometry enlarged model width");
        close(g.height,v.modelHeight,"geometry enlarged model height");
        check(g.compact==compact,"square widget incorrectly switched to compact layout");
        for(WidgetGeometry.Box b:new WidgetGeometry.Box[]{g.statusTouch,g.batteryTouch,g.anc,g.transparency}) {
            WidgetGeometry.Box card=v.toCard(b),host=v.toHost(b);
            inside(card,v.width,v.height);inside(host,v.hostWidth,v.hostHeight);
            close(host.x-card.x,v.offsetX,"horizontal hit offset");
            close(host.y-card.y,v.offsetY,"vertical hit offset");
        }
        close(v.toCard(g.left).w,v.toCard(g.left).h,"battery circle distorted");
    }
    public static void main(String[] args) {
        // Measured from the user's screenshot: 514x566 slot beside a 514x514 system card.
        WidgetViewport reference=new WidgetViewport(514,566,false);
        close(reference.width,514,"reference width");close(reference.height,514,"reference square height");
        close(reference.offsetY,52,"reference bottom alignment");targets(reference,false);
        int cases=0;
        for(boolean compact:new boolean[]{false,true})for(float w:new float[]{100,130,170,180,269,360,514,700})
            for(float h:new float[]{48,51,86,102,117,130,180,220,360,566}) {
                WidgetViewport v=new WidgetViewport(w,h,compact);
                close(v.width/v.height,compact?WidgetViewport.COMPACT_ASPECT:1,"card aspect changed");
                close(v.offsetY+v.height,h,"card bottom edge shifted");
                close(v.offsetX*2+v.width,w,"horizontal centering changed");
                check(v.width<=w+.02f && v.height<=h+.02f,"card overflows slot");
                close(v.width/v.modelWidth,v.height/v.modelHeight,"nonuniform drawing scale");
                targets(v,compact);cases++;
            }
        for(float n:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY}) {
            try { new WidgetViewport(n,100,false);throw new AssertionError("invalid slot accepted"); }
            catch(IllegalArgumentException expected) { }
        }
        System.out.println("PASS: screenshot regression + "+cases+" proportional viewports and mapped hit regions");
    }
}
