# 额外安全配置
# 防止反编译和逆向工程

# 移除所有调试信息
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# 混淆所有类名、方法名、字段名 (已移除过时选项)
-keeppackagenames

# 移除未使用的代码和资源
-dontwarn **
-ignorewarnings

# 保护字符串常量
-adaptclassstrings
-adaptresourcefilenames
-adaptresourcefilecontents

# 移除注释和调试信息
-dontnote
-dontwarn

# 混淆包名
-repackageclasses ''

# 移除未使用的导入
-dontwarn java.lang.invoke.StringConcatFactory

# 保护关键类不被混淆（根据实际需要调整）
-keep class com.example.carrotamap.MainActivity { *; }

# 移除日志输出
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# 移除System.out.println
-assumenosideeffects class java.io.PrintStream {
    public void println(%);
    public void println(**);
}

# 移除调试信息
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}
