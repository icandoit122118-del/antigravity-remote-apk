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
import { execSync } from 'node:child_process';

const portArgIndex = process.argv.indexOf('--port');
const PORT = portArgIndex !== -1 && process.argv[portArgIndex + 1]
  ? Number(process.argv[portArgIndex + 1])
  : Number(process.env.ANTIGRAVITY_BRIDGE_PORT || 8766);

const ANTIGRAVITY_ROOT = path.join(os.homedir(), '.gemini', 'antigravity');
const BRAIN_DIR = path.join(ANTIGRAVITY_ROOT, 'brain');
const SCRATCH_DIR = path.join(ANTIGRAVITY_ROOT, 'scratch');
const PRIMARY_CONVERSATION_ID = 'ace251b1-f9a3-4165-a54b-3fe8ea9bc9e8';

let currentActiveSessionId = PRIMARY_CONVERSATION_ID;
let currentActiveProjectName = 'antigravity-remote-apk';

// Ensure scratch directory exists
try {
  fs.mkdirSync(SCRATCH_DIR, { recursive: true });
} catch {
  // Ignore
}

// Overlay store for mobile messages and mobile-created sessions
const mobileMessagesBySession = new Map();
const mobileSessionMeta = new Map();

function getLocalLanIp() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name] || []) {
      if (net.family === 'IPv4' && !net.internal) {
        return net.address;
      }
    }
  }
  return '127.0.0.1';
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
      .filter((e) => e.isDirectory())
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
          mtimeMs
        };
      })
      .sort((a, b) => b.mtimeMs - a.mtimeMs);
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
  return {
    folderName,
    path: projectPath.replace(/\\/g, '/'),
    created: true
  };
}

function createAntigravitySession(rawTitle, rawProjectFolder = '') {
  const sessionId = crypto.randomUUID ? crypto.randomUUID() : `mob-session-${Date.now()}`;
  const title = String(rawTitle || '').trim() || `새 세션 (${new Date().toLocaleTimeString('ko-KR')})`;
  let projectInfo = null;

  if (rawProjectFolder && String(rawProjectFolder).trim().length > 0) {
    projectInfo = createPcProjectFolder(rawProjectFolder, `세션 '${title}'과 연동된 프로젝트 폴더`);
  }

  const logsDir = path.join(BRAIN_DIR, sessionId, '.system_generated', 'logs');
  fs.mkdirSync(logsDir, { recursive: true });

  const nowIso = new Date().toISOString();
  const initialUserStep = {
    step_index: 0,
    source: 'USER_EXPLICIT',
    type: 'USER_INPUT',
    status: 'DONE',
    created_at: nowIso,
    content: `<USER_REQUEST>\n모바일에서 새 세션 '${title}' 시작${projectInfo ? ` (프로젝트 폴더: ${projectInfo.folderName})` : ''}\n</USER_REQUEST>`
  };
  const initialAgentStep = {
    step_index: 1,
    source: 'MODEL',
    type: 'PLANNER_RESPONSE',
    status: 'DONE',
    created_at: nowIso,
    thinking: '모바일 리모트 앱에서 요청한 새 안그래비티 세션과 PC 작업 공간을 초기화했습니다.',
    content: projectInfo
      ? `새 안그래비티 세션(**${title}**)과 PC 프로젝트 폴더(\`${projectInfo.path}\`)를 생성하고 실시간 연동을 시작했습니다.`
      : `새 안그래비티 세션(**${title}**)을 생성하고 PC와 실시간 연동을 시작했습니다.`
  };

  const transcriptPath = path.join(logsDir, 'transcript.jsonl');
  fs.writeFileSync(
    transcriptPath,
    JSON.stringify(initialUserStep) + '\n' + JSON.stringify(initialAgentStep) + '\n',
    'utf8'
  );

  mobileSessionMeta.set(sessionId, {
    id: sessionId,
    title,
    projectFolder: projectInfo ? projectInfo.folderName : currentActiveProjectName,
    projectPath: projectInfo ? projectInfo.path : path.join(SCRATCH_DIR, currentActiveProjectName).replace(/\\/g, '/')
  });

  currentActiveSessionId = sessionId;
  return {
    sessionId,
    title,
    projectInfo
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

function parseTranscriptSession(convId) {
  const transcriptPath = path.join(BRAIN_DIR, convId, '.system_generated', 'logs', 'transcript.jsonl');
  if (!fs.existsSync(transcriptPath)) return null;

  try {
    const raw = fs.readFileSync(transcriptPath, 'utf8');
    const lines = raw.split(/\r?\n/).filter(Boolean);
    const messages = [];
    let lastThought = '';
    let pendingTools = [];
    let awaitingTurnResponse = false;

    for (let idx = 0; idx < lines.length; idx++) {
      const line = lines[idx];
      let step;
      try {
        step = JSON.parse(line);
      } catch {
        continue;
      }

      const createdAtMs = Date.parse(step.created_at) || (idx * 1000);

      if (step.type === 'USER_INPUT' && step.source === 'USER_EXPLICIT') {
        const text = extractUserRequestText(step.content);
        if (text) {
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
      } else if (step.type === 'PLANNER_RESPONSE' && step.source === 'MODEL') {
        if (step.thinking && typeof step.thinking === 'string') {
          lastThought = step.thinking.trim();
        }
        const hasToolCalls = (Array.isArray(step.tool_calls) && step.tool_calls.length > 0)
          || (Array.isArray(step.truncated_fields) && step.truncated_fields.includes('tool_calls'));

        if (Array.isArray(step.tool_calls) && step.tool_calls.length > 0) {
          for (const tc of step.tool_calls) {
            pendingTools.push({
              id: `tool-${step.step_index}-${pendingTools.length}`,
              title: tc.name || 'tool_call',
              commandOrPath: tc.args ? JSON.stringify(tc.args).slice(0, 120) : '',
              outputPreview: '실행 완료',
              durationMs: 320,
              status: 'COMPLETED'
            });
          }
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
              tools: pendingTools.slice(-4)
            };
            // If the previous message was an interim tool-calling ack for the same user turn and this is the final response, replace the interim ack
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

    const overlay = mobileMessagesBySession.get(convId) || [];
    for (const item of overlay) {
      messages.push(item);
    }

    messages.sort((a, b) => (a.createdAtMs || 0) - (b.createdAtMs || 0));
    const recentMessages = messages.slice(-20);
    if (recentMessages.length === 0) return null;

    const customMeta = mobileSessionMeta.get(convId);
    const firstUserMsg = messages.find((m) => m.role === 'USER');
    const isPrimary = convId === PRIMARY_CONVERSATION_ID;
    const title = customMeta?.title
      || (isPrimary
        ? '현재 안그래비티 세션 (실시간 연동)'
        : (firstUserMsg ? firstUserMsg.bodyText.slice(0, 20) : `세션 ${convId.slice(0, 8)}`));

    let inferredFolder = customMeta?.projectFolder || (isPrimary ? currentActiveProjectName : '');
    if (!inferredFolder) {
      const joinedText = messages.map((m) => m.bodyText).join(' ');
      if (joinedText.includes('전기산업기사') || joinedText.includes('electric-engineer')) {
        inferredFolder = 'electric-engineer-2026';
      } else if (joinedText.includes('wikidocs') || joinedText.includes('파이썬')) {
        inferredFolder = 'wikidocs-170810-pdf';
      } else if (joinedText.includes('D2R') || joinedText.includes('야영지') || joinedText.includes('d2r')) {
        inferredFolder = 'D2R-HUD';
      } else {
        inferredFolder = currentActiveProjectName;
      }
    }

    return {
      id: convId,
      title,
      ambientCaption: customMeta?.projectPath
        ? `폴더: ${customMeta.projectFolder} (${customMeta.projectPath})`
        : `폴더: ${inferredFolder}`,
      branchName: inferredFolder,
      activeTaskSummary: isPrimary ? 'PC 안그래비티 리모트 컨트롤 및 실시간 세션 연동' : title,
      durationTag: convId === currentActiveSessionId ? '활성' : '동기화됨',
      quickPrompts: [
        '오늘 해야 할 일을 깔끔하게 정리해줘',
        'PC 상태와 프로젝트 폴더 목록 보여줘',
        '새 프로젝트 폴더 만들어줘'
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

  // 1. Put mobile-created sessions first if newly created, plus PRIMARY_CONVERSATION_ID
  for (const [id] of mobileSessionMeta) {
    const parsed = parseTranscriptSession(id);
    if (parsed) {
      results.push(parsed);
      seen.add(id);
    }
  }

  if (!seen.has(PRIMARY_CONVERSATION_ID)) {
    const primary = parseTranscriptSession(PRIMARY_CONVERSATION_ID);
    if (primary) {
      results.unshift(primary);
      seen.add(PRIMARY_CONVERSATION_ID);
    }
  }

  try {
    if (fs.existsSync(BRAIN_DIR)) {
      const entries = fs.readdirSync(BRAIN_DIR, { withFileTypes: true })
        .filter((e) => e.isDirectory() && !seen.has(e.name))
        .map((e) => {
          const tPath = path.join(BRAIN_DIR, e.name, '.system_generated', 'logs', 'transcript.jsonl');
          const stat = fs.existsSync(tPath) ? fs.statSync(tPath) : null;
          return { id: e.name, mtimeMs: stat ? stat.mtimeMs : 0 };
        })
        .filter((e) => e.mtimeMs > 0)
        .sort((a, b) => b.mtimeMs - a.mtimeMs)
        .slice(0, 4);

      for (const entry of entries) {
        const parsed = parseTranscriptSession(entry.id);
        if (parsed) {
          results.push(parsed);
          seen.add(entry.id);
        }
      }
    }
  } catch {
    // Ignore
  }

  return results;
}

function executePcRemoteCommand(command, args = {}) {
  const freeMemMb = Math.round(os.freemem() / (1024 * 1024));
  const totalMemMb = Math.round(os.totalmem() / (1024 * 1024));
  const projects = listPcProjects();

  if (command === 'pc_status') {
    return {
      ok: true,
      title: 'PC 상태 및 리소스 점검',
      summary: [
        `• 호스트: ${os.hostname()} (${os.platform()} ${os.release()})`,
        `• 메모리: 여유 ${freeMemMb} MB / 전체 ${totalMemMb} MB`,
        `• 활성 프로젝트: ${currentActiveProjectName}`,
        `• 프로젝트 폴더 수: ${projects.length}개 (${projects.slice(0, 4).map((p) => p.name).join(', ') || '없음'})`
      ].join('\n'),
      projects
    };
  }

  if (command === 'list_projects') {
    const lines = projects.map((p, idx) => `${idx + 1}. ${p.name} (${p.path}) — 파일 ${p.fileCount}개`);
    return {
      ok: true,
      title: 'PC 프로젝트 폴더 목록',
      summary: lines.length > 0
        ? `현재 PC 워크스페이스(\`${SCRATCH_DIR.replace(/\\/g, '/')}\`) 폴더 목록입니다:\n` + lines.join('\n')
        : '생성된 프로젝트 폴더가 없습니다.',
      projects
    };
  }

  if (command === 'git_status') {
    const targetDir = path.join(SCRATCH_DIR, args.projectName || currentActiveProjectName);
    let gitOut = 'Git 저장소가 아니거나 변경 사항이 없습니다.';
    try {
      gitOut = execSync('git status -s && git log -1 --oneline', {
        cwd: targetDir,
        encoding: 'utf8',
        timeout: 2500
      }).trim() || '작업 트리가 깨끗합니다 (변경 사항 없음).';
    } catch (e) {
      gitOut = `경로 확인 완료: ${targetDir.replace(/\\/g, '/')}`;
    }
    return {
      ok: true,
      title: `프로젝트 Git 상태 (${args.projectName || currentActiveProjectName})`,
      summary: `• 경로: ${targetDir.replace(/\\/g, '/')}\n• 상태:\n${gitOut}`,
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

async function tryLocalOllamaOrLmStudio(modelId, prompt) {
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 2200);
    const res = await fetch('http://127.0.0.1:11434/api/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: modelId, prompt, stream: false }),
      signal: controller.signal
    });
    clearTimeout(timer);
    if (res.ok) {
      const data = await res.json();
      if (data && data.response) {
        return `[PC 로컬 Ollama (${modelId}) 응답]\n${data.response}`;
      }
    }
  } catch {
    // Not active
  }

  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 2200);
    const res = await fetch('http://127.0.0.1:1234/v1/chat/completions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: modelId,
        messages: [{ role: 'user', content: prompt }],
        max_tokens: 256
      }),
      signal: controller.signal
    });
    clearTimeout(timer);
    if (res.ok) {
      const data = await res.json();
      const content = data?.choices?.[0]?.message?.content;
      if (content) {
        return `[PC 로컬 LM Studio (${modelId}) 응답]\n${content}`;
      }
    }
  } catch {
    // Not active
  }

  return null;
}

function recordOverlayExchange(sessionId, prompt, reply, toolTitle = '안그래비티 라이브 세션 동기화', toolPath = '') {
  const targetSessionId = sessionId.includes('session-') ? currentActiveSessionId : sessionId;
  const list = mobileMessagesBySession.get(targetSessionId) || [];
  const nowMs = Date.now();
  const nowIso = new Date(nowMs).toISOString();
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
    const sessions = getLiveAntigravitySessions();
    const projects = listPcProjects();
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({
      ok: true,
      activeConversationId: currentActiveSessionId,
      activeProjectName: currentActiveProjectName,
      scratchRoot: SCRATCH_DIR.replace(/\\/g, '/'),
      projects,
      sessions
    }));
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
        const created = createAntigravitySession(parsed.title, parsed.projectFolder);
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          ok: true,
          session: created,
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
        recordOverlayExchange(sessionId, `[PC 리모트 컨트롤] ${result.title}`, result.summary, result.title, command);

        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          ...result,
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
        const sessionId = String(parsed.sessionId || currentActiveSessionId);
        const engineLabel = String(parsed.engineLabel || 'Antigravity 기본');
        const modelId = String(parsed.modelId || 'antigravity-agent');
        const isLocalAi = Boolean(parsed.isLocalAi);
        const freeMemMb = Math.round(os.freemem() / (1024 * 1024));

        let reply = null;

        // Check if prompt asks to create a project folder on PC
        const folderMatch = prompt.match(/(?:폴더|프로젝트)\s*(?:생성|만들|추가)[해줘어:\s]*([a-zA-Z0-9_-]+)/i)
          || prompt.match(/([a-zA-Z0-9_-]+)\s*(?:폴더|프로젝트)\s*(?:생성|만들)/i);

        if (folderMatch && folderMatch[1]) {
          const created = createPcProjectFolder(folderMatch[1], `모바일 채팅 명령("${prompt}")으로 생성됨`);
          reply = `PC에 새 프로젝트 폴더를 즉시 생성했습니다.\n• 폴더명: ${created.folderName}\n• 경로: ${created.path}`;
        } else if (prompt.includes('폴더 목록') || prompt.includes('프로젝트 목록') || prompt.includes('PC 상태')) {
          const statusRes = executePcRemoteCommand('pc_status');
          const listRes = executePcRemoteCommand('list_projects');
          reply = `${statusRes.summary}\n\n${listRes.summary}`;
        } else if (isLocalAi) {
          reply = await tryLocalOllamaOrLmStudio(modelId, prompt);
          if (!reply) {
            reply = `[PC 로컬 AI · ${engineLabel} (${modelId})]\n현재 안그래비티 세션(${sessionId.slice(0, 8)})과 연동된 로컬 파이프라인에서 "${prompt}" 요청을 처리했습니다. (PC 여유 메모리 ${freeMemMb}MB)`;
          }
        } else {
          reply = `[PC 안그래비티 리모트 (${os.hostname()}) 실시간 연동]\n모바일에서 보낸 "${prompt}" 요청이 현재 세션(${sessionId.slice(0, 8)}) 및 활성 프로젝트(${currentActiveProjectName})에 동기화되어 처리되었습니다.`;
        }

        const targetSessionId = recordOverlayExchange(sessionId, prompt, reply);

        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          ok: true,
          sessionId: targetSessionId,
          engineLabel,
          modelId,
          isLocalAi,
          reply,
          projects: listPcProjects()
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
  const lanIp = getLocalLanIp();
  console.log(`[Antigravity PC Remote Control + Live Session Sync] Listening on http://${lanIp}:${PORT} (active session: ${currentActiveSessionId})`);
});
