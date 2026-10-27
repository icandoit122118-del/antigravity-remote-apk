Set WshShell = CreateObject("WScript.Shell")
WshShell.Run "cmd /c cd /d C:\Users\icand\.gemini\antigravity\scratch\antigravity-remote-apk && ""C:\Users\icand\AppData\Local\Programs\GeminiCLI\runtime\node-v24.21.0-win-x64\node.exe"" pc-bridge-server.mjs --port 8766", 0, False
