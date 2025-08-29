import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Auth, idToken, user } from '@angular/fire/auth';
import { Firestore, collection, collectionData, query, where, orderBy, limit } from '@angular/fire/firestore';
import { switchMap, first, Observable, of, map } from 'rxjs';

export interface MemoryData {
  userId: string;
  userText: string;
  imagePrompt: string;
  imageUrl: string;
  timestamp: number;
}

export interface InsightData {
  userId: string;
  text: string;
  timestamp: number;
  type: string;
}

export interface AtmosphereData {
  weather: string;
  backgroundColor: string;
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
  private generateFunctionUrl = 'http://127.0.0.1:5001/cziczere-ai/us-central1/generateMemoryPlant';
  private analyzeFunctionUrl = 'http://127.0.0.1:5001/cziczere-ai/us-central1/analyzeMemories';
  private atmosphereFunctionUrl = 'http://127.0.0.1:5001/cziczere-ai/us-central1/getAtmosphere';


  createMemory(text: string): Observable<MemoryData> {
    return idToken(this.auth).pipe(
      first(),
      switchMap(token => {
        if (!token) {
          throw new Error('User not logged in!');
        }
        const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
        const body = { text };
        return this.http.post<MemoryData>(this.generateFunctionUrl, body, { headers });
      })
    );
  }

  getAtmosphere(): Observable<AtmosphereData> {
    return idToken(this.auth).pipe(
      first(),
      switchMap(token => {
        if (!token) {
          throw new Error('User not logged in!');
        }
        const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
        return this.http.post<AtmosphereData>(this.atmosphereFunctionUrl, {}, { headers });
      })
    );
  }

  requestInsight(analysisType: string = 'insight'): Observable<InsightData> {
    return idToken(this.auth).pipe(
      first(),
      switchMap(token => {
        if (!token) {
          throw new Error('User not logged in!');
        }
        const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
        const body = { analysisType };
        return this.http.post<InsightData>(this.analyzeFunctionUrl, body, { headers });
      })
    );
  }

  getLatestInsight(): Observable<InsightData | null> {
    return this.user$.pipe(
      switchMap(currentUser => {
        if (!currentUser) {
          return of(null);
        }
        const insightsCollection = collection(this.firestore, 'insights');
        const q = query(
          insightsCollection,
          where('userId', '==', currentUser.uid),
          orderBy('timestamp', 'desc'),
          limit(1)
        );
        return collectionData(q).pipe(
          map(insights => (insights.length > 0 ? insights[0] as InsightData : null))
        );
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
