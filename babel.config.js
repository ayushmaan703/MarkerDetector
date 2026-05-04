module.exports = {
  presets: ['module:@react-native/babel-preset'],
  plugins: [
    'react-native-worklets-core/plugin', // only if needed (Vision Camera)
    'react-native-reanimated/plugin',    // MUST be last
  ],
};