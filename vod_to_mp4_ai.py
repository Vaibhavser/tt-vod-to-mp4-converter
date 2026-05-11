import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


def run_command(command: list[str]) -> None:
    process = subprocess.run(command, capture_output=True, text=True)
    if process.returncode != 0:
        raise RuntimeError(
            "Command failed:\n"
            f"{' '.join(command)}\n\n"
            f"STDOUT:\n{process.stdout}\n\n"
            f"STDERR:\n{process.stderr}"
        )


def run_command_output(command: list[str]) -> str:
    process = subprocess.run(command, capture_output=True, text=True)
    if process.returncode != 0:
        raise RuntimeError(
            "Command failed:\n"
            f"{' '.join(command)}\n\n"
            f"STDOUT:\n{process.stdout}\n\n"
            f"STDERR:\n{process.stderr}"
        )
    return process.stdout.strip()


def require_ffmpeg_tools() -> None:
    missing = [tool for tool in ("ffmpeg", "ffprobe") if shutil.which(tool) is None]
    if missing:
        raise EnvironmentError(
            "Missing required tools: "
            + ", ".join(missing)
            + ". Install FFmpeg and make sure it is in your PATH."
        )


def validate_paths(input_path: Path, output_path: Path) -> None:
    if not input_path.exists():
        raise FileNotFoundError(f"Input file not found: {input_path}")
    if input_path.is_dir():
        raise IsADirectoryError(f"Input path is a directory, not a file: {input_path}")
    if output_path.suffix.lower() != ".mp4":
        raise ValueError("Output file must use the .mp4 extension.")


def has_audio_stream(input_path: Path) -> bool:
    output = run_command_output(
        [
            "ffprobe",
            "-v",
            "error",
            "-select_streams",
            "a",
            "-show_entries",
            "stream=index",
            "-of",
            "csv=p=0",
            str(input_path),
        ]
    )
    return bool(output)


def build_transcode_command(
    input_path: Path,
    output_path: Path,
    crf: int,
    preset: str,
    audio_bitrate: str,
    fps: float | None,
) -> list[str]:
    command = [
        "ffmpeg",
        "-y",
        "-i",
        str(input_path),
    ]
    if fps is not None:
        command += ["-r", str(fps)]
    command += [
        "-map",
        "0:v:0",
        "-map",
        "0:a?",
        "-c:v",
        "libx264",
        "-preset",
        preset,
        "-crf",
        str(crf),
        "-pix_fmt",
        "yuv420p",
        "-movflags",
        "+faststart",
        "-c:a",
        "aac",
        "-b:a",
        audio_bitrate,
        str(output_path),
    ]
    return command


def extract_audio(input_path: Path, output_audio: Path) -> None:
    run_command(
        [
            "ffmpeg",
            "-y",
            "-i",
            str(input_path),
            "-vn",
            "-acodec",
            "pcm_s16le",
            str(output_audio),
        ]
    )


def extract_frames(input_path: Path, frames_dir: Path, fps: float | None) -> None:
    pattern = frames_dir / "frame_%08d.png"
    command = ["ffmpeg", "-y", "-i", str(input_path)]
    if fps is not None:
        command += ["-r", str(fps)]
    command.append(str(pattern))
    run_command(command)


def enhance_frames_with_realesrgan(
    input_dir: Path,
    output_dir: Path,
    model_name: str,
    outscale: int,
) -> None:
    try:
        import cv2
        from realesrgan import RealESRGANer
        from basicsr.archs.rrdbnet_arch import RRDBNet
    except ImportError as exc:
        raise ImportError(
            "AI enhancement requires the packages `opencv-python`, `realesrgan`, and "
            "`basicsr`. Install them first."
        ) from exc

    model_scales = {
        "RealESRGAN_x2plus": 2,
        "RealESRGAN_x4plus": 4,
    }
    if model_name not in model_scales:
        raise ValueError(
            f"Unsupported model '{model_name}'. Use one of: {', '.join(model_scales)}."
        )

    scale = model_scales[model_name]
    model = RRDBNet(
        num_in_ch=3,
        num_out_ch=3,
        num_feat=64,
        num_block=23,
        num_grow_ch=32,
        scale=scale,
    )

    weights_name = f"{model_name}.pth"
    try:
        enhancer = RealESRGANer(
            scale=scale,
            model_path=weights_name,
            model=model,
            tile=0,
            tile_pad=10,
            pre_pad=0,
            half=False,
        )
    except Exception as exc:
        raise RuntimeError(
            f"Could not initialize Real-ESRGAN with weights '{weights_name}'. "
            "Download the model weights into the working directory or provide them via "
            "the REAL_ESRGAN_MODEL_DIR environment variable supported by the package."
        ) from exc

    frame_paths = sorted(input_dir.glob("frame_*.png"))
    if not frame_paths:
        raise RuntimeError("No extracted frames were found to enhance.")

    output_dir.mkdir(parents=True, exist_ok=True)
    for frame_path in frame_paths:
        frame = cv2.imread(str(frame_path), cv2.IMREAD_COLOR)
        if frame is None:
            raise RuntimeError(f"OpenCV could not read frame: {frame_path}")
        enhanced_frame, _ = enhancer.enhance(frame, outscale=outscale)
        output_path = output_dir / frame_path.name
        if not cv2.imwrite(str(output_path), enhanced_frame):
            raise RuntimeError(f"OpenCV could not write enhanced frame: {output_path}")


def encode_from_frames(
    frames_dir: Path,
    audio_path: Path | None,
    output_path: Path,
    crf: int,
    preset: str,
    audio_bitrate: str,
    fps: float | None,
) -> None:
    pattern = frames_dir / "frame_%08d.png"
    command = ["ffmpeg", "-y"]
    if fps is not None:
        command += ["-framerate", str(fps)]
    command += ["-i", str(pattern)]
    if audio_path is not None:
        command += ["-i", str(audio_path)]
    command += [
        "-c:v",
        "libx264",
        "-preset",
        preset,
        "-crf",
        str(crf),
        "-pix_fmt",
        "yuv420p",
        "-movflags",
        "+faststart",
    ]
    if audio_path is not None:
        command += [
            "-c:a",
            "aac",
            "-b:a",
            audio_bitrate,
            "-shortest",
        ]
    command.append(str(output_path))
    run_command(command)


def convert_direct(
    input_path: Path,
    output_path: Path,
    crf: int,
    preset: str,
    audio_bitrate: str,
    fps: float | None,
) -> None:
    command = build_transcode_command(
        input_path=input_path,
        output_path=output_path,
        crf=crf,
        preset=preset,
        audio_bitrate=audio_bitrate,
        fps=fps,
    )
    run_command(command)


def convert_with_ai(
    input_path: Path,
    output_path: Path,
    crf: int,
    preset: str,
    audio_bitrate: str,
    fps: float | None,
    model_name: str,
    outscale: int,
) -> None:
    with tempfile.TemporaryDirectory(prefix="vod_to_mp4_ai_") as temp_dir:
        temp_root = Path(temp_dir)
        frames_dir = temp_root / "frames"
        enhanced_dir = temp_root / "enhanced_frames"
        audio_path = temp_root / "audio_track.wav"
        extracted_audio_path: Path | None = None

        frames_dir.mkdir(parents=True, exist_ok=True)
        extract_frames(input_path=input_path, frames_dir=frames_dir, fps=fps)
        if has_audio_stream(input_path):
            extract_audio(input_path=input_path, output_audio=audio_path)
            extracted_audio_path = audio_path
        enhance_frames_with_realesrgan(
            input_dir=frames_dir,
            output_dir=enhanced_dir,
            model_name=model_name,
            outscale=outscale,
        )
        encode_from_frames(
            frames_dir=enhanced_dir,
            audio_path=extracted_audio_path,
            output_path=output_path,
            crf=crf,
            preset=preset,
            audio_bitrate=audio_bitrate,
            fps=fps,
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Convert a .vod video file to high-quality MP4. Optionally enhance frames "
            "with Real-ESRGAN AI upscaling before encoding."
        )
    )
    parser.add_argument("input", type=Path, help="Path to the input .vod file.")
    parser.add_argument(
        "output",
        type=Path,
        help="Path to the output .mp4 file.",
    )
    parser.add_argument(
        "--crf",
        type=int,
        default=16,
        help="H.264 quality setting. Lower is better quality; default is 16.",
    )
    parser.add_argument(
        "--preset",
        default="slow",
        choices=["ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow", "slower", "veryslow"],
        help="FFmpeg x264 preset. Default is slow.",
    )
    parser.add_argument(
        "--audio-bitrate",
        default="320k",
        help="AAC audio bitrate. Default is 320k.",
    )
    parser.add_argument(
        "--fps",
        type=float,
        default=None,
        help="Optional output frame rate. If omitted, the source timing is preserved.",
    )
    parser.add_argument(
        "--ai-enhance",
        action="store_true",
        help="Enable AI frame enhancement using Real-ESRGAN before encoding.",
    )
    parser.add_argument(
        "--ai-model",
        default="RealESRGAN_x2plus",
        choices=["RealESRGAN_x2plus", "RealESRGAN_x4plus"],
        help="Real-ESRGAN model to use when --ai-enhance is enabled.",
    )
    parser.add_argument(
        "--ai-outscale",
        type=int,
        default=2,
        help="Upscaling factor passed to Real-ESRGAN. Default is 2.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    try:
        require_ffmpeg_tools()
        validate_paths(args.input, args.output)

        if args.ai_enhance:
            convert_with_ai(
                input_path=args.input,
                output_path=args.output,
                crf=args.crf,
                preset=args.preset,
                audio_bitrate=args.audio_bitrate,
                fps=args.fps,
                model_name=args.ai_model,
                outscale=args.ai_outscale,
            )
        else:
            convert_direct(
                input_path=args.input,
                output_path=args.output,
                crf=args.crf,
                preset=args.preset,
                audio_bitrate=args.audio_bitrate,
                fps=args.fps,
            )
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1

    print(f"Conversion complete: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
