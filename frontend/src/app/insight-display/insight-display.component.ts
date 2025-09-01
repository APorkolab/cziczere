import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { InsightData } from '../api.service';

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
}
