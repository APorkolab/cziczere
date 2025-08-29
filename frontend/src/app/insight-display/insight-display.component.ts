import { Component, Input, Output, EventEmitter } from '@angular/core';
import { InsightData } from '../api.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-insight-display',
  templateUrl: './insight-display.component.html',
  styleUrls: ['./insight-display.component.css'],
  standalone: true,
  imports: [CommonModule]
})
export class InsightDisplayComponent {
  @Input() insight: InsightData | null = null;
  @Output() requestSummary = new EventEmitter<void>();

  requestWeeklySummary(): void {
    this.requestSummary.emit();
  }
}
