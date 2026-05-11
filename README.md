# `.VOD` to MP4 Converter With Optional AI Enhancement

This folder contains a Python script that converts a `.vod` video file into a high-quality `.mp4`.

File:
- `vod_to_mp4_ai.py`

What it does:
- Converts `.vod` to `.mp4` with H.264 video and AAC audio
- Uses high-quality defaults: `CRF 16`, `preset slow`, `audio 320k`
- Can optionally enhance video frames with AI using Real-ESRGAN before encoding

## Requirements

Install FFmpeg and make sure `ffmpeg` and `ffprobe` are available in your `PATH`.

No extra Python package is required for basic conversion.

Python packages for AI enhancement:

```bash
pip install opencv-python realesrgan basicsr
```

You also need Real-ESRGAN model weights in your working directory, for example:
- `RealESRGAN_x2plus.pth`
- `RealESRGAN_x4plus.pth`

## Usage

Basic high-quality conversion:

```bash
python vod_to_mp4_ai.py input.vod output.mp4
```

If Windows does not recognize `python`, use:

```bash
py vod_to_mp4_ai.py input.vod output.mp4
```

AI-enhanced conversion:

```bash
python vod_to_mp4_ai.py input.vod output.mp4 --ai-enhance --ai-model RealESRGAN_x2plus --ai-outscale 2
```

Use a different output frame rate if needed:

```bash
python vod_to_mp4_ai.py input.vod output.mp4 --fps 30
```

## Notes

- If your `.vod` file contains unusual codecs, FFmpeg still needs to be able to decode it.
- AI enhancement is slower because frames are extracted, enhanced one by one, and then re-encoded.
- For very large files, make sure you have enough temporary disk space.
