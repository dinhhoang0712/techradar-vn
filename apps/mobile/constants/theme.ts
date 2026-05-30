import { Platform } from 'react-native';

// Design tokens matching Minimalist Monochrome theme
export const DM = {
  bg: '#000000',
  bg2: '#050505',
  surface: '#111111',
  surface2: '#1a1a1a',
  border: '#2a2a2a',
  border2: '#333333',

  primary: '#FFFFFF',
  primaryLight: '#F5F5F5',
  primaryDark: '#E0E0E0',
  primaryGlow: 'rgba(255, 255, 255, 0.1)',

  accent: '#FFFFFF',
  accentLight: '#F5F5F5',
  accentGlow: 'rgba(255, 255, 255, 0.1)',

  green: '#FFFFFF',
  greenLight: '#FFFFFF',
  greenGlow: 'rgba(255, 255, 255, 0.1)',

  yellow: '#FFFFFF',
  yellowGlow: 'rgba(255, 255, 255, 0.1)',

  text: '#FFFFFF',
  text2: '#A0A0A0',
  text3: '#666666',

  radiusSm: 8,
  radius: 12,
  radiusLg: 18,
  radiusXl: 24,
};

export const Colors = {
  light: {
    text: '#000000',
    background: '#FFFFFF',
    tint: '#000000',
    icon: '#666666',
    tabIconDefault: '#999999',
    tabIconSelected: '#000000',
  },
  dark: {
    text: DM.text,
    background: DM.bg,
    tint: DM.primary,
    icon: DM.text2,
    tabIconDefault: DM.text3,
    tabIconSelected: DM.primary,
  },
};

export const Fonts = Platform.select({
  ios: {
    sans: 'System',
    serif: 'Georgia',
    rounded: 'System',
    mono: 'Menlo',
  },
  default: {
    sans: 'normal',
    serif: 'serif',
    rounded: 'normal',
    mono: 'monospace',
  },
});
