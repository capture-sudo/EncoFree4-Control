package io.codex.encofree4;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.time.Duration;
import android.os.Looper;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowSystemProperties;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, qualifiers="w412dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ClearGlassPanelNativeTest {
    @Before public void allowFrameworkBlurRequests() {
        // Exercise the supported-device path; the host JVM has no real SurfaceFlinger/GPU blur.
        ShadowSystemProperties.override("ro.surface_flinger.supports_background_blur","true");
    }
    private Object field(Object target, String name) throws Exception {
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    private void call(Object target,String name,Class<?> argType,Object value) throws Exception {
        Method m=target.getClass().getDeclaredMethod(name,argType);m.setAccessible(true);m.invoke(target,value);
    }
    private int requestedBlur(ControlActivity activity) throws Exception {
        return (int)field(activity.getWindow().getDecorView(),"mOriginalBackgroundBlurRadius");
    }
    private void settled() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300)); }
    private void layout(View root,int width) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(820,View.MeasureSpec.AT_MOST));
        root.layout(0,0,width,root.getMeasuredHeight());
    }
    private void save(Bitmap bitmap,String name) throws Exception {
        File folder=new File("build/reports/clear-glass");folder.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(folder,name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));
        }
    }
    private void wallpaper(Canvas canvas,int w,int h,boolean light) {
        Paint p=new Paint();p.setShader(new LinearGradient(0,0,w,h,
                light?new int[]{0xFFF8DEBA,0xFF9EBCD4,0xFFF2C5C8}
                        :new int[]{0xFF061936,0xFF247FC5,0xFF251751},null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,p);
    }

    @Test public void windowIsClearAndBadgeHasNoActionInAnyConnectionState() throws Exception {
        try(ActivityController<ControlActivity> lifecycle=Robolectric.buildActivity(ControlActivity.class).create().start().resume().visible()) {
            ControlActivity activity=lifecycle.get();
            settled();
            int flags=activity.getWindow().getAttributes().flags;
            assertEquals(0,flags & (WindowManager.LayoutParams.FLAG_BLUR_BEHIND|WindowManager.LayoutParams.FLAG_DIM_BEHIND));
            assertEquals("a separate wallpaper surface is still requested",0,flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            assertTrue("window must be a real floating card",activity.getWindow().isFloating());
            assertEquals(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,activity.getWindow().getAttributes().gravity);
            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,activity.getWindow().getAttributes().height);
            assertEquals(0,activity.getWindow().getAttributes().windowAnimations);
            assertEquals(0f,activity.getWindow().getAttributes().dimAmount,.001f);
            FrameLayout container=activity.findViewById(android.R.id.content);
            View root=container.getChildAt(0);
            int width=activity.getWindow().getAttributes().width;
            assertEquals(388,width);
            layout(root,width);
            assertTrue("floating card unexpectedly fills screen",root.getHeight()<820);
            activity.onRequestPermissionsResult(4101,new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    new int[]{PackageManager.PERMISSION_DENIED});
            TextView badge=(TextView)field(activity,"connectionStatusLabel");
            for(String status:new String[]{"正在连接…","已连接","已断开","蓝牙未开启"}) {
                activity.onConnectionChanged(status.equals("已连接"),status);
                assertFalse(badge.hasOnClickListeners());
                assertFalse(badge.isClickable());
                assertFalse(badge.isFocusable());
                assertFalse(badge.performClick());
                assertFalse(badge.getContentDescription().toString().contains("点击"));
            }
            TextView help=(TextView)field(activity,"moreTitle");
            assertEquals("连接帮助",help.getText().toString());
            ((View)help.getParent()).performClick();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull("connection recovery became inaccessible",dialog);
            assertTrue(dialog.isShowing());dialog.dismiss();
            activity.onConnectionChanged(true,"已连接");
            activity.onNoiseMode(EncoProtocol.MODE_ANC);
            EncoProtocol.Battery battery=new EncoProtocol.Battery();battery.left=85;battery.right=80;
            activity.onBattery(battery);
            assertEquals("更多设置",help.getText().toString());
            View panel=(View)field(activity,"panel");panel.animate().cancel();
            panel.setAlpha(1);panel.setScaleX(1);panel.setScaleY(1);panel.setTranslationY(0);
            layout(root,width);
            assertEquals("已连接",badge.getText().toString());
            assertTrue("connection label is clipped",badge.getLayout().getLineWidth(0)>0 && badge.getHeight()>=badge.getLineHeight());
            for(boolean light:new boolean[]{false,true}) {
                Bitmap preview=Bitmap.createBitmap(412,900,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(preview);
                wallpaper(canvas,412,900,light);
                canvas.save();canvas.translate((412-width)/2f,900-root.getHeight()-24);
                GlassSurfaceDrawable material=(GlassSurfaceDrawable)field(activity,"panelMaterial");
                material.setBounds(0,0,width,root.getHeight());material.draw(canvas);
                root.draw(canvas);canvas.restore();
                save(preview,light?"panel-light.png":"panel-dark.png");
            }
        }
    }

    @Test public void frostedTintHasUsefulOpacityAndSelectedButtonIsClearer() throws Exception {
        int unselected=0;
        for(boolean selected:new boolean[]{false,true}) {
            GlassSurfaceDrawable surface=new GlassSurfaceDrawable(28,1,selected);surface.setBounds(0,0,360,200);
            Bitmap bitmap=Bitmap.createBitmap(360,200,Bitmap.Config.ARGB_8888);surface.draw(new Canvas(bitmap));
            int alpha=Color.alpha(bitmap.getPixel(180,100));
            assertTrue("glass lost its tint",alpha>0);
            assertTrue("glass is still opaque: "+alpha,alpha<(selected?135:80));
            if(selected)assertTrue("selected mode is hard to distinguish",alpha>unselected+30);
            else unselected=alpha;
        }
        Context context=RuntimeEnvironment.getApplication();
        for(boolean compact:new boolean[]{false,true}) {
            WidgetGeometry g=new WidgetGeometry(360,compact?143:360,compact);
            PanelState state=new PanelState(-1);state.observeMode(EncoProtocol.MODE_ANC);
            EncoProtocol.Battery b=new EncoProtocol.Battery();b.left=85;b.right=80;state.mergeBattery(b);
            Bitmap widget=WidgetRenderer.render(context,g,new WidgetSnapshot(true,false,state,false),0,2);
            int alpha=Color.alpha(widget.getPixel(360,compact?140:450));
            assertTrue("widget is still too clear or too dark: "+alpha,alpha>=95&&alpha<175);
            for(boolean light:new boolean[]{false,true}) {
                Bitmap preview=Bitmap.createBitmap(widget.getWidth(),widget.getHeight(),Bitmap.Config.ARGB_8888);
                Canvas c=new Canvas(preview);wallpaper(c,preview.getWidth(),preview.getHeight(),light);c.drawBitmap(widget,0,0,null);
                save(preview,(compact?"widget-2x1":"widget-2x2")+(light?"-light.png":"-dark.png"));
            }
        }
    }

    @Test public void localBlurHasStrongerTintFallbackAndNeverBlursWholeScreen() throws Exception {
        try(ActivityController<ControlActivity> lifecycle=Robolectric.buildActivity(ControlActivity.class).create().start().resume().visible()) {
            ControlActivity activity=lifecycle.get();settled();
            GlassSurfaceDrawable material=(GlassSurfaceDrawable)field(activity,"panelMaterial");material.setBounds(0,0,360,200);
            call(activity,"updatePanelBlur",boolean.class,true);
            Bitmap blur=Bitmap.createBitmap(360,200,Bitmap.Config.ARGB_8888);material.draw(new Canvas(blur));
            assertTrue(requestedBlur(activity)>0);
            assertTrue(requestedBlur(activity)<=64);
            call(activity,"updatePanelBlur",boolean.class,false);
            Bitmap fallback=Bitmap.createBitmap(360,200,Bitmap.Config.ARGB_8888);material.draw(new Canvas(fallback));
            assertTrue("no-blur fallback lacks contrast",Color.alpha(fallback.getPixel(180,100))>Color.alpha(blur.getPixel(180,100))+40);
            assertEquals(0,requestedBlur(activity));
            assertEquals(0,activity.getWindow().getAttributes().getBlurBehindRadius());
            assertEquals(0,activity.getWindow().getAttributes().flags & (WindowManager.LayoutParams.FLAG_BLUR_BEHIND|WindowManager.LayoutParams.FLAG_DIM_BEHIND|WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER));
            call(activity,"updatePanelBlur",boolean.class,true);
            assertTrue("blur did not restore",requestedBlur(activity)>0);
        }
    }

    @Test public void wholeCardMotionSettlesAndOutsideTapUsesTheSameExit() throws Exception {
        try(ActivityController<ControlActivity> lifecycle=Robolectric.buildActivity(ControlActivity.class).create().start().resume().visible()) {
            ControlActivity activity=lifecycle.get();settled();
            assertEquals(1f,activity.getWindow().getAttributes().alpha,.001f);
            assertEquals(12,activity.getWindow().getAttributes().y);
            View panel=(View)field(activity,"panel");
            assertEquals(1f,panel.getScaleX(),.001f);
            assertEquals(0f,panel.getTranslationY(),.001f);
            PanelDragHandleView handle=(PanelDragHandleView)field(activity,"dragHandle");
            PanelDragHandleView.Listener listener=(PanelDragHandleView.Listener)field(handle,"listener");
            listener.onDragStarted();listener.onDrag(40);
            assertEquals("bottom-gravity card moved against the finger",-28,activity.getWindow().getAttributes().y);
            listener.onDragCancelled();settled();
            assertEquals(12,activity.getWindow().getAttributes().y);
            MotionEvent outside=MotionEvent.obtain(0,0,MotionEvent.ACTION_OUTSIDE,-1,-1,0);
            assertTrue(activity.onTouchEvent(outside));outside.recycle();settled();
            assertTrue("outside tap did not close card",activity.isFinishing());
            assertEquals(0f,activity.getWindow().getAttributes().alpha,.001f);
            assertEquals(0,requestedBlur(activity));
        }
    }

    @Test public void notificationPermissionIsAbsentButSyncServiceKeepsSystemContract() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_PERMISSIONS);
        assertFalse(Arrays.asList(info.requestedPermissions).contains(Manifest.permission.POST_NOTIFICATIONS));
        assertTrue(Arrays.asList(info.requestedPermissions).contains(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE));
        EncoWidgetService service=Robolectric.buildService(EncoWidgetService.class).get();
        Method method=EncoWidgetService.class.getDeclaredMethod("notification");method.setAccessible(true);
        Notification notice=(Notification)method.invoke(service);
        assertNotNull(notice.getSmallIcon());
        assertTrue(notice.actions==null||notice.actions.length==0);
        assertNull(notice.sound);
        assertNotNull(notice.getChannelId());
    }
}
