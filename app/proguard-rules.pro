# R8 rules for the release build.
#
# `app/build.gradle.kts` names this file, so it must exist even when it has
# nothing to say: R8 treats a missing configuration as a build failure rather
# than an empty one.
#
# It stays close to empty on purpose. Every dependency here ships its own
# consumer rules inside its AAR, so Compose, DataStore, Navigation, and
# coroutines already keep what they need. A keep rule written here on suspicion
# would be a permanent exemption from shrinking that nobody could later prove
# unnecessary.
#
# Almost nothing in this app is reached by reflection. Manifest components are
# kept by R8 from the merged manifest, and the ViewModel is built by a lambda
# rather than by `Class.newInstance`. The exception is the C boundary, below.
#
# Two settings are stored as `Enum.name`, so their constants' names are the
# storage format. No keep rule appears here for them, and one would not help if
# it did: `name()` returns a string the compiler passes to the enum constructor
# in `<clinit>`, not the name of the static field, so renaming the field does not
# change it and keeping the field would not protect it. Worth confirming against
# the first release build rather than assumed, and worth remembering before
# renaming one of those constants after the app has shipped.

# Crash reports are read by people. Without these the line numbers in a release
# stack trace are gone, and the trace names a file that R8 renamed, so the report
# points at nothing. `-renamesourcefileattribute` replaces the real file name
# with a fixed token, which keeps the mapping useful without shipping the layout
# of the source tree.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The C boundary.
#
# JNA reads a Structure's fields by reflection to compute the native layout, and
# builds a callback trampoline from a Callback interface's single method. Neither
# is a call R8 can see, so shrinking or renaming either one produces a struct
# whose fields land at the wrong offsets, or a trampoline with no method to call.
# There is no diagnostic for that: the native side reads whatever bytes are
# there. These rules are the narrowest that keep both intact.
#
# @FieldOrder is what fixes declaration order, so the annotation is kept too;
# without it JNA falls back to asking the JVM for a field order it does not
# promise.
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keep interface * extends com.sun.jna.Library { *; }
-keepattributes *Annotation*

# JNA compiles against desktop and Windows APIs that are absent on Android and
# unreachable from it. R8 reports them as missing rather than as unused.
-dontwarn java.awt.**
-dontwarn com.sun.jna.platform.**
