import { useState, useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { MessagingContext } from './messagingStore';
import {
    getConversations,
    getOrCreateConversation,
    getMessages,
    sendMessage,
    markConversationRead,
    streamConversations,
} from '../api/messagingService';
import { getUserProfile } from '../api/userService';
import { useToast } from '../components/common/toastContext';
import type { Conversation, DirectMessage } from '../types/messaging';

export function MessagingProvider({ children }: { children: ReactNode }) {
    const [currentUserId, setCurrentUserId] = useState<string | null>(null);
    const [conversations, setConversations] = useState<Conversation[]>([]);
    const [conversationsLoading, setConversationsLoading] = useState(true);
    const [conversationsError, setConversationsError] = useState(false);
    const [messagesByConversation, setMessagesByConversation] = useState<Record<string, DirectMessage[]>>({});
    const [messagesError, setMessagesError] = useState<Record<string, boolean>>({});
    const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
    const notify = useToast();

    const conversationsRef = useRef<Conversation[]>([]);
    const activeConversationIdRef = useRef<string | null>(null);
    conversationsRef.current = conversations;
    activeConversationIdRef.current = activeConversationId;

    const refreshConversations = async () => {
        setConversationsLoading(true);
        setConversationsError(false);
        try {
            const res = await getConversations();
            setConversations(res?.data ?? []);
        } catch (err) {
            console.warn('[Messaging] refreshConversations failed:', err);
            setConversationsError(true);
            notify({ title: 'Không tải được danh sách trò chuyện', body: 'Vui lòng thử lại.', variant: 'error' });
        } finally {
            setConversationsLoading(false);
        }
    };

    useEffect(() => {
        if (!localStorage.getItem('access_token')) {
            setConversationsLoading(false);
            return;
        }
        getUserProfile()
            .then((res) => {
                const data = (res && 'data' in res ? res.data : res) ?? {};
                setCurrentUserId(data.id ?? data.user?.id ?? null);
            })
            .catch(() => {});
        refreshConversations();
    }, []);

    // Live stream — một kết nối duy nhất cho toàn bộ cuộc trò chuyện, mở khi provider mount.
    useEffect(() => {
        if (!localStorage.getItem('access_token')) return undefined;

        const controller = streamConversations(
            (msg) => {
                const isActive = activeConversationIdRef.current === msg.conversation_id;

                setMessagesByConversation((prev) => {
                    if (!prev[msg.conversation_id]) return prev; // chưa mở thread này thì chưa cần giữ message rời rạc
                    return { ...prev, [msg.conversation_id]: [...prev[msg.conversation_id], msg] };
                });

                const exists = conversationsRef.current.some((c) => c.id === msg.conversation_id);
                if (!exists) {
                    // Cuộc trò chuyện mới chưa từng có trong danh sách — tải lại để lấy đủ other_user.
                    refreshConversations();
                } else {
                    setConversations((prev) => {
                        const idx = prev.findIndex((c) => c.id === msg.conversation_id);
                        if (idx === -1) return prev;
                        const updated: Conversation = {
                            ...prev[idx],
                            last_message_content: msg.content,
                            last_message_at: msg.created_at,
                            last_message_sender_id: msg.sender_id,
                            unread_count: isActive ? 0 : prev[idx].unread_count + 1,
                        };
                        return [updated, ...prev.slice(0, idx), ...prev.slice(idx + 1)];
                    });
                }

                if (isActive) {
                    markConversationRead(msg.conversation_id).catch(() => {});
                }
            },
            (err) => console.warn('[Messaging] stream error:', err),
        );

        return () => controller.abort();
    }, []);

    const loadMessages = async (conversationId: string, { force = false }: { force?: boolean } = {}) => {
        if (!force && messagesByConversation[conversationId]) return;
        try {
            const res = await getMessages(conversationId);
            setMessagesByConversation((prev) => ({ ...prev, [conversationId]: res?.data ?? [] }));
            setMessagesError((prev) => ({ ...prev, [conversationId]: false }));
        } catch (err) {
            console.warn('[Messaging] loadMessages failed:', err);
            setMessagesError((prev) => ({ ...prev, [conversationId]: true }));
            notify({ title: 'Không tải được tin nhắn', body: 'Vui lòng thử lại.', variant: 'error' });
        }
    };

    const selectConversation = async (conversationId: string | null) => {
        setActiveConversationId(conversationId);
        if (!conversationId) return;
        await loadMessages(conversationId);
        const previousUnread = conversationsRef.current.find((c) => c.id === conversationId)?.unread_count;
        setConversations((prev) => prev.map((c) => (c.id === conversationId ? { ...c, unread_count: 0 } : c)));
        try {
            await markConversationRead(conversationId);
        } catch (err) {
            console.warn('[Messaging] markConversationRead failed:', err);
            setConversations((prev) =>
                prev.map((c) => (c.id === conversationId ? { ...c, unread_count: previousUnread ?? c.unread_count } : c)));
            notify({ title: 'Không thể đánh dấu đã đọc', body: 'Vui lòng thử lại.', variant: 'error' });
        }
    };

    const openConversationWith = async (userId: string) => {
        const res = await getOrCreateConversation(userId);
        const conversationId = res?.data?.id;
        if (conversationId) await refreshConversations();
        return conversationId;
    };

    const send = async (conversationId: string, content: string) => {
        const res = await sendMessage(conversationId, content);
        const msg = res?.data;
        if (msg) {
            setMessagesByConversation((prev) => ({
                ...prev,
                [conversationId]: [...(prev[conversationId] || []), msg],
            }));
            setConversations((prev) => {
                const idx = prev.findIndex((c) => c.id === conversationId);
                if (idx === -1) return prev;
                const updated: Conversation = {
                    ...prev[idx],
                    last_message_content: msg.content,
                    last_message_at: msg.created_at,
                    last_message_sender_id: msg.sender_id,
                };
                return [updated, ...prev.slice(0, idx), ...prev.slice(idx + 1)];
            });
        }
        return msg;
    };

    return (
        <MessagingContext.Provider
            value={{
                currentUserId,
                conversations,
                conversationsLoading,
                conversationsError,
                messagesByConversation,
                messagesError,
                activeConversationId,
                refreshConversations,
                loadMessages,
                selectConversation,
                openConversationWith,
                send,
            }}
        >
            {children}
        </MessagingContext.Provider>
    );
}
