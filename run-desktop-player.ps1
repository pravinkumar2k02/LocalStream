# Launch Local Stream Player Laptop Client
$env:JAVA_HOME = "C:\Users\p.k.palanisamy\.jdks\jbr-21.0.11"
$env:VLC_HOME = "C:\Program Files\VideoLAN\VLC"
$env:PATH = "$env:VLC_HOME;$env:JAVA_HOME\bin;$env:PATH"

Set-Location -Path "$PSScriptRoot\DesktopStreamPlayer"
.\gradlew.bat run
