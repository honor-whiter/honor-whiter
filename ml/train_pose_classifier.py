#!/usr/bin/env python3
"""Train a simple distance-based pose classifier for workout detection.

The training pipeline expects a CSV dataset with the following header:

    label,left_elbow_angle,right_elbow_angle,left_knee_angle,right_knee_angle,\
    wrist_to_shoulder,hip_to_knee

Angles are provided in degrees (0-180). The ratio features should already be
normalized by torso length, typically landing in the [-1, 1] range. The script
computes per-feature statistics, converts angles to the [0, 1] range, and then
standardizes all features before calculating a prototype (mean vector) for each
class label. The resulting classifier definition is saved as JSON for use
on-device.

Example usage:

    python ml/train_pose_classifier.py \
        --input ml/data/sample_pose_dataset.csv \
        --output android/JumpHeightRecorder/app/src/main/assets/workout_classifier.json

The produced JSON contains feature statistics, normalized class prototypes, and
metadata describing the training run.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Sequence, Tuple

FEATURE_NAMES = [
    "left_elbow_angle",
    "right_elbow_angle",
    "left_knee_angle",
    "right_knee_angle",
    "wrist_to_shoulder",
    "hip_to_knee",
]

ANGLE_FEATURES = {
    "left_elbow_angle",
    "right_elbow_angle",
    "left_knee_angle",
    "right_knee_angle",
}


@dataclass
class Sample:
    label: str
    features: List[float]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train pose classifier")
    parser.add_argument("--input", type=Path, required=True, help="CSV file with pose features")
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="Destination JSON file for the trained classifier",
    )
    parser.add_argument(
        "--min-count",
        type=int,
        default=2,
        help="Minimum number of samples required per class",
    )
    return parser.parse_args()


def load_dataset(path: Path) -> List[Sample]:
    samples: List[Sample] = []
    with path.open("r", newline="", encoding="utf-8") as fp:
        reader = csv.DictReader(fp)
        missing = [name for name in FEATURE_NAMES if name not in reader.fieldnames]
        if missing:
            raise ValueError(f"Dataset missing required columns: {missing}")
        for row in reader:
            label = row.get("label", "").strip()
            if not label:
                continue
            features: List[float] = []
            for name in FEATURE_NAMES:
                value = float(row[name])
                if name in ANGLE_FEATURES:
                    features.append(value / 180.0)
                else:
                    features.append(value)
            samples.append(Sample(label=label, features=features))
    if not samples:
        raise ValueError("Dataset is empty")
    return samples


def compute_feature_stats(samples: Sequence[Sample]) -> Tuple[List[float], List[float]]:
    transposed: List[List[float]] = [[] for _ in FEATURE_NAMES]
    for sample in samples:
        for idx, value in enumerate(sample.features):
            transposed[idx].append(value)
    means: List[float] = []
    stds: List[float] = []
    for values in transposed:
        means.append(statistics.fmean(values))
        if len(values) > 1:
            std = statistics.stdev(values)
        else:
            std = 1.0
        if math.isclose(std, 0.0, abs_tol=1e-6):
            std = 1.0
        stds.append(std)
    return means, stds


def normalize_features(features: Sequence[float], means: Sequence[float], stds: Sequence[float]) -> List[float]:
    normalized: List[float] = []
    for value, mean, std in zip(features, means, stds):
        normalized.append((value - mean) / std)
    return normalized


def compute_class_prototypes(samples: Sequence[Sample], means: Sequence[float], stds: Sequence[float], min_count: int) -> Dict[str, List[float]]:
    grouped: Dict[str, List[List[float]]] = defaultdict(list)
    for sample in samples:
        grouped[sample.label].append(normalize_features(sample.features, means, stds))

    prototypes: Dict[str, List[float]] = {}
    for label, vectors in grouped.items():
        if len(vectors) < min_count:
            raise ValueError(f"Not enough samples for label '{label}'. Found {len(vectors)}, require >= {min_count}")
        transposed: List[List[float]] = [[] for _ in FEATURE_NAMES]
        for vector in vectors:
            for idx, value in enumerate(vector):
                transposed[idx].append(value)
        prototypes[label] = [statistics.fmean(values) for values in transposed]
    return prototypes


def build_metadata(samples: Sequence[Sample]) -> Dict[str, object]:
    counts: Dict[str, int] = defaultdict(int)
    for sample in samples:
        counts[sample.label] += 1
    return {
        "total_samples": len(samples),
        "per_label": counts,
        "feature_names": FEATURE_NAMES,
        "angle_features": sorted(ANGLE_FEATURES),
    }


def train_classifier(dataset_path: Path, output_path: Path, min_count: int) -> None:
    samples = load_dataset(dataset_path)
    means, stds = compute_feature_stats(samples)
    prototypes = compute_class_prototypes(samples, means, stds, min_count)
    metadata = build_metadata(samples)

    classifier_definition = {
        "feature_means": means,
        "feature_stds": stds,
        "feature_names": FEATURE_NAMES,
        "prototypes": prototypes,
        "metadata": metadata,
        "version": 1,
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as fp:
        json.dump(classifier_definition, fp, indent=2, sort_keys=True)
    print(f"Saved classifier to {output_path} ({len(prototypes)} classes)")


def main() -> None:
    args = parse_args()
    train_classifier(args.input, args.output, args.min_count)


if __name__ == "__main__":
    main()
