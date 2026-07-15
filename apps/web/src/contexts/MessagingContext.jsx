import { useState, useEffect, useRef } from 'react';
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

export function MessagingProvider({ children }) {
    const [currentUserId, setCurrentUserId] = useState(null);
    const [conversations, setConversations] = useState([]);
    const [conversationsLoading, setConversationsLoading] = useState(true);
    const [messagesByConversation, setMessagesByConversation] = useState({});
    const [activeConversationId, setActiveConversationId] = useState(null);

    const conversationsRef = useRef([]);
    const activeConversationIdRef = useRef(null);
    conversationsRef.current = conversations;
    activeConversationIdRef.current = activeConversationId;

    const refreshConversations = async () => {
        setConversationsLoading(true);
        try {
            const res = await getConversations();
            setConversations(res?.data ?? []);
        } catch (err) {
            console.warn('[Messaging] refreshConversations failed:', err);
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
                const data = res?.data ?? res ?? {};
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
                        const updated = {
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

    const loadMessages = async (conversationId, { force = false } = {}) => {
        if (!force && messagesByConversation[conversationId]) return;
        try {
            const res = await getMessages(conversationId);
            setMessagesByConversation((prev) => ({ ...prev, [conversationId]: res?.data ?? [] }));
        } catch (err) {
            console.warn('[Messaging] loadMessages failed:', err);
        }
    };

    const selectConversation = async (conversationId) => {
        setActiveConversationId(conversationId);
        if (!conversationId) return;
        await loadMessages(conversationId);
        setConversations((prev) => prev.map((c) => (c.id === conversationId ? { ...c, unread_count: 0 } : c)));
        try {
            await markConversationRead(conversationId);
        } catch {
            /* optimistic — không sao nếu request nền thất bại */
        }
    };

    const openConversationWith = async (userId) => {
        const res = await getOrCreateConversation(userId);
        const conversationId = res?.data?.id;
        if (conversationId) await refreshConversations();
        return conversationId;
    };

    const send = async (conversationId, content) => {
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
                const updated = {
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
                messagesByConversation,
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
