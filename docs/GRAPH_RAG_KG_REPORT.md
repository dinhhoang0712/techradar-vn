# Triển khai và Đánh giá: Adaptive Hybrid Graph RAG + Knowledge Graph

> Báo cáo triển khai và đánh giá cho luận án "Graph RAG + Knowledge Graph", áp dụng lên
> `services/ai-rag-core` (Chat AI) và `data-platform` (Knowledge Graph quality). Toàn bộ số
> liệu trong tài liệu này lấy từ dữ liệu thật (Neo4j/Postgres/MLflow đang chạy), không suy đoán.
>
> Ngày thực hiện: 2026-07-24.

## 1. Bối cảnh và phạm vi

Hệ thống Chat AI (`services/ai-rag-core`) trước cải tiến khớp với kiến trúc RAG cơ bản: mọi truy vấn đều chạy đồng thời vector search + graph search (không điều kiện), graph traversal chỉ 1 hop, ngữ cảnh đưa vào LLM là văn bản phẳng (không triple/JSON-LD), reranking chỉ áp dụng cho kết quả vector search. Khảo sát code phát hiện thêm 6/8 câu Cypher trong `graph_queries.py` khớp nhầm quan hệ `HIRES_FOR` (dữ liệu cũ, không còn writer nào ghi) thay vì `POSTED_BY` (writer thật đang hoạt động).

Phạm vi triển khai chia 2 nhánh, đúng với 2 vế tên đề tài:

- **Graph RAG** (thuộc `ai-rag-core`, chỉ Chat AI): bộ chọn chiến lược dựa trên luật (rule-based Strategy Selector), mở rộng đồ thị đa bước thật, tận dụng điểm PageRank/Louvain đã có sẵn từ backend Java, hợp nhất reranking đa nguồn.
- **Knowledge Graph** (thuộc `data-platform`): phân loại category cho Technology, công cụ kiểm tra chất lượng đồ thị (KG Health Audit).

**Cố ý loại khỏi phạm vi** (ghi nhận là hướng phát triển tương lai, không triển khai): chunked embeddings, trích xuất thực thể/quan hệ bằng LLM (thay pipeline dictionary/regex hiện tại), entity resolution cho Company/Job (ví dụ "FPT" vs "FPT Software").

## 2. Triển khai

### 2.1 Sửa lỗi nền tảng (Phase 0)

**Bug 1 — quan hệ chết `HIRES_FOR`:** writer Kafka thật (`Neo4jExtractionWriter.java`) và batch sync (`data-platform`) chỉ ghi `[:POSTED_BY]`. Xác nhận trên dữ liệu thật hiện tại: **0** cạnh `HIRES_FOR`, **907** cạnh `POSTED_BY` (đúng bằng tổng số Job). 3/6 câu Cypher dùng `MATCH` bắt buộc trên `HIRES_FOR` — nghĩa là các câu tra cứu theo công ty (`JOBS_BY_COMPANY`, `JOBS_BY_LOCATION`, `JOBS_BY_TITLE_AND_COMPANY`) **luôn trả về 0 dòng** trên toàn bộ dữ liệu thật trước khi sửa. Đã sửa cả 6 câu sang khớp `[:POSTED_BY|HIRES_FOR]`.

**Bug 2 — sai tên thuộc tính tiêu đề job (phát hiện mới, trong phiên làm việc này):** có **2 writer độc lập** cùng ghi node `:Job` nhưng đặt tên property khác nhau — `Neo4jExtractionWriter.java` (đường Kafka, hiếm khi chạy trót lọt) ghi `title`; `neo4j_job_sync.py` (đường batch từ Postgres, chạy mỗi giờ, **901/907 node thật**) ghi `name`. Toàn bộ 6 câu Cypher chỉ đọc `j.title` → **tiêu đề công việc trả về `NULL` cho gần như 100% dữ liệu thật** (đã xác minh trực tiếp: truy vấn "FPT" trả về tiêu đề `NULL` ở mọi dòng). Đã sửa bằng `coalesce(j.title, j.name)` ở cả 6 câu (đọc lẫn lọc từ khoá), xác minh lại cho kết quả tiêu đề thật ("Senior AI Engineer", "Data Engineer - Up to $2000"...).

### 2.2 Strategy Selector — bộ chọn chiến lược dựa trên luật, theo năng lực (capability-based)

Module mới `strategy_selector.py`: từ thực thể trích xuất được (`tech_names`, `company_names`, `job_titles`, `locations`) và tín hiệu ý định câu hỏi (2 tập regex mới: `ANALYTICS_INTENT_PATTERNS`, `MULTIHOP_INTENT_PATTERNS`), suy ra 3 quyết định **độc lập, không loại trừ nhau**:

- `use_graph` — có thực thể nào không
- `graph_expansion_depth` (0/1/2) — 2 nếu có tín hiệu so sánh/đa bước, ngược lại 1
- `use_sql_analytics` — có thực thể tech VÀ có tín hiệu phân tích/xu hướng

Đây là điểm thiết kế quan trọng cho luận án: **capability-based, không phải type-based** — một câu hỏi có thể kích hoạt đồng thời nhiều năng lực (ví dụ "Xu hướng lương Java" bật cả `use_graph` lẫn `use_sql_analytics`), thay vì phải phân loại vào đúng 1 nhóm cố định (Salary/Comparison/Analytics). Có cờ bật/tắt (`strategy_selector_enabled`) để so sánh trước/sau (ablation).

### 2.3 Mở rộng đồ thị đa bước + graph-aware ranking

Câu Cypher mới `subgraph_expand_query(depth, limit)` (viết dưới dạng hàm vì `depth` phải nội suy chuỗi — Neo4j không cho tham số hoá `*1..N`), lan truyền 1-2 hop từ thực thể gốc, trả về triple phẳng kèm `hop` (khoảng cách) và `pagerank_score`. Tận dụng lại điểm PageRank/Louvain **đã được tính và lưu sẵn bởi backend Java** (`Neo4jGraphAnalyticsAdapter`, qua endpoint admin `/admin/graph-analytics/rebuild`) — trước đây chỉ dùng để vẽ đồ thị ở frontend, chưa từng được retrieval đọc tới.

Trong quá trình viết câu này gặp và sửa 2 lỗi thật: (1) `ORDER BY` tham chiếu biến đã ra khỏi scope sau `RETURN DISTINCT` → phải project `pagerank_score` thành cột riêng; (2) `coalesce()` không xử lý được `NaN` (chỉ xử lý `NULL`) — dữ liệu thật có node `pagerank_score = NaN` thật (node không có cạnh nào trong graph projection GDS dùng để tính) → phải dùng `CASE WHEN ... isNaN(...)`.

### 2.4 Serialization ngữ cảnh: triple + JSON-LD, nhóm theo hop

`graph_serializer.py` sinh JSON-LD tối giản cho response API; `prompt_builder.py` thêm khối `_build_subgraph_block()` nhóm triple theo `hop` trước khi render cho LLM (phân biệt "liên quan trực tiếp" vs "mở rộng 2 bước"), thay vì một danh sách phẳng không cấu trúc.

### 2.5 Hợp nhất reranking đa nguồn

`context_ranker.py` rerank riêng từng loại nguồn (bài viết / job / công ty / phân tích SQL) bằng cross-encoder đã có sẵn (`reranker.rerank()`), thay vì chỉ rerank kết quả vector search như trước — vì điểm cross-encoder không hiệu chỉnh được giữa các miền văn bản khác nhau (đoạn bài viết dài vs. một dòng thống kê ngắn).

### 2.6 Knowledge Graph: phân loại category + KG Health Audit

Bổ sung bảng `dp_tech_category` (Postgres, migration Flyway mới), mở rộng prompt LLM sẵn có trong `tech_dedup.py` để phân loại đồng thời với gộp trùng tên, thêm script backfill một lần cho toàn bộ catalog cũ. Script mới `kg_health_audit.py` (data-platform) tự động hoá đúng loại phát hiện tạo ra bug HIRES_FOR: quan hệ chết, node mồ côi, độ phủ property, tên trùng chỉ khác hoa/thường.

## 3. Đánh giá

### 3.1 Phương pháp

Đánh giá theo ma trận ablation 4 biến thể (bật/tắt từng phần bằng config-flag trong cùng 1 codebase, không diff qua git checkout), chạy trên cùng 13 câu hỏi test, log vào MLflow (experiment `rag_evaluation`).

Do quota miễn phí của cả Groq và Gemini cạn trong phiên làm việc (xác nhận qua run MLflow `ragas_eval_baseline` — trạng thái **FAILED**, chỉ đánh giá được 5/13 câu trước khi hết quota), đã xây dựng thêm **chế độ đánh giá cục bộ không dùng LLM** (`EVAL_METRICS_MODE=local`, mặc định) — dùng word-overlap cho faithfulness và cosine similarity embedding cho relevancy, làm proxy thay cho RAGAS thật, **giữ nguyên** code RAGAS làm tuỳ chọn (`EVAL_METRICS_MODE=ragas`) khi quota được cấp lại.

`ground_truth` cho `context_precision`/`context_recall` (13 câu) vừa được soạn bằng cách tra trực tiếp Postgres/Neo4j (độc lập với code retriever đang được đánh giá, để tránh vòng lặp tự-chấm-điểm-chính-mình) — đây là **bản nháp cần người có domain knowledge duyệt lại** trước khi dùng số liệu chính thức, đặc biệt các câu liên quan công ty có nhiều pháp nhân trùng tên (VD "FPT" khớp 6 tên công ty khác nhau trong dữ liệu — xem 3.3).

### 3.2 Kết quả định lượng (local proxy metrics, chạy thật ngày hôm nay)

| Biến thể | Selector | Expansion | Rerank | answered_rate | avg_latency (ms) | faithfulness | relevancy | %dùng graph | %dùng SQL analytics | avg triples |
|---|---|---|---|---|---|---|---|---|---|---|
| Baseline | ✗ | ✗ | ✗ | 0.923 | 7 086 | 0.477 | 0.893 | 0 % | 0 % | 0 |
| +Selector | ✓ | ✗ | ✗ | **1.000** | 8 883 | 0.421 | 0.899 | 92.3 % | 30.8 % | 0 |
| +Selector+Expansion | ✓ | ✓ | ✗ | 0.923 | 13 928 | 0.435 | 0.897 | 92.3 % | 30.8 % | 50.4 |
| Full (+Rerank) | ✓ | ✓ | ✓ | 0.923 | 15 725 | **0.549** | **0.909** | 92.3 % | 30.8 % | 50.4 |

**Nhận xét trung thực:**
- Selector một mình làm **giảm nhẹ faithfulness** (0.477→0.421) — nhiều ngữ cảnh graph/SQL hơn không tự động tốt hơn nếu chưa được rerank/sắp xếp hợp lý; đây đúng là lý do Phase 3 (unified rerank) cần thiết, không phải suy đoán.
- Faithfulness chỉ thực sự tăng rõ rệt (+0.11 so với baseline) khi **có rerank** (biến thể Full) — cho thấy đóng góp chính đến từ việc lọc/sắp xếp ngữ cảnh, không chỉ từ việc lấy thêm dữ liệu.
- Độ trễ tăng đơn điệu theo từng tính năng bật thêm: 7.1s → 8.9s → 13.9s → 15.7s — chi phí thật của graph expansion (Cypher đa bước) là rõ ràng, cần cân nhắc trade-off latency/chất lượng trong luận án, không chỉ báo cáo điểm chất lượng một chiều.
- `answered_rate` đạt 100% chỉ ở biến thể Selector — biến thể Expansion/Full có 1/13 câu không trả lời được (có thể do timeout hoặc lỗi khi tải quá nhiều ngữ cảnh), cần ghi nhận là hạn chế, chưa điều tra nguyên nhân gốc trong phiên này.
- Đây là **proxy cục bộ, không phải RAGAS thật** — number tuyệt đối không nên trích dẫn như kết quả RAGAS chuẩn trong luận án; nên trình bày rõ là "chỉ số proxy cục bộ" và bổ sung RAGAS thật khi có quota.

### 3.3 Kết quả Knowledge Graph Audit (trạng thái hiện tại, chạy thật — cập nhật sau vòng audit thứ 2)

| Chỉ số | Giá trị hiện tại |
|---|---|
| Quan hệ "chết"/lạ (không thuộc writer đang hoạt động) | 0 |
| Node Technology/Company mồ côi | 0 |
| Độ phủ `category` | 95.6 % (390/408 node) |
| Độ phủ `pagerank_score` | 99.3 %, **dùng được** (không NaN): 86.0 % |
| Nhóm tên Technology trùng chỉ khác hoa/thường | **0** (đã tự động gộp qua `tech_dedup.py` — trước đó 8 nhóm, tổng Technology giảm 418→408) |
| Job rác từ crawl bị chặn (TopCV anti-bot) | **0** (đã dọn — trước đó 48/907, ~5.3%; xem 3.3.1) |
| Nhóm Company nghi trùng cùng tổ chức khác tên pháp lý | 11 (heuristic; phát hiện ở Python + review/merge có kiểm soát qua admin UI Java `kgreview` — xem 3.3.2) |
| Cạnh `USES` (Company→Technology) | **3018** (đã sửa — trước đó 46; xem 3.3.3) |

#### 3.3.1 Bug mới phát hiện + đã dọn: 48 Job rác từ crawl bị chặn

Quét sâu hơn phần "trùng lặp" phát hiện: **48/907 Job node (~5.3%)** không phải tin tuyển dụng thật mà là trang lỗi/captcha do crawler TopCV bị chặn (anti-bot/WAF) — title = `"Sorry, you have been blocked"` (36 node) hoặc domain trần `"www.topcv.vn"` (12 node), company/salary/description đều rỗng. Đã xử lý 2 tầng:
- **Root-cause**: `silver/processor.py` thêm `_is_blocked_page_job()` — chặn từ gốc trước khi ghi Postgres.
- **Dọn dữ liệu cũ**: script một lần `gold/cleanup_garbage_jobs.py` — chạy thật, xoá 48 node khỏi Neo4j + đánh dấu 48 dòng Postgres `status='invalid'`. Idempotent, xác nhận lần chạy thứ 2 trả về 0.
- Thêm check `garbage_jobs` vào `kg_health_audit.py` để phát hiện sớm nếu tái diễn.

#### 3.3.2 Company near-duplicate — phát hiện (Python) + review/merge có kiểm soát (Java, `kgreview`)

Mở rộng `kg_health_audit.py` thêm `_check_company_near_duplicates()`: bóc bỏ boilerplate pháp lý (Công Ty/TNHH/Cổ Phần/Chi Nhánh/Trách Nhiệm Hữu Hạn viết đầy đủ...) rồi so khớp phần lõi theo **word-boundary** (không phải substring thô — thử nghiệm cho thấy substring thô gây nhiễu nặng: 140 nhóm toàn từ tiếng Việt phổ biến, hoặc bắt nhầm cặp trùng ký tự ngẫu nhiên như "Insmart"/"VinSmart"). Kết quả trên dữ liệu thật: **11 nhóm** (Nasani, FPT Software, Viễn Thông FPT, Reeracoen, LG CNS, One Mount, Bưu Điện Việt Nam, De Heus...). Heuristic này có giới hạn đã ghi rõ trong code: không bắt hết mọi biến thể (VD không gộp được cả 6 biến thể "FPT" thành 1 nhóm).

**Cập nhật quan trọng:** phát hiện 1 module Java hoàn chỉnh `apps/backend/.../features/kgreview/` (port song song, không phải do tôi viết trong phiên này — comment trong `Neo4jCompanyDuplicateAdapter.java` ghi rõ "Java port of `data-platform/gold/kg_health_audit.py`'s `_check_company_near_duplicates`") xây một admin review workflow đầy đủ:
- `GET/POST /admin/kg-review/company-duplicates` — liệt kê nhóm nghi trùng (tính live từ Neo4j, không lưu) + merge **có xác nhận của admin qua UI**, không tự động — đúng tinh thần "chỉ phát hiện, người duyệt mới gộp" đã thống nhất, nhưng có giao diện thật thay vì chỉ 1 JSON report.
- `GET/POST /admin/kg-review/tech-aliases` — approve/reject cho `dp_tech_alias_review_queue` (bảng đã tồn tại từ `tech_dedup.py` Giai đoạn B nhưng trước đó chưa rõ có UI duyệt hay không).

Java port này còn **phát hiện đúng 1 bug thật trong code Python của tôi**: `_check_company_near_duplicates()` duyệt cặp bằng so sánh lexicographic (`if name_a >= name_b: continue`) — 2 Company **trùng hệt tên** thoả điều kiện `>=` nên bị bỏ qua, không bao giờ được ghép cặp, dù trùng tên y hệt là tín hiệu rõ ràng hơn cả biến thể pháp nhân. Đã sửa: duyệt theo index (i<j) + track theo `id` node (không chỉ theo tên, vì gộp theo tên bằng `set` sẽ làm 2 node vật lý khác nhau nhưng trùng tên bị hiện thành 1 dòng duy nhất) — khớp đúng cách `Neo4jCompanyDuplicateAdapter.java` đã làm. Xác nhận lại bằng test mới + chạy thật, không phá kết quả 11 nhóm hiện có.

**Lưu ý:** bản thân test suite của module Java `kgreview` (`Neo4jCompanyDuplicateAdapterTest`, `Neo4jGraphMergeAdapterTest`) đang có lỗi test có sẵn (`UnfinishedStubbingException`/`UnnecessaryStubbingException`) — xác nhận không liên quan tới thay đổi của tôi, thuộc về phần code của phiên làm việc khác, chưa sửa trong báo cáo này.

#### 3.3.3 Bug mới phát hiện + đã sửa: `USES` đói dữ liệu nghiêm trọng

Phát hiện (qua audit quan hệ `USES`, không thuộc nhóm "trùng lặp" nhưng cùng đợt điều tra): `_COMPANY_USES_TECH` trong `neo4j_enricher.py` trước đây **chỉ** suy ra từ Article co-mention — nhưng chỉ **6/425** Company từng được 1 Article nhắc tên, 419 công ty còn lại chỉ tồn tại qua Job posting nên không bao giờ có cạnh `USES`. Hệ quả: 46 cạnh `USES` thay vì hàng nghìn — cả `ai-rag-core`'s `COMPANIES_USING_TECH` (câu hỏi "công ty nào dùng React?") lẫn `ml-clustering`'s feature huấn luyện cluster đều đói dữ liệu.

Đã sửa: thêm `_COMPANY_USES_TECH_FROM_JOB` — tín hiệu `Company<-[:POSTED_BY|HIRES_FOR]-Job-[:REQUIRES]->Technology`, cùng pattern mà `Neo4jCompanyRepository`/`COMPANY_INSIGHT_CONTEXT` (Java) đã tin dùng — MERGE chung vào cùng cạnh `USES`. Xác minh trực tiếp: **46 → 3018 cạnh**, và câu hỏi "công ty nào dùng React?" giờ trả kết quả thật thay vì rỗng.

#### 3.3.4 Bug mới phát hiện + đã sửa: Entity Extraction (Company/Location) chưa từng hoạt động

Điều tra tiếp root cause của 3.3.3 (vì sao chỉ 6/425 Company từng được Article nhắc tên) dẫn tới phát hiện sâu hơn, ở phía Java (`apps/backend`): `EntityExtractionService.extractEntities()` — service chịu trách nhiệm đọc Article/Job text và tách ra tên công nghệ/công ty/địa điểm — **luôn trả về `ORG` và `LOC` rỗng**, bất kể nội dung văn bản là gì. `Neo4jExtractionWriter.java` đã có sẵn code chờ ghi `MENTIONS(Article→Company)`/`MENTIONS(Article→Location)` từ 2 field này, nhưng chưa bao giờ thực thi được vì đầu vào luôn rỗng — đây là nguyên nhân gốc thực sự của con số 6/425, không phải vì Article hiếm khi nhắc tên công ty.

Phân biệt rõ 2 loại thực thể trong hệ thống:
- **Technology**: trích xuất bằng dictionary + regex (~130 từ khoá + alias tiếng Việt) — hoạt động, nhưng giới hạn bởi danh sách cứng, không phải NLP thật.
- **Company/Location từ Job posting**: lấy trực tiếp field crawler có sẵn (`company_name`, `location`) — không cần NLP, hoạt động tốt, đây là nguồn chính nuôi 425 Company node hiện có.
- **Company/Location từ Article text**: cần NLP để đọc câu văn — đây là phần bị hỏng.

Đã sửa theo hướng dictionary-based (nhất quán với cách Technology đã làm, không phải NER/LLM thật — việc đó vẫn nằm ngoài phạm vi như đã ghi ở mục 1):
- **Location**: thêm `LOCATION_KEYWORDS` — dictionary tĩnh 63 tỉnh/thành + alias phổ biến (TP.HCM/Sài Gòn, HN...). An toàn vì danh sách này cố định, không cần cache.
- **Company**: thêm `CompanyNameCache` (mới) — cache tên Company đã biết trong Neo4j (refresh mỗi 15 phút, cùng pattern với `TechAliasCache`), `EntityExtractionService.extractOrg()` so khớp theo word-boundary (không dùng regex — tên công ty có thể chứa ký tự đặc biệt bất kỳ, không an toàn để biên dịch thành regex).

**Giới hạn có chủ đích**: cách tiếp cận dictionary chỉ phát hiện được Company **đã từng biết** qua Job posting — Company hoàn toàn mới, chưa từng đăng tin tuyển dụng, vẫn sẽ không được nhận diện dù bài viết nhắc tên rõ ràng. Đây là đánh đổi chấp nhận được so với trạng thái trước (0% Company nào được nhận diện từ Article).

**Đã build + test thật** (qua `docker run maven:3.9.6-eclipse-temurin-21` — dùng đúng build stage có sẵn trong `apps/backend/Dockerfile`, không cần cài JDK/Maven ở host): biên dịch thành công, `EntityExtractionServiceTest` 9/9 pass. Quá trình chạy test thật phát hiện 1 lỗi thật trong chính file test (không phải code chính) — `UnnecessaryStubbingException` ở stub `techAliasCache.resolve()` trong `@BeforeEach` (không được gọi ở các test chỉ kiểm tra `extractOrg`/`extractLoc` vì text không chứa từ khoá tech nào) — đã sửa bằng `lenient()`. Đây là bằng chứng cụ thể cho lý do nên chạy test thật thay vì chỉ review code thủ công. **Chưa deploy** container `techradar-spring-api` thật với image mới — mới xác nhận build/test, chưa restart service đang chạy.

### 3.4 Kiểm thử

Ai-rag-core: 78/97 test pass sau toàn bộ thay đổi (19 fail còn lại xác nhận là lỗi có sẵn từ trước, không liên quan tới thay đổi trong phiên này — lỗi 401 auth ở 3 file test route, lỗi tính ngày ở `test_summarize_service.py`). Data-platform: 96/96 test pass (thêm test cho garbage-job check/cleanup, Company near-duplicate + fix id-tracking, `USES` từ Job). Backend (Java): build + test thật qua Docker — `EntityExtractionServiceTest` 9/9 pass sau khi sửa 1 lỗi thật trong test (`UnnecessaryStubbingException`, xem 3.3.4); riêng suite tích hợp (50 test, cần Testcontainers/Docker-trong-Docker) và module `kgreview` có sẵn của phiên khác không chạy được/còn lỗi — xác nhận không liên quan tới thay đổi của tôi. Đã bổ sung test riêng cho cả 2 bug ở Phase 0 (`test_graph_queries.py`), strategy selector, graph expansion, context ranker, serializer, và toàn bộ phát hiện mới ở 3.3.1-3.3.4.

## 4. Hạn chế còn tồn tại (nêu trung thực)

- Số liệu RAGAS thật (`context_precision`/`context_recall`, `faithfulness`/`answer_relevancy` chuẩn) **chưa có** — quota Groq/Gemini free-tier cạn giữa phiên; số liệu mục 3.2 chỉ là proxy cục bộ.
- `ground_truth` vừa soạn là **bản nháp round 1**, cần người có domain knowledge duyệt lại, đặc biệt với các câu liên quan công ty bị trùng tên (mục 3.3).
- Entity resolution cho Company/Job (ví dụ 6 pháp nhân "FPT") **chưa được tự động gộp** — KG Health Audit giờ đã phát hiện được (11 nhóm nghi trùng, mục 3.3.2) nhưng dừng ở mức review, chưa merge; heuristic hiện tại cũng không bắt hết mọi biến thể (VD không gộp được cả 6 biến thể "FPT" cùng lúc).
- `pipeline.py`/`pipeline_stream.py` trùng lặp logic có chủ đích (để tương thích test) — mọi thay đổi tương lai phải sửa tay cả 2 file, không có cơ chế chống trôi (drift).
- `pagerank_score`/`community_id` phụ thuộc rebuild admin thủ công, không có lịch tự động.
- Category backfill có độ trễ tới ~24h do thứ tự chạy job nightly.
- Fix Entity Extraction (`EntityExtractionService.java`, mục 3.3.4) đã build + test thật (9/9 pass) nhưng **chưa deploy** — container `techradar-spring-api` đang chạy vẫn là image cũ, code mới chưa có hiệu lực trên hệ thống thật cho tới khi rebuild + restart container.
- Module Java `kgreview` (mục 3.3.2, không phải do tôi viết) hiện có 50 test tích hợp lỗi do thiếu Docker-trong-Docker (môi trường build, không phải bug) + 1 số test unit lỗi có sẵn (`UnfinishedStubbingException`/`UnnecessaryStubbingException`) — chưa sửa, không thuộc phạm vi phiên này.
- `extractOrg()` (Company) chỉ phát hiện được Company đã biết qua Job posting — không phát hiện Company hoàn toàn mới chỉ xuất hiện trong Article.

## 5. Hướng phát triển tiếp theo

1. Chạy lại đánh giá bằng RAGAS thật khi quota Groq/Gemini được cấp lại.
2. Người dùng duyệt lại 13 `ground_truth` vừa soạn.
3. Người duyệt 11 nhóm Company nghi trùng (mục 3.3.2) trước khi cân nhắc gộp — thủ công hoặc xây thêm bước LLM-based giống `tech_dedup.py`.
4. Điều tra nguyên nhân 1/13 câu không trả lời được ở biến thể Expansion/Full.
5. Chờ lần retrain `ml-clustering` kế tiếp (Chủ nhật) để xác nhận tín hiệu `USES` giàu hơn (46→3018 cạnh) cải thiện chất lượng cluster thật, không chỉ lý thuyết.
6. Deploy thật fix Entity Extraction (đã build+test pass, chưa restart container `techradar-spring-api`) — xác nhận `MENTIONS(Article→Company/Location)` thực sự tăng trên dữ liệu thật sau khi có Article mới được xử lý.
7. Sửa 50 test tích hợp cần Docker-trong-Docker + lỗi test có sẵn trong module `kgreview` (không phải việc của phiên này, nhưng nên biết trước khi CI chạy full suite).
8. (Ngoài phạm vi hiện tại, ghi nhận cho tương lai) chunked embeddings, LLM-based entity/relation extraction, entity resolution toàn diện.
