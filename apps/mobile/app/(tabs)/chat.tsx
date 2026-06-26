import { DM } from '@/constants/theme';
import { createChatSession, getChatHistory, streamChatMessage, getChatSessions, deleteChatSession } from '@/services/chatService';
import { getSystemStatus } from '../../api/authService';
import MaintenanceOverlay from '../../components/MaintenanceOverlay';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useRouter } from 'expo-router';
import React, { useEffect, useRef, useState } from 'react';
import {
    Keyboard,
    Dimensions,
    KeyboardAvoidingView, Platform,
    ScrollView, StyleSheet,
    Text,
    TextInput,
    TouchableOpacity,
    useWindowDimensions,
    View
} from 'react-native';

const CHAT_WINDOW_HEIGHT = Math.max(Dimensions.get('window').height * 0.5, 350);
const ACTIVE_SID_KEY = 'chat_session_id';
const SESSIONS_KEY = 'chat_sessions_list';
const GREETING = 'Xin chào! Mình là **Tech Radar AI**. Mình có thể giúp bạn phân tích xu hướng công nghệ, mức lương và tư vấn lộ trình sự nghiệp.';

const QUICK_PROMPTS = [
    'Tôi muốn tìm việc Data Engineer',
    'FPT tuyển kỹ sư phần mềm không?',
    'Shopee đang tuyển vị trí gì?',
    'Lương DevOps engineer ở Việt Nam bao nhiêu?',
    'Vì sao AI được cho là gây hại cho môi trường?',
];

interface Message { id: number; role: 'user' | 'bot'; text: string; streaming?: boolean }
let msgId = 0;

function formatTime(isoStr?: string) {
    if (!isoStr) return '';
    const d = new Date(isoStr);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return 'vừa xong';
    if (diffMin < 60) return `${diffMin} phút trước`;
    const diffH = Math.floor(diffMin / 60);
    if (diffH < 24) return `${diffH} giờ trước`;
    return d.toLocaleDateString('vi-VN');
}

function resolveChatErrorMessage(err: unknown, fallback: string) {
    const message = err instanceof Error ? err.message : String((err as any)?.message || '');
    if (!message) return fallback;
    if (/^(API error|Stream error):\s*\d+$/i.test(message)) return fallback;
    if (message === 'SESSION_TIMEOUT') return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    return message;
}

// Simple markdown: bold, bullet list
function renderText(text: string) {
    return text.split('\n').map((line, i) => {
        if (line.startsWith('- ') || line.startsWith('* ')) {
            return <Text key={i} style={s.mdBullet}>  •  {renderInline(line.slice(2))}</Text>;
        }
        if (/^#{1,3} /.test(line)) {
            return <Text key={i} style={s.mdHeading}>{renderInline(line.replace(/^#+\s/, ''))}</Text>;
        }
        if (line.trim() === '') return <Text key={i}>{'\n'}</Text>;
        return <Text key={i} style={s.mdP}>{renderInline(line)}</Text>;
    });
}

function renderInline(text: string): (string | React.ReactElement)[] {
    const parts: (string | React.ReactElement)[] = [];
    let rest = text, k = 0;
    while (rest.length > 0) {
        const m = rest.match(/\*\*(.+?)\*\*/);
        if (!m) { parts.push(rest); break; }
        const idx = rest.indexOf(m[0]);
        if (idx > 0) parts.push(rest.slice(0, idx));
        parts.push(<Text key={k++} style={s.mdBold}>{m[1]}</Text>);
        rest = rest.slice(idx + m[0].length);
    }
    return parts;
}

export default function ChatScreen() {
    const router = useRouter();
    const scrollRef = useRef<ScrollView>(null);
    const { width: windowWidth } = useWindowDimensions();
    const width = Platform.OS === 'web' ? Math.min(windowWidth, 480) : windowWidth;
    const isWide = width > 700;

    const [sessionId,   setSessionId]   = useState<string | null>(null);
    const [messages,    setMessages]    = useState<Message[]>([{ id: msgId++, role: 'bot', text: GREETING }]);
    const [input,       setInput]       = useState('');
    const [isStreaming, setIsStreaming] = useState(false);
    const [showProfile, setShowProfile] = useState(false);
    const [profile,     setProfile]     = useState({ skills: '', exp: '', salary: '' });
    const [isMaintenance, setIsMaintenance] = useState(false);

    // History states
    const [sessions,    setSessions]    = useState<any[]>([]);
    const [showHistory, setShowHistory] = useState(false);
    const [loadingSessions, setLoadingSessions] = useState(false);
    const [fetchError, setFetchError] = useState<string | null>(null);
    const [loadingHistory, setLoadingHistory] = useState(false);
    const [keyboardHeight, setKeyboardHeight] = useState(0);



    // Helper: Parse message format từ API trả về (hỗ trợ cả {query, answer} lẫn {role, content})
    const parseHistoryMessages = (historyData: any) => {
        const msgs = Array.isArray(historyData) ? historyData : historyData?.data;
        if (!Array.isArray(msgs) || msgs.length === 0) return [];
        
        let mappedMsgs: Message[] = [];
        msgs.forEach((m: any) => {
            if (m.query || m.answer) {
                if (m.query) mappedMsgs.push({ id: msgId++, role: 'user', text: m.query });
                if (m.answer) mappedMsgs.push({ id: msgId++, role: 'bot', text: m.answer });
            } else if (m.role || m.type) {
                const isUser = m.role === 'user' || m.role === 'human' || m.type === 'human' || m.type === 'user';
                mappedMsgs.push({
                    id: msgId++,
                    role: isUser ? 'user' : 'bot',
                    text: m.content || m.text || '',
                });
            }
        });
        return mappedMsgs;
    };

    const fetchHistoryList = async () => {
        setLoadingSessions(true);
        setFetchError(null);
        try {
            const res = await getChatSessions();
            const rawList = Array.isArray(res) ? res : res?.data || [];
            
            const mappedList = rawList.map((s: any) => ({
                ...s,
                id: s.id || s.session_id,
                title: s.title || 'Cuộc trò chuyện mới',
                created_at: s.created_at || new Date().toISOString()
            }));
            
            setSessions(mappedList);
            return mappedList;
        } catch (err: any) {
            console.error('Fetch history list error', err);
            setFetchError(err.message || 'Lỗi kết nối server');
            return [];
        } finally {
            setLoadingSessions(false);
        }
    };

    // ── Khởi tạo session khi mount ──────────────────────────────────────────
    useEffect(() => {
        const checkStatus = async () => {
            try {
                const res = await getSystemStatus();
                // Admin page toggles 'feature_rag' for AI RAG, we should use that to block AI Chat
                if (res && (res.feature_rag === false || res.feature_rag === 'false' || res.feature_chat === false || res.feature_chat === 'false')) {
                    setIsMaintenance(true);
                } else {
                    setIsMaintenance(false);
                }
            } catch(e) {}
        };
        checkStatus();
        const interval = setInterval(checkStatus, 30000);

        const init = async () => {
            try {
                await fetchHistoryList();

                const savedSid = await AsyncStorage.getItem(ACTIVE_SID_KEY);
                if (savedSid) {
                    setSessionId(savedSid);
                    // Load lịch sử chat
                    try {
                        const history = await getChatHistory(savedSid);
                        const mapped = parseHistoryMessages(history);
                        if (mapped.length > 0) {
                            setMessages(mapped);
                        } else {
                            setMessages([{ id: msgId++, role: 'bot', text: GREETING }]);
                        }
                    } catch {
                        // Session cũ hết hạn → tạo mới
                        await AsyncStorage.removeItem(ACTIVE_SID_KEY);
                        await createNewSession();
                    }
                } else {
                    await createNewSession();
                }
            } catch (err) {
                console.error('[ChatScreen] Init error:', err);
            }
        };
        init();
        return () => clearInterval(interval);
    }, []);

    useEffect(() => {
        setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 100);
    }, [messages]);

    useEffect(() => {
        if (keyboardHeight > 0) {
            setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 100);
        }
    }, [keyboardHeight]);

    useEffect(() => {
        const showEvent = Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow';
        const hideEvent = Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide';

        const showSub = Keyboard.addListener(showEvent, (event) => {
            setKeyboardHeight(event.endCoordinates?.height || 0);
        });
        const hideSub = Keyboard.addListener(hideEvent, () => {
            setKeyboardHeight(0);
        });

        return () => {
            showSub.remove();
            hideSub.remove();
        };
    }, []);

    const createNewSession = async () => {
        const res = await createChatSession();
        const sid = res?.session_id || res?.data?.session_id;
        if (!sid) throw new Error('No session_id returned');
        await AsyncStorage.setItem(ACTIVE_SID_KEY, sid);
        setSessionId(sid);
        
        // Refresh session list from server
        await fetchHistoryList();
        
        setMessages([{ id: msgId++, role: 'bot', text: GREETING }]);
        return sid;
    };

    const switchSession = async (sid: string) => {
        if (sid === sessionId || isStreaming) return;
        setLoadingHistory(true);
        setShowHistory(false);
        setMessages([]); // Clear màn hình tạm thời trong lúc fetch
        try {
            await AsyncStorage.setItem(ACTIVE_SID_KEY, sid);
            setSessionId(sid);
            
            // Lấy lịch sử tin nhắn của session này từ server
            const history = await getChatHistory(sid);
            const mapped = parseHistoryMessages(history);
            
            if (mapped.length > 0) {
                setMessages(mapped);
            } else {
                // Nếu chưa có tin nhắn nào (session mới tạo)
                setMessages([{ id: msgId++, role: 'bot', text: GREETING }]);
            }
        } catch (e) {
            console.error('Switch session error', e);
            setMessages([{ id: msgId++, role: 'bot', text: '⚠️ Lỗi tải lịch sử trò chuyện. Vui lòng thử lại.' }]);
        } finally {
            setLoadingHistory(false);
        }
    };

    const clearSession = async () => {
        if (isStreaming) return;
        setShowHistory(false);
        try {
            await createNewSession();
        } catch (e) {
            console.error('Clear session error', e);
        }
    };

    const deleteSession = async (sid: string) => {
        // Optimistic: remove locally, then delete on the server (ignore failure for local-only sessions).
        setSessions(prev => prev.filter(s => (s.id || s.session_id) !== sid));
        if (sid === sessionId) {
            clearSession();
        }
        try {
            await deleteChatSession(sid);
        } catch (e) {
            console.warn('[chat] deleteChatSession failed', e);
        }
    };

    // ── Gửi tin nhắn via SSE stream ─────────────────────────────────────────
    const sendMessage = (text: string) => {
        if (!text.trim() || isStreaming) return;

        const userMsg: Message = { id: msgId++, role: 'user', text };
        const botMsg:  Message = { id: msgId++, role: 'bot', text: '', streaming: true };
        setMessages(prev => [...prev, userMsg, botMsg]);
        setInput('');
        setIsStreaming(true);

        // Cập nhật title session (Local UI update trước)
        setSessions(prev => {
            const userMsgs = messages.filter(m => m.role === 'user');
            if (userMsgs.length === 0 && sessionId) {
                 return prev.map(s => (s.id === sessionId || s.session_id === sessionId) ? { ...s, title: text.slice(0, 40) } : s);
            }
            return prev;
        });

        if (!sessionId) {
            setMessages(prev => prev.map(m =>
                m.id === botMsg.id
                    ? { ...m, text: '⚠️ Chưa kết nối server. Vui lòng thử lại.', streaming: false }
                    : m
            ));
            setIsStreaming(false);
            return;
        }

        let accumulated = '';
        let pendingText = '';
        let flushTimer: ReturnType<typeof setTimeout> | null = null;

        const updateBotText = (textValue: string, extra: Partial<Message> = {}) => {
            setMessages(prev => prev.map(m =>
                m.id === botMsg.id ? { ...m, text: textValue, ...extra } : m
            ));
        };

        const flushPendingText = () => {
            if (!pendingText) return;
            accumulated += pendingText;
            pendingText = '';
            updateBotText(accumulated);
        };

        const scheduleFlush = () => {
            if (flushTimer) return;
            flushTimer = setTimeout(() => {
                flushTimer = null;
                flushPendingText();
            }, 45);
        };

        streamChatMessage(
            sessionId, text,
            // onToken
            (chunk: string) => {
                pendingText += chunk;
                scheduleFlush();
            },
            // onDone
            (meta: any) => {
                if (flushTimer) {
                    clearTimeout(flushTimer);
                    flushTimer = null;
                }
                flushPendingText();
                const finalText = meta?.answer || accumulated;
                updateBotText(finalText, { streaming: false });
                setIsStreaming(false);
            },
            // onError
            (err: Error) => {
                console.error('[ChatScreen] Stream error:', err);
                if (flushTimer) {
                    clearTimeout(flushTimer);
                    flushTimer = null;
                }
                flushPendingText();
                const detail = resolveChatErrorMessage(err, '');
                const errText = accumulated
                    ? accumulated + '\n\n⚠️ Kết nối bị gián đoạn.'
                    : detail
                        ? `⚠️ ${detail}`
                        : '⚠️ Không nhận được phản hồi. Vui lòng thử lại.';
                updateBotText(errText, { streaming: false });
                setIsStreaming(false);
            }
        );
    };

    const sidebar = null;

    return (
        <KeyboardAvoidingView
            style={s.container}
            behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
            keyboardVerticalOffset={Platform.OS === 'ios' ? 8 : 0}
        >
            <MaintenanceOverlay visible={isMaintenance} />
            <View style={s.header}>
                <Text style={s.headerTitle}>AI Chat</Text>
                <View style={s.headerActions}>
                    <TouchableOpacity 
                        style={s.headerBtn} 
                        onPress={() => {
                            const nextState = !showHistory;
                            setShowHistory(nextState);
                            if (nextState) fetchHistoryList(); // Refresh khi mở
                        }}
                    >
                        <Text style={s.headerBtnText}>{showHistory ? 'Đóng' : 'Lịch sử'}</Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={s.headerBtn} onPress={clearSession} disabled={isStreaming}>
                        <Text style={s.headerBtnText}>+</Text>
                    </TouchableOpacity>
                </View>
            </View>

            <View style={s.body}>
                {showHistory ? (
                    <View style={{ flex: 1, backgroundColor: '#000' }}>
                        <View style={{ flexDirection: 'row', justifyContent: 'space-between', padding: 8, alignItems: 'center', backgroundColor: '#000' }}>
                            <Text style={{ color: '#ffffff', fontSize: 10 }}>
                                {sessions.length} cuộc trò chuyện | {loadingSessions ? 'Đang tải...' : 'Đã đồng bộ'}
                            </Text>
                            {fetchError && <Text style={{ color: '#ff4444', fontSize: 10 }}>{fetchError}</Text>}
                        </View>

                        <ScrollView
                            style={s.historyContainer}
                            contentContainerStyle={{ paddingBottom: 40 }}
                            showsVerticalScrollIndicator={false}
                        >
                            {sessions.length === 0 && !loadingSessions ? (
                                <View style={{ marginTop: 40, alignItems: 'center', paddingHorizontal: 20 }}>
                                    <Text style={[s.historyEmpty, { color: '#ffffff' }]}>Chưa có cuộc trò chuyện nào.</Text>
                                </View>
                            ) : (
                                    sessions.map((s, index) => (
                                        <TouchableOpacity 
                                            key={s.id || index} 
                                            style={{
                                                flexDirection: 'row',
                                                alignItems: 'center',
                                                padding: 16,
                                                backgroundColor: '#1a1a1a',
                                                borderWidth: 1,
                                                borderColor: (s.id === sessionId || s.session_id === sessionId) ? '#ffffff' : '#333333',
                                                borderRadius: 12,
                                                marginBottom: 12,
                                                minHeight: 80,
                                            }} 
                                            onPress={() => switchSession(s.id || s.session_id)}
                                        >
                                            <View style={{ flex: 1, marginRight: 10 }}>
                                                <Text style={{ color: '#ffffff', fontSize: 15, fontWeight: '600', marginBottom: 6 }} numberOfLines={1}>
                                                    {s.title || '(Không có tiêu đề)'}
                                                </Text>
                                                <Text style={{ color: '#888888', fontSize: 12 }}>{formatTime(s.created_at)}</Text>
                                            </View>
                                            {false && <TouchableOpacity 
                                                onPress={() => deleteSession(s.id || s.session_id)} 
                                                style={{ padding: 10, backgroundColor: 'rgba(239, 68, 68, 0.1)', borderRadius: 8 }}
                                            >
                                                <Text style={{ color: '#ef4444', fontSize: 20, fontWeight: '700' }}>×</Text>
                                            </TouchableOpacity>}
                                        </TouchableOpacity>
                                    ))
                            )}
                        </ScrollView>
                    </View>
                ) : (
                <View style={s.chatMain}>
                    {/* Messages */}
                    <ScrollView
                        ref={scrollRef}
                        style={s.chatWindow}
                        contentContainerStyle={s.chatContent}
                        showsVerticalScrollIndicator={false}
                        keyboardShouldPersistTaps="handled"
                    >
                        {loadingHistory && <Text style={{ color: DM.text3, textAlign: 'center', marginVertical: 10 }}>Đang tải...</Text>}
                        {messages.map(msg => (
                            <View key={msg.id} style={[s.bubbleWrap, msg.role === 'user' && s.bubbleWrapUser]}>
                                {msg.role === 'bot' && (
                                    <View style={s.botAvatar}><Text style={s.avatarText}>AI</Text></View>
                                )}
                                <View style={[s.bubble, msg.role === 'user' ? s.bubbleUser : s.bubbleBot]}>
                                    {renderText(msg.text)}
                                    {msg.streaming && <Text style={s.cursor}>▊</Text>}
                                </View>
                                {msg.role === 'user' && (
                                    <View style={s.userAvatar}><Text style={s.avatarTextUser}>U</Text></View>
                                )}
                            </View>
                        ))}
                    </ScrollView>

                    {/* Input bar */}
                    <View style={s.inputBar}>
                        <TextInput
                            style={s.chatInput}
                            placeholder="Hỏi về xu hướng công nghệ, lương, lộ trình học..."
                            placeholderTextColor={DM.text3}
                            value={input}
                            onChangeText={setInput}
                            onSubmitEditing={() => sendMessage(input)}
                            editable={!isStreaming}
                            multiline
                            scrollEnabled={false}
                            showsVerticalScrollIndicator={false}
                        />
                        <TouchableOpacity
                            style={[s.sendBtn, (!input.trim() || isStreaming) && s.sendBtnDisabled]}
                            onPress={() => sendMessage(input)}
                            disabled={isStreaming || !input.trim()}
                        >
                            <Text style={s.sendBtnText}>{isStreaming ? '...' : 'Gửi'}</Text>
                        </TouchableOpacity>
                    </View>
                </View>
                )}
            </View>
        </KeyboardAvoidingView>
    );
}

const s = StyleSheet.create({
    container: { 
        flex: 1, 
        backgroundColor: DM.bg, 
        paddingTop: 48,
        ...(Platform.OS === 'web' && { alignSelf: 'center', width: '100%' })
    },
    header: { paddingHorizontal: 16, marginBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
    headerTitle: { fontSize: 20, fontWeight: '800', color: DM.text },
    headerActions: { flexDirection: 'row', gap: 10 },
    headerBtn: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: DM.radiusSm, borderWidth: 1, borderColor: DM.border, backgroundColor: DM.surface2 },
    headerBtnText: { color: DM.text, fontSize: 13, fontWeight: '600' },

    body: { flex: 1, paddingHorizontal: 16 },
    bodyWide: { flexDirection: 'row', gap: 16 },

    // History
    historyContainer: { 
        flex: 1, 
        marginTop: 10,
        backgroundColor: '#000', // Đảm bảo nền đen
    },
    historyEmpty: { 
        color: '#999', 
        fontSize: 14, 
        textAlign: 'center', 
        marginTop: 40 
    },
    historyItem: { 
        flexDirection: 'row', 
        alignItems: 'center', 
        padding: 16, 
        backgroundColor: '#1a1a1a', // Xám đậm cực kỳ rõ ràng
        borderWidth: 1, 
        borderColor: '#333', 
        borderRadius: 12, 
        marginBottom: 10,
        minHeight: 70, // Cố định chiều cao tối thiểu
    },
    historyItemActive: { 
        borderColor: '#fff',
        backgroundColor: '#222',
    },
    historyItemBody: { 
        flex: 1, 
        marginRight: 10,
    },
    historyItemTitle: { 
        color: '#fff', // Chữ trắng trên nền xám
        fontSize: 15, 
        fontWeight: '600', 
        marginBottom: 4 
    },
    historyItemTitleActive: { 
        color: '#fff', 
    },
    historyItemTime: { 
        color: '#666', 
        fontSize: 12 
    },
    historyItemDel: { 
        padding: 10, 
        backgroundColor: 'rgba(239,68,68,0.1)', 
        borderRadius: 8,
    },
    historyItemDelText: { 
        color: '#ef4444', 
        fontSize: 20,
        fontWeight: '700',
    },

    // Chat main
    chatMain: { flex: 1 },
    chatWindow: { flex: 1, marginBottom: 12 },
    chatContent: { paddingBottom: 12 },

    bubbleWrap: { flexDirection: 'row', alignItems: 'flex-start', gap: 8, marginBottom: 12 },
    bubbleWrapUser: { justifyContent: 'flex-end' },
    botAvatar: {
        width: 32, height: 32, borderRadius: 16, backgroundColor: DM.primary,
        alignItems: 'center', justifyContent: 'center',
    },
    userAvatar: {
        width: 32, height: 32, borderRadius: 16, backgroundColor: DM.surface2,
        alignItems: 'center', justifyContent: 'center',
    },
    avatarText: { color: '#000', fontSize: 11, fontWeight: '700' },
    avatarTextUser: { color: '#fff', fontSize: 11, fontWeight: '700' },
    bubble: {
        maxWidth: '75%', borderRadius: DM.radius, padding: 14,
    },
    bubbleBot: { backgroundColor: DM.surface, borderWidth: 1, borderColor: DM.border },
    bubbleUser: { backgroundColor: DM.primaryGlow, borderWidth: 1, borderColor: DM.primary, overflow: 'hidden' },

    // Markdown
    mdP: { color: DM.text, fontSize: 13, lineHeight: 20 },
    mdBold: { fontWeight: '700', color: DM.text },
    mdBullet: { color: DM.text, fontSize: 13, lineHeight: 22 },
    mdHeading: { color: DM.text, fontSize: 14, fontWeight: '700', marginTop: 6, marginBottom: 2 },

    cursor: { color: DM.primary, fontSize: 14 },
    // Quick prompts
    quickRow: { maxHeight: 44, marginBottom: 6 },
    quickContent: { gap: 8, alignItems: 'center' },
    quickBtn: {
        paddingHorizontal: 12, paddingVertical: 7, borderRadius: DM.radiusSm,
        borderWidth: 1, borderColor: DM.primary, backgroundColor: DM.primaryGlow,
        maxWidth: 280,
    },
    quickBtnText: { color: DM.primaryLight, fontSize: 11, fontWeight: '500' },

    // Input bar
    inputBar: {
        flexDirection: 'row', alignItems: 'flex-end', gap: 8,
        backgroundColor: DM.surface, borderWidth: 1, borderColor: DM.border,
        borderRadius: DM.radius, paddingHorizontal: 12, paddingVertical: 6,
        marginBottom: 18,
    },
    chatInput: { flex: 1, color: DM.text, fontSize: 13, paddingVertical: 6, maxHeight: 80, overflow: 'hidden' },
    sendBtn: {
        paddingHorizontal: 16, paddingVertical: 8, borderRadius: DM.radiusSm,
        backgroundColor: DM.primary,
    },
    sendBtnDisabled: { opacity: 0.4 },
    sendBtnText: { color: '#000', fontWeight: '700', fontSize: 13 },

    // Sidebar
    sidebar: { gap: 12, paddingBottom: 16 },
    sidebarWide: { flex: 1, maxWidth: 300 },

    card: {
        backgroundColor: DM.surface, borderWidth: 1, borderColor: DM.border,
        borderRadius: DM.radius, padding: 14,
    },
    cardHeaderRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
    cardTitle: { fontSize: 14, fontWeight: '700', color: DM.text },
    expandBtn: {
        fontSize: 11, color: DM.primaryLight, fontWeight: '600',
        paddingHorizontal: 8, paddingVertical: 3, borderRadius: DM.radiusSm,
        borderWidth: 1, borderColor: DM.border, backgroundColor: DM.surface2,
    },

    // Profile form
    profileForm: { gap: 8 },
    pfGroup: { gap: 3 },
    pfLabel: { fontSize: 10, color: DM.text3, fontWeight: '600' },
    pfInput: {
        backgroundColor: DM.bg2, borderWidth: 1, borderColor: DM.border,
        borderRadius: DM.radiusSm, paddingHorizontal: 10, paddingVertical: 6,
        color: DM.text, fontSize: 12,
    },
    pfHint: { color: DM.text3, fontSize: 12, lineHeight: 18 },
    btnPrimary: {
        backgroundColor: DM.primary, borderRadius: DM.radiusSm,
        paddingVertical: 8, alignItems: 'center', marginTop: 4,
    },
    btnPrimaryText: { color: '#000', fontWeight: '700', fontSize: 12 },

    // Suggestions
    sugItem: { flexDirection: 'row', alignItems: 'flex-start', gap: 8, marginTop: 8 },
    sugNum: {
        width: 22, height: 22, borderRadius: 11, backgroundColor: DM.primary,
        alignItems: 'center', justifyContent: 'center',
    },
    sugNumText: { color: '#000', fontSize: 10, fontWeight: '700' },
    sugText: { color: DM.text2, fontSize: 12, flex: 1, lineHeight: 17 },

    // Tool buttons
    toolBtn: {
        backgroundColor: DM.surface2, borderWidth: 1, borderColor: DM.border,
        borderRadius: DM.radiusSm, paddingHorizontal: 12, paddingVertical: 8,
        marginTop: 6,
    },
    toolBtnText: { color: DM.text, fontSize: 12, fontWeight: '500' },
});
