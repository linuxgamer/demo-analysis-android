# JNI: the Rust library looks these methods up by symbol name; renaming
# them breaks every native call.
-keepclasseswithmembernames class com.tf2demo.analyzer.DemoAnalysis {
    native <methods>;
}

# org.json is accessed normally, but keep its API surface stable against
# aggressive shrinking just in case (it is also provided by the platform).
-dontwarn org.json.**
