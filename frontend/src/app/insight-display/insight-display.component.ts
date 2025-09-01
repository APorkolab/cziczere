import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService, InsightData } from '../api.service';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'app-insight-display',
  templateUrl: './insight-display.component.html',
  styleUrls: ['./insight-display.component.css'],
  standalone: true,
  imports: [CommonModule]
})
export class InsightDisplayComponent {
  @Input() insight: InsightData | null = null;
  @Input() isLoading: boolean = false;
  @Input() error: string | null = null;

  private apiService: ApiService = inject(ApiService);
  private audioPlayer = new Audio();
  isAudioLoading$ = new BehaviorSubject<boolean>(false);

  playInsightAudio(insightId: string | undefined) {
    if (!insightId) return;

    this.isAudioLoading$.next(true);
    this.apiService.getInsightAudioUrl(insightId).subscribe({
      next: (response) => {
        this.audioPlayer.src = response.audioUrl;
        this.audioPlayer.play();
        this.isAudioLoading$.next(false);
      },
      error: (err) => {
        console.error('Error getting insight audio:', err);
        this.isAudioLoading$.next(false);
        // Optionally show an error message to the user
      }
    });
  }
}
