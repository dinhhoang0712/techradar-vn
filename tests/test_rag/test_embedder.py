import pytest
import numpy as np
from app.core.embedder import embed_query, embed_passage, embed_batch  # type: ignore # noqa

def test_van_hanh_embedder():
    """Xác thực toàn diện các hoạt động của embedder: từ kích thước vector, prefix đến xử lý lô và chuỗi rỗng."""
    
    # 1. Kiểm tra query embedding (kích thước và chuẩn hóa)
    q_text = "Kiểm tra hệ thống"
    v_query = embed_query(q_text)
    assert len(v_query) == 768
    assert 0.99 <= np.linalg.norm(v_query) <= 1.01
    
    # 2. Kiểm tra sự khác biệt do prefix (query vs passage)
    v_passage = embed_passage(q_text)
    assert v_query != v_passage
    
    # 3. Kiểm tra xử lý chuỗi rỗng
    v_empty = embed_query("")
    assert len(v_empty) == 768
    
    # 4. Kiểm tra xử lý theo lô (batch processing)
    batch_texts = ["Văn bản A", "Văn bản B"]
    vectors = embed_batch(batch_texts, is_query=False)
    assert len(vectors) == 2
    assert vectors[0] != vectors[1]
