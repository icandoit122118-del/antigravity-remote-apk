# DESIGN.md — Antigravity Remote (APK)

## 1. Visual Direction & Design Owner
- **Design Owner**: `hallmark` + `hallmark-apps` (Native Android Jetpack Compose adaptation)
- **Theme Deal**: `Ribbon (two-in-the-morning)` merged with **WWIT 코끼리(Elephant)** Korean mental-care dark night aesthetic
- **Genre**: Atmospheric Minimalism & High-Craft Dark UI + Kinetic Typography & Neo-Brutalist Swiss Grids (for terminal/code cards)
- **Seed**: `antigravity-remote-agent-chat-c892·0434c987·2026-10-26·r0`

## 2. Color Tokens (Dark Mode Default — Calm Night Sage)
| Role | Token Name | Hex | OKLCH Equivalent | Contrast vs Ground |
|---|---|---|---|---|
| Ground (App Background) | `NightGround` | `#11161B` | `oklch(19% 0.02 235)` | — |
| Surface (20dp Card) | `NightSurface` | `#1A2229` | `oklch(24% 0.022 235)` | 1.25:1 (1dp border separated) |
| Elevated / Floating Pill | `NightRaised` | `#232E37` | `oklch(29% 0.025 235)` | 1.58:1 |
| Hairline Border (1dp) | `NightRule` | `#2A3742` | `oklch(33% 0.025 235)` | — |
| Primary Ink | `WarmIvoryInk` | `#F2EFE9` | `oklch(95% 0.012 85)` | **13.8:1** (AAA) |
| Secondary Ink | `MistSlateInk` | `#A7B4BE` | `oklch(75% 0.02 230)` | **6.4:1** (AA) |
| Muted Ink | `MutedSlateInk` | `#7A8A96` | `oklch(61% 0.02 230)` | **4.6:1** (AA) |
| Primary Accent (Sage Mint) | `CalmSageAccent` | `#68C5B2` | `oklch(76% 0.095 178)` | **8.2:1** |
| Accent Ink (On Sage) | `DeepForestInk` | `#0D1F1B` | `oklch(21% 0.03 178)` | **9.1:1** (vs Accent) |
| Secondary Accent (Warm Lamp) | `WarmLampGold` | `#E8B96D` | `oklch(80% 0.095 75)` | **8.9:1** |
| Danger / Error | `SoftCoralDanger` | `#E57373` | `oklch(68% 0.14 25)` | **5.6:1** |

## 3. Typography & Scannable Hierarchy
- **Display Greeting (Elephant Header)**: `22sp`, SemiBold (`600`), line-height `30sp`, tracking `-0.3sp`
- **Card / Section Title**: `16sp`, SemiBold (`600`), line-height `22sp`
- **Chat Body (Korean Optimized)**: `15sp`, Regular (`400`), line-height `24sp` (`1.6x`)
- **Code / Terminal / Metrics**: `FontFamily.Monospace`, `13sp`, line-height `19sp`, `fontFeatureSettings = "tnum"` (Tabular Numerals)

## 4. Layout, Touch Targets & States
- **Card Shape**: `20.dp` rounded corners (`RoundedCornerShape(20.dp)`) with `1.dp` hairline border (`#2A3742`), `16.dp` horizontal screen margins, `12.dp` vertical card rhythm.
- **Touch Targets**: All interactive controls enforce `48.dp × 48.dp` minimum touch bounds.
- **4 Data States**:
  1. `Normal`: Realistic Antigravity remote coding session with collapsible thought process, tool execution cards, and streaming tokens.
  2. `Loading`: Skeleton shimmer cards and calm status indicator while connecting to the PC bridge.
  3. `Empty`: Calm Elephant-style night illustration card with 3 quick-start prompt pills when a session has no messages.
  4. `Error`: Non-blocking connection recovery banner (`ws://<IP>:8765 응답 없음`) with instant retry and `라이브 시뮬레이션 전환` actions.
- **Break-UI Stress Mode**: Built-in `기본 데모 / 최악 데이터(Worst-case)` toggle testing 120-char unbroken filenames, 8,000+ token counts, 1-char names, multilingual/RTL strings, and 0-item edge cases without layout overflow.
- **Motion & Accessibility**:
  - `scale(0.97f)` spring feedback on press; `FastOutSlowInEasing` (`220ms`) on collapsible thought/tool cards.
  - Respects OS `ANIMATOR_DURATION_SCALE == 0f` (`prefers-reduced-motion`) by disabling infinite pulse animations and snapping transitions immediately.
