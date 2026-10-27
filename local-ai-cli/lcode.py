#!/usr/bin/env python3
"""
lcode v2.0 — Antigravity-Grade Local AI Vibe-Coding & RAG Agent Harness
Mirrors the full Google Antigravity customization & harness architecture for local Ollama models:
  1. Global & Hierarchical Rule Loader:
     - Loads user_global rules (GEMINI.md, design-motion.md) + workspace GEMINI.md / AGENTS.md / DESIGN.md / .agents/rules/*.md
  2. Progressive Skill Disclosure Engine:
     - Scans all 38+ skills in ~/.gemini/config/skills & ~/.gemini/antigravity/builtin/skills
     - Injects compact YAML frontmatter (name + description) into <skills>, loads full SKILL.md on demand via `activate_skill`
  3. Design-Motion Stack D0~D5 Orchestrator:
     - Native support for D0~D5 conflict priority order (hallmark + hallmark-apps -> ui-ux-pro-max / frontend-design / taste -> emil / animate -> impeccable -> remotion / hyperframes)
  4. Local Vector RAG Engine (AnythingLLM-style):
     - nomic-embed-text + SQLite cosine similarity search across indexed codebases & cloned GitHub repos
  5. Autonomous ReAct Coding & Installation Harness (OpenCode/Aider-style):
     - Tools: list_files, read_file, write_file, patch_file, grep_search, run_shell, activate_skill, git_clone_and_index, rag_index, rag_search
     - Loop-Breaker deduplication + Final Synthesis guardrail
"""
import argparse
import hashlib
import json
import math
import os
import re
import sqlite3
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

HOME = Path(os.path.expanduser("~"))
GEMINI_DIR = HOME / ".gemini"
CONFIG_SKILLS_DIR = GEMINI_DIR / "config" / "skills"
BUILTIN_SKILLS_DIR = GEMINI_DIR / "antigravity" / "builtin" / "skills"
BASE_DIR = GEMINI_DIR / "antigravity" / "scratch" / "local-ai-vibe"
DB_PATH = BASE_DIR / "lcode_rag.sqlite3"

OLLAMA_API = os.environ.get("OLLAMA_HOST_URL", "http://127.0.0.1:11434")
OLLAMA_BIN = os.path.expandvars(r"%LOCALAPPDATA%\Programs\Ollama\ollama.exe")
EMBED_MODEL = "nomic-embed-text"

MODELS = {
    "coder": {
        "id": "qwen2.5-coder:7b",
        "desc": "코딩·리포지토리 해석·설치 주력 (4.7GB VRAM / ~52 tok/s)",
        "num_ctx": 8192,
        "num_gpu": -1,
    },
    "game": {
        "id": "qwen3:4b",
        "desc": "고사양 게임 동시 실행용 초경량 (2.5GB VRAM / ~30 tok/s)",
        "num_ctx": 8192,
        "num_gpu": -1,
    },
    "ram-safe": {
        "id": "qwen2.5-coder-7b-local",
        "desc": "VRAM 절약 하이브리드 (GPU 12레이어 + 32GB 시스템 RAM 분산)",
        "num_ctx": 16384,
        "num_gpu": 12,
    },
    "deep": {
        "id": "gemma4-26b-a4b-local:latest",
        "desc": "비게임 시 심층 아키텍처 추론 (26B MoE A4B / 17GB)",
        "num_ctx": 16384,
        "num_gpu": -1,
    },
}

DEFAULT_USER_GLOBAL_RULES = """<RULE[user_global]>
## 맞춤형 응답·말투·작업 정리 원칙
1. **UI와 역할 분담 (중복 중계 금지)**:
   - 기계적인 복창이나 사전 계획 번호표를 쓰지 마세요.
   - 작업 시작 시에는 1문장으로 핵심 의도만 담백하게 짚고 즉시 도구를 실행해 작업을 끝까지 완료하세요.
2. **답변 말투 및 결과 정리 포맷**:
   - 군더더기 인사말·마무리 치레를 빼고, 동료 엔지니어에게 브리핑하듯 현재 시제·능동태·단문(`~합니다`/`~했습니다`)으로 간결하게 작성하세요.
   - **단순 질문·단순 수정**: 결론 또는 조치 결과와 클릭 가능한 파일 링크(`[파일명](file:///...)`)만 1~3줄로 바로 제시합니다.
   - **복합 수정·구현 작업**:
     - **한 줄 결론**: 무엇을 어떻게 바꿨는지 핵심 결과 1줄
     - **주요 변경**: `[파일명](file:///...)` — 변경 요지 불릿 (중첩 최소화)
     - **검증**: 실행한 테스트·UI/동작 확인 결과 1줄 (미검증 항목은 구분해 명시)

## Hallmark for Windows and mobile interfaces
When building Windows or mobile app UI, read `C:/Users/icand/.gemini/config/skills/hallmark-apps/SKILL.md` before applying Hallmark, then read `C:/Users/icand/.gemini/config/skills/hallmark/SKILL.md` and its App screens guidance. Preserve existing design systems unless redesign is requested; backend-only work does not require Hallmark.

## 디자인·모션 스킬 전부 사용: 기획·구상 직후 단계 (D0~D5)
Windows 앱, 모바일 앱, 화면이 있는 소프트웨어를 새로 만들거나 화면을 크게 바꿀 때는 기획·구상 직후 구현 계획 전에 `design-motion-stack` (`C:/Users/icand/.gemini/config/skills/design-motion-stack/SKILL.md`)을 읽고 D0~D5 역할·충돌 우선순위(1. 사용자/잠긴 DESIGN.md > 2. 플랫폼/hallmark-apps > 3. Hallmark Floor > 4. D2 > 5. D3·D4)·결과표대로 진행한다.
- 시각 방향은 D1 한 곳(기존 디자인 시스템, 없으면 `hallmark` + `hallmark-apps`)에서만 정하고 나머지 스킬(`frontend-design`, `ui-ux-pro-max`, `design-taste-frontend`, `impeccable`, Emil Kowalski 스킬, 영상 산출물 시 `remotion-best-practices` / `hyperframes`)은 점검·보강·다듬기에 쓴다.
- 화면·영상이 없는 작업(백엔드, CLI, 학습·분석)은 '해당 없음: 화면 없음'으로 기록한다.
</RULE[user_global]>"""


# ============================================================================
# [1] Antigravity Progressive Skill Loader & Hierarchical Rule Discovery
# ============================================================================
class AntigravityCustomizationLoader:
    def __init__(self) -> None:
        self.skills: dict[str, dict] = {}
        self._discover_skills()

    def _parse_frontmatter(self, text: str) -> tuple[str, str]:
        m = re.match(r"^---\s*\n(.*?)\n---\s*\n", text, re.DOTALL)
        if not m:
            return "", ""
        fm = m.group(1)
        name_m = re.search(r"^name:\s*(.+)$", fm, re.MULTILINE)
        desc_m = re.search(r"^description:\s*(.+)$", fm, re.MULTILINE)
        name = name_m.group(1).strip().strip("'\"") if name_m else ""
        desc = desc_m.group(1).strip().strip("'\"") if desc_m else ""
        return name, desc

    def _discover_skills(self) -> None:
        roots = [BUILTIN_SKILLS_DIR, CONFIG_SKILLS_DIR]
        for root in roots:
            if not root.exists():
                continue
            for skill_md in sorted(root.glob("*/SKILL.md")):
                try:
                    raw = skill_md.read_text(encoding="utf-8", errors="ignore")
                    name, desc = self._parse_frontmatter(raw)
                    sname = name or skill_md.parent.name
                    self.skills[sname] = {
                        "name": sname,
                        "path": str(skill_md),
                        "description": desc or f"Skill at {skill_md}",
                    }
                except Exception:
                    continue

    def load_hierarchical_rules(self, cwd: Path) -> str:
        rules_blocks: list[str] = []
        seen_paths: set[str] = set()

        global_gemini = GEMINI_DIR / "GEMINI.md"
        if global_gemini.is_file():
            try:
                rules_blocks.append(f"<RULE[user_global]>\n{global_gemini.read_text(encoding='utf-8', errors='ignore').strip()}\n</RULE[user_global]>")
                seen_paths.add(str(global_gemini.resolve()))
            except Exception:
                rules_blocks.append(DEFAULT_USER_GLOBAL_RULES)
        else:
            rules_blocks.append(DEFAULT_USER_GLOBAL_RULES)

        # Walk from CWD up to root for workspace GEMINI.md, AGENTS.md, DESIGN.md, .agents/rules/*.md
        curr = cwd.resolve()
        chain: list[Path] = []
        while True:
            chain.append(curr)
            if (curr / ".git").exists() or curr.parent == curr:
                break
            curr = curr.parent

        for folder in reversed(chain):
            for fname in ("GEMINI.md", "AGENTS.md", "DESIGN.md"):
                try:
                    fp = folder / fname
                    if fp.is_file() and str(fp.resolve()) not in seen_paths:
                        seen_paths.add(str(fp.resolve()))
                        rules_blocks.append(f"<RULE[{fp.name}] path=\"{fp}\">\n{fp.read_text(encoding='utf-8', errors='ignore')[:3000]}\n</RULE[{fp.name}]>")
                except OSError:
                    pass
            for ag_dir in (".agents", ".agent"):
                try:
                    rdir = folder / ag_dir / "rules"
                    if rdir.is_dir():
                        for rfile in sorted(rdir.glob("*.md")):
                            if str(rfile.resolve()) not in seen_paths:
                                seen_paths.add(str(rfile.resolve()))
                                rules_blocks.append(f"<RULE[{rfile.name}]>\n{rfile.read_text(encoding='utf-8', errors='ignore')[:2500]}\n</RULE[{rfile.name}]>")
                except OSError:
                    pass
        return "\n\n".join(rules_blocks)

    def format_skills_catalog(self) -> str:
        names = ", ".join(sorted(self.skills.keys()))
        return (
            f"[설치된 Antigravity 스킬 카탈로그 (총 {len(self.skills)}개 — 필요시 activate_skill 도구로 본문 로드)]:\n"
            f"{names}"
        )

    def get_skill_content(self, skill_name: str) -> str:
        meta = self.skills.get(skill_name)
        if not meta:
            for k, v in self.skills.items():
                if skill_name.lower() in k.lower():
                    meta = v
                    break
        if not meta:
            return f"Skill '{skill_name}' not found."
        try:
            text = Path(meta["path"]).read_text(encoding="utf-8", errors="ignore")
            text = re.sub(r"^---\s*\n.*?\n---\s*\n", "", text, flags=re.DOTALL).strip()
            return f"[{meta['name']}] {meta['description'][:140]}\n{text[:320]}"
        except Exception as e:
            return f"Error reading skill {skill_name}: {e}"


def build_antigravity_system_prompt(
    loader: AntigravityCustomizationLoader,
    cwd: Path,
    active_skills: list[str],
    include_tools: bool = False,
) -> str:
    active_skill_blobs = ""
    if active_skills:
        blobs = [loader.get_skill_content(s) for s in active_skills[:2]]
        active_skill_blobs = "\n" + "\n".join(blobs)

    tools_guide = ""
    if include_tools:
        tools_guide = (
            "\n[도구 호출 규칙]\n"
            "PC 제어·명령 실행·파일 작업이 필요하면 먼저 아래 형식의 코드블록 하나만 출력하세요:\n"
            "```tool_call\n"
            '{"tool": "도구명", "args": {...}}\n'
            "```\n"
            "사용 가능한 도구:\n"
            '- pc_control: {"action": "open_app|close_app|open_url|volume|window|mouse|keyboard|system_info|powershell", "target": "앱/URL/창/명령", "value": "입력값/단계", "x": 960, "y": 540}\n'
            '- run_shell: {"command": "PowerShell 명령어"}\n'
            '- list_files: {"path": "."}\n'
            '- read_file: {"path": "파일경로", "start_line": 1, "end_line": 100}\n'
            '- write_file: {"path": "파일경로", "content": "내용"}\n'
            '- patch_file: {"path": "파일경로", "target": "기존문자열", "replacement": "새문자열"}\n'
            '- grep_search: {"pattern": "정규식", "path": "."}\n'
            '- rag_search: {"query": "검색어"}\n'
            '- activate_skill: {"name": "스킬명"}\n'
        )

    return (
        "역할: Antigravity 시스템 프롬프트·48개 스킬·PC 원격 제어 하네스가 적용된 로컬 AI 에이전트 LCODE.\n"
        "규칙: 1) 복창 없이 현재 시제·능동태·단문(~합니다/~했습니다) 작성. "
        "2) **한 줄 결론** / **주요 변경** / **검증** 포맷과 [파일명](file:///경로) 링크 사용. "
        "3) UI/앱 작업 시 hallmark-apps -> hallmark 및 design-motion-stack(D1:방향결정 hallmark -> D2:ui-ux-pro-max/frontend-design/taste 점검 -> D3:emil/animate 모션 -> D4:impeccable 다듬기, 우선순위: 사용자/DESIGN.md > 플랫폼/hallmark-apps > Hallmark > D2 > D3·D4) 적용."
        f"{tools_guide}{active_skill_blobs}"
    )


def ensure_ollama() -> None:
    try:
        urllib.request.urlopen(f"{OLLAMA_API}/api/tags", timeout=1.5)
    except Exception:
        if os.path.exists(OLLAMA_BIN):
            subprocess.Popen(
                [OLLAMA_BIN, "serve"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            )
            time.sleep(2)


# ============================================================================
# [2] Local Vector RAG Engine (SQLite + nomic-embed-text)
# ============================================================================
class LocalRAGEngine:
    def __init__(self, db_path: Path = DB_PATH):
        db_path.parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(str(db_path))
        self.conn.execute(
            """
            CREATE TABLE IF NOT EXISTS chunks (
                id TEXT PRIMARY KEY,
                workspace TEXT,
                source_path TEXT,
                chunk_index INTEGER,
                content TEXT,
                embedding TEXT
            )
            """
        )
        self.conn.commit()

    def embed(self, text: str) -> list[float]:
        ensure_ollama()
        req = urllib.request.Request(
            f"{OLLAMA_API}/api/embeddings",
            data=json.dumps({"model": EMBED_MODEL, "prompt": text[:4000], "options": {"num_gpu": 0}}).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        return data.get("embedding", [])

    def ingest_path(self, target_path: str, workspace: str = "default") -> dict:
        root = Path(target_path).resolve()
        if not root.exists():
            return {"error": f"Path not found: {target_path}"}

        exts = {".py", ".kt", ".java", ".js", ".ts", ".tsx", ".json", ".md", ".txt", ".toml", ".yaml", ".yml", ".gradle", ".kts", ".ps1", ".sh", ".html", ".css", ".rs", ".go", ".c", ".cpp", ".h"}
        skip_dirs = {".git", "node_modules", "build", ".gradle", "__pycache__", ".venv", "venv", "dist", ".idea"}

        files: list[Path] = []
        if root.is_file():
            files = [root]
        else:
            for p in root.rglob("*"):
                if any(part in skip_dirs for part in p.parts):
                    continue
                if p.is_file() and (p.suffix.lower() in exts or p.name in {"README", "Dockerfile", "Makefile"}):
                    if p.stat().st_size <= 250_000:
                        files.append(p)

        indexed_chunks = 0
        files_count = 0
        for fpath in files[:80]:
            try:
                text = fpath.read_text(encoding="utf-8", errors="ignore")
            except Exception:
                continue
            if not text.strip():
                continue
            files_count += 1
            rel = str(fpath.relative_to(root.parent if root.is_file() else root))
            chunk_size = 1400
            for idx, i in enumerate(range(0, len(text), chunk_size)):
                piece = text[i : i + chunk_size]
                cid = hashlib.sha1(f"{workspace}:{rel}:{idx}:{piece[:64]}".encode("utf-8")).hexdigest()
                cur = self.conn.execute("SELECT 1 FROM chunks WHERE id = ?", (cid,)).fetchone()
                if cur:
                    continue
                vec = self.embed(f"File: {rel}\n{piece}")
                if vec:
                    self.conn.execute(
                        "INSERT OR REPLACE INTO chunks VALUES (?, ?, ?, ?, ?, ?)",
                        (cid, workspace, rel, idx, piece, json.dumps(vec)),
                    )
                    indexed_chunks += 1
        self.conn.commit()
        return {"workspace": workspace, "files_scanned": files_count, "new_chunks_indexed": indexed_chunks}

    def search(self, query: str, workspace: str = "default", top_k: int = 4) -> list[dict]:
        rows = self.conn.execute(
            "SELECT source_path, chunk_index, content, embedding FROM chunks WHERE workspace = ?",
            (workspace,),
        ).fetchall()
        if not rows:
            rows = self.conn.execute(
                "SELECT source_path, chunk_index, content, embedding FROM chunks"
            ).fetchall()
        if not rows:
            return []
        qvec = self.embed(query)
        if not qvec:
            return []
        scored = []
        qnorm = math.sqrt(sum(x * x for x in qvec)) or 1.0
        for spath, cidx, content, emb_json in rows:
            vec = json.loads(emb_json)
            vnorm = math.sqrt(sum(x * x for x in vec)) or 1.0
            sim = sum(a * b for a, b in zip(qvec, vec)) / (qnorm * vnorm)
            scored.append((sim, spath, cidx, content))
        scored.sort(key=lambda x: x[0], reverse=True)
        return [
            {"score": round(s, 3), "file": sp, "chunk": ci, "content": ct}
            for s, sp, ci, ct in scored[:top_k]
        ]

    def stats(self) -> list[dict]:
        rows = self.conn.execute(
            "SELECT workspace, COUNT(DISTINCT source_path), COUNT(*) FROM chunks GROUP BY workspace"
        ).fetchall()
        return [{"workspace": w, "files": f, "chunks": c} for w, f, c in rows]


# ============================================================================
# [3] Autonomous ReAct Tool Executor
# ============================================================================
def build_repo_map(cwd: Path, max_entries: int = 35) -> str:
    skip = {".git", "node_modules", "build", ".gradle", "__pycache__", ".venv", "dist", "AppData", ".ollama", ".gemini", ".android", ".vscode", ".antigravity"}
    entries = []
    try:
        items = [p for p in cwd.iterdir() if not p.name.startswith(".") and p.name not in skip]
    except Exception:
        return "(Unable to list directory)"
    for p in sorted(items, key=lambda x: (not x.is_dir(), x.name.lower()))[:max_entries]:
        try:
            if p.is_dir():
                entries.append(f"📁 {p.name}/")
            else:
                entries.append(f"📄 {p.name} ({p.stat().st_size}B)")
        except Exception:
            continue
    return "\n".join(entries)


def execute_tool(
    tool_name: str,
    args: dict,
    cwd: Path,
    rag: LocalRAGEngine,
    loader: AntigravityCustomizationLoader,
    active_ws: str,
    active_skills: list[str],
) -> str:
    try:
        if tool_name == "activate_skill":
            sname = args.get("name", "")
            if sname and sname not in active_skills:
                active_skills.append(sname)
            return loader.get_skill_content(sname)

        if tool_name == "list_files":
            target = (cwd / args.get("path", ".")).resolve()
            return build_repo_map(target)

        if tool_name == "read_file":
            raw_path = args["path"]
            fpath = Path(raw_path) if Path(raw_path).is_absolute() else (cwd / raw_path).resolve()
            lines = fpath.read_text(encoding="utf-8", errors="ignore").splitlines()
            start = max(1, int(args.get("start_line", 1)))
            end = min(len(lines), int(args.get("end_line", start + 120)))
            numbered = [f"{i}: {lines[i-1]}" for i in range(start, end + 1)]
            return f"[{fpath.name} (file:///{str(fpath).replace(chr(92), '/')}) lines {start}-{end} of {len(lines)}]\n" + "\n".join(numbered)

        if tool_name == "write_file":
            raw_path = args["path"]
            fpath = Path(raw_path) if Path(raw_path).is_absolute() else (cwd / raw_path).resolve()
            fpath.parent.mkdir(parents=True, exist_ok=True)
            fpath.write_text(args["content"], encoding="utf-8")
            return f"Wrote {len(args['content'])} chars to [{fpath.name}](file:///{str(fpath).replace(chr(92), '/')})"

        if tool_name == "patch_file":
            raw_path = args["path"]
            fpath = Path(raw_path) if Path(raw_path).is_absolute() else (cwd / raw_path).resolve()
            text = fpath.read_text(encoding="utf-8")
            target = args["target"]
            if target not in text:
                return f"Error: target string not found in {fpath.name}"
            new_text = text.replace(target, args["replacement"], 1)
            fpath.write_text(new_text, encoding="utf-8")
            return f"Patched [{fpath.name}](file:///{str(fpath).replace(chr(92), '/')}) successfully."

        if tool_name == "grep_search":
            pattern = args["pattern"]
            search_root = (cwd / args.get("path", ".")).resolve()
            rx = re.compile(pattern, re.IGNORECASE)
            matches = []
            skip_dirs = {".git", "node_modules", "build", ".gradle", "__pycache__", ".venv", "dist"}
            for p in search_root.rglob("*"):
                if any(part in skip_dirs for part in p.parts) or not p.is_file() or p.stat().st_size > 200_000:
                    continue
                try:
                    for idx, line in enumerate(p.read_text(encoding="utf-8", errors="ignore").splitlines(), 1):
                        if rx.search(line):
                            matches.append(f"{p.name}:{idx}: {line.strip()[:140]}")
                            if len(matches) >= 30:
                                break
                except Exception:
                    continue
                if len(matches) >= 30:
                    break
            return "\n".join(matches) if matches else f"No matches for '{pattern}'"

        if tool_name == "run_shell":
            cmd = args["command"]
            proc = subprocess.run(
                ["powershell", "-NoProfile", "-Command", cmd],
                cwd=str(cwd),
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="ignore",
                timeout=180,
            )
            out = (proc.stdout + "\n" + proc.stderr).strip()
            return f"[Exit {proc.returncode}]\n{out[:3500]}"

        if tool_name == "git_clone_and_index":
            url = args["url"]
            repo_name = url.rstrip("/").split("/")[-1].removesuffix(".git")
            ws = args.get("workspace", repo_name)
            dest = (cwd / repo_name).resolve()
            if not dest.exists():
                subprocess.run(["git", "clone", "--depth", "1", url, str(dest)], check=True, capture_output=True, text=True)
            ing = rag.ingest_path(str(dest), workspace=ws)
            return json.dumps({"cloned_to": str(dest), "rag_indexed": ing}, ensure_ascii=False)

        if tool_name == "rag_index":
            ws = args.get("workspace", active_ws)
            res = rag.ingest_path(str((cwd / args["path"]).resolve()), workspace=ws)
            return json.dumps(res, ensure_ascii=False)

        if tool_name == "rag_search":
            ws = args.get("workspace", active_ws)
            res = rag.search(args["query"], workspace=ws, top_k=int(args.get("top_k", 4)))
            return json.dumps(res, ensure_ascii=False, indent=2)

        if tool_name == "pc_control":
            action = str(args.get("action", "powershell")).lower()
            target = str(args.get("target", "")).strip()
            val = str(args.get("value", "")).strip()
            x = int(args.get("x", 960))
            y = int(args.get("y", 540))

            if action == "open_app":
                app_map = {
                    "메모장": "notepad.exe",
                    "notepad": "notepad.exe",
                    "계산기": "calc.exe",
                    "calc": "calc.exe",
                    "탐색기": "explorer.exe",
                    "explorer": "explorer.exe",
                    "크롬": "chrome.exe",
                    "chrome": "chrome.exe",
                    "브라우저": "msedge.exe",
                    "작업관리자": "taskmgr.exe",
                    "작업 관리자": "taskmgr.exe",
                    "taskmgr": "taskmgr.exe",
                    "터미널": "wt.exe",
                    "cmd": "cmd.exe",
                    "powershell": "powershell.exe",
                    "설정": "ms-settings:",
                }
                exe = app_map.get(target.lower(), target or "notepad.exe")
                subprocess.Popen(["cmd", "/c", "start", "", exe], cwd=str(cwd))
                return f"PC 앱 실행 완료: {exe}"

            if action == "open_url":
                url = target if target.startswith(("http://", "https://")) else f"https://{target}"
                subprocess.Popen(["cmd", "/c", "start", "", url], cwd=str(cwd))
                return f"PC 브라우저 URL 열기 완료: {url}"

            if action == "close_app":
                proc_name = target.removesuffix(".exe")
                proc = subprocess.run(
                    ["powershell", "-NoProfile", "-Command", f"Stop-Process -Name '{proc_name}' -Force -ErrorAction SilentlyContinue; Write-Output '종료 처리: {proc_name}'"],
                    capture_output=True, text=True, encoding="utf-8", errors="ignore", timeout=15
                )
                return (proc.stdout or f"프로세스 종료 요청: {proc_name}").strip()

            if action == "volume":
                steps = max(1, min(25, int(val) if val.isdigit() else 5))
                char_code = "173" if target in ("mute", "음소거") else ("174" if target in ("down", "내려", "감소") else "175")
                ps_cmd = f"$w = New-Object -ComObject WScript.Shell; 1..{1 if char_code == '173' else steps} | ForEach-Object {{ $w.SendKeys([char]{char_code}); Start-Sleep -Milliseconds 30 }}"
                subprocess.run(["powershell", "-NoProfile", "-Command", ps_cmd], capture_output=True, timeout=10)
                return f"PC 볼륨 제어 완료: {target} ({steps}단계)"

            if action == "window":
                if target in ("minimize_all", "desktop", "바탕화면"):
                    ps_cmd = "(New-Object -ComObject Shell.Application).ToggleDesktop()"
                elif target in ("restore_all", "복구"):
                    ps_cmd = "(New-Object -ComObject Shell.Application).UndoMinimizeALL()"
                elif target in ("close_active", "닫기"):
                    ps_cmd = "(New-Object -ComObject WScript.Shell).SendKeys('%{F4}')"
                elif target in ("switch", "alttab", "전환"):
                    ps_cmd = "(New-Object -ComObject WScript.Shell).SendKeys('%{TAB}')"
                else:
                    ps_cmd = f"(New-Object -ComObject WScript.Shell).AppActivate('{target}')"
                subprocess.run(["powershell", "-NoProfile", "-Command", ps_cmd], capture_output=True, timeout=10)
                return f"PC 창 제어 완료: {target}"

            if action == "keyboard":
                text_to_send = val or target
                escaped = text_to_send.replace("'", "''")
                ps_cmd = (
                    f"Set-Clipboard -Value '{escaped}'; "
                    "$w = New-Object -ComObject WScript.Shell; "
                    "Start-Sleep -Milliseconds 120; "
                    "$w.SendKeys('^v')"
                )
                subprocess.run(["powershell", "-NoProfile", "-Command", ps_cmd], capture_output=True, timeout=10)
                return f"PC 키보드 텍스트 입력 완료: {text_to_send[:60]}"

            if action == "mouse":
                ps_cmd = (
                    "Add-Type -AssemblyName System.Windows.Forms; "
                    f"[System.Windows.Forms.Cursor]::Position = New-Object System.Drawing.Point({x}, {y}); "
                    "Write-Output \"마우스 이동 완료: ({x}, {y})\""
                )
                subprocess.run(["powershell", "-NoProfile", "-Command", ps_cmd], capture_output=True, timeout=10)
                return f"PC 마우스 제어 완료: ({x}, {y}) {target}"

            if action == "system_info":
                ps_cmd = (
                    "$cpu = (Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average; "
                    "$os = Get-CimInstance Win32_OperatingSystem; "
                    "$ramUsed = [math]::Round(($os.TotalVisibleMemorySize - $os.FreePhysicalMemory)/1MB, 1); "
                    "$ramTotal = [math]::Round($os.TotalVisibleMemorySize/1MB, 1); "
                    "$disk = Get-PSDrive C; "
                    "$diskFree = [math]::Round($disk.Free/1GB, 1); "
                    "$procs = (Get-Process | Sort-Object WorkingSet64 -Descending | Select-Object -First 5 -ExpandProperty ProcessName) -join ', '; "
                    "Write-Output \"CPU: ${cpu}% | RAM: ${ramUsed}GB / ${ramTotal}GB | C드라이브 여유: ${diskFree}GB | 상위 프로세스: ${procs}\""
                )
                proc = subprocess.run(["powershell", "-NoProfile", "-Command", ps_cmd], capture_output=True, text=True, encoding="utf-8", errors="ignore", timeout=15)
                return proc.stdout.strip()

            # Fallback: execute target/value as PowerShell
            cmd_str = target or val
            proc = subprocess.run(
                ["powershell", "-NoProfile", "-Command", cmd_str],
                cwd=str(cwd), capture_output=True, text=True, encoding="utf-8", errors="ignore", timeout=60
            )
            return f"[Exit {proc.returncode}]\n{(proc.stdout + proc.stderr).strip()[:2500]}"

        return f"Unknown tool: {tool_name}"
    except Exception as e:
        return f"Tool Execution Error ({tool_name}): {e}"


def resolve_model_profile(model_arg: str) -> tuple[str, dict]:
    clean = (model_arg or "game").strip()
    if clean in MODELS:
        return clean, MODELS[clean]
    low = clean.lower()
    if "qwen3" in low or "game" in low:
        return "game", {**MODELS["game"], "id": clean}
    if "coder" in low or "qwen2.5" in low:
        return "coder", {**MODELS["coder"], "id": clean}
    if "gemma" in low or "deep" in low:
        return "deep", {**MODELS["deep"], "id": clean}
    return "custom", {
        "id": clean,
        "desc": f"사용자 지정 로컬 모델 ({clean})",
        "num_ctx": 8192,
        "num_gpu": -1,
    }


def chat_ollama(profile_key: str, messages: list[dict]) -> tuple[str, float]:
    ensure_ollama()
    _, prof = resolve_model_profile(profile_key)
    options = {"num_ctx": prof.get("num_ctx", 8192), "temperature": 0.2, "num_predict": 2048}
    if prof["num_gpu"] >= 0:
        options["num_gpu"] = prof["num_gpu"]

    chat_messages = []
    for m in messages:
        role = m.get("role", "user")
        content = m.get("content", "")
        if role == "user" and "qwen3" in prof["id"]:
            content = content.replace(" /no_think", "").replace("\n/no_think", "").replace("/no_think\n", "").strip()
            content = f"{content} /no_think"
        chat_messages.append({"role": role, "content": content})

    payload = {
        "model": prof["id"],
        "messages": chat_messages,
        "think": False,
        "stream": False,
        "keep_alive": "10m",
        "options": options,
    }
    req = urllib.request.Request(
        f"{OLLAMA_API}/api/chat",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=300) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    msg_obj = data.get("message") or {}
    content = msg_obj.get("content", "") or data.get("response", "")
    content = re.sub(r"<think>.*?</think>\s*", "", content, flags=re.DOTALL).strip()
    if "</think>" in content:
        content = content.split("</think>")[-1].strip()
    if content.startswith("<think>"):
        content = content.replace("<think>", "").strip()
    eval_count = data.get("eval_count", 0)
    eval_dur = data.get("eval_duration", 1) / 1e9
    speed = round(eval_count / eval_dur, 1) if eval_dur > 0 else 0.0
    return content, speed


def extract_tool_call(text: str) -> dict | None:
    m = re.search(r"```(?:tool_call|json)\s*(\{.*?\})\s*```", text, re.DOTALL)
    if m:
        try:
            obj = json.loads(m.group(1))
            if "tool" in obj and "args" in obj:
                return obj
        except Exception:
            pass
    return None


def auto_detect_skills(query: str, loader: AntigravityCustomizationLoader) -> list[str]:
    q_low = query.lower()
    matched = []
    if any(k in q_low for k in ("디자인", "화면", "ui", "ux", "모바일", "앱", "윈도우", "모션", "애니메이션")):
        for s in ("design-motion-stack", "hallmark-apps", "hallmark"):
            if s in loader.skills:
                matched.append(s)
    for sname in loader.skills:
        if sname.lower() in q_low and sname not in matched:
            matched.append(sname)
    return matched[:3]


def run_agent_turn(
    user_query: str,
    profile_key: str,
    cwd: Path,
    rag: LocalRAGEngine,
    loader: AntigravityCustomizationLoader,
    workspace: str,
    history: list[dict],
    active_skills: list[str],
    max_steps: int = 5,
    tools_log: list[dict] | None = None,
) -> tuple[str, float]:
    for auto_s in auto_detect_skills(user_query, loader):
        if auto_s not in active_skills:
            active_skills.append(auto_s)

    tool_keywords = (
        "파일", "폴더", "읽", "수정", "패치", "설치", "클론", "clone", "git", "빌드", "실행", "검색", "인덱싱", "분석", "구조",
        "열어", "켜", "꺼", "종료", "볼륨", "소리", "음소거", "창", "바탕화면", "마우스", "클릭", "키보드", "입력", "타이핑",
        "상태", "사양", "메모장", "크롬", "계산기", "탐색기", "유튜브", "캡처", "명령", "제어", "컨트롤", "프로세스", "목록", "확인", "만들", "생성"
    )
    needs_tools = any(k in user_query.lower() for k in tool_keywords)

    sys_prompt = build_antigravity_system_prompt(loader, cwd, active_skills, include_tools=needs_tools)
    if not history:
        history.append({"role": "system", "content": sys_prompt})
    else:
        history[0] = {"role": "system", "content": sys_prompt}

    rag_context = ""
    wants_rag = any(k in user_query.lower() for k in ("rag", "인덱싱", "벡터", "지식 검색", "클론"))
    if wants_rag and len(user_query) >= 6:
        rag_hits = rag.search(user_query, workspace=workspace, top_k=2)
        if rag_hits and rag_hits[0]["score"] >= 0.55:
            snippets = [f"[{h['file']}]\n{h['content'][:300]}" for h in rag_hits]
            rag_context = "\n[RAG 참고]\n" + "\n".join(snippets)

    _, prof = resolve_model_profile(profile_key)
    no_think_suffix = " /no_think" if "qwen3" in prof["id"] else ""
    augmented_user = (
        f"[현재 작업 폴더: {cwd}]\n"
        f"{rag_context}\n\n[요청]\n{user_query}{no_think_suffix}"
    )
    history.append({"role": "user", "content": augmented_user})

    if not needs_tools:
        reply, speed = chat_ollama(profile_key, history)
        return reply, speed

    last_speed = 0.0
    seen_calls: set[str] = set()
    for step in range(max_steps):
        reply, speed = chat_ollama(profile_key, history)
        last_speed = speed
        history.append({"role": "assistant", "content": reply})

        tc = extract_tool_call(reply)
        if not tc:
            return reply, last_speed

        tname = tc["tool"]
        targs = tc["args"]
        sig = f"{tname}:{json.dumps(targs, sort_keys=True, ensure_ascii=False)}"
        if sig in seen_calls:
            history.append(
                {
                    "role": "user",
                    "content": "이미 동일한 도구를 실행했습니다. 추가 도구 호출(tool_call) 없이 지금까지 수집한 정보와 user_global 규칙 포맷(한 줄 결론 / 주요 변경 / 검증 또는 단문 결론, 클릭 가능한 file:/// 링크)에 맞춰 즉시 최종 한국어 답변을 작성하세요. /no_think",
                }
            )
            final_reply, final_spd = chat_ollama(profile_key, history)
            return final_reply, final_spd
        seen_calls.add(sig)

        print(f"  ⚙️ [Harness Step {step+1}] 도구 실행: {tname}({json.dumps(targs, ensure_ascii=False)[:100]})", file=sys.stderr)
        t_start = time.time()
        obs = execute_tool(tname, targs, cwd, rag, loader, workspace, active_skills)
        t_dur_ms = int((time.time() - t_start) * 1000)
        if tools_log is not None:
            tools_log.append(
                {
                    "id": f"lcode-tool-{step+1}",
                    "title": f"LCODE CLI 도구 실행: {tname}",
                    "commandOrPath": json.dumps(targs, ensure_ascii=False)[:120],
                    "outputPreview": obs[:500],
                    "durationMs": max(50, t_dur_ms),
                    "status": "COMPLETED",
                }
            )
        history.append(
            {
                "role": "user",
                "content": f"[도구 실행 결과 ({tname})]\n{obs}\n\n충분한 정보를 얻었다면 tool_call 없이 user_global 포맷(한 줄 결론 / 주요 변경 / 검증, 클릭 가능한 file:/// 링크)으로 최종 한국어 답변을 작성하고, 추가 작업이 꼭 필요할 때만 다른 도구를 호출하세요. /no_think",
            }
        )

    history.append(
        {
            "role": "user",
            "content": "모든 도구 실행 단계가 완료되었습니다. tool_call 블록 없이 user_global 포맷에 맞춰 최종 한국어 답변만 명확하게 출력하세요. /no_think",
        }
    )
    final_reply, final_spd = chat_ollama(profile_key, history)
    return final_reply, final_spd


def repl(profile_key: str, workspace: str, cwd: Path, active_skills: list[str]) -> None:
    rag = LocalRAGEngine()
    loader = AntigravityCustomizationLoader()
    history: list[dict] = []
    _, prof = resolve_model_profile(profile_key)
    print("=" * 78)
    print(" ⚡ LCODE v2.0 — Antigravity 하네스·스킬(38종)·시스템프롬프트·RAG·PC제어 통합 CLI")
    print("=" * 78)
    print(f" • 모델 프로파일   : [{profile_key}] -> {prof['id']} ({prof['desc']})")
    print(f" • 로드된 스킬 수  : {len(loader.skills)}개 (~/.gemini/config/skills & builtin)")
    print(f" • 활성 스킬       : {active_skills if active_skills else '자동 감지 (On-Demand Progressive Disclosure)'}")
    print(f" • 로컬 RAG DB     : [{workspace}] ({rag.stats()})")
    print(f" • 작업 디렉토리   : {cwd}")
    print("-" * 78)
    print(" 명령어: /model [coder|game|ram-safe|deep] | /skills | /skill <이름> | /design-stack")
    print("         /index <경로> | /clone <깃허브URL> | /search <질의> | /vram | /clear | /exit")
    print("=" * 78)

    while True:
        try:
            line = input(f"\n[{profile_key}|{workspace}] > ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n종료합니다.")
            break
        if not line:
            continue
        if line in {"/exit", "/quit", "exit", "quit"}:
            break
        if line == "/clear":
            history.clear()
            print("대화 컨텍스트를 초기화했습니다.")
            continue
        if line == "/skills":
            print(f"\n[설치된 Antigravity 스킬 총 {len(loader.skills)}개]")
            for sname, meta in sorted(loader.skills.items()):
                mark = "★" if sname in active_skills else "·"
                print(f" {mark} {sname:30s} : {meta['description'][:95]}")
            continue
        if line.startswith("/skill "):
            sname = line[len("/skill "):].strip()
            if sname in loader.skills:
                if sname not in active_skills:
                    active_skills.append(sname)
                print(f"✓ 스킬 활성화 완료: {sname} ({loader.skills[sname]['path']})")
            else:
                print(f"스킬을 찾을 수 없습니다: {sname} (/skills 로 목록 확인)")
            continue
        if line == "/design-stack":
            for s in ("design-motion-stack", "hallmark-apps", "hallmark", "ui-ux-pro-max", "frontend-design", "design-taste-frontend", "emil-design-eng", "impeccable"):
                if s in loader.skills and s not in active_skills:
                    active_skills.append(s)
            print(f"✓ 디자인·모션 스택(D0~D5) 핵심 스킬 활성화 완료: {', '.join(active_skills)}")
            continue
        if line.startswith("/model"):
            parts = line.split()
            if len(parts) > 1:
                profile_key = parts[1]
                _, prof = resolve_model_profile(profile_key)
                print(f"✓ 모델 전환 완료: [{profile_key}] -> {prof['id']} ({prof['desc']})")
            else:
                for k, v in MODELS.items():
                    mark = "*" if k == profile_key else " "
                    print(f" {mark} {k:10s} : {v['id']} — {v['desc']}")
            continue
        if line.startswith("/index"):
            target = line[len("/index"):].strip() or "."
            print(f"📚 로컬 지식 인덱싱 중 ({target} -> workspace '{workspace}')...")
            res = rag.ingest_path(str((cwd / target).resolve()), workspace=workspace)
            print(f"✓ 인덱싱 완료: {json.dumps(res, ensure_ascii=False)}")
            continue
        if line.startswith("/clone"):
            url = line[len("/clone"):].strip()
            out = execute_tool("git_clone_and_index", {"url": url, "workspace": workspace}, cwd, rag, loader, workspace, active_skills)
            print(f"✓ 클론 및 지식 학습 완료: {out}")
            continue
        if line.startswith("/search"):
            q = line[len("/search"):].strip()
            hits = rag.search(q, workspace=workspace, top_k=4)
            print(json.dumps(hits, ensure_ascii=False, indent=2))
            continue
        if line == "/vram":
            subprocess.run([OLLAMA_BIN, "ps"])
            continue

        t0 = time.time()
        ans, spd = run_agent_turn(line, profile_key, cwd, rag, loader, workspace, history, active_skills)
        dt = round(time.time() - t0, 1)
        _, prof = resolve_model_profile(profile_key)
        print(f"\n{ans}\n\n[⚡ {prof['id']} | {spd} tok/s | {dt}s | 활성 스킬: {len(active_skills)}]")


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="LCODE v2.0 — Antigravity-Grade Local AI Vibe-Coding & RAG CLI")
    parser.add_argument("prompt", nargs="*", help="One-shot prompt (if omitted, starts interactive REPL)")
    parser.add_argument("-m", "--model", default="game", help="Model profile (game, coder, ram-safe, deep) or Ollama model ID")
    parser.add_argument("-w", "--workspace", default="default", help="RAG knowledge workspace name")
    parser.add_argument("--cwd", default="", help="Working directory path for file/shell tools")
    parser.add_argument("--json", action="store_true", help="Output structured JSON for PC bridge integration")
    parser.add_argument("-s", "--skill", action="append", default=[], help="Activate specific skill(s) by name")
    parser.add_argument("--design-stack", action="store_true", help="Preload D0~D5 design-motion-stack skills")
    parser.add_argument("--list-skills", action="store_true", help="List all discovered Antigravity skills and exit")
    parser.add_argument("--dump-system-prompt", action="store_true", help="Print the assembled Antigravity system prompt and exit")
    parser.add_argument("--index", help="Index a folder/file into local RAG before running")
    parser.add_argument("--clone", help="Clone a GitHub repo and index it into local RAG")
    args = parser.parse_args()

    cwd = Path(args.cwd).resolve() if args.cwd else Path.cwd()
    rag = LocalRAGEngine()
    loader = AntigravityCustomizationLoader()
    active_skills = list(args.skill)
    if args.design_stack:
        for s in ("design-motion-stack", "hallmark-apps", "hallmark", "ui-ux-pro-max", "frontend-design", "emil-design-eng", "impeccable"):
            if s in loader.skills and s not in active_skills:
                active_skills.append(s)

    if args.list_skills:
        print(f"총 {len(loader.skills)}개 Antigravity 스킬 로드됨:")
        for sname, meta in sorted(loader.skills.items()):
            print(f" - {sname:30s} ({meta['path']})")
        return

    if args.dump_system_prompt:
        print(build_antigravity_system_prompt(loader, cwd, active_skills))
        return

    if args.index:
        res = rag.ingest_path(str((cwd / args.index).resolve()), workspace=args.workspace)
        print(json.dumps(res, ensure_ascii=False))
        if not args.prompt and not args.clone:
            return

    if args.clone:
        out = execute_tool("git_clone_and_index", {"url": args.clone, "workspace": args.workspace}, cwd, rag, loader, args.workspace, active_skills)
        print(out)
        if not args.prompt:
            return

    if args.prompt:
        q = " ".join(args.prompt)
        history: list[dict] = []
        tools_log: list[dict] = []
        t0 = time.time()
        ans, spd = run_agent_turn(q, args.model, cwd, rag, loader, args.workspace, history, active_skills, tools_log=tools_log)
        dt = round(time.time() - t0, 1)
        _, prof = resolve_model_profile(args.model)
        if args.json:
            payload = {
                "ok": True,
                "reply": ans,
                "model": prof["id"],
                "speed": spd,
                "elapsedSec": dt,
                "cwd": str(cwd),
                "activeSkills": active_skills,
                "tools": tools_log,
            }
            print(json.dumps(payload, ensure_ascii=False))
        else:
            print(ans)
            print(f"\n[⚡ {prof['id']} | {spd} tok/s | 로드된 스킬 카탈로그: {len(loader.skills)}개 | 활성 스킬: {active_skills}]")
    else:
        repl(args.model, args.workspace, cwd, active_skills)


if __name__ == "__main__":
    main()
