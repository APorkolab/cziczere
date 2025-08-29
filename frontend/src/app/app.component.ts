import { Component, inject } from '@angular/core';
import { Auth, authState, signInWithPopup, GoogleAuthProvider, signOut, User } from '@angular/fire/auth';
import { Observable, BehaviorSubject } from 'rxjs';
import { ApiService, MemoryData } from './api.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  private auth: Auth = inject(Auth);
  private api: ApiService = inject(ApiService);
  public readonly user: Observable<User | null> = authState(this.auth);

  public latestMemory$ = new BehaviorSubject<MemoryData | null>(null);
  public isLoading$ = new BehaviorSubject<boolean>(false);

  constructor() {}

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

  login() {
    signInWithPopup(this.auth, new GoogleAuthProvider());
  }

  logout() {
    signOut(this.auth);
  }
}
