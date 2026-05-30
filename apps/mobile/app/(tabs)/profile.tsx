import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Alert, Platform, ActivityIndicator } from 'react-native';
import { DM } from '@/constants/theme';
import { useRouter } from 'expo-router';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { logoutUser } from '../../api/authService';
import { getUserProfile } from '../../api/userService';

export default function ProfileScreen() {
  const router = useRouter();
  const [profile, setProfile] = useState<{ full_name?: string; email?: string; avatar_url?: string } | null>(null);
  const [loadingProfile, setLoadingProfile] = useState(true);

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const res = await getUserProfile();
        const data = res?.data ?? res ?? {};
        const flatData = {
            full_name: data.user?.full_name || data.full_name || '',
            email: data.user?.email || data.email || '',
            avatar_url: data.profile?.avatar_url || data.avatar_url || ''
        };
        setProfile(flatData);
      } catch (e) {
        console.warn('[Profile] Failed to load user profile:', e);
      } finally {
        setLoadingProfile(false);
      }
    };
    fetchProfile();
  }, []);

  // Lấy chữ cái đầu của tên để hiển thị avatar fallback
  const avatarLetter = profile?.full_name
    ? profile.full_name.charAt(0).toUpperCase()
    : profile?.email
    ? profile.email.charAt(0).toUpperCase()
    : 'U';

  const handleLogout = async () => {
    const performLogout = async () => {
      try {
        await logoutUser();
      } catch (e) {
        // Vẫn cho phép logout ở client ngay cả khi gọi API thất bại
      }
      await AsyncStorage.multiRemove(['access_token', 'refresh_token']);
      router.replace('/login');
    };

    if (Platform.OS === 'web') {
      if (window.confirm('Bạn có chắc chắn muốn đăng xuất?')) {
        await performLogout();
      }
    } else {
      Alert.alert('Đăng xuất', 'Bạn có chắc chắn muốn đăng xuất?', [
        { text: 'Huỷ', style: 'cancel' },
        {
          text: 'Đăng xuất',
          style: 'destructive',
          onPress: performLogout
        },
      ]);
    }
  };

  return (
    <View style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Tài khoản</Text>
        </View>

        <View style={styles.profileCard}>
          <View style={styles.avatarLarge}>
            {loadingProfile ? (
              <ActivityIndicator size="small" color="#fff" />
            ) : (
              <Text style={styles.avatarText}>{avatarLetter}</Text>
            )}
          </View>
          <View style={styles.info}>
            {loadingProfile ? (
              <>
                <View style={styles.skeletonName} />
                <View style={styles.skeletonEmail} />
              </>
            ) : (
              <>
                <Text style={styles.name}>{profile?.full_name ?? 'Người dùng'}</Text>
                <Text style={styles.email}>{profile?.email ?? 'user@techradar.vn'}</Text>
              </>
            )}
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>CÀI ĐẶT</Text>
          
          <TouchableOpacity style={styles.item} onPress={() => router.push('/personal-info')}>
            <Text style={styles.itemText}>Thông tin cá nhân</Text>
          </TouchableOpacity>
          
          <TouchableOpacity style={styles.item}>
            <Text style={styles.itemText}>Cài đặt thông báo</Text>
          </TouchableOpacity>
          
          <TouchableOpacity style={styles.item}>
            <Text style={styles.itemText}>Bảo mật & Quyền riêng tư</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>HỖ TRỢ</Text>
          
          <TouchableOpacity style={styles.item}>
            <Text style={styles.itemText}>Trợ giúp & Phản hồi</Text>
          </TouchableOpacity>
          
          <TouchableOpacity style={styles.item}>
            <Text style={styles.itemText}>Về chúng tôi</Text>
          </TouchableOpacity>
        </View>

        <TouchableOpacity style={styles.logoutBtn} onPress={handleLogout}>
          <Text style={styles.logoutBtnText}>ĐĂNG XUẤT</Text>
        </TouchableOpacity>

        <View style={styles.footer}>
          <Text style={styles.footerText}>TECHRADAR MOBILE · PHIÊN BẢN 2.0.0</Text>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: DM.bg,
    paddingTop: 48,
    ...(Platform.OS === 'web' && { alignSelf: 'center', width: '100%' }),
  },
  header: {
    paddingHorizontal: 24,
    marginBottom: 24,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: '900',
    color: DM.text,
    letterSpacing: 1,
  },
  profileCard: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 24,
    marginBottom: 32,
    gap: 20,
  },
  avatarLarge: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: '#4a5568',
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    fontSize: 24,
    fontWeight: '800',
    color: '#fff',
  },
  info: {
    flex: 1,
  },
  name: {
    fontSize: 18,
    fontWeight: '700',
    color: DM.text,
  },
  email: {
    fontSize: 13,
    color: DM.text3,
    marginTop: 2,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 10,
    fontWeight: '800',
    color: DM.text3,
    marginLeft: 24,
    marginBottom: 8,
    letterSpacing: 1,
  },
  item: {
    paddingHorizontal: 24,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: DM.border,
    backgroundColor: DM.surface,
  },
  itemText: {
    fontSize: 14,
    color: DM.text2,
    fontWeight: '500',
  },
  logoutBtn: {
    marginHorizontal: 24,
    marginTop: 12,
    paddingVertical: 16,
    borderRadius: 12,
    backgroundColor: '#fff',
    alignItems: 'center',
  },
  logoutBtnText: {
    fontSize: 14,
    fontWeight: '900',
    color: '#000',
    letterSpacing: 1,
  },
  footer: {
    marginTop: 48,
    paddingBottom: 48,
    alignItems: 'center',
  },
  footerText: {
    fontSize: 10,
    color: '#333',
    fontWeight: '700',
    letterSpacing: 2,
  },
  skeletonName: {
    height: 16,
    borderRadius: 8,
    backgroundColor: DM.border,
    width: 140,
    marginBottom: 8,
  },
  skeletonEmail: {
    height: 12,
    borderRadius: 6,
    backgroundColor: DM.border,
    width: 200,
  },
});
