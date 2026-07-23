# Baseline TF-IDF + KMeans (không graph) so với Graph+Content Fusion

**Mục đích:** định lượng graph+content fusion (production, xem
`src/features/feature_pipeline.py` + `src/clustering/trainer.py`) tốt hơn một
baseline content-only *bao nhiêu*, thay vì chỉ giả định. Dùng cho hạng mục
"Cài 1 baseline đơn giản để so sánh" trong roadmap Tháng 1.

**Code:** [`experiments/run_tfidf_kmeans_baseline.py`](run_tfidf_kmeans_baseline.py)
(+ 2 cột mới `content`/`description`/`requirement` trong
[`src/data/neo4j_loader.py`](../src/data/neo4j_loader.py) để baseline có
content thật thay vì chỉ title ngắn).

**Trạng thái kết quả dưới đây: SƠ BỘ**, chạy ở chế độ `--offline` (title-only)
trên snapshot cache cục bộ, KHÔNG phải chế độ `--live` (mặc định, dùng
`article.content` + `job.description/requirement` fetch trực tiếp từ Neo4j) —
xem mục "Giới hạn" và "Cách tái tạo" bên dưới để chạy lại lấy số liệu chính
thức. Toàn bộ số liệu ở đây là output thật của lần chạy này (không phải số ước
lượng).

---

## 1. Phương pháp

Để so sánh công bằng, baseline dùng **chung** với Stage 02/03 (model production):

- Cùng snapshot tag, cùng `canonicalize_technology_snapshot` + `noise_filter`
  (`params.yaml: features.noise_filter`) → cùng tech universe (N).
- Cùng `edges_tech_related_tech` (ground truth RELATED_TO đã curate thủ công)
  và cùng hàm đánh giá (`src/clustering/evaluator.py`:
  `evaluate_clustering`, `compute_related_split_ratio`).
- Cùng `tfidf_min_df` / `tfidf_max_features` / `kmeans.n_clusters_grid` từ
  `params.yaml` — không tự bịa hyperparameter riêng cho baseline.

**Điểm khác biệt cốt lõi với model production:**

| | Baseline (mới) | Production champion |
|---|---|---|
| Graph embedding | **Không** (không node2vec, không graph_stats, không skill-jaccard) | node2vec (64d, client-side networkx+gensim) |
| "Content" | TF-IDF trên **text tự nhiên thật**: tên tech + title/content Article MENTIONS + title/description/requirement Job REQUIRES | name embedding (e5, 768d→64d PCA) + article embedding aggregate |
| Bag-of-relation TF-IDF | **Không dùng** — 2 block `company_tfidf`/`job_tfidf` hiện có trong `graph_features.py` thực ra là bag-of-company-id / bag-of-(job-title+level)-token, tức mã hoá **quan hệ đồ thị** (công ty/job nào dùng tech), không phải nội dung văn bản → không tính là "content-only" | Có dùng (`job_tfidf`, weight 2.0) |
| Fusion / dimensionality | Không có fusion — TF-IDF matrix (max 500 chiều) đưa thẳng vào KMeans | Concat nhiều block → scale riêng từng block → UMAP 32d |
| Clustering | KMeans (grid `n_clusters_grid` = production) | HDBSCAN (grid search, chọn theo DBCV) |
| Chọn best trial | Silhouette (DBCV không áp dụng được cho KMeans — không phải density-based, không có `relative_validity_`) | DBCV |

---

## 2. Dữ liệu dùng cho lần chạy này

Snapshot tag `2026-05-13` (bản đầy đủ cục bộ dùng tag
`2026-05-13__ablation_baseline_all` — copy y hệt dữ liệu gốc, `best_metrics.json`
của 2 tag này giống hệt nhau byte-for-byte, đã verify).

| Thống kê | Giá trị |
|---|---|
| Technology (raw, trong snapshot) | 85 |
| Technology (sau canonicalize alias) | 84 |
| Technology (sau `noise_filter`, = N thật đưa vào clustering) | **40** |
| Article | 3,621 |
| Job | 94 |
| Article -[:MENTIONS]-> Tech (edges) | 7,235 |
| Job -[:REQUIRES]-> Tech (edges) | 340 |
| Tech -[:RELATED_TO]-> Tech (ground truth, sau canonicalize) | **2** |

**Lưu ý quan trọng:** N=40 là quy mô rất nhỏ (params.yaml tune cho ~250 tech ở
quy mô production), và chỉ còn **2 cạnh RELATED_TO** sau canonicalize+noise
filter → không đủ để tính `related_pairs_split_ratio` cho **cả 2 model**
(`related_pairs_evaluated=0` ở cả baseline lẫn champion). Đây chính là lý do
task song song "mở rộng crawl để tăng N thật" quan trọng — không chỉ tăng N
cho clustering ổn hơn, mà còn để metric RELATED_TO (metric bán giám sát duy
nhất hệ thống có) thực sự dùng được.

**Corpus text (chế độ `--offline`, title-only):**

| Thống kê | Giá trị |
|---|---|
| Tech chỉ có tên (không Article/Job nào mention) | 2 / 40 (5.0%) |
| Số từ trung bình / document | 1,730 |
| Số từ min / max | 1 / 16,946 |
| TF-IDF vocab size (max_features cấu hình = 500) | 500 |
| TF-IDF `min_df` dùng thực tế | 2 (không cần fallback) |

Số từ trung bình cao (1,730) dù chỉ dùng title — vì nhiều tech được hàng trăm
Article cùng mention (7,235 edges / 40 tech ≈ 180 title/tech), nên tín hiệu
title-only ở dataset này khá hơn dự kiến ban đầu, dù vẫn là các cụm từ ngắn lặp
lại chứ không phải văn xuôi đầy đủ.

---

## 3. Kết quả

### 3.1. Baseline — grid search KMeans (5 trial, grid = `params.yaml: clustering.kmeans.n_clusters_grid`)

| k (n_clusters) | Silhouette | Davies-Bouldin | Calinski-Harabasz | min/median/max cluster size |
|---|---|---|---|---|
| **20** (⭐ best) | **0.1715** | 0.8013 | 3.80 | 1 / 1.5 / 5 |
| 24 | 0.1543 | 0.6636 | 3.86 | 1 / 1.0 / 5 |
| 28 | 0.1423 | 0.5404 | 4.37 | 1 / 1.0 / 4 |
| 32 | 0.1192 | 0.4348 | 5.18 | 1 / 1.0 / 4 |
| 36 | 0.0987 | 0.2527 | 8.32 | 1 / 1.0 / 3 |

Silhouette **giảm đơn điệu** khi k tăng trong toàn bộ grid test — dấu hiệu k
tối ưu thật sự nằm **ngoài** grid này (thấp hơn 20), vì grid được tune cho quy
mô ~250 tech, không phải 40 tech. Xem mục Giới hạn.

### 3.2. Baseline (best, k=20) vs Production Champion

| Metric | Baseline (TF-IDF+KMeans) | Champion (graph+content fusion, HDBSCAN) | Chênh lệch |
|---|---|---|---|
| n_clusters | 20 | 4 | — |
| noise_ratio | 0.0 (KMeans không có noise) | 0.317 | — |
| **Silhouette** ↑ tốt hơn | 0.1715 | **0.4035** | **+0.232 tuyệt đối (champion gấp ~2.35 lần)** |
| **Calinski-Harabasz** ↑ tốt hơn | 3.80 | **19.56** | **champion gấp ~5.1 lần** |
| Davies-Bouldin ↓ tốt hơn | 0.8013 | 0.8114 | gần như ngang nhau (baseline nhỉnh hơn chút) |
| DBCV | N/A (không áp dụng cho KMeans) | 0.2477 | — |
| related_pairs_evaluated | 0 | 0 | không đủ ground-truth để so sánh (xem §2) |

CSV đầy đủ: [`results/tfidf_kmeans_baseline_2026-05-13.csv`](results/tfidf_kmeans_baseline_2026-05-13.csv)
(thư mục `results/` bị gitignore — chỉ tồn tại local, tự tái tạo bằng lệnh ở §6).

---

## 4. Diễn giải (khách quan — nêu cả bằng chứng ủng hộ lẫn không ủng hộ)

**Ủng hộ giả thuyết "fusion tốt hơn":**
- **Silhouette** (đo mức độ 1 điểm gần cụm của nó hơn cụm khác, cùng đo được
  cho cả 2 model) của champion cao hơn baseline **~2.35 lần** (0.4035 vs
  0.1715) — cụm của model production tách bạch rõ hơn hẳn so với chỉ dùng
  TF-IDF thuần.
- **Calinski-Harabasz** (tỷ lệ phương sai liên-cụm / nội-cụm, càng cao càng
  tốt) của champion cao hơn **~5.1 lần** (19.56 vs 3.80) — đây là chênh lệch
  lớn nhất trong 3 metric, ủng hộ mạnh cho giả thuyết.

**Không ủng hộ / cần lưu ý — không nên bỏ qua để giữ tính khách quan cho luận
án:**
- **Davies-Bouldin gần như ngang nhau** (0.801 baseline vs 0.811 champion,
  baseline thậm chí nhỉnh hơn 1 chút). Một phần lý do: baseline sinh ra 20 cụm
  rất nhỏ (median size 1.5, nhiều cụm chỉ 1 tech) trong khi champion có 4 cụm
  lớn hơn nhiều (noise_ratio 31.7%) — Davies-Bouldin nhạy với số lượng/kích
  thước cụm theo cách không tuyến tính, nên so sánh trực tiếp giữa 2 cấu hình
  cụm rất khác nhau (20 cụm nhỏ vs 4 cụm lớn) qua chỉ số này **kém tin cậy
  hơn** Silhouette/CH ở đây.
- **RELATED_TO split ratio — chưa thể dùng để so sánh** với dữ liệu hiện tại
  (0 cặp đánh giá được ở cả 2 model, do chỉ còn 2 cạnh RELATED_TO sau lọc noise
  trên N=40 tech). Đây là metric "ground truth" duy nhất hệ thống có, nên kết
  luận hiện tại **chưa có** xác nhận bán giám sát, chỉ dựa trên 2 metric nội
  tại (Silhouette, CH).

**Kết luận sơ bộ (cho dữ liệu hiện tại, N=40, chế độ offline title-only):**
Có bằng chứng rõ ràng trên 2/3 metric nội tại rằng graph+content fusion tạo ra
cụm tách bạch và cô đọng hơn hẳn so với baseline TF-IDF+KMeans thuần (Silhouette
gấp 2.35×, Calinski-Harabasz gấp 5.1×), nhưng Davies-Bouldin không phân biệt rõ
2 model, và RELATED_TO — metric đối chiếu ground-truth — chưa đủ dữ liệu để
tham chiếu. Cần chạy lại ở chế độ `--live` (content thật) và trên N lớn hơn
(sau khi mở rộng crawl) để có kết luận đủ mạnh cho luận án.

---

## 5. Giới hạn (đọc trước khi trích số liệu vào luận án)

1. **Chế độ `--offline` (title-only), chưa phải `--live`.** Sandbox chạy thí
   nghiệm này không có `NEO4J_URI`/`NEO4J_PASSWORD` thật trong `.env` nên
   không fetch được `article.content`/`job.description`/`requirement` (đã có
   sẵn trong Neo4j, ghi bởi `data-platform/gold/neo4j_job_sync.py` +
   `neo4j_article_sync.py`, nhưng snapshot cũ chưa từng lưu). Số liệu "chính
   thức" cho luận án nên lấy từ `--live` (mặc định, không cần flag) — xem §6.
2. **N=40 rất nhỏ.** `params.yaml` được tune cho quy mô production (~250
   tech, comment trong file). Ở N=40, KMeans `n_clusters_grid=[20,24,28,32,36]`
   (kế thừa nguyên từ production) tạo ra rất nhiều cụm 1-2 phần tử — không
   thực sự có ý nghĩa cụm. Nên chạy thêm 1 grid mở rộng xuống thấp hơn (vd
   `[3,5,8,12,16]`) cho baseline khi N còn nhỏ, và nhắc lại toàn bộ thí nghiệm
   sau khi hạng mục "mở rộng crawl" tăng N thật lên.
3. **RELATED_TO ground truth quá thưa** (2 cạnh sau lọc, trên tổng ~70 cạnh đã
   curate ở quy mô đầy đủ theo tài liệu hệ thống) — không đánh giá được ở
   N=40. Cần N lớn hơn để nhiều cạnh RELATED_TO "sống sót" qua noise filter.
4. Baseline và champion được đánh giá trên **2 không gian đặc trưng khác nhau
   hoàn toàn** (TF-IDF 500 chiều thô vs UMAP 32 chiều đã fusion) — đây là so
   sánh "pipeline tốt nhất vs pipeline tốt nhất" (đúng tinh thần ablation
   study), không phải so sánh cùng 1 không gian, nên Silhouette/DB/CH không
   nên đọc như con số tuyệt đối mà chỉ nên đọc như xếp hạng tương đối.

---

## 6. Cách tái tạo (để lấy số liệu chính thức)

```bash
cd services/ml-clustering

# Chế độ --live (mặc định, khuyến nghị cho số liệu chính thức) — cần
# NEO4J_URI/NEO4J_USERNAME/NEO4J_PASSWORD thật trong .env ở project root:
python -m experiments.run_tfidf_kmeans_baseline --params params.yaml

# Chế độ --offline (không cần Neo4j, title-only — dùng số liệu ở báo cáo này):
python -m experiments.run_tfidf_kmeans_baseline --params params.yaml --offline
```

Output: bảng so sánh in ra stdout + CSV tại
`experiments/results/tfidf_kmeans_baseline_<tag>.csv`.

Sau khi hạng mục "mở rộng crawl (ITviec/TopDev/VietnamWorks)" hoàn tất và
snapshot mới được lấy (`tag` mới trong `params.yaml`), chạy lại toàn bộ lệnh
trên với `--live` để có: (a) N lớn hơn, (b) content thật thay vì title-only,
(c) nhiều khả năng RELATED_TO split ratio tính được — 3 điều kiện cần để kết
luận đủ mạnh cho luận án.
