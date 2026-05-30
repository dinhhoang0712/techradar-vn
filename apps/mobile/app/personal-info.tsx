import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, TextInput, ActivityIndicator, Alert, Platform } from 'react-native';
import { useRouter } from 'expo-router';
import { DM } from '@/constants/theme';
import { getUserProfile, updateUserProfile } from '../api/userService';
import { Ionicons } from '@expo/vector-icons';

export default function PersonalInfoScreen() {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [formData, setFormData] = useState({
    full_name: '',
    email: '',
    bio: '',
    job_role: '',
    location: '',
  });

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const res = await getUserProfile();
        const data = res?.data ?? res ?? {};
        const profileInfo = data.profile || {};
        const userInfo = data.user || data || {};

        setFormData({
          full_name: userInfo.full_name || '',
          email: userInfo.email || '',
          bio: profileInfo.bio || '',
          job_role: profileInfo.job_role || '',
          location: profileInfo.location || '',
        });
      } catch (error) {
        console.warn('[PersonalInfo] Failed to load profile:', error);
        Alert.alert('Lỗi', 'Không thể tải thông tin cá nhân. Vui lòng thử lại sau.');
      } finally {
        setLoading(false);
      }
    };

    fetchProfile();
  }, []);

  const handleSave = async () => {
    if (!formData.full_name.trim()) {
      Alert.alert('Thiếu thông tin', 'Vui lòng nhập họ tên.');
      return;
    }

    setSaving(true);
    try {
      await updateUserProfile({
        full_name: formData.full_name,
        bio: formData.bio,
        job_role: formData.job_role,
        location: formData.location,
      });

      if (Platform.OS === 'web') {
        window.alert('Cập nhật thông tin thành công!');
      } else {
        Alert.alert('Thành công', 'Thông tin cá nhân đã được cập nhật.');
      }
      
      // Có thể quay lại hoặc giữ nguyên màn hình
      // router.back();
    } catch (error) {
      console.error('[PersonalInfo] Error updating profile', error);
      Alert.alert('Lỗi', 'Không thể cập nhật thông tin. Vui lòng thử lại.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <View style={[styles.container, styles.center]}>
        <ActivityIndicator size="large" color={DM.primary} />
        <Text style={{ color: DM.text2, marginTop: 10 }}>Đang tải dữ liệu...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
          <Ionicons name="arrow-back" size={24} color={DM.text} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Thông tin cá nhân</Text>
        <View style={{ width: 24 }} /> {/* Placeholder for spacing */}
      </View>

      <ScrollView showsVerticalScrollIndicator={false} style={styles.content}>
        <View style={styles.formGroup}>
          <Text style={styles.label}>Họ và tên</Text>
          <TextInput
            style={styles.input}
            value={formData.full_name}
            onChangeText={(text) => setFormData({ ...formData, full_name: text })}
            placeholder="Nhập họ và tên..."
            placeholderTextColor={DM.text3}
          />
        </View>

        <View style={styles.formGroup}>
          <Text style={styles.label}>Chức danh / Vai trò</Text>
          <TextInput
            style={styles.input}
            value={formData.job_role}
            onChangeText={(text) => setFormData({ ...formData, job_role: text })}
            placeholder="VD: Software Engineer, Data Analyst..."
            placeholderTextColor={DM.text3}
          />
        </View>

        <View style={styles.formGroup}>
          <Text style={styles.label}>Khu vực làm việc</Text>
          <TextInput
            style={styles.input}
            value={formData.location}
            onChangeText={(text) => setFormData({ ...formData, location: text })}
            placeholder="VD: TP. Hồ Chí Minh, Hà Nội..."
            placeholderTextColor={DM.text3}
          />
        </View>

        <View style={styles.formGroup}>
          <Text style={styles.label}>Giới thiệu bản thân</Text>
          <TextInput
            style={[styles.input, styles.textArea]}
            value={formData.bio}
            onChangeText={(text) => setFormData({ ...formData, bio: text })}
            placeholder="Giới thiệu ngắn về kinh nghiệm của bạn..."
            placeholderTextColor={DM.text3}
            multiline
            textAlignVertical="top"
          />
        </View>

        <TouchableOpacity 
          style={[styles.saveBtn, saving && styles.saveBtnDisabled]} 
          onPress={handleSave}
          disabled={saving}
        >
          {saving ? (
            <ActivityIndicator size="small" color="#000" />
          ) : (
            <Text style={styles.saveBtnText}>LƯU THAY ĐỔI</Text>
          )}
        </TouchableOpacity>
        
        <View style={styles.footerSpace} />
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
  center: {
    justifyContent: 'center',
    alignItems: 'center',
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
  backButton: {
    padding: 8,
    marginLeft: -8,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '800',
    color: DM.text,
  },
  content: {
    padding: 24,
  },
  formGroup: {
    marginBottom: 20,
  },
  label: {
    fontSize: 12,
    fontWeight: '700',
    color: DM.text3,
    marginBottom: 8,
    letterSpacing: 0.5,
  },
  input: {
    backgroundColor: DM.surface2,
    borderWidth: 1,
    borderColor: DM.border,
    borderRadius: DM.radiusSm,
    paddingHorizontal: 16,
    paddingVertical: 14,
    fontSize: 14,
    color: DM.text,
  },
  inputDisabled: {
    backgroundColor: DM.surface,
    color: DM.text3,
  },
  textArea: {
    minHeight: 100,
  },
  saveBtn: {
    backgroundColor: DM.primary,
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 12,
    shadowColor: DM.primary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 4,
  },
  saveBtnDisabled: {
    opacity: 0.7,
  },
  saveBtnText: {
    color: '#000',
    fontSize: 14,
    fontWeight: '800',
    letterSpacing: 1,
  },
  footerSpace: {
    height: 48,
  },
});
