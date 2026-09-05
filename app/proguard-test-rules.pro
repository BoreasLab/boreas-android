# R8 rules for the test APK, when the release build is under instrumentation.
#
# Named by `testProguardFiles`, which R8 reads when it minifies the test APK.
# Rules meant for the app under test go in proguard-under-test.pro instead.
#
# androidx.test compiles against Error Prone's annotations and does not ship
# them. They are source-retention, so nothing looks for them at run time, but R8
# reports a reference it cannot resolve as an error.
-dontwarn com.google.errorprone.annotations.**

# Nothing inside the test APK references a test class: the runner finds them by
# reflection. Without this R8 is free to take every one of them.
-keep @org.junit.runner.RunWith class * { *; }
-keepattributes *Annotation*
