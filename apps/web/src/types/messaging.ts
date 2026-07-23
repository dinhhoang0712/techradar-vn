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

export interface DirectMessage {
    id: string;
    conversation_id: string;
    sender_id: string;
    content: string;
    created_at?: string;
    read?: boolean;
}

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
    send: (conversationId: string, content: string) => Promise<DirectMessage | undefined>;
}
