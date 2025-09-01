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
  @Output() requestInsight = new EventEmitter<'weekly_summary' | 'monthly_insight'>();

  requestWeeklySummary(): void {
    this.requestInsight.emit('weekly_summary');
  }

  requestMonthlyInsight(): void {
    this.requestInsight.emit('monthly_insight');
  }
}
