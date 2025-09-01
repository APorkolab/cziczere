import { Component, AfterViewInit, ViewChild, ElementRef, Input, Output, EventEmitter } from '@angular/core';
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { VRButton } from 'three/examples/jsm/webxr/VRButton.js';
import { ApiService, AtmosphereData, MemoryData } from '../api.service';

import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-garden',
  templateUrl: './garden.component.html',
  styleUrls: ['./garden.component.css'],
  standalone: true,
  imports: [CommonModule]
})
export class GardenComponent implements AfterViewInit {
  @ViewChild('canvas') private canvasRef!: ElementRef;
  @Input() memories: MemoryData[] = [];
  @Output() memoryClicked = new EventEmitter<MemoryData>();

  private camera!: THREE.PerspectiveCamera;
  private scene!: THREE.Scene;
  private renderer!: THREE.WebGLRenderer;
  private controls!: OrbitControls;
  private atmosphere: AtmosphereData | null = null;

  constructor(private apiService: ApiService) {}

  ngAfterViewInit(): void {
    this.createScene();
    this.createControls();
    this.createMemories();
    this.startRenderingLoop();
    this.getAtmosphere();
  }

  private createScene(): void {
    this.scene = new THREE.Scene();
    // Set a default background color
    this.scene.background = new THREE.Color(0xabcdef);
    this.camera = new THREE.PerspectiveCamera(75, this.getAspectRatio(), 0.1, 1000);
    this.camera.position.z = 5;
  }

  private getAtmosphere(): void {
    this.apiService.getAtmosphere().subscribe(
      (data: AtmosphereData) => {
        this.atmosphere = data;
        this.scene.background = new THREE.Color(data.backgroundColor);
        // TODO: Implement weather effects based on data.weather
        console.log('Atmosphere data:', data);
      },
      (error) => {
        console.error('Error getting atmosphere:', error);
      }
    );
  }

  private createControls(): void {
    const canvas = this.canvasRef.nativeElement;
    this.renderer = new THREE.WebGLRenderer({ canvas });
    this.renderer.setSize(window.innerWidth, window.innerHeight);

    this.renderer.xr.enabled = true;
    this.canvasRef.nativeElement.parentElement.appendChild(VRButton.createButton(this.renderer));

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
  }

  private createMemories(): void {
    const textureLoader = new THREE.TextureLoader();
    const geometry = new THREE.PlaneGeometry(1, 1);

    this.memories.forEach((memory, index) => {
      const texture = textureLoader.load(memory.imageUrl);
      const material = new THREE.MeshBasicMaterial({ map: texture });
      const plane = new THREE.Mesh(geometry, material);

      const x = (index % 5 - 2) * 2;
      const y = (Math.floor(index / 5) - 2) * -2;
      plane.position.set(x, y, 0);

      this.scene.add(plane);
    });
  }

  private startRenderingLoop(): void {
    this.renderer.setAnimationLoop(() => {
      this.controls.update();
      this.renderer.render(this.scene, this.camera);
    });
  }

  private getAspectRatio(): number {
    return this.canvasRef.nativeElement.clientWidth / this.canvasRef.nativeElement.clientHeight;
  }

  onCanvasClick(event: MouseEvent): void {
    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    const y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

    const mouse = new THREE.Vector2(x, y);
    const raycaster = new THREE.Raycaster();
    raycaster.setFromCamera(mouse, this.camera);

    const intersects = raycaster.intersectObjects(this.scene.children);

    if (intersects.length > 0) {
      const clickedObject = intersects[0].object;
      const memoryIndex = this.scene.children.indexOf(clickedObject) - 1; // -1 for the light
      if (this.memories[memoryIndex]) {
        this.memoryClicked.emit(this.memories[memoryIndex]);
      }
    }
  }
}
