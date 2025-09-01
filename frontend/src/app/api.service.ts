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
  type: string;
  emotions: { [key: string]: number; };
}

export interface InsightData {
  id?: string; // Add optional id field
  userId: string;
  text: string;
  timestamp: number;
}

export interface PosterResponse {
  base64Image: string;
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
  private firestore: Firestore = inject(Firestore);
  private http: HttpClient = inject(HttpClient);
  user$ = user(this.auth);

  // TODO: Replace with the actual URL of your deployed Cloud Function.
  private generateFunctionUrl = 'http://127.0.0.1:5001/cziczere-ai/us-central1/generateMemoryPlant';
  private analyzeFunctionUrl = 'http://127.0.0.1:5001/cziczere-ai/us-central1/analyzeMemories';
  private atmosphereFunctionUrl = 'http://127.0.0.1:5001/cziczere-ai/us-central1/getAtmosphere';
  private exportGardenFunctionUrl = 'http://127.0.0.1:5001/cziczere-ai/us-central1/exportGarden';
  private insightAudioFunctionUrl = 'http://127.0.0.1:5001/cziczere-ai/us-central1/getInsightAudio';


  getInsightAudioUrl(insightId: string): Observable<{audioUrl: string}> {
    return idToken(this.auth).pipe(
      first(),
      switchMap(token => {
        if (!token) {
          throw new Error('User not logged in!');
        }
        const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
        const params = { insightId };
        return this.http.get<{audioUrl: string}>(this.insightAudioFunctionUrl, { headers, params });
      })
    );
  }

  exportGardenAsPoster(): Observable<PosterResponse> {
    return idToken(this.auth).pipe(
      first(),
      switchMap(token => {
        if (!token) {
          throw new Error('User not logged in!');
        }
        const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
        return this.http.post<PosterResponse>(this.exportGardenFunctionUrl, {}, { headers });
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

  requestInsight(type: 'insight' | 'weekly' | 'monthly' = 'insight'): Observable<InsightData> {
    return idToken(this.auth).pipe(
      first(),
      switchMap(token => {
        if (!token) {
          throw new Error('User not logged in!');
        }
        const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
        const params = { type };
        return this.http.post<InsightData>(this.analyzeFunctionUrl, {}, { headers, params });
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
        return collectionData(q, { idField: 'id' }).pipe(
          map(insights => (insights.length > 0 ? insights[0] as InsightData : null))
        );
      })
    );
  }

  getMemories(): Observable<MemoryData[]> {
    return this.user$.pipe(
      switchMap(currentUser => {
        if (!currentUser) {
          return of([]); // Return empty array if no user
        }
        const memoriesCollection = collection(this.firestore, 'memories');
        const q = query(memoriesCollection, where('userId', '==', currentUser.uid));
        return collectionData(q) as Observable<MemoryData[]>;
      })
    );
  }
}
