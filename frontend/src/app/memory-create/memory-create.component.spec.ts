import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MemoryCreateComponent } from './memory-create.component';

describe('MemoryCreateComponent', () => {
  let component: MemoryCreateComponent;
  let fixture: ComponentFixture<MemoryCreateComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [MemoryCreateComponent]
    });
    fixture = TestBed.createComponent(MemoryCreateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
