# AR/VR Mode - Technical Concept & Prototype Findings

This document outlines the technical concept and findings from the prototype development for the Augmented Reality (AR) feature in Cziczere.

## 1. Core Technology

The prototype was successfully built using the following core technologies:

- **Angular:** As the primary frontend framework.
- **Three.js:** For 3D scene management, rendering, and object creation.
- **WebXR Device API:** The browser-native API for handling AR sessions.
- **`three/examples/jsm/webxr/ARButton.js`:** A helper script from the Three.js examples that significantly simplifies the process of checking for AR compatibility and creating the session entry button.

## 2. Prototype Implementation Steps

The prototype was implemented by creating a new standalone Angular component (`ar-view`) and integrating it into the main application.

1.  **Component Creation:** An `ar-view` component was created to encapsulate all AR-related logic.
2.  **Three.js Scene Setup:** Inside the component, a basic Three.js scene was initialized:
    *   A `Scene` object was created.
    *   A `PerspectiveCamera` was set up.
    *   A `WebGLRenderer` was created with `xr.enabled = true`.
3.  **AR Session Management:**
    *   The `ARButton` helper from Three.js was used. It automatically handles:
        *   Checking if the device supports WebXR with the required 'immersive-ar' mode.
        *   Displaying a user-friendly "START AR" button if supported.
        *   Handling the user click to request and start the `XRSession`.
    *   The renderer's animation loop (`renderer.setAnimationLoop`) was used to continuously render the scene on every frame provided by the XR device.
4.  **Displaying Memories:**
    *   The component subscribes to the `ApiService.getMemories()` observable to fetch the user's memories.
    *   For each memory containing an `imageUrl`, a `THREE.PlaneGeometry` (a simple 2D plane) was created.
    *   A `THREE.TextureLoader` was used to load the memory's image.
    *   A `THREE.MeshBasicMaterial` was created with the loaded texture as its `map`.
    *   A `THREE.Mesh` was created from the geometry and material.
    *   The meshes were positioned in a simple circle in front of the camera's starting point for demonstration.

## 3. Findings & Challenges

-   **Feasibility:** The prototype confirms that creating a basic AR experience that displays the user's garden images in their real-world environment is **highly feasible** with the current technology stack.
-   **Performance:** Loading and displaying a moderate number of textured planes (e.g., 10-20) is performant on modern mobile devices. Performance may degrade with a very large number of high-resolution textures.
-   **Hit-Testing:** The AR view uses hit-testing to allow users to place memories on real-world surfaces like floors and tables.
-   **Tap-to-View:** Users can tap on a memory plane in the AR scene to view the associated memory details, including the original text, emotions, and the AI-generated image prompt.
-   **User Experience (UX):** The memory planes always face the camera, ensuring that the user can always see the memory's image, regardless of their position.
-   **Angular Integration:** Integrating Three.js into an Angular component is straightforward. The `ViewChild` and `ElementRef` decorators are used to get a handle on the container div for the renderer. Component lifecycle hooks (`ngOnInit`, `ngOnDestroy`) are essential for setting up and tearing down the Three.js scene correctly to avoid memory leaks.
