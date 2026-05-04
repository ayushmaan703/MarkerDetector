import React from 'react';
import { StyleSheet, useColorScheme } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import CameraFile from './CameraFile';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

function App() {
  const isDarkMode = useColorScheme() === 'dark';

  return (
    <SafeAreaProvider>
      <GestureHandlerRootView style={{ flex: 1 }}>
        <CameraFile />
      </GestureHandlerRootView>
    </SafeAreaProvider>
  );
}

export default App;

const styles = StyleSheet.create({});
