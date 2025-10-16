#!/usr/bin/env python3
"""Convert raw pose landmark sequences into feature CSV rows.

The script expects a JSON file containing a list of samples. Each sample should
look like this:

    {
      "label": "PUSH_UP",
      "landmarks": [
        {"name": "LEFT_SHOULDER", "x": 0.45, "y": 0.50, "z": -0.1, "visibility": 0.9},
        ... (33 ML Kit landmarks total) ...
      ]
    }

Only the subset of landmarks required for the feature computation needs to be
provided; missing landmarks result in the sample being skipped. The generated
CSV can be fed into ``train_pose_classifier.py``.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

REQUIRED_LANDMARKS = {
    "LEFT_SHOULDER",
    "RIGHT_SHOULDER",
    "LEFT_ELBOW",
    "RIGHT_ELBOW",
    "LEFT_WRIST",
    "RIGHT_WRIST",
    "LEFT_HIP",
    "RIGHT_HIP",
    "LEFT_KNEE",
    "RIGHT_KNEE",
    "LEFT_ANKLE",
    "RIGHT_ANKLE",
}

FEATURE_NAMES = [
    "left_elbow_angle",
    "right_elbow_angle",
    "left_knee_angle",
    "right_knee_angle",
    "wrist_to_shoulder",
    "hip_to_knee",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Extract pose features from landmark JSON")
    parser.add_argument("--input", type=Path, required=True, help="JSON file with pose landmarks")
    parser.add_argument("--output", type=Path, required=True, help="Destination CSV path")
    parser.add_argument(
        "--min-visibility",
        type=float,
        default=0.5,
        help="Minimum landmark visibility required to keep a sample",
    )
    return parser.parse_args()


def load_samples(path: Path) -> List[Dict[str, object]]:
    with path.open("r", encoding="utf-8") as fp:
        data = json.load(fp)
    if not isinstance(data, list):
        raise ValueError("Expected the JSON file to contain a list of samples")
    return data


def landmark_map(landmarks: Iterable[Dict[str, object]]) -> Dict[str, Dict[str, float]]:
    mapping: Dict[str, Dict[str, float]] = {}
    for landmark in landmarks:
        name = str(landmark.get("name", "")).upper()
        if name:
            mapping[name] = {
                "x": float(landmark.get("x", 0.0)),
                "y": float(landmark.get("y", 0.0)),
                "z": float(landmark.get("z", 0.0)),
                "visibility": float(landmark.get("visibility", 0.0)),
            }
    return mapping


def vector(a: Dict[str, float], b: Dict[str, float]) -> Tuple[float, float, float]:
    return (b["x"] - a["x"], b["y"] - a["y"], b["z"] - a["z"])


def vector_length(v: Sequence[float]) -> float:
    return math.sqrt(v[0] ** 2 + v[1] ** 2 + v[2] ** 2)


def angle(a: Dict[str, float], b: Dict[str, float], c: Dict[str, float]) -> Optional[float]:
    ab = vector(b, a)
    cb = vector(b, c)
    len_ab = vector_length(ab)
    len_cb = vector_length(cb)
    if len_ab == 0 or len_cb == 0:
        return None
    dot = ab[0] * cb[0] + ab[1] * cb[1] + ab[2] * cb[2]
    cos_value = max(-1.0, min(1.0, dot / (len_ab * len_cb)))
    return math.degrees(math.acos(cos_value))


def average_visibility(points: Sequence[Dict[str, float]]) -> float:
    if not points:
        return 0.0
    return sum(point.get("visibility", 0.0) for point in points) / len(points)


def compute_features(mapping: Dict[str, Dict[str, float]], min_visibility: float) -> Optional[List[float]]:
    if not REQUIRED_LANDMARKS.issubset(mapping.keys()):
        return None

    visibilities = [mapping[name]["visibility"] for name in REQUIRED_LANDMARKS]
    if any(v < min_visibility for v in visibilities):
        return None

    left_shoulder = mapping["LEFT_SHOULDER"]
    right_shoulder = mapping["RIGHT_SHOULDER"]
    left_hip = mapping["LEFT_HIP"]
    right_hip = mapping["RIGHT_HIP"]

    torso_vector = vector(left_shoulder, left_hip)
    torso_length = vector_length(torso_vector)
    if torso_length == 0:
        torso_length = 1.0

    left_elbow_angle = angle(mapping["LEFT_SHOULDER"], mapping["LEFT_ELBOW"], mapping["LEFT_WRIST"])
    right_elbow_angle = angle(mapping["RIGHT_SHOULDER"], mapping["RIGHT_ELBOW"], mapping["RIGHT_WRIST"])
    left_knee_angle = angle(mapping["LEFT_HIP"], mapping["LEFT_KNEE"], mapping["LEFT_ANKLE"])
    right_knee_angle = angle(mapping["RIGHT_HIP"], mapping["RIGHT_KNEE"], mapping["RIGHT_ANKLE"])

    if None in (left_elbow_angle, right_elbow_angle, left_knee_angle, right_knee_angle):
        return None

    left_wrist = mapping["LEFT_WRIST"]
    right_wrist = mapping["RIGHT_WRIST"]
    left_knee = mapping["LEFT_KNEE"]
    right_knee = mapping["RIGHT_KNEE"]

    mid_shoulder_y = (left_shoulder["y"] + right_shoulder["y"]) / 2.0
    mid_wrist_y = (left_wrist["y"] + right_wrist["y"]) / 2.0
    mid_hip_y = (left_hip["y"] + right_hip["y"]) / 2.0
    mid_knee_y = (left_knee["y"] + right_knee["y"]) / 2.0

    wrist_to_shoulder = (mid_shoulder_y - mid_wrist_y) / torso_length
    hip_to_knee = (mid_hip_y - mid_knee_y) / torso_length

    return [
        float(left_elbow_angle),
        float(right_elbow_angle),
        float(left_knee_angle),
        float(right_knee_angle),
        float(wrist_to_shoulder),
        float(hip_to_knee),
    ]


def main() -> None:
    args = parse_args()
    samples = load_samples(args.input)
    args.output.parent.mkdir(parents=True, exist_ok=True)

    with args.output.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.writer(fp)
        writer.writerow(["label", *FEATURE_NAMES])
        skipped = 0
        kept = 0
        for sample in samples:
            label = str(sample.get("label", "")).strip()
            if not label:
                skipped += 1
                continue
            landmarks = sample.get("landmarks", [])
            mapping = landmark_map(landmarks)
            features = compute_features(mapping, args.min_visibility)
            if features is None:
                skipped += 1
                continue
            writer.writerow([label, *features])
            kept += 1
    print(f"Wrote {kept} samples to {args.output} (skipped {skipped})")


if __name__ == "__main__":
    main()
