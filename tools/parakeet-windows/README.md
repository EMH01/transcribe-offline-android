# Transcripción local de archivos en Windows (experimental)

Herramienta independiente de la APK oficial de Loquia. Interfaz HTML en `http://127.0.0.1:8765` con un servidor Python **solo local**. No requiere conexión durante la transcripción y no sube audio a servicios externos.

## Fidelidad a Loquia

Utiliza el **mismo modelo convertido** Parakeet TDT 0.6B v3 **Q4_0** que la aplicación Android y el runtime Parakeet de **ggml-org/whisper.cpp**. La entrada se convierte a PCM mono de 16 kHz y se usa un máximo de cuatro hilos CPU. A diferencia de Loquia Android, esta versión experimental **no replica todavía** el recorte de silencio `VoiceActivityTrimmer` ni el corrector de vocabulario personalizado. Por tanto, la calidad puede diferir: usar el mismo modelo no garantiza resultados idénticos.

## Requisitos Windows 10/11 (64 bits)

- Python 3.10+ y pip
- FFmpeg, con `ffmpeg.exe` disponible en PATH
- `parakeet-cli.exe` compilado desde [ggml-org/whisper.cpp](https://github.com/ggml-org/whisper.cpp/tree/master/examples/parakeet-cli) (con sus DLL necesarias accesibles)
- Modelo `ggml-parakeet-tdt-0.6b-v3-q4_0.bin` desde [ggml-org/parakeet-GGUF](https://huggingface.co/ggml-org/parakeet-GGUF)

Puedes compilar el ejecutable con Visual Studio C++ Build Tools y CMake:

```powershell
git clone https://github.com/ggml-org/whisper.cpp.git
cd whisper.cpp
cmake -B build -S .
cmake --build build --config Release --target parakeet-cli
```

La ruta habitual del ejecutable será `build\bin\Release\parakeet-cli.exe`. Copia también las DLL que requiera o mantén el directorio de compilación. Puedes descargar el modelo Q4_0 mediante la CLI de Hugging Face:

```powershell
pip install -U huggingface_hub
hf download ggml-org/parakeet-GGUF ggml-parakeet-tdt-0.6b-v3-q4_0.bin --local-dir models
```

## Ejecutar

Desde `tools/parakeet-windows`:

```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:PARAKEET_CLI = "C:\ruta\a\parakeet-cli.exe"
$env:PARAKEET_MODEL = (Resolve-Path ".\models\ggml-parakeet-tdt-0.6b-v3-q4_0.bin").Path
python app.py
```

Abre `http://127.0.0.1:8765` en el navegador, elige el archivo y pulsa **Transcribir**. Copia el resultado o guárdalo en TXT. El servidor atiende una transcripción a la vez de forma práctica; para archivos muy largos la espera puede ser elevada.

Formatos de entrada: WAV, MP3, M4A, AAC, OGG, FLAC, OPUS, WMA y MP4, sujetos a los códecs de FFmpeg. Límite de subida: 250 MB. No se necesitan permisos de micrófono ni claves API.

**Estado:** prototipo sin validación en Windows real. No distribuir como producto terminado hasta comprobar la compilación, extracción del texto y rendimiento con audios de prueba. No se modifica la APK ni el código Android oficial.
