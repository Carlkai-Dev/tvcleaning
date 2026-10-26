-dontobfuscate

# Drawables and views inflated from XML: R8 cannot see reflective/XML instantiation.
-keep class com.gathin.tvhome.LiquidGlassDrawable { public <init>(); }
-keep class com.gathin.tvhome.LiquidGlassDrawable$Panel { public <init>(); }
-keep class com.gathin.tvhome.ShineDrawable { public <init>(); }
-keep class com.gathin.tvhome.ShineView { public <init>(...); }
-keep class com.gathin.tvhome.GlassView { public <init>(...); }
