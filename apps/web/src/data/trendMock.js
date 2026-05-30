const TECHS = [
  { id: 'golang',     name: 'Golang',       color: '#00ACD7' },
  { id: 'react',      name: 'React',        color: '#61DAFB' },
  { id: 'python',     name: 'Python',       color: '#FFD43B' },
  { id: 'rust',       name: 'Rust',         color: '#FF6584' },
  { id: 'nodejs',     name: 'Node.js',      color: '#6CC24A' },
  { id: 'aiml',       name: 'AI/ML',        color: '#A78BFA' },
  { id: 'devops',     name: 'DevOps',       color: '#FB923C' },
  { id: 'kubernetes', name: 'Kubernetes',   color: '#326CE5' },
  { id: 'typescript', name: 'TypeScript',   color: '#3178C6' },
  { id: 'nextjs',     name: 'Next.js',      color: '#FFFFFF' },
  { id: 'flutter',    name: 'Flutter',      color: '#54C5F8' },
  { id: 'vue',        name: 'Vue.js',       color: '#4FC08D' },
  { id: 'dotnet',     name: '.NET',         color: '#512BD4' },
  { id: 'aws',        name: 'AWS',          color: '#FF9900' },
  { id: 'docker',     name: 'Docker',       color: '#2496ED' },
];

// Generate growth curve per tech
function generateSeries(baseJobs, growthRate, volatility, months) {
  let val = baseJobs;
  return Array.from({ length: months }, (_, i) => {
    val = val * (1 + growthRate + (Math.random() - 0.5) * volatility);
    return Math.round(val);
  });
}

const startDate = new Date(2024, 2, 1); // March 2024

function monthLabel(offset) {
  const d = new Date(startDate);
  d.setMonth(d.getMonth() + offset);
  return d.toLocaleString('vi-VN', { month: 'short', year: '2-digit' });
}

export const MONTHS_24 = Array.from({ length: 24 }, (_, i) => monthLabel(i));

const rawSeries = {
  golang:     generateSeries(820,  0.035, 0.08, 24),
  react:      generateSeries(2100, 0.01,  0.05, 24),
  python:     generateSeries(1900, 0.04,  0.06, 24),
  rust:       generateSeries(210,  0.06,  0.12, 24),
  nodejs:     generateSeries(1400, 0.015, 0.06, 24),
  aiml:       generateSeries(950,  0.07,  0.09, 24),
  devops:     generateSeries(1100, 0.025, 0.07, 24),
  kubernetes: generateSeries(640,  0.045, 0.1,  24),
  typescript: generateSeries(1650, 0.03,  0.05, 24),
  nextjs:     generateSeries(870,  0.05,  0.08, 24),
  flutter:    generateSeries(430,  0.02,  0.1,  24),
  vue:        generateSeries(580,  0.008, 0.06, 24),
  dotnet:     generateSeries(700,  0.01,  0.05, 24),
  aws:        generateSeries(1300, 0.03,  0.06, 24),
  docker:     generateSeries(990,  0.02,  0.07, 24),
};

const rawSentiment = {
  golang:     generateSeries(0.78, 0.005, 0.04, 24).map(v => Math.min(1, v / 100)),
  react:      generateSeries(0.72, 0.002, 0.03, 24).map(v => Math.min(1, v / 100)),
  python:     generateSeries(0.82, 0.004, 0.03, 24).map(v => Math.min(1, v / 100)),
  rust:       generateSeries(0.88, 0.003, 0.04, 24).map(v => Math.min(1, v / 100)),
  nodejs:     generateSeries(0.69, 0.001, 0.03, 24).map(v => Math.min(1, v / 100)),
  aiml:       generateSeries(0.85, 0.005, 0.04, 24).map(v => Math.min(1, v / 100)),
  devops:     generateSeries(0.75, 0.003, 0.03, 24).map(v => Math.min(1, v / 100)),
  kubernetes: generateSeries(0.77, 0.004, 0.04, 24).map(v => Math.min(1, v / 100)),
  typescript: generateSeries(0.80, 0.003, 0.03, 24).map(v => Math.min(1, v / 100)),
  nextjs:     generateSeries(0.83, 0.004, 0.04, 24).map(v => Math.min(1, v / 100)),
  flutter:    generateSeries(0.74, 0.002, 0.05, 24).map(v => Math.min(1, v / 100)),
  vue:        generateSeries(0.70, 0.001, 0.03, 24).map(v => Math.min(1, v / 100)),
  dotnet:     generateSeries(0.65, 0.002, 0.03, 24).map(v => Math.min(1, v / 100)),
  aws:        generateSeries(0.79, 0.003, 0.03, 24).map(v => Math.min(1, v / 100)),
  docker:     generateSeries(0.76, 0.002, 0.03, 24).map(v => Math.min(1, v / 100)),
};

// Build flat timeline data: [{ month, golang, react, ... }]
export const timelineData = MONTHS_24.map((month, i) => {
  const row = { month };
  TECHS.forEach(t => { row[t.id] = rawSeries[t.id][i]; });
  return row;
});

// Percent growth: normalize to first value
export const growthData = MONTHS_24.map((month, i) => {
  const row = { month };
  TECHS.forEach(t => {
    const base = rawSeries[t.id][0];
    row[t.id] = Math.round(((rawSeries[t.id][i] - base) / base) * 100);
  });
  return row;
});

// Top 10 by last-month jobs
export const topTechs = [...TECHS]
  .sort((a, b) => rawSeries[b.id][23] - rawSeries[a.id][23])
  .slice(0, 10)
  .map(t => ({
    ...t,
    jobs: rawSeries[t.id][23],
    growthPct: Math.round(((rawSeries[t.id][23] - rawSeries[t.id][0]) / rawSeries[t.id][0]) * 100),
    sentiment: parseFloat((rawSentiment[t.id][23] * 100).toFixed(1)),
    yoy: Math.round(((rawSeries[t.id][23] - rawSeries[t.id][11]) / rawSeries[t.id][11]) * 100),
    mom: Math.round(((rawSeries[t.id][23] - rawSeries[t.id][22]) / rawSeries[t.id][22]) * 100),
  }));

// Per-tech sentiment
export function getSentiment(techId, monthIdx) {
  return parseFloat((rawSentiment[techId]?.[monthIdx] * 100 || 0).toFixed(1));
}

export const ALL_TECHS = TECHS;

// Peak & saturation per tech
export function getPeakMonth(techId) {
  const series = rawSeries[techId];
  const maxIdx = series.indexOf(Math.max(...series));
  return { idx: maxIdx, month: MONTHS_24[maxIdx], value: series[maxIdx] };
}

export function getSaturationMonth(techId) {
  // Saturation = first month where growth drops below 1%
  const series = rawSeries[techId];
  for (let i = 1; i < series.length; i++) {
    if ((series[i] - series[i - 1]) / series[i - 1] < 0.005) {
      return { idx: i, month: MONTHS_24[i], value: series[i] };
    }
  }
  return null;
}
