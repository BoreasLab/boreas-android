# Applied to the app itself, and only while the release build is the one under
# instrumentation. `app/build.gradle.kts` adds it from `instrumentedBuildType`,
# so a shipped release never sees it.
#
# Renaming goes off here rather than in proguard-test-rules.pro, which R8 reads
# when it minifies the test APK and not when it minifies the app. With the rule
# in the wrong file the app was obfuscated, every class the tests name was gone,
# and the runner loaded nothing: a green cell that ran zero tests.
#
# Shrinking stays on. It is the half that deletes a JNA field and the half this
# run exists to test; renaming can only move one.
-dontobfuscate
