
# 🌱 Cziczere: The AI-Powered Digital Memory Garden

Cziczere is a generative art and digital well-being application that transforms user memories into a unique, ever-evolving digital garden. Using advanced AI, each memory is planted as a "seed," which blossoms into a procedurally generated visual element, creating a beautiful and personal sanctuary of positive moments.

This project is not a journal; it's an interactive art piece powered by your own reflections.

## ## Core Concept & Vision

In a fast-paced digital world, Cziczere offers a space for quiet reflection. The core philosophy is to leverage technology not for productivity, but for mindfulness. By focusing on positive memories and gratitude, users cultivate a personal garden that serves as a visual representation of their joy and peace. The application uses a serverless architecture on Google Cloud to provide a scalable, cost-effective, and intelligent experience.

## ## ✨ Key Features

-   **AI-Enhanced Memory Input**: A Large Language Model (LLM) analyzes user's memories to identify key themes, sentiments, and generate a creative prompt for image generation.
    
-   **Generative Visuals**: Each memory is rendered by Google's Imagen model into a unique visual element, which is then stored in Google Cloud Storage.
    
-   **Secure Authentication**: User authentication is handled by Firebase Authentication, ensuring that each user's garden is private and secure.

-   **(In-Progress) Dynamic Garden Atmosphere**: The garden's ambient environment (lighting, weather, sound) will subtly change based on the overall mood of the memories planted over time.
    
-   **(Planned) Interactive Exploration**: Users will be able to navigate their 3D garden, revisit memories by clicking on their corresponding plants, and watch their landscape flourish.
    
-   **(Planned) The Gardener's Assistant**: An AI-powered conversational agent that helps users find patterns in their thoughts and offers gentle prompts for reflection.

## ## 🛠️ Tech Stack & Architecture

This project is built on a fully serverless, cost-optimized architecture using the Google Cloud & Firebase ecosystem.


| Tier              | Technology / Service                                                                                     | Purpose                                                                                           |
|-------------------|----------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| **Frontend**      | [Angular](https://angular.io/)                                                                           | Modern, component-based framework for the user interface.                                         |
|                   | [Three.js](https://threejs.org/) / [p5.js](https://p5js.org/)                                            | WebGL library for rendering the 2D/3D garden canvas.                                              |
| **Hosting**       | [Firebase Hosting](https://firebase.google.com/docs/hosting)                                             | Global CDN for fast delivery of the frontend application.                                          |
| **Backend Logic** | [Cloud Functions for Firebase](https://firebase.google.com/docs/functions) (Java)                        | A Java-based serverless function that orchestrates AI calls and database interactions.            |
| **Database**      | [Cloud Firestore](https://firebase.google.com/docs/firestore)                                            | Real-time NoSQL database for storing memories, prompts, emotions, and image URLs.                 |
| **Authentication**| [Firebase Authentication](https://firebase.google.com/docs/auth)                                         | Secure, managed user authentication via Firebase ID tokens.                                       |
| **Image Storage** | [Google Cloud Storage](https://cloud.google.com/storage)                                                 | Stores the generated images and provides public URLs for access.                                  |
| **AI Services**   | [Google Vertex AI](https://cloud.google.com/vertex-ai)                                                   | - **Gemini 1.5 Flash:** Text analysis and image prompt generation.<br>- **Imagen:** Generative image creation. |

## 🚀 Getting Started

Follow these instructions to get the project running on your local machine for development and testing purposes.
### Prerequisites

Ensure you have the following tools installed on your system:

 - Node.js (v18 or later) & npm
 - Angular CLI (npm install -g @angular/cli)   
 - Java JDK (v17 or later) & Maven   
 - Google Cloud SDK (gcloud)   
 - Firebase CLI (npm install -g
   firebase-tools)

### Setup and Installation

    Clone the Repository

    git clone https://github.com/aporkolab/cziczere.git
    cd cziczere

Google Cloud & Firebase Setup

 - Create a new project on the Google Cloud Console.
 - Create a new Firebase project and link it to your Google Cloud
   project.
   
   
 - Enable the following APIs in your GCP project: Vertex AI API, Cloud
   Functions API, Cloud Build API.
   
 - Crucially, set up a billing alert in the GCP console to avoid
   unexpected charges. A $5 alert is recommended to start.

Backend Setup (Cloud Functions)

    cd functions

   Authenticate with Google Cloud:

    gcloud auth application-default login

Create a .env.local file for local environment variables. See .env.example for the required variables:

        GCP_PROJECT_ID="your-gcp-project-id"
        GCP_REGION="your-gcp-region" # e.g., us-central1

        Install Java dependencies:

        mvn install

Frontend Setup (Angular)

    cd ../frontend # from the root directory
    npm install

    Follow the on-screen instructions to set up your Firebase web configuration in src/environments/environment.ts.

### Running the Application Locally

For local development, we will use the Firebase Local Emulator Suite.

Start the Backend Emulators
From the project's root directory, start the emulators for Functions and Firestore:

    firebase emulators:start --only functions,firestore

This will start a local server for your Cloud Functions and a local Firestore database.

Start the Frontend Development Server
In a separate terminal, from the frontend directory:

    ng serve

Navigate to http://localhost:4200/. The application will automatically connect to your local emulators.

## ☁️ Deployment

To deploy the application to your live Firebase project:

Build the Angular Application

    cd frontend
    ng build --configuration production

Deploy to Firebase
From the project's root directory, deploy the hosting, functions, and Firestore rules:

    firebase deploy --only hosting,functions,firestore

## 🤝 Contributing

Contributions are welcome! If you have ideas for new features or improvements, please follow these steps:

- Fork the Project
- Create your Feature Branch (git checkout -b feature/AmazingFeature)
- Commit your Changes (git commit -m 'Add some AmazingFeature')
- Push to the Branch (git push origin feature/AmazingFeature)
- Open a Pull Request

## 📄 License

This project is distributed under the MIT License. See LICENSE.txt for more information.
