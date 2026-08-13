# Resume Evidence Validation Commands

> Executed on 2026-08-13. Secret values are intentionally omitted. Commands
> that failed because of a path, quoting, wildcard, timeout, or environment
> issue are listed explicitly; they were not silently replaced.

## Repository and evidence inspection

```powershell
git status --short
git branch --show-current
git log -1 --oneline
Get-Content -Raw AGENTS.md
Get-Content -Raw README.md
Get-Content -Raw docs/PROJECT_STATUS.md
Get-Content -Raw docs/ARCHITECTURE.md
Get-Content -Raw docs/API.md
Get-Content -Raw docs/DATABASE.md
Get-Content -Raw docs/REDIS.md
Get-Content -Raw learning/phase-12/TestMatrix.md
Get-Content -Raw learning/phase-12/Audit.md
Get-Content -Raw finetune/README.md
Get-Content -Raw finetune/scripts/eval_real.py
Get-Content -Raw finetune/scripts/evaluate.py
Get-Content -Raw finetune/scripts/run_train.py
Get-Content -Raw finetune/scripts/train.py
rg --files
rg -n "79.69|10.5%|1.18|8.0s|57.62|24.3244" .
git log --all --oneline -S "79.69%"
git log --all --oneline -S "Eval Loss 1.18"
git log --all --oneline -S "57.62%"
```

## Baseline rerun

```powershell
# Java (java-backend; host-specific executable/cache paths redacted)
mvn `
  -Dphase12.python.executable=<python-executable> `
  clean package

# Python
python -m pytest -q

# Vue (web-client), executed in isolation after the concurrent worker retry failed
npm run typecheck
npm test -- --run
npm run build
```

## BGE and FAISS

The existing project index builder was run with the production configuration
and D-drive Hugging Face caches. The first build call exceeded the caller's
15-minute wait after downloading/loading the model, but the controlled index
was produced. It was then loaded in offline mode and queried without rebuilding.

```powershell
$evidenceCacheRoot = Join-Path ([IO.Path]::GetTempPath()) 'insurance-resume-evidence'
$env:HF_HOME = Join-Path $evidenceCacheRoot 'huggingface'
$env:SENTENCE_TRANSFORMERS_HOME = Join-Path $evidenceCacheRoot 'sentence-transformers'
$env:TRANSFORMERS_OFFLINE='1'
$env:HF_HUB_OFFLINE='1'
python <controlled index build command>
python <offline load/search evidence command>
```

The first search evidence command incorrectly treated `RetrievalResult` as an
iterable. The second attempted to serialize NumPy `float32` directly. The final
command parsed `result.documents` and converted scores to built-in `float`.

## Online environment and fixed Evidence run

```powershell
docker version --format '{{.Server.Version}}'
.\scripts\resume_evidence\Start-ResumeEvidence.ps1

# The first startup used a 90-second Python readiness limit and timed out while
# CPU BGE was loading. After approval, only the Evidence timeout was changed to
# 300 seconds. The second startup completed.

# A first smoke with the old host key returned Python AI_INTERNAL_ERROR / Java
# HTTP 502. A separate approved, redacted request established HTTP 401. After
# the key was replaced, the minimal DeepSeek request returned HTTP 200.

python `
  scripts\resume_evidence\run_online_evidence.py
python `
  scripts\resume_evidence\enrich_online_results.py
```

The DeepSeek targeted checks used an in-process User environment value and sent
a minimal `deepseek-chat` request to `https://api.deepseek.com/chat/completions`.
Only HTTP status and structured error type/code were printed; the key and response
body were never printed or written.

## Browser smoke

Chrome extension diagnostics were executed from the installed Chrome plugin:

```powershell
chrome-is-running.js --browser chrome --check
installed-browsers.js --json
check-extension-installed.js --browser chrome --json
check-native-host-manifest.js --browser chrome --json
open-chrome-window.js --browser chrome
```

The connected Chrome session opened `http://127.0.0.1:5173/`, registered a
temporary account, logged in, created a conversation, and sent one waiting-period
question. The first wait assertion hit the browser-control command budget; a DOM
snapshot confirmed that the single already-sent request completed. It was not resent.

## Static checks and cleanup

```powershell
python -m json.tool `
  evidence\resume_evidence\cases.json
python -m py_compile `
  scripts\resume_evidence\run_online_evidence.py
python -m py_compile `
  scripts\resume_evidence\enrich_online_results.py
rg -n -i <secret-value-patterns> evidence scripts/resume_evidence
.\scripts\resume_evidence\Stop-ResumeEvidence.ps1
git diff --check
git diff --stat
git status --short
```

Other disclosed command corrections:

- An initial terms path incorrectly prefixed `python-ai-service/`; `rg --files`
  located the repository-root path.
- An initial Java DTO read used `chat/dto/ChatResponse.java`; the class is in
  `chat/vo/ChatResponse.java`.
- An attempted root `docker-compose.yml` read failed because the file does not exist.
- Two secret-scan/read commands had PowerShell quoting/wildcard errors; corrected
  read-only scans passed without exposing a secret.
- A host-privileged command could not find `git` in its PATH; repository Git
  checks were rerun in the normal workspace shell.
