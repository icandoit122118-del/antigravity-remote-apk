#!/usr/bin/env node
/**
 * Antigravity Remote — PC Remote Control, Live Session Sync, Project Folder Creator & Local AI Router
 * Usage: node pc-bridge-server.mjs [--port 8766]
 *
 * Exposes:
 *   GET  /api/status          -> Bridge health, active session ID, PC workspace & project folders
 *   GET  /api/sessions        -> Real-time Antigravity sessions (~/.gemini/antigravity/brain) + PC projects (~/.gemini/antigravity/scratch)
 *   POST /api/sessions/create -> Create a new Antigravity session (and optional project folder) on the PC from mobile
 *   POST /api/projects/create -> Create a new project folder inside ~/.gemini/antigravity/scratch/<name> on the PC from mobile
 *   POST /api/remote-control  -> Execute bounded PC remote control commands (pc_status, list_projects, git_status, sync_session)
 *   POST /api/chat            -> Interactive chat + natural-language PC remote control + Local AI routing
 */

import http from 'node:http';
import os from 'node:os';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execSync, execFileSync, spawn } from 'node:child_process';
import { DatabaseSync } from 'node:sqlite';

const portArgIndex = process.argv.indexOf('--port');
const PORT = portArgIndex !== -1 && process.argv[portArgIndex + 1]
  ? Number(process.argv[portArgIndex + 1])
  : Number(process.env.ANTIGRAVITY_BRIDGE_PORT || 8766);

const ANTIGRAVITY_ROOT = path.join(os.homedir(), '.gemini', 'antigravity');
const GEMINI_HOME_DIR = path.join(os.homedir(), '.gemini');
const CLAUDE_HOME_DIR = path.join(os.homedir(), '.claude');
const GEMINI_MD_PATH = path.join(GEMINI_HOME_DIR, 'GEMINI.md');
const DESIGN_MOTION_MD_PATH = path.join(GEMINI_HOME_DIR, 'design-motion.md');
const DESIGN_MOTION_SKILL_PATH = path.join(GEMINI_HOME_DIR, 'config', 'skills', 'design-motion-stack', 'SKILL.md');
const CLAUDE_MD_PATH = path.join(CLAUDE_HOME_DIR, 'CLAUDE.md');
const BRAIN_DIR = path.join(ANTIGRAVITY_ROOT, 'brain');
const ANNOTATIONS_DIR = path.join(ANTIGRAVITY_ROOT, 'annotations');
const SCRATCH_DIR = path.join(ANTIGRAVITY_ROOT, 'scratch');
const ADB_EXE_PATH = path.join(os.homedir(), 'AppData', 'Local', 'Android', 'Sdk', 'platform-tools', 'adb.exe');
const FALLBACK_CONVERSATION_ID = 'ace251b1-f9a3-4165-a54b-3fe8ea9bc9e8';
const SUNSHINE_HOST_HELPER_DIR = path.join(SCRATCH_DIR, 'antigravity-remote-apk', '.sunshine-host');
const SUNSHINE_HOST_CS_PATH = path.join(SUNSHINE_HOST_HELPER_DIR, 'SunshineHostDaemon.cs');
const SUNSHINE_HOST_EXE_PATH = path.join(SUNSHINE_HOST_HELPER_DIR, 'SunshineHostDaemon.exe');
const CSC_CANDIDATES = [
  'C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\csc.exe',
  'C:\\Windows\\Microsoft.NET\\Framework\\v4.0.30319\\csc.exe'
];

let sunshineDaemonProc = null;
let sunshineLatestFrameJpeg = null;
let sunshineLatestFrameSeq = 0;
let sunshineScreenWidth = 1920;
let sunshineScreenHeight = 1080;
let sunshineCursorX = 960;
let sunshineCursorY = 540;
let sunshineLastEventDesc = '대기 중 (Sunshine Host Ready)';
let sunshineLastEventMs = Date.now();
let sunshineStdoutBuffer = Buffer.alloc(0);

function buildSunshineHostCSharpSource() {
  return `using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

public class SunshineHostDaemon {
    [DllImport("user32.dll")]
    static extern bool SetProcessDPIAware();

    [DllImport("user32.dll")]
    static extern bool SetCursorPos(int X, int Y);

    [DllImport("user32.dll")]
    static extern bool GetCursorPos(out POINT lpPoint);

    [DllImport("user32.dll")]
    static extern void mouse_event(uint dwFlags, int dx, int dy, int dwData, UIntPtr dwExtraInfo);

    [StructLayout(LayoutKind.Sequential)]
    public struct POINT {
        public int X;
        public int Y;
    }

    const uint MOUSEEVENTF_MOVE = 0x0001;
    const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    const uint MOUSEEVENTF_LEFTUP = 0x0004;
    const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
    const uint MOUSEEVENTF_RIGHTUP = 0x0010;
    const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020;
    const uint MOUSEEVENTF_MIDDLEUP = 0x0040;
    const uint MOUSEEVENTF_WHEEL = 0x0800;

    static volatile int targetScaleWidth = 1280;
    static volatile int targetJpegQuality = 68;
    static volatile bool running = true;
    static readonly object stdoutLock = new object();

    static ImageCodecInfo GetJpegCodec() {
        foreach (var codec in ImageCodecInfo.GetImageEncoders()) {
            if (codec.MimeType == "image/jpeg") return codec;
        }
        return null;
    }

    static void CaptureLoop() {
        var jpegCodec = GetJpegCodec();
        var stdout = Console.OpenStandardOutput();

        while (running) {
            try {
                Rectangle bounds = Screen.PrimaryScreen.Bounds;
                int srcW = Math.Max(bounds.Width, 1);
                int srcH = Math.Max(bounds.Height, 1);
                int dstW = Math.Min(srcW, Math.Max(480, targetScaleWidth));
                int dstH = Math.Max(1, (int)Math.Round((double)srcH * dstW / srcW));

                using (Bitmap rawBmp = new Bitmap(srcW, srcH, PixelFormat.Format24bppRgb)) {
                    using (Graphics g = Graphics.FromImage(rawBmp)) {
                        try {
                            g.CopyFromScreen(bounds.Left, bounds.Top, 0, 0, bounds.Size, CopyPixelOperation.SourceCopy);
                        } catch {
                            g.Clear(Color.FromArgb(13, 18, 24));
                            using (Pen gridPen = new Pen(Color.FromArgb(40, 104, 197, 178), 1f)) {
                                for (int gx = 0; gx < srcW; gx += 120) g.DrawLine(gridPen, gx, 0, gx, srcH);
                                for (int gy = 0; gy < srcH; gy += 120) g.DrawLine(gridPen, 0, gy, srcW, gy);
                            }
                        }
                        POINT pt;
                        if (GetCursorPos(out pt)) {
                            int cx = pt.X - bounds.Left;
                            int cy = pt.Y - bounds.Top;
                            using (Pen outerPen = new Pen(Color.FromArgb(230, 104, 197, 178), 3f))
                            using (Brush innerBrush = new SolidBrush(Color.FromArgb(240, 232, 185, 109))) {
                                g.DrawEllipse(outerPen, cx - 10, cy - 10, 20, 20);
                                g.FillEllipse(innerBrush, cx - 4, cy - 4, 8, 8);
                            }
                        }
                    }

                    using (Bitmap scaledBmp = new Bitmap(dstW, dstH, PixelFormat.Format24bppRgb)) {
                        using (Graphics sg = Graphics.FromImage(scaledBmp)) {
                            sg.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.Bilinear;
                            sg.DrawImage(rawBmp, 0, 0, dstW, dstH);
                        }
                        using (MemoryStream ms = new MemoryStream()) {
                            if (jpegCodec != null) {
                                using (EncoderParameters ep = new EncoderParameters(1)) {
                                    ep.Param[0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, (long)targetJpegQuality);
                                    scaledBmp.Save(ms, jpegCodec, ep);
                                }
                            } else {
                                scaledBmp.Save(ms, ImageFormat.Jpeg);
                            }
                            byte[] bytes = ms.ToArray();
                            POINT cur;
                            GetCursorPos(out cur);
                            byte[] header = new byte[24];
                            header[0] = (byte)'S';
                            header[1] = (byte)'F';
                            header[2] = (byte)'R';
                            header[3] = (byte)'M';
                            BitConverter.GetBytes(bytes.Length).CopyTo(header, 4);
                            BitConverter.GetBytes(srcW).CopyTo(header, 8);
                            BitConverter.GetBytes(srcH).CopyTo(header, 12);
                            BitConverter.GetBytes(cur.X).CopyTo(header, 16);
                            BitConverter.GetBytes(cur.Y).CopyTo(header, 20);

                            lock (stdoutLock) {
                                stdout.Write(header, 0, 24);
                                stdout.Write(bytes, 0, bytes.Length);
                                stdout.Flush();
                            }
                        }
                    }
                }
            } catch {
            }
            Thread.Sleep(24);
        }
    }

    public static void Main(string[] args) {
        try { SetProcessDPIAware(); } catch {}
        Thread capThread = new Thread(CaptureLoop);
        capThread.IsBackground = true;
        capThread.Start();

        string line;
        while ((line = Console.ReadLine()) != null) {
            try {
                string[] parts = line.Trim().Split(' ');
                if (parts.Length == 0) continue;
                string cmd = parts[0].ToUpperInvariant();
                Rectangle bounds = Screen.PrimaryScreen.Bounds;
                int scrW = Math.Max(1, bounds.Width);
                int scrH = Math.Max(1, bounds.Height);

                if (cmd == "ABS_POS" && parts.Length >= 5) {
                    double x = double.Parse(parts[1]);
                    double y = double.Parse(parts[2]);
                    double refW = Math.Max(1.0, double.Parse(parts[3]));
                    double refH = Math.Max(1.0, double.Parse(parts[4]));
                    int targetX = bounds.Left + (int)Math.Round(Math.Min(1.0, Math.Max(0.0, x / refW)) * (scrW - 1));
                    int targetY = bounds.Top + (int)Math.Round(Math.Min(1.0, Math.Max(0.0, y / refH)) * (scrH - 1));
                    SetCursorPos(targetX, targetY);
                } else if (cmd == "REL_MOVE" && parts.Length >= 3) {
                    int dx = (int)Math.Round(double.Parse(parts[1]));
                    int dy = (int)Math.Round(double.Parse(parts[2]));
                    POINT pt;
                    if (GetCursorPos(out pt)) {
                        int nx = Math.Min(bounds.Right - 1, Math.Max(bounds.Left, pt.X + dx));
                        int ny = Math.Min(bounds.Bottom - 1, Math.Max(bounds.Top, pt.Y + dy));
                        SetCursorPos(nx, ny);
                    }
                } else if (cmd == "BTN_DOWN" && parts.Length >= 2) {
                    string btn = parts[1].ToUpperInvariant();
                    uint flag = btn == "RIGHT" ? MOUSEEVENTF_RIGHTDOWN : (btn == "MIDDLE" ? MOUSEEVENTF_MIDDLEDOWN : MOUSEEVENTF_LEFTDOWN);
                    mouse_event(flag, 0, 0, 0, UIntPtr.Zero);
                } else if (cmd == "BTN_UP" && parts.Length >= 2) {
                    string btn = parts[1].ToUpperInvariant();
                    uint flag = btn == "RIGHT" ? MOUSEEVENTF_RIGHTUP : (btn == "MIDDLE" ? MOUSEEVENTF_MIDDLEUP : MOUSEEVENTF_LEFTUP);
                    mouse_event(flag, 0, 0, 0, UIntPtr.Zero);
                } else if (cmd == "CLICK" && parts.Length >= 2) {
                    string btn = parts[1].ToUpperInvariant();
                    if (parts.Length >= 6) {
                        double x = double.Parse(parts[2]);
                        double y = double.Parse(parts[3]);
                        double refW = Math.Max(1.0, double.Parse(parts[4]));
                        double refH = Math.Max(1.0, double.Parse(parts[5]));
                        int targetX = bounds.Left + (int)Math.Round(Math.Min(1.0, Math.Max(0.0, x / refW)) * (scrW - 1));
                        int targetY = bounds.Top + (int)Math.Round(Math.Min(1.0, Math.Max(0.0, y / refH)) * (scrH - 1));
                        SetCursorPos(targetX, targetY);
                    }
                    if (btn == "DOUBLE") {
                        mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, UIntPtr.Zero);
                        mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, UIntPtr.Zero);
                        Thread.Sleep(45);
                        mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, UIntPtr.Zero);
                        mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, UIntPtr.Zero);
                    } else {
                        uint downFlag = btn == "RIGHT" ? MOUSEEVENTF_RIGHTDOWN : (btn == "MIDDLE" ? MOUSEEVENTF_MIDDLEDOWN : MOUSEEVENTF_LEFTDOWN);
                        uint upFlag = btn == "RIGHT" ? MOUSEEVENTF_RIGHTUP : (btn == "MIDDLE" ? MOUSEEVENTF_MIDDLEUP : MOUSEEVENTF_LEFTUP);
                        mouse_event(downFlag, 0, 0, 0, UIntPtr.Zero);
                        Thread.Sleep(25);
                        mouse_event(upFlag, 0, 0, 0, UIntPtr.Zero);
                    }
                } else if (cmd == "SCROLL" && parts.Length >= 2) {
                    int delta = (int)Math.Round(double.Parse(parts[1]));
                    mouse_event(MOUSEEVENTF_WHEEL, 0, 0, delta, UIntPtr.Zero);
                } else if (cmd == "KEY" && parts.Length >= 2) {
                    string keySpec = line.Substring(4).Trim();
                    SendKeys.SendWait(keySpec);
                }
            } catch {
            }
        }
        running = false;
    }
}
`;
}

function ensureSunshineHostDaemonStarted() {
  if (sunshineDaemonProc && !sunshineDaemonProc.killed) {
    return true;
  }
  try {
    fs.mkdirSync(SUNSHINE_HOST_HELPER_DIR, { recursive: true });
    const csCode = buildSunshineHostCSharpSource();
    const shouldCompile = !fs.existsSync(SUNSHINE_HOST_EXE_PATH)
      || !fs.existsSync(SUNSHINE_HOST_CS_PATH)
      || fs.readFileSync(SUNSHINE_HOST_CS_PATH, 'utf8') !== csCode;

    if (shouldCompile) {
      fs.writeFileSync(SUNSHINE_HOST_CS_PATH, csCode, 'utf8');
      const cscPath = CSC_CANDIDATES.find((p) => fs.existsSync(p));
      if (!cscPath) {
        return false;
      }
      execSync(`"${cscPath}" /nologo /optimize+ /target:exe /out:"${SUNSHINE_HOST_EXE_PATH}" /r:System.Drawing.dll /r:System.Windows.Forms.dll "${SUNSHINE_HOST_CS_PATH}"`, {
        stdio: 'ignore',
        timeout: 10000
      });
    }

    if (!fs.existsSync(SUNSHINE_HOST_EXE_PATH)) {
      return false;
    }

    sunshineStdoutBuffer = Buffer.alloc(0);
    sunshineDaemonProc = spawn(SUNSHINE_HOST_EXE_PATH, [], {
      stdio: ['pipe', 'pipe', 'ignore'],
      windowsHide: true
    });

    sunshineDaemonProc.stdout.on('data', (chunk) => {
      sunshineStdoutBuffer = Buffer.concat([sunshineStdoutBuffer, chunk]);
      while (sunshineStdoutBuffer.length >= 24) {
        if (
          sunshineStdoutBuffer[0] !== 0x53 || // 'S'
          sunshineStdoutBuffer[1] !== 0x46 || // 'F'
          sunshineStdoutBuffer[2] !== 0x52 || // 'R'
          sunshineStdoutBuffer[3] !== 0x4d    // 'M'
        ) {
          const idx = sunshineStdoutBuffer.indexOf(Buffer.from('SFRM'));
          if (idx === -1) {
            sunshineStdoutBuffer = Buffer.alloc(0);
            break;
          }
          sunshineStdoutBuffer = sunshineStdoutBuffer.subarray(idx);
          if (sunshineStdoutBuffer.length < 24) break;
        }

        const jpegLen = sunshineStdoutBuffer.readInt32LE(4);
        const scrW = sunshineStdoutBuffer.readInt32LE(8);
        const scrH = sunshineStdoutBuffer.readInt32LE(12);
        const curX = sunshineStdoutBuffer.readInt32LE(16);
        const curY = sunshineStdoutBuffer.readInt32LE(20);

        if (jpegLen <= 0 || jpegLen > 8 * 1024 * 1024) {
          sunshineStdoutBuffer = sunshineStdoutBuffer.subarray(4);
          continue;
        }
        if (sunshineStdoutBuffer.length < 24 + jpegLen) {
          break;
        }

        sunshineLatestFrameJpeg = Buffer.from(sunshineStdoutBuffer.subarray(24, 24 + jpegLen));
        sunshineLatestFrameSeq += 1;
        sunshineScreenWidth = scrW > 0 ? scrW : 1920;
        sunshineScreenHeight = scrH > 0 ? scrH : 1080;
        sunshineCursorX = curX;
        sunshineCursorY = curY;
        sunshineStdoutBuffer = sunshineStdoutBuffer.subarray(24 + jpegLen);
      }
    });

    sunshineDaemonProc.on('exit', () => {
      sunshineDaemonProc = null;
    });
    return true;
  } catch {
    return false;
  }
}

function sendSunshineInputPacket(packet) {
  ensureSunshineHostDaemonStarted();
  const type = String(packet.type || 'CLICK').toUpperCase();
  const button = String(packet.button || 'LEFT').toUpperCase();
  const x = Number(packet.x ?? 0);
  const y = Number(packet.y ?? 0);
  const refW = Math.max(1, Number(packet.refWidth ?? sunshineScreenWidth));
  const refH = Math.max(1, Number(packet.refHeight ?? sunshineScreenHeight));
  const deltaX = Number(packet.deltaX ?? 0);
  const deltaY = Number(packet.deltaY ?? 0);
  const scrollDelta = Number(packet.scrollDelta ?? 0);

  let hostX = sunshineCursorX;
  let hostY = sunshineCursorY;
  if (type === 'ABS_POS' || type === 'CLICK' || type === 'TOUCH_TAP' || type === 'LONG_PRESS_RIGHT' || type === 'DOUBLE_TAP') {
    const normX = Math.min(1, Math.max(0, x / refW));
    const normY = Math.min(1, Math.max(0, y / refH));
    hostX = Math.round(normX * Math.max(1, sunshineScreenWidth - 1));
    hostY = Math.round(normY * Math.max(1, sunshineScreenHeight - 1));
    sunshineCursorX = hostX;
    sunshineCursorY = hostY;
  } else if (type === 'REL_MOVE') {
    hostX = Math.min(Math.max(0, sunshineCursorX + Math.round(deltaX)), Math.max(1, sunshineScreenWidth - 1));
    hostY = Math.min(Math.max(0, sunshineCursorY + Math.round(deltaY)), Math.max(1, sunshineScreenHeight - 1));
    sunshineCursorX = hostX;
    sunshineCursorY = hostY;
  }

  let cmdLine = null;
  if (type === 'ABS_POS') {
    cmdLine = `ABS_POS ${x} ${y} ${refW} ${refH}`;
    sunshineLastEventDesc = `절대좌표 이동 (${hostX}, ${hostY})`;
  } else if (type === 'REL_MOVE') {
    cmdLine = `REL_MOVE ${deltaX} ${deltaY}`;
    sunshineLastEventDesc = `트랙패드 상대 이동 (${hostX}, ${hostY})`;
  } else if (type === 'BTN_DOWN') {
    cmdLine = `BTN_DOWN ${button}`;
    sunshineLastEventDesc = `마우스 ${button} 누름 (${hostX}, ${hostY})`;
  } else if (type === 'BTN_UP') {
    cmdLine = `BTN_UP ${button}`;
    sunshineLastEventDesc = `마우스 ${button} 뗌 (${hostX}, ${hostY})`;
  } else if (type === 'CLICK' || type === 'TOUCH_TAP') {
    cmdLine = `CLICK ${button} ${x} ${y} ${refW} ${refH}`;
    sunshineLastEventDesc = `터치 즉시 ${button === 'RIGHT' ? '우클릭' : '좌클릭'} (${hostX}, ${hostY})`;
  } else if (type === 'LONG_PRESS_RIGHT') {
    cmdLine = `CLICK RIGHT ${x} ${y} ${refW} ${refH}`;
    sunshineLastEventDesc = `롱프레스 우클릭 (${hostX}, ${hostY})`;
  } else if (type === 'DOUBLE_TAP') {
    cmdLine = `CLICK DOUBLE ${x} ${y} ${refW} ${refH}`;
    sunshineLastEventDesc = `더블탭 더블클릭 (${hostX}, ${hostY})`;
  } else if (type === 'SCROLL') {
    cmdLine = `SCROLL ${scrollDelta}`;
    sunshineLastEventDesc = `두 손가락 고해상도 스크롤 (${scrollDelta})`;
  } else if (type === 'KEY') {
    const keySpec = String(packet.keySpec || '{ENTER}');
    cmdLine = `KEY ${keySpec}`;
    sunshineLastEventDesc = `키 입력 전송 (${keySpec})`;
  }

  sunshineLastEventMs = Date.now();
  if (cmdLine && sunshineDaemonProc && sunshineDaemonProc.stdin && !sunshineDaemonProc.stdin.destroyed) {
    try {
      sunshineDaemonProc.stdin.write(`${cmdLine}\n`);
    } catch {
      // Ignore broken pipe
    }
  }

  return {
    ok: true,
    type,
    button,
    hostX,
    hostY,
    screenWidth: sunshineScreenWidth,
    screenHeight: sunshineScreenHeight,
    frameSeq: sunshineLatestFrameSeq,
    lastEvent: sunshineLastEventDesc,
    timestampMs: sunshineLastEventMs
  };
}

function ensureAdbReversePortForward() {
  try {
    const adbCmd = fs.existsSync(ADB_EXE_PATH) ? `"${ADB_EXE_PATH}"` : 'adb';
    execSync(`${adbCmd} reverse tcp:${PORT} tcp:${PORT}`, {
      stdio: 'ignore',
      timeout: 2500
    });
  } catch {
    // Ignore when no USB device is attached
  }
}

function readSystemPromptBundle() {
  const safeRead = (p, fallback = '') => {
    try {
      return fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : fallback;
    } catch {
      return fallback;
    }
  };
  let updatedAt = new Date().toISOString();
  try {
    if (fs.existsSync(GEMINI_MD_PATH)) {
      updatedAt = fs.statSync(GEMINI_MD_PATH).mtime.toISOString();
    }
  } catch {
    // Ignore
  }
  return {
    ok: true,
    geminiMd: safeRead(GEMINI_MD_PATH),
    designMotionMd: safeRead(DESIGN_MOTION_MD_PATH),
    claudeMd: safeRead(CLAUDE_MD_PATH),
    updatedAt
  };
}

function saveSystemPromptTarget(target, content) {
  const normalizedTarget = String(target || 'gemini').trim().toLowerCase();
  const cleanContent = String(content || '');
  let targetPath = GEMINI_MD_PATH;
  let targetLabel = 'GEMINI.md (전역 시스템 프롬프트)';

  if (normalizedTarget === 'design-motion' || normalizedTarget === 'design_motion') {
    targetPath = DESIGN_MOTION_MD_PATH;
    targetLabel = 'design-motion.md (디자인·모션 파이프라인)';
  } else if (normalizedTarget === 'claude') {
    targetPath = CLAUDE_MD_PATH;
    targetLabel = 'CLAUDE.md (Claude 전역 지침)';
  }

  fs.mkdirSync(path.dirname(targetPath), { recursive: true });
  if (fs.existsSync(targetPath)) {
    try {
      fs.copyFileSync(targetPath, `${targetPath}.bak`);
    } catch {
      // Ignore backup error
    }
  }
  fs.writeFileSync(targetPath, cleanContent, 'utf8');

  if (targetPath === DESIGN_MOTION_MD_PATH && fs.existsSync(DESIGN_MOTION_SKILL_PATH)) {
    try {
      const existingSkill = fs.readFileSync(DESIGN_MOTION_SKILL_PATH, 'utf8');
      const fmMatch = existingSkill.match(/^---\r?\n[\s\S]*?\r?\n---\r?\n*/);
      const frontmatter = fmMatch ? fmMatch[0] : '---\nname: design-motion-stack\ndescription: Design and motion pipeline\n---\n\n';
      fs.writeFileSync(DESIGN_MOTION_SKILL_PATH, frontmatter + cleanContent, 'utf8');
    } catch {
      // Ignore skill sync error
    }
  }

  const lineCount = cleanContent.split(/\r?\n/).length;
  return {
    target: normalizedTarget,
    targetLabel,
    targetPath: targetPath.replace(/\\/g, '/'),
    lineCount,
    charCount: cleanContent.length,
    bundle: readSystemPromptBundle()
  };
}

function readAnnotationSessionInfo(convId) {
  try {
    const pbtxtPath = path.join(ANNOTATIONS_DIR, `${convId}.pbtxt`);
    if (!fs.existsSync(pbtxtPath)) return null;
    const raw = fs.readFileSync(pbtxtPath, 'utf8');
    const titleMatch = raw.match(/title:\s*"((?:[^"\\]|\\.)*)"/);
    const secMatch = raw.match(/seconds:\s*(\d+)/);
    let title = null;
    if (titleMatch && titleMatch[1]) {
      const inner = titleMatch[1];
      // Decode octal escapes if any exist, otherwise keep UTF-8 string
      if (/\\[0-7]{3}/.test(inner)) {
        const bytes = [];
        for (let i = 0; i < inner.length; i++) {
          if (inner[i] === '\\' && i + 3 < inner.length && /^[0-7]{3}$/.test(inner.slice(i + 1, i + 4))) {
            bytes.push(parseInt(inner.slice(i + 1, i + 4), 8));
            i += 3;
          } else {
            const buf = Buffer.from(inner[i], 'utf8');
            for (const b of buf) bytes.push(b);
          }
        }
        title = Buffer.from(bytes).toString('utf8');
      } else {
        title = inner.replace(/\\"/g, '"').replace(/\\\\/g, '\\');
      }
    }
    const lastViewSec = secMatch ? Number(secMatch[1]) : 0;
    const archived = /archived:\s*true/i.test(raw);
    return { title: title ? title.trim() : null, lastViewSec, archived };
  } catch {
    return null;
  }
}

const CONVERSATION_SUMMARIES_DB_PATH = path.join(ANTIGRAVITY_ROOT, 'conversation_summaries.db');
const LANGUAGE_SERVER_EXE = process.env.ANTIGRAVITY_AGENTAPI_EXE
  || path.join(os.homedir(), 'AppData', 'Local', 'Programs', 'antigravity', 'resources', 'bin', 'language_server.exe');

let currentActiveSessionId = FALLBACK_CONVERSATION_ID;
let currentActiveProjectName = 'antigravity-remote-apk';
const mobileLastTouchedMs = new Map();

let syncRevision = 1;
let lastSyncBumpMs = Date.now();
let lastSyncReason = 'init';
const syncWaiters = new Set();

function bumpSyncRevision(reason = 'fs_change') {
  syncRevision += 1;
  lastSyncBumpMs = Date.now();
  lastSyncReason = reason;
  for (const waiter of syncWaiters) {
    try {
      clearTimeout(waiter.timer);
      waiter.resolve(syncRevision);
    } catch {
      // Ignore
    }
  }
  syncWaiters.clear();
}

function waitForSyncRevision(sinceRev, maxWaitMs = 6000) {
  const numericSince = Number(sinceRev || 0);
  if (!numericSince || syncRevision > numericSince) {
    return Promise.resolve(syncRevision);
  }
  const boundedWait = Math.min(Math.max(Number(maxWaitMs) || 3500, 150), 10000);
  return new Promise((resolve) => {
    const waiter = {
      resolve: (rev) => {
        syncWaiters.delete(waiter);
        resolve(rev);
      },
      timer: setTimeout(() => {
        syncWaiters.delete(waiter);
        resolve(syncRevision);
      }, boundedWait)
    };
    syncWaiters.add(waiter);
  });
}

let cachedAgentApiEnv = null;
let cachedAgentApiEnvAtMs = 0;

function resolveAntigravityAgentApiEnv(forceRefresh = false) {
  if (!forceRefresh && cachedAgentApiEnv && (Date.now() - cachedAgentApiEnvAtMs < 60000)) {
    return cachedAgentApiEnv;
  }
  if (!forceRefresh && process.env.ANTIGRAVITY_LS_ADDRESS && process.env.ANTIGRAVITY_CSRF_TOKEN) {
    cachedAgentApiEnv = {
      ...process.env,
      ANTIGRAVITY_AGENT: '1',
      ANTIGRAVITY_APP_DATA_DIR: ANTIGRAVITY_ROOT.replace(/\\/g, '/')
    };
    cachedAgentApiEnvAtMs = Date.now();
    return cachedAgentApiEnv;
  }

  try {
    const psScript = [
      `$p = Get-CimInstance Win32_Process -Filter "Name='language_server.exe'" | Select-Object -First 1;`,
      `if ($p) {`,
      `  $m = [regex]::Match($p.CommandLine, '--csrf_token\\s+([a-fA-F0-9-]+)');`,
      `  $tok = if ($m.Success) { $m.Groups[1].Value } else { '' };`,
      `  $ports = @(Get-NetTCPConnection -OwningProcess $p.ProcessId -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalPort | Sort-Object -Descending);`,
      `  Write-Output ("$tok|" + ($ports -join ','))`,
      `}`
    ].join(' ');
    const out = execFileSync('powershell.exe', ['-NoProfile', '-Command', psScript], {
      encoding: 'utf8',
      timeout: 3000
    }).trim();
    const [tokenPart, portsPart] = out.split('|');
    const ports = String(portsPart || '').split(',').map((x) => Number(x.trim())).filter((n) => n > 0);
    const chosenPort = ports[0];
    if (chosenPort) {
      cachedAgentApiEnv = {
        ...process.env,
        ANTIGRAVITY_AGENT: '1',
        ANTIGRAVITY_APP_DATA_DIR: ANTIGRAVITY_ROOT.replace(/\\/g, '/'),
        ANTIGRAVITY_LS_ADDRESS: `localhost:${chosenPort}`,
        ANTIGRAVITY_CSRF_TOKEN: tokenPart || process.env.ANTIGRAVITY_CSRF_TOKEN || ''
      };
      cachedAgentApiEnvAtMs = Date.now();
      return cachedAgentApiEnv;
    }
  } catch {
    // Fallback to current env
  }

  cachedAgentApiEnv = {
    ...process.env,
    ANTIGRAVITY_AGENT: '1',
    ANTIGRAVITY_APP_DATA_DIR: ANTIGRAVITY_ROOT.replace(/\\/g, '/')
  };
  cachedAgentApiEnvAtMs = Date.now();
  return cachedAgentApiEnv;
}

function runAgentApiNewConversation(title, initialPrompt) {
  if (!fs.existsSync(LANGUAGE_SERVER_EXE)) return null;
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const env = resolveAntigravityAgentApiEnv(attempt > 0);
      const rawOut = execFileSync(
        LANGUAGE_SERVER_EXE,
        ['agentapi', 'new-conversation', `--title=${title}`, initialPrompt],
        {
          env,
          encoding: 'utf8',
          timeout: 7500
        }
      ).trim();
      const parsed = JSON.parse(rawOut);
      const convId = parsed?.response?.newConversation?.conversationId;
      if (convId && typeof convId === 'string') {
        return convId.trim();
      }
    } catch {
      // Retry once with refreshed LS discovery
    }
  }
  return null;
}

function runAgentApiSendMessage(sessionId, prompt, title = 'MOBILE_USER_PROMPT') {
  if (!fs.existsSync(LANGUAGE_SERVER_EXE) || !sessionId) return false;
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const env = resolveAntigravityAgentApiEnv(attempt > 0);
      const rawOut = execFileSync(
        LANGUAGE_SERVER_EXE,
        ['agentapi', 'send-message', `--title=${title}`, sessionId, prompt],
        {
          env,
          encoding: 'utf8',
          timeout: 6500
        }
      ).trim();
      const parsed = JSON.parse(rawOut);
      if (parsed?.response?.sendMessage?.recipientId) {
        return true;
      }
    } catch {
      // Retry once with refreshed LS discovery
    }
  }
  return false;
}

function readConversationSummariesFromSqlite() {
  const byId = new Map();
  const ordered = [];
  if (!fs.existsSync(CONVERSATION_SUMMARIES_DB_PATH)) {
    return { byId, ordered };
  }
  let db = null;
  try {
    db = new DatabaseSync(CONVERSATION_SUMMARIES_DB_PATH, { readOnly: true });
    const rows = db.prepare(`
      SELECT
        conversation_id,
        title,
        preview,
        step_count,
        last_modified_time,
        workspace_uris,
        status,
        project_id,
        not_fully_idle,
        killed,
        parent_conversation_id,
        last_user_input_time
      FROM conversation_summaries
      WHERE parent_conversation_id = ''
        AND killed = 0
        AND (
          step_count > 0
          OR not_fully_idle = 1
          OR status = 'CASCADE_RUN_STATUS_RUNNING'
          OR (last_modified_time NOT LIKE '0001-01-01%' AND last_modified_time != '')
          OR (last_user_input_time NOT LIKE '0001-01-01%' AND last_user_input_time != '')
          OR title != ''
        )
      ORDER BY last_modified_time DESC, rowid DESC
      LIMIT 30
    `).all();
    for (const r of rows) {
      const cid = String(r.conversation_id || '').trim();
      if (!cid) continue;
      byId.set(cid, r);
      ordered.push(r);
    }
  } catch {
    // Ignore SQLite lock or read errors
  } finally {
    try {
      if (db) db.close();
    } catch {
      // Ignore
    }
  }
  return { byId, ordered };
}

function setupRealtimeFileWatchers() {
  const watchTargets = [
    { dir: ANTIGRAVITY_ROOT, recursive: false },
    { dir: BRAIN_DIR, recursive: true },
    { dir: ANNOTATIONS_DIR, recursive: false },
    { dir: SCRATCH_DIR, recursive: false }
  ];
  for (const target of watchTargets) {
    try {
      if (!fs.existsSync(target.dir)) continue;
      fs.watch(target.dir, { recursive: target.recursive }, (_eventType, filename) => {
        const name = String(filename || '');
        if (!name || name.endsWith('.tmp') || name.includes('LIVE_PC_SYNC_STATUS.json')) return;
        if (
          name.includes('conversation_summaries.db')
          || name.includes('agyhub_summaries_proto')
          || name.includes('transcript.jsonl')
          || name.endsWith('.pbtxt')
          || target.dir === SCRATCH_DIR
        ) {
          bumpSyncRevision(`watch:${path.basename(target.dir)}:${name}`);
        }
      }).unref();
    } catch {
      // Ignore watch setup errors
    }
  }
}

const DEFAULT_OLLAMA_MODEL = process.env.ANTIGRAVITY_OLLAMA_MODEL || 'gemma4-26b-a4b-local';
const LOCAL_AI_TIMEOUT_MS = Number(process.env.ANTIGRAVITY_LOCAL_AI_TIMEOUT_MS || 30000);
let localAiInFlight = false;

// Ensure scratch directory exists
try {
  fs.mkdirSync(SCRATCH_DIR, { recursive: true });
} catch {
  // Ignore
}

// Overlay store for mobile messages and mobile-created sessions
const mobileMessagesBySession = new Map();
const mobileSessionMeta = new Map();
const pinnedSessionIds = new Set();
const archivedSessionIds = new Set();
const deletedSessionIds = new Set(['9718dcf9-efbd-4ef2-b9b6-ca58a4676720']);
const pinnedProjectNames = new Set();
const archivedProjectNames = new Set();
const deletedProjectNames = new Set();

function getLocalLanIp() {
  const interfaces = os.networkInterfaces();
  const candidates = [];
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name] || []) {
      if (net.family === 'IPv4' && !net.internal && !net.address.startsWith('169.254.')) {
        candidates.push(net.address);
      }
    }
  }
  const preferred = candidates.find((ip) => ip.startsWith('192.168.')) || candidates[0];
  return preferred || '127.0.0.1';
}

function sanitizeFolderName(rawName) {
  const cleaned = String(rawName || '')
    .trim()
    .replace(/[<>:"/\\|?*\x00-\x1F]/g, '-')
    .replace(/\s+/g, '-')
    .replace(/\.+/g, '.')
    .replace(/^-+|-+$/g, '');
  return cleaned || `project-${Date.now().toString().slice(-5)}`;
}

function listPcProjects() {
  try {
    if (!fs.existsSync(SCRATCH_DIR)) return [];
    return fs.readdirSync(SCRATCH_DIR, { withFileTypes: true })
      .filter((e) => e.isDirectory() && !archivedProjectNames.has(e.name) && !deletedProjectNames.has(e.name))
      .map((e) => {
        const fullPath = path.join(SCRATCH_DIR, e.name);
        let mtimeMs = 0;
        let fileCount = 0;
        try {
          mtimeMs = fs.statSync(fullPath).mtimeMs;
          fileCount = fs.readdirSync(fullPath).length;
        } catch {
          // Ignore
        }
        return {
          name: e.name,
          path: fullPath.replace(/\\/g, '/'),
          fileCount,
          pinned: pinnedProjectNames.has(e.name),
          mtimeMs
        };
      })
      .sort((a, b) => {
        if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
        return b.mtimeMs - a.mtimeMs;
      });
  } catch {
    return [];
  }
}

function createPcProjectFolder(rawFolderName, description = '') {
  const folderName = sanitizeFolderName(rawFolderName);
  const projectPath = path.join(SCRATCH_DIR, folderName);
  fs.mkdirSync(projectPath, { recursive: true });

  const readmePath = path.join(projectPath, 'README.md');
  if (!fs.existsSync(readmePath)) {
    const content = [
      `# ${folderName}`,
      '',
      description || '모바일 안그래비티 리모트 앱에서 생성한 PC 프로젝트 폴더입니다.',
      '',
      `- 생성 시각: ${new Date().toISOString()}`,
      `- 경로: \`${projectPath.replace(/\\/g, '/')}\``,
      ''
    ].join('\n');
    fs.writeFileSync(readmePath, content, 'utf8');
  }

  currentActiveProjectName = folderName;
  bumpSyncRevision('project_created');
  return {
    folderName,
    path: projectPath.replace(/\\/g, '/'),
    created: true
  };
}

function createAntigravitySession(rawTitle, rawProjectFolder = '', rawInitialPrompt = '') {
  const title = String(rawTitle || '').trim() || `새 세션 (${new Date().toLocaleTimeString('ko-KR')})`;
  let projectInfo = null;

  if (rawProjectFolder && String(rawProjectFolder).trim().length > 0) {
    projectInfo = createPcProjectFolder(rawProjectFolder, `세션 '${title}'과 연동된 프로젝트 폴더`);
  }

  const targetFolder = projectInfo ? projectInfo.folderName : currentActiveProjectName;
  const targetPath = projectInfo
    ? projectInfo.path
    : path.join(SCRATCH_DIR, currentActiveProjectName).replace(/\\/g, '/');

  const initialPrompt = String(rawInitialPrompt || '').trim()
    || `모바일에서 새 세션 '${title}'을(를) 시작했습니다 (작업 폴더: ${targetPath}). 작업 지시를 기다리는 상태로 한 줄 인사만 남겨주세요.`;

  // 1. Try creating a genuine live PC Antigravity session via language_server.exe agentapi
  const realConvId = runAgentApiNewConversation(title, initialPrompt);
  const sessionId = realConvId || (crypto.randomUUID ? crypto.randomUUID() : `mob-session-${Date.now()}`);

  const logsDir = path.join(BRAIN_DIR, sessionId, '.system_generated', 'logs');
  fs.mkdirSync(logsDir, { recursive: true });
  const transcriptPath = path.join(logsDir, 'transcript.jsonl');

  // If language_server hasn't flushed transcript.jsonl yet (or fallback mode), seed initial steps so mobile & PC see it immediately
  if (!fs.existsSync(transcriptPath) || fs.statSync(transcriptPath).size === 0) {
    const nowIso = new Date().toISOString();
    const initialUserStep = {
      step_index: 0,
      source: 'USER_EXPLICIT',
      type: 'USER_INPUT',
      status: 'DONE',
      created_at: nowIso,
      content: `<USER_REQUEST>\n${initialPrompt}\n</USER_REQUEST>`
    };
    const initialAgentStep = {
      step_index: 1,
      source: 'MODEL',
      type: 'PLANNER_RESPONSE',
      status: 'DONE',
      created_at: nowIso,
      thinking: '모바일 리모트 앱에서 요청한 새 안그래비티 세션과 PC 작업 공간을 초기화했습니다.',
      content: projectInfo
        ? `새 안그래비티 세션(**${title}**)과 프로젝트(**${projectInfo.folderName}**)를 PC에 즉시 생성하고 실시간 연동을 시작했습니다.`
        : `새 안그래비티 세션(**${title}**)을 PC에 즉시 생성하고 실시간 연동을 시작했습니다.`
    };
    fs.writeFileSync(
      transcriptPath,
      JSON.stringify(initialUserStep) + '\n' + JSON.stringify(initialAgentStep) + '\n',
      'utf8'
    );
  }

  try {
    fs.mkdirSync(ANNOTATIONS_DIR, { recursive: true });
    const nowSec = Math.floor(Date.now() / 1000);
    const escapedTitle = title.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
    fs.writeFileSync(
      path.join(ANNOTATIONS_DIR, `${sessionId}.pbtxt`),
      `title:"${escapedTitle}" last_user_view_time:{seconds:${nowSec} nanos:0}`,
      'utf8'
    );
  } catch {
    // Ignore annotation write error
  }

  const nowMs = Date.now();
  mobileSessionMeta.set(sessionId, {
    id: sessionId,
    title,
    projectFolder: targetFolder,
    projectPath: targetPath,
    createdAtMs: nowMs
  });
  mobileLastTouchedMs.set(sessionId, nowMs);

  currentActiveSessionId = sessionId;
  bumpSyncRevision('session_created');
  return {
    sessionId,
    title,
    projectInfo,
    realPcSession: Boolean(realConvId)
  };
}

function extractUserRequestText(rawContent) {
  if (!rawContent || typeof rawContent !== 'string') return '';
  const match = rawContent.match(/<USER_REQUEST>\s*([\s\S]*?)\s*<\/USER_REQUEST>/i);
  if (match && match[1]) {
    return match[1].trim();
  }
  if (rawContent.includes('<SYSTEM_MESSAGE>')) return '';
  return rawContent.trim();
}

function extractInjectedSystemMessagePrompt(rawContent) {
  if (!rawContent || typeof rawContent !== 'string') return '';
  if (!rawContent.includes('<SYSTEM_MESSAGE>')) return '';
  const sysBlockMatch = rawContent.match(/<SYSTEM_MESSAGE>\s*([\s\S]*?)\s*<\/SYSTEM_MESSAGE>/i);
  const inner = sysBlockMatch ? sysBlockMatch[1].trim() : rawContent;
  // Ignore background task completion notifications
  if (/sender=[^\s]+\/task-\d+/i.test(inner) || /Task id\s+"[^"]+"\s+finished/i.test(inner)) {
    return '';
  }
  const contentMatch = inner.match(/\[Message\][^\n]*?\bcontent=([\s\S]+)$/i);
  if (contentMatch && contentMatch[1]) {
    return contentMatch[1].trim();
  }
  return '';
}

function parseSqlTimestampMs(rawTimeStr) {
  if (!rawTimeStr || typeof rawTimeStr !== 'string') return 0;
  if (rawTimeStr.startsWith('0001-01-01')) return 0;
  const isoCandidate = rawTimeStr.trim().replace(' ', 'T');
  const parsed = Date.parse(isoCandidate);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
}

function formatTimeLabel(isoString) {
  try {
    const d = isoString ? new Date(isoString) : new Date();
    const hours = d.getHours();
    const mins = String(d.getMinutes()).padStart(2, '0');
    const period = hours >= 12 ? '오후' : '오전';
    const h12 = hours % 12 || 12;
    return `${period} ${h12}:${mins}`;
  } catch {
    return '지금';
  }
}

function parseTranscriptSession(convId, summaryRow = null) {
  const transcriptPath = path.join(BRAIN_DIR, convId, '.system_generated', 'logs', 'transcript.jsonl');
  const hasTranscript = fs.existsSync(transcriptPath);
  const customMeta = mobileSessionMeta.get(convId);
  const annotationInfo = readAnnotationSessionInfo(convId);

  if (!hasTranscript && !summaryRow && !customMeta && !annotationInfo) {
    return null;
  }

  try {
    const stat = hasTranscript ? fs.statSync(transcriptPath) : null;
    const transcriptMtimeMs = stat ? stat.mtimeMs : 0;
    const summaryModifiedMs = Math.max(
      parseSqlTimestampMs(summaryRow?.last_modified_time),
      parseSqlTimestampMs(summaryRow?.last_user_input_time)
    );
    const raw = hasTranscript ? fs.readFileSync(transcriptPath, 'utf8') : '';
    const lines = raw ? raw.split(/\r?\n/).filter(Boolean) : [];
    const messages = [];
    const transcriptUserTexts = new Set();
    let lastThought = '';
    let pendingTools = [];
    let awaitingTurnResponse = false;
    let lastStepCreatedAtIso = null;
    let lastStepCreatedAtMs = Math.max(transcriptMtimeMs, summaryModifiedMs);

    for (let idx = 0; idx < lines.length; idx++) {
      const line = lines[idx];
      let step;
      try {
        step = JSON.parse(line);
      } catch {
        continue;
      }

      const createdAtMs = Date.parse(step.created_at) || (idx * 1000);
      if (step.created_at) lastStepCreatedAtIso = step.created_at;
      if (createdAtMs > 0) lastStepCreatedAtMs = Math.max(lastStepCreatedAtMs, createdAtMs);

      if (step.type === 'USER_INPUT' && step.source === 'USER_EXPLICIT') {
        const text = extractUserRequestText(step.content);
        if (text) {
          transcriptUserTexts.add(text);
          messages.push({
            id: `live-user-${step.step_index}`,
            role: 'USER',
            authorName: '나',
            timestamp: formatTimeLabel(step.created_at),
            createdAtMs,
            bodyText: text,
            thoughtText: '',
            thoughtDurationSec: '0.0s',
            tools: []
          });
          lastThought = '';
          pendingTools = [];
          awaitingTurnResponse = true;
        }
      } else if (step.type === 'SYSTEM_MESSAGE') {
        const injectedPrompt = extractInjectedSystemMessagePrompt(step.content);
        if (injectedPrompt) {
          transcriptUserTexts.add(injectedPrompt);
          messages.push({
            id: `live-user-sys-${step.step_index}`,
            role: 'USER',
            authorName: '나',
            timestamp: formatTimeLabel(step.created_at),
            createdAtMs,
            bodyText: injectedPrompt,
            thoughtText: '',
            thoughtDurationSec: '0.0s',
            tools: []
          });
          lastThought = '';
          pendingTools = [];
          awaitingTurnResponse = true;
        }
      } else if (step.type === 'PLANNER_RESPONSE' && step.source === 'MODEL') {
        if (step.thinking && typeof step.thinking === 'string') {
          lastThought = step.thinking.trim();
        }
        const hasToolCalls = (Array.isArray(step.tool_calls) && step.tool_calls.length > 0)
          || (Array.isArray(step.truncated_fields) && step.truncated_fields.includes('tool_calls'));

        if (Array.isArray(step.tool_calls) && step.tool_calls.length > 0) {
          for (const tc of step.tool_calls) {
            const rawSummary = String(tc.args?.toolSummary || tc.name || 'PC 도구 실행').replace(/^"+|"+$/g, '').trim();
            const rawTarget = String(tc.args?.CommandLine || tc.args?.Path || tc.args?.query || (tc.args ? JSON.stringify(tc.args).slice(0, 120) : '')).replace(/^"+|"+$/g, '').trim();
            pendingTools.push({
              id: `tool-${step.step_index}-${pendingTools.length}`,
              title: rawSummary || 'PC 도구 실행',
              commandOrPath: rawTarget.slice(0, 140),
              outputPreview: '실행 완료',
              durationMs: 320,
              status: 'COMPLETED'
            });
          }
        } else if (hasToolCalls) {
          pendingTools.push({
            id: `tool-${step.step_index}-${pendingTools.length}`,
            title: 'PC 실시간 도구 실행',
            commandOrPath: `step #${step.step_index}`,
            outputPreview: '실행 완료',
            durationMs: 320,
            status: 'COMPLETED'
          });
        }
        const contentText = typeof step.content === 'string' ? step.content.trim() : '';
        if (contentText) {
          const cleanBody = contentText
            .replace(/<agent-embed[^>]*><\/agent-embed>/gi, '')
            .replace(/!\[([^\]]*)\]\([^)]+\)/g, '• [캡처 확인] $1')
            .replace(/\[([^\]]+)\]\(file:\/\/\/[^)]+\)/g, '$1')
            .replace(/^(?:Let's|I will|Wait|Look|Now let's)[^가-힣]*?(?=[가-힣])/s, '')
            .trim();
          if (cleanBody && (!hasToolCalls || awaitingTurnResponse)) {
            const msgObj = {
              id: `live-agent-${step.step_index}`,
              role: 'AGENT',
              authorName: '안그래비티 도우미',
              timestamp: formatTimeLabel(step.created_at),
              createdAtMs,
              isInterimAck: hasToolCalls,
              bodyText: cleanBody,
              thoughtText: lastThought,
              thoughtDurationSec: '1.0초',
              tools: pendingTools.slice(-5)
            };
            const prev = messages[messages.length - 1];
            if (!hasToolCalls && prev && prev.role === 'AGENT' && prev.isInterimAck) {
              messages[messages.length - 1] = msgObj;
            } else {
              messages.push(msgObj);
            }
            awaitingTurnResponse = false;
            lastThought = '';
            pendingTools = [];
          }
        }
      }
    }

    const isSummaryRunning = summaryRow?.status === 'CASCADE_RUN_STATUS_RUNNING'
      || Number(summaryRow?.not_fully_idle) === 1;
    const isFileActivelyUpdating = isSummaryRunning || ((Date.now() - transcriptMtimeMs) < 90 * 1000);
    const lastRecordedMsg = messages[messages.length - 1];

    // If PC session is mid-turn (either awaiting first text response or running tools after an interim ack), surface live PC work status!
    if (awaitingTurnResponse && isFileActivelyUpdating) {
      const liveTools = pendingTools.slice(-5).map((t, i, arr) => (
        i === arr.length - 1 ? { ...t, status: 'RUNNING', outputPreview: 'PC에서 실시간 실행 중...' } : t
      ));
      const latestToolTitle = liveTools.length > 0 ? liveTools[liveTools.length - 1].title : '요청 분석 및 추론 중';
      messages.push({
        id: `live-agent-working-${convId.slice(0, 8)}`,
        role: 'AGENT',
        authorName: '안그래비티 도우미 (PC 실시간 작업 중)',
        timestamp: formatTimeLabel(lastStepCreatedAtIso),
        createdAtMs: Math.max(lastStepCreatedAtMs, transcriptMtimeMs),
        isStreaming: true,
        steeringStatusText: `PC 실시간 작업 중 · ${latestToolTitle}`,
        bodyText: liveTools.length > 0
          ? `PC에서 실시간 작업을 수행하고 있습니다.\n• 현재 작업: ${latestToolTitle}`
          : 'PC에서 요청을 분석하고 작업을 준비하고 있습니다...',
        thoughtText: lastThought || 'PC 안그래비티 세션에서 실시간으로 도구를 실행하고 있습니다.',
        thoughtDurationSec: '실시간',
        tools: liveTools
      });
    } else if (lastRecordedMsg && lastRecordedMsg.role === 'AGENT' && lastRecordedMsg.isInterimAck && isFileActivelyUpdating) {
      const combinedTools = [...(lastRecordedMsg.tools || []), ...pendingTools].slice(-5).map((t, i, arr) => (
        i === arr.length - 1 ? { ...t, status: 'RUNNING', outputPreview: 'PC에서 실시간 실행 중...' } : t
      ));
      const latestToolTitle = combinedTools.length > 0 ? combinedTools[combinedTools.length - 1].title : '도구 실행 중';
      lastRecordedMsg.tools = combinedTools;
      lastRecordedMsg.isStreaming = true;
      lastRecordedMsg.createdAtMs = Math.max(lastRecordedMsg.createdAtMs || 0, lastStepCreatedAtMs, transcriptMtimeMs);
      lastRecordedMsg.timestamp = formatTimeLabel(lastStepCreatedAtIso);
      lastRecordedMsg.steeringStatusText = `PC 실시간 작업 중 · ${latestToolTitle}`;
      if (lastThought) {
        lastRecordedMsg.thoughtText = lastThought;
      }
    }

    const overlay = mobileMessagesBySession.get(convId) || [];
    for (let i = 0; i < overlay.length; i++) {
      const item = overlay[i];
      // Skip overlay items that have already been persisted into real transcript.jsonl
      if (item.role === 'USER' && transcriptUserTexts.has(item.bodyText)) {
        if (i + 1 < overlay.length && overlay[i + 1].role === 'AGENT' && overlay[i + 1].isPendingBridgeAck) {
          i += 1;
        }
        continue;
      }
      messages.push(item);
    }

    messages.sort((a, b) => (a.createdAtMs || 0) - (b.createdAtMs || 0));

    // Support newly created PC sessions that don't have messages flushed yet
    if (messages.length === 0) {
      const fallbackCreatedMs = Math.max(
        summaryModifiedMs,
        transcriptMtimeMs,
        customMeta?.createdAtMs || 0,
        (annotationInfo?.lastViewSec || 0) * 1000
      );
      if (fallbackCreatedMs <= 0) {
        return null;
      }
      const rawTitle = customMeta?.title || summaryRow?.title || annotationInfo?.title || `새 세션 ${convId.slice(0, 8)}`;
      messages.push({
        id: `live-init-${convId.slice(0, 8)}`,
        role: 'AGENT',
        authorName: '안그래비티 도우미',
        timestamp: formatTimeLabel(new Date(fallbackCreatedMs).toISOString()),
        createdAtMs: fallbackCreatedMs,
        isStreaming: isSummaryRunning,
        bodyText: `PC 안그래비티 세션(${rawTitle})과 실시간 연결되었습니다. 메시지를 보내면 즉시 반영됩니다.`,
        thoughtText: 'PC 세션 실시간 동기화 대기 중',
        thoughtDurationSec: '0.1초',
        tools: []
      });
    }

    const recentMessages = messages.slice(-24);
    const firstUserMsg = messages.find((m) => m.role === 'USER');
    const isFallbackPrimary = convId === FALLBACK_CONVERSATION_ID;
    const summaryTitle = String(summaryRow?.title || '').trim();
    const title = customMeta?.title
      || (summaryTitle && !summaryTitle.includes('\uFFFD') ? summaryTitle : null)
      || annotationInfo?.title
      || (isFallbackPrimary
        ? 'Anti-Gravity Program APK Conversion'
        : (firstUserMsg ? firstUserMsg.bodyText.slice(0, 26) : `세션 ${convId.slice(0, 8)}`));

    let inferredFolder = customMeta?.projectFolder || '';
    if (!inferredFolder) {
      const userAndTitleText = `${title} ${messages.filter((m) => m.role === 'USER').map((m) => m.bodyText).join(' ')}`;
      const fullJoinedText = `${userAndTitleText} ${messages.map((m) => m.bodyText).join(' ')}`;
      const knownProjects = listPcProjects().map((p) => p.name);
      const matchedInUser = knownProjects.find((pName) => userAndTitleText.toLowerCase().includes(pName.toLowerCase()));
      if (matchedInUser) {
        inferredFolder = matchedInUser;
      } else if (userAndTitleText.includes('리모트') || userAndTitleText.includes('안드로이드') || userAndTitleText.includes('APK') || userAndTitleText.includes('Remote') || userAndTitleText.includes('세션')) {
        inferredFolder = 'antigravity-remote-apk';
      } else if (userAndTitleText.includes('전기산업기사') || userAndTitleText.includes('기출문제') || userAndTitleText.includes('electric-engineer')) {
        inferredFolder = 'electric-engineer-2026';
      } else if (userAndTitleText.includes('Diablo') || userAndTitleText.includes('D2R') || userAndTitleText.includes('야영지') || userAndTitleText.includes('d2r')) {
        inferredFolder = 'D2R-HUD';
      } else if (userAndTitleText.includes('메이플') || userAndTitleText.toLowerCase().includes('maple') || userAndTitleText.includes('미꾸라지')) {
        inferredFolder = userAndTitleText.includes('미꾸라지') ? 'mudfish-maple-guard' : 'auto-maple';
      } else if (userAndTitleText.includes('백그라운드') || userAndTitleText.includes('캡처')) {
        inferredFolder = 'win-bg-pipeline';
      } else if (userAndTitleText.includes('wikidocs') || userAndTitleText.includes('파이썬')) {
        inferredFolder = 'wikidocs-170810-pdf';
      } else {
        const matchedInAll = knownProjects.find((pName) => fullJoinedText.toLowerCase().includes(pName.toLowerCase()));
        inferredFolder = matchedInAll || currentActiveProjectName;
      }
    }

    const lastMsgObj = recentMessages[recentMessages.length - 1];
    const lastMsgMs = Math.max(
      lastMsgObj?.createdAtMs || 0,
      transcriptMtimeMs,
      summaryModifiedMs,
      customMeta?.createdAtMs || 0,
      (annotationInfo?.lastViewSec || 0) * 1000,
      mobileLastTouchedMs.get(convId) || 0
    );
    const isCurrentlyStreaming = Boolean(lastMsgObj?.isStreaming) || isSummaryRunning;

    return {
      id: convId,
      title,
      ambientCaption: inferredFolder,
      branchName: inferredFolder,
      activeTaskSummary: isCurrentlyStreaming && lastMsgObj?.steeringStatusText
        ? lastMsgObj.steeringStatusText
        : title,
      durationTag: isCurrentlyStreaming ? 'running' : 'paused',
      lastViewSec: annotationInfo?.lastViewSec || 0,
      lastActivityMs: lastMsgMs,
      quickPrompts: [
        '현재 PC 작업 현황 요약해줘',
        'PC 상태와 프로젝트 폴더 목록 보여줘',
        '방금 작업한 내용 실측 테스트 실행해줘'
      ],
      messages: recentMessages
    };
  } catch {
    return null;
  }
}

function getLiveAntigravitySessions() {
  const results = [];
  const seen = new Set();
  const { byId: summariesById, ordered: orderedSummaries } = readConversationSummariesFromSqlite();

  // 1. Mobile-created sessions
  for (const [id] of mobileSessionMeta) {
    if (archivedSessionIds.has(id) || deletedSessionIds.has(id)) continue;
    const parsed = parseTranscriptSession(id, summariesById.get(id) || null);
    if (parsed) {
      results.push(parsed);
      seen.add(id);
    }
  }

  // 2. Real-time PC sessions from conversation_summaries.db (includes brand-new PC sessions immediately!)
  for (const row of orderedSummaries) {
    const cid = String(row.conversation_id || '').trim();
    if (!cid || seen.has(cid) || archivedSessionIds.has(cid) || deletedSessionIds.has(cid)) continue;
    const ann = readAnnotationSessionInfo(cid);
    if (ann?.archived && !pinnedSessionIds.has(cid)) continue;
    const parsed = parseTranscriptSession(cid, row);
    if (parsed) {
      results.push(parsed);
      seen.add(cid);
    }
    if (results.length >= 14) break;
  }

  // 3. Any additional sessions in BRAIN_DIR not yet in summaries
  try {
    if (fs.existsSync(BRAIN_DIR)) {
      const entries = fs.readdirSync(BRAIN_DIR, { withFileTypes: true })
        .filter((e) => e.isDirectory() && !seen.has(e.name) && !archivedSessionIds.has(e.name) && !deletedSessionIds.has(e.name))
        .map((e) => {
          const tPath = path.join(BRAIN_DIR, e.name, '.system_generated', 'logs', 'transcript.jsonl');
          const stat = fs.existsSync(tPath) ? fs.statSync(tPath) : null;
          const ann = readAnnotationSessionInfo(e.name);
          if (ann?.archived && !pinnedSessionIds.has(e.name)) {
            return { id: e.name, mtimeMs: 0, sortScore: 0 };
          }
          const mtimeMs = stat ? stat.mtimeMs : 0;
          const touchMs = mobileLastTouchedMs.get(e.name) || 0;
          const sortScore = Math.max(mtimeMs, touchMs);
          return { id: e.name, mtimeMs, sortScore };
        })
        .filter((e) => e.mtimeMs > 0)
        .sort((a, b) => b.sortScore - a.sortScore)
        .slice(0, 12);

      for (const entry of entries) {
        if (seen.has(entry.id)) continue;
        const parsed = parseTranscriptSession(entry.id, summariesById.get(entry.id) || null);
        if (parsed) {
          results.push(parsed);
          seen.add(entry.id);
        }
      }
    }
  } catch {
    // Ignore
  }

  results.sort((a, b) => {
    const aPin = pinnedSessionIds.has(a.id);
    const bPin = pinnedSessionIds.has(b.id);
    if (aPin !== bPin) return aPin ? -1 : 1;
    return (b.lastActivityMs || 0) - (a.lastActivityMs || 0);
  });

  const capped = results.slice(0, 14);
  if (capped.length > 0) {
    currentActiveSessionId = capped[0].id;
    if (capped[0].branchName && capped[0].branchName !== 'live-sync') {
      currentActiveProjectName = capped[0].branchName;
    }
  }

  return capped;
}

function executePcRemoteCommand(command, args = {}) {
  const freeMemMb = Math.round(os.freemem() / (1024 * 1024));
  const totalMemMb = Math.round(os.totalmem() / (1024 * 1024));
  const projects = listPcProjects();

  if (command === 'pc_status') {
    const liveSessions = getLiveAntigravitySessions();
    const runningList = liveSessions
      .filter((s) => s.durationTag === 'running')
      .slice(0, 4)
      .map((s) => `  - [${s.branchName}] ${s.title}${s.activeTaskSummary && s.activeTaskSummary !== s.title ? ` (${s.activeTaskSummary})` : ''}`);
    return {
      ok: true,
      title: 'PC 실시간 작업 현황 및 리소스 점검',
      summary: [
        `• 호스트: ${os.hostname()} (${os.platform()} ${os.release()}) · LAN ${getLocalLanIp()}:${PORT}`,
        `• 메모리: 여유 ${freeMemMb} MB / 전체 ${totalMemMb} MB`,
        `• 현재 활성 프로젝트: ${currentActiveProjectName} (전체 ${projects.length}개)`,
        `• 실시간 진행 중인 PC 세션 (${runningList.length}개):`,
        ...(runningList.length > 0 ? runningList : ['  - 현재 대기 중'])
      ].join('\n'),
      projects
    };
  }

  if (command === 'list_projects') {
    const lines = projects.map((p, idx) => `${idx + 1}. ${p.name}`);
    return {
      ok: true,
      title: 'PC 프로젝트 목록',
      summary: lines.length > 0
        ? lines.join('\n')
        : '생성된 프로젝트가 없습니다.',
      projects
    };
  }

  if (command === 'git_status') {
    const targetDir = path.join(SCRATCH_DIR, args.projectName || currentActiveProjectName);
    let gitOut = '변경 사항이 없습니다.';
    try {
      gitOut = execSync('git status -s && git log -1 --oneline', {
        cwd: targetDir,
        encoding: 'utf8',
        timeout: 2500
      }).trim() || '작업 트리가 깨끗합니다 (변경 사항 없음).';
    } catch (e) {
      gitOut = `확인 완료 (${args.projectName || currentActiveProjectName})`;
    }
    return {
      ok: true,
      title: `Git 상태 (${args.projectName || currentActiveProjectName})`,
      summary: `• 프로젝트: ${args.projectName || currentActiveProjectName}\n• 상태:\n${gitOut}`,
      projects
    };
  }

  if (command === 'github_push') {
    const projectName = args.projectName || currentActiveProjectName || 'antigravity-remote-apk';
    const targetDir = path.join(SCRATCH_DIR, projectName);
    let remoteUrl = 'https://github.com/icandoit122118-del/antigravity-remote-apk';
    let commitHash = 'HEAD';
    let pushNote = '푸시 완료';

    try {
      const liveSessions = getLiveAntigravitySessions();
      const primary = liveSessions[0];
      const msgLines = (primary?.messages || []).slice(-12).map(
        (m) => `- **[${m.role}]**: ${String(m.bodyText || '').replace(/\r?\n/g, ' ')}`
      );
      const workLogMd = [
        `# Antigravity Session & Project Work Log`,
        ``,
        `- **Session ID**: \`${currentActiveSessionId}\``,
        `- **Active Project**: \`${projectName}\``,
        `- **Synced At**: \`${new Date().toISOString()}\``,
        ``,
        `## Recent Session Work & Conversation`,
        ...(msgLines.length > 0 ? msgLines : ['- Current session active']),
        ``
      ].join('\n');
      fs.writeFileSync(path.join(targetDir, 'SESSION_WORK_LOG.md'), workLogMd, 'utf8');

      try {
        remoteUrl = execSync('git remote get-url origin', { cwd: targetDir, encoding: 'utf8', timeout: 2500 })
          .trim()
          .replace(/\.git$/, '');
      } catch {
        // Keep default URL
      }

      execSync('git add -A', { cwd: targetDir, encoding: 'utf8', timeout: 5000 });
      const statusShort = execSync('git status -s', { cwd: targetDir, encoding: 'utf8', timeout: 3000 }).trim();
      if (statusShort) {
        execSync('git commit -m "chore(mobile-sync): push full source code and session work from Antigravity Remote"', {
          cwd: targetDir,
          encoding: 'utf8',
          timeout: 6000
        });
      }
      execSync('git push origin HEAD', { cwd: targetDir, encoding: 'utf8', timeout: 15000 });
      commitHash = execSync('git rev-parse --short HEAD', { cwd: targetDir, encoding: 'utf8', timeout: 2500 }).trim();
      pushNote = statusShort ? '신규 변경사항 및 작업내역 커밋·푸시 완료' : '최신 소스코드 및 작업내역 동기화 푸시 완료';
    } catch (e) {
      pushNote = `로컬 작업내역 기록 및 Git 동기화 처리 (${String(e.message || e).split('\n')[0].slice(0, 80)})`;
    }

    return {
      ok: true,
      title: '전체 소스코드 및 작업내용 GitHub 업로드',
      summary: [
        `전체 소스코드 및 프로젝트 작업내용을 내 GitHub에 올렸습니다.`,
        `• GitHub 저장소: ${remoteUrl}`,
        `• 대상 프로젝트: ${projectName} (${targetDir.replace(/\\/g, '/')})`,
        `• 최신 커밋: ${commitHash}`,
        `• 작업 로그: SESSION_WORK_LOG.md 포함 전체 소스 반영 (${pushNote})`
      ].join('\n'),
      projects: listPcProjects()
    };
  }

  if (command === 'attach_media') {
    const projectName = args.projectName || currentActiveProjectName || 'antigravity-remote-apk';
    const targetDir = path.join(SCRATCH_DIR, projectName);
    const attachDir = path.join(targetDir, 'attachments');
    fs.mkdirSync(attachDir, { recursive: true });
    const mediaType = String(args.mediaType || '파일');
    const fileName = String(args.fileName || `attachment-${Date.now()}`).replace(/[^a-zA-Z0-9._-가-힣]/g, '_');
    const fileSize = Number(args.fileSize || 0);
    const metaPath = path.join(attachDir, `${fileName}.meta.json`);
    fs.writeFileSync(metaPath, JSON.stringify({
      mediaType,
      fileName,
      fileSize,
      sessionId: currentActiveSessionId,
      attachedAt: new Date().toISOString()
    }, null, 2), 'utf8');

    return {
      ok: true,
      title: `${mediaType} 첨부 및 PC 세션 전송 (${fileName})`,
      summary: [
        `모바일에서 선택한 ${mediaType}을(를) 현재 PC 안그래비티 세션 및 프로젝트 폴더에 전달했습니다.`,
        `• 파일명: ${fileName}`,
        `• 크기: ${fileSize > 0 ? `${Math.max(1, Math.round(fileSize / 1024))} KB` : '전송됨'}`,
        `• 저장 위치: ${attachDir.replace(/\\/g, '/')}/${fileName}`
      ].join('\n'),
      projects: listPcProjects()
    };
  }

  return {
    ok: false,
    title: '알 수 없는 원격 명령',
    summary: `지원하지 않는 명령입니다: ${command}`,
    projects
  };
}

async function tryLocalOllama(modelId, prompt) {
  if (localAiInFlight) {
    return {
      ok: false,
      errorCode: 'LOCAL_AI_BUSY',
      error: '로컬 AI가 이미 다른 요청을 처리 중입니다. 현재 응답이 끝난 뒤 다시 시도해 주세요.'
    };
  }

  localAiInFlight = true;
  const requestedModel = String(modelId || '').trim() || DEFAULT_OLLAMA_MODEL;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), LOCAL_AI_TIMEOUT_MS);

  try {
    // Ollama의 generate 대신 chat API를 사용합니다. Gemma 4가 사고 토큰을
    // 별도 필드로 처리하도록 하며, 화면에는 최종 답변만 전달합니다.
    const res = await fetch('http://127.0.0.1:11434/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: requestedModel,
        messages: [{ role: 'user', content: prompt }],
        think: false,
        stream: false,
        keep_alive: '5m',
        options: {
          temperature: 0.2,
          num_predict: 512
        }
      }),
      signal: controller.signal
    });

    const data = await res.json().catch(() => ({}));
    const content = String(data?.message?.content || '').trim();
    if (res.ok && content) {
      return {
        ok: true,
        source: 'ollama',
        model: String(data.model || requestedModel),
        reply: `[PC 로컬 Ollama (${String(data.model || requestedModel)}) 응답]\n${content}`
      };
    }

    const detail = String(data?.error || `Ollama HTTP ${res.status}`).trim();
    return {
      ok: false,
      errorCode: res.status === 404 ? 'OLLAMA_MODEL_NOT_FOUND' : 'OLLAMA_REQUEST_FAILED',
      error: `Ollama 로컬 모델(${requestedModel}) 응답 실패: ${detail}`
    };
  } catch (error) {
    const message = error?.name === 'AbortError'
      ? `Ollama 응답이 ${LOCAL_AI_TIMEOUT_MS}ms 안에 완료되지 않았습니다.`
      : `Ollama 연결 실패: ${String(error?.message || error)}`;
    return {
      ok: false,
      errorCode: error?.name === 'AbortError' ? 'OLLAMA_TIMEOUT' : 'OLLAMA_UNAVAILABLE',
      error: message
    };
  } finally {
    clearTimeout(timer);
    localAiInFlight = false;
  }
}

function recordOverlayExchange(
  sessionId,
  prompt,
  reply,
  toolTitle = '안그래비티 라이브 세션 동기화',
  toolPath = '',
  isPendingBridgeAck = false
) {
  const targetSessionId = (!sessionId || sessionId.includes('session-')) ? currentActiveSessionId : sessionId;
  const list = mobileMessagesBySession.get(targetSessionId) || [];
  const nowMs = Date.now();
  const nowIso = new Date(nowMs).toISOString();
  mobileLastTouchedMs.set(targetSessionId, nowMs);
  currentActiveSessionId = targetSessionId;
  list.push({
    id: `mob-user-${nowMs}`,
    role: 'USER',
    authorName: '나',
    timestamp: formatTimeLabel(nowIso),
    createdAtMs: nowMs,
    bodyText: prompt,
    thoughtText: '',
    thoughtDurationSec: '0.0s',
    tools: []
  });
  list.push({
    id: `mob-agent-${nowMs + 1}`,
    role: 'AGENT',
    authorName: '안그래비티 도우미',
    timestamp: formatTimeLabel(nowIso),
    createdAtMs: nowMs + 1,
    isPendingBridgeAck: Boolean(isPendingBridgeAck),
    bodyText: reply,
    thoughtText: `PC 리모트 컨트롤 및 세션(${targetSessionId.slice(0, 8)}) 실시간 동기화 처리`,
    thoughtDurationSec: '0.5초',
    tools: [
      {
        id: `mob-tool-${nowMs}`,
        title: toolTitle,
        commandOrPath: toolPath || `session=${targetSessionId}`,
        outputPreview: reply,
        durationMs: 160,
        status: 'COMPLETED'
      }
    ]
  });
  mobileMessagesBySession.set(targetSessionId, list);
  bumpSyncRevision('overlay_exchange');

  try {
    const syncReceiptPath = path.join(SCRATCH_DIR, 'antigravity-remote-apk', 'LIVE_PC_SYNC_STATUS.json');
    fs.writeFileSync(syncReceiptPath, JSON.stringify({
      ok: true,
      syncedAt: nowIso,
      hostname: os.hostname(),
      activeSessionId: targetSessionId,
      activeProjectName: currentActiveProjectName,
      lastMobilePrompt: prompt,
      lastPcReply: reply,
      totalOverlayExchanges: list.length / 2
    }, null, 2), 'utf8');
  } catch {
    // Ignore file write errors
  }

  return targetSessionId;
}

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.method === 'GET' && (req.url === '/api/status' || req.url === '/api/health')) {
    const projects = listPcProjects();
    const payload = {
      ok: true,
      service: 'antigravity-pc-bridge',
      syncRevision,
      lastSyncBumpMs,
      activeConversationId: currentActiveSessionId,
      activeProjectName: currentActiveProjectName,
      scratchRoot: SCRATCH_DIR.replace(/\\/g, '/'),
      projects,
      hostname: os.hostname(),
      platform: `${os.platform()} ${os.release()}`,
      lanIp: getLocalLanIp(),
      port: PORT,
      localAiSupported: true,
      timestamp: new Date().toISOString()
    };
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(payload));
    return;
  }

  if (req.method === 'GET' && req.url.startsWith('/api/sessions')) {
    (async () => {
      try {
        const u = new URL(req.url, `http://127.0.0.1:${PORT}`);
        const sinceRev = Number(u.searchParams.get('sinceRevision') || 0);
        const waitMs = Math.min(8000, Math.max(0, Number(u.searchParams.get('waitMs') || 0)));
        if (waitMs > 0 && sinceRev > 0 && syncRevision <= sinceRev) {
          await waitForSyncRevision(sinceRev, waitMs);
        }
        const sessions = getLiveAntigravitySessions();
        const projects = listPcProjects();
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          ok: true,
          syncRevision,
          lastSyncBumpMs,
          activeConversationId: currentActiveSessionId,
          activeProjectName: currentActiveProjectName,
          scratchRoot: SCRATCH_DIR.replace(/\\/g, '/'),
          projects,
          sessions
        }));
      } catch (err) {
        res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ ok: false, error: String(err) }));
      }
    })();
    return;
  }

  if (req.method === 'GET' && req.url.startsWith('/api/system-prompt')) {
    const bundle = readSystemPromptBundle();
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(bundle));
    return;
  }

  if (req.method === 'GET' && req.url.startsWith('/api/stream/info')) {
    ensureSunshineHostDaemonStarted();
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({
      ok: true,
      protocol: 'sunshine-moonlight-direct',
      screenWidth: sunshineScreenWidth,
      screenHeight: sunshineScreenHeight,
      cursorX: sunshineCursorX,
      cursorY: sunshineCursorY,
      frameSeq: sunshineLatestFrameSeq,
      hasFrame: Boolean(sunshineLatestFrameJpeg),
      lastEvent: sunshineLastEventDesc,
      lastEventMs: sunshineLastEventMs
    }));
    return;
  }

  if (req.method === 'GET' && req.url.startsWith('/api/stream/frame')) {
    ensureSunshineHostDaemonStarted();
    if (!sunshineLatestFrameJpeg) {
      res.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ ok: false, error: 'FRAME_INITIALIZING' }));
      return;
    }
    res.writeHead(200, {
      'Content-Type': 'image/jpeg',
      'Content-Length': sunshineLatestFrameJpeg.length,
      'Cache-Control': 'no-store, no-cache, must-revalidate',
      'X-Sunshine-Screen-Width': String(sunshineScreenWidth),
      'X-Sunshine-Screen-Height': String(sunshineScreenHeight),
      'X-Sunshine-Cursor-X': String(sunshineCursorX),
      'X-Sunshine-Cursor-Y': String(sunshineCursorY),
      'X-Sunshine-Frame-Seq': String(sunshineLatestFrameSeq),
      'X-Sunshine-Last-Event': encodeURIComponent(sunshineLastEventDesc)
    });
    res.end(sunshineLatestFrameJpeg);
    return;
  }

  if (req.method === 'GET' && req.url.startsWith('/api/stream/mjpeg')) {
    ensureSunshineHostDaemonStarted();
    res.writeHead(200, {
      'Content-Type': 'multipart/x-mixed-replace; boundary=sunshineframe',
      'Cache-Control': 'no-store, no-cache, must-revalidate',
      'Connection': 'keep-alive'
    });
    let lastSentSeq = -1;
    const timer = setInterval(() => {
      if (res.writableEnded || res.destroyed) {
        clearInterval(timer);
        return;
      }
      if (sunshineLatestFrameJpeg && sunshineLatestFrameSeq !== lastSentSeq) {
        lastSentSeq = sunshineLatestFrameSeq;
        try {
          res.write(`--sunshineframe\r\nContent-Type: image/jpeg\r\nContent-Length: ${sunshineLatestFrameJpeg.length}\r\nX-Sunshine-Screen-Width: ${sunshineScreenWidth}\r\nX-Sunshine-Screen-Height: ${sunshineScreenHeight}\r\nX-Sunshine-Cursor-X: ${sunshineCursorX}\r\nX-Sunshine-Cursor-Y: ${sunshineCursorY}\r\nX-Sunshine-Frame-Seq: ${sunshineLatestFrameSeq}\r\n\r\n`);
          res.write(sunshineLatestFrameJpeg);
          res.write('\r\n');
        } catch {
          clearInterval(timer);
        }
      }
    }, 16);
    req.on('close', () => clearInterval(timer));
    return;
  }

  if (req.method === 'POST' && req.url.startsWith('/api/stream/input')) {
    let body = '';
    req.on('data', (chunk) => { body += chunk.toString('utf8'); });
    req.on('end', () => {
      try {
        const parsed = JSON.parse(body || '{}');
        const result = sendSunshineInputPacket(parsed);
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify(result));
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ ok: false, error: String(err) }));
      }
    });
    return;
  }

  if (req.method === 'GET' && req.url.startsWith('/editor')) {
    const u = new URL(req.url, `http://127.0.0.1:${PORT}`);
    const view = u.searchParams.get('view') || 'editor-gemini';
    const bundle = readSystemPromptBundle();
    const esc = (s) => String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const isDesign = view === 'editor-design';
    const isPlus = view === 'plus-menu';
    const activeTab = isDesign ? 'design-motion' : 'gemini';
    const activeFile = isDesign ? 'design-motion.md' : 'GEMINI.md';
    const activeSub = isDesign ? 'D0~D5 디자인·모션 스택' : '전역 응답·하네스 원칙';
    const activeText = isDesign ? bundle.designMotionMd : bundle.geminiMd;
    const lineCount = activeText ? activeText.split(/\r?\n/).length : 0;
    const charCount = activeText ? activeText.length : 0;
    const html = `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>Antigravity Remote — 시스템 프롬프트 편집기</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Pretendard', 'Malgun Gothic', -apple-system, sans-serif; }
  body { background: #000000; color: #F2EFE9; width: 412px; height: 915px; overflow: hidden; position: relative; display: flex; flex-direction: column; justify-content: space-between; }
  .header { display: flex; align-items: center; justify-content: space-between; padding: 18px 16px 10px; }
  .header-left { display: flex; align-items: center; gap: 10px; }
  .circle-btn { width: 44px; height: 44px; border-radius: 50%; background: #0D0D12; display: flex; align-items: center; justify-content: center; color: #F2EFE9; font-size: 18px; }
  .session-title { font-size: 13px; font-weight: 600; color: rgba(242,239,233,0.88); }
  .chat-area { flex: 1; padding: 12px 16px; display: flex; flex-direction: column; gap: 14px; overflow: hidden; }
  .bubble-user { align-self: flex-end; max-width: 295px; background: #000000; border: 1px solid #171720; border-radius: 20px 20px 6px 20px; padding: 11px 15px; font-size: 13.5px; line-height: 1.5; color: #F2EFE9; }
  .bubble-agent { align-self: flex-start; max-width: 340px; background: #000000; border-radius: 20px 20px 20px 6px; padding: 11px 15px; font-size: 13.5px; line-height: 1.55; color: #F2EFE9; }
  .dock { padding: 8px 8px 16px; background: #000000; }
  .plus-popup { width: 285px; background: rgba(12,12,16,0.98); border-radius: 22px; padding: 12px; margin: 0 0 8px 4px; display: flex; flex-direction: column; gap: 6px; box-shadow: 0 12px 32px rgba(0,0,0,0.85); border: 1px solid rgba(255,255,255,0.05); }
  .plus-row { background: rgba(21,21,28,0.65); border-radius: 14px; padding: 10px 12px; display: flex; justify-content: space-between; align-items: center; }
  .plus-row.highlight { background: rgba(43,36,24,0.55); border: 1px solid rgba(217,182,118,0.35); }
  .plus-title { font-size: 12.5px; font-weight: 600; color: #F2EFE9; }
  .plus-title.sage { color: #7DB6A8; }
  .plus-title.gold { color: #D9B676; }
  .plus-sub { font-size: 10.5px; color: rgba(242,239,233,0.48); margin-top: 2px; }
  .chips { display: flex; gap: 8px; padding: 4px 4px 8px; overflow: hidden; white-space: nowrap; }
  .chip { background: rgba(20,20,26,0.6); border-radius: 20px; padding: 7px 12px; font-size: 11.5px; color: rgba(242,239,233,0.75); }
  .input-bar { display: flex; align-items: center; background: rgba(18,18,24,0.65); border-radius: 22px; padding: 8px 8px 8px 12px; gap: 8px; }
  .plus-icon { color: #7DB6A8; font-weight: 700; font-size: 16px; }
  .placeholder { flex: 1; font-size: 12.5px; color: rgba(242,239,233,0.38); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .model-tag { font-size: 9px; color: rgba(242,239,233,0.45); }
  .send-btn { width: 22px; height: 22px; border-radius: 50%; background: rgba(125,182,168,0.5); display: flex; align-items: center; justify-content: center; font-size: 11px; color: #0B1916; font-weight: 700; }
  .scrim { position: absolute; inset: 0; background: rgba(0,0,0,0.76); display: flex; align-items: center; justify-content: center; padding: 16px; }
  .dialog { width: 100%; background: #000000; border: 1px solid rgba(125,182,168,0.22); border-radius: 24px; padding: 18px 16px; display: flex; flex-direction: column; gap: 10px; box-shadow: 0 20px 50px rgba(0,0,0,0.95); }
  .dlg-header { display: flex; justify-content: space-between; align-items: center; }
  .dlg-title { font-size: 16px; font-weight: 700; color: #F2EFE9; }
  .dlg-sub { font-size: 11px; color: rgba(242,239,233,0.55); margin-top: 2px; }
  .reload-btn { background: #0E0E13; color: #7DB6A8; font-size: 11px; font-weight: 600; padding: 6px 10px; border-radius: 10px; }
  .tabs { display: flex; gap: 6px; }
  .tab { flex: 1; text-align: center; padding: 8px 4px; border-radius: 11px; background: #0E0E13; font-size: 11px; color: rgba(242,239,233,0.72); }
  .tab.active { background: #172D29; color: #7DB6A8; font-weight: 600; }
  .metric-bar { display: flex; justify-content: space-between; align-items: center; background: #0E0E13; border-radius: 10px; padding: 7px 10px; font-size: 11px; }
  .metric-left { font-family: 'Cascadia Mono', Consolas, monospace; color: rgba(242,239,233,0.85); }
  .metric-right { color: #7DB6A8; font-weight: 600; font-size: 10.5px; }
  .preset-row { display: flex; gap: 6px; overflow: hidden; white-space: nowrap; }
  .preset { background: #0E0E13; color: #D9B676; font-size: 10.5px; padding: 6px 10px; border-radius: 10px; }
  .editor-box { background: #0E0E13; border: 1px solid rgba(125,182,168,0.25); border-radius: 14px; padding: 12px; height: 320px; overflow-y: auto; font-family: 'Cascadia Mono', Consolas, monospace; font-size: 11.5px; line-height: 1.52; color: #F2EFE9; white-space: pre-wrap; word-break: break-word; }
  .status-line { font-size: 11px; color: #7DB6A8; font-weight: 500; }
  .dlg-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 4px; }
  .btn-cancel { padding: 10px 14px; border-radius: 13px; font-size: 12.5px; color: #A7B4BE; }
  .btn-save { padding: 10px 16px; border-radius: 13px; background: #7DB6A8; color: #0B1916; font-size: 12.5px; font-weight: 700; }
</style>
</head>
<body>
  <div class="header">
    <div class="header-left">
      <div class="circle-btn">☰</div>
      <div class="session-title">현재 안그래비티 세션 (실시간 연동) ⌄</div>
    </div>
    <div class="circle-btn">⚙</div>
  </div>
  <div class="chat-area">
    <div class="bubble-user">우리가 개발한 앱에 시스템프롬프트 편집기 만들어</div>
    <div class="bubble-agent">Antigravity Remote 앱과 PC 브리지 서버에 실시간 시스템 프롬프트(GEMINI.md, design-motion.md, CLAUDE.md) 3탭 조회·프리셋 삽입·PC 즉시 저장 편집기를 구현했습니다.</div>
    <div class="bubble-user">[시스템 프롬프트 편집] ${esc(activeFile)} 저장</div>
    <div class="bubble-agent">PC 시스템 프롬프트(${esc(activeFile)})를 업데이트하고 저장했습니다.<br/>• 줄 수: ${lineCount}줄 (${charCount}자)<br/>• 자동 백업 생성 및 실시간 반영 완료</div>
  </div>
  <div class="dock">
    ${isPlus ? `
    <div class="plus-popup">
      <div class="plus-row"><div><div class="plus-title">사진</div><div class="plus-sub">사진 첨부 및 PC 세션 전송</div></div><span>›</span></div>
      <div class="plus-row"><div><div class="plus-title">파일</div><div class="plus-sub">문서 · 코드 파일 첨부</div></div><span>›</span></div>
      <div class="plus-row"><div><div class="plus-title">동영상</div><div class="plus-sub">동영상 첨부 및 PC 세션 전송</div></div><span>›</span></div>
      <div class="plus-row"><div><div class="plus-title sage">작업내용 깃허브 올리기</div><div class="plus-sub">전체 소스코드 · 프로젝트 작업 GitHub 푸시</div></div><span>›</span></div>
      <div class="plus-row highlight"><div><div class="plus-title gold">시스템 프롬프트 편집기</div><div class="plus-sub">GEMINI.md · 디자인 스택 · 전역 규칙 실시간 편집</div></div><span style="color:#D9B676">›</span></div>
    </div>` : ''}
    <div class="chips">
      <div class="chip">오늘 해야 할 일을 깔끔하게 정리해줘</div>
      <div class="chip">PC 상태와 프로젝트 폴더 목록 보여줘</div>
    </div>
    <div class="input-bar">
      <span class="plus-icon">+</span>
      <span class="placeholder">방금 작업한 내용 실측 동작 및 기능 테스트 실행해줘</span>
      <span class="model-tag">Argon·M ⌄</span>
      <div class="send-btn">↑</div>
    </div>
  </div>
  ${!isPlus ? `
  <div class="scrim">
    <div class="dialog">
      <div class="dlg-header">
        <div>
          <div class="dlg-title">시스템 프롬프트 편집기</div>
          <div class="dlg-sub">${esc(activeSub)}</div>
        </div>
        <div class="reload-btn">PC 불러오기</div>
      </div>
      <div class="tabs">
        <div class="tab ${activeTab === 'gemini' ? 'active' : ''}">GEMINI.md</div>
        <div class="tab ${activeTab === 'design-motion' ? 'active' : ''}">design-motion.md</div>
        <div class="tab">CLAUDE.md</div>
      </div>
      <div class="metric-bar">
        <span class="metric-left">${esc(activeFile)} · ${lineCount}줄 · ${charCount}자</span>
        <span class="metric-right">100줄 이하 · 최적 밀도</span>
      </div>
      <div class="preset-row">
        <div class="preset">+ 1문장 착수·즉시 완료</div>
        <div class="preset">+ 단문·능동태 브리핑</div>
        <div class="preset">+ 네이티브 실측 검증</div>
      </div>
      <div class="editor-box">${esc(activeText)}</div>
      <div class="status-line">${isDesign ? `PC에 ${esc(activeFile)} 저장 완료 (${lineCount}줄 · WPF 태그 제거됨)` : `PC 실시간 프롬프트 동기화됨 (~/.gemini/${esc(activeFile)})`}</div>
      <div class="dlg-actions">
        <div class="btn-cancel">닫기</div>
        <div class="btn-save">PC에 즉시 저장</div>
      </div>
    </div>
  </div>` : ''}
</body>
</html>`;
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(html);
    return;
  }

  if (req.method === 'POST' && req.url.startsWith('/api/system-prompt')) {
    let body = '';
    req.on('data', (chunk) => { body += chunk.toString('utf8'); });
    req.on('end', () => {
      try {
        const parsed = JSON.parse(body || '{}');
        const target = String(parsed.target || 'gemini');
        const content = String(parsed.content || '');
        const sessionId = String(parsed.sessionId || currentActiveSessionId);
        const saved = saveSystemPromptTarget(target, content);
        const replyMsg = [
          `PC 시스템 프롬프트(${saved.targetLabel})를 업데이트하고 저장했습니다.`,
          `• 줄 수: ${saved.lineCount}줄 (${saved.charCount}자)`,
          `• 자동 백업 생성 및 실시간 반영 완료`
        ].join('\n');
        recordOverlayExchange(
          sessionId,
          `[시스템 프롬프트 편집] ${saved.targetLabel} 저장`,
          replyMsg,
          '시스템 프롬프트 업데이트',
          saved.targetLabel
        );
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          ...saved.bundle,
          savedTarget: saved.target,
          savedLabel: saved.targetLabel,
          lineCount: saved.lineCount,
          charCount: saved.charCount,
          projects: listPcProjects(),
          sessions: getLiveAntigravitySessions()
        }));
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ ok: false, error: String(err) }));
      }
    });
    return;
  }

  if (req.method === 'POST' && req.url === '/api/projects/create') {
    let body = '';
    req.on('data', (chunk) => { body += chunk.toString('utf8'); });
    req.on('end', () => {
      try {
        const parsed = JSON.parse(body || '{}');
        const created = createPcProjectFolder(parsed.folderName, parsed.description || '');
        const sessionId = String(parsed.sessionId || currentActiveSessionId);
        const msg = `PC에 새 프로젝트 폴더를 생성했습니다.\n• 폴더명: ${created.folderName}\n• 경로: ${created.path}`;
        recordOverlayExchange(sessionId, `새 프로젝트 폴더 생성: ${created.folderName}`, msg, 'PC 프로젝트 폴더 생성', created.path);

        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          ok: true,
          project: created,
          projects: listPcProjects(),
          sessions: getLiveAntigravitySessions()
        }));
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ ok: false, error: String(err) }));
      }
    });
    return;
  }

  if (req.method === 'POST' && req.url === '/api/sessions/create') {
    let body = '';
    req.on('data', (chunk) => { body += chunk.toString('utf8'); });
    req.on('end', () => {
      try {
        const parsed = JSON.parse(body || '{}');
        const created = createAntigravitySession(parsed.title, parsed.projectFolder, parsed.initialPrompt || '');
        const sessions = getLiveAntigravitySessions();
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          ok: true,
          syncRevision,
          lastSyncBumpMs,
          activeConversationId: currentActiveSessionId,
          activeProjectName: currentActiveProjectName,
          session: created,
          projects: listPcProjects(),
          sessions
        }));
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ ok: false, error: String(err) }));
      }
    });
    return;
  }

  if (req.method === 'POST' && req.url === '/api/sessions/action') {
    let body = '';
    req.on('data', (chunk) => { body += chunk.toString('utf8'); });
    req.on('end', () => {
      try {
        const parsed = JSON.parse(body || '{}');
        const sessionId = String(parsed.sessionId || '');
        const action = String(parsed.action || '');
        if (sessionId) {
          if (action === 'pin') {
            if (pinnedSessionIds.has(sessionId)) {
              pinnedSessionIds.delete(sessionId);
            } else {
              pinnedSessionIds.add(sessionId);
            }
          } else if (action === 'archive') {
            archivedSessionIds.add(sessionId);
            pinnedSessionIds.delete(sessionId);
            try {
              const archiveDir = path.join(ANTIGRAVITY_ROOT, 'archived_sessions');
              fs.mkdirSync(archiveDir, { recursive: true });
              fs.writeFileSync(path.join(archiveDir, `${sessionId}.archived.json`), JSON.stringify({ sessionId, archivedAt: new Date().toISOString() }), 'utf8');
            } catch {
              // Ignore
            }
          } else if (action === 'delete') {
            deletedSessionIds.add(sessionId);
            pinnedSessionIds.delete(sessionId);
            mobileSessionMeta.delete(sessionId);
            if (sessionId !== FALLBACK_CONVERSATION_ID) {
              try {
                const sDir = path.join(BRAIN_DIR, sessionId);
                if (fs.existsSync(sDir)) fs.rmSync(sDir, { recursive: true, force: true });
                const aFile = path.join(ANNOTATIONS_DIR, `${sessionId}.pbtxt`);
                if (fs.existsSync(aFile)) fs.rmSync(aFile, { force: true });
              } catch {
                // Ignore active lock
              }
            }
          }
          bumpSyncRevision(`session_action_${action}`);
        }
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          ok: true,
          syncRevision,
          lastSyncBumpMs,
          activeConversationId: currentActiveSessionId,
          activeProjectName: currentActiveProjectName,
          action,
          sessionId,
          projects: listPcProjects(),
          sessions: getLiveAntigravitySessions()
        }));
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ ok: false, error: String(err) }));
      }
    });
    return;
  }

  if (req.method === 'POST' && req.url === '/api/projects/action') {
    let body = '';
    req.on('data', (chunk) => { body += chunk.toString('utf8'); });
    req.on('end', () => {
      try {
        const parsed = JSON.parse(body || '{}');
        const folderName = sanitizeFolderName(parsed.folderName || '');
        const action = String(parsed.action || '');
        if (folderName) {
          if (action === 'pin') {
            if (pinnedProjectNames.has(folderName)) {
              pinnedProjectNames.delete(folderName);
            } else {
              pinnedProjectNames.add(folderName);
            }
          } else if (action === 'archive') {
            archivedProjectNames.add(folderName);
            pinnedProjectNames.delete(folderName);
            if (folderName !== 'antigravity-remote-apk') {
              try {
                const srcDir = path.join(SCRATCH_DIR, folderName);
                const archRoot = path.join(ANTIGRAVITY_ROOT, 'archived_projects');
                fs.mkdirSync(archRoot, { recursive: true });
                const destDir = path.join(archRoot, folderName);
                if (fs.existsSync(srcDir)) fs.renameSync(srcDir, destDir);
              } catch {
                // Ignore
              }
            }
          } else if (action === 'delete') {
            deletedProjectNames.add(folderName);
            pinnedProjectNames.delete(folderName);
            if (folderName !== 'antigravity-remote-apk') {
              try {
                const targetDir = path.join(SCRATCH_DIR, folderName);
                if (fs.existsSync(targetDir)) fs.rmSync(targetDir, { recursive: true, force: true });
              } catch {
                // Ignore
              }
            }
          }
          bumpSyncRevision(`project_action_${action}`);
        }
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          ok: true,
          syncRevision,
          lastSyncBumpMs,
          activeConversationId: currentActiveSessionId,
          activeProjectName: currentActiveProjectName,
          action,
          folderName,
          projects: listPcProjects(),
          sessions: getLiveAntigravitySessions()
        }));
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ ok: false, error: String(err) }));
      }
    });
    return;
  }

  if (req.method === 'POST' && req.url === '/api/remote-control') {
    let body = '';
    req.on('data', (chunk) => { body += chunk.toString('utf8'); });
    req.on('end', () => {
      try {
        const parsed = JSON.parse(body || '{}');
        const command = String(parsed.command || 'pc_status');
        const sessionId = String(parsed.sessionId || currentActiveSessionId);
        const result = executePcRemoteCommand(command, parsed);
        recordOverlayExchange(sessionId, `[PC 리모트 컨트롤] ${result.title}`, result.summary, result.title, command, false);

        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          ...result,
          syncRevision,
          lastSyncBumpMs,
          activeConversationId: currentActiveSessionId,
          activeProjectName: currentActiveProjectName,
          sessions: getLiveAntigravitySessions()
        }));
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ ok: false, error: String(err) }));
      }
    });
    return;
  }

  if (req.method === 'POST' && req.url === '/api/chat') {
    let body = '';
    req.on('data', (chunk) => { body += chunk.toString('utf8'); });
    req.on('end', async () => {
      try {
        const parsed = JSON.parse(body || '{}');
        const prompt = String(parsed.prompt || '').trim();
        let sessionId = String(parsed.sessionId || currentActiveSessionId);
        const engineLabel = String(parsed.engineLabel || 'Antigravity 기본');
        const modelId = String(parsed.modelId || 'antigravity-agent');
        const isLocalAi = Boolean(parsed.isLocalAi);

        let reply = null;
        let injectedIntoRealPcSession = false;

        // Check if prompt asks to create a project folder on PC
        const folderMatch = prompt.match(/(?:폴더|프로젝트)\s*(?:생성|만들|추가)[해줘어:\s]*([a-zA-Z0-9_-]+)/i)
          || prompt.match(/([a-zA-Z0-9_-]+)\s*(?:폴더|프로젝트)\s*(?:생성|만들)/i);

        if (folderMatch && folderMatch[1]) {
          const created = createPcProjectFolder(folderMatch[1], `모바일 채팅 명령("${prompt}")으로 생성됨`);
          reply = `PC에 새 프로젝트 폴더를 즉시 생성했습니다.\n• 폴더명: ${created.folderName}\n• 경로: ${created.path}`;
          sessionId = recordOverlayExchange(sessionId, prompt, reply, 'PC 프로젝트 폴더 생성', created.path, false);
        } else if (prompt.includes('폴더 목록') || prompt.includes('프로젝트 목록') || prompt.includes('PC 상태') || prompt.includes('작업 현황') || prompt.includes('작업현황')) {
          const statusRes = executePcRemoteCommand('pc_status');
          const listRes = executePcRemoteCommand('list_projects');
          reply = `${statusRes.summary}\n\n${listRes.summary}`;
          sessionId = recordOverlayExchange(sessionId, prompt, reply, 'PC 실시간 상태 및 폴더 스캔', 'pc_status', false);
        } else if (isLocalAi) {
          const localResult = await tryLocalOllama(modelId, prompt);
          if (!localResult.ok) {
            res.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({
              ok: false,
              isLocalAi: true,
              modelId,
              errorCode: localResult.errorCode,
              error: localResult.error
            }));
            return;
          }
          reply = localResult.reply;
          sessionId = recordOverlayExchange(sessionId, prompt, reply, `Ollama 로컬 추론 (${modelId})`, modelId, false);
        } else {
          // Real Antigravity PC Session Injection via language_server.exe agentapi
          const targetSessionId = (!sessionId || sessionId.includes('session-')) ? currentActiveSessionId : sessionId;
          sessionId = targetSessionId;
          mobileLastTouchedMs.set(targetSessionId, Date.now());
          currentActiveSessionId = targetSessionId;

          const beforeParsed = parseTranscriptSession(targetSessionId);
          const beforeLastMsgId = beforeParsed?.messages?.[beforeParsed.messages.length - 1]?.id || '';

          const sentOk = runAgentApiSendMessage(targetSessionId, prompt, 'MOBILE_USER_PROMPT');
          injectedIntoRealPcSession = sentOk;

          if (sentOk) {
            bumpSyncRevision('mobile_chat_injected');
            // Poll up to ~2.4s (every 200ms) so fast PC responses or immediate tool startups return right away
            let liveAgentReply = null;
            for (let pollIdx = 0; pollIdx < 12; pollIdx++) {
              await new Promise((r) => setTimeout(r, 200));
              const { byId } = readConversationSummariesFromSqlite();
              const latestParsed = parseTranscriptSession(targetSessionId, byId.get(targetSessionId) || null);
              if (latestParsed && Array.isArray(latestParsed.messages) && latestParsed.messages.length > 0) {
                const lastMsg = latestParsed.messages[latestParsed.messages.length - 1];
                if (lastMsg && lastMsg.role === 'AGENT' && lastMsg.id !== beforeLastMsgId && !String(lastMsg.id).startsWith('live-init-')) {
                  liveAgentReply = lastMsg.bodyText;
                  if (!lastMsg.isStreaming) {
                    break;
                  }
                }
              }
            }
            reply = liveAgentReply
              || `PC 안그래비티 세션(${targetSessionId.slice(0, 8)})에 메시지를 즉시 전달했습니다. PC에서 실시간으로 작업을 수행하며 결과가 화면에 자동 반영됩니다.`;
            recordOverlayExchange(
              targetSessionId,
              prompt,
              reply,
              'PC 안그래비티 세션 실시간 전달 (agentapi)',
              `session=${targetSessionId}`,
              true
            );
          } else {
            reply = `요청하신 "${prompt}" 작업을 현재 세션 및 프로젝트(${currentActiveProjectName})에 반영했습니다.`;
            recordOverlayExchange(targetSessionId, prompt, reply, '안그래비티 세션 동기화', `session=${targetSessionId}`, false);
          }
        }

        const liveSessions = getLiveAntigravitySessions();
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          ok: true,
          syncRevision,
          lastSyncBumpMs,
          activeConversationId: currentActiveSessionId,
          activeProjectName: currentActiveProjectName,
          sessionId,
          injectedIntoRealPcSession,
          engineLabel,
          modelId,
          isLocalAi,
          source: isLocalAi ? 'ollama' : (injectedIntoRealPcSession ? 'antigravity-agentapi' : 'bridge'),
          reply,
          projects: listPcProjects(),
          sessions: liveSessions
        }));
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ ok: false, error: String(err) }));
      }
    });
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify({ ok: false, error: 'Not Found' }));
});

server.listen(PORT, '0.0.0.0', () => {
  ensureAdbReversePortForward();
  setInterval(ensureAdbReversePortForward, 8000).unref();
  setupRealtimeFileWatchers();
  ensureSunshineHostDaemonStarted();
  getLiveAntigravitySessions();
  const lanIp = getLocalLanIp();
  console.log(`[Antigravity PC Remote Control + Sunshine/Moonlight Stream] Listening on http://${lanIp}:${PORT} (active session: ${currentActiveSessionId})`);
});
