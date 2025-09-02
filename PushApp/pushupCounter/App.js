
import React, { useState, useEffect, useRef } from 'react';
import {
  StyleSheet,
  Text,
  View,
  Dimensions,
  Platform,
} from 'react-native';
import { Camera } from 'react-native-camera';
import * as tf from '@tensorflow/tfjs';
import * as posenet from '@tensorflow-models/posenet';
import { cameraWithTensors } from '@tensorflow/tfjs-react-native';
import Svg, { Circle, Line } from 'react-native-svg';

const TensorCamera = cameraWithTensors(Camera);

const { width, height } = Dimensions.get('window');

// Model input size
const TENSOR_SIZE = {
  width: 152,
  height: 200,
};

export default function App() {
  const [isTfReady, setIsTfReady] = useState(false);
  const [model, setModel] = useState(null);
  const [poses, setPoses] = useState(null);
  const [pushupCount, setPushupCount] = useState(0);
  const [isPushup, setIsPushup] = useState(false);
  const [feedback, setFeedback] = useState('');

  const rafId = useRef(null);

  useEffect(() => {
    async function setup() {
      await tf.ready();
      setIsTfReady(true);
      const model = await posenet.load({
        architecture: 'MobileNetV1',
        outputStride: 16,
        inputResolution: TENSOR_SIZE,
        multiplier: 0.75,
      });
      setModel(model);
    }
    setup();
  }, []);

  useEffect(() => {
    return () => {
      if (rafId.current) {
        cancelAnimationFrame(rafId.current);
      }
    };
  }, []);

  const handleCameraStream = (images) => {
    const loop = async () => {
      const nextImageTensor = images.next().value;
      if (model && nextImageTensor) {
        const poses = await model.estimateMultiplePoses(nextImageTensor, {
          flipHorizontal: true,
          maxDetections: 1,
          scoreThreshold: 0.7,
          nmsRadius: 20,
        });
        setPoses(poses);
        tf.dispose([nextImageTensor]);

        if (poses && poses.length > 0) {
          detectPushup(poses[0]);
        }
      }
      rafId.current = requestAnimationFrame(loop);
    };
    loop();
  };

  const detectPushup = (pose) => {
    const shoulder = pose.keypoints.find((k) => k.part === 'leftShoulder');
    const elbow = pose.keypoints.find((k) => k.part === 'leftElbow');
    const wrist = pose.keypoints.find((k) => k.part === 'leftWrist');
    const hip = pose.keypoints.find((k) => k.part === 'leftHip');
    const knee = pose.keypoints.find((k) => k.part === 'leftKnee');

    if (shoulder && elbow && wrist && hip && knee) {
      const shoulderAngle = getAngle(hip, shoulder, elbow);
      const elbowAngle = getAngle(shoulder, elbow, wrist);

      if (
        shoulderAngle > 150 &&
        elbowAngle > 150 &&
        isStraight(shoulder, hip, knee)
      ) {
        if (!isPushup) {
          setFeedback('Down');
        }
        setIsPushup(true);
      } else if (isPushup && elbowAngle < 90) {
        setPushupCount((prev) => prev + 1);
        setIsPushup(false);
        setFeedback('Up');
      }
    }
  };

  const isStraight = (p1, p2, p3) => {
    const angle = getAngle(p1, p2, p3);
    return angle > 160;
  };

  const getAngle = (p1, p2, p3) => {
    const a = Math.pow(p2.position.x - p1.position.x, 2) + Math.pow(p2.position.y - p1.position.y, 2);
    const b = Math.pow(p2.position.x - p3.position.x, 2) + Math.pow(p2.position.y - p3.position.y, 2);
    const c = Math.pow(p3.position.x - p1.position.x, 2) + Math.pow(p3.position.y - p1.position.y, 2);
    return (Math.acos((a + b - c) / Math.sqrt(4 * a * b)) * 180) / Math.PI;
  };

  const renderPose = () => {
    if (poses && poses.length > 0) {
      const keypoints = poses[0].keypoints.filter((k) => k.score > 0.5);
      const skeleton = posenet.getAdjacentKeyPoints(keypoints, 0.5);

      return (
        <Svg height={height} width={width} style={styles.svg}>
          {keypoints.map((k) => (
            <Circle
              key={k.part}
              cx={k.position.x}
              cy={k.position.y}
              r={5}
              strokeWidth={2}
              fill={'#00AA00'}
              stroke={'white'}
            />
          ))}
          {skeleton.map(([p1, p2], i) => (
            <Line
              key={`line_${i}`}
              x1={p1.position.x}
              y1={p1.position.y}
              x2={p2.position.x}
              y2={p2.position.y}
              stroke={'#00AA00'}
              strokeWidth={2}
            />
          ))}
        </Svg>
      );
    }
    return null;
  };

  if (!isTfReady) {
    return (
      <View style={styles.loadingContainer}>
        <Text style={styles.text}>Loading TensorFlow.js</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <TensorCamera
        style={styles.camera}
        type={Camera.Constants.Type.front}
        cameraTextureHeight={TENSOR_SIZE.height}
        cameraTextureWidth={TENSOR_SIZE.width}
        resizeHeight={TENSOR_SIZE.height}
        resizeWidth={TENSOR_SIZE.width}
        resizeDepth={3}
        onReady={handleCameraStream}
        autorender={true}
      />
      <View style={styles.counterContainer}>
        <Text style={styles.text}>Push-ups: {pushupCount}</Text>
        <Text style={styles.text}>{feedback}</Text>
      </View>
      {renderPose()}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  camera: {
    width: '100%',
    height: '100%',
  },
  svg: {
    position: 'absolute',
    zIndex: 10,
  },
  counterContainer: {
    position: 'absolute',
    bottom: 20,
    left: 20,
    backgroundColor: 'rgba(255,255,255,0.7)',
    padding: 10,
    borderRadius: 5,
  },
  text: {
    fontSize: 20,
    fontWeight: 'bold',
  },
});
