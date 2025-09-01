import { Injectable, inject } from '@angular/core';
import { getMessaging, getToken, onMessage } from 'firebase/messaging';
import { doc, setDoc, Firestore } from '@angular/fire/firestore';
import { Auth } from '@angular/fire/auth';

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private firestore: Firestore = inject(Firestore);
  private auth: Auth = inject(Auth);

  constructor() { }

  requestPermission() {
    const messaging = getMessaging();
    getToken(messaging, { vapidKey: 'YOUR_VAPID_KEY' })
      .then((currentToken) => {
        if (currentToken) {
          this.saveToken(currentToken);
        } else {
          console.log('No registration token available. Request permission to generate one.');
        }
      })
      .catch((err) => {
        console.log('An error occurred while retrieving token. ', err);
      });
  }

  private saveToken(token: string) {
    const user = this.auth.currentUser;
    if (user) {
      const tokenDoc = doc(this.firestore, `fcm_tokens/${user.uid}`);
      setDoc(tokenDoc, { token }, { merge: true });
    }
  }

  listenForMessages() {
    const messaging = getMessaging();
    onMessage(messaging, (payload) => {
      console.log('Message received. ', payload);
      // Customize notification handling here
    });
  }
}
