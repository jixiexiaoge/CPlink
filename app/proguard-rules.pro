# ===========================================
# 高级代码混淆和保护规则
# ===========================================

# 启用代码混淆和优化
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontusemixedcaseclassnames
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# 启用代码混淆字典
-obfuscationdictionary dictionary.txt
-classobfuscationdictionary dictionary.txt
-packageobfuscationdictionary dictionary.txt

# 保持应用程序入口点
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# 保持Compose相关类
-keep class androidx.compose.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# 保持反射使用的类
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保持序列化类
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保持枚举类
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保持Parcelable类
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保持Gson相关类
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }

# 保持OkHttp相关类
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# 保持ExoPlayer相关类
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# 移除日志输出
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# 移除System.out.println和System.out.print
-assumenosideeffects class java.io.PrintStream {
    public void println(%);
    public void println(**);
    public void print(%);
    public void print(**);
}

# 移除System.out的所有输出方法
-assumenosideeffects class java.lang.System {
    public static void setOut(java.io.PrintStream);
    public static void setErr(java.io.PrintStream);
}

# 移除调试信息
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

# 移除网络调试输出
-assumenosideeffects class java.net.DatagramSocket {
    public void send(java.net.DatagramPacket);
    public void receive(java.net.DatagramPacket);
}

# 移除网络包调试输出
-assumenosideeffects class java.net.DatagramPacket {
    public java.net.InetAddress getAddress();
    public int getPort();
}

# 保持源文件名和行号信息（用于崩溃日志分析）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 移除调试信息
-renamesourcefileattribute SourceFile

# 混淆包名
-repackageclasses ''

# 移除未使用的代码
-dontwarn **
-ignorewarnings

# 保护字符串常量
-adaptclassstrings
-adaptresourcefilenames
-adaptresourcefilecontents

# 移除注释
-dontnote
-dontwarn

# 混淆字段名
-useuniqueclassmembernames
-keeppackagenames

# 保护关键类不被混淆
-keep class com.example.carrotamap.MainActivity { *; }
-keep class com.example.carrotamap.** { *; }

# 移除未使用的资源
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保护WebView相关类
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 移除未使用的导入
-dontwarn java.lang.invoke.StringConcatFactory