import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { runInterviewTurn } from '../api/interviewService';
import RingGauge from '../components/common/RingGauge';
import { renderMarkdown } from '../utils/markdown';
import type { InterviewTurn, InterviewFinalSummary } from '../types/interview';
import './InterviewPage.css';

// Backend luôn cố định số lượt phỏng vấn (xem MAX_TURNS trong interview_service.py).
const MAX_TURNS = 5;

const COMMON_ROLES = [
    'Senior Backend Developer',
    'Senior Frontend Developer',
    'Full Stack Developer',
    'DevOps Engineer',
    'Data Engineer',
];

export default function InterviewPage() {
    // Cho phép điều hướng từ trang Công ty với công ty mục tiêu điền sẵn
    // (VD: nút "Luyện phỏng vấn công ty này" ở CompanyExplorer.jsx).
    const location = useLocation();
    const [targetRole, setTargetRole] = useState('');
    const [targetCompany, setTargetCompany] = useState((location.state as { targetCompany?: string } | null)?.targetCompany || '');
    const [started, setStarted] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const [history, setHistory] = useState<InterviewTurn[]>([]);
    const [currentQuestion, setCurrentQuestion] = useState<string | null>(null);
    const [currentAnswer, setCurrentAnswer] = useState('');
    const [turn, setTurn] = useState(1);
    const [isFinal, setIsFinal] = useState(false);
    const [finalSummary, setFinalSummary] = useState<InterviewFinalSummary | null>(null);

    const handleStart = async () => {
        if (!targetRole.trim() || loading) return;
        setLoading(true);
        setError('');
        try {
            const res = await runInterviewTurn({ targetRole: targetRole.trim(), targetCompany: targetCompany.trim() });
            const data = ('data' in res ? res.data : res) ?? {};
            setCurrentQuestion(data.next_question ?? null);
            setTurn(data.turn ?? 1);
            setStarted(true);
        } catch (err) {
            setError((err as Error).message || 'Không thể bắt đầu buổi phỏng vấn. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    };

    const handleSubmitAnswer = async () => {
        if (!currentAnswer.trim() || loading || !currentQuestion) return;
        setLoading(true);
        setError('');
        const newHistory = [...history, { question: currentQuestion, answer: currentAnswer.trim() }];
        try {
            const res = await runInterviewTurn({ targetRole: targetRole.trim(), targetCompany: targetCompany.trim(), history: newHistory });
            const data = ('data' in res ? res.data : res) ?? {};
            const historyWithFeedback = newHistory.map((h, i) =>
                i === newHistory.length - 1 ? { ...h, feedback: data.feedback_on_last_answer } : h
            );
            setHistory(historyWithFeedback);
            setCurrentAnswer('');
            if (data.is_final) {
                setIsFinal(true);
                setFinalSummary(data.final_summary ?? null);
                setCurrentQuestion(null);
            } else {
                setCurrentQuestion(data.next_question ?? null);
                setTurn(data.turn ?? turn + 1);
            }
        } catch (err) {
            setError((err as Error).message || 'Không thể tiếp tục buổi phỏng vấn. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    };

    const handleReset = () => {
        setStarted(false);
        setHistory([]);
        setCurrentQuestion(null);
        setCurrentAnswer('');
        setTurn(1);
        setIsFinal(false);
        setFinalSummary(null);
        setError('');
    };

    return (
        <div className="interview-page">
            <div className="interview-hero">
                <h1 className="interview-title">AI Phỏng vấn thử</h1>
                <p className="interview-subtitle">
                    Luyện phỏng vấn với câu hỏi dựa trên tin tuyển dụng thật, có đánh giá và chấm điểm cuối buổi
                </p>
            </div>

            {!started ? (
                <div className="card interview-setup-card">
                    <h2 className="section-title">Bắt đầu buổi phỏng vấn</h2>
                    <div className="form-group">
                        <label className="form-label">Vai trò mục tiêu</label>
                        <div className="role-quick-picks">
                            {COMMON_ROLES.map(r => (
                                <button
                                    key={r}
                                    type="button"
                                    className={`chip${targetRole === r ? ' active' : ''}`}
                                    onClick={() => setTargetRole(r)}
                                >
                                    {r}
                                </button>
                            ))}
                        </div>
                        <input
                            type="text"
                            className="form-input"
                            value={targetRole}
                            onChange={e => setTargetRole(e.target.value)}
                            placeholder="VD: Senior Backend Developer"
                        />
                    </div>
                    <div className="form-group">
                        <label className="form-label">Công ty mục tiêu (tuỳ chọn)</label>
                        <input
                            type="text"
                            className="form-input"
                            value={targetCompany}
                            onChange={e => setTargetCompany(e.target.value)}
                            placeholder="VD: FPT Software"
                        />
                    </div>
                    <button
                        type="button"
                        className="btn btn-primary"
                        onClick={handleStart}
                        disabled={loading || !targetRole.trim()}
                    >
                        {loading ? 'Đang chuẩn bị...' : 'Bắt đầu phỏng vấn'}
                    </button>
                    {error && <div className="interview-error">{error}</div>}
                </div>
            ) : (
                <div className="card interview-chat-card">
                    {!isFinal && (
                        <div className="interview-progress">
                            <span>Câu {turn}/{MAX_TURNS}</span>
                        </div>
                    )}
                    <div className="chat-window">
                        {history.map((h, i) => (
                            <div key={i} className="interview-turn">
                                <div className="chat-bubble-wrap bot">
                                    <div className="bot-avatar">AI</div>
                                    <div className="chat-bubble bot"><div className="bubble-content">{h.question}</div></div>
                                </div>
                                <div className="chat-bubble-wrap user">
                                    <div className="user-avatar">B</div>
                                    <div className="chat-bubble user"><div className="bubble-content">{h.answer}</div></div>
                                </div>
                                {h.feedback && <div className="interview-feedback-note">💡 {h.feedback}</div>}
                            </div>
                        ))}

                        {currentQuestion && (
                            <div className="chat-bubble-wrap bot">
                                <div className="bot-avatar">AI</div>
                                <div className="chat-bubble bot"><div className="bubble-content">{currentQuestion}</div></div>
                            </div>
                        )}

                        {loading && (
                            <div className="chat-bubble-wrap bot">
                                <div className={`bot-avatar gradient-ring${loading ? ' active' : ''}`}>AI</div>
                                <div className="chat-bubble bot">
                                    <div className="bubble-content">
                                        <span className="dots-animation"><span>.</span><span>.</span><span>.</span></span>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>

                    {error && <div className="interview-error">{error}</div>}

                    {!isFinal ? (
                        <div className="interview-answer-bar">
                            <textarea
                                className="interview-answer-input"
                                value={currentAnswer}
                                onChange={e => setCurrentAnswer(e.target.value)}
                                placeholder="Nhập câu trả lời của bạn..."
                                rows={3}
                                disabled={loading || !currentQuestion}
                            />
                            <button
                                type="button"
                                className="btn btn-primary"
                                onClick={handleSubmitAnswer}
                                disabled={loading || !currentAnswer.trim() || !currentQuestion}
                            >
                                Gửi câu trả lời
                            </button>
                        </div>
                    ) : (
                        <div className="interview-result-card">
                            <div className="interview-result-header">
                                <h3 className="section-title">Kết quả đánh giá</h3>
                                <RingGauge
                                    percent={(finalSummary?.score ?? 0) * 10}
                                    size={60}
                                    strokeWidth={5}
                                    label={`${finalSummary?.score ?? 0}/10`}
                                    className="interview-score-gauge"
                                />
                            </div>
                            <div className="interview-summary-text">{renderMarkdown(finalSummary?.summary || '')}</div>
                            <button type="button" className="btn btn-secondary" onClick={handleReset}>
                                Phỏng vấn lại
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
