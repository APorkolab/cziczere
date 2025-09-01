import { Component, Input, OnChanges, SimpleChanges, ElementRef, ViewChild, AfterViewInit, OnDestroy } from '@angular/core';
import p5 from 'p5';
import { MemoryData } from '../api.service';

@Component({
  selector: 'app-garden-canvas',
  templateUrl: './garden-canvas.component.html',
  styleUrls: ['./garden-canvas.component.css']
})
export class GardenCanvasComponent implements AfterViewInit, OnDestroy, OnChanges {
  @Input() memories: MemoryData[] | null = [];
  @ViewChild('canvas') canvasHost!: ElementRef;

  private sketch!: p5;

  ngAfterViewInit(): void {
    this.createCanvas();
  }

  ngOnDestroy(): void {
    this.sketch.remove();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.sketch && changes['memories']) {
      this.sketch.redraw();
    }
  }

  private createCanvas(): void {
    this.sketch = new p5(this.createSketch.bind(this), this.canvasHost.nativeElement);
  }

  private createSketch(s: p5): void {
    const images: p5.Image[] = [];
    const positions: { x: number, y: number }[] = [];

    s.setup = () => {
      s.createCanvas(s.windowWidth * 0.8, 500).parent(this.canvasHost.nativeElement);
      s.noLoop(); // Redraw only when ngOnChanges is called
      this.loadImages(s, images, positions);
    };

    s.draw = () => {
      s.background(240, 248, 255); // AliceBlue background
      for (let i = 0; i < images.length; i++) {
        if (images[i]) {
          s.image(images[i], positions[i].x, positions[i].y, 100, 100); // Draw image
        }
      }
    };
  }

  private loadImages(s: p5, images: p5.Image[], positions: {x: number, y: number}[]): void {
    if (!this.memories) {
      return;
    }
    images.length = 0;
    positions.length = 0;

    this.memories.forEach((memory, index) => {
      images[index] = s.loadImage(memory.imageUrl, () => {
        positions[index] = {
          x: (s.width / (this.memories!.length + 1)) * (index + 1) - 50,
          y: s.height / 2 - 50,
        };
        s.redraw();
      });
    });
  }
}
