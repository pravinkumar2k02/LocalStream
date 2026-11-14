# Launch Local Stream Player Laptop Client
$env:JAVA_HOME = "C:\Users\p.k.palanisamy\.jdks\jbr-21.0.11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Set-Location -Path "$PSScriptRoot\DesktopStreamPlayer"
.\gradlew.bat run
