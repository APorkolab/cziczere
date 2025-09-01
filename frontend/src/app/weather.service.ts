import { Injectable } from '@angular/core';
import * as THREE from 'three';

@Injectable({
  providedIn: 'root'
})
export class WeatherService {
  private rainParticles: THREE.Points | null = null;
  private rainGeometry: THREE.BufferGeometry | null = null;

  constructor() { }

  createRain(scene: THREE.Scene): void {
    const vertices = [];
    for (let i = 0; i < 1500; i++) {
      const x = Math.random() * 2000 - 1000;
      const y = Math.random() * 2000 - 1000;
      const z = Math.random() * 2000 - 1000;
      vertices.push(x, y, z);
    }

    this.rainGeometry = new THREE.BufferGeometry();
    this.rainGeometry.setAttribute('position', new THREE.Float32BufferAttribute(vertices, 3));

    const material = new THREE.PointsMaterial({
      color: 0xffffff,
      size: 1.5,
      transparent: true
    });

    this.rainParticles = new THREE.Points(this.rainGeometry, material);
    scene.add(this.rainParticles);
  }

  updateRain(): void {
    if (this.rainParticles && this.rainGeometry) {
      const positions = this.rainGeometry.attributes.position.array as Float32Array;
      for (let i = 0; i < positions.length; i += 3) {
        positions[i + 1] -= 2; // Move particle down
        if (positions[i + 1] < -1000) {
          positions[i + 1] = 1000; // Reset particle to the top
        }
      }
      this.rainGeometry.attributes.position.needsUpdate = true;
    }
  }
}
