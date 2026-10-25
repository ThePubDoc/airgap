# kotlinx.serialization ships its own R8 rules; nothing extra needed yet.

# Strip debug and verbose logging from release builds so card data can never end up in logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
