import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { ReactiveFormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { initializeApp, provideFirebaseApp } from '@angular/fire/app';
import { provideAuth, getAuth } from '@angular/fire/auth';
import { provideFirestore, getFirestore } from '@angular/fire/firestore';
import { provideFunctions, getFunctions } from '@angular/fire/functions';

import { AppComponent } from './app.component';
import { environment } from '../environments/environment';
import { MemoryCreateComponent } from './memory-create/memory-create.component';
import { InsightDisplayComponent } from './insight-display/insight-display.component';
import { ArViewComponent } from './ar-view/ar-view.component';
import { GardenComponent } from './garden/garden.component';

@NgModule({
  declarations: [
    AppComponent,
    MemoryCreateComponent,
    InsightDisplayComponent
  ],
  imports: [
    BrowserModule,
    GardenComponent,
    ArViewComponent,
    ReactiveFormsModule,
    HttpClientModule,
    provideFirebaseApp(() => initializeApp(environment.firebase)),
    provideAuth(() => getAuth()),
    provideFirestore(() => getFirestore()),
    provideFunctions(() => getFunctions())
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
