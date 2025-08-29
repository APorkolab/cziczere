import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Auth, idToken, user } from '@angular/fire/auth';
import { Firestore, collection, collectionData, query, where } from '@angular/fire/firestore';
import { switchMap, first, Observable, of } from 'rxjs';

export interface MemoryData {
  userId: string;
  userText: string;
  imagePrompt: string;
  imageUrl: string;
  timestamp: number;
}

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private auth: Auth = inject(Auth);
  private http: HttpClient = inject(HttpClient);
  private firestore: Firestore = inject(Firestore);
  private user$ = user(this.auth);

  // TODO: Replace with the actual URL of your deployed Cloud Function.
  private functionUrl = 'http://127.0.0.1:5001/cziczere-ai/us-central1/generateMemoryPlant';

  createMemory(text: string): Observable<MemoryData> {
    return idToken(this.auth).pipe(
      first(),
      switchMap(token => {
        if (!token) {
          throw new Error('User not logged in!');
        }
        const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
        const body = { text };
        return this.http.post<MemoryData>(this.functionUrl, body, { headers });
      })
    );
  }

  getMemories(): Observable<MemoryData[]> {
    return this.user$.pipe(
      switchMap(currentUser => {
        if (!currentUser) {
          return of([]); // Return empty array if no user is logged in
        }
        const memoriesCollection = collection(this.firestore, 'memories');
        const q = query(memoriesCollection, where('userId', '==', currentUser.uid));
        return collectionData(q) as Observable<MemoryData[]>;
      })
    );
  }
}
