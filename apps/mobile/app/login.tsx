import React, { useState } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { DM } from '../constants/theme';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { loginUser, getSystemStatus } from '../api/authService';
import { useRouter } from 'expo-router';

const resolveLoginErrorMessage = (error: any) => {
  const status = error?.status;
  const message = String(error?.message || '').trim();
  const lower = message.toLowerCase();

  if (status === 401 || lower.includes('invalid email or password')) {
    return 'Sai email hoặc mật khẩu. Vui lòng kiểm tra lại.';
  }

  if (status === 400) {
    return message || 'Thông tin đăng nhập không hợp lệ. Vui lòng kiểm tra lại.';
  }

  if (status === 403) {
    return 'Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.';
  }

  return message || 'Không thể đăng nhập. Vui lòng thử lại.';
};

export default function LoginScreen() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async () => {
    if (!email || !password) {
      Alert.alert('Lỗi', 'Vui lòng nhập đầy đủ thông tin');
      return;
    }

    setLoading(true);
    try {
      const status = await getSystemStatus();
      if (status) {
        await AsyncStorage.setItem('feature_graph', String(status.feature_graph));
        await AsyncStorage.setItem('feature_rag', String(status.feature_rag));

        if (status.maintenance_mobile === true) {
          const maintenanceMsg = 'Hệ thống đang bảo trì phiên bản di động. Vui lòng quay lại sau.';
          if (Platform.OS === 'web') {
            window.alert('Thông báo: ' + maintenanceMsg);
          } else {
            Alert.alert('Bảo trì', maintenanceMsg);
          }
          setLoading(false);
          return;
        }
      }

      const response = await loginUser({ email, password });
      if (response && response.access_token) {
        await AsyncStorage.setItem('access_token', response.access_token);
        if (response.refresh_token) {
          await AsyncStorage.setItem('refresh_token', response.refresh_token);
        }
        await AsyncStorage.setItem('login_timestamp', Date.now().toString());
        router.replace('/(tabs)');
      } else {
        const msg = 'Không nhận được mã xác thực từ máy chủ.';
        if (Platform.OS === 'web') {
          window.alert('Đăng nhập thất bại: ' + msg);
        } else {
          Alert.alert('Đăng nhập thất bại', msg);
        }
      }
    } catch (error: any) {
      console.error('Login error:', error);
      const errorMsg = resolveLoginErrorMessage(error);
      if (Platform.OS === 'web') {
        window.alert('Lỗi đăng nhập: ' + errorMsg);
      } else {
        Alert.alert('Lỗi đăng nhập', errorMsg);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={styles.container}
    >
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <Text style={styles.logo}>TechRadar</Text>
          <Text style={styles.tagline}>Theo dõi xu hướng công nghệ thông minh</Text>
        </View>

        <View style={styles.formCard}>
          <Text style={styles.title}>Đăng nhập</Text>
          <Text style={styles.subtitle}>Chào mừng bạn quay trở lại</Text>

          <View style={styles.inputGroup}>
            <Text style={styles.label}>EMAIL</Text>
            <TextInput
              style={styles.input}
              placeholder="example@email.com"
              placeholderTextColor={DM.text3}
              value={email}
              onChangeText={setEmail}
              keyboardType="email-address"
              autoCapitalize="none"
            />
          </View>

          <View style={styles.inputGroup}>
            <Text style={styles.label}>MẬT KHẨU</Text>
            <TextInput
              style={styles.input}
              placeholder="••••••••"
              placeholderTextColor={DM.text3}
              value={password}
              onChangeText={setPassword}
              secureTextEntry
            />
          </View>

          <TouchableOpacity
            style={[styles.loginBtn, loading && styles.loginBtnDisabled]}
            onPress={handleLogin}
            disabled={loading}
          >
            {loading ? (
              <ActivityIndicator color="#000" />
            ) : (
              <Text style={styles.loginBtnText}>ĐĂNG NHẬP</Text>
            )}
          </TouchableOpacity>

          <View style={styles.footer}>
            <Text style={styles.footerText}>Chưa có tài khoản?</Text>
            <TouchableOpacity onPress={() => router.push('/register')}>
              <Text style={styles.footerLink}>Đăng ký ngay</Text>
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.bottomBranding}>
          <Text style={styles.brandingText}>HARDCORE MINIMALISM · 2026</Text>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
    ...(Platform.OS === 'web' && { alignSelf: 'center', width: '100%' }),
  },
  scrollContent: {
    flexGrow: 1,
    padding: 24,
    justifyContent: 'center',
  },
  header: {
    alignItems: 'center',
    marginBottom: 48,
  },
  logo: {
    fontSize: 32,
    fontWeight: '900',
    color: '#fff',
    letterSpacing: 2,
  },
  tagline: {
    fontSize: 12,
    color: DM.text2,
    marginTop: 8,
    textAlign: 'center',
  },
  formCard: {
    backgroundColor: '#000',
    borderWidth: 1,
    borderColor: DM.border,
    borderRadius: 24,
    padding: 24,
  },
  title: {
    fontSize: 24,
    fontWeight: '800',
    color: '#fff',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: DM.text3,
    marginBottom: 32,
  },
  inputGroup: {
    marginBottom: 20,
  },
  label: {
    fontSize: 10,
    fontWeight: '700',
    color: DM.text2,
    marginBottom: 8,
    letterSpacing: 1,
  },
  input: {
    backgroundColor: '#050505',
    borderWidth: 1,
    borderColor: DM.border,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    color: '#fff',
    fontSize: 14,
  },
  loginBtn: {
    backgroundColor: '#fff',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 12,
  },
  loginBtnDisabled: {
    opacity: 0.5,
  },
  loginBtnText: {
    color: '#000',
    fontWeight: '800',
    fontSize: 14,
    letterSpacing: 1,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginTop: 24,
    gap: 8,
  },
  footerText: {
    fontSize: 13,
    color: DM.text3,
  },
  footerLink: {
    fontSize: 13,
    color: '#fff',
    fontWeight: '700',
  },
  bottomBranding: {
    marginTop: 64,
    alignItems: 'center',
  },
  brandingText: {
    fontSize: 10,
    color: '#222',
    fontWeight: '600',
    letterSpacing: 2,
  },
});
