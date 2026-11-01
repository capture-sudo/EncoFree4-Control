package io.codex.encofree4;

public final class WidgetStateTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void inside(WidgetGeometry.Box b,float w,float h,String name){
        check(b.x>=-.01f&&b.y>=-.01f&&b.w>=0&&b.h>=0&&b.x+b.w<=w+.01f&&b.y+b.h<=h+.01f,
                name+" out of bounds: "+b.x+","+b.y+" "+b.w+"x"+b.h+" in "+w+"x"+h);
    }
    static boolean overlap(WidgetGeometry.Box a,WidgetGeometry.Box b){
        return Math.min(a.x+a.w,b.x+b.w)-Math.max(a.x,b.x)>.1f
                &&Math.min(a.y+a.h,b.y+b.h)-Math.max(a.y,b.y)>.1f;
    }
    public static void main(String[] args){
        int layouts=0;
        for(boolean compact:new boolean[]{false,true})for(int w:new int[]{130,150,170,180,220,269,360,600})
            for(int h:compact?new int[]{51,72,86,102,172}:new int[]{130,180,220,360}){
                WidgetGeometry g=new WidgetGeometry(w,h,compact);
                for(WidgetGeometry.Box b:new WidgetGeometry.Box[]{g.status,g.left,g.right,g.track,g.anc,g.transparency,g.statusTouch,g.batteryTouch})inside(b,w,h,"layout "+compact);
                check(!overlap(g.statusTouch,g.batteryTouch),"battery/status clicks overlap in "+w+"x"+h+" compact="+compact);
                check(!overlap(g.anc,g.transparency),"mode hit regions overlap");
                check(g.track.w>=g.track.h,"capsule has invalid aspect ratio");
                check(!overlap(g.statusTouch,g.anc)&&!overlap(g.batteryTouch,g.anc),"mode overlaps info in "+w+"x"+h);
                layouts++;
            }
        PanelState state=new PanelState(EncoProtocol.MODE_ANC);
        EncoProtocol.Battery b=new EncoProtocol.Battery();b.left=85;b.leftCharging=true;
        state.mergeBattery(b);state.observeMode(EncoProtocol.MODE_ANC);
        WidgetSnapshot known=new WidgetSnapshot(true,false,state,true);
        check(known.left==85&&known.right==-1&&known.leftCharging&&known.refreshing,"partial battery must stay partial");
        check(known.hasSelection()&&known.position()==0,"ANC must select left");
        state.observeMode(EncoProtocol.MODE_TRANSPARENCY);
        check(known.mode==EncoProtocol.MODE_ANC,"snapshot changed after a later report");
        check(new WidgetSnapshot(true,false,state,false).position()==1,"transparency must select right");
        state.observeMode(EncoProtocol.MODE_OFF);
        check(!new WidgetSnapshot(true,false,state,false).hasSelection(),"off must select neither");
        WidgetSnapshot disconnected=new WidgetSnapshot(false,false,state,true);
        check(disconnected.mode==-1&&disconnected.left==-1&&!disconnected.leftCharging&&!disconnected.refreshing,"disconnected widget leaked live data");
        System.out.println("PASS: "+layouts+" adaptive layouts and 7 widget state assertions");
    }
}
