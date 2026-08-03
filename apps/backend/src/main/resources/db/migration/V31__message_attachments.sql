-- Optional single file/image attachment per direct message (base64 in, BYTEA storage — same
-- shape as post_image/avatar). attachment_data is only ever selected by the dedicated serve
-- endpoint, never by the conversation history list query, to keep that query lightweight.
ALTER TABLE direct_message
    ADD COLUMN attachment_content_type VARCHAR(150),
    ADD COLUMN attachment_filename     VARCHAR(255),
    ADD COLUMN attachment_size         INTEGER,
    ADD COLUMN attachment_data         BYTEA;
