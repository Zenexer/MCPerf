# http://proguard.sourceforge.net/manual/usage.html


## Input/Output ##

#-basedirectory                  .
#-injars                         jar/MCPerf.deob.jar(!maven-archiver/**,!maven-status/**,!surefire/**,!META-INF/maven/**)
#-outjars                        jar/MCPerf.jar
#-libraryjars                    <java.home>/lib/rt.jar
#-libraryjars                    server.jar
-target                         1.8


## Keep ##

-keep                           public class * extends org.bukkit.plugin.java.JavaPlugin
-keepclassmembers               public class * implements com.earth2me.mcperf.config.Configurable {
	@com.earth2me.mcperf.config.ConfigSetting <fields>;
	@com.earth2me.mcperf.config.ConfigSettingSetter <methods>;
	public synthetic !static void set*(***);
}
-keepclassmembers               public class * extends com.earth2me.mcperf.Manager {
	@com.earth2me.mcperf.config.ConfigSetting <fields>;
	@com.earth2me.mcperf.config.ConfigSettingSetter <methods>;
	public !static void set?*(***);
}
# This doesn't work at the moment:
-keepclassmembers               @com.earth2me.mcperf.ob.ContainsConfig class * {
	@com.earth2me.mcperf.config.ConfigSetting <fields>;
	@com.earth2me.mcperf.config.ConfigSettingSetter <methods>;
	public !static void set?*(***);
}
-keepclassmembers               class * {
	@org.bukkit.event.EventHandler <methods>;
}


## Optimization ##


## Obfuscation ##

-obfuscationdictionary          proguard.dict
-classobfuscationdictionary     proguard.dict
-packageobfuscationdictionary   proguard.dict
-overloadaggressively
-repackageclasses               com.earth2me.mcperf
# Optional:
-keepattributes                 LineNumberTable
# Required by config system:
-keepattributes                 Signature,AnnotationDefault,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-adaptresourcefilecontents      plugin.yml


## Preverification ##


## General ##

-dontnote                       com.earth2me.mcperf.validity.MetaValidator
-dontnote                       org.hamcrest.**