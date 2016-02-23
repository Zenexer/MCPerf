# http://proguard.sourceforge.net/manual/usage.html


## Input/Output ##

-libraryjars                    <java.home>/lib/rt.jar
-target                         1.8
#-libraryjars                    ../server.jar
#-basedirectory                  ../jar
#-injars                         MCPerf.deob.jar(!maven-archiver/**,!maven-status/**,!surefire/**,!META-INF/maven/**)
#-outjars                        jar/MCPerf.jar
#-injars                         assembly(MCPerf-Full.jar;)
#-outjars                        final


## Keep ##

-keep                           public class * extends org.bukkit.plugin.java.JavaPlugin
-keepclassmembers               public class * implements com.earth2me.mcperf.config.Configurable {
	@com.earth2me.mcperf.config.ConfigSetting <fields>;
	@com.earth2me.mcperf.config.ConfigSettingSetter <methods>;
	public synthetic !static void set*(***);
}
-keepclassmembers               public class * extends com.earth2me.mcperf.managers.Manager {
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

-obfuscationdictionary          identifiers.dict
-classobfuscationdictionary     identifiers.dict
-packageobfuscationdictionary   identifiers.dict
-overloadaggressively
-repackageclasses               com.earth2me.mcperf
# Optional:
-keepattributes                 LineNumberTable
# Required by config system:
-keepattributes                 Signature,AnnotationDefault,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-adaptresourcefilecontents      plugin.yml
-adaptresourcefilecontents      META-INF/services/com.earth2me.mcperf.managers.Manager
-adaptresourcefilenames         META-INF/services/com.earth2me.mcperf.managers.Manager


## Preverification ##


## General ##

-dontnote                       com.earth2me.mcperf.managers.creative.validity.MetaValidator
-dontnote                       org.hamcrest.**
-dontnote                       com.earth2me.mcperf.managers.creative.ValidityManager