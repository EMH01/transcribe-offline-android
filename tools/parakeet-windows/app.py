"""Local-only Windows file transcription using Loquia's Parakeet Q4_0 model."""
from __future__ import annotations

import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

from flask import Flask, jsonify, render_template, request

ROOT = Path(__file__).resolve().parent
MODEL = Path(os.environ.get("PARAKEET_MODEL", str(ROOT / "models" / "ggml-parakeet-tdt-0.6b-v3-q4_0.bin")))
CLI = os.environ.get("PARAKEET_CLI", "parakeet-cli")
FFMPEG = os.environ.get("FFMPEG", "ffmpeg")
ALLOWED = {".wav", ".mp3", ".m4a", ".aac", ".ogg", ".flac", ".opus", ".wma", ".mp4"}
MAX_BYTES = 250 * 1024 * 1024

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = MAX_BYTES


def executable(command: str) -> str:
    path = Path(command)
    if path.is_file():
        return str(path.resolve())
    resolved = shutil.which(command)
    if resolved:
        return resolved
    raise RuntimeError(f"No se encontró el ejecutable: {command}. Consulta el README.")


def extract_transcript(output: str) -> str:
    """Extract CLI text without including diagnostic output or segment metadata."""
    lines = [line.strip() for line in output.splitlines()]
    segments = []
    for line in lines:
        match = re.match(r'^Segment\s+\d+:\s+\[.*?\]\s+"(.*)"\s*$', line)
        if match:
            segments.append(match.group(1))
    if segments:
        return " ".join(segments).strip()
    excluded = ("Processing audio", "parakeet_decode:", "Segments (", "Segment ", "ggml_", "main:", "system_info:", "load_", "parakeet_", "error:", "warning:")
    candidates = [line for line in lines if line and not line.startswith(excluded) and not line.startswith("[")]
    return "\n".join(candidates).strip()


def transcribe(source: Path) -> str:
    if not MODEL.is_file():
        raise RuntimeError(f"Falta el modelo Q4_0: {MODEL}. Consulta el README.")
    ffmpeg = executable(FFMPEG)
    cli = executable(CLI)
    with tempfile.TemporaryDirectory(prefix="loquia-file-") as directory:
        wav = Path(directory) / "audio.wav"
        conversion = subprocess.run(
            [ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
             "-i", str(source), "-vn", "-ac", "1", "-ar", "16000",
             "-c:a", "pcm_s16le", str(wav)],
            capture_output=True, text=True, timeout=600, check=False,
        )
        if conversion.returncode != 0 or not wav.is_file():
            raise RuntimeError("No se pudo decodificar el audio. Comprueba el formato y el archivo.")
        if wav.stat().st_size <= 44:
            raise RuntimeError("El archivo no contiene audio válido.")
        result = subprocess.run(
            [cli, "-m", str(MODEL), "-f", str(wav), "-t", "4", "--no-gpu", "--print-segments"],
            capture_output=True, text=True, timeout=7200, check=False,
        )
        if result.returncode != 0:
            raise RuntimeError("Parakeet no pudo procesar el archivo. Revisa la instalación del ejecutable y el modelo.")
        transcript = extract_transcript(result.stdout)
        if not transcript:
            raise RuntimeError("El motor no devolvió texto. Comprueba si hay voz audible en el archivo.")
        return transcript


@app.get("/")
def index():
    return render_template("index.html")


@app.post("/transcribe")
def transcribe_upload():
    upload = request.files.get("audio")
    if upload is None or not upload.filename:
        return jsonify(error="Selecciona un archivo de audio."), 400
    suffix = Path(upload.filename).suffix.lower()
    if suffix not in ALLOWED:
        return jsonify(error="Formato no admitido. Prueba WAV, MP3, M4A, AAC, OGG, FLAC, OPUS, WMA o MP4."), 400
    try:
        with tempfile.TemporaryDirectory(prefix="loquia-upload-") as directory:
            source = Path(directory) / ("input" + suffix)
            upload.save(source)
            if source.stat().st_size == 0:
                return jsonify(error="El archivo está vacío."), 400
            return jsonify(text=transcribe(source))
    except (RuntimeError, subprocess.TimeoutExpired) as exc:
        return jsonify(error=str(exc)), 422


@app.errorhandler(413)
def too_large(_error):
    return jsonify(error="El archivo supera el límite de 250 MB."), 413


if __name__ == "__main__":
    # Intentionally not reachable from other computers.
    app.run(host="127.0.0.1", port=8765, debug=False)
