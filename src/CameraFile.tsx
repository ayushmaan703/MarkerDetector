import React, { useEffect, useRef, useState } from 'react';
import { View, StyleSheet, Button, Text, Image, Alert } from 'react-native';
import {
  Camera,
  useCameraDevice,
  useCameraPermission,
  usePhotoOutput,
} from 'react-native-vision-camera';
import { MarkerDetector } from '../src/native/MarkerDetector';

export default function CameraFile() {
  const device = useCameraDevice('back');
  const { hasPermission, requestPermission } = useCameraPermission();
  const photoOutput = usePhotoOutput();

  const cameraRef = useRef<any>(null);

  const [processing, setProcessing] = useState(false);
  const [markers, setMarkers] = useState<string[]>([]);

  useEffect(() => {
    requestPermission();
  }, []);

  if (!device || !hasPermission) {
    return <View style={{ flex: 1, backgroundColor: 'black' }} />;
  }

  const scan = async () => {
    if (!cameraRef.current || processing) return;

    try {
      setProcessing(true);

      const photo = await photoOutput.capturePhotoToFile(
        { flashMode: 'off' },
        {},
      );

      const result = await MarkerDetector.detectEdgesAndContours(
        photo.filePath,
      );

      if (result && result.image) {
        Alert.alert('Success', 'Valid marker detected ✅');

        setMarkers(prev => {
          if (prev.includes(result.image)) return prev;
          if (prev.length >= 20) return prev;
          return [...prev, result.image];
        });
      } else {
        Alert.alert('Invalid', 'Not a valid marker ❌');
      }
    } catch (e) {
      Alert.alert('Invalid', 'Not a valid marker ❌');
    } finally {
      setProcessing(false);
    }
  };

  return (
    <View style={styles.container}>
      <Camera
        ref={cameraRef}
        style={StyleSheet.absoluteFill}
        device={device}
        isActive={true}
        outputs={[photoOutput]}
      />

      <View style={styles.topBar}>
        <Text style={styles.title}>Marker Scanner</Text>
      </View>

      {processing && (
        <View style={styles.processing}>
          <Text style={styles.processingText}>Scanning...</Text>
        </View>
      )}

      <View style={styles.bottomContainer}>
        <View style={styles.controls}>
          <Text style={styles.resetBtn} onPress={() => setMarkers([])}>
            Reset
          </Text>
          <View style={styles.scanWrapper} >
            <Text style={styles.scanBtn} onPress={scan}>
              ●
            </Text>
          </View>
          <Text style={styles.counter}>{markers.length}/20</Text>
        </View>
      </View>

      {markers.length > 0 && (
        <View style={styles.gridContainer}>
          <View style={styles.grid}>
            {markers.map((img, i) => (
              <Image
                key={i}
                source={{ uri: 'file://' + img }}
                style={styles.gridImage}
              />
            ))}
          </View>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'black',
  },

  topBar: {
    position: 'absolute',
    top: 50,
    width: '100%',
    alignItems: 'center',
  },

  title: {
    color: 'white',
    fontSize: 18,
    fontWeight: '600',
    letterSpacing: 1,
  },

  processing: {
    position: 'absolute',
    top: '45%',
    alignSelf: 'center',
    backgroundColor: 'rgba(0,0,0,0.7)',
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 20,
  },

  processingText: {
    color: 'white',
    fontSize: 14,
  },

  bottomContainer: {
    position: 'absolute',
    bottom: 40,
    width: '100%',
    alignItems: 'center',
  },

  controls: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    width: '80%',
  },

  resetBtn: {
    color: '#ff4d4d',
    fontSize: 14,
  },

  counter: {
    color: 'white',
    fontSize: 14,
  },

  scanWrapper: {
    borderWidth: 3,
    borderColor: 'white',
    borderRadius: 50,
    padding: 6,
    width: 60,
    height: 60,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#ffffff',
  },

  scanBtn: {
    fontSize: 1000,
    color: 'red',
    textAlign: 'center',
  },

  gridContainer: {
    position: 'absolute',
    bottom: 110,
    width: '100%',
    alignItems: 'center',
  },

  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    width: '90%',
    backgroundColor: 'rgba(0,0,0,0.4)',
    padding: 10,
    borderRadius: 16,
  },

  gridImage: {
    width: 55,
    height: 55,
    margin: 5,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#00ff99',
  },
});
