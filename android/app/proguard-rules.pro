# Liftr keeps its own classes readable in crash reports; library rules come with the libraries.
-keepattributes SourceFile,LineNumberTable
-keep class nl.lrvdlinden.liftr.** { *; }
-dontwarn com.google.errorprone.annotations.**
