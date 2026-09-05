# R8 rules for the test APK, when the release build is under instrumentation.
#
# Named by `testProguardFiles`, which R8 reads when it minifies the test APK and
# not when it minifies the app. Rules for the app go in proguard-under-test.pro.
#
# Shrinking the test APK was never the point: the app is what R8 has to be
# proven against, and the test APK is only minified because AGP minifies it
# alongside. Nothing inside it references a test class, since the runner finds
# them by reflection, so R8 is free to delete every one. It did, and the cell
# passed having run nothing.
-dontshrink
-dontobfuscate

# androidx.test compiles against Error Prone's annotations and does not ship
# them. They are source-retention, so nothing looks for them at run time, but R8
# reports a reference it cannot resolve as an error.
-dontwarn com.google.errorprone.annotations.**
