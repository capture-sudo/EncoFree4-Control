package io.codex.encofree4;

/** All dimensions are dp. Shared by drawing and hit regions so resize cannot desynchronize them. */
final class WidgetGeometry {
    static final class Box {
        final float x, y, w, h;
        Box(float x, float y, float w, float h) { this.x=x; this.y=y; this.w=w; this.h=h; }
        float cx() { return x+w/2; }
        float cy() { return y+h/2; }
        Box touch(float width, float height) {
            float tw=Math.min(width, Math.max(w, 48)), th=Math.min(height, Math.max(h, 48));
            return new Box(clamp(cx()-tw/2, 0, width-tw), clamp(cy()-th/2, 0, height-th), tw, th);
        }
    }
    final boolean compact;
    final float width, height, radius, pad, titleSize, statusSize, numberSize;
    final Box status, title, photo, left, right, track, anc, transparency, batteryTouch, statusTouch;

    WidgetGeometry(float width, float height, boolean compact) {
        this.width=Math.max(100,width); this.height=Math.max(48,height);
        compact=compact || this.width>this.height*1.85f;
        this.compact=compact;
        width=this.width; height=this.height;
        pad=clamp(width*.0556f, 7, 20);
        // Preserve the compact card's generous corner radius when trimming its height.
        radius=compact?Math.min(width*.1223f,height*.42f):Math.min(width,height)*.1556f;
        if (!compact) {
            float headH=clamp(height*.1f,22,36), statusW=clamp(width*.311f,49,112);
            status=new Box(width-pad-statusW,pad,statusW,Math.min(headH,34));
            title=new Box(pad,pad,Math.max(0,status.x-pad-4),headH);
            titleSize=clamp(width*.0722f,12,26); statusSize=clamp(width*.0556f,11,20);
            float controlH=Math.min(clamp(width*.2884f,40,104),height*.32f);
            float controlW=Math.min(width-pad*2,width*.7894f);
            track=new Box((width-controlW)/2,height-pad-controlH,controlW,controlH);
            float bodyTop=pad+headH+6, bodyH=Math.max(24,track.y-bodyTop-8);
            float photoW=width*.389f;
            photo=new Box(pad,bodyTop,photoW,bodyH);
            float availableW=width-pad-(photo.x+photo.w+6);
            float gap=clamp(width*.0444f,5,16);
            float d=Math.min(clamp(width*.1778f,24,64),(availableW-gap)/2);
            float pairX=photo.x+photo.w+6+(availableW-2*d-gap)/2;
            left=new Box(pairX,bodyTop+(bodyH-d)/2,d,d);
            right=new Box(pairX+d+gap,left.y,d,d);
            numberSize=clamp(d*.36f,10,23);
        } else {
            float statusH=Math.min(30,Math.max(20,height*.174f));
            float innerH=height-2*pad;
            if(width<170 && height>=88) {
                // Narrow home grids: keep useful controls full-width rather than shrinking every icon.
                float d=Math.min(28,(width-2*pad-54-8)/2), gap=4;
                status=new Box(pad,pad,50,statusH);
                left=new Box(width-pad-2*d-gap,pad+(statusH-d)/2,d,d);
                right=new Box(width-pad-d,left.y,d,d);
                float th=Math.min(height-pad-statusH-pad-8,50);
                track=new Box(pad,height-pad-th,width-2*pad,th);
            } else {
                float leftW=clamp(width*.2833f,50,102), gap=clamp(width*.0278f,5,10);
                float d=Math.min((leftW-6)/2,clamp(height*.256f,22,44));
                float groupH=statusH+6+d, groupY=(height-groupH)/2;
                status=new Box(pad,groupY,leftW,statusH);
                left=new Box(pad+(leftW-2*d-6)/2,groupY+statusH+6,d,d);
                right=new Box(left.x+d+6,left.y,d,d);
                float tw=width-2*pad-leftW-gap;
                float th=Math.min(innerH,clamp(tw*.404f,36,86.4f));
                track=new Box(pad+leftW+gap,(height-th)/2,tw,th);
            }
            title=new Box(0,0,0,0); photo=new Box(0,0,0,0); titleSize=0;
            statusSize=clamp(width*.05f,11,18);
            // Leave space for three digits (100) inside the compact battery rings.
            numberSize=clamp(left.w*.29f,7.5f,13);
        }
        float touchH=Math.min(height,Math.max(48,track.h));
        float touchY=clamp(track.cy()-touchH/2,0,height-touchH);
        anc=new Box(track.x,touchY,track.w/2,touchH);
        transparency=new Box(track.x+track.w/2,touchY,track.w/2,touchH);
        float bx=left.x, by=left.y, bw=right.x+right.w-left.x;
        // Battery refresh and status never overlap, even when their individual visuals are small.
        float boundary=(status.y+status.h+by)/2;
        if (compact && width<170 && height>=88) {
            statusTouch=new Box(0,0,left.x,Math.min(anc.y,height));
            batteryTouch=new Box(left.x,0,width-left.x,Math.min(anc.y,height));
        } else if(compact) {
            float split=track.x-2;
            statusTouch=new Box(0,Math.max(0,status.y-5),split,Math.max(1,boundary-Math.max(0,status.y-5)));
            batteryTouch=new Box(0,boundary,split,Math.min(height-boundary,Math.max(48,by+left.h-boundary+4)));
        } else {
            Box st=status.touch(width,height),bt=new Box(bx,by,bw,left.h).touch(width,height);
            float split=(status.y+status.h+by)/2;
            statusTouch=new Box(st.x,st.y,st.w,Math.max(1,Math.min(st.y+st.h,split)-st.y));
            float top=Math.max(bt.y,split);
            batteryTouch=new Box(bt.x,top,bt.w,Math.max(1,Math.min(bt.y+bt.h,anc.y)-top));
        }
    }
    static float clamp(float n,float min,float max) { return Math.max(min,Math.min(max,n)); }
}
