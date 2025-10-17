#!/usr/bin/env python3
"""Download workout videos/images defined in a manifest for pose model training.

The script is designed to help you build a legally compliant dataset for the
workout counter model.  Provide a JSON manifest describing each clip you have
permission to download, and the script will fetch the media into a local folder
and optionally extract frame images.  You are responsible for ensuring that
every listed URL grants you the right to download and reuse the material.

Manifest example (see ``ml/data_sources/sample_workout_sources.json``)::

    [
      {
        "id": "pushup_demo_1",
        "label": "PUSH_UP",
        "source": {
          "type": "youtube",
          "url": "https://www.youtube.com/watch?v=REPLACE_WITH_YOUR_VIDEO_ID",
          "start": 30.0,
          "end": 45.0
        },
        "notes": "Free-form comments about licensing or recording details"
      }
    ]

Supported source ``type`` values:

``direct``
    Plain HTTP(S) download (e.g. your own cloud storage, archive.org).
``youtube``
    Downloads through ``yt-dlp``.  Install with ``pip install yt-dlp`` before
    running the script.  Only list videos you are allowed to copy.

Optional ``start``/``end`` values (seconds) allow clipping a segment of the
original video.  Clipping requires ``ffmpeg`` to be available on ``PATH``.

Example usage::

    python ml/download_workout_media.py \
        --manifest ml/data_sources/my_sources.json \
        --video-dir ml/raw_media \
        --extract-frames --frame-dir ml/extracted_frames --frame-rate 2

When ``--extract-frames`` is enabled the script saves JPEG frames at roughly the
requested frame rate using OpenCV (``pip install opencv-python``).  Frames are
organized under ``<frame-dir>/<label>/<video-id>_00001.jpg`` so they can be fed
to downstream pose extraction tools.
"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List, Optional

import logging
import tempfile

try:
    import requests
except ImportError as exc:  # pragma: no cover - requests should be available but provide guidance otherwise
    raise SystemExit("This script requires the 'requests' package. Install via 'pip install requests'.") from exc


LOGGER = logging.getLogger("download_workout_media")


@dataclass
class Source:
    type: str
    url: str
    start: Optional[float] = None
    end: Optional[float] = None


@dataclass
class MediaItem:
    identifier: str
    label: str
    source: Source


class DownloadError(RuntimeError):
    """Raised when a media item cannot be downloaded."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Download workout media for training")
    parser.add_argument("--manifest", type=Path, required=True, help="JSON manifest describing media sources")
    parser.add_argument(
        "--video-dir",
        type=Path,
        default=Path("ml/raw_media"),
        help="Directory to store downloaded/processed video files",
    )
    parser.add_argument(
        "--extract-frames",
        action="store_true",
        help="Extract JPEG frames for each downloaded video (requires opencv-python)",
    )
    parser.add_argument(
        "--frame-dir",
        type=Path,
        default=Path("ml/extracted_frames"),
        help="Directory to store extracted frames when --extract-frames is set",
    )
    parser.add_argument(
        "--frame-rate",
        type=float,
        default=2.0,
        help="Approximate frame rate for extracted images (frames per second)",
    )
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="Re-download media even if the destination file already exists",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=60.0,
        help="HTTP timeout in seconds for direct downloads",
    )
    parser.add_argument(
        "--keep-temp",
        action="store_true",
        help="Preserve temporary download files for debugging",
    )
    parser.add_argument(
        "--log-level",
        default="INFO",
        choices=["DEBUG", "INFO", "WARNING", "ERROR"],
        help="Set logging verbosity",
    )
    return parser.parse_args()


def load_manifest(path: Path) -> List[MediaItem]:
    with path.open("r", encoding="utf-8") as fp:
        data = json.load(fp)
    if not isinstance(data, list):
        raise ValueError("Manifest must be a list of entries")

    items: List[MediaItem] = []
    for raw in data:
        identifier = str(raw.get("id", "")).strip()
        label = str(raw.get("label", "")).strip()
        source_data = raw.get("source", {})
        if not identifier or not label or not isinstance(source_data, dict):
            LOGGER.warning("Skipping malformed entry: %s", raw)
            continue
        source_type = str(source_data.get("type", "")).strip().lower()
        url = str(source_data.get("url", "")).strip()
        start = source_data.get("start")
        end = source_data.get("end")
        source = Source(type=source_type, url=url, start=start, end=end)
        items.append(MediaItem(identifier=identifier, label=label, source=source))
    return items


def ensure_directory(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def requests_download(url: str, destination: Path, timeout: float) -> None:
    LOGGER.info("Downloading %s -> %s", url, destination)
    with requests.get(url, stream=True, timeout=timeout) as response:
        response.raise_for_status()
        with destination.open("wb") as fp:
            for chunk in response.iter_content(chunk_size=1 << 20):
                if chunk:
                    fp.write(chunk)


def youtube_download(url: str, destination: Path) -> None:
    try:
        import yt_dlp  # type: ignore
    except ImportError as exc:  # pragma: no cover - optional dependency
        raise DownloadError(
            "yt-dlp is required for YouTube downloads. Install via 'pip install yt-dlp'"
        ) from exc

    LOGGER.info("Downloading via yt-dlp %s -> %s", url, destination)
    temp_dir = destination.parent
    ydl_opts = {
        "outtmpl": str(temp_dir / f"{destination.stem}.%(ext)s"),
        "format": "mp4/bestvideo[ext=mp4]+bestaudio[ext=m4a]/best",
        "quiet": True,
        "noprogress": True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=True)
        downloaded: Optional[str] = None
        if "requested_downloads" in info:
            # yt-dlp >= 2021.12 returns requested downloads entries
            requested = info.get("requested_downloads") or []
            if requested:
                downloaded = requested[0].get("filepath")
        if not downloaded:
            downloaded = ydl.prepare_filename(info)
    temp_path = Path(downloaded)
    if not temp_path.exists():
        raise DownloadError(f"yt-dlp reported download at {temp_path}, but file was not found")
    shutil.move(str(temp_path), destination)


def ffmpeg_available() -> bool:
    return shutil.which("ffmpeg") is not None


def clip_video(source: Path, destination: Path, start: Optional[float], end: Optional[float]) -> None:
    if start is None and end is None:
        shutil.move(str(source), destination)
        return
    if not ffmpeg_available():
        LOGGER.warning(
            "ffmpeg not found on PATH, skipping clip for %s. Copying original file instead.",
            destination.name,
        )
        shutil.move(str(source), destination)
        return
    args = [
        "ffmpeg",
        "-y",
        "-i",
        str(source),
    ]
    if start is not None:
        args.extend(["-ss", str(start)])
    if end is not None:
        args.extend(["-to", str(end)])
    args.extend(["-c", "copy", str(destination)])
    LOGGER.info("Clipping %s -> %s", source.name, destination.name)
    completed = subprocess.run(args, check=False, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if completed.returncode != 0:
        LOGGER.warning("ffmpeg failed to clip %s, falling back to untrimmed file", source.name)
        shutil.move(str(source), destination)
    else:
        source.unlink(missing_ok=True)


def download_item(item: MediaItem, video_dir: Path, overwrite: bool, timeout: float, keep_temp: bool) -> Path:
    ensure_directory(video_dir)
    destination = video_dir / f"{item.identifier}.mp4"
    if destination.exists() and not overwrite:
        LOGGER.info("Skipping %s; file already exists", destination)
        return destination

    tmp_dir = tempfile.TemporaryDirectory(prefix=f"download_{item.identifier}_")
    tmp_path = Path(tmp_dir.name) / "source_video"
    try:
        if item.source.type == "direct":
            if not item.source.url:
                raise DownloadError(f"Entry {item.identifier} missing direct download URL")
            requests_download(item.source.url, tmp_path, timeout)
        elif item.source.type == "youtube":
            if not item.source.url:
                raise DownloadError(f"Entry {item.identifier} missing YouTube URL")
            youtube_destination = tmp_path.with_suffix(".mp4")
            youtube_download(item.source.url, youtube_destination)
            tmp_path = youtube_destination
        else:
            raise DownloadError(
                f"Unsupported source type '{item.source.type}' for entry {item.identifier}."
                " Use 'direct' or 'youtube'."
            )

        clip_destination = tmp_path.with_suffix(".processed.mp4")
        clip_video(tmp_path, clip_destination, item.source.start, item.source.end)
        shutil.move(str(clip_destination), destination)
        LOGGER.info("Saved %s", destination)
        return destination
    finally:
        if keep_temp:
            LOGGER.info("Temporary files kept at %s", tmp_dir.name)
        else:
            tmp_dir.cleanup()


def extract_frames(video_path: Path, frame_dir: Path, label: str, frame_rate: float) -> int:
    try:
        import cv2  # type: ignore
    except ImportError as exc:  # pragma: no cover - optional dependency
        raise DownloadError(
            "OpenCV (opencv-python) is required for frame extraction. Install via 'pip install opencv-python'"
        ) from exc

    ensure_directory(frame_dir)
    label_dir = frame_dir / label
    ensure_directory(label_dir)

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise DownloadError(f"Failed to open {video_path} for frame extraction")

    fps = cap.get(cv2.CAP_PROP_FPS) or 0.0
    if fps <= 0:
        fps = frame_rate
    frame_interval = max(1, int(round(fps / frame_rate)))
    total_saved = 0
    frame_index = 0

    while True:
        success, frame = cap.read()
        if not success:
            break
        if frame_index % frame_interval == 0:
            frame_name = f"{video_path.stem}_{total_saved:05d}.jpg"
            frame_path = label_dir / frame_name
            if not cv2.imwrite(str(frame_path), frame):
                raise DownloadError(f"Failed to write frame {frame_path}")
            total_saved += 1
        frame_index += 1

    cap.release()
    LOGGER.info("Extracted %d frames from %s", total_saved, video_path.name)
    return total_saved


def process_manifest(items: Iterable[MediaItem], args: argparse.Namespace) -> None:
    for item in items:
        try:
            video_path = download_item(item, args.video_dir, args.overwrite, args.timeout, args.keep_temp)
            if args.extract_frames:
                extract_frames(video_path, args.frame_dir, item.label, args.frame_rate)
        except DownloadError as exc:
            LOGGER.error("Failed to process %s: %s", item.identifier, exc)
        except requests.HTTPError as exc:
            LOGGER.error("HTTP error while downloading %s: %s", item.identifier, exc)
        except Exception as exc:  # pragma: no cover - defensive logging for unexpected issues
            LOGGER.exception("Unexpected error while processing %s: %s", item.identifier, exc)


def main() -> None:
    args = parse_args()
    logging.basicConfig(level=getattr(logging, args.log_level), format="%(levelname)s: %(message)s")
    items = load_manifest(args.manifest)
    if not items:
        LOGGER.warning("Manifest %s did not contain any valid entries", args.manifest)
        return
    LOGGER.info("Processing %d media items", len(items))
    process_manifest(items, args)


if __name__ == "__main__":
    main()
