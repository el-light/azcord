# PowerShell script to run azcord with JDK 17

# Path to JDK 17 from config file
$javaHome = "C:\Users\Public\Documents\jdk-17.0.10"

# Set environment variables
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"

# Display Java version to confirm
Write-Host "Using Java from: $env:JAVA_HOME"
& "$env:JAVA_HOME\bin\java" -version

# Run the application using Maven
Write-Host "`nStarting azcord application...`n"
mvn clean spring-boot:run 