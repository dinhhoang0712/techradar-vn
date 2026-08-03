// Domain types cho MessagesPage: danh sách hội thoại 1-1 + tin nhắn.
import type { PostAuthor } from './social';

export interface Conversation {
    id: string;
    other_user?: PostAuthor;
    last_message_content?: string;
    last_message_at?: string;
    last_message_sender_id?: string;
    unread_count: number;
}

export interface MessageAttachment {
    content_type: string;
    filename: string;
    size: number;
    url: string;
}

export interface MessageReaction {
    emoji: string;
    count: number;
    reacted_by_me: boolean;
}

export interface AttachmentInput {
    content_type: string;
    filename: string;
    data_base64: string;
}

export interface DirectMessage {
    id: string;
    conversation_id: string;
    sender_id: string;
    content: string;
    created_at?: string;
    read?: boolean;
    attachment?: MessageAttachment | null;
    reactions?: MessageReaction[];
}

export type MessageLiveEvent =
    | { type: 'NEW_MESSAGE'; message: DirectMessage }
    | { type: 'REACTIONS_CHANGED'; conversation_id: string; message_id: string; reactions: MessageReaction[] };

export interface MessagingContextValue {
    currentUserId: string | null;
    conversations: Conversation[];
    conversationsLoading: boolean;
    conversationsError: boolean;
    messagesByConversation: Record<string, DirectMessage[]>;
    messagesError: Record<string, boolean>;
    activeConversationId: string | null;
    refreshConversations: () => Promise<void>;
    loadMessages: (conversationId: string, opts?: { force?: boolean }) => Promise<void>;
    selectConversation: (conversationId: string | null) => Promise<void>;
    openConversationWith: (userId: string) => Promise<string | undefined>;
    send: (conversationId: string, content: string, attachment?: AttachmentInput) => Promise<DirectMessage | undefined>;
    setReaction: (conversationId: string, messageId: string, emoji: string) => Promise<void>;
    removeReaction: (conversationId: string, messageId: string) => Promise<void>;
}
