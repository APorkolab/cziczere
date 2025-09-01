import { Component, OnInit, inject, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Auth, User, user, GoogleAuthProvider, signInWithPopup, signOut } from '@angular/fire/auth';
import { Observable, of, BehaviorSubject } from 'rxjs';
import { catchError, switchMap, tap } from 'rxjs/operators';

import { GardenComponent } from './garden/garden.component';
import { MemoryCreateComponent } from './memory-create/memory-create.component';
import { InsightDisplayComponent } from './insight-display/insight-display.component';
import { MemoryDetailComponent } from './memory-detail/memory-detail.component'; // Import MemoryDetailComponent
import { ApiService, InsightData, MemoryData } from './api.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
  standalone: true,
  imports: [CommonModule, GardenComponent, MemoryCreateComponent, InsightDisplayComponent, MemoryDetailComponent] // Add MemoryDetailComponent
})
export class AppComponent implements OnInit {
  private auth: Auth = inject(Auth);
  private apiService: ApiService = inject(ApiService);

  @ViewChild(GardenComponent) gardenComponent!: GardenComponent;

  user: Observable<User | null> = user(this.auth);

  memories$: Observable<MemoryData[]>;
  latestInsight$: Observable<InsightData | null | undefined>;
  selectedMemory: MemoryData | null = null;

  isLoading$ = new BehaviorSubject<boolean>(false);
  isInsightLoading$ = new BehaviorSubject<boolean>(false);
  isExporting$ = new BehaviorSubject<boolean>(false);
  insightError$ = new BehaviorSubject<string | null>(null);

  constructor() {
    this.memories$ = this.apiService.getMemories();
    this.latestInsight$ = of(undefined);
  }

  ngOnInit(): void {
    this.fetchLatestInsight();
  }

  login() {
    signInWithPopup(this.auth, new GoogleAuthProvider());
  }

  logout() {
    signOut(this.auth);
  }

  onMemorySubmit(memoryText: string) {
    this.isLoading$.next(true);
    this.apiService.createMemory(memoryText).subscribe({
      next: () => this.isLoading$.next(false),
      error: (err) => {
        console.error('Memory creation failed:', err);
        this.isLoading$.next(false);
      }
    });
  }

  fetchLatestInsight(): void {
    this.isInsightLoading$.next(true);
    this.insightError$.next(null);
    this.latestInsight$ = this.apiService.getLatestInsight().pipe(
      catchError(err => {
        console.error('Error fetching latest insight:', err);
        this.insightError$.next('Could not load the latest insight.');
        return of(null);
      }),
      tap(() => this.isInsightLoading$.next(false))
    );
  }

  requestInsight(): void {
    this.isInsightLoading$.next(true);
    this.insightError$.next(null);
    this.latestInsight$ = of(undefined);

    this.apiService.requestInsight().pipe(
      catchError(err => {
        console.error('Error requesting new insight:', err);
        this.insightError$.next('Failed to generate a new insight. There may be no memories to analyze yet.');
        return of(null);
      }),
      switchMap(() => this.apiService.getLatestInsight()),
      tap(() => this.isInsightLoading$.next(false))
    ).subscribe(insight => {
        this.latestInsight$ = of(insight);
    });
  }

  onExportPoster(): void {
      if (this.gardenComponent) {
          this.gardenComponent.exportAsPoster();
      } else {
          console.error("Garden component not available to export.");
      }
  }

  onMemoryClicked(memory: MemoryData) {
    this.selectedMemory = memory;
  }

  onDetailClose() {
    this.selectedMemory = null;
  }
}
