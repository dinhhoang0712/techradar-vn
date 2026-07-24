// Short two-tone chime played when a new notification arrives — synthesized via the Web Audio
// API (no bundled audio asset needed). Silently no-ops if the browser blocks audio without a
// prior user gesture (autoplay policy) — a missed chime is harmless, unlike a thrown error.
let audioCtx: AudioContext | null = null;

function getAudioContext(): AudioContext | null {
    if (typeof window === 'undefined') return null;
    const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextClass) return null;
    if (!audioCtx) audioCtx = new AudioContextClass();
    return audioCtx;
}

function playTone(ctx: AudioContext, startAt: number, freq: number, peakGain: number, duration: number): void {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.value = freq;
    gain.gain.setValueAtTime(0, startAt);
    gain.gain.linearRampToValueAtTime(peakGain, startAt + 0.015);
    gain.gain.exponentialRampToValueAtTime(0.0001, startAt + duration);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start(startAt);
    osc.stop(startAt + duration + 0.02);
}

export function playNotificationSound(): void {
    try {
        const ctx = getAudioContext();
        if (!ctx) return;
        if (ctx.state === 'suspended') {
            ctx.resume().catch(() => {});
        }
        const now = ctx.currentTime;
        // A5 -> D6, quick decay — soft "ding" rather than an alarm.
        playTone(ctx, now, 880, 0.1, 0.09);
        playTone(ctx, now + 0.09, 1174.66, 0.1, 0.12);
    } catch {
        // Autoplay policy or unsupported browser — a missed chime is fine.
    }
}
