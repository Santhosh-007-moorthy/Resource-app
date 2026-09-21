import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

export interface ResourceItem {
  id?: number;
  nameId: string; // business-facing unique ID for this resource person, set on the Resource page
  dbName?: string; // name of this resource's own database (informational, set by the server)
  name: string;
  role: string;
}

@Injectable({
  providedIn: 'root'
})
export class ResourceService {
  // Base URL of the Spring Boot backend. Update this if the backend runs elsewhere.
  private baseUrl = 'http://localhost:8080/api/resources';

  constructor(private http: HttpClient, private authService: AuthService) {}

  getAllResources(): Observable<ResourceItem[]> {
    return this.http.get<ResourceItem[]>(this.baseUrl);
  }

  addResource(resource: ResourceItem): Observable<ResourceItem> {
    return this.http.post<ResourceItem>(this.baseUrl, resource);
  }

  deleteResource(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  // Admin-only: reads the *exact* current shared password straight from the server, so the
  // Resource page always shows the real value (right down to case/punctuation) instead of a
  // hardcoded guess that can drift out of sync with application.properties.
  getSharedLoginPassword(): Observable<{ password: string }> {
    return this.http.get<{ password: string }>(`${this.baseUrl}/shared-login-password`, {
      headers: this.authService.adminHeaders()
    });
  }
}

