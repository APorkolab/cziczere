import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { MemoryData } from './api.service';

@Injectable({
  providedIn: 'root'
})
export class ArStateService {
  private selectedMemorySource = new BehaviorSubject<MemoryData | null>(null);
  selectedMemory$ = this.selectedMemorySource.asObservable();

  constructor() { }

  setSelectedMemory(memory: MemoryData | null) {
    this.selectedMemorySource.next(memory);
  }
}
