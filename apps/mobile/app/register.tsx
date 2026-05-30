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
import { registerUser } from '../api/authService';
import { useRouter } from 'expo-router';

const resolveRegisterErrorMessage = (error: any) => {
  const status = error?.status;
  const message = String(error?.message || '').trim();
  const lower = message.toLowerCase();

  if (status === 409 || lower.includes('already registered') || lower.includes('email already taken')) {
    return 'Email đã tồn tại. Vui lòng dùng email khác.';
  }

  if (status === 400) {
    return message || 'Thông tin đăng ký không hợp lệ. Vui lòng kiểm tra lại.';
  }

  return message || 'Không thể tạo tài khoản. Vui lòng thử lại.';
};

export default function RegisterScreen() {
  const router = useRouter();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleRegister = async () => {
    if (!name || !email || !password || !confirmPassword) {
      Alert.alert('Lỗi', 'Vui lòng nhập đầy đủ thông tin');
      return;
    }

    if (password !== confirmPassword) {
      Alert.alert('Lỗi', 'Mật khẩu xác nhận không khớp');
      return;
    }

    setLoading(true);
    try {
      await registerUser({
        email,
        password,
        full_name: name,
        confirm_password: confirmPassword,
      });

      if (Platform.OS === 'web') {
        window.alert('Thành công: Tài khoản của bạn đã được khởi tạo thành công!');
        router.replace('/login');
      } else {
        Alert.alert('Thành công', 'Tài khoản của bạn đã được khởi tạo thành công!', [
          { text: 'Đăng nhập ngay', onPress: () => router.replace('/login') },
        ]);
      }
    } catch (error: any) {
      console.error('Register error:', error);
      const errorMsg = resolveRegisterErrorMessage(error);
      if (Platform.OS === 'web') {
        window.alert('Lỗi đăng ký: ' + errorMsg);
      } else {
        Alert.alert('Lỗi đăng ký', errorMsg);
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
          <Text style={styles.tagline}>Gia nhập cộng đồng dữ liệu công nghệ</Text>
        </View>

        <View style={styles.formCard}>
          <Text style={styles.title}>Đăng ký</Text>
          <Text style={styles.subtitle}>Bắt đầu hành trình của bạn ngay hôm nay</Text>

          <View style={styles.inputGroup}>
            <Text style={styles.label}>HỌ VÀ TÊN</Text>
            <TextInput
              style={styles.input}
              placeholder="Nguyễn Văn A"
              placeholderTextColor={DM.text3}
              value={name}
              onChangeText={setName}
            />
          </View>

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

          <View style={styles.inputGroup}>
            <Text style={styles.label}>XÁC NHẬN MẬT KHẨU</Text>
            <TextInput
              style={styles.input}
              placeholder="••••••••"
              placeholderTextColor={DM.text3}
              value={confirmPassword}
              onChangeText={setConfirmPassword}
              secureTextEntry
            />
          </View>

          <TouchableOpacity
            style={[styles.registerBtn, loading && styles.registerBtnDisabled]}
            onPress={handleRegister}
            disabled={loading}
          >
            {loading ? (
              <ActivityIndicator color="#000" />
            ) : (
              <Text style={styles.registerBtnText}>TẠO TÀI KHOẢN</Text>
            )}
          </TouchableOpacity>

          <View style={styles.footer}>
            <Text style={styles.footerText}>Đã có tài khoản?</Text>
            <TouchableOpacity onPress={() => router.push('/login')}>
              <Text style={styles.footerLink}>Đăng nhập</Text>
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.bottomBranding}>
          <Text style={styles.brandingText}>JOIN THE FUTURE · 2026</Text>
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
  registerBtn: {
    backgroundColor: '#fff',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 12,
  },
  registerBtnDisabled: {
    opacity: 0.5,
  },
  registerBtnText: {
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
