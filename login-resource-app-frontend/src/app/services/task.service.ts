import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

export interface TaskItem {
  id?: number;
  taskName: string;
  taskCode: string;
  vehicle?: string;
  weatherRegulations?: string;
  fromDate: string; // ISO yyyy-MM-dd
  toDate: string;   // ISO yyyy-MM-dd
  assignedResourceId: number;      // the one resource person this task is allocated to
  assignedResourceNameId?: string;
  assignedResourceName?: string;
  withinAllocatedPeriod?: boolean; // computed by the backend: does today fall in [fromDate, toDate]
  completed?: boolean;             // set by the admin via the Mark Complete/Pending button
  disabledDates?: string[];        // ISO dates (yyyy-MM-dd) the admin manually blocked for this task,
                                    // on top of Sundays which are always blocked and not stored here
}

@Injectable({
  providedIn: 'root'
})
export class TaskService {
  private baseUrl = 'http://localhost:8080/api/tasks';

  constructor(private http: HttpClient, private authService: AuthService) {}

  // Open to any logged-in user - populates the task dropdown and the read-only
  // Task Master list.
  getAllTasks(): Observable<TaskItem[]> {
    return this.http.get<TaskItem[]>(this.baseUrl);
  }

  // Admin-only: the backend re-validates the admin credentials sent as headers.
  addTask(task: TaskItem): Observable<TaskItem> {
    return this.http.post<TaskItem>(this.baseUrl, task, { headers: this.authService.adminHeaders() });
  }

  updateTask(id: number, task: TaskItem): Observable<TaskItem> {
    return this.http.put<TaskItem>(`${this.baseUrl}/${id}`, task, { headers: this.authService.adminHeaders() });
  }

  deleteTask(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`, { headers: this.authService.adminHeaders() });
  }

  // Admin-only "Mark Complete" / "Mark Pending" button on the Task Master page. Kept
  // separate from updateTask so toggling completion never touches (or is overwritten by)
  // the rest of a task's details.
  setTaskCompletion(id: number, completed: boolean): Observable<TaskItem> {
    return this.http.patch<TaskItem>(`${this.baseUrl}/${id}/completion`, { completed }, {
      headers: this.authService.adminHeaders()
    });
  }
}

// Sundays are always blocked for reporting, for every task, without the admin having to
// list them one by one - shared by the Task Master popup (to grey them out) and the
// Report Task calendar (to exclude them). Expects a yyyy-MM-dd string.
export function isSundayDate(isoDate: string): boolean {
  const d = new Date(isoDate + 'T00:00:00');
  return !isNaN(d.getTime()) && d.getDay() === 0;
}
