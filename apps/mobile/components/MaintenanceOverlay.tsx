import React from 'react';
import { View, Text, StyleSheet, Platform, SafeAreaView } from 'react-native';
import { DM } from '../constants/theme';

interface MaintenanceOverlayProps {
  visible: boolean;
}

export default function MaintenanceOverlay({ visible }: MaintenanceOverlayProps) {
  if (!visible) return null;

  return (
    <View style={styles.overlayRoot}>
      <SafeAreaView style={styles.container}>
        <View style={styles.content}>
          <View style={styles.iconBox}>
            <Text style={styles.icon}>!</Text>
          </View>
          <Text style={styles.title}>HỆ THỐNG ĐANG BẢO TRÌ</Text>
          <Text style={styles.message}>
            Chúng tôi đang nâng cấp ứng dụng để mang lại trải nghiệm tốt hơn. 
            Vui lòng quay lại sau ít phút.
          </Text>
          <View style={styles.footer}>
            <Text style={styles.footerText}>TECHRADAR MOBILE · 2026</Text>
          </View>
        </View>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  overlayRoot: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#000',
    zIndex: 99999,
    justifyContent: 'center',
    alignItems: 'center',
  },
  container: {
    flex: 1,
    backgroundColor: '#000',
    width: '100%',
    ...(Platform.OS === 'web' && {
      maxWidth: 480,
    }),
  },
  content: {
    flex: 1,
    padding: 32,
    justifyContent: 'center',
    alignItems: 'center',
  },
  iconBox: {
    width: 80,
    height: 80,
    borderRadius: 40,
    borderWidth: 2,
    borderColor: '#fff',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 32,
  },
  icon: {
    fontSize: 40,
    fontWeight: '900',
    color: '#fff',
  },
  title: {
    fontSize: 24,
    fontWeight: '900',
    color: '#fff',
    textAlign: 'center',
    letterSpacing: 2,
    marginBottom: 16,
  },
  message: {
    fontSize: 14,
    color: DM.text2,
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 48,
  },
  footer: {
    position: 'absolute',
    bottom: 48,
  },
  footerText: {
    fontSize: 10,
    color: '#333',
    fontWeight: '700',
    letterSpacing: 3,
  },
});
