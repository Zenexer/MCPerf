-include                        common.pro

-basedirectory                  .
-injars                         jar/deob/MCPerf.jar(!maven-archiver/**,!maven-status/**,!surefire/**,!META-INF/maven/**)
-outjars                        jar/final/MCPerf.jar
-libraryjars                    server.jar