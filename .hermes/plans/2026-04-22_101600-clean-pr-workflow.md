# Plan: Saubere PR-Struktur für Autolike + Age-Cutoff

## Ausgangslage

**Repository:** `nopnop9090/SmartTube` → geforkt von `yuliskov/SmartTube`

**Offene PRs im Upstream (yuliskov):**
| PR # | Branch | Inhalt | Problem |
|------|--------|--------|---------|
| #5713 | `feature/autolike-lightweight` | Nur Autolike | OK |
| #5714 | `feature/age-cutoff` | Age-Cutoff **+ Autolike** (wg. Merge) | Vermischt |

**Commits auf `feature/autolike-lightweight`:** (5 Commits, nur 4 Dateien, keine submodule-Änderungen)
- `ba8d320d8` — Auto-like: lightweight configurable trigger ← **Das ist der Kern-Commit**
- `28edb589b` — Fix autolike toast spam
- `9ce0baed3` — Remove local build script

**Commits auf `feature/age-cutoff`:** (6 Commits, vermischt)
- `a1ce92070` — Merge autolike-lightweight into feature/age-cutoff ← **PROBLEM**
- `d46d04352` — MediaServiceCore: squash age-cutoff MSC commits
- `873c561be` — common: Video uses RelativePublishedTime; submodules for merge ← submodule-Änderungen
- `d57fbe138` — Add age cutoff for discovery feeds ← **Das ist der Age-Cutoff Kern-Commit**
- +autolike Commits durch Merge

**Autolike-Dateien (nur diese 4, keine submodule):**
```
common/src/main/java/.../presenters/settings/PlayerSettingsPresenter.java
common/src/main/java/.../prefs/PlayerTweaksData.java
common/src/main/res/values/strings.xml
smarttubetv/src/main/java/.../playback/PlaybackFragment.java
```

**Age-Cutoff-Dateien:**
```
common/src/main/java/.../prefs/AgeCutoffData.java          ← NEU
common/src/main/java/.../app/models/data/Video.java        ← Textänderungen
common/src/main/java/.../app/models/data/VideoGroup.java    ← Textänderungen
common/src/main/java/.../presenters/ChannelUploadsPresenter.java  ← Textänderungen
common/src/main/java/.../presenters/settings/GeneralSettingsPresenter.java ← Textänderungen
common/src/main/java/.../misc/MediaServiceManager.java     ← Textänderungen
common/src/main/res/values/strings.xml                     ← Textänderungen
MediaServiceCore  (submodule-pointer auf 6ec47968)
SharedModules    (submodule-pointer auf 64cc024a) ← PROBLEM: dieser Commit enthält autolike-Code
```

**Submodule-Problem:** `873c561be` aktualisiert SharedModules auf `64cc024a`. Dieser SharedModules-Commit enthält Autolike-Code. Wenn man den submodule-Pointer nimmt, zieht man Autolike mit rein. Lösung: nur die Java-Dateien cherry-picken, submodule-Pointer unberührt lassen.

---

## Ablauf (Schritt für Schritt, mit Bestätigung)

### Phase 0: Analyse (keine Änderungen, nur Lesen)
- [x] Verzeichnis: `~/projects/SmartTube`
- [x] Upstream: `yuliskov/SmartTube`
- [x] Fork: `nopnop9090/SmartTube`
- [x] Auth: `gh` CLI

### Phase 1: Bestehende PRs schließen
1. **DU BESTÄTIGST** ✓
2. `gh pr close 5713` — schließe PR #5713 (autolike-lightweight)
3. `gh pr close 5714` — schließe PR #5714 (age-cutoff)
4. Verifikation: `curl` auf upstream PRs → keine nopnop-PRs mehr offen

### Phase 2: Fork auf Basiszustand zurücksetzen
1. **DU BESTÄTIGST** ✓
2. Lokal: `git fetch origin master`
3. Lokal: `git branch -D` aller local branches außer master
4. Lokal: `git push origin --delete` aller remote branches außer master
5. Lokal: `git reset --hard origin/master`
6. Lokal: submodule update
7. Verifikation: `git log --oneline -3` → origin/master HEAD

### Phase 3: Sauberen Autolike-Branch erstellen
1. **DU BESTÄTIGST** ✓
2. `git checkout -b feature/autolike`
3. Cherry-picke autolike-Commits (ohne submodule):
   ```
   git cherry-pick -n 9ce0baed3  # Remove local build script
   git cherry-pick -n 28edb589b  # Fix autolike toast spam
   git cherry-pick -n ba8d320d8  # Auto-like: lightweight configurable trigger
   ```
4. Add + Commit (einzelner sauberer Commit oder behalte die 3)
5. `git push origin feature/autolike`
6. Zeige Diff-Stat zur Kontrolle

### Phase 4: Age-Cutoff vorbereiten
1. **DU BESTÄTIGST** ✓
2. `git checkout -b feature/age-cutoff` (von origin/master)
3. Cherry-picke nur die Age-Cutoff-Java-Dateien (keine submodule-Änderungen):
   ```
   # Nur AgeCutoffData.java
   git show d57fbe138 -- common/src/main/java/.../prefs/AgeCutoffData.java | git apply
   git show 873c561be -- common/src/main/java/.../app/models/data/Video.java | git apply
   git show 873c561be -- common/src/main/java/.../app/models/data/VideoGroup.java | git apply
   git show 873c561be -- common/src/main/java/.../presenters/ChannelUploadsPresenter.java | git apply
   git show 873c561be -- common/src/main/java/.../presenters/settings/GeneralSettingsPresenter.java | git apply
   git show 873c561be -- common/src/main/java/.../misc/MediaServiceManager.java | git apply
   git show 873c561be -- common/src/main/res/values/strings.xml | git apply
   ```
   *(Die submodule-Pointer (.gitmodules, MediaServiceCore, SharedModules) werden NICHT angefasst.)*
4. Strings prüfen (keine Autolike-Strings in den Age-Cutoff-Strings)
5. Commit erstellen
6. `git push origin feature/age-cutoff`
7. Zeige Diff-Stat zur Kontrolle

### Phase 5: PRs erstellen (mit deiner Vorab-Bestätigung)
1. **DU BESTÄTIGST** → Autolike-PR erstellen:
   ```
   gh pr create --repo yuliskov/SmartTube \
     --base master --head nopnop9090:feature/autolike \
     --title "Auto-like: lightweight configurable overlay duration and background dimming" \
     --body "..."
   ```
2. **DU BESTÄTIGST** → Age-Cutoff-PR erstellen:
   ```
   gh pr create --repo yuliskov/SmartTube \
     --base master --head nopnop9090:feature/age-cutoff \
     --title "Add age cutoff option for discovery feeds" \
     --body "..."
   ```

### Phase 6: Abschluss
- Beide PRs sind offen
- Fork hat nur master + 2 feature-branches
- Lokal: nur master + 2 feature-branches

---

## Wichtige Hinweise
- Die submodule-Pointer in Age-Cutoff bleiben auf dem Stand von origin/master. Die Java-Änderungen in Video.java etc. sind Read-Only-Änderungen (Variablennamen, Texte) und funktionieren auch mit dem alten submodule-Stand.
- Wenn upstream (yuliskov) das SharedModules-Modul separat updated, müssen ggf. die submodule-Pointer in Age-Cutoff später angepasst werden.
- **Reihenfolge wichtig:** Autolike-PR zuerst, damit Age-Cutoff sauber darauf aufbauen kann.
