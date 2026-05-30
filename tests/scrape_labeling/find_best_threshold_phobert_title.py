import argparse
from pathlib import Path

from evaluate_phobert_title import compute_metrics, load_posts


def parse_args():
    parser = argparse.ArgumentParser(description="Find the best threshold for the PhoBERT title classifier")
    parser.add_argument(
        "--input-json",
        default="tests/scrape_labeling/output_data/raw_data_merged_label.json",
        help="Path to JSON file that already contains p_it values",
    )
    parser.add_argument(
        "--start",
        type=float,
        default=0.0,
        help="Start threshold for the sweep",
    )
    parser.add_argument(
        "--end",
        type=float,
        default=1.0,
        help="End threshold for the sweep",
    )
    parser.add_argument(
        "--step",
        type=float,
        default=0.01,
        help="Step size for the threshold sweep",
    )
    return parser.parse_args()


def score_threshold(samples, threshold):
    tp = fp = tn = fn = 0

    for sample in samples:
        y_true = sample["label"]
        y_pred = 1 if sample["p_it"] >= threshold else 0

        if y_true == 1 and y_pred == 1:
            tp += 1
        elif y_true == 0 and y_pred == 1:
            fp += 1
        elif y_true == 0 and y_pred == 0:
            tn += 1
        else:
            fn += 1

    acc, precision, recall, f1 = compute_metrics(tp, fp, tn, fn)
    return {
        "threshold": threshold,
        "tp": tp,
        "fp": fp,
        "tn": tn,
        "fn": fn,
        "accuracy": acc,
        "precision": precision,
        "recall": recall,
        "f1": f1,
    }


def build_thresholds(start, end, step):
    thresholds = []
    current = start
    while current <= end + 1e-9:
        thresholds.append(round(current, 10))
        current += step
    return thresholds


def main():
    args = parse_args()

    input_json = Path(args.input_json)

    if not input_json.exists():
        raise FileNotFoundError(f"Input JSON not found: {input_json}")

    _, posts = load_posts(input_json)
    if not posts:
        print("No posts found in input JSON.")
        return

    labeled_samples = []
    for item in posts:
        label = item.get("label")
        try:
            y_true = int(label)
        except Exception:
            continue

        if y_true not in (0, 1):
            continue

        p_it = item.get("p_it")
        try:
            p_it = float(p_it)
        except Exception:
            continue

        labeled_samples.append({"label": y_true, "p_it": p_it})

    if not labeled_samples:
        print("No labeled samples available for threshold search.")
        return

    thresholds = build_thresholds(args.start, args.end, args.step)
    best_score = None

    print("Threshold sweep")
    print("-" * 72)
    print(f"{'threshold':>10}  {'acc':>8}  {'prec':>8}  {'recall':>8}  {'f1':>8}  {'tp':>4}  {'fp':>4}  {'tn':>4}  {'fn':>4}")

    for threshold in thresholds:
        score = score_threshold(labeled_samples, threshold)
        print(
            f"{threshold:10.2f}  {score['accuracy']:8.4f}  {score['precision']:8.4f}  {score['recall']:8.4f}  {score['f1']:8.4f}  "
            f"{score['tp']:4d}  {score['fp']:4d}  {score['tn']:4d}  {score['fn']:4d}"
        )

        ranking_key = (score["accuracy"], score["f1"], -threshold)
        if best_score is None or ranking_key > best_score["ranking_key"]:
            best_score = {"ranking_key": ranking_key, **score}

    print("-" * 72)
    print(
        "Best threshold   : "
        f"{best_score['threshold']:.2f} "
        f"(acc={best_score['accuracy']:.4f}, "
        f"f1={best_score['f1']:.4f}, "
        f"tp={best_score['tp']}, fp={best_score['fp']}, "
        f"tn={best_score['tn']}, fn={best_score['fn']})"
    )


if __name__ == "__main__":
    main()