@echo off
set MAVEN_WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar
if not exist "%MAVEN_WRAPPER_JAR%" (
    echo Downloading Maven wrapper...
    curl -o "%MAVEN_WRAPPER_JAR%" -L "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
)
java -Dmaven.multiModuleProjectDirectory="%CD%" -cp "%MAVEN_WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
