import argparse
import importlib
import json
import re
from pathlib import Path

import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

try:
    underthesea_mod = importlib.import_module("underthesea")
    ner = underthesea_mod.ner
    word_tokenize = underthesea_mod.word_tokenize
except Exception:
    ner = None
    word_tokenize = None


def parse_args():
    parser = argparse.ArgumentParser(description="Evaluate PhoBERT title classifier on labeled JSON data")
    parser.add_argument(
        "--model-path",
        default="tests/phobert_title_classifier_best",
        help="Path to fine-tuned PhoBERT classifier directory",
    )
    parser.add_argument(
        "--input-json",
        default="tests/scrape_labeling/output_data/raw_data_merged_label.json",
        help="Path to labeled JSON file",
    )
    parser.add_argument("--threshold", type=float, default=0.5, help="P(IT) threshold for positive prediction")
    parser.add_argument(
        "--max-len",
        type=int,
        default=256,
        help="Max token length used by tokenizer",
    )
    parser.add_argument(
        "--find-best-threshold",
        action="store_true",
        help="Scan thresholds from 0.00 to 1.00 and print the best one based on labeled samples",
    )
    return parser.parse_args()


def preprocess_title(text: str) -> str:
    if not text or not isinstance(text, str):
        return ""

    if ner is not None:
        try:
            entities = ner(text)
            for word, _pos, _chunk, ner_tag in reversed(entities):
                if ner_tag in ("B-PER", "I-PER", "B-ORG", "I-ORG"):
                    text = text.replace(word, "name", 1)
                elif ner_tag in ("B-LOC", "I-LOC"):
                    text = text.replace(word, "loc", 1)
        except Exception:
            pass

    text = re.sub(r"\d+[.,]?\d*\s*%", "percent", text)
    text = re.sub(r"\d{1,2}[/-]\d{1,2}[/-]\d{2,4}", "date", text)
    text = re.sub(r"\d{1,2}[/-]\d{2,4}", "date", text)
    text = re.sub(r"[Nn]ăm\s+\d{4}", "date", text)
    text = re.sub(r"[Tt]háng\s+\d{1,2}", "date", text)
    text = re.sub(r"[Qq]uý\s+\d", "date", text)
    text = re.sub(r"\d+[.,]?\d*", "number", text)
    text = re.sub(r"[.,;:!?\"\'\'()\[\]{}\-–—…\"\"\'\'`]", " ", text)

    if word_tokenize is not None:
        try:
            text = word_tokenize(text, format="text")
        except Exception:
            pass

    text = text.lower().strip()
    text = re.sub(r"\s+", " ", text)
    return text


def load_posts(input_path: Path):
    data = json.loads(input_path.read_text(encoding="utf-8"))
    posts = data.get("post_detail", [])
    return data, posts


def compute_metrics(tp, fp, tn, fn):
    total = tp + fp + tn + fn
    acc = (tp + tn) / total if total else 0.0
    precision = tp / (tp + fp) if (tp + fp) else 0.0
    recall = tp / (tp + fn) if (tp + fn) else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else 0.0
    return acc, precision, recall, f1


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


def main():
    args = parse_args()

    model_path = Path(args.model_path)
    input_json = Path(args.input_json)

    if not model_path.exists():
        raise FileNotFoundError(f"Model path not found: {model_path}")
    if not input_json.exists():
        raise FileNotFoundError(f"Input JSON not found: {input_json}")

    data, posts = load_posts(input_json)
    if not posts:
        print("No posts found in input JSON.")
        return

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    tokenizer = AutoTokenizer.from_pretrained(str(model_path))
    model = AutoModelForSequenceClassification.from_pretrained(str(model_path))
    model.to(device)
    model.eval()

    tp = fp = tn = fn = 0

    labeled_count = 0
    unlabeled_count = 0
    labeled_samples = []

    for item in posts:
        title = item.get("title", "")

        processed = preprocess_title(title)
        inputs = tokenizer(
            processed,
            padding="max_length",
            truncation=True,
            max_length=args.max_len,
            return_tensors="pt",
        ).to(device)

        with torch.no_grad():
            logits = model(**inputs).logits
            probs = torch.softmax(logits, dim=-1)[0]

        p_it = float(probs[1].item())
        y_pred = 1 if p_it >= args.threshold else 0
        item["p_it"] = round(p_it, 6)
        item["pred_label"] = y_pred

        label = item.get("label")
        try:
            y_true = int(label)
        except Exception:
            unlabeled_count += 1
            continue

        if y_true not in (0, 1):
            unlabeled_count += 1
            continue

        labeled_count += 1
        labeled_samples.append({"label": y_true, "p_it": p_it})

        if y_true == 1 and y_pred == 1:
            tp += 1
        elif y_true == 0 and y_pred == 1:
            fp += 1
        elif y_true == 0 and y_pred == 0:
            tn += 1
        else:
            fn += 1

    acc, precision, recall, f1 = compute_metrics(tp, fp, tn, fn)

    best_threshold_info = None
    if args.find_best_threshold:
        if labeled_samples:
            print("\nThreshold sweep")
            print("-" * 72)
            print(f"{'threshold':>10}  {'acc':>8}  {'prec':>8}  {'recall':>8}  {'f1':>8}  {'tp':>4}  {'fp':>4}  {'tn':>4}  {'fn':>4}")

            best_score = None
            for step in range(0, 101):
                threshold = step / 100
                score = score_threshold(labeled_samples, threshold)
                print(
                    f"{threshold:10.2f}  {score['accuracy']:8.4f}  {score['precision']:8.4f}  {score['recall']:8.4f}  {score['f1']:8.4f}  "
                    f"{score['tp']:4d}  {score['fp']:4d}  {score['tn']:4d}  {score['fn']:4d}"
                )

                ranking_key = (score["accuracy"], score["f1"], -threshold)
                if best_score is None or ranking_key > best_score["ranking_key"]:
                    best_score = {"ranking_key": ranking_key, **score}

            best_threshold_info = best_score
            print("-" * 72)
            print(
                "Best threshold   : "
                f"{best_threshold_info['threshold']:.2f} "
                f"(acc={best_threshold_info['accuracy']:.4f}, "
                f"f1={best_threshold_info['f1']:.4f}, "
                f"tp={best_threshold_info['tp']}, fp={best_threshold_info['fp']}, "
                f"tn={best_threshold_info['tn']}, fn={best_threshold_info['fn']})"
            )
        else:
            print("\nThreshold sweep skipped: no labeled samples available.")

    data["post_detail"] = posts
    data["evaluation_summary"] = {
        "model_path": str(model_path),
        "threshold": args.threshold,
        "total_posts": len(posts),
        "evaluated_labeled_samples": labeled_count,
        "unlabeled_or_invalid_samples": unlabeled_count,
        "confusion_matrix": {"tp": tp, "fp": fp, "tn": tn, "fn": fn},
        "metrics": {
            "accuracy": round(acc, 6),
            "precision_it": round(precision, 6),
            "recall_it": round(recall, 6),
            "f1_it": round(f1, 6),
        },
    }
    if best_threshold_info is not None:
        data["evaluation_summary"]["best_threshold_search"] = {
            "start": 0.0,
            "end": 1.0,
            "step": 0.01,
            "best_threshold": round(best_threshold_info["threshold"], 2),
            "metrics": {
                "accuracy": round(best_threshold_info["accuracy"], 6),
                "precision": round(best_threshold_info["precision"], 6),
                "recall": round(best_threshold_info["recall"], 6),
                "f1": round(best_threshold_info["f1"], 6),
            },
            "confusion_matrix": {
                "tp": best_threshold_info["tp"],
                "fp": best_threshold_info["fp"],
                "tn": best_threshold_info["tn"],
                "fn": best_threshold_info["fn"],
            },
        }
    input_json.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    print("=" * 60)
    print("PhoBERT Title Classifier Evaluation")
    print("=" * 60)
    print(f"Model path        : {model_path}")
    print(f"Input JSON        : {input_json}")
    print(f"Threshold P(IT)   : {args.threshold:.2f}")
    print(f"Total posts       : {len(posts)}")
    print(f"Evaluated samples : {labeled_count}")
    print(f"Unlabeled/invalid : {unlabeled_count}")
    print("-" * 60)
    print(f"Confusion Matrix  : TP={tp} | FP={fp} | TN={tn} | FN={fn}")
    print(f"Accuracy          : {acc:.4f}")
    print(f"Precision (IT=1)  : {precision:.4f}")
    print(f"Recall    (IT=1)  : {recall:.4f}")
    print(f"F1-score  (IT=1)  : {f1:.4f}")
    print("-" * 60)
    print(f"Updated JSON with p_it/pred_label: {input_json}")


if __name__ == "__main__":
    main()
