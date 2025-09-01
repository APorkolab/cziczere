import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Auth, idToken } from '@angular/fire/auth';
import { switchMap, first, Observable } from 'rxjs';

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

  // TODO: Replace with the actual URL of your deployed Cloud Function.
  private functionUrl = 'https://your-region-your-project-id.cloudfunctions.net/generateMemoryPlant';

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
}
