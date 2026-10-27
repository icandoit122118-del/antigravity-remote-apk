Set WshShell = CreateObject("WScript.Shell")
WshShell.Run "cmd /c cd /d C:\Users\icand\.gemini\antigravity\scratch\antigravity-remote-apk && node pc-bridge-server.mjs --port 8766", 0, False
