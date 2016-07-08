# http://proguard.sourceforge.net/manual/usage.html


## Input/Output ##

#-basedirectory                  .
#-injars                         jar/MCPerf.deob.jar(!maven-archiver/**,!maven-status/**,!surefire/**,!META-INF/maven/**)
#-outjars                        jar/MCPerf.jar
-libraryjars                    <java.home>/lib/rt.jar
#-libraryjars                    server.jar
-target                         1.8


## Keep ##

-keep,allowobfuscation,allowoptimization              public class * extends org.bukkit.plugin.java.JavaPlugin
-keep,allowobfuscation,allowoptimization              public class * extends com.earth2me.mcperf.managers.Manager

# As long as -adaptresourcefilenames doesn't work on the files in META-INF/services, we can't obfuscate this name.
-keep,allowoptimization                               public class com.earth2me.mcperf.managers.Manager

-keepclassmembers                                     public class * implements com.earth2me.mcperf.config.Configurable {
	@com.earth2me.mcperf.config.ConfigSetting <fields>;
	@com.earth2me.mcperf.config.ConfigSettingSetter <methods>;
	public !static void set?*(***);
}
-keepclassmembers                                     public class * extends com.earth2me.mcperf.managers.Manager {
	@com.earth2me.mcperf.config.ConfigSetting <fields>;
	@com.earth2me.mcperf.config.ConfigSettingSetter <methods>;
	public !static void set?*(***);
}
# This doesn't work at the moment:
-keepclassmembers                                     @com.earth2me.mcperf.annotation.ContainsConfig class * {
	@com.earth2me.mcperf.config.ConfigSetting <fields>;
	@com.earth2me.mcperf.config.ConfigSettingSetter <methods>;
	public !static void set?*(***);
}
-keepclassmembers,allowobfuscation,allowoptimization  class * {
	@org.bukkit.event.EventHandler <methods>;
}


## Optimization ##


## Obfuscation ##

-obfuscationdictionary          identifiers.dict
# Encoding issues prevent the plugin and services from loading if it gets obfuscated with a multi-byte encoding.
#-classobfuscationdictionary     identifiers.dict
#-packageobfuscationdictionary   identifiers.dict
-overloadaggressively
-repackageclasses               com.earth2me.mcperf
# Optional:
-keepattributes                 LineNumberTable
# Required by config system:
-keepattributes                 Signature,AnnotationDefault,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-adaptresourcefilecontents      plugin.yml
-adaptresourcefilecontents      META-INF/services/**
-adaptresourcefilenames         META-INF/services/**


## Preverification ##


## General ##

# https://sourceforge.net/p/proguard/bugs/566/
-dontwarn                       java.lang.invoke.MethodHandle

-dontnote                       com.earth2me.mcperf.managers.creative.validity.GenericMetaValidator_1_9
-dontnote                       org.hamcrest.**
-dontnote                       com.earth2me.mcperf.managers.creative.ValidityManager
