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
# Nothing in this app is reached by reflection. Manifest components are kept by
# R8 from the merged manifest, the ViewModel is built by a lambda rather than by
# `Class.newInstance`, and no persisted value depends on a Kotlin identifier
# surviving minification: see `Persisted` in :domain, where every stored token is
# written out rather than derived from the name of the constant that carries it.

# Crash reports are read by people. Without these the line numbers in a release
# stack trace are gone, and the trace names a file that R8 renamed, so the report
# points at nothing. `-renamesourcefileattribute` replaces the real file name
# with a fixed token, which keeps the mapping useful without shipping the layout
# of the source tree.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
