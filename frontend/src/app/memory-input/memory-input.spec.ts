import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MemoryInput } from './memory-input';

describe('MemoryInput', () => {
  let component: MemoryInput;
  let fixture: ComponentFixture<MemoryInput>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MemoryInput]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MemoryInput);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
