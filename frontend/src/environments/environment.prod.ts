export const environment = {
  production: true,
  firebase: {
    apiKey: "${FIREBASE_API_KEY}",
    authDomain: "${FIREBASE_AUTH_DOMAIN}", 
    projectId: "${FIREBASE_PROJECT_ID}",
    storageBucket: "${FIREBASE_STORAGE_BUCKET}",
    messagingSenderId: "${FIREBASE_MESSAGING_SENDER_ID}",
    appId: "${FIREBASE_APP_ID}",
    measurementId: "${FIREBASE_MEASUREMENT_ID}"
  },
  apis: {
    // Production Cloud Function URLs - automatically determined from project ID
    generateMemoryPlant: `https://${process.env['FIREBASE_PROJECT_ID'] || 'us-central1'}-${process.env['FIREBASE_PROJECT_ID']}.cloudfunctions.net/generateMemoryPlant`,
    analyzeMemories: `https://${process.env['FIREBASE_PROJECT_ID'] || 'us-central1'}-${process.env['FIREBASE_PROJECT_ID']}.cloudfunctions.net/analyzeMemories`,
    getAtmosphere: `https://${process.env['FIREBASE_PROJECT_ID'] || 'us-central1'}-${process.env['FIREBASE_PROJECT_ID']}.cloudfunctions.net/getAtmosphere`,
    exportGarden: `https://${process.env['FIREBASE_PROJECT_ID'] || 'us-central1'}-${process.env['FIREBASE_PROJECT_ID']}.cloudfunctions.net/exportGarden`,
    getInsightAudio: `https://${process.env['FIREBASE_PROJECT_ID'] || 'us-central1'}-${process.env['FIREBASE_PROJECT_ID']}.cloudfunctions.net/getInsightAudio`,
    poeticRephrasing: `https://${process.env['FIREBASE_PROJECT_ID'] || 'us-central1'}-${process.env['FIREBASE_PROJECT_ID']}.cloudfunctions.net/poeticRephrasing`,
    chatWebSocket: `wss://${process.env['FIREBASE_PROJECT_ID'] || 'us-central1'}-${process.env['FIREBASE_PROJECT_ID']}.cloudfunctions.net/chatWebSocket`
  },
  features: {
    enableAnalytics: true,
    enablePerformanceMonitoring: true,
    enableCrashlytics: true,
    enableNotifications: true,
    maxMemoriesPerUser: 1000,
    maxConversationHistory: 100,
    enableAdvancedAR: true,
    enableProfanityFilter: true,
    enableRateLimiting: true
  },
  monitoring: {
    sentryDsn: "${SENTRY_DSN}",
    logLevel: "warn",
    enableUserTracking: true,
    enableErrorReporting: true
  },
  performance: {
    enableServiceWorker: true,
    enableLazyLoading: true,
    enableImageOptimization: true,
    enableBundleAnalysis: false
  }
};
