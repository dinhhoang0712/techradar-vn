import { DM } from '@/constants/theme';
import { Tabs } from 'expo-router';
import React from 'react';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { View, Platform } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

export default function TabLayout() {
  const insets = useSafeAreaInsets();

  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: '#FFFFFF',
        tabBarInactiveTintColor: 'rgba(255, 255, 255, 0.45)',
        tabBarShowLabel: false,
        tabBarStyle: {
          backgroundColor: '#000000',
          borderTopColor: DM.border,
          borderTopWidth: 1,
          height: Platform.OS === 'web' ? 60 : 55 + insets.bottom,
          paddingBottom: Platform.OS === 'web' ? 8 : insets.bottom,
          paddingTop: 8,
        },
        headerShown: false,
      }}>
      <Tabs.Screen
        name="index"
        options={{
          title: 'Trang chủ',
          tabBarIcon: ({ focused, color }) => (
            <TabIcon name={focused ? 'home' : 'home-outline'} color={color} focused={focused} />
          ),
        }}
      />
      <Tabs.Screen
        name="compare"
        options={{
          title: 'So sánh',
          tabBarIcon: ({ focused, color }) => (
            <TabIcon name={focused ? 'git-compare' : 'git-compare-outline'} color={color} focused={focused} />
          ),
        }}
      />
      <Tabs.Screen
        name="cluster"
        options={{
          title: 'Phân cụm',
          tabBarIcon: ({ focused, color }) => (
            <TabIcon name={focused ? 'albums' : 'albums-outline'} color={color} focused={focused} />
          ),
        }}
      />
      <Tabs.Screen
        name="graph"
        options={{
          title: 'Đồ thị',
          tabBarIcon: ({ focused, color }) => (
            <TabIcon name={focused ? 'git-network' : 'git-network-outline'} color={color} focused={focused} />
          ),
        }}
      />
      <Tabs.Screen
        name="chat"
        options={{
          title: 'AI Chat',
          tabBarIcon: ({ focused, color }) => (
            <TabIcon name={focused ? 'chatbubble-ellipses' : 'chatbubble-ellipses-outline'} color={color} focused={focused} />
          ),
        }}
      />
      <Tabs.Screen
        name="profile"
        options={{
          title: 'Tài khoản',
          tabBarIcon: ({ focused, color }) => (
            <TabIcon name={focused ? 'person' : 'person-outline'} color={color} focused={focused} />
          ),
        }}
      />
    </Tabs>
  );
}

function TabIcon({ name, color, focused }: { name: any; color: string; focused: boolean }) {
  return (
    <View style={{ alignItems: 'center', justifyContent: 'center' }}>
      {focused && (
        <View style={{
          position: 'absolute',
          top: -8,
          width: 32,
          height: 3,
          borderRadius: 2,
          backgroundColor: '#FFFFFF',
        }} />
      )}
      <Ionicons name={name} size={26} color={color} />
    </View>
  );
}
