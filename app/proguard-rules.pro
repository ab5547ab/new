# כללי ProGuard בסיסיים
-keepattributes *Annotation*
-keep class com.parking.gate.control.** { *; }
-keep public class * extends android.app.Activity
