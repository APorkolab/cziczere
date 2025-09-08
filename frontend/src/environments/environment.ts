// This file can be replaced during build by using the `fileReplacements` array.
// `ng build` replaces `environment.ts` with `environment.prod.ts`.
// The list of file replacements can be found in `angular.json`.

export const environment = {
  production: false,
  firebase: {
    apiKey: 'YOUR_API_KEY',
    authDomain: 'YOUR_AUTH_DOMAIN',
    projectId: 'YOUR_PROJECT_ID',
    storageBucket: 'YOUR_STORAGE_BUCKET',
    messagingSenderId: 'YOUR_MESSAGING_SENDER_ID',
    appId: 'YOUR_APP_ID',
  },
  apis: {
    // Local development URLs
    generateMemoryPlant: 'http://127.0.0.1:5001/cziczere-ai/us-central1/generateMemoryPlant',
    analyzeMemories: 'http://127.0.0.1:5001/cziczere-ai/us-central1/analyzeMemories',
    getAtmosphere: 'http://127.0.0.1:5001/cziczere-ai/us-central1/getAtmosphere',
    exportGarden: 'http://127.0.0.1:5001/cziczere-ai/us-central1/exportGarden',
    getInsightAudio: 'http://127.0.0.1:5001/cziczere-ai/us-central1/getInsightAudio',
    poeticRephrasing: 'http://127.0.0.1:5001/cziczere-ai/us-central1/poeticRephrasing',
    chatWebSocket: 'ws://localhost:8080/chat'
  },
  features: {
    enableAnalytics: false,
    enablePerformanceMonitoring: false,
    enableCrashlytics: false,
    enableNotifications: false,
    maxMemoriesPerUser: 100,
    maxConversationHistory: 50,
    enableAdvancedAR: true,
    enableProfanityFilter: false,
    enableRateLimiting: false
  },
  monitoring: {
    sentryDsn: '',
    logLevel: 'debug',
    enableUserTracking: false,
    enableErrorReporting: true
  },
  performance: {
    enableServiceWorker: false,
    enableLazyLoading: false,
    enableImageOptimization: false,
    enableBundleAnalysis: true
  }
};
