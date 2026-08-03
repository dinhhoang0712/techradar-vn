-- One emoji reaction per user per message (setting a new one replaces the previous, same as
-- Messenger) — mirrors post_like's shape, generalized with an emoji column so counts can be
-- aggregated per distinct emoji on a message.
CREATE TABLE message_reaction (
    message_id UUID NOT NULL REFERENCES direct_message(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji      VARCHAR(8) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);
CREATE INDEX idx_message_reaction_message ON message_reaction(message_id);
