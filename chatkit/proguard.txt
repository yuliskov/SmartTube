# ViewHolder constructors are resolved by reflection
-keepclassmembers class * extends com.stfalcon.chatkit.commons.ViewHolder {
   public <init>(android.view.View);
}