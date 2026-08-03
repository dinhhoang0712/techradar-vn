-- Log 1 dòng cho mỗi lần llm-gateway (services/llm-gateway) gọi thành công 1 provider LLM.
-- Dùng để tính chi phí LLM theo provider/model theo thời gian (bước Cost Tracking / Billing).
-- Ghi bởi ai-rag-core (và sau này ml-clustering/data-platform khi migrate sang dùng gateway chung).
-- UUID sinh ở tầng application (giống chat_session/chat_message), không dùng gen_random_uuid()
-- để khỏi phụ thuộc pgcrypto/Postgres version — khớp convention hiện tại của các bảng khác.
CREATE TABLE llm_usage_log (
    id               UUID PRIMARY KEY,
    service          VARCHAR(50)  NOT NULL,  -- "ai-rag-core" | "ml-clustering" | "data-platform"
    provider         VARCHAR(50)  NOT NULL,  -- "openai" | "groq" | "gemini" | "claude"
    model            VARCHAR(100) NOT NULL,
    input_tokens     INTEGER      NOT NULL,
    output_tokens    INTEGER      NOT NULL,
    cost_usd         NUMERIC(12, 6) NOT NULL,
    fallback_from    VARCHAR(50),            -- provider đã lỗi trước khi rơi xuống provider này, NULL nếu gọi thẳng thành công
    created_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_llm_usage_log_created_at ON llm_usage_log(created_at);
CREATE INDEX idx_llm_usage_log_provider_model ON llm_usage_log(provider, model);
