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
