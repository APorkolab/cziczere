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

  public isLoading$ = new BehaviorSubject<boolean>(false);
  public isInsightLoading$ = new BehaviorSubject<boolean>(false);

  constructor() {
    this.memories$ = this.api.getMemories();
    this.latestInsight$ = this.api.getLatestInsight();
  }

  onMemorySubmit(memoryText: string) {
    this.isLoading$.next(true);
    this.api.createMemory(memoryText).subscribe({
      next: () => {
        // The garden will update automatically via the real-time listener.
        this.isLoading$.next(false);
      },
      error: (err) => {
        console.error('Error creating memory:', err);
        alert('There was an error creating your memory. Please check the console.');
        this.isLoading$.next(false);
      }
    });
  }

  requestInsight(type: 'insight' | 'weekly_summary' | 'monthly_insight' = 'insight') {
    this.isInsightLoading$.next(true);
    let apiCall;
    switch (type) {
      case 'weekly_summary':
        apiCall = this.api.requestWeeklySummary();
        break;
      case 'monthly_insight':
        apiCall = this.api.requestMonthlyInsight();
        break;
      default:
        apiCall = this.api.requestInsight();
        break;
    }

    apiCall.pipe(
      catchError(err => {
        console.error(`Error requesting ${type}:`, err);
        alert(`Could not get an ${type}. Are there enough memories in your garden?`);
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

  onMemoryClicked(memory: MemoryData) {
    console.log('Memory clicked:', memory);
    alert(`Memory: "${memory.userText}"`);
  }
}
