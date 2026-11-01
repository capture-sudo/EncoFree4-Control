package io.codex.encofree4;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;
/** Explicit small frames survive launchers that suppress drawable animations. */
final class WidgetSlider {
 static void frame(Context c,RemoteViews r,WidgetGeometry g,WidgetSnapshot s,float p,float scale){
  r.setImageViewBitmap(R.id.widget_slider_still,WidgetRenderer.controlKnob(c,g,s,p,scale));
  r.setViewVisibility(R.id.widget_slider_still,s.hasSelection()?View.VISIBLE:View.GONE);
 }
 static void apply(Context c,RemoteViews r,WidgetGeometry g,WidgetSnapshot s,float p,float scale){
  EncoWidgets.anchor(r,g,g.track,new int[]{R.id.slider_y_extent,R.id.slider_y_size,R.id.slider_x_extent,R.id.slider_x_size});
  frame(c,r,g,s,p,scale);
 }
}
