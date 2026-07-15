import React, { useEffect, useState } from 'react';
import {
    View, Text, StyleSheet, ScrollView, TouchableOpacity,
    TextInput, ActivityIndicator, Platform,
} from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { DM } from '@/constants/theme';
import { getCareerAdvice } from '../api/careerService';
import { getUserProfile } from '../api/userService';

const COMMON_ROLES = [
    'Senior Backend Developer',
    'Senior Frontend Developer',
    'Full Stack Developer',
    'DevOps Engineer',
    'Data Engineer',
];

// Simple markdown: bold, bullet list, heading — same convention as (tabs)/chat.tsx.
function renderRoadmap(text: string) {
    return text.split('\n').map((line, i) => {
        if (line.startsWith('- ') || line.startsWith('* ')) {
            return <Text key={i} style={styles.mdBullet}>  •  {renderInline(line.slice(2))}</Text>;
        }
        if (/^#{1,3} /.test(line)) {
            return <Text key={i} style={styles.mdHeading}>{renderInline(line.replace(/^#+\s/, ''))}</Text>;
        }
        if (line.trim() === '') return <Text key={i}>{'\n'}</Text>;
        return <Text key={i} style={styles.mdP}>{renderInline(line)}</Text>;
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
        parts.push(<Text key={k++} style={styles.mdBold}>{m[1]}</Text>);
        rest = rest.slice(idx + m[0].length);
    }
    return parts;
}

interface SkillGapItem { priority: number; skill: string; reason: string; job_demand: number }
interface CareerResult {
    target_role: string;
    estimated_months?: number;
    current_skills?: string[];
    skill_gap?: SkillGapItem[];
    roadmap?: string;
}

export default function CareerScreen() {
    const router = useRouter();
    const [targetRole, setTargetRole] = useState('');
    const [currentSkills, setCurrentSkills] = useState('');
    const [profileLoaded, setProfileLoaded] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [result, setResult] = useState<CareerResult | null>(null);

    useEffect(() => {
        getUserProfile()
            .then((res) => {
                const data = res?.data ?? res ?? {};
                const techs = data.profile?.technologies || data.technologies || [];
                if (techs.length > 0) {
                    setCurrentSkills(techs.join(', '));
                    setProfileLoaded(true);
                }
            })
            .catch(() => {});
    }, []);

    const handleSubmit = async () => {
        if (!targetRole.trim()) return;
        setLoading(true);
        setError('');
        setResult(null);
        try {
            const skills = currentSkills.split(',').map((s) => s.trim()).filter(Boolean);
            const res = await getCareerAdvice(targetRole.trim(), skills);
            setResult(res?.data ?? res);
        } catch (err: any) {
            setError(err?.message || 'Không thể tải dữ liệu. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <View style={styles.container}>
            <View style={styles.header}>
                <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
                    <Ionicons name="arrow-back" size={24} color={DM.text} />
                </TouchableOpacity>
                <Text style={styles.headerTitle}>Lộ trình nghề nghiệp</Text>
                <View style={{ width: 24 }} />
            </View>

            <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
                <Text style={styles.label}>Vai trò mục tiêu</Text>
                <View style={styles.chipRow}>
                    {COMMON_ROLES.map((r) => (
                        <TouchableOpacity
                            key={r}
                            style={[styles.chip, targetRole === r && styles.chipActive]}
                            onPress={() => setTargetRole(r)}
                        >
                            <Text style={[styles.chipText, targetRole === r && styles.chipTextActive]}>{r}</Text>
                        </TouchableOpacity>
                    ))}
                </View>
                <TextInput
                    style={styles.input}
                    value={targetRole}
                    onChangeText={setTargetRole}
                    placeholder="VD: Senior Backend Developer"
                    placeholderTextColor={DM.text3}
                />

                <Text style={[styles.label, { marginTop: 20 }]}>
                    Kỹ năng hiện có{profileLoaded ? ' (đã tải từ hồ sơ)' : ''}
                </Text>
                <TextInput
                    style={[styles.input, styles.textArea]}
                    value={currentSkills}
                    onChangeText={setCurrentSkills}
                    placeholder="VD: Python, Django, PostgreSQL, Docker"
                    placeholderTextColor={DM.text3}
                    multiline
                    textAlignVertical="top"
                />
                <Text style={styles.hint}>Phân tách bằng dấu phẩy</Text>

                <TouchableOpacity
                    style={[styles.submitBtn, (loading || !targetRole.trim()) && styles.submitBtnDisabled]}
                    onPress={handleSubmit}
                    disabled={loading || !targetRole.trim()}
                >
                    {loading ? <ActivityIndicator size="small" color="#000" /> : <Text style={styles.submitBtnText}>PHÂN TÍCH LỘ TRÌNH</Text>}
                </TouchableOpacity>

                {error ? <Text style={styles.error}>{error}</Text> : null}

                {result && (
                    <View style={styles.resultBox}>
                        <View style={styles.resultHeaderRow}>
                            <Text style={styles.resultTitle}>Mục tiêu: {result.target_role}</Text>
                            {result.estimated_months ? (
                                <View style={styles.estimateBadge}>
                                    <Text style={styles.estimateBadgeText}>~{result.estimated_months} tháng</Text>
                                </View>
                            ) : null}
                        </View>

                        {result.current_skills && result.current_skills.length > 0 && (
                            <View style={styles.chipRow}>
                                {result.current_skills.map((s) => (
                                    <View key={s} style={styles.skillChip}>
                                        <Text style={styles.skillChipText}>{s}</Text>
                                    </View>
                                ))}
                            </View>
                        )}

                        {result.skill_gap && result.skill_gap.length > 0 && (
                            <View style={{ marginTop: 16 }}>
                                <Text style={styles.sectionTitle}>Kỹ năng cần học</Text>
                                {result.skill_gap.map((step) => (
                                    <View key={step.skill} style={styles.gapRow}>
                                        <Text style={styles.gapPriority}>#{step.priority}</Text>
                                        <View style={{ flex: 1 }}>
                                            <Text style={styles.gapSkill}>{step.skill}</Text>
                                            <Text style={styles.gapReason}>{step.reason}</Text>
                                        </View>
                                        <Text style={styles.gapDemand}>{step.job_demand?.toLocaleString()} jobs</Text>
                                    </View>
                                ))}
                            </View>
                        )}

                        {result.roadmap && (
                            <View style={{ marginTop: 16 }}>
                                <Text style={styles.sectionTitle}>Lộ trình học tập</Text>
                                {renderRoadmap(result.roadmap)}
                            </View>
                        )}
                    </View>
                )}

                <View style={{ height: 48 }} />
            </ScrollView>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: DM.bg,
        ...(Platform.OS === 'web' && { alignSelf: 'center', width: '100%', maxWidth: 480 }),
    },
    header: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingHorizontal: 16,
        paddingTop: Platform.OS === 'android' ? 48 : 56,
        paddingBottom: 16,
        borderBottomWidth: 1,
        borderBottomColor: DM.border,
        backgroundColor: DM.surface,
    },
    backButton: { padding: 8, marginLeft: -8 },
    headerTitle: { fontSize: 18, fontWeight: '800', color: DM.text },
    content: { padding: 24 },
    label: { fontSize: 12, fontWeight: '700', color: DM.text3, marginBottom: 8, letterSpacing: 0.5 },
    hint: { fontSize: 11, color: DM.text3, marginTop: 6 },
    chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 10 },
    chip: {
        paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20,
        borderWidth: 1, borderColor: DM.border, backgroundColor: DM.surface,
    },
    chipActive: { backgroundColor: DM.primaryGlow, borderColor: DM.primary },
    chipText: { fontSize: 12, color: DM.text2 },
    chipTextActive: { color: DM.primaryLight, fontWeight: '600' },
    input: {
        backgroundColor: DM.surface2, borderWidth: 1, borderColor: DM.border,
        borderRadius: DM.radiusSm, paddingHorizontal: 16, paddingVertical: 14,
        fontSize: 14, color: DM.text,
    },
    textArea: { minHeight: 90 },
    submitBtn: {
        backgroundColor: DM.primary, borderRadius: 12, paddingVertical: 16,
        alignItems: 'center', justifyContent: 'center', marginTop: 20,
    },
    submitBtnDisabled: { opacity: 0.5 },
    submitBtnText: { fontSize: 14, fontWeight: '900', color: '#000', letterSpacing: 1 },
    error: {
        marginTop: 12, color: '#ff6b6b', fontSize: 13, padding: 12,
        backgroundColor: 'rgba(255,107,107,0.1)', borderRadius: 8,
    },
    resultBox: { marginTop: 24 },
    resultHeaderRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
    resultTitle: { fontSize: 16, fontWeight: '700', color: DM.text, flex: 1 },
    estimateBadge: {
        backgroundColor: DM.primaryGlow, borderWidth: 1, borderColor: DM.primary,
        borderRadius: 20, paddingHorizontal: 12, paddingVertical: 4,
    },
    estimateBadgeText: { fontSize: 12, fontWeight: '700', color: DM.primaryLight },
    skillChip: {
        borderRadius: 14, paddingHorizontal: 10, paddingVertical: 3,
        backgroundColor: 'rgba(0,214,143,0.1)', borderWidth: 1, borderColor: 'rgba(0,214,143,0.35)',
    },
    skillChipText: { fontSize: 12, color: '#00d68f' },
    sectionTitle: { fontSize: 14, fontWeight: '700', color: DM.text, marginBottom: 10 },
    gapRow: {
        flexDirection: 'row', alignItems: 'center', gap: 12, padding: 12,
        backgroundColor: DM.surface, borderRadius: 8, borderWidth: 1, borderColor: DM.border, marginBottom: 8,
    },
    gapPriority: { color: DM.primary, fontWeight: '700', fontSize: 13, width: 24 },
    gapSkill: { fontWeight: '600', color: DM.text, fontSize: 14 },
    gapReason: { color: DM.text3, fontSize: 12, marginTop: 2 },
    gapDemand: { color: DM.text2, fontSize: 12 },
    mdP: { color: DM.text2, fontSize: 13, lineHeight: 20, marginBottom: 4 },
    mdHeading: { color: DM.text, fontSize: 14, fontWeight: '700', marginTop: 10, marginBottom: 4 },
    mdBullet: { color: DM.text2, fontSize: 13, lineHeight: 20, marginBottom: 4 },
    mdBold: { fontWeight: '700', color: DM.text },
});
