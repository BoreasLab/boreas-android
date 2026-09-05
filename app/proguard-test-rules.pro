# R8 rules that apply only while the release build is under instrumentation.
#
# Named by `testProguardFiles`, so an ordinary release build never sees them and
# what ships stays exactly what R8 produced for it.
#
# Shrinking stays on: removing a class JNA reaches only by reflection is the
# failure this run exists to catch, and proguard-rules.pro claims to prevent.
# Renaming goes off, because the test APK is compiled against the original names
# and R8 does not rewrite it. That leaves the shrink tested and the rename
# untested, which is the right half to give up: a renamed field is still there,
# a removed one is not.
-dontobfuscate

# androidx.test compiles against Error Prone's annotations and does not ship
# them. They are source-retention, so nothing looks for them at run time, but R8
# reports a reference it cannot resolve as an error.
-dontwarn com.google.errorprone.annotations.**

# The test APK is minified too, and nothing inside it references a test class:
# the runner finds them by reflection. Without this R8 removed every one of
# them and the cell passed having run nothing, which is worse than failing.
-keep @org.junit.runner.RunWith class * { *; }
-keepattributes *Annotation*
