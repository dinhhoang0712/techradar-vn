import { DarkTheme, ThemeProvider } from '@react-navigation/native';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import React, { useState, useEffect } from 'react';
import { Platform, View, StyleSheet } from 'react-native';
import 'react-native-reanimated';
import MaintenanceOverlay from '../components/MaintenanceOverlay';

// Minimalist Monochrome Theme for Navigation
const monochromeTheme = {
  ...DarkTheme,
  colors: {
    ...DarkTheme.colors,
    background: '#000000',
    card: '#000000',
    text: '#ffffff',
    border: '#1a1a1a',
    primary: '#ffffff',
  },
};

export default function RootLayout() {
  // Simulated state for Maintenance (matching Web Admin's "isAppMaintenance")
  const [isMaintenance, setIsMaintenance] = useState(false);

  return (
    <ThemeProvider value={monochromeTheme}>
      <View style={styles.rootWrapper}>
        <View style={styles.rootContainer}>
          <Stack screenOptions={{ headerShown: false }}>
            {/* Auth Screens */}
            <Stack.Screen name="login" options={{ gestureEnabled: false }} />
            <Stack.Screen name="register" />
            
            {/* Main App */}
            <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          </Stack>
        </View>
      </View>

      {/* Maintenance Overlay - Blocks everything if active */}
      <MaintenanceOverlay visible={isMaintenance} />
      
      <StatusBar style="light" />
    </ThemeProvider>
  );
}
const styles = StyleSheet.create({
  rootWrapper: {
    flex: 1,
    backgroundColor: '#000', // Nền ngoài cùng trên web
    ...(Platform.OS === 'web' && {
      minHeight: '100vh',
      overflow: 'hidden',
    })
  },
  rootContainer: {
    flex: 1,
    backgroundColor: '#000',
    ...(Platform.OS === 'web' && {
      maxWidth: 480,
      alignSelf: 'center',
      width: '100%',
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 0 },
      shadowOpacity: 0.5,
      shadowRadius: 20,
    })
  }
});
