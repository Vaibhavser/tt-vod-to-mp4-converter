# `TT VOD To MP4 Converter`

This repository now contains two converters:

- `vod_to_mp4_ai.py`: desktop Python converter for `.vod` to high-quality `.mp4`
- `android-app/`: Android Studio project for on-device `.vod` to `.mp4` export

What the desktop tool does:
- Converts `.vod` to `.mp4` with H.264 video and AAC audio
- Uses high-quality defaults: `CRF 16`, `preset slow`, `audio 320k`
- Can optionally enhance video frames with AI using Real-ESRGAN before encoding

What the Android app does:
- Lets you pick a `.vod` or other video file from storage
- Converts it to `.mp4` on-device using Media3 Transformer
- Tracks export progress and lets you share the finished MP4
- Uses high-quality on-device transcoding, but does not yet include on-device AI upscaling

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

## Android App

The Android project lives in `android-app/`.

Open it in Android Studio, let Gradle sync, then run the app on a device with Android 6.0 or newer.

Important note:
- The Android app uses Google's Media3 Transformer API for reliable on-device MP4 export.
- The desktop Python script remains the place where the optional AI enhancement path is implemented.
