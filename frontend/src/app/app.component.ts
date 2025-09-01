import { Component, inject } from '@angular/core';
import { Auth, authState, signInWithPopup, GoogleAuthProvider, signOut, User } from '@angular/fire/auth';
import { Observable, BehaviorSubject, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService, MemoryData, InsightData } from './api.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  private auth: Auth = inject(Auth);
  private api: ApiService = inject(ApiService);
  public readonly user$: Observable<User | null> = authState(this.auth);
  public memories$: Observable<MemoryData[]>;
  public latestInsight$: Observable<InsightData | null>;

  public latestMemory$ = new BehaviorSubject<MemoryData | null>(null);
  public isLoading$ = new BehaviorSubject<boolean>(false);
  public isInsightLoading$ = new BehaviorSubject<boolean>(false);

  constructor() {
    this.memories$ = this.api.getMemories();
    this.latestInsight$ = this.api.getLatestInsight();
  }

  onMemorySubmit(memoryText: string) {
    this.isLoading$.next(true);
    this.latestMemory$.next(null);
    this.api.createMemory(memoryText).subscribe({
      next: (memory) => {
        this.latestMemory$.next(memory);
        this.isLoading$.next(false);
      },
      error: (err) => {
        console.error('Error creating memory:', err);
        alert('There was an error creating your memory. Please check the console.');
        this.isLoading$.next(false);
      }
    });
  }

  requestInsight() {
    this.isInsightLoading$.next(true);
    this.api.requestInsight().pipe(
      catchError(err => {
        console.error('Error requesting insight:', err);
        alert('Could not get an insight. Are there enough memories in your garden?');
        return of(null); // Return a null observable to complete the stream
      })
    ).subscribe({
      next: () => {
        // The insight display will update automatically via its real-time listener.
        this.isInsightLoading$.next(false);
      },
      error: () => {
        // Error is already handled by catchError
        this.isInsightLoading$.next(false);
      }
    });
  }

  login() {
    signInWithPopup(this.auth, new GoogleAuthProvider());
  }

  logout() {
    signOut(this.auth);
  }
}
