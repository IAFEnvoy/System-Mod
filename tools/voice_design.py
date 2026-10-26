#!/usr/bin/env python3
"""DashScope voice design, cloning and temporary uploads. Python 3.10+, standard library only."""

import argparse
import base64
import binascii
import getpass
import hashlib
import io
import json
import locale
import os
from pathlib import Path
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import wave


MODELS = ("qwen-audio-3.1-tts-flash", "qwen-audio-3.1-tts-plus",
          "qwen-audio-3.0-tts-flash", "qwen-audio-3.0-tts-plus")
MAX_AUDIO = 8 * 1024 * 1024
MAX_UPLOAD = 20 * 1024 * 1024
LANG = "en"
MESSAGES = {
    "description": ("阿里云音色设计 / 预览 / 临时上传；密钥从环境变量或隐藏输入读取。", "Aliyun voice design, preview and temporary upload; keys use the environment or hidden input."),
    "workspace": ("可选业务空间 ID；也可设置 DASHSCOPE_WORKSPACE_ID，留空使用兼容域名", "Optional Workspace ID or DASHSCOPE_WORKSPACE_ID; blank uses the legacy endpoint"),
    "design": ("按文字描述创建音色并保存试听 WAV 和模组配置", "Design a voice and save a preview WAV plus mod profile"),
    "clone": ("从 16-bit PCM WAV 复刻音色，保存本地 ID 与真实音色 ID 的映射", "Clone a 16-bit PCM WAV and save a local ID to provider voice ID mapping"),
    "synthesize": ("使用导出的音色配置合成测试 WAV", "Synthesize a test WAV using an exported voice profile"),
    "config": ("读取客户端 config/system/system-speech.json 中的密钥、地域与 Workspace ID", "Read key, region and Workspace ID from client config/system/system-speech.json"),
    "id": ("本地音色标识，如 system-test1；云端 ID 由阿里云生成", "Local voice ID, e.g. system-test1; Alibaba generates the provider ID"),
    "clone_audio": ("参考音频需为 16-bit PCM WAV，至少 5 秒、最多 60 秒，采样率不低于 16 kHz，最大 10 MiB。", "Reference must be a 16-bit PCM WAV, 5-60 seconds, at least 16 kHz, at most 10 MiB."),
    "exists": ("此本地 ID 已有结果或提交记录；先查询，避免重复创建收费音色。", "This local ID already has a result or submission record; query first to avoid duplicate billable voices."),
    "clone_created": ("本地 ID: {0}\n音色已创建；请用 query 查询状态，OK 后可用于合成。", "Local ID: {0}\nVoice created; use query to check status. Synthesis requires OK status."),
    "list": ("列出已创建音色", "List created voices"),
    "query": ("查询音色状态及绑定模型", "Query voice status and bound model"),
    "delete": ("删除一个音色（会要求确认）", "Delete a voice (asks for confirmation)"),
    "upload": ("临时上传参考音频，返回 oss:// URL（48 小时有效）", "Upload reference audio; return an oss:// URL valid for 48 hours"),
    "prompt": ("声音描述，1～500 字符，中英文", "Voice description, 1-500 characters, Chinese or English"),
    "preview": ("试听文本，15～200 字符，中英文", "Preview text, 15-200 characters, Chinese or English"),
    "prefix": ("音色前缀，1～10 位英文字母/数字", "Voice prefix, 1-10 ASCII letters/digits"),
    "output": ("输出目录；已有目录中生成唯一子目录", "Output directory; creates a unique child folder"),
    "key": ("百炼 API Key（不回显）: ", "DashScope API key (hidden): "),
    "key_required": ("需要百炼 API Key。", "A DashScope API key is required."),
    "workspace_required": ("音色操作需要 --workspace 或 DASHSCOPE_WORKSPACE_ID。", "Voice operations need --workspace or DASHSCOPE_WORKSPACE_ID."),
    "beijing": ("当前声音设计接口只为北京地域提供此模型，请使用北京密钥。", "This voice-design model is currently documented for Beijing; use a Beijing key."),
    "invalid": ("参数无效: {0}", "Invalid parameter: {0}"),
    "http": ("API 请求失败，HTTP {0}（响应内容已隐藏）。", "API request failed: HTTP {0} (response body omitted)."),
    "network": ("网络请求失败或超时，未自动重试。", "Network request failed or timed out; no automatic retry."),
    "response": ("接口返回了无法识别的数据。", "Unrecognized API response."),
    "oversize": ("数据超过体积限制。", "Data exceeds the size limit."),
    "confirm": ("输入音色 ID 确认删除: ", "Type the voice ID to confirm deletion: "),
    "cancel": ("已取消。", "Cancelled."),
    "voice": ("音色 ID: {0}", "Voice ID: {0}"),
    "profile": ("配置: {0}\n将音色参数合并到 MC config/system/system-speech.json，保留密钥和本地偏好，再在语音设置中重新加载；此文件不包含密钥。", "Profile: {0}\nMerge its voice parameters into MC config/system/system-speech.json, preserving credentials and local preferences, then reload Speech settings; this file contains no key."),
    "audio": ("试听: {0}", "Preview: {0}"),
    "audio_failed": ("音色已创建且配置已保存，但试听数据无效；请用 query 查询音色，不要重复创建。", "Voice created and profile saved, but preview data is invalid; use query instead of creating again."),
    "upload_note": ("有效期 48 小时；模型请求需加 X-DashScope-OssResourceResolve: enable。", "Valid for 48 hours; model requests must include X-DashScope-OssResourceResolve: enable."),
    "io": ("无法读取/写入本地文件，请检查路径与权限。", "Unable to read/write local files; check paths and permissions."),
    "model_note": ("qwen3-tts-flash 使用系统音色；设计音色绑定 Qwen-Audio-TTS，请同时更新模组配置中的 model 和 voice 后重新加载。", "qwen3-tts-flash uses system voices; designed voices bind Qwen-Audio-TTS. Update both model and voice in the mod settings before reloading."),
}


def tr(key, *args):
    return MESSAGES[key][0 if LANG == "zh" else 1].format(*args)


class ToolError(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def normalize_wav(data):
    # Some API WAVs retain a streaming placeholder length; rebuild the header from real PCM bytes.
    try:
        with wave.open(io.BytesIO(data), "rb") as source:
            channels, rate, width = source.getnchannels(), source.getframerate(), source.getsampwidth()
            if channels not in (1, 2) or width != 2 or not 8000 <= rate <= 48000:
                raise ToolError(tr("response"))
            pcm = source.readframes(MAX_AUDIO // (channels * width) + 1)
        if not pcm or len(pcm) > MAX_AUDIO or len(pcm) % (channels * width):
            raise ToolError(tr("response"))
        target = io.BytesIO()
        with wave.open(target, "wb") as output:
            output.setnchannels(channels)
            output.setsampwidth(width)
            output.setframerate(rate)
            output.writeframes(pcm)
        return target.getvalue()
    except (wave.Error, EOFError):
        raise ToolError(tr("response")) from None


def request(url, key=None, payload=None, headers=None, limit=12 * 1024 * 1024, method=None):
    """Never redirect bearer credentials or retry billable calls; cap every response."""
    supplied = dict(headers or {})
    if key:
        supplied["Authorization"] = "Bearer " + key
    data = payload
    if isinstance(payload, dict):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        supplied["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=supplied, method=method)
    try:
        with urllib.request.build_opener(NoRedirect()).open(req, timeout=60) as response:
            result = response.read(limit + 1)
            if len(result) > limit:
                raise ToolError(tr("oversize"))
            return result
    except urllib.error.HTTPError as error:
        raise ToolError(tr("http", error.code)) from None
    except (urllib.error.URLError, TimeoutError, OSError):
        raise ToolError(tr("network")) from None


def json_request(url, key, payload=None):
    try:
        data = json.loads(request(url, key, payload))
        if not isinstance(data, dict) or data.get("code"):
            raise ToolError(tr("response"))
        return data
    except (ValueError, UnicodeError):
        raise ToolError(tr("response")) from None


def base_url(args, legacy=False):
    if args.workspace and not legacy:
        region = "cn-beijing" if args.region == "beijing" else "ap-southeast-1"
        return f"https://{args.workspace}.{region}.maas.aliyuncs.com"
    return "https://dashscope.aliyuncs.com" if args.region == "beijing" else "https://dashscope-intl.aliyuncs.com"


def voice_request(args, key, action, parameters=None, **fields):
    if args.region != "beijing":
        raise ToolError(tr("beijing"))
    result = json_request(base_url(args) + "/api/v1/services/audio/tts/customization", key,
                          {"model": "voice-enrollment", "input": {"action": action, **fields},
                           **({"parameters": parameters} if parameters is not None else {})})
    if not isinstance(result.get("output"), dict):
        raise ToolError(tr("response"))
    return result["output"]


def design(args, key):
    if not 1 <= len(args.prompt) <= 500 or not 15 <= len(args.preview_text) <= 200:
        raise ToolError(tr("invalid", "prompt / preview-text"))
    if not re.fullmatch(r"[A-Za-z0-9]{1,10}", args.prefix):
        raise ToolError(tr("invalid", "prefix"))
    # Reserve a writable directory before creating a billable remote voice.
    folder = args.output / (time.strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:8])
    folder.mkdir(parents=True, exist_ok=False)
    profile_path = folder / "system-voice.json"
    profile_path.touch(exist_ok=False)
    try:
        output = voice_request(args, key, "create_voice", target_model=args.target_model,
                               voice_prompt=args.prompt, preview_text=args.preview_text, prefix=args.prefix,
                               language_hints=[args.voice_language], parameters={"sample_rate": 24000, "response_format": "wav"})
    except Exception:
        profile_path.unlink(missing_ok=True)
        folder.rmdir()
        raise
    voice = output.get("voice_id")
    model = output.get("target_model", args.target_model)
    if not isinstance(voice, str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,200}", voice) or model != args.target_model:
        raise ToolError(tr("response"))
    profile = {"provider": "aliyun", "region": args.region, "workspace": args.workspace,
               "model": model, "voice": voice, "language": "Chinese" if args.voice_language == "zh" else "English"}
    print(tr("voice", voice))
    profile_path.write_text(json.dumps(profile, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(tr("profile", profile_path.resolve()))
    audio = output.get("preview_audio", {})
    try:
        encoded = audio.get("data", "")
        if not isinstance(encoded, str) or len(encoded) > (MAX_AUDIO + 2) // 3 * 4:
            raise ValueError()
        wav = base64.b64decode(encoded, validate=True)
        if len(wav) > MAX_AUDIO or not wav.startswith(b"RIFF") or wav[8:12] != b"WAVE":
            raise ValueError()
        audio_path = folder / "preview.wav"
        audio_path.write_bytes(normalize_wav(wav))
        print(tr("audio", audio_path.resolve()))
    except (ValueError, binascii.Error, AttributeError):
        print(tr("audio_failed"), file=sys.stderr)


def clone(args, key):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,79}", args.id):
        raise ToolError(tr("invalid", "id"))
    compact = re.sub(r"[^A-Za-z0-9]", "", args.id)
    prefix = args.prefix or (compact if len(compact) <= 10 else compact[:9] + compact[-1])
    if not re.fullmatch(r"[A-Za-z0-9]{1,10}", prefix):
        raise ToolError(tr("invalid", "prefix"))
    with args.file.open("rb") as source:
        audio = source.read(10 * 1024 * 1024 + 1)
    if not audio or len(audio) > 10 * 1024 * 1024:
        raise ToolError(tr("clone_audio"))
    try:
        with wave.open(str(args.file), "rb") as source:
            duration = source.getnframes() / source.getframerate()
            if source.getsampwidth() != 2 or source.getnchannels() not in (1, 2) or source.getframerate() < 16000 or not 5 <= duration <= 60:
                raise ToolError(tr("clone_audio"))
    except (wave.Error, EOFError):
        raise ToolError(tr("clone_audio")) from None
    folder = args.output / args.id
    folder.mkdir(parents=True, exist_ok=True)
    profile_path, receipt_path = folder / "system-voice.json", folder / "submission.json"
    if profile_path.exists() or receipt_path.exists():
        raise ToolError(tr("exists"))
    receipt = {"id": args.id, "prefix": prefix, "model": args.target_model, "region": args.region,
               "workspace": args.workspace, "source_sha256": hashlib.sha256(audio).hexdigest(),
               "source_seconds": round(duration, 3), "submitted_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
               "state": "submitting"}
    # Keep a durable receipt for ambiguous network failures; never silently repeat enrollment.
    with receipt_path.open("x", encoding="utf-8") as target:
        json.dump(receipt, target, ensure_ascii=False, indent=2)
    try:
        output = voice_request(args, key, "create_voice", target_model=args.target_model, prefix=prefix,
                               audio="data:audio/wav;base64," + base64.b64encode(audio).decode("ascii"),
                               language_hints=[args.voice_language], max_prompt_audio_length=min(30.0, duration),
                               enable_preprocess=args.preprocess)
    except Exception:
        receipt["state"] = "unknown"
        receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        raise
    voice = output.get("voice_id")
    if not isinstance(voice, str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,200}", voice):
        raise ToolError(tr("response"))
    print(tr("voice", voice))
    profile = {"id": args.id, "provider": "aliyun", "region": args.region, "workspace": args.workspace,
               "model": args.target_model, "voice": voice,
               "language": "Chinese" if args.voice_language == "zh" else "English"}
    # Persist the provider ID before doing any optional status checks or synthesis.
    profile_path.write_text(json.dumps(profile, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    receipt.update(state="created", voice_id=voice)
    receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(tr("clone_created", args.id))
    print(tr("profile", profile_path.resolve()))


def synthesize(args, key):
    if args.profile.stat().st_size > 8192:
        raise ToolError(tr("oversize"))
    try:
        profile = json.loads(args.profile.read_text(encoding="utf-8-sig"))
    except (ValueError, UnicodeError):
        raise ToolError(tr("invalid", "profile")) from None
    if not isinstance(profile, dict):
        raise ToolError(tr("invalid", "profile"))
    model, voice = profile.get("model"), profile.get("voice")
    if model not in MODELS or not isinstance(voice, str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,200}", voice):
        raise ToolError(tr("invalid", "profile model / voice"))
    workspace, region = profile.get("workspace", ""), profile.get("region", "beijing")
    if not isinstance(workspace, str) or workspace and not re.fullmatch(r"[A-Za-z0-9-]{1,80}", workspace) or region not in ("beijing", "singapore"):
        raise ToolError(tr("invalid", "profile region / workspace"))
    if not 1 <= len(args.text) <= 600:
        raise ToolError(tr("invalid", "text"))
    args.workspace, args.region = workspace, region
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("xb"):
        pass
    # Never synthesize twice after an ambiguous failure; metadata retains the original response.
    result = json_request(base_url(args) + "/api/v1/services/audio/tts/SpeechSynthesizer", key,
                          {"model": model, "input": {"text": args.text, "voice": voice, "format": "wav", "sample_rate": 24000}})
    output = result.get("output", {})
    audio = output.get("audio", {}) if isinstance(output, dict) else {}
    if not isinstance(audio, dict):
        raise ToolError(tr("response"))
    metadata = {"id": profile.get("id"), "model": model, "voice": voice, "text": args.text,
                "request_id": result.get("request_id"), "usage": result.get("usage", {}), "audio": audio}
    metadata_path = args.output.with_suffix(".json")
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if audio.get("url"):
        parsed = urllib.parse.urlsplit(audio["url"])
        if parsed.scheme not in ("http", "https") or not parsed.hostname or not parsed.hostname.endswith(".aliyuncs.com") or ".oss-" not in parsed.hostname or parsed.username or parsed.port or parsed.fragment:
            raise ToolError(tr("response"))
        url = urllib.parse.urlunsplit(parsed._replace(scheme="https"))
        wav = request(url, limit=MAX_AUDIO)
    else:
        try:
            encoded = audio.get("data", "")
            if not isinstance(encoded, str) or len(encoded) > (MAX_AUDIO + 2) // 3 * 4:
                raise ValueError()
            wav = base64.b64decode(encoded, validate=True)
        except (ValueError, binascii.Error):
            raise ToolError(tr("response")) from None
    if len(wav) > MAX_AUDIO or not wav.startswith(b"RIFF") or wav[8:12] != b"WAVE":
        raise ToolError(tr("response"))
    args.output.write_bytes(normalize_wav(wav))
    # Signed download URLs and inline audio are not needed once the WAV has been saved.
    metadata.pop("audio", None)
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(tr("audio", args.output.resolve()))


def upload(args, key):
    """Upload signed multipart directly to OSS; the file part must be last."""
    size = args.file.stat().st_size
    if not args.file.is_file() or size == 0 or size > MAX_UPLOAD:
        raise ToolError(tr("oversize"))
    suffix = args.file.suffix.lower()
    if suffix not in (".wav", ".mp3", ".m4a", ".flac", ".ogg", ".aac", ".pcm"):
        raise ToolError(tr("invalid", "audio file extension"))
    query = urllib.parse.urlencode({"action": "getPolicy", "model": args.model})
    policy = json_request(base_url(args, legacy=True) + "/api/v1/uploads?" + query, key).get("data", {})
    fields = {"OSSAccessKeyId": "oss_access_key_id", "Signature": "signature", "policy": "policy",
              "x-oss-object-acl": "x_oss_object_acl", "x-oss-forbid-overwrite": "x_oss_forbid_overwrite"}
    if not isinstance(policy, dict) or any(not isinstance(policy.get(k), str) for k in (*fields.values(), "upload_host", "upload_dir")):
        raise ToolError(tr("response"))
    parsed = urllib.parse.urlsplit(policy["upload_host"])
    if parsed.scheme != "https" or not parsed.hostname or not parsed.hostname.endswith(".aliyuncs.com") or ".oss-" not in parsed.hostname or parsed.username or parsed.port:
        raise ToolError(tr("response"))
    maximum = policy.get("max_file_size_mb", 0)
    if not isinstance(maximum, (int, float)) or size > maximum * 1024 * 1024:
        raise ToolError(tr("oversize"))
    filename = uuid.uuid4().hex + suffix
    object_key = policy["upload_dir"].rstrip("/") + "/" + filename
    values = {name: policy[source] for name, source in fields.items()}
    values.update(key=object_key, success_action_status="200")
    boundary = "system-voice-" + uuid.uuid4().hex
    parts = []
    for name, value in values.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode())
    with args.file.open("rb") as source:
        contents = source.read(MAX_UPLOAD + 1)
    if len(contents) != size:
        raise ToolError(tr("invalid", "file changed during upload"))
    parts.append(contents)
    parts.append(f"\r\n--{boundary}--\r\n".encode())
    request(policy["upload_host"], payload=b"".join(parts), method="POST",
            headers={"Content-Type": "multipart/form-data; boundary=" + boundary}, limit=65536)
    print("oss://" + object_key)
    print(tr("upload_note"))


def parser():
    result = argparse.ArgumentParser(description=tr("description"), epilog=tr("model_note"))
    result.add_argument("--lang", choices=("zh", "en"), default=LANG)
    result.add_argument("--region", choices=("beijing", "singapore"))
    result.add_argument("--workspace", help=tr("workspace"))
    result.add_argument("--config", type=Path, help=tr("config"))
    commands = result.add_subparsers(dest="command", required=True)
    create = commands.add_parser("design", help=tr("design"))
    create.add_argument("--prompt", required=True, help=tr("prompt"))
    create.add_argument("--preview-text", default="宿主，系统连接已经建立。新的任务已经解锁，请查看任务列表。", help=tr("preview"))
    create.add_argument("--prefix", default="system", help=tr("prefix"))
    create.add_argument("--target-model", choices=MODELS, default=MODELS[0])
    create.add_argument("--voice-language", choices=("zh", "en"), default="zh")
    create.add_argument("--output", type=Path, default=Path("voice-output"), help=tr("output"))
    cloning = commands.add_parser("clone", help=tr("clone"))
    cloning.add_argument("file", type=Path)
    cloning.add_argument("--id", required=True, help=tr("id"))
    cloning.add_argument("--prefix", help=tr("prefix"))
    cloning.add_argument("--target-model", choices=MODELS, default=MODELS[0])
    cloning.add_argument("--voice-language", choices=("zh", "en"), default="zh")
    cloning.add_argument("--preprocess", action="store_true")
    cloning.add_argument("--output", type=Path, default=Path("voice-output"), help=tr("output"))
    synthesis = commands.add_parser("synthesize", help=tr("synthesize"))
    synthesis.add_argument("--profile", type=Path, required=True)
    synthesis.add_argument("--text", default="宿主，系统连接已经建立。音色复刻已完成，新的任务已经解锁，请查看任务列表。")
    synthesis.add_argument("--output", type=Path, default=Path("voice-output/test.wav"))
    listing = commands.add_parser("list", help=tr("list"))
    listing.add_argument("--prefix", default="system")
    listing.add_argument("--page", type=int, default=0)
    for action in ("query", "delete"):
        command = commands.add_parser(action, help=tr(action))
        command.add_argument("voice_id")
    uploading = commands.add_parser("upload", help=tr("upload"))
    uploading.add_argument("file", type=Path)
    uploading.add_argument("--model", required=True)
    return result


def main():
    global LANG
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    language_parser = argparse.ArgumentParser(add_help=False)
    language_parser.add_argument("--lang", choices=("zh", "en"))
    selected, _ = language_parser.parse_known_args()
    LANG = selected.lang or ("zh" if (locale.getlocale()[0] or "").startswith("zh") else "en")
    args = parser().parse_args()
    try:
        config = {}
        if args.config:
            if args.config.stat().st_size > 8192:
                raise ToolError(tr("oversize"))
            try:
                config = json.loads(args.config.read_text(encoding="utf-8-sig"))
            except (ValueError, UnicodeError):
                raise ToolError(tr("invalid", "config")) from None
            if not isinstance(config, dict) or any(not isinstance(config.get(name, ""), str) for name in ("apiKey", "region", "workspace")):
                raise ToolError(tr("invalid", "config"))
        args.region = args.region or config.get("region") or "beijing"
        args.workspace = args.workspace or os.environ.get("DASHSCOPE_WORKSPACE_ID") or config.get("workspace", "")
        if args.region not in ("beijing", "singapore"):
            raise ToolError(tr("invalid", "region"))
        if args.workspace and not re.fullmatch(r"[A-Za-z0-9-]{1,80}", args.workspace):
            raise ToolError(tr("invalid", "workspace"))
        if args.command in ("design", "clone", "list", "query", "delete"):
            if args.region != "beijing":
                raise ToolError(tr("beijing"))
        if args.command == "delete" and input(tr("confirm")) != args.voice_id:
            print(tr("cancel"))
            return 0
        key = config.get("apiKey", "").strip() or os.environ.get("DASHSCOPE_API_KEY", "").strip() or getpass.getpass(tr("key")).strip()
        if not key or len(key) > 512 or any(c.isspace() for c in key):
            raise ToolError(tr("key_required"))
        if args.command == "design":
            design(args, key)
        elif args.command == "clone":
            clone(args, key)
        elif args.command == "synthesize":
            synthesize(args, key)
        elif args.command == "upload":
            upload(args, key)
        else:
            if args.command == "list":
                if args.page < 0 or not re.fullmatch(r"[A-Za-z0-9]{0,10}", args.prefix):
                    raise ToolError(tr("invalid", "page / prefix"))
                output = voice_request(args, key, "list_voice", prefix=args.prefix, page_index=args.page, page_size=20)
            else:
                if not re.fullmatch(r"[A-Za-z0-9_.-]{1,200}", args.voice_id):
                    raise ToolError(tr("invalid", "voice_id"))
                output = voice_request(args, key, args.command + "_voice", voice_id=args.voice_id)
            print(json.dumps(output, ensure_ascii=False, indent=2))
        return 0
    except ToolError as error:
        print(str(error), file=sys.stderr)
        return 1
    except OSError:
        print(tr("io"), file=sys.stderr)
        return 1
    except (KeyboardInterrupt, EOFError):
        print(tr("cancel"), file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
