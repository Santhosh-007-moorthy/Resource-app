import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface AuthResponse {
  username: string;
  role: 'ADMIN' | 'USER';
  resourceId?: number;
  resourceNameId?: string;
  resourceName?: string;
}

export interface AuthSession {
  username: string;
  password: string; // kept only in sessionStorage, for re-sending as admin headers on Task Master writes
  role: 'ADMIN' | 'USER';
  // Only set for a resource person's login (role === 'USER'): which Resource they are, so
  // the sidebar can greet them by name and the Assigned Task / Report Task pages can load
  // their own data straight away, with no manual "which resource are you" picker.
  resourceId?: number;
  resourceNameId?: string;
  resourceName?: string;
}

const SESSION_KEY = 'resourceAppSession';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private baseUrl = 'http://localhost:8080/api/auth';

  constructor(private http: HttpClient) {}

  // Every login account (the admin, and every resource person) is provisioned server-side -
  // the admin seeds itself once at startup, and a resource person's account is created
  // automatically the moment an admin adds that Resource. There is no self-registration.
  login(username: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, { username, password }).pipe(
      tap((res) => {
        const session: AuthSession = {
          username: res.username,
          password,
          role: res.role,
          resourceId: res.resourceId,
          resourceNameId: res.resourceNameId,
          resourceName: res.resourceName
        };
        sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
      })
    );
  }

  logout(): void {
    sessionStorage.removeItem(SESSION_KEY);
  }

  getSession(): AuthSession | null {
    const raw = sessionStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as AuthSession) : null;
  }

  isLoggedIn(): boolean {
    return this.getSession() !== null;
  }

  isAdmin(): boolean {
    return this.getSession()?.role === 'ADMIN';
  }

  getUsername(): string {
    return this.getSession()?.username ?? '';
  }

  // The signed-in resource person's own resource id/name, for the sidebar greeting and for
  // loading only their own Assigned Task / Report Task data. Null for the admin.
  getResourceId(): number | null {
    return this.getSession()?.resourceId ?? null;
  }

  getResourceName(): string {
    return this.getSession()?.resourceName ?? this.getUsername();
  }

  getResourceNameId(): string {
    return this.getSession()?.resourceNameId ?? '';
  }

  // Headers the backend re-checks on every Task Master write, so hiding the admin buttons
  // in the UI is backed up by a real server-side credential check, not just client trust.
  adminHeaders(): HttpHeaders {
    const session = this.getSession();
    return new HttpHeaders({
      'X-Admin-Username': session?.username ?? '',
      'X-Admin-Password': session?.password ?? ''
    });
  }
}
