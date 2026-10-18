#!/usr/bin/env python3
"""Gemini image generation for character assets (stdlib only). Same rules as opw_audio_project:

- One key, GEMINI_API_KEY, on StoryRig's own Google AI Studio project (its own billing and monthly spend cap): from
  the environment, else Storyrig/.env (gitignored). Never another project's key, so StoryRig never spends opw's credits.
- Tier mode STORYRIG_TIER, "free" by default (like OPW_TIER): waits between calls, waits 60 s / 120 s on 429,
  2 attempts at most, and a daily cap. "paid" only when set explicitly.
- "free" is only the pacing (waits, retries, daily cap); it does not make calls free on a billed project. Ask before
  calling, and report the number of calls.
- Model gemini-3.1-flash-image by default (2.5 copies the pose of an attached character image); reference images go first, then the prompt; aspect ratio set in imageConfig.

Usage (from the repo root):
  python3 -I tools/gemini_gen.py gen --prompt assets/kai/prompts/1_rig_pose.txt --out assets/kai/gemini/ref.png \
      [--attach assets/kai/gemini/ref.png ...] [--aspect 1:1] [--model gemini-2.5-flash-image]
  python3 -I tools/gemini_gen.py usage
  python3 -I tools/gemini_gen.py list
"""
import argparse
import base64
import datetime
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "https://generativelanguage.googleapis.com/v1beta"
ROOT = Path(__file__).resolve().parent.parent
USAGE = ROOT / "tools" / ".gemini_usage.json"
MODEL = "gemini-3.1-flash-image"  # follows a pose template; 2.5 copied the reference pose

TIER = os.getenv("STORYRIG_TIER", "free").lower()
if TIER == "free":
    SLEEP_BETWEEN_CALLS = 25
    DAILY_CAP = int(os.getenv("STORYRIG_DAILY_CAP", "20"))
    RETRY_DELAY = 25
elif TIER == "paid":
    SLEEP_BETWEEN_CALLS = 2
    DAILY_CAP = int(os.getenv("STORYRIG_DAILY_CAP", "200"))
    RETRY_DELAY = 5
else:
    sys.exit(f"STORYRIG_TIER must be 'free' or 'paid', not '{TIER}'")
MAX_RETRIES = 2


def read_env(path):
    env = {}
    if path.exists():
        for line in path.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def api_key():
    for source, value in (
        ("environment", os.environ.get("GEMINI_API_KEY")),
        ("Storyrig/.env", read_env(ROOT / ".env").get("GEMINI_API_KEY")),
    ):
        if value:
            print(f"key from {source}, tier={TIER}", file=sys.stderr)
            return value
    sys.exit("GEMINI_API_KEY not found: put StoryRig's own key in Storyrig/.env (GEMINI_API_KEY=...)")


def usage_today():
    data = json.loads(USAGE.read_text()) if USAGE.exists() else {}
    return data, datetime.date.today().isoformat()


def record_call():
    data, today = usage_today()
    data[today] = data.get(today, 0) + 1
    data["last_call"] = time.time()
    USAGE.write_text(json.dumps(data))


def pace():
    data, today = usage_today()
    if data.get(today, 0) >= DAILY_CAP:
        sys.exit(f"daily cap reached ({DAILY_CAP} calls, tier={TIER}); raise STORYRIG_DAILY_CAP on purpose if needed")
    wait = SLEEP_BETWEEN_CALLS - (time.time() - data.get("last_call", 0))
    if wait > 0:
        print(f"waiting {wait:.0f}s between calls (tier={TIER})", file=sys.stderr)
        time.sleep(wait)


def generate(key, model, parts, aspect):
    body = {
        "contents": [{"parts": parts}],
        "generationConfig": {"responseModalities": ["IMAGE"], "imageConfig": {"aspectRatio": aspect}},
    }
    req = urllib.request.Request(
        f"{API}/models/{model}:generateContent",
        data=json.dumps(body).encode(),
        method="POST",
        headers={"x-goog-api-key": key, "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=300) as r:
        return json.loads(r.read())


def cmd_gen(a):
    key = api_key()
    parts = []
    for f in a.attach or []:
        parts.append({"inline_data": {"mime_type": "image/png", "data": base64.b64encode(Path(f).read_bytes()).decode()}})
    # Lines starting with # are notes for people (see assets/templates/prompts), not sent.
    text = "\n".join(l for l in Path(a.prompt).read_text().splitlines() if not l.startswith("#"))
    parts.append({"text": text})

    res = None
    for attempt in range(1, MAX_RETRIES + 1):
        pace()
        record_call()
        try:
            res = generate(key, a.model, parts, a.aspect)
            break
        except urllib.error.HTTPError as e:
            detail = e.read().decode(errors="replace")[:300]
            print(f"attempt {attempt}/{MAX_RETRIES}: HTTP {e.code} {detail}", file=sys.stderr)
            # 400/403: bad request or key; 402: prepaid credits used up. Retrying can't help, it only adds to the count.
            if e.code in (400, 402, 403) or attempt == MAX_RETRIES:
                sys.exit(1)
            wait = min(60 * attempt, 180) if e.code == 429 else min(RETRY_DELAY * attempt, 120)
            print(f"waiting {wait}s before retrying", file=sys.stderr)
            time.sleep(wait)

    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    for cand in res.get("candidates", []):
        for p in (cand.get("content") or {}).get("parts", []):
            data = p.get("inlineData") or p.get("inline_data")
            if data:
                out.write_bytes(base64.b64decode(data["data"]))
                print(out)
                return
            if p.get("text"):
                print("model says:", p["text"][:300], file=sys.stderr)
    sys.exit("no image in response: " + json.dumps(res)[:600])


def cmd_list(_):
    """Models whose name mentions image generation (listing is free, it does not count as a call)."""
    req = urllib.request.Request(f"{API}/models?pageSize=1000", headers={"x-goog-api-key": api_key()})
    with urllib.request.urlopen(req, timeout=60) as r:
        for m in json.loads(r.read()).get("models", []):
            name = m["name"].removeprefix("models/")
            if "image" in name or "imagen" in name:
                print(name, "-", ",".join(m.get("supportedGenerationMethods", [])))


def cmd_usage(_):
    data, today = usage_today()
    print(f"tier={TIER} today={data.get(today, 0)}/{DAILY_CAP} calls")


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    g = sub.add_parser("gen")
    g.add_argument("--model", default=MODEL)
    g.add_argument("--prompt", required=True)
    g.add_argument("--out", required=True)
    g.add_argument("--attach", action="append")
    g.add_argument("--aspect", default="1:1")
    sub.add_parser("usage")
    sub.add_parser("list")
    a = ap.parse_args()
    {"gen": cmd_gen, "usage": cmd_usage, "list": cmd_list}[a.cmd](a)


if __name__ == "__main__":
    main()
