import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GardenCanvasComponent } from './garden-canvas.component';

describe('GardenCanvasComponent', () => {
  let component: GardenCanvasComponent;
  let fixture: ComponentFixture<GardenCanvasComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [GardenCanvasComponent]
    });
    fixture = TestBed.createComponent(GardenCanvasComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
