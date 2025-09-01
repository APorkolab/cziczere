import { Component, EventEmitter, Output } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-memory-create',
  templateUrl: './memory-create.component.html',
  styleUrls: ['./memory-create.component.css']
})
export class MemoryCreateComponent {
  @Output() memorySubmit = new EventEmitter<string>();

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
