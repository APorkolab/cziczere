import { Component, EventEmitter, Output, Input } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-memory-create',
  templateUrl: './memory-create.component.html',
  styleUrls: ['./memory-create.component.css']
})
export class MemoryCreateComponent {
  @Output() memorySubmit = new EventEmitter<string>();
  @Input() isLoading: boolean = false;
  @Input() error: string | null = null;

  memoryForm = new FormGroup({
    text: new FormControl('', [Validators.required, Validators.minLength(10)])
  });

  onSubmit() {
    if (this.memoryForm.valid && this.memoryForm.value.text) {
      this.memorySubmit.emit(this.memoryForm.value.text);
      this.memoryForm.reset();
    }
  }
}
