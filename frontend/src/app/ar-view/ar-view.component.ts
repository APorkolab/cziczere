import { Component, OnInit, ElementRef, ViewChild, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import * as THREE from 'three';
import { ARButton } from 'three/examples/jsm/webxr/ARButton.js';
import { ApiService, MemoryData } from '../api.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-ar-view',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ar-view.component.html',
  styleUrls: ['./ar-view.component.css']
})
export class ArViewComponent implements OnInit, OnDestroy {
  @ViewChild('arContainer', { static: true }) arContainer!: ElementRef;

  private api: ApiService = inject(ApiService);
  private memoriesSubscription!: Subscription;

  private renderer!: THREE.WebGLRenderer;
  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;

  ngOnInit(): void {
    this.initXR();
  }

  ngOnDestroy(): void {
    if (this.memoriesSubscription) {
      this.memoriesSubscription.unsubscribe();
    }
    // Clean up Three.js resources
    if (this.renderer) {
      this.renderer.dispose();
    }
    if (this.scene) {
      // You might need to traverse the scene and dispose geometries, materials, textures
    }
  }

  private initXR(): void {
    const container = this.arContainer.nativeElement;

    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 0.01, 20);

    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    this.renderer.setPixelRatio(window.devicePixelRatio);
    this.renderer.setSize(window.innerWidth, window.innerHeight);
    this.renderer.xr.enabled = true;
    container.appendChild(this.renderer.domElement);

    const arButton = ARButton.createButton(this.renderer);
    container.appendChild(arButton);

    this.setupContent();

    this.renderer.setAnimationLoop(() => {
      this.renderer.render(this.scene, this.camera);
    });
  }

  private setupContent(): void {
    const light = new THREE.HemisphereLight(0xffffff, 0xbbbbff, 1);
    light.position.set(0.5, 1, 0.25);
    this.scene.add(light);

    this.memoriesSubscription = this.api.getMemories().subscribe(memories => {
      this.addMemoriesToScene(memories);
    });
  }

  private addMemoriesToScene(memories: MemoryData[]): void {
    const textureLoader = new THREE.TextureLoader();
    const geometry = new THREE.PlaneGeometry(0.5, 0.5);

    memories.forEach((memory, index) => {
      if (memory.imageUrl) {
        const material = new THREE.MeshBasicMaterial({
          map: textureLoader.load(memory.imageUrl)
        });
        const plane = new THREE.Mesh(geometry, material);

        // Position them in a circle for now
        const angle = (index / memories.length) * Math.PI * 2;
        const radius = 1.5;
        plane.position.set(Math.cos(angle) * radius, 0, Math.sin(angle) * radius - 2);

        this.scene.add(plane);
      }
    });
  }
}
