import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AssignmentItem {
  id?: number;
  resourceId?: number;
  resourceNameId?: string;
  resourceName: string;
  taskName: string;
  taskCode: string;
  date?: string;
  month: string;
  year: number;
  hours?: number;
  remarks?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AssignmentService {
  private baseUrl = 'http://localhost:8080/api/assignments';

  constructor(private http: HttpClient) {}

  // Only the saved tasks belonging to one resource person, read straight out of that
  // person's own database - this is what the User page uses so switching the Resource
  // dropdown never shows another person's data.
  getAssignmentsByResource(resourceId: number): Observable<AssignmentItem[]> {
    return this.http.get<AssignmentItem[]>(`${this.baseUrl}/by-resource/${resourceId}`);
  }

  addAssignment(resourceId: number, assignment: AssignmentItem): Observable<AssignmentItem> {
    return this.http.post<AssignmentItem>(`${this.baseUrl}/${resourceId}`, assignment);
  }

  // resourceId is required because each person's assignment IDs live in their own
  // database and are not unique across everyone.
  deleteAssignment(resourceId: number, id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${resourceId}/${id}`);
  }
}
