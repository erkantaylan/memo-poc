import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { colors } from '@/theme/colors';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <StatusBar style="light" backgroundColor={colors.bg} />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.bg },
          headerTintColor: colors.fg,
          headerTitleStyle: { color: colors.fg, fontWeight: '500' },
          contentStyle: { backgroundColor: colors.bg },
        }}
      >
        <Stack.Screen name="index" options={{ title: 'Memorize' }} />
        <Stack.Screen name="first-letter" options={{ title: 'First letters' }} />
        <Stack.Screen name="typing" options={{ title: 'Type it out' }} />
        <Stack.Screen name="bionic" options={{ title: 'Bionic reading' }} />
      </Stack>
    </SafeAreaProvider>
  );
}
