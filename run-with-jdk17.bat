@echo off
REM Batch script to run azcord with JDK 17

REM Path to JDK 17
set JAVA_HOME=C:\Users\Public\Documents\jdk-17.0.10
set PATH=%JAVA_HOME%\bin;%PATH%

REM Display Java version to confirm
echo Using Java from: %JAVA_HOME%
java -version

REM Run the application using Maven
echo.
echo Starting azcord application...
echo.
mvn clean spring-boot:run

pause 