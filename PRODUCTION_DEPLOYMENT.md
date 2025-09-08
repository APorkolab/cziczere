# Production Deployment Guide

## 🚀 Memory Garden - Production Ready Deployment

This guide covers the complete production deployment process for the Memory Garden application, including all senior-level features and monitoring.

## 🔧 Prerequisites

### Required Services & Accounts
- **Firebase Project** with Blaze plan (required for Cloud Functions)
- **Google Cloud Project** with billing enabled
- **Sentry Account** for error tracking
- **GitHub Repository** with Actions enabled
- **Slack Workspace** for deployment notifications (optional)

### Required Secrets in GitHub
Set these in your GitHub repository settings under Secrets and variables > Actions:

```bash
# Firebase Configuration
FIREBASE_API_KEY=your_firebase_api_key
FIREBASE_AUTH_DOMAIN=your_project.firebaseapp.com
FIREBASE_PROJECT_ID=your_project_id
FIREBASE_STORAGE_BUCKET=your_project.appspot.com
FIREBASE_MESSAGING_SENDER_ID=your_sender_id
FIREBASE_APP_ID=your_app_id
FIREBASE_TOKEN=your_firebase_deploy_token

# Google Cloud Service Account
GCP_SA_KEY=your_service_account_json_key

# Monitoring & Security
SENTRY_DSN=your_sentry_dsn
SENTRY_AUTH_TOKEN=your_sentry_auth_token
SENTRY_ORG=your_sentry_organization
SNYK_TOKEN=your_snyk_token

# WebSocket Domain
WEBSOCKET_DOMAIN=your_websocket_domain.com

# Notifications (Optional)
SLACK_WEBHOOK=your_slack_webhook_url

# Monitoring APIs (Optional)
MONITORING_API_KEY=your_monitoring_api_key
MONITORING_WEBHOOK_URL=your_monitoring_webhook
UPTIME_API_KEY=your_uptime_api_key
UPTIME_WEBHOOK_URL=your_uptime_webhook

# Lighthouse CI
LHCI_GITHUB_APP_TOKEN=your_lighthouse_ci_token
```

## 🏗️ Architecture Overview

### Frontend (Angular 17+)
- **Hosting**: Firebase Hosting with CDN
- **Real-time Features**: Firestore onSnapshot + WebSocket chat
- **State Management**: Redux-like pattern with RxJS
- **Security**: CSP headers, rate limiting, input sanitization
- **Performance**: Lazy loading, bundle optimization, performance monitoring

### Backend (Java 17)
- **Runtime**: Google Cloud Functions
- **AI Integration**: Vertex AI (Gemini 1.5 Flash + Imagen)
- **Database**: Firestore with security rules
- **WebSocket**: Custom Java WebSocket handler
- **Security**: Firebase Auth, rate limiting, input validation

### Infrastructure
- **CI/CD**: GitHub Actions with multi-stage pipeline
- **Monitoring**: Performance tracking, error reporting, security alerts
- **Caching**: Firebase Hosting CDN + browser caching
- **Security**: CSP, HSTS, rate limiting, vulnerability scanning

## 📦 Deployment Steps

### 1. Initial Setup

```bash
# Clone and setup
git clone <your-repository>
cd memory-garden

# Install Firebase CLI
npm install -g firebase-tools

# Login to Firebase
firebase login

# Set Firebase project
firebase use your-project-id
```

### 2. Configure Environment

Update `frontend/src/environments/environment.prod.ts`:
```typescript
export const environment = {
  production: true,
  firebase: {
    // Your Firebase config
  },
  apis: {
    // Your Cloud Function URLs
  },
  features: {
    enableAnalytics: true,
    enablePerformanceMonitoring: true,
    // ... other production features
  }
};
```

### 3. Security Configuration

The application includes comprehensive security measures:

#### Content Security Policy
- Strict CSP headers in `firebase.json`
- XSS protection and content type validation
- Frame busting and referrer policies

#### Rate Limiting
- Frontend: 60 requests/minute, 1000/hour per IP
- Backend: Function-level rate limiting
- Automatic IP blocking for suspicious activity

#### Input Sanitization
- XSS attempt detection and blocking
- File upload validation
- URL safety validation

### 4. Deploy to Production

Push to main branch to trigger automated deployment:

```bash
git add .
git commit -m "Production deployment"
git push origin main
```

The CI/CD pipeline will:
1. ✅ Run quality checks (linting, testing, type checking)
2. 🔒 Security scanning (Snyk, CodeQL, npm audit)
3. 🏗️ Build and deploy backend (Cloud Functions)
4. 🌐 Build and deploy frontend (Firebase Hosting)
5. ⚡ Performance testing (Lighthouse CI)
6. 🧪 Integration testing (Playwright)
7. 📊 Setup monitoring and alerts

## 📊 Monitoring & Observability

### Performance Monitoring
- **Lighthouse CI**: Automated performance scoring
- **Real User Monitoring**: FPS, memory usage, API response times
- **Bundle Analysis**: Automated bundle size tracking
- **Core Web Vitals**: LCP, FID, CLS monitoring

### Error Tracking
- **Sentry Integration**: Automatic error reporting and releases
- **Security Violations**: CSP violations, XSS attempts, rate limit breaches
- **API Failures**: Cloud Function errors and timeouts

### Real-time Dashboards
- **Garden State**: Memory count, user activity, emotional trends
- **System Health**: API response times, error rates, active users
- **Security Status**: Blocked IPs, violation attempts, success rates

## 🔐 Security Features

### Frontend Security
```typescript
// Automatic XSS detection
securityService.sanitizeInput(userInput);

// URL safety validation
securityService.isUrlSafe(url);

// File upload validation
securityService.validateFileUpload(file);

// Rate limit monitoring
securityService.getSecurityReport();
```

### Backend Security
- Firebase Auth token verification
- Input validation and sanitization
- Rate limiting per user/IP
- CORS configuration
- Secure WebSocket connections

## 📈 Performance Optimizations

### Frontend Optimizations
- **Lazy Loading**: Route-based code splitting
- **Tree Shaking**: Unused code elimination
- **Service Worker**: Offline caching (production only)
- **Image Optimization**: WebP conversion and compression
- **Bundle Analysis**: Automated size monitoring

### Backend Optimizations
- **Cold Start Reduction**: Java GraalVM native image (future)
- **Connection Pooling**: Firestore connection reuse
- **Caching**: Memory and Redis caching for AI responses
- **Batch Operations**: Bulk Firestore writes

## 🚨 Incident Response

### Automated Alerts
- **High Error Rate**: >5% API failures trigger alerts
- **Performance Degradation**: <80 Lighthouse score triggers warnings  
- **Security Incidents**: XSS attempts, rate limit breaches
- **Downtime**: Health check failures

### Manual Monitoring
```bash
# Check application health
curl https://your-domain/health

# View recent deployments
firebase hosting:channel:list

# Check function logs
firebase functions:log

# Security report
# Available in admin dashboard
```

## 🔄 Rollback Procedures

### Frontend Rollback
```bash
# List recent deployments
firebase hosting:releases:list

# Rollback to specific release
firebase hosting:clone SOURCE_SITE_ID:SOURCE_CHANNEL_ID DEST_SITE_ID:live
```

### Backend Rollback
```bash
# Deploy previous version
git checkout previous-release-tag
firebase deploy --only functions
```

## 📋 Production Checklist

### Pre-Deploy
- [ ] All secrets configured in GitHub
- [ ] Environment variables updated
- [ ] Security headers configured
- [ ] Monitoring services connected
- [ ] DNS/domain configured

### Post-Deploy Verification
- [ ] All pages load without errors
- [ ] Real-time features working (chat, garden updates)
- [ ] AI features functional (memory generation, insights)
- [ ] Performance scores >80 (Lighthouse)
- [ ] Security headers present
- [ ] Monitoring dashboards populated
- [ ] Error tracking configured

### Ongoing Maintenance
- [ ] Weekly performance reviews
- [ ] Monthly security audits
- [ ] Quarterly dependency updates
- [ ] Regular backup verification

## 🆘 Support & Troubleshooting

### Common Issues

**Deployment Failures**
- Check GitHub Actions logs
- Verify all secrets are set
- Ensure Firebase project has Blaze plan

**Performance Issues**
- Review Lighthouse reports
- Check bundle analysis reports
- Monitor real user metrics

**Security Alerts**
- Review Sentry error reports
- Check security violation logs
- Verify rate limiting configuration

### Getting Help
- Check GitHub Issues
- Review Firebase Console logs
- Contact development team

## 📚 Additional Resources

- [Firebase Hosting Documentation](https://firebase.google.com/docs/hosting)
- [Cloud Functions Documentation](https://cloud.google.com/functions/docs)
- [Angular Performance Guide](https://angular.io/guide/performance)
- [Lighthouse Performance Auditing](https://web.dev/lighthouse-performance/)

---

**Memory Garden Production Team**
Version: 1.0.0 | Last Updated: 2024
