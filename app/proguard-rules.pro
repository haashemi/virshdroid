# JSch and Bouncy Castle load algorithms reflectively by class name.
-keep class com.jcraft.jsch.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.bouncycastle.**
